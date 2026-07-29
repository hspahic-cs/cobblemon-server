package com.cobblemonroguelite.arena

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.structure.BoundingBox

/**
 * Visiting the blocks in an arena box **that can actually be blocks**.
 *
 * ### The cost this exists to remove
 *
 * The first version of the stamp walked every cell of the box — 64×32×64 is 131,072 — calling
 * `getBlockState` on each before deciding whether to clear it. On the server thread, once per stamp.
 * Since §2.23 that is not a rare path: every session's first entry re-stamps, so the cost lands as a
 * hitch on the first resume of the day rather than only at run start, which is where it was noticed.
 *
 * Almost all of that work is provably wasted. The arena dimension is a **void generator** — nothing
 * exists in it that we did not put there — so a chunk section nobody has stamped into is empty, and
 * `LevelChunkSection.hasOnlyAir()` answers that in constant time off a flag the section already
 * maintains. A default box is 16 chunk columns × 2 sections = 32 sections, so:
 *
 * - a slot that has never been used costs **32 checks** instead of 131,072 state reads;
 * - a slot with a platform standing in it has blocks in one section layer, so it costs 32 checks plus
 *   16 × 4,096 ≈ 65k reads — half the old figure, and the half that is skipped is the empty air above
 *   the build, which is most arenas.
 *
 * Both numbers are arithmetic over the section grid rather than measurements; what was measured is
 * the symptom the old figure produced. The actual saving on a live server needs the dev VM.
 *
 * ### What it does not do
 *
 * It does not visit air. Every caller here is asking "what is left over from the last build", and air
 * is never left over. A caller that needs to *write* into empty cells walks its own plan instead —
 * see [ArenaGenerator], which places from the plan and clears through this.
 */
object ArenaBoxScan {

    /** Blocks along one edge of a chunk section. */
    private const val SECTION = 16

    /**
     * Call [action] for every non-air block position inside [box], skipping empty sections wholesale.
     *
     * The position handed to [action] is a shared mutable cursor — cheap, and every caller here
     * either reads it or passes it straight to `setBlock`, which copies. A caller that wants to keep
     * it must take `.immutable()`.
     *
     * Requires the chunks to be held ([ArenaChunks.hold]); it fetches them blocking, which in a void
     * dimension generates nothing but which would be real work in an owner's own world.
     */
    fun forEachNonAir(level: ServerLevel, box: BoundingBox, action: (BlockPos) -> Unit) {
        val cursor = BlockPos.MutableBlockPos()
        val minChunkX = SectionPos.blockToSectionCoord(box.minX())
        val maxChunkX = SectionPos.blockToSectionCoord(box.maxX())
        val minChunkZ = SectionPos.blockToSectionCoord(box.minZ())
        val maxChunkZ = SectionPos.blockToSectionCoord(box.maxZ())
        val minSectionY = SectionPos.blockToSectionCoord(box.minY())
        val maxSectionY = SectionPos.blockToSectionCoord(box.maxY())

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                val chunk = level.getChunk(chunkX, chunkZ)
                val sections = chunk.sections
                val xs = overlap(box.minX(), box.maxX(), chunkX)
                val zs = overlap(box.minZ(), box.maxZ(), chunkZ)
                for (sectionY in minSectionY..maxSectionY) {
                    val index = chunk.getSectionIndexFromSectionY(sectionY)
                    // Out of the world's vertical range. Not an error: an owner pointing
                    // [ArenaConfig.fixedArenas] at a real world can put a box wherever they like, and
                    // the part of it outside the world simply has nothing in it.
                    if (index < 0 || index >= sections.size) continue
                    if (sections[index].hasOnlyAir()) continue
                    for (y in overlap(box.minY(), box.maxY(), sectionY)) {
                        for (x in xs) {
                            for (z in zs) {
                                cursor.set(x, y, z)
                                if (!level.getBlockState(cursor).isAir) action(cursor)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The part of `min..max` that lies inside the section at [sectionCoord].
     *
     * Pure integer arithmetic, and the one piece of this file a test can reach. Getting it wrong in
     * the narrow direction leaves debris in a stripe along a chunk edge — invisible until a band
     * transition puts a smaller build in and the previous one's wall is still standing.
     */
    fun overlap(min: Int, max: Int, sectionCoord: Int): IntRange {
        val start = SectionPos.sectionToBlockCoord(sectionCoord)
        return maxOf(min, start)..minOf(max, start + SECTION - 1)
    }
}
