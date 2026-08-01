package com.cobblemonpokerogue.bridge.api;

/**
 * Presentation-side hook for PokeRogue run lifecycle events. All callbacks fire on the
 * SERVER MAIN THREAD (the core dispatches via {@code server.execute()}), so listeners may
 * touch world/player state directly. All methods default to no-ops — implement what you need.
 */
public interface RunEventListener {
    default void onRunStarted(RunSnapshot s) {}
    default void onWaveProgress(RunSnapshot s, int previousWave) {}
    default void onRunEnded(RunSnapshot s, RunEndSummary summary) {}
    default void onMilestone(RunSnapshot s, Milestone m) {}

    /**
     * Fired during the silent boot baseline for a run whose last save is recent enough to be
     * considered live. Presentation may quietly restore per-player state (tab suffix, ghost);
     * nothing should be announced — server restarts must not repeat dreams in progress.
     */
    default void onRunResumed(RunSnapshot s) {}
}
