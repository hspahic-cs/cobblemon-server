package com.cobblemonroguelite.starter

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The paging arithmetic behind the starter draft grid.
 *
 * Everything here is about a page that is not there, because that is the only way this can fail in
 * play: the catalogue is rebuilt from a player's unlocks, so a screen can outlive the list it is
 * paging over. [StarterDraftPaging.pageAt] is required to clamp rather than throw, and the tests below
 * are what says so.
 */
class StarterDraftPagingTest {

    private fun options(count: Int): List<StarterOption> =
        (1..count).map { StarterOption(ResourceLocation.fromNamespaceAndPath("cobblemon", "mon$it"), it) }

    @Test
    fun `an empty catalogue is page 1 of 1, not 1 of 0`() {
        val page = StarterDraftPaging.pageAt(emptyList(), 0)
        assertEquals(1, page.pageCount)
        assertEquals(1, page.humanIndex)
        assertTrue(page.options.isEmpty())
        assertFalse(page.hasPrevious)
        assertFalse(page.hasNext)
    }

    @Test
    fun `a catalogue that fits on one page has no page buttons`() {
        val page = StarterDraftPaging.pageAt(options(StarterDraftPaging.PER_PAGE), 0)
        assertEquals(1, page.pageCount)
        assertEquals(StarterDraftPaging.PER_PAGE, page.options.size)
        assertFalse(page.hasNext)
    }

    @Test
    fun `one species past a full page opens a second page holding exactly it`() {
        val all = options(StarterDraftPaging.PER_PAGE + 1)
        val first = StarterDraftPaging.pageAt(all, 0)
        val second = StarterDraftPaging.pageAt(all, 1)

        assertEquals(2, first.pageCount)
        assertTrue(first.hasNext)
        assertFalse(first.hasPrevious)

        assertEquals(listOf(all.last()), second.options)
        assertTrue(second.hasPrevious)
        assertFalse(second.hasNext)
    }

    @Test
    fun `the whole catalogue is reachable by paging, in order, with nothing repeated`() {
        // 542 is PokéRogue's priced species count — the size this grid exists to survive.
        val all = options(542)
        val pageCount = StarterDraftPaging.pageCount(all.size)
        val walked = (0 until pageCount).flatMap { StarterDraftPaging.pageAt(all, it).options }
        assertEquals(all, walked)
    }

    @Test
    fun `a page index past the end clamps to the last page rather than throwing`() {
        // Sized off PER_PAGE rather than off a literal: this test is about the clamp, and it should not
        // start failing the next time a row is given to the header.
        val all = options(StarterDraftPaging.PER_PAGE * 2 + 3)
        val page = StarterDraftPaging.pageAt(all, 99)
        assertEquals(2, page.index)
        assertEquals(3, page.pageCount)
        assertEquals(all.takeLast(3), page.options)
    }

    @Test
    fun `a negative page index clamps to the first page`() {
        val page = StarterDraftPaging.pageAt(options(100), -4)
        assertEquals(0, page.index)
        assertFalse(page.hasPrevious)
    }

    @Test
    fun `tabs are derived from the costs actually in the catalogue`() {
        // A new player's catalogue is the baseline pool and nothing else, so a fixed 1..10 tab row would
        // be six tabs that open onto nothing.
        val sparse = listOf(3, 3, 5, 8).mapIndexed { index, cost ->
            StarterOption(ResourceLocation.fromNamespaceAndPath("cobblemon", "mon$index"), cost)
        }
        val tabs = StarterDraftFilter.tabsFor(sparse, maxTabs = 9)
        assertEquals(
            listOf("All", "3", "5", "8"),
            tabs.map { it.label },
        )
    }

    @Test
    fun `more distinct costs than tabs buckets the tail and loses nothing`() {
        // PokéRogue's real shape: ten costs, nine slots.
        val all = (1..10).flatMap { cost ->
            List(3) { StarterOption(ResourceLocation.fromNamespaceAndPath("cobblemon", "c${cost}_$it"), cost) }
        }
        val tabs = StarterDraftFilter.tabsFor(all, maxTabs = 9)

        assertEquals(9, tabs.size)
        assertEquals(listOf("All", "1", "2", "3", "4", "5", "6", "7", "8+"), tabs.map { it.label })
        // The load-bearing property: every species is reachable through some tab.
        val reachable = all.filter { option -> tabs.any { it.matches(option) } }
        assertEquals(all.size, reachable.size)
        assertEquals(9, all.count { StarterDraftFilter.AtLeast(8).matches(it) })
    }

    @Test
    fun `a tab matches exactly its own cost`() {
        val three = StarterOption(ResourceLocation.fromNamespaceAndPath("cobblemon", "a"), 3)
        val four = StarterOption(ResourceLocation.fromNamespaceAndPath("cobblemon", "b"), 4)
        assertTrue(StarterDraftFilter.Exactly(3).matches(three))
        assertFalse(StarterDraftFilter.Exactly(3).matches(four))
        assertTrue(StarterDraftFilter.All.matches(four))
    }

    @Test
    fun `an empty catalogue still gets an All tab rather than an empty row`() {
        assertEquals(listOf(StarterDraftFilter.All), StarterDraftFilter.tabsFor(emptyList(), maxTabs = 9))
        assertEquals(listOf(StarterDraftFilter.All), StarterDraftFilter.tabsFor(options(5), maxTabs = 1))
    }

    @Test
    fun `the meter is empty at nothing spent and full at the budget`() {
        assertEquals(0, StarterDraftMeter.filled(spent = 0, budget = 10))
        assertEquals(StarterDraftMeter.SEGMENTS, StarterDraftMeter.filled(spent = 10, budget = 10))
    }

    @Test
    fun `any spending at all lights the first segment`() {
        // A player who spends 1 of 10 and sees an empty bar reads it as "the click did nothing".
        assertEquals(1, StarterDraftMeter.filled(spent = 1, budget = 10))
        assertEquals(1, StarterDraftMeter.filled(spent = 1, budget = 100))
    }

    @Test
    fun `the meter clamps instead of overflowing on an impossible spend`() {
        assertEquals(StarterDraftMeter.SEGMENTS, StarterDraftMeter.filled(spent = 99, budget = 10))
        assertEquals(0, StarterDraftMeter.filled(spent = -1, budget = 10))
        // A zero budget is an operator fault, not a divide-by-zero.
        assertEquals(StarterDraftMeter.SEGMENTS, StarterDraftMeter.filled(spent = 1, budget = 0))
    }

    @Test
    fun `the meter fills monotonically across the budget`() {
        val filled = (0..10).map { StarterDraftMeter.filled(it, 10) }
        assertEquals(filled.sorted(), filled)
        assertTrue(filled.all { it in 0..StarterDraftMeter.SEGMENTS })
    }

    @Test
    fun `only the last segment is red and only the one before it is amber`() {
        val zones = (0 until StarterDraftMeter.SEGMENTS).map(StarterDraftMeter::zoneOf)
        assertEquals(1, zones.count { it == StarterDraftMeter.Zone.RED })
        assertEquals(1, zones.count { it == StarterDraftMeter.Zone.AMBER })
        assertEquals(StarterDraftMeter.Zone.RED, zones.last())
        assertEquals(StarterDraftMeter.Zone.GREEN, zones.first())
    }

    @Test
    fun `pageCount never returns zero, so 'page 1 of N' is always sayable`() {
        assertEquals(1, StarterDraftPaging.pageCount(0))
        assertEquals(1, StarterDraftPaging.pageCount(-1))
        assertEquals(1, StarterDraftPaging.pageCount(1))
        assertEquals(2, StarterDraftPaging.pageCount(StarterDraftPaging.PER_PAGE + 1))
    }
}
