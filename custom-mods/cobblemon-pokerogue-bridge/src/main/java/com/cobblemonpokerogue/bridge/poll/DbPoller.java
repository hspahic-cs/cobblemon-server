package com.cobblemonpokerogue.bridge.poll;

import com.cobblemonpokerogue.bridge.BridgeConfig;
import com.cobblemonpokerogue.bridge.db.PokerogueDb;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import com.cobblemonpokerogue.bridge.milestones.MilestoneEngine;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single background poll thread. {@code scheduleAtFixedRate} keeps the cadence
 * jitter-free; every poll is wrapped so no exception can kill the schedule. All DB access in
 * the mod happens on this thread — command-triggered lookups (link validation) are
 * {@link #submit}ted onto the same executor, which makes {@link PokerogueDb}'s single
 * connection single-threaded by construction.
 *
 * <p>DB outages are warned about ONCE, then retried quietly every cycle; recovery is logged.
 */
public final class DbPoller implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");

    private final MinecraftServer server;
    private final BridgeConfig config;
    private final LinkStore links;
    private final PokerogueDb db;
    private final RunTracker tracker;
    private final MilestoneEngine milestones;

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cobblemon-pokerogue-bridge-poller");
        t.setDaemon(true);
        return t;
    });

    /** Previous sessionsWon per lowercased username — victory-corroboration deltas. */
    private final Map<String, Long> prevSessionsWon = new HashMap<>();
    private boolean dbDown = false;

    public DbPoller(MinecraftServer server, BridgeConfig config, LinkStore links, PokerogueDb db,
                    RunTracker tracker, MilestoneEngine milestones) {
        this.server = server;
        this.config = config;
        this.links = links;
        this.db = db;
        this.tracker = tracker;
        this.milestones = milestones;
    }

    public void start() {
        long period = Math.max(1, config.pollSeconds);
        exec.scheduleAtFixedRate(this::safePoll, period, period, TimeUnit.SECONDS);
        LOGGER.info("PokeRogue DB poller started (every {}s)", period);
    }

    /** Run a task on the poller thread — the only thread allowed to touch {@link PokerogueDb}. */
    public void submit(Runnable task) {
        exec.execute(task);
    }

    @Override
    public void close() {
        exec.shutdownNow();
    }

    private void safePoll() {
        try {
            pollOnce();
        } catch (Throwable t) {
            // scheduleAtFixedRate silently cancels on an uncaught throwable — never let one out.
            LOGGER.error("poll cycle failed unexpectedly", t);
        }
    }

    private void pollOnce() {
        Map<String, LinkStore.Entry> linked = links.byUsernameLower();
        prevSessionsWon.keySet().retainAll(linked.keySet());
        if (linked.isEmpty()) return;
        List<String> usernames = linked.values().stream().map(LinkStore.Entry::username).toList();
        try {
            Map<String, Map<String, Long>> stats = db.fetchStats(usernames);
            List<PokerogueDb.SessionHeader> headers = db.fetchSessionHeaders(usernames);
            List<PokerogueDb.RunStateRow> runStates = db.fetchRunStates(usernames); // null = degraded

            // sessionsWon deltas (victory corroboration). First sighting of an account only
            // baselines — a pre-existing win count is not a win that happened this poll.
            Set<String> wonThisPoll = new HashSet<>();
            for (Map.Entry<String, Map<String, Long>> e : stats.entrySet()) {
                long won = e.getValue().getOrDefault("sessionsWon", 0L);
                Long prev = prevSessionsWon.put(e.getKey(), won);
                if (prev != null && won > prev) wonThisPoll.add(e.getKey());
            }

            // stats keys are already lowercased usernames, so wonThisPoll is too.
            tracker.process(server, linked, headers, runStates, wonThisPoll, db::hasCompletion);

            // Feed the maxClassicWave virtual stat BEFORE evaluating, so a first-ever wave
            // milestone fires on the same poll that observed the crossing save.
            for (Map.Entry<String, Integer> e : tracker.liveClassicMaxWaves().entrySet()) {
                milestones.observeClassicWave(e.getKey(), e.getValue());
            }

            for (Map.Entry<String, Map<String, Long>> e : stats.entrySet()) {
                LinkStore.Entry link = linked.get(e.getKey());
                if (link == null) continue;
                milestones.evaluate(server, link, e.getValue(), tracker.activeSnapshot(e.getKey(), link));
            }

            if (dbDown) {
                LOGGER.info("PokeRogue DB is reachable again");
                dbDown = false;
            }
        } catch (SQLException e) {
            db.invalidate();
            if (!dbDown) {
                LOGGER.warn("PokeRogue DB unreachable ({}); will keep retrying quietly", e.getMessage());
                dbDown = true;
            }
        }
    }
}
