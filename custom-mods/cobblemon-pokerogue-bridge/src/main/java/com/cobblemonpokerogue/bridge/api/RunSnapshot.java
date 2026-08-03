package com.cobblemonpokerogue.bridge.api;

/**
 * A point-in-time view of a linked player's PokeRogue run.
 *
 * <p>{@code slot} and {@code wave} are {@code -1} when unknown — notably on
 * {@link RunEventListener#onMilestone} events that fire while no run is active, and in
 * degraded mode (rogueserver without the bridgeRunState patch). {@code leadSpecies} is
 * PokeRogue's numeric SpeciesId rendered as a decimal string (e.g. {@code "25"};
 * 2xxx/4xxx/6xxx/8xxx prefixes are regional forms), or empty when unknown.
 * {@code party} is the whole lineup in order as a CSV of those ids ({@code "25,6,150"}),
 * or empty when unknown (degraded mode, or rogueserver predating the party column).
 * {@code gameMode} is one of {@code "classic"}, {@code "endless"},
 * {@code "spliced_endless"}, {@code "daily"}, {@code "challenge"} — or empty when unknown.
 */
public record RunSnapshot(java.util.UUID mcPlayerId, String pokerogueUsername, int slot, int wave,
                          String leadSpecies, String party, String gameMode) {}
