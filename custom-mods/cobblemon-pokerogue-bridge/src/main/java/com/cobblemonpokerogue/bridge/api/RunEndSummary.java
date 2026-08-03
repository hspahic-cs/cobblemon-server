package com.cobblemonpokerogue.bridge.api;

/**
 * How a run ended. {@code finalWave} is the last wave the bridge observed for the run
 * ({@code -1} if it never read one); {@code maxObservedWave} is the deepest wave any poll
 * ever saw for it — normally equal to {@code finalWave} since the server rejects wave
 * regressions, but kept separately so payouts key off depth reached, not the last write
 * (also {@code -1} when never read, e.g. degraded mode). {@code victory} is true when the
 * account's {@code sessionsWon} counter advanced in the same poll the run ended.
 */
public record RunEndSummary(int finalWave, int maxObservedWave, boolean victory) {}
