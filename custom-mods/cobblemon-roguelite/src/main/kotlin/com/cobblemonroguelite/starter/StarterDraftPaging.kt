package com.cobblemonroguelite.starter

/**
 * One screenful of the starter catalogue, and the arithmetic that produces it.
 *
 * ### Why this is not three lines inside the menu
 *
 * It is the only part of [StarterDraftMenu] that can be wrong in a way a reader cannot see: an
 * off-by-one in `subList` throws, an unclamped index paints an empty grid the player reads as "I have
 * no starters", and neither is reachable from a test that needs a `MinecraftServer`. Pulled out here
 * it is a pure function over a list, and the menu keeps only the parts that genuinely need Minecraft.
 *
 * ### The clamp is the contract
 *
 * [pageAt] never throws and never returns a page that is not there. That matters more than it sounds:
 * the catalogue is rebuilt from the player's unlocks, so a menu held open across a reload can be
 * pointing at page 7 of a catalogue that now has three. Clamping turns that into "you are looking at
 * the last page", which is what a player would expect, rather than a crash inside a repaint.
 */
data class StarterDraftPage(

    /** Zero-based, already clamped into `0 until pageCount`. */
    val index: Int,

    /** At least 1, so "page 1 of 1" is what an empty catalogue reads as rather than "1 of 0". */
    val pageCount: Int,

    /** The options on this page, in catalogue order — cheapest first (see [StarterCatalogue.options]). */
    val options: List<StarterOption>,
) {
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < pageCount - 1

    /** What a human reads on the page buttons. */
    val humanIndex: Int get() = index + 1
}

object StarterDraftPaging {

    /**
     * Rows 0–4 of a 6-row chest. Row 5 is the controls, and the split is fixed rather than configurable
     * because the control row has fixed slots too — a grid that could grow into row 5 would paint over
     * the confirm button.
     */
    const val PER_PAGE = 45

    fun pageCount(total: Int): Int = if (total <= 0) 1 else (total + PER_PAGE - 1) / PER_PAGE

    fun pageAt(options: List<StarterOption>, index: Int): StarterDraftPage {
        val pageCount = pageCount(options.size)
        val clamped = index.coerceIn(0, pageCount - 1)
        val from = clamped * PER_PAGE
        return StarterDraftPage(
            index = clamped,
            pageCount = pageCount,
            // coerceAtMost rather than min-of-two-ints so the last page is short instead of out of range.
            options = options.subList(from.coerceAtMost(options.size), (from + PER_PAGE).coerceAtMost(options.size)),
        )
    }
}
