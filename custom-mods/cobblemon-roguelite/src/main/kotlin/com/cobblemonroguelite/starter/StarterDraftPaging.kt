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
     * Rows 1–4 of a 6-row chest, full width. Row 0 is the header (sort, and the points meter) and row 5
     * is the controls, and the split is fixed rather than configurable because both of those rows have
     * fixed slots — a grid that could grow into them would paint over the confirm button.
     *
     * It was 45 before the header row existed. Nine fewer per page is thirteen pages becoming sixteen
     * across PokéRogue's 542, which the sort control more than pays back: the reason to page that far
     * was to find the affordable end of the list, and sorting puts it under the cursor instead.
     */
    const val PER_PAGE = 36

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

/**
 * How the grid is ordered, cycled by the header button.
 *
 * ### Why sorting lives here and not in the catalogue
 *
 * [StarterCatalogue.options] is cheapest-first and must stay that way: it is the order the *validator*
 * and the log lines use, and two builds of the same catalogue are required to render identically. This
 * is a view over that list. Nothing downstream reads the sorted copy except the paint, so a player
 * changing sort cannot change what they are allowed to buy — only where it is on screen.
 *
 * ### Every mode breaks ties on the full id
 *
 * Cost is not unique and neither is a species path across namespaces, so a comparator that stopped at
 * the visible key would leave equal entries free to swap places between repaints — species jumping
 * around under a cursor that has not moved. The id makes each order total.
 */
enum class StarterDraftSort {

    /** The catalogue's own order. Default, because the affordable end is the end a new player needs. */
    CHEAPEST,

    /** Costliest first — "what is the best thing I can afford", which is the other way people shop. */
    COSTLIEST,

    /** By species name. The one order that does not move when prices change, so it is how you find a
     *  Pokémon you already have in mind rather than one you are choosing between. */
    ALPHABETICAL,
    ;

    val label: String
        get() = when (this) {
            CHEAPEST -> "Cheapest first"
            COSTLIEST -> "Most expensive first"
            ALPHABETICAL -> "A to Z"
        }

    fun next(): StarterDraftSort = entries[(ordinal + 1) % entries.size]

    fun sort(options: List<StarterOption>): List<StarterOption> = when (this) {
        CHEAPEST -> options.sortedWith(compareBy({ it.cost }, { it.species.toString() }))
        COSTLIEST -> options.sortedWith(compareByDescending<StarterOption> { it.cost }.thenBy { it.species.toString() })
        // The path, not the full id: `cobblemon:bulbasaur` sorts under B, which is what a player reading
        // "A to Z" means. The namespace only breaks ties, where an addon's Bulbasaur would otherwise be
        // free to swap places with Cobblemon's.
        ALPHABETICAL -> options.sortedWith(compareBy({ it.species.path }, { it.species.toString() }))
    }
}

/**
 * The points meter: how much of the budget is gone, as a row of coloured panes.
 *
 * ### Why a gauge and not a number
 *
 * There is a number too — it is on the budget icon. This is the thing you can read without reading:
 * the panes fill left to right and change colour as they go, so "am I nearly out" is answered by a
 * glance at a shape rather than by subtracting one number from another mid-draft.
 *
 * ### The colour is the position, not the total
 *
 * Each segment is coloured by *where in the budget it sits* — the first three green, the fourth amber,
 * the last red — the way a fuel gauge is. The alternative, recolouring every filled segment according
 * to the overall fraction, makes the whole bar change colour at once on a single click and loses the
 * "you are entering the last of it" reading, which is the only part a player has to act on.
 *
 * ### It cannot overflow, and that is enforced elsewhere
 *
 * [StarterDraftMenu] only accepts a pick [StarterSelection] would accept, so `spent` never exceeds the
 * budget through the GUI. [filled] clamps anyway, because a full red bar is a better answer to an
 * impossible state than an index out of range inside a repaint.
 */
object StarterDraftMeter {

    /** One per row of the chest below the header, so the meter reads as a bar rather than as five items. */
    const val SEGMENTS = 5

    enum class Zone { GREEN, AMBER, RED }

    /**
     * Segments to light up for [spent] of [budget].
     *
     * Rounds **up**, so any spending at all lights the first segment: a player who has spent 1 of 10
     * and sees an empty bar reads it as "the click did nothing".
     */
    fun filled(spent: Int, budget: Int): Int {
        if (spent <= 0) return 0
        if (budget <= 0) return SEGMENTS
        val segments = (spent.toLong() * SEGMENTS + budget - 1) / budget
        return segments.coerceIn(1L, SEGMENTS.toLong()).toInt()
    }

    /** Which colour the segment at [index] is when lit. Green, green, green, amber, red. */
    fun zoneOf(index: Int): Zone = when {
        index >= SEGMENTS - 1 -> Zone.RED
        index >= SEGMENTS - 2 -> Zone.AMBER
        else -> Zone.GREEN
    }
}
