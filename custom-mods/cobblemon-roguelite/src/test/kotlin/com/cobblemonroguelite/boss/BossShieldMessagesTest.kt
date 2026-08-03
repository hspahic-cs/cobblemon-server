package com.cobblemonroguelite.boss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a player actually reads when a boss's shield holds (§2.32).
 *
 * ### Why this is worth a test at all
 *
 * Because the messages are the mechanic, not a decoration on it. An unexplained damage floor is
 * *worse* than no damage floor: a player who watches a hit that should have killed land for 80% and
 * hears nothing concludes the mod is broken, and they are being reasonable about it. So the words
 * are the deliverable, and the thing that can go wrong with them without a test is not a crash — it
 * is a sentence that renders as "Boss Onix's shield held — null damage was absorbed."
 *
 * What this cannot check is that the line ever *arrives*: that depends on Cobblemon's interpreter
 * having our protocol id registered and on Showdown having called `battle.add` at all, both of which
 * need a real battle. See the dev-VM checklist in the pull request.
 */
class BossShieldMessagesTest {

    private fun render(action: String?, name: String? = "Boss Onix", first: String? = null, second: String? = null) =
        BossShieldBattle.render(action, name, first, second)?.string

    @Test
    fun `the send-in line says how many shields there are`() {
        assertEquals("Boss Onix is shielded — 3 shields to break through.", render("start", first = "3"))
    }

    /** Singular, because "1 shields" is the kind of thing that makes a mod feel unfinished. */
    @Test
    fun `counts are pluralised`() {
        assertEquals("Boss Onix is shielded — 1 shield to break through.", render("start", first = "1"))
    }

    /**
     * THE line. It names the damage that was thrown away, not just the outcome — the missing damage
     * is the thing the player is confused about, so it is the thing the sentence has to account for.
     */
    @Test
    fun `the absorb line accounts for the missing damage`() {
        assertEquals(
            "Boss Onix's shield held — 175 damage was absorbed. 2 shields left.",
            render("absorb", first = "175", second = "2"),
        )
    }

    /**
     * The break line supplies the *cause* of a `-boost` that Cobblemon renders on its own. Without
     * the sentence the boost looks like an ability nobody can see.
     */
    @Test
    fun `the break line names the stat that rose`() {
        assertEquals(
            "Boss Onix's shield shattered! 1 shield left. Its Sp. Atk rose!",
            render("break", first = "1", second = "spa"),
        )
    }

    /** The last break reads as an ending rather than as "0 shields left". */
    @Test
    fun `the last break says so`() {
        assertEquals(
            "Boss Onix's shield shattered! Its last shield is gone. Its Speed rose!",
            render("break", first = "0", second = "spe"),
        )
    }

    /**
     * Every stat at +6 is a real state, and the JS sends an empty stat for it rather than claiming
     * a rise that cannot happen. The sentence has to still be a sentence.
     */
    @Test
    fun `a break with nothing left to boost still reads`() {
        assertEquals(
            "Boss Onix's shield shattered! 2 shields left.",
            render("break", first = "2", second = ""),
        )
    }

    /**
     * A line we do not understand produces silence, not a half-rendered sentence.
     *
     * A malformed line means the JS and the Kotlin have drifted, and the useful outcome of that is a
     * log entry plus nothing on screen. A fallback string would reach the player as a typo report.
     */
    @Test
    fun `malformed lines render nothing`() {
        assertNull(render("start", first = null))
        assertNull(render("start", first = "not a number"))
        assertNull(render("absorb", first = "10", second = null))
        assertNull(render("break", first = null, second = "atk"))
        assertNull(render(action = null))
        assertNull(render("somethingelse", first = "1"))
        assertNull(render("start", name = "", first = "1"))
    }

    /**
     * The protocol id is namespaced, because Showdown's id space is global and shared with every
     * other mod that has the same idea. A collision does not error — the later registration simply
     * wins — so the name is the whole defence.
     */
    @Test
    fun `the protocol id is namespaced and leading-dashed`() {
        assertTrue(BossShieldBattle.PROTOCOL_ID.startsWith("-"))
        assertTrue(BossShieldBattle.PROTOCOL_ID.contains("roguelite"))
        // Cobblemon strips pipes from a message id before looking it up, so the registered key must
        // carry none of its own.
        assertTrue('|' !in BossShieldBattle.PROTOCOL_ID)
    }

    /** The marker is a prefix on the species name, so it has to end in a separator. */
    @Test
    fun `the boss name prefix is a prefix`() {
        assertNotNull(BossShieldBattle.NAME_PREFIX)
        assertTrue(BossShieldBattle.NAME_PREFIX.endsWith(" "))
    }
}
