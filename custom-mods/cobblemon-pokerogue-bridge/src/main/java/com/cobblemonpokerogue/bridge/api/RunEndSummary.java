package com.cobblemonpokerogue.bridge.api;

/**
 * How a run ended. {@code finalWave} is the last wave the bridge observed for the run
 * ({@code -1} if it never read one); {@code victory} is true when the account's
 * {@code sessionsWon} counter advanced in the same poll the run ended.
 */
public record RunEndSummary(int finalWave, boolean victory) {}
