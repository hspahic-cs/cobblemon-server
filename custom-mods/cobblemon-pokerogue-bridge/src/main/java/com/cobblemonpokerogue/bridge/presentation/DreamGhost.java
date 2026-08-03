package com.cobblemonpokerogue.bridge.presentation;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.cobblemonpokerogue.bridge.api.RunEndSummary;
import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dream ghosts at the shrine: while a run is active, a static, uncatchable, no-AI
 * {@link PokemonEntity} of the runner's current lead stands near the configured shrine with a
 * nameplate ("X's dream — wave N"). The species swaps when the lead changes, the nameplate
 * updates each wave, and the ghost is discarded on run end and on server stop.
 *
 * <p>Ghosts must never survive on disk: every ghost carries a command tag, and any tagged entity
 * that joins a level {@code loadedFromDisk} (a crash left it behind) is discarded on sight. That
 * sweeper runs even when the feature is disabled, so leftovers from a previously-enabled config
 * still get cleaned up.
 *
 * <p>Registered on the NeoForge event bus.
 */
public final class DreamGhost {
    private static final Logger LOG = LoggerFactory.getLogger("pokerogue-bridge");
    private static final String GHOST_TAG = "pokerogue_dream_ghost";

    private final Anchors anchors;
    private final DreamLang lang;
    /** player UUID -> live ghost; only touched on the server main thread (contract guarantee). */
    private final Map<UUID, GhostRef> ghosts = new HashMap<>();

    private record GhostRef(UUID entityUuid, @Nullable UUID labelUuid, String speciesId) {}

    DreamGhost(Anchors anchors, DreamLang lang) {
        this.anchors = anchors;
        this.lang = lang;
    }

    private boolean active() {
        return anchors.shrine() != null;
    }

    void onRunStartedOrProgress(RunSnapshot s) {
        if (!active()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? shrineLevel(server) : null;
        if (level == null) {
            return;
        }
        Species species = RogueSpecies.resolve(s.leadSpecies());
        String speciesKey = species == null ? "" : species.getName();
        String nameplate = lang.format("pokerogue.presentation.ghost.nameplate",
                DreamAnnouncer.playerName(server, s), s.wave());

        GhostRef ref = ghosts.get(s.mcPlayerId());
        if (ref != null) {
            Entity existing = level.getEntity(ref.entityUuid());
            if (existing != null && ref.speciesId().equals(speciesKey)) {
                placeInRing(level, existing); // shimmer to a new spot in the dream ring
                Entity label = ref.labelUuid() == null ? null : level.getEntity(ref.labelUuid());
                if (label != null) {
                    WallKit.retext(level, label, Component.literal(nameplate));
                    moveLabelTo(label, existing);
                }
                return;
            }
            if (existing != null) {
                existing.discard(); // lead changed: swap species
            }
            discardLabel(level, ref);
            ghosts.remove(s.mcPlayerId());
        }
        PokemonEntity ghost = spawn(level, s, species);
        if (ghost != null) {
            // The dream label is a free-standing text display kept in lockstep with the ghost
            // (riding a PokemonEntity fights Cobblemon 1.7's mount logic and desyncs) —
            // Cobblemon's own overhead label spoiler-guards unseen species, so it is hidden.
            Entity label = WallKit.spawnText(level, ghost.getX(),
                    ghost.getY() + ghost.getBbHeight() + 0.4, ghost.getZ(),
                    0.0f, 1.0f, 0, "center",
                    Component.literal(nameplate).withStyle(net.minecraft.ChatFormatting.AQUA),
                    GHOST_TAG);
            ghosts.put(s.mcPlayerId(), new GhostRef(ghost.getUUID(),
                    label == null ? null : label.getUUID(), speciesKey));
        }
    }

    private static void moveLabelTo(Entity label, Entity ghost) {
        label.moveTo(ghost.getX(), ghost.getY() + ghost.getBbHeight() + 0.4, ghost.getZ(), 0.0f, 0.0f);
    }

    private static void discardLabel(ServerLevel level, GhostRef ref) {
        if (ref.labelUuid() != null) {
            Entity label = level.getEntity(ref.labelUuid());
            if (label != null) {
                label.discard();
            }
        }
    }

    void onRunEnded(RunSnapshot s, RunEndSummary summary) {
        GhostRef ref = ghosts.remove(s.mcPlayerId());
        if (ref == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? shrineLevel(server) : null;
        if (level != null) {
            Entity existing = level.getEntity(ref.entityUuid());
            if (existing != null) {
                existing.discard();
            }
            discardLabel(level, ref);
        }
    }

    @Nullable
    private PokemonEntity spawn(ServerLevel level, RunSnapshot s, @Nullable Species species) {
        if (species == null) {
            LOG.warn("dream ghost: could not resolve lead species '{}', skipping ghost for {}",
                    s.leadSpecies(), s.pokerogueUsername());
            return null;
        }
        try {
            Pokemon pokemon = new Pokemon();
            pokemon.setSpecies(species);
            PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
            UncatchableProperty.INSTANCE.uncatchable().apply(entity);
            entity.hideNameRendering(); // "??? Lv. 1" begone — the label rides above instead
            entity.setNoAi(true);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistenceRequired(); // no natural despawn
            entity.addTag(GHOST_TAG);        // disk-load sweeper marker
            entity.setGlowingTag(true);      // the spectral outline that marks it as a ghost

            // Ghosts materialize anywhere inside the shrine's dream ring; every wave update
            // drifts them to a new spot (see drift), so simultaneous dreamers shimmer around
            // the area rather than standing in fixed slots.
            placeInRing(level, entity);

            if (!level.addFreshEntity(entity)) {
                LOG.warn("dream ghost: level rejected the ghost entity for {}", s.pokerogueUsername());
                return null;
            }
            return entity;
        } catch (RuntimeException e) {
            LOG.warn("dream ghost: failed to spawn '{}' for {}", species.getName(), s.pokerogueUsername(), e);
            return null;
        }
    }

    /** A fresh random spot inside the dream ring (annulus 1.5..radius), facing the shrine heart. */
    private void placeInRing(ServerLevel level, Entity entity) {
        PresentationConfig.ShrinePos shrine = anchors.shrine();
        if (shrine == null) {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double dist = 1.5 + level.random.nextDouble() * Math.max(0.5, shrine.radius() - 1.5);
        double x = shrine.x() + Math.cos(angle) * dist;
        double z = shrine.z() + Math.sin(angle) * dist;
        float yaw = (float) (Math.toDegrees(Math.atan2(shrine.z() - z, shrine.x() - x)) - 90.0);
        entity.moveTo(x, shrine.y(), z, yaw, 0.0f);
    }

    /** Ghosts are ephemeral: anything tagged that comes back from disk is a leftover — discard it. */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(GHOST_TAG)) {
            event.setCanceled(true);
            event.getEntity().discard();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ServerLevel level = shrineLevel(event.getServer());
        if (level != null) {
            for (GhostRef ref : ghosts.values()) {
                Entity existing = level.getEntity(ref.entityUuid());
                if (existing != null) {
                    existing.discard();
                }
                discardLabel(level, ref);
            }
        }
        ghosts.clear();
    }

    @Nullable
    private ServerLevel shrineLevel(MinecraftServer server) {
        PresentationConfig.ShrinePos shrine = anchors.shrine();
        if (shrine == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(shrine.dimension());
        if (id == null) {
            LOG.warn("dream ghost: bad shrine dimension id '{}'", shrine.dimension());
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            LOG.warn("dream ghost: shrine dimension '{}' is not loaded", shrine.dimension());
        }
        return level;
    }

    // Kept for API clarity: PokemonProperties is the documented alternate spawn path should the
    // direct-constructor route regress in a Cobblemon update. See module report.
    @SuppressWarnings("unused")
    private static PokemonEntity spawnViaProperties(ServerLevel level, String speciesId) {
        return PokemonProperties.Companion.parse(speciesId).createEntity(level);
    }
}
