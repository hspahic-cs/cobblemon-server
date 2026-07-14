package com.cobblemonwilderness.gen;

/**
 * Duck-typing marker mixed into {@code ChunkGeneratorStructureState} so a per-dimension structure
 * state can be tagged with whether it belongs to the overworld.
 *
 * <p>The relocation salt hook ({@code getPotentialStructureChunk}) has no dimension context and its
 * seed is shared across dimensions, so the overworld gate must be threaded in. The structure state is
 * created per dimension (one per {@code ChunkMap}); tagging it at construction gives a deterministic,
 * call-context-independent overworld signal that the gen-path mixin reads to set
 * {@link WildernessGenState#beginOverworld()}.
 */
public interface WildernessDimensionAware {
    boolean cobblemonwilderness$isOverworld();

    void cobblemonwilderness$setOverworld(boolean overworld);
}
