package com.cobblemonpokerogue.bridge.presentation;

import org.jetbrains.annotations.Nullable;

/**
 * Everything the presentation layer needs from the core config. The orchestrator adapts the
 * core module's config format to this record at merge.
 *
 * @param dreamGhostEnabled master toggle for the shrine dream-ghost feature; even when true the
 *                          feature stays dormant unless {@code shrinePos} is also configured
 * @param shrinePos         where the Dream Machine shrine is, or {@code null} if not configured
 * @param boardPos          where the leaderboard wall floats, or {@code null} if not configured
 * @param journalPos        where the personal journal wall renders, or {@code null}
 */
public record PresentationConfig(boolean dreamGhostEnabled, @Nullable ShrinePos shrinePos,
                                 @Nullable BoardPos boardPos, @Nullable BoardPos journalPos) {

    /**
     * @param dimension dimension id, e.g. {@code "minecraft:overworld"}
     * @param radius    dream ghosts drift anywhere within this many blocks of the anchor
     */
    public record ShrinePos(String dimension, double x, double y, double z, int radius) {}

    /**
     * @param x         anchor BLOCK the top row centers on (rows stack downward)
     * @param facing    which way the paintings face: {@code north|south|east|west}
     * @param size      how many leaderboard rows to show
     * @param scale     painting width in blocks (bubble height is half of this)
     * @param spriteDir PokéRogue party-icon tree, {@code <dir>/<gen>/<speciesId>.png}
     */
    public record BoardPos(String dimension, int x, int y, int z, String facing, int size,
                           float scale, String spriteDir) {}
}
