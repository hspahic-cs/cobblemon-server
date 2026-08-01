package com.cobblemonpokerogue.bridge.poll;

import com.cobblemonpokerogue.bridge.api.BridgeEventsInternal;
import com.cobblemonpokerogue.bridge.api.RunEndSummary;
import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import com.cobblemonpokerogue.bridge.db.PokerogueDb;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/**
 * Derives PokeRogue run lifecycle from the DB poll and fires the {@code api} events.
 *
 * <h2>Lifecycle rules (verified against rogueserver source + live binary; see
 * docs/pokerogue-session-schema.md)</h2>
 * <ul>
 *   <li><b>Run START</b> — a new {@code (uuid, slot)} sessionSaveData row appears (the wave-1
 *       write is immediate), or the {@code seed} changes on an existing slot (the old run was
 *       deleted and a new one started between two polls; we synthesize the end, then the
 *       start). Note {@code classicSessionsPlayed} bumps at run START, so it is useless as an
 *       end signal and is not used here.</li>
 *   <li><b>PROGRESS</b> — the row's timestamp (or bridgeRunState's updatedAt/waveIndex)
 *       advances; the client saves on waves %5==1 and every 300s of play.</li>
 *   <li><b>VICTORY</b> — the session row is deleted AND either a dailyRunCompletions row
 *       exists for the run's seed (written server-side on /savedata/session/clear — records
 *       CLASSIC wins too, despite the name) or accountStats.sessionsWon advanced this poll.</li>
 *   <li><b>DEFEAT</b> — the session row is deleted with neither victory signal. This only
 *       happens after the player confirms game-over; an ABANDONED run just leaves a dormant
 *       row, so staleness is deliberately never used to infer an end.</li>
 *   <li><b>Known ambiguity (accepted for v1)</b> — a manual save-slot delete is
 *       indistinguishable from a defeat.</li>
 * </ul>
 *
 * <h2>Baseline and degraded mode</h2>
 * The first successful poll after boot seeds all existing rows SILENTLY (pre-existing saves
 * are not new runs); a seeded run goes live — firing {@code onRunStarted} — on its next
 * progress, which also means an MC-server restart mid-run re-announces that run on its next
 * save. When bridgeRunState is absent (unpatched rogueserver) the tracker runs DEGRADED:
 * start/end still fire from row existence, but wave stays -1, species/mode stay empty, no
 * {@code onWaveProgress} is emitted, and seed-change detection is unavailable.
 *
 * <p>All methods are called from the single poller thread; {@code synchronized} guards the
 * occasional read from other threads (e.g. {@link #activeSnapshot}).
 */
public final class RunTracker {

    /** Victory probe, backed by {@link PokerogueDb#hasCompletion}. */
    public interface VictoryCheck {
        boolean hasCompletion(String username, String seed);
    }

    private static final class Tracked {
        final String userLower;
        final int slot;
        String seed;            // null until a bridgeRunState row is seen (always null when degraded)
        long timestampMs;       // sessionSaveData.timestamp
        long detailMs;          // bridgeRunState.updatedAt
        int wave = -1;
        int leadSpecies = -1;
        String gameMode = "";
        boolean live;           // onRunStarted fired and no end fired yet

        Tracked(String userLower, int slot) {
            this.userLower = userLower;
            this.slot = slot;
        }
    }

    private final Map<String, Tracked> tracked = new HashMap<>();
    private boolean baselined = false;

    public synchronized void process(MinecraftServer server,
                                     Map<String, LinkStore.Entry> links,
                                     List<PokerogueDb.SessionHeader> headers,
                                     List<PokerogueDb.RunStateRow> runStates,
                                     Set<String> wonThisPoll,
                                     VictoryCheck victoryCheck) {
        boolean baseline = !baselined;
        Map<String, PokerogueDb.RunStateRow> detail = new HashMap<>();
        if (runStates != null) {
            for (PokerogueDb.RunStateRow r : runStates) detail.put(key(r.usernameLower(), r.slot()), r);
        }

        // Pass 1: rows present — starts, seed-change turnovers, progress.
        Map<String, PokerogueDb.SessionHeader> present = new HashMap<>();
        for (PokerogueDb.SessionHeader h : headers) {
            LinkStore.Entry link = links.get(h.usernameLower());
            if (link == null) continue;
            String key = key(h.usernameLower(), h.slot());
            present.put(key, h);
            PokerogueDb.RunStateRow d = detail.get(key);
            Tracked t = tracked.get(key);
            if (t == null) {
                t = new Tracked(h.usernameLower(), h.slot());
                applyDetail(t, d);
                t.timestampMs = h.timestampMs();
                tracked.put(key, t);
                if (!baseline) {
                    t.live = true;
                    BridgeEventsInternal.fireRunStarted(server, snapshot(t, link));
                }
                continue;
            }
            if (d != null && t.seed != null && d.seed() != null && !t.seed.equals(d.seed())) {
                // Seed changed on an existing slot: old run ended and a new one began between polls.
                if (t.live) {
                    boolean victory = isVictory(link, t.seed, wonThisPoll, victoryCheck);
                    BridgeEventsInternal.fireRunEnded(server, snapshot(t, link), new RunEndSummary(t.wave, victory));
                }
                Tracked fresh = new Tracked(t.userLower, t.slot);
                applyDetail(fresh, d);
                fresh.timestampMs = h.timestampMs();
                fresh.live = true;
                tracked.put(key, fresh);
                BridgeEventsInternal.fireRunStarted(server, snapshot(fresh, link));
                continue;
            }
            boolean advanced = h.timestampMs() > t.timestampMs
                    || (d != null && (d.updatedAtMs() > t.detailMs || d.waveIndex() != t.wave));
            int prevWave = t.wave;
            applyDetail(t, d);
            if (h.timestampMs() > t.timestampMs) t.timestampMs = h.timestampMs();
            if (advanced) {
                if (!t.live) {
                    // Baseline-seeded (or turned-over) save progressing again = the run is live.
                    t.live = true;
                    BridgeEventsInternal.fireRunStarted(server, snapshot(t, link));
                } else if (t.wave >= 0 && prevWave >= 0 && t.wave != prevWave) {
                    BridgeEventsInternal.fireWaveProgress(server, snapshot(t, link), prevWave);
                }
                // Degraded mode: wave stays -1, so no onWaveProgress spam — by design.
            }
        }

        // Pass 2: rows gone — run endings (victory vs defeat), plus unlink cleanup.
        Iterator<Map.Entry<String, Tracked>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Tracked> e = it.next();
            if (present.containsKey(e.getKey())) continue;
            Tracked t = e.getValue();
            LinkStore.Entry link = links.get(t.userLower);
            it.remove();
            if (link == null) continue; // account unlinked — drop silently
            if (t.live) {
                boolean victory = isVictory(link, t.seed, wonThisPoll, victoryCheck);
                BridgeEventsInternal.fireRunEnded(server, snapshot(t, link), new RunEndSummary(t.wave, victory));
            }
            // A never-live row deleted (dormant baseline save) is a manual slot delete or a
            // run whose entire life fit between two polls — ends silently, accepted for v1.
        }

        baselined = true;
    }

    /**
     * The most recently updated LIVE run for an account, or null — used to attach milestone
     * events to the run they most plausibly happened in.
     */
    public synchronized RunSnapshot activeSnapshot(String usernameLower, LinkStore.Entry link) {
        Tracked best = null;
        for (Tracked t : tracked.values()) {
            if (!t.live || !t.userLower.equals(usernameLower)) continue;
            if (best == null || t.timestampMs > best.timestampMs) best = t;
        }
        return best == null ? null : snapshot(best, link);
    }

    private static boolean isVictory(LinkStore.Entry link, String seed, Set<String> wonThisPoll,
                                     VictoryCheck victoryCheck) {
        if (seed != null && !seed.isEmpty() && victoryCheck.hasCompletion(link.username(), seed)) return true;
        return wonThisPoll.contains(link.username().toLowerCase(java.util.Locale.ROOT));
    }

    private static void applyDetail(Tracked t, PokerogueDb.RunStateRow d) {
        if (d == null) return;
        t.seed = d.seed();
        t.detailMs = d.updatedAtMs();
        t.wave = d.waveIndex();
        t.leadSpecies = d.leadSpecies();
        t.gameMode = gameModeName(d.gameMode());
    }

    private static RunSnapshot snapshot(Tracked t, LinkStore.Entry link) {
        return new RunSnapshot(link.mcId(), link.username(), t.slot, t.wave,
                t.leadSpecies >= 0 ? String.valueOf(t.leadSpecies) : "",
                t.gameMode == null ? "" : t.gameMode);
    }

    /** PokeRogue GameModes enum ordinals, rendered lowercase per the RunSnapshot contract. */
    private static String gameModeName(int mode) {
        return switch (mode) {
            case 0 -> "classic";
            case 1 -> "endless";
            case 2 -> "spliced_endless";
            case 3 -> "daily";
            case 4 -> "challenge";
            default -> "mode_" + mode;
        };
    }

    private static String key(String userLower, int slot) {
        return userLower + "#" + slot;
    }
}
