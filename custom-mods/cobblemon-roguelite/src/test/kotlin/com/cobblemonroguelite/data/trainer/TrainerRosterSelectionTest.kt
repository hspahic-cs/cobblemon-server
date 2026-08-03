package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.wave.WaveDrawStream
import com.cobblemonroguelite.wave.WaveRandom
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Selection, which is the half of this layer a player can feel.
 *
 * The property under test throughout is §2.3's: a run resumed from a checkpoint meets the *same*
 * trainer it would have met before the disconnect. Nothing here boots a server — selection is a pure
 * function of `(seed, wave, kind)` by construction, and a test that needed a world would be evidence
 * it had stopped being one.
 */
class TrainerRosterSelectionTest {

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("test", path)

    private fun band(id: String, kind: RunOpponent, min: Int, max: Int?, vararg trainers: String) =
        TrainerBand(id, kind, min, max, trainers.map { id(it) })

    private val schedule = WaveCompositionConfig(runLength = 200, trainerInterval = 5, bossInterval = 10)
    private val composition = WaveComposition(schedule)

    private fun roster(fixed: List<FixedEncounter> = emptyList()) = TrainerRoster(
        id = id("roster"),
        authoredFor = schedule,
        bands = listOf(
            band("t_early", RunOpponent.TRAINER, 1, 100, "t_a", "t_b", "t_c"),
            band("t_late", RunOpponent.TRAINER, 101, null, "t_d", "t_e"),
            band("b_early", RunOpponent.BOSS, 1, 100, "b_a", "b_b"),
            band("b_late", RunOpponent.BOSS, 101, null, "b_c"),
        ),
        fixed = fixed.associateBy { it.wave },
    )

    /** Every non-wild wave of a full run, as the run loop would ask for them. */
    private fun sweep(roster: TrainerRoster, seed: Long): Map<Int, TrainerPick?> =
        (1..schedule.runLength)
            .filter { composition.kindOf(it) != RunOpponent.WILD || roster.isFixed(it) }
            .associateWith { roster.pickFor(it, roster.effectiveKind(it, composition.kindOf(it)), seed) }

    @Test
    fun `a wave draws from the band covering it, by kind`() {
        val roster = roster()
        // 15 is a trainer wave (multiple of 5, not of 10); 20 is a boss wave.
        assertTrue(roster.pickFor(15, RunOpponent.TRAINER, 7L)!!.trainerId.path.startsWith("t_"))
        assertTrue(roster.pickFor(20, RunOpponent.BOSS, 7L)!!.trainerId.path.startsWith("b_"))
        assertEquals("t_early", roster.pickFor(15, RunOpponent.TRAINER, 7L)!!.bandId)
        assertEquals("t_late", roster.pickFor(155, RunOpponent.TRAINER, 7L)!!.bandId)
        assertEquals(TrainerPickSource.BAND, roster.pickFor(15, RunOpponent.TRAINER, 7L)!!.source)
    }

    @Test
    fun `a wild wave has no trainer`() {
        assertNull(roster().pickFor(3, RunOpponent.WILD, 7L))
    }

    @Test
    fun `the same seed and wave always give the same trainer`() {
        // The resume guarantee, stated the only way it can be: two independent rosters, two
        // independent sweeps, no shared state between them.
        val first = sweep(roster(), seed = 0x1234_5678L)
        val second = sweep(roster(), seed = 0x1234_5678L)
        assertEquals(first, second)
        assertTrue(first.values.all { it != null }, "the fixture roster covers every non-wild wave")
    }

    @Test
    fun `a different seed gives a different run`() {
        val a = sweep(roster(), seed = 1L)
        val b = sweep(roster(), seed = 2L)
        // Not "every wave differs" — pools of two and three collide often, and asserting total
        // divergence would be asserting something false about a fair draw.
        assertTrue(a.keys.count { a[it] != b[it] } > a.size / 4, "two seeds produced near-identical runs: $a vs $b")
    }

    @Test
    fun `seeds one apart do not produce near-identical runs`() {
        // The failure this guards is a real one: `seed = System.currentTimeMillis()` hands two
        // players starting together adjacent seeds, and an unmixed stream would give them the same
        // ladder. WaveRandom folds the inputs for this reason; this asserts the roster benefits.
        val a = sweep(roster(), seed = 999_000L)
        val b = sweep(roster(), seed = 999_001L)
        assertTrue(a.keys.count { a[it] != b[it] } > a.size / 4, "adjacent seeds produced near-identical runs")
    }

    @Test
    fun `the whole pool is reachable`() {
        // A draw that always lands on index 0 is deterministic too, and would pass every test above.
        val roster = roster()
        val drawn = (1..100).filter { composition.kindOf(it) == RunOpponent.TRAINER }
            .map { roster.pickFor(it, RunOpponent.TRAINER, 42L)!!.trainerId.path }
            .toSet()
        assertEquals(setOf("t_a", "t_b", "t_c"), drawn)
    }

    @Test
    fun `the trainer draw does not move with the level draw`() {
        // Both are functions of (seed, wave). Sharing a stream would correlate them — every run that
        // rolled a high level would meet the same trainer — and is why WaveDrawStream salts per draw.
        val seed = 88L
        repeat(20) { wave ->
            val trainer = WaveRandom.forDraw(seed, wave + 1, WaveDrawStream.TRAINER).nextLong()
            val level = WaveRandom.forDraw(seed, wave + 1, WaveDrawStream.LEVEL).nextLong()
            val species = WaveRandom.forDraw(seed, wave + 1, WaveDrawStream.SPECIES).nextLong()
            assertNotEquals(level, trainer, "trainer and level draws share a stream at wave ${wave + 1}")
            assertNotEquals(species, trainer, "trainer and species draws share a stream at wave ${wave + 1}")
        }
    }

    @Test
    fun `appending to a pool re-points waves an in-flight run has not reached`() {
        // Pins the honest version of the rule, because the tempting version — "appending is safe,
        // reordering is not" — is false: the index is the uniform draw scaled by the pool size, so
        // adding a fifth trainer moves waves that were already decided. Anyone documenting this
        // format to server owners needs to say so, and a test is what stops the comment drifting
        // back to the comfortable claim.
        val short = roster()
        val longer = short.copy(
            bands = short.bands.map {
                if (it.id == "t_early") it.copy(trainers = it.trainers + id("t_x")) else it
            },
        )
        val waves = (1..100).filter { composition.kindOf(it) == RunOpponent.TRAINER }
        val moved = waves.count {
            short.pickFor(it, RunOpponent.TRAINER, 3L) != longer.pickFor(it, RunOpponent.TRAINER, 3L)
        }
        assertTrue(moved > 0, "appending to a pool must be understood to re-point existing runs")

        // The seeded stream itself is untouched by the edit — the movement is the index, not the
        // draw. That distinction is what makes the run still deterministic afterwards.
        assertEquals(
            WaveRandom.forDraw(3L, 15, WaveDrawStream.TRAINER).nextDouble(),
            WaveRandom.forDraw(3L, 15, WaveDrawStream.TRAINER).nextDouble(),
        )
    }

    @Test
    fun `a fixed encounter beats the band pool`() {
        val roster = roster(listOf(FixedEncounter(wave = 50, trainerId = id("rival"))))
        val pick = roster.pickFor(50, RunOpponent.BOSS, 7L)!!
        assertEquals(id("rival"), pick.trainerId)
        assertEquals(TrainerPickSource.FIXED, pick.source)
        assertNull(pick.bandId)
        // …and only that wave.
        assertEquals("b_early", roster.pickFor(60, RunOpponent.BOSS, 7L)!!.bandId)
    }

    @Test
    fun `a fixed encounter is the same for every seed`() {
        val roster = roster(listOf(FixedEncounter(wave = 50, trainerId = id("rival"))))
        val seeds = (1L..50L).map { roster.pickFor(50, RunOpponent.BOSS, it)!!.trainerId }.toSet()
        assertEquals(setOf(id("rival")), seeds)
    }

    @Test
    fun `an undeclared fixed encounter on a wild wave does not fire`() {
        // Matches what validate() says about it. A validator that reports a dead entry while the
        // runtime quietly honours it is worse than either behaviour on its own.
        val roster = roster(listOf(FixedEncounter(wave = 183, trainerId = id("e4"))))
        assertEquals(RunOpponent.WILD, composition.kindOf(183))
        assertNull(roster.pickFor(183, RunOpponent.WILD, 7L))
    }

    @Test
    fun `a declared fixed encounter promotes a wild wave`() {
        // The reason this mechanism exists: PokéRogue's E4 sits at 182/184/186/188 and their
        // champion at 190, and four of those five are wild waves under any 5/10 schedule.
        val roster = roster(
            listOf(182, 184, 186, 188, 190).map { FixedEncounter(it, id("e4_$it"), RunOpponent.BOSS) },
        )
        listOf(182, 184, 186, 188, 190).forEach { wave ->
            assertEquals(RunOpponent.BOSS, roster.effectiveKind(wave, composition.kindOf(wave)))
            assertEquals(id("e4_$wave"), roster.pickFor(wave, RunOpponent.WILD, 7L)?.trainerId)
        }
        assertTrue(roster.validate(composition).isEmpty(), "the E4 schedule must validate clean")
    }

    @Test
    fun `a promoted wave stops being catchable and takes the boss level`() {
        // All three consequences of a promotion, together, because missing any one of them is silent:
        // a catchable Elite Four member ends up in the player's party, and a boss at trainer level is
        // simply an easy wave nobody reports.
        val roster = roster(listOf(FixedEncounter(184, id("e4"), RunOpponent.BOSS)))
        val plain = composition.planFor(184, seed = 5L)
        val promoted = roster.planFor(184, seed = 5L, composition = composition)

        assertEquals(RunOpponent.WILD, plain.kind)
        assertTrue(plain.catchable)
        assertEquals(RunOpponent.BOSS, promoted.kind)
        assertFalse(promoted.catchable)
        // Both clamp at 100 this deep, so the multiplier is not observable here — the check that
        // matters is that the level came from the curve at all rather than being left at the wild
        // value by accident, which a shallower promoted wave shows.
        assertEquals(plain.level, promoted.level)

        val shallow = roster(listOf(FixedEncounter(37, id("mini"), RunOpponent.BOSS)))
        assertTrue(
            shallow.planFor(37, 5L, composition).level > composition.planFor(37, 5L).level,
            "a promotion to BOSS must pick up the boss multiplier",
        )
    }

    @Test
    fun `an unpromoted wave plans exactly as the composition planned it`() {
        val roster = roster(listOf(FixedEncounter(50, id("rival"))))
        assertEquals(composition.planFor(50, 5L), roster.planFor(50, 5L, composition))
    }
}
