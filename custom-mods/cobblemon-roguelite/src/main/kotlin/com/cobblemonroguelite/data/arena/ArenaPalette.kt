package com.cobblemonroguelite.data.arena

import net.minecraft.resources.ResourceLocation

/**
 * An arena expressed as a choice of **blocks**, not as a choice of architecture (§2.29).
 *
 * ### What this format refuses to be
 *
 * It is not a build language. There is no way to say "an archway here" or "a statue there", and that
 * is the point rather than a limitation waiting to be lifted: the person this exists for cannot
 * build, so every field they have to fill in has to be answerable by naming a block. Floor, rim,
 * pillars and two numbers is the whole vocabulary, and a volcano differs from a meadow by four block
 * ids. §2.24's repaint — sky, fog, water and grass tint, ambient loops, music — is what actually
 * carries the place; this only has to be a floor to stand on that is the right colour.
 *
 * ### Everything here is a *default* state
 *
 * Blocks are named as ids and placed with `defaultBlockState()`. Naming a stair or a slab is legal
 * and gets you the default facing, which is almost certainly not what was wanted. Block states are
 * deliberately not exposed: they would double the size of the format for a payoff a hand-built
 * `.nbt` already delivers to anybody who cares that much.
 *
 * @property id the file path, the same convention every other registry here uses.
 * @property floor the platform. The one required field, because a palette that places no floor is a
 *   run in a void dimension with nothing under the player — see [com.cobblemonroguelite.arena.ArenaPlan]
 *   for why the floor is exactly one layer thick and cannot be more.
 * @property width platform size along x, or null for "as wide as the arena box". Odd and even both
 *   work; the platform is centred in the box either way.
 * @property depth the same along z.
 * @property rim an optional wall around the platform edge. Not decoration: the arena dimension is
 *   void and the platform has an edge, so a rim is the difference between a player who walks off and
 *   a player who does not. Left optional because an owner may want that edge.
 * @property pillars four optional corner pillars. The only field here that is purely shape, and it
 *   is cheap enough to be worth having: four columns is the difference between "a platform" and
 *   "a place", for a few dozen blocks.
 * @property powerSpot whether to place Mega Showdown's `power_spot` (§2.5). True by default and it
 *   should stay true — the entire gimmick confinement is "the block exists inside an arena and
 *   nowhere else", so a palette that turns it off is a biome where Dynamax silently does not work.
 *   It is a field at all so that an owner who has banned the gimmick can say so rather than having
 *   to notice that we place a block from a mod they did not want.
 */
/**
 * Whether the island is square or round.
 *
 * Round is what the arenas are being detailed as, and it is a real difference rather than a finish:
 * a square platform in a void dimension reads as a chunk of a world that was cut out, and a disc
 * reads as somewhere that was always an island. `width`/`depth` keep their meaning either way — they
 * are the bounding footprint, and a circle is inscribed in it.
 *
 * SQUARE stays the default so that a palette written before this existed keeps producing exactly what
 * it used to. Nothing about a shipped arena depends on that; it is the smaller surprise.
 */
enum class ArenaShape { SQUARE, CIRCLE }

data class ArenaPalette(
    val id: ResourceLocation,
    val floor: ResourceLocation,
    val shape: ArenaShape = ArenaShape.SQUARE,
    val width: Int? = null,
    val depth: Int? = null,
    val rim: ArenaRim? = null,
    val pillars: ArenaPillars? = null,
    val powerSpot: Boolean = true,
)

/**
 * A wall around the platform edge, [height] blocks tall, standing on the floor.
 *
 * One block thick, always. A thicker rim eats platform the player could have fought on, and the
 * thing a rim is for — not falling into the void — is done by the first block.
 */
data class ArenaRim(val block: ResourceLocation, val height: Int)

/**
 * Four columns at the platform corners, [height] blocks tall, standing on the floor.
 *
 * @property inset how far in from the corner each pillar sits, in blocks. Zero puts them exactly on
 *   the corner, which with a [ArenaRim] means inside the wall — legal, and the pillar wins the cell.
 */
data class ArenaPillars(val block: ResourceLocation, val height: Int, val inset: Int = 0)
