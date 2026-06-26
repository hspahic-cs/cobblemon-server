package com.cobblemonwilderness.gen;

/**
 * Server-global state read by the structure-placement mixin on the worldgen hot path.
 *
 * Configured once at server start (and again whenever a prune bumps the cycle salt) from the
 * Kotlin reset flow. Plain statics + volatiles on purpose: the mixin runs on chunk-gen worker
 * threads, must not allocate or lock, and only ever reads a handful of primitives written from
 * the main thread before generation begins.
 */
public final class WildernessGenState {
    private WildernessGenState() {}

    // active = feature enabled AND a non-zero cycle salt exists (i.e. at least one prune has run).
    private static volatile boolean active = false;
    private static volatile int cycleSalt = 0;
    // Keep-box in BLOCK coordinates, inclusive.
    private static volatile int boxMinX = 0, boxMinZ = 0, boxMaxX = 0, boxMaxZ = 0;

    /** Set the box + cycle salt and enable the hook. Called from the reset flow at boot. */
    public static void configure(boolean enabled, int salt, int minX, int minZ, int maxX, int maxZ) {
        boxMinX = minX;
        boxMinZ = minZ;
        boxMaxX = maxX;
        boxMaxZ = maxZ;
        cycleSalt = salt;
        active = enabled && salt != 0;
    }

    /** Turn the hook off (no relocation; placement is vanilla everywhere). */
    public static void disable() {
        active = false;
    }

    /**
     * Extra salt to XOR into a random-spread structure placement RNG for the grid cell
     * (cellX, cellZ) of the given spacing (in chunks). Returns 0 — leaving placement exactly
     * vanilla — when the feature is off or the cell touches the keep-box; returns the cycle salt
     * for a cell lying wholly in the wilderness, which deterministically relocates that cell's
     * structure for the cycle. Cheap: a few comparisons, no allocation.
     */
    public static int cellSalt(int cellX, int cellZ, int spacing) {
        if (!active || spacing <= 0) {
            return 0;
        }
        // Cell covers chunks [cellX*spacing .. cellX*spacing+spacing-1]; widen to blocks (x16).
        long minBX = (long) cellX * spacing * 16L;
        long maxBX = ((long) cellX * spacing + spacing - 1) * 16L + 15L;
        long minBZ = (long) cellZ * spacing * 16L;
        long maxBZ = ((long) cellZ * spacing + spacing - 1) * 16L + 15L;
        boolean touchesBox = maxBX >= boxMinX && minBX <= boxMaxX && maxBZ >= boxMinZ && minBZ <= boxMaxZ;
        return touchesBox ? 0 : cycleSalt;
    }
}
