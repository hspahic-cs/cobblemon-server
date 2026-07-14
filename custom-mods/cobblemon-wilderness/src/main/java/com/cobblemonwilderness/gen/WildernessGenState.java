package com.cobblemonwilderness.gen;

import java.util.Arrays;
import java.util.Map;

/**
 * Server-global state read by the structure-placement mixin on the worldgen hot path.
 *
 * Configured once at server start (and again whenever a prune bumps a region's reset generation)
 * from the Kotlin reset flow. Plain statics + a single volatile snapshot on purpose: the mixin runs
 * on chunk-gen worker threads, must not allocate or lock, and only ever reads an immutable snapshot
 * published by the main thread before generation begins.
 *
 * <p><b>Per-region relocation (T3):</b> instead of one global cycle salt, each overworld region
 * carries its own reset-generation counter. A cell's placement salt is a pure function of its anchor
 * region and that region's generation, so every chunk that could reference a structure computes the
 * same relocated position, and only regions that have actually been reset move.
 *
 * <p><b>Dimension gate:</b> {@code resetGeneration} is keyed by region coords, which are shared
 * across dimensions — the nether region (5,3) has the same key as the overworld's. To keep nether/end
 * placement byte-identical to vanilla, salt is applied only when the current worldgen work is for the
 * overworld. That signal is a per-thread flag ({@link #beginOverworld()}/{@link #endOverworld()}) set
 * by the dimension-aware mixin around {@code getPotentialStructureChunk}. It defaults {@code false}, so
 * until that mixin is wired and verified, {@link #cellSalt(int, int, int)} is inert (returns 0 = vanilla)
 * everywhere — the safe direction.
 */
public final class WildernessGenState {
    private WildernessGenState() {}

    /** Immutable region-generation snapshot, published via this single volatile. Never mutated in place. */
    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    /** active = feature enabled AND at least one region has been reset (snapshot non-empty). */
    private static volatile boolean active = false;
    /** Keep-box in BLOCK coordinates, inclusive. */
    private static volatile int boxMinX = 0, boxMinZ = 0, boxMaxX = 0, boxMaxZ = 0;

    /**
     * Per-thread "this worldgen work is for the overworld" flag. Set/cleared by the dimension-aware
     * mixin around the two {@code getPotentialStructureChunk} call sites (gen + /locate), so the salt
     * is applied identically in both paths. Defaults false — no salt until explicitly marked overworld.
     */
    private static final ThreadLocal<Boolean> OVERWORLD = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Mark the current thread as generating/locating OVERWORLD structures (call before, clear after). */
    public static void beginOverworld() {
        OVERWORLD.set(Boolean.TRUE);
    }

    /** Clear the overworld mark for the current thread. */
    public static void endOverworld() {
        OVERWORLD.set(Boolean.FALSE);
    }

    /** Whether the current thread is marked as overworld worldgen. */
    public static boolean isOverworld() {
        return OVERWORLD.get();
    }

    /**
     * Publish a new immutable snapshot + box, enabling the hook. [generations] is the overworld
     * region-key → reset-generation map; the hook is active only while it is non-empty (i.e. at least
     * one prune has run). Called from the reset flow at boot, on the main thread.
     */
    public static void configure(boolean enabled, Map<Long, Integer> generations, int minX, int minZ, int maxX, int maxZ) {
        Snapshot snap = Snapshot.of(generations);
        boxMinX = minX;
        boxMinZ = minZ;
        boxMaxX = maxX;
        boxMaxZ = maxZ;
        snapshot = snap;
        active = enabled && !snap.isEmpty();
    }

    /** Turn the hook off (no relocation; placement is vanilla everywhere). */
    public static void disable() {
        active = false;
    }

    /** Packs a region coord into the flat map/snapshot key. Single source of truth for Kotlin + Java. */
    public static long regionKey(int rx, int rz) {
        return ((long) rx << 32) | ((long) rz & 0xffffffffL);
    }

    /**
     * Extra salt to XOR into a random-spread structure placement RNG for grid cell (cellX, cellZ) of
     * the given spacing (in chunks). Reads the per-thread overworld flag. Returns 0 — leaving placement
     * exactly vanilla — when the feature is off, the thread isn't marked overworld, the cell touches the
     * keep-box, or the cell's anchor region has never been reset. Cheap: a few comparisons plus one
     * binary search, no allocation.
     */
    public static int cellSalt(int cellX, int cellZ, int spacing) {
        return cellSalt(cellX, cellZ, spacing, OVERWORLD.get());
    }

    /** Testable core of {@link #cellSalt(int, int, int)} with the overworld gate passed explicitly. */
    static int cellSalt(int cellX, int cellZ, int spacing, boolean overworld) {
        if (!active || spacing <= 0 || !overworld) {
            return 0;
        }
        // Cell covers chunks [cellX*spacing .. cellX*spacing+spacing-1]; widen to blocks (x16).
        long minBX = (long) cellX * spacing * 16L;
        long maxBX = ((long) cellX * spacing + spacing - 1) * 16L + 15L;
        long minBZ = (long) cellZ * spacing * 16L;
        long maxBZ = ((long) cellZ * spacing + spacing - 1) * 16L + 15L;
        boolean touchesBox = maxBX >= boxMinX && minBX <= boxMaxX && maxBZ >= boxMinZ && minBZ <= boxMaxZ;
        if (touchesBox) {
            return 0;
        }
        // Anchor region of the cell = its origin chunk (cellX*spacing) shifted to region space (>>5).
        int rx = (cellX * spacing) >> 5;
        int rz = (cellZ * spacing) >> 5;
        int gen = snapshot.get(regionKey(rx, rz));
        return deriveSalt(rx, rz, gen);
    }

    /**
     * Deterministic per-region placement salt. {@code gen == 0} → 0 (vanilla, never reset). For
     * {@code gen >= 1} it returns a hash of (rx, rz, gen) that is GUARANTEED nonzero — a zero result
     * would silently fail to relocate, so it is substituted with 1.
     */
    static int deriveSalt(int rx, int rz, int gen) {
        if (gen == 0) {
            return 0;
        }
        int h = rx * 0x9E3779B1;
        h = (h ^ rz) * 0x85EBCA77;
        h = (h ^ gen) * 0xC2B2AE3D;
        h ^= (h >>> 15);
        return h == 0 ? 1 : h;
    }

    /**
     * Immutable region-generation lookup. Sorted parallel primitive arrays + binary search: no boxing,
     * no allocation on read, so it is safe to share across worldgen worker threads via one volatile.
     */
    static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(new long[0], new int[0]);

        private final long[] keys; // sorted ascending
        private final int[] gens;

        private Snapshot(long[] keys, int[] gens) {
            this.keys = keys;
            this.gens = gens;
        }

        boolean isEmpty() {
            return keys.length == 0;
        }

        /** Reset generation for a region key, or 0 if the region is absent (never reset). */
        int get(long key) {
            int i = Arrays.binarySearch(keys, key);
            return i >= 0 ? gens[i] : 0;
        }

        static Snapshot of(Map<Long, Integer> generations) {
            if (generations == null || generations.isEmpty()) {
                return EMPTY;
            }
            long[] keys = new long[generations.size()];
            int idx = 0;
            for (Long key : generations.keySet()) {
                keys[idx++] = key;
            }
            Arrays.sort(keys);
            int[] gens = new int[keys.length];
            for (int i = 0; i < keys.length; i++) {
                Integer g = generations.get(keys[i]);
                gens[i] = g == null ? 0 : g;
            }
            return new Snapshot(keys, gens);
        }
    }
}
