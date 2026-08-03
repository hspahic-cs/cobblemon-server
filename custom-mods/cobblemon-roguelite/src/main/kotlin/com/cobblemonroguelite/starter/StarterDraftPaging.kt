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
     * Rows 1–4 of a 6-row chest, minus the right-hand column the points meter owns. Row 0 is the cost
     * tabs and row 5 is the controls; the split is fixed rather than configurable because all three of
     * those regions have fixed slots, and a grid that could grow into them would paint over a button.
     *
     * It has fallen 45 → 36 → 32 as the chrome arrived, which sounds like a loss and is not: 45 a page
     * was thirteen pages of *undifferentiated* catalogue, and the only way to find anything in it was
     * to page. Cost tabs cut 542 to at most 179 and usually far less, so the number of pages a player
     * actually walks went down, not up.
     */
    const val PER_PAGE = 32

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
 * Which slice of the catalogue the grid is showing, chosen from the tabs along the top.
 *
 * ### Why filtering, and why it replaced sorting
 *
 * A sort control answers "where do I start reading"; it does not stop the list being 542 long. Under
 * §2.13 the question a player is actually asking is "what can I get for 3 points", and that is a
 * *subset*, not a starting position — sorting alone still makes them page past everything cheaper to
 * find where the 3s end and the 4s begin. The tabs make the budget arithmetic the axis you navigate
 * by, which is what the budget was for, and they absorbed the sort control outright: inside a
 * single-cost tab every entry ties on cost, so [StarterCatalogue.options]'s (cost, id) order leaves
 * the grid alphabetical with no control to press.
 *
 * ### Why the top of the range gets bucketed
 *
 * PokéRogue's table uses ten distinct costs and a chest row has nine slots, so All plus one tab each
 * does not fit. The tail is bucketed rather than the head because that is where the species get
 * sparse — 1–7 covers 514 of 542, and the 28 above it are a "show me the expensive ones" browse
 * rather than a set anybody picks a specific price out of.
 */
sealed interface StarterDraftFilter {

    /** What the tab is labelled. Short: it is a tab, not a sentence. */
    val label: String

    fun matches(option: StarterOption): Boolean

    data object All : StarterDraftFilter {
        override val label = "All"
        override fun matches(option: StarterOption) = true
    }

    data class Exactly(val cost: Int) : StarterDraftFilter {
        override val label = "$cost"
        override fun matches(option: StarterOption) = option.cost == cost
    }

    /** The bucket the tail folds into when there are more distinct costs than tabs. */
    data class AtLeast(val cost: Int) : StarterDraftFilter {
        override val label = "$cost+"
        override fun matches(option: StarterOption) = option.cost >= cost
    }

    companion object {

        /**
         * The tabs to show for a catalogue: All, then a tab per cost that is actually in it.
         *
         * Derived from the catalogue rather than from a fixed 1..10 so a new player — whose catalogue
         * is the baseline pool and nothing else — gets three or four tabs that all contain something,
         * instead of ten of which six are empty.
         */
        fun tabsFor(options: List<StarterOption>, maxTabs: Int): List<StarterDraftFilter> {
            if (maxTabs <= 1) return listOf(All)
            val costs = options.map { it.cost }.distinct().sorted()
            if (costs.isEmpty()) return listOf(All)
            if (costs.size + 1 <= maxTabs) return listOf(All) + costs.map(::Exactly)
            // One slot for All, one for the bucket, the rest for exact costs.
            val exact = costs.take(maxTabs - 2)
            return listOf(All) + exact.map(::Exactly) + AtLeast(costs[maxTabs - 2])
        }
    }
}

/**
 * The points meter: how much of the budget is gone, as a row of coloured panes.
 *
 * ### Why a gauge and not a number
 *
 * The exact numbers are on every segment's tooltip. The gauge is the part you can read *without*
 * reading: the column fills and changes colour as it goes, so "am I nearly out" is answered by a
 * glance at a shape rather than by subtracting one number from another mid-draft.
 *
 * ### The colour is the position, not the total
 *
 * Each segment is coloured by *where in the budget it sits* — the lower three green, the fourth amber,
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

    /** One per chest row below the tabs, so the right-hand column reads as one bar rather than five items. */
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
