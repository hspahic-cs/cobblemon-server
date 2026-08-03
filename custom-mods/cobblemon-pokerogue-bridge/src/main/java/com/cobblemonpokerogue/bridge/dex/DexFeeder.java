package com.cobblemonpokerogue.bridge.dex;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemonpokerogue.bridge.BridgeConfig;
import com.cobblemonpokerogue.bridge.BridgeServices;
import com.cobblemonpokerogue.bridge.CobblemonPokerogueBridge;
import com.cobblemonpokerogue.bridge.db.PokerogueDb;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import com.cobblemonpokerogue.bridge.poll.DbPoller;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * §2.49 dex-locked dreams, bridge side: feeds each linked account's server-dex CAUGHT species
 * (as national dex numbers — PokeRogue starter ids; regional-form starters activate at base
 * species by ruling) into {@code pokeroguedb.bridgeDexWhitelist}, where the patched
 * rogueserver's glimpse filter reads them. The whitelist is append-only by design — a catch
 * cannot be un-caught — so rows are only ever inserted, never updated or deleted.
 *
 * <p>Three feed paths (plan §2.49): the capture event (activation within one poll of the
 * catch), login (a fresh snapshot the moment a linked player appears — also what makes
 * "no grandfathering" concrete: on enable the whitelist simply becomes the dex as it stands),
 * and a periodic reconcile that sweeps every other CAUGHT source (starter chosen, evolution,
 * hatch, trade) without hooking each Cobblemon event individually.
 *
 * <p>Threading: dex snapshots are read on the server main thread (Cobblemon instanced player
 * data); diffs and DB writes happen on the poller thread — the only thread allowed to touch
 * {@link PokerogueDb}. {@code pushedByUser} and {@code accountUuidCache} are poller-thread-only.
 *
 * <p>Entirely inert unless {@code dexLockedDreams} is true in config.json (§2.49 rollout gate);
 * the enforcement itself lives rogueserver-side, this class only keeps the whitelist fed.
 */
public final class DexFeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");

    private final MinecraftServer server;
    private final BridgeConfig config;
    private final LinkStore links;
    private final PokerogueDb db;
    private final DbPoller poller;

    /** Ids already confirmed written, per lowercased username. Poller thread only. */
    private final Map<String, Set<Integer>> pushedByUser = new HashMap<>();
    /** rogueserver account uuid cache, per lowercased username. Poller thread only. */
    private final Map<String, byte[]> accountUuidCache = new HashMap<>();
    private boolean warnedFeedFailing = false;

    public DexFeeder(MinecraftServer server, BridgeConfig config, LinkStore links, PokerogueDb db,
                     DbPoller poller) {
        this.server = server;
        this.config = config;
        this.links = links;
        this.db = db;
        this.poller = poller;
    }

    /** Schedules the reconcile sweep; call only when {@code dexLockedDreams} is enabled. */
    public void start() {
        long period = Math.max(1, config.pollSeconds);
        poller.scheduleRepeating(this::reconcile, period);
        LOGGER.info("dex whitelist feeder started (dexLockedDreams on, reconcile every {}s)", period);
    }

    /** Static event hooks route through the live services — guarded once per JVM like the presentation layer. */
    private static boolean subscribed = false;

    public static void init() {
        if (subscribed) return;
        subscribed = true;
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, DexFeeder::onCaptured);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> onLogin(e));
    }

    /** Main thread (Cobblemon capture flow). The event carries the species — no dex read needed. */
    private static void onCaptured(PokemonCapturedEvent event) {
        BridgeServices svc = CobblemonPokerogueBridge.services();
        if (svc == null || !svc.config().dexLockedDreams || svc.dexFeeder() == null) return;
        String username = svc.links().usernameFor(event.getPlayer().getUUID());
        if (username == null) return;
        Set<Integer> ids = new HashSet<>();
        addWithPreEvolutions(ids, event.getPokemon().getSpecies());
        if (ids.isEmpty()) return;
        String key = username.toLowerCase(Locale.ROOT);
        svc.poller().submit(() -> svc.dexFeeder().push(key, ids));
    }

    /** Main thread (NeoForge login event). */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BridgeServices svc = CobblemonPokerogueBridge.services();
        if (svc == null || !svc.config().dexLockedDreams || svc.dexFeeder() == null) return;
        String username = svc.links().usernameFor(player.getUUID());
        if (username == null) return;
        Set<Integer> caught = snapshotCaught(player);
        if (caught.isEmpty()) return;
        String key = username.toLowerCase(Locale.ROOT);
        svc.poller().submit(() -> svc.dexFeeder().push(key, caught));
    }

    /** MAIN THREAD ONLY: the linked player's CAUGHT species as national dex numbers. */
    private static Set<Integer> snapshotCaught(ServerPlayer player) {
        Set<Integer> out = new HashSet<>();
        PokedexManager dex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
        for (Map.Entry<ResourceLocation, SpeciesDexRecord> e : dex.getSpeciesRecords().entrySet()) {
            if (!e.getValue().hasAtLeast(PokedexEntryProgress.CAUGHT)) continue;
            addWithPreEvolutions(out, PokemonSpecies.getByIdentifier(e.getKey()));
        }
        return out;
    }

    /**
     * Adds the species' national dex number AND its whole pre-evolution chain. PokeRogue's
     * own catch semantics mark every pre-evolution caught (the starter unlock lands on the
     * line root), so a server catch of an evolved form must activate the same line or the
     * root starter would stay masked. Hop cap guards against cyclic species data.
     */
    private static void addWithPreEvolutions(Set<Integer> out, Species species) {
        Species cur = species;
        for (int hops = 0; cur != null && hops < 6; hops++) {
            if (cur.getNationalPokedexNumber() > 0) out.add(cur.getNationalPokedexNumber());
            var pre = cur.getPreEvolution();
            cur = pre == null ? null : pre.getSpecies();
        }
    }

    /**
     * Poller thread. Sweeps every ONLINE linked player: snapshot on the main thread, then the
     * diff+write hops back to the poller thread. Offline players need no sweep — their dex
     * cannot change, and login re-feeds them.
     */
    private void reconcile() {
        Map<String, LinkStore.Entry> linked = links.byUsernameLower();
        pushedByUser.keySet().retainAll(linked.keySet());
        accountUuidCache.keySet().retainAll(linked.keySet());
        if (linked.isEmpty()) return;
        server.execute(() -> {
            Map<String, Set<Integer>> snapshots = new HashMap<>();
            for (Map.Entry<String, LinkStore.Entry> e : linked.entrySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getValue().mcId());
                if (p == null) continue;
                Set<Integer> caught = snapshotCaught(p);
                if (!caught.isEmpty()) snapshots.put(e.getKey(), caught);
            }
            if (!snapshots.isEmpty()) poller.submit(() -> snapshots.forEach(this::push));
        });
    }

    /**
     * Poller thread. Inserts ids not yet confirmed written. On any failure nothing is marked
     * pushed, so the next capture/login/reconcile retries — INSERT IGNORE makes retries free.
     */
    private void push(String usernameLower, Set<Integer> caught) {
        Set<Integer> pushed = pushedByUser.computeIfAbsent(usernameLower, k -> new HashSet<>());
        List<Integer> fresh = caught.stream().filter(id -> !pushed.contains(id)).toList();
        if (fresh.isEmpty()) return;
        try {
            byte[] uuid = accountUuidCache.get(usernameLower);
            if (uuid == null) {
                uuid = db.lookupAccountUuid(usernameLower);
                if (uuid == null) return; // linked but unregistered account; reconcile retries
                accountUuidCache.put(usernameLower, uuid);
            }
            if (db.syncDexWhitelist(uuid, fresh)) {
                pushed.addAll(fresh);
                if (warnedFeedFailing) {
                    LOGGER.info("dex whitelist feed is writing again");
                    warnedFeedFailing = false;
                }
            }
        } catch (SQLException e) {
            db.invalidate();
            if (!warnedFeedFailing) {
                LOGGER.warn("dex whitelist feed failing ({}); will keep retrying quietly", e.getMessage());
                warnedFeedFailing = true;
            }
        }
    }
}
