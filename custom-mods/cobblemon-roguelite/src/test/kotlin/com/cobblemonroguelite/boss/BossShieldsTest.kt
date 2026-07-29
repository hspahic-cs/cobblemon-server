package com.cobblemonroguelite.boss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The boss shield mechanic — the parts of it that exist on this side of the language boundary.
 *
 * ### What is under test, and what emphatically is not
 *
 * The mechanic *runs* in `data/cobblemon_roguelite/held_items/boss_shield_*.js`, inside Showdown's
 * GraalJS context, driven by a battle. Nothing here can reach it, and pretending otherwise would be
 * worse than not trying: a mock of Showdown's damage pipeline would test the mock.
 *
 * What is real, and what breaks the mechanic silently when it drifts, is the **contract** between
 * the two sides. Three things cross it, and all three are checked here:
 *
 * 1. the **boundary arithmetic** — [BossShields.floorHp] is the specification the JS implements,
 *    written in the same shape on both sides, so the percentages a player experiences are pinned by
 *    a test even though the code that produces them is not;
 * 2. the **item id** a shield count maps to, and the **item stack string** that carries it, which is
 *    the only channel the count has;
 * 3. that a JS file actually **exists** for every count [BossShields.MAX_SHIELDS] admits, and
 *    declares itself with the matching name and count.
 *
 * That third one is the one worth having. Raising `MAX_SHIELDS` without adding a file produces a
 * held item id Showdown has never heard of; the item resolves to nothing, the boss fights with no
 * shields, and *nothing anywhere logs*, because from Cobblemon's point of view a Pokémon holding an
 * unknown item is completely ordinary. It is a two-edit change that looks like one.
 */
class BossShieldsTest {

    // ---------------------------------------------------------------------------------------
    // Boundary arithmetic
    // ---------------------------------------------------------------------------------------

    /**
     * Three shields on 100 HP stop the boss at 75%, 50% and 25%, and the last quarter is killable.
     *
     * These are the numbers a player actually watches happen, so they are written as numbers rather
     * than derived from the formula — a test that recomputes the implementation agrees with itself
     * by construction and would survive the `shields + 1` being changed to `shields`.
     */
    @Test
    fun `three shields quarter the hp bar`() {
        assertEquals(75, BossShields.floorHp(maxHp = 100, shields = 3, broken = 0))
        assertEquals(50, BossShields.floorHp(maxHp = 100, shields = 3, broken = 1))
        assertEquals(25, BossShields.floorHp(maxHp = 100, shields = 3, broken = 2))
        assertEquals(0, BossShields.floorHp(maxHp = 100, shields = 3, broken = 3))
    }

    /**
     * One shield is a half bar, not a whole one.
     *
     * The `+ 1` in the formula is the entire difference between "three shields" meaning three stops
     * and meaning two, and a one-shield boss is where dropping it would be least visible — so this
     * is the case that catches it.
     */
    @Test
    fun `one shield halves the hp bar`() {
        assertEquals(50, BossShields.floorHp(maxHp = 100, shields = 1, broken = 0))
        assertEquals(0, BossShields.floorHp(maxHp = 100, shields = 1, broken = 1))
    }

    /**
     * Floors never rise, never go negative, and always reach zero — for every count and every
     * plausible HP pool.
     *
     * A floor that *rose* as shields broke would be the single worst failure available here: the
     * boss's floor would sit above its own current HP, every later hit would clamp to zero damage,
     * and the boss could not be killed at all. Worth a sweep rather than a spot check because
     * rounding is where it would come from, and rounding misbehaves at small HP pools rather than at
     * the round ones a hand-written case would use.
     *
     * Deliberately **not strictly** descending. Below `shields + 1` HP there is not enough bar to
     * give every shield a distinct boundary, so consecutive floors tie. That is not a bug and must
     * not be "fixed" into a rejection: the JS advances its broken count past any boundary it is
     * already at (`sync`), so a tie simply collapses two shields into one break. Asserting
     * strictness here would outlaw a case the mechanic already handles.
     */
    @Test
    fun `floors never rise and always reach zero`() {
        for (maxHp in listOf(1, 2, 3, 7, 13, 100, 341, 714)) {
            for (shields in 1..BossShields.MAX_SHIELDS) {
                var previous = maxHp
                for (broken in 0..shields) {
                    val floor = BossShields.floorHp(maxHp, shields, broken)
                    assertTrue(
                        floor <= previous,
                        "maxHp=$maxHp shields=$shields broken=$broken: floor $floor rose above $previous",
                    )
                    assertTrue(floor >= 0, "maxHp=$maxHp shields=$shields broken=$broken: floor $floor is negative")
                    previous = floor
                }
                assertEquals(
                    0,
                    BossShields.floorHp(maxHp, shields, shields),
                    "maxHp=$maxHp shields=$shields: the last chunk must be killable",
                )
            }
        }
    }

    /**
     * On any HP pool big enough to hold them, the boundaries are all distinct.
     *
     * The companion to the sweep above: ties are tolerated where the bar is too small to avoid them,
     * and are a bug anywhere else. Without this, the previous test would pass on an implementation
     * that returned the same floor for every break.
     */
    @Test
    fun `floors are distinct whenever the bar is big enough`() {
        for (maxHp in listOf(50, 100, 341, 714)) {
            for (shields in 1..BossShields.MAX_SHIELDS) {
                val floors = (0..shields).map { BossShields.floorHp(maxHp, shields, it) }
                assertEquals(floors.size, floors.toSet().size, "maxHp=$maxHp shields=$shields: $floors")
            }
        }
    }

    /**
     * Rounding goes **up**, so the remainder is lost from the killable chunk and not from a
     * shielded one — the version a player cannot notice.
     */
    @Test
    fun `uneven hp pools round the floor up`() {
        // 7 HP, 2 shields, three chunks: 7*2/3 = 4.67 -> 5, and 7*1/3 = 2.33 -> 3.
        assertEquals(5, BossShields.floorHp(maxHp = 7, shields = 2, broken = 0))
        assertEquals(3, BossShields.floorHp(maxHp = 7, shields = 2, broken = 1))
        assertEquals(0, BossShields.floorHp(maxHp = 7, shields = 2, broken = 2))
    }

    /**
     * A boss whose HP pool is smaller than its shield count still works, it just collapses.
     *
     * Not a case anyone would author, but it *is* reachable: §2.19 starts a run's party at level 1,
     * and a low-level Pokémon promoted to a boss by a roster has a two-digit HP pool. The property
     * that matters is only that the floors stay descending, which the sweep above already asserts
     * for maxHp=1; this states the intent so nobody later "fixes" it into a rejection.
     */
    @Test
    fun `a tiny hp pool does not break the floors`() {
        assertEquals(1, BossShields.floorHp(maxHp = 1, shields = 5, broken = 0))
        assertEquals(0, BossShields.floorHp(maxHp = 1, shields = 5, broken = 5))
    }

    /** The amount a hit may take before the next shield goes. Zero at the boundary, never negative HP. */
    @Test
    fun `absorbable damage is the distance to the next floor`() {
        assertEquals(25, BossShields.absorbableDamage(currentHp = 100, maxHp = 100, shields = 3, broken = 0))
        assertEquals(0, BossShields.absorbableDamage(currentHp = 75, maxHp = 100, shields = 3, broken = 0))
        // Already past the boundary. Negative is the signal the JS uses to advance the broken count
        // rather than clamp — HP can move without passing through the damage handler.
        assertTrue(BossShields.absorbableDamage(currentHp = 60, maxHp = 100, shields = 3, broken = 0) < 0)
        // Last chunk: everything is absorbable, i.e. nothing is floored.
        assertEquals(25, BossShields.absorbableDamage(currentHp = 25, maxHp = 100, shields = 3, broken = 3))
    }

    @Test
    fun `out of range arguments are refused rather than guessed`() {
        assertFailsWith<IllegalArgumentException> { BossShields.floorHp(maxHp = 0, shields = 1, broken = 0) }
        assertFailsWith<IllegalArgumentException> { BossShields.floorHp(maxHp = 100, shields = 0, broken = 0) }
        assertFailsWith<IllegalArgumentException> {
            BossShields.floorHp(maxHp = 100, shields = BossShields.MAX_SHIELDS + 1, broken = 0)
        }
        assertFailsWith<IllegalArgumentException> { BossShields.floorHp(maxHp = 100, shields = 2, broken = 3) }
    }

    // ---------------------------------------------------------------------------------------
    // The channel: which item id carries which count
    // ---------------------------------------------------------------------------------------

    @Test
    fun `ids are the showdown form of the item name`() {
        assertEquals("bossshield1", BossShields.showdownId(1))
        assertEquals("bossshield5", BossShields.showdownId(5))
        assertFailsWith<IllegalArgumentException> { BossShields.showdownId(0) }
        assertFailsWith<IllegalArgumentException> { BossShields.showdownId(BossShields.MAX_SHIELDS + 1) }
    }

    @Test
    fun `only our ids are recognised as shields`() {
        for (shields in 1..BossShields.MAX_SHIELDS) {
            assertTrue(BossShields.isShieldItem(BossShields.showdownId(shields)))
        }
        assertFalse(BossShields.isShieldItem(null))
        assertFalse(BossShields.isShieldItem("leftovers"))
        // Prefix alone is not enough: a future `bossshieldxyz` belonging to somebody else is not
        // this mechanic, and quietly renaming their boss "Boss Whatever" would be our bug.
        assertFalse(BossShields.isShieldItem("bossshield"))
        assertFalse(BossShields.isShieldItem("bossshield9"))
        assertFalse(BossShields.isShieldItem("bossshieldxyz"))
    }

    /**
     * The two properties of the item fragment that Cobblemon's properties parser cares about.
     *
     * A properties string is split on **spaces first** and each token then on its **first** `=`
     * only. So a space anywhere in this fragment truncates it, and the `=` inside the component
     * brackets must not be the first one. Both failures are silent — the Pokémon is built, it just
     * holds nothing — which is why they are asserted rather than trusted.
     */
    @Test
    fun `the held item fragment survives the properties parser`() {
        val fragment = BossShields.heldItemProperty(3)

        assertFalse(fragment.any { it.isWhitespace() }, "a space would truncate the value: $fragment")
        assertEquals("held_item", fragment.substringBefore('='))
        assertTrue(fragment.substringAfter('=').startsWith("minecraft:shield["), fragment)
        assertTrue(fragment.contains("cobblemon:held_item_effect"), fragment)
        assertTrue(fragment.contains("showdownId:\"bossshield3\""), fragment)
        // Spelled out rather than defaulted: the component's codec is not ours, and an omitted
        // required field fails the parse — silently, as a boss with no shields.
        assertTrue(fragment.contains("consumed:false"), fragment)
        assertTrue(fragment.endsWith("]"), fragment)
    }

    // ---------------------------------------------------------------------------------------
    // The contract with the datapack scripts
    // ---------------------------------------------------------------------------------------

    /**
     * Every shield count [BossShields.MAX_SHIELDS] admits has a script, and the script agrees.
     *
     * This is the test that earns its keep. Raising the constant without adding a file is a change
     * that compiles, passes every other test, and produces bosses that hold an item Showdown does
     * not have — no shields, no messages, nothing in any log. The reverse (a file with no constant)
     * is harmless but is caught too, because a stale file is a stale file.
     *
     * Read out of the classpath rather than off disk so it checks what is actually **packaged**: a
     * script that exists in the source tree and not in the jar is exactly the same outage.
     */
    @Test
    fun `a datapack script ships for every shield count and declares itself correctly`() {
        for (shields in 1..BossShields.MAX_SHIELDS) {
            val path = "/data/cobblemon_roguelite/held_items/boss_shield_$shields.js"
            val source = javaClass.getResource(path)?.readText()
            assertNotNull(source, "no held item script at $path for shields=$shields")

            // Showdown derives the item id from `name`, lowercased and stripped of spaces. This is
            // the actual join between the two sides — if it drifts, `showdownId` names nothing.
            assertTrue(
                source.contains("name: \"Boss Shield $shields\""),
                "$path does not declare name \"Boss Shield $shields\", so its Showdown id is not " +
                    "${BossShields.showdownId(shields)}",
            )
            assertEquals(
                BossShields.showdownId(shields),
                "Boss Shield $shields".lowercase().replace(" ", ""),
                "the id derivation in this test no longer matches Showdown's toID()",
            )
            assertTrue(
                source.contains("rogueliteShields: $shields"),
                "$path does not grant $shields shields",
            )
            // Non-negotiable per §2.32: without it Knock Off or Trick deletes a boss's defining
            // mechanic mid-fight, which reads as a bug rather than as counterplay.
            assertTrue(source.contains("onTakeItem"), "$path does not refuse item removal")
        }
    }
}
