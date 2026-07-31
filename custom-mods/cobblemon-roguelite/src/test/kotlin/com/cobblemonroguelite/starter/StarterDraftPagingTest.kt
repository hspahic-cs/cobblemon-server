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
        val all = options(100)
        val page = StarterDraftPaging.pageAt(all, 99)
        assertEquals(2, page.index)
        assertEquals(3, page.pageCount)
        assertEquals(all.drop(90), page.options)
    }

    @Test
    fun `a negative page index clamps to the first page`() {
        val page = StarterDraftPaging.pageAt(options(100), -4)
        assertEquals(0, page.index)
        assertFalse(page.hasPrevious)
    }

    @Test
    fun `pageCount never returns zero, so 'page 1 of N' is always sayable`() {
        assertEquals(1, StarterDraftPaging.pageCount(0))
        assertEquals(1, StarterDraftPaging.pageCount(-1))
        assertEquals(1, StarterDraftPaging.pageCount(1))
        assertEquals(2, StarterDraftPaging.pageCount(StarterDraftPaging.PER_PAGE + 1))
    }
}
