package com.cobblemonpokerogue.bridge.milestones;

import com.cobblemonpokerogue.bridge.api.BridgeEventsInternal;
import com.cobblemonpokerogue.bridge.api.Milestone;
import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Threshold milestones over accountStats counters.
 *
 * <p>{@code milestones.json} is CONTENT — the human authors it (see this module's README and
 * {@code milestones.example.json}); this class is only the mechanism. Design rule carried
 * from plan §2.44: milestones only, never raw web-side quantities — the web save is
 * client-trusting, so a cheated save can at worst skip to a milestone cap, not print money.
 *
 * <p>A milestone is granted when the account's counter is {@code >= threshold} and the id was
 * never granted before (per PokeRogue account, persisted in {@code state.json} together with
 * pending unclaimed ids and the last-seen stat values). Granting records a PENDING claim and
 * fires {@code onMilestone}; the reward commands only run when the player runs
 * {@code /pokerogue claim} — rewards are claimed, never auto-mailed (§2.44 immersion ruling:
 * claiming is the shrine moment). Stats already past a threshold at link time grant
 * immediately: they were earned, and the granted-set makes it once-ever.
 *
 * <p>Two §2.45 additions share this engine's state and claim flow:
 * <ul>
 *   <li>{@code maxClassicWave} — a bridge-side VIRTUAL stat (accountStats has no classic-depth
 *       column): the deepest classic wave ever observed for the account, fed by
 *       {@link #observeClassicWave} and injected into every {@link #evaluate} so
 *       milestones.json can reference it exactly like a real column.</li>
 *   <li>Ad-hoc payout claims ({@link #enqueuePayout}) — repeatable per-run payouts whose defs
 *       are not in milestones.json, persisted whole (display + reward commands) in state.json
 *       and popped by the same {@code /pokerogue claim}.</li>
 * </ul>
 */
public final class MilestoneEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record MilestoneDef(String id, String stat, long threshold, int tier, String display,
                               List<String> rewards) {}

    /** The name milestones.json uses to reference the virtual classic-depth stat. */
    public static final String STAT_MAX_CLASSIC_WAVE = "maxClassicWave";

    private static final class AccountState {
        final Set<String> granted = new LinkedHashSet<>();
        final List<String> pending = new ArrayList<>();
        /** Ad-hoc claims (per-run payouts) persisted whole — their defs are not in milestones.json. */
        final List<MilestoneDef> pendingPayouts = new ArrayList<>();
        Map<String, Long> lastStats = new HashMap<>();
        long maxClassicWave = -1;
    }

    private final Path stateFile;
    private final List<MilestoneDef> defs;
    private final Map<String, AccountState> accounts = new HashMap<>(); // key: lowercased username

    private MilestoneEngine(Path stateFile, List<MilestoneDef> defs) {
        this.stateFile = stateFile;
        this.defs = defs;
    }

    public static MilestoneEngine load(Path milestonesFile, Path stateFile) {
        List<MilestoneDef> defs = new ArrayList<>();
        if (Files.exists(milestonesFile)) {
            try {
                JsonArray arr = JsonParser.parseString(Files.readString(milestonesFile, StandardCharsets.UTF_8))
                        .getAsJsonArray();
                for (JsonElement e : arr) {
                    MilestoneDef def = parseDef(e);
                    if (def != null) defs.add(def);
                }
                LOGGER.info("loaded {} milestone(s) from {}", defs.size(), milestonesFile.getFileName());
            } catch (IOException | RuntimeException e) {
                LOGGER.error("milestones.json is malformed — milestone engine idle until fixed", e);
                defs.clear();
            }
        } else {
            LOGGER.info("no milestones.json — milestone engine idle; author one from milestones.example.json");
        }
        MilestoneEngine engine = new MilestoneEngine(stateFile, List.copyOf(defs));
        engine.loadState();
        return engine;
    }

    private static MilestoneDef parseDef(JsonElement e) {
        try {
            JsonObject o = e.getAsJsonObject();
            // Comment convention (mirrors the lang file): an entry with "_" and no "id" is
            // documentation, skipped silently.
            if (o.has("_") && !o.has("id")) return null;
            String id = o.get("id").getAsString();
            String stat = o.get("stat").getAsString();
            long threshold = o.get("threshold").getAsLong();
            int tier = o.has("tier") ? o.get("tier").getAsInt() : 1;
            if (tier < 1 || tier > 3) tier = Math.min(3, Math.max(1, tier));
            String display = o.has("display") ? o.get("display").getAsString() : id;
            List<String> rewards = new ArrayList<>();
            for (JsonElement r : o.getAsJsonArray("rewards")) rewards.add(r.getAsString());
            if (id.isEmpty() || stat.isEmpty()) return null;
            return new MilestoneDef(id, stat, threshold, tier, display, List.copyOf(rewards));
        } catch (RuntimeException bad) {
            LOGGER.warn("skipping malformed milestone entry: {}", e);
            return null;
        }
    }

    /**
     * Called by the poller for each linked account each cycle. {@code activeRun} may be null;
     * milestone events outside a run carry a synthetic snapshot (slot/wave -1).
     */
    public synchronized void evaluate(MinecraftServer server, LinkStore.Entry link,
                                      Map<String, Long> stats, RunSnapshot activeRun) {
        String key = link.username().toLowerCase(Locale.ROOT);
        AccountState st = accounts.computeIfAbsent(key, k -> new AccountState());
        // Inject the virtual stat (copy — the caller owns the map) so milestone defs can
        // reference maxClassicWave exactly like an accountStats column.
        if (st.maxClassicWave >= 0) {
            stats = new HashMap<>(stats);
            stats.put(STAT_MAX_CLASSIC_WAVE, st.maxClassicWave);
        }
        boolean dirty = false;
        for (MilestoneDef def : defs) {
            if (st.granted.contains(def.id())) continue;
            if (stats.getOrDefault(def.stat(), 0L) < def.threshold()) continue;
            st.granted.add(def.id());
            st.pending.add(def.id());
            dirty = true;
            LOGGER.info("milestone '{}' reached by {} (pending claim)", def.id(), link.username());
            RunSnapshot s = activeRun != null ? activeRun
                    : new RunSnapshot(link.mcId(), link.username(), -1, -1, "", "");
            BridgeEventsInternal.fireMilestone(server, s, new Milestone(def.id(), def.display(), def.tier()));
        }
        if (!stats.equals(st.lastStats)) {
            st.lastStats = new HashMap<>(stats);
            dirty = true;
        }
        if (dirty) saveState();
    }

    /**
     * Feeds the {@code maxClassicWave} virtual stat: monotonic, persisted, per account.
     * Called by the poller from live classic-run waves and by the payout engine at run end.
     */
    public synchronized void observeClassicWave(String username, int wave) {
        if (wave < 0) return;
        AccountState st = accounts.computeIfAbsent(username.toLowerCase(Locale.ROOT), k -> new AccountState());
        if (wave <= st.maxClassicWave) return;
        st.maxClassicWave = wave;
        saveState();
    }

    /**
     * Enqueues a repeatable ad-hoc claim (§2.45 per-run payout) for {@code /pokerogue claim}.
     * The whole def is persisted in state.json — it does not exist in milestones.json.
     */
    public synchronized void enqueuePayout(String username, MilestoneDef payout) {
        AccountState st = accounts.computeIfAbsent(username.toLowerCase(Locale.ROOT), k -> new AccountState());
        st.pendingPayouts.add(payout);
        LOGGER.info("payout '{}' enqueued for {} (pending claim)", payout.id(), username);
        saveState();
    }

    public synchronized int pendingCount(String username) {
        AccountState st = accounts.get(username.toLowerCase(Locale.ROOT));
        return st == null ? 0 : st.pending.size() + st.pendingPayouts.size();
    }

    /**
     * Pops all pending claims for an account, resolving ids to their current defs. Ids whose
     * def was removed from milestones.json since being earned are dropped with a warning.
     */
    public synchronized List<MilestoneDef> takePending(String username) {
        AccountState st = accounts.get(username.toLowerCase(Locale.ROOT));
        if (st == null || (st.pending.isEmpty() && st.pendingPayouts.isEmpty())) return List.of();
        List<MilestoneDef> out = new ArrayList<>();
        for (String id : st.pending) {
            MilestoneDef def = defs.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
            if (def == null) {
                LOGGER.warn("pending milestone '{}' no longer exists in milestones.json — dropped", id);
            } else {
                out.add(def);
            }
        }
        out.addAll(st.pendingPayouts); // ad-hoc payouts carry their own defs
        st.pending.clear();
        st.pendingPayouts.clear();
        saveState();
        return out;
    }

    public synchronized void save() {
        saveState();
    }

    // ---- state.json ----------------------------------------------------------------------

    private void loadState() {
        if (!Files.exists(stateFile)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(stateFile, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject accs = root.getAsJsonObject("accounts");
            if (accs == null) return;
            for (Map.Entry<String, JsonElement> e : accs.entrySet()) {
                AccountState st = new AccountState();
                JsonObject o = e.getValue().getAsJsonObject();
                if (o.has("granted")) o.getAsJsonArray("granted").forEach(g -> st.granted.add(g.getAsString()));
                if (o.has("pending")) o.getAsJsonArray("pending").forEach(p -> st.pending.add(p.getAsString()));
                if (o.has("maxClassicWave")) st.maxClassicWave = o.get("maxClassicWave").getAsLong();
                if (o.has("pendingPayouts")) {
                    for (JsonElement p : o.getAsJsonArray("pendingPayouts")) {
                        JsonObject po = p.getAsJsonObject();
                        List<String> rewards = new ArrayList<>();
                        po.getAsJsonArray("rewards").forEach(r -> rewards.add(r.getAsString()));
                        st.pendingPayouts.add(new MilestoneDef(
                                po.get("id").getAsString(), "", 0,
                                po.has("tier") ? po.get("tier").getAsInt() : 1,
                                po.get("display").getAsString(), List.copyOf(rewards)));
                    }
                }
                if (o.has("lastStats")) {
                    for (Map.Entry<String, JsonElement> s : o.getAsJsonObject("lastStats").entrySet()) {
                        st.lastStats.put(s.getKey(), s.getValue().getAsLong());
                    }
                }
                accounts.put(e.getKey().toLowerCase(Locale.ROOT), st);
            }
        } catch (IOException | RuntimeException e) {
            // Refuse to run with a corrupt state file: an empty granted-set would re-grant
            // (and re-pay) every milestone to every account.
            throw new IllegalStateException("state.json is unreadable; fix or remove it: " + stateFile, e);
        }
    }

    private void saveState() {
        JsonObject accs = new JsonObject();
        for (Map.Entry<String, AccountState> e : accounts.entrySet()) {
            JsonObject o = new JsonObject();
            JsonArray granted = new JsonArray();
            e.getValue().granted.forEach(granted::add);
            JsonArray pending = new JsonArray();
            e.getValue().pending.forEach(pending::add);
            JsonObject lastStats = new JsonObject();
            e.getValue().lastStats.forEach(lastStats::addProperty);
            JsonArray pendingPayouts = new JsonArray();
            for (MilestoneDef p : e.getValue().pendingPayouts) {
                JsonObject po = new JsonObject();
                po.addProperty("id", p.id());
                po.addProperty("display", p.display());
                po.addProperty("tier", p.tier());
                JsonArray rewards = new JsonArray();
                p.rewards().forEach(rewards::add);
                po.add("rewards", rewards);
                pendingPayouts.add(po);
            }
            o.add("granted", granted);
            o.add("pending", pending);
            o.add("pendingPayouts", pendingPayouts);
            if (e.getValue().maxClassicWave >= 0) o.addProperty("maxClassicWave", e.getValue().maxClassicWave);
            o.add("lastStats", lastStats);
            accs.add(e.getKey(), o);
        }
        JsonObject root = new JsonObject();
        root.add("accounts", accs);
        try {
            Files.writeString(stateFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("failed to write {}", stateFile, e);
        }
    }
}
