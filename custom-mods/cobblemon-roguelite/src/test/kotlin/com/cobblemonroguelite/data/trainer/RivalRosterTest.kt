package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.wave.WaveDrawStream
import com.cobblemonroguelite.wave.WaveRandom
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rival as the roster sees it: which kind a meeting wave *is*, who it summons, and what the
 * promotion carries with it.
 *
 * ### Why the promotion is the risky part rather than the team
 *
 * §2.36's ladder lands on six waves and only one of them is a wild wave, so most of what this class has
 * to get right is *reconciliation*: [WaveComposition] is a pure function of the wave number and must
 * stay that way (which waves are bosses cannot depend on which datapack is loaded), so the roster is the
 * only thing that can say wave 25 is a rival wave rather than an ordinary trainer wave. Every symptom of
 * getting that wrong is silent in play — a catchable rival at wave 8 ends up in somebody's party (§2.14),
 * a rival counted as a boss takes shields it was never meant to have (§2.32), a rival left as a plain
 * trainer wave gets drawn from a band pool instead.
 *
 * The validator half is here for [TrainerRosterParseTest]'s reason: the reader of these messages has a
 * two-hundred-line schedule open and needs to be told which line, so the assertions are on wording.
 */
class RivalRosterTest {

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("test", path)

    private fun species(path: String) = TeamSpecies(ResourceLocation.fromNamespaceAndPath("cobblemon", path))

    private fun slot(vararg stages: String) = SignatureSlot(listOf(SpeciesLine(stages.map { species(it) })))

    private val schedule = WaveCompositionConfig()
    private val composition = WaveComposition(schedule)

    private fun stage(index: Int) = id("rgl_rival_$index")

    private val meetings = RivalLadder.POKEROGUE_MEETING_WAVES.mapIndexed { index, wave ->
        RivalMeeting(wave, stage(index + 1))
    }

    private fun team(name: String = "example", slots: Int = 6) =
        RivalTeam(name, (1..slots).map { slot("mon$it") })

    private fun ladder(
        meetings: List<RivalMeeting> = this.meetings,
        teams: List<RivalTeam> = listOf(team()),
        partySizes: List<Int> = emptyList(),
    ) = RivalLadder(meetings, teams, partySizes)

    private fun roster(
        rival: RivalLadder? = ladder(),
        bands: List<TrainerBand> = listOf(
            TrainerBand("t", RunOpponent.TRAINER, 1, null, listOf(id("t_a"), id("t_b"))),
            TrainerBand("b", RunOpponent.BOSS, 1, null, listOf(id("b_a"))),
        ),
        fixed: List<FixedEncounter> = emptyList(),
        generated: Map<ResourceLocation, TrainerEntry> = emptyMap(),
        authoredFor: WaveCompositionConfig = schedule,
    ) = TrainerRoster(
        id = id("roster"),
        authoredFor = authoredFor,
        bands = bands,
        fixed = fixed.associateBy { it.wave },
        generated = generated,
        rival = rival,
    )

    // ─── which kind a meeting wave is ──────────────────────────────────────

    @Test
    fun `a meeting wave is a rival wave whatever the schedule called it`() {
        val roster = roster()
        RivalLadder.POKEROGUE_MEETING_WAVES.forEach { wave ->
            assertEquals(
                RunOpponent.RIVAL,
                roster.effectiveKind(wave, composition.kindOf(wave)),
                "wave $wave was left as ${composition.kindOf(wave)}",
            )
        }
    }

    @Test
    fun `every other wave keeps the kind the schedule gave it`() {
        val roster = roster()
        listOf(1, 5, 9, 10, 24, 26, 100, 200).forEach { wave ->
            assertEquals(composition.kindOf(wave), roster.effectiveKind(wave, composition.kindOf(wave)))
        }
    }

    @Test
    fun `a roster with no rival is unchanged in every respect`() {
        // The property that made this shippable without touching any existing roster: null is the state
        // of every file written before §2.36, and it must behave exactly as it did.
        val plain = roster(rival = null)
        assertFalse(plain.isRivalMeeting(8))
        assertNull(plain.pickFor(8, RunOpponent.WILD, seed = 7L))
        assertEquals(composition.planFor(25, 7L), plain.planFor(25, 7L, composition))
        assertTrue(plain.validate(composition).isEmpty(), plain.validate(composition).toString())
    }

    // ─── what the promotion carries ────────────────────────────────────────

    @Test
    fun `promoting wave 8 out of a wild wave takes catchability with it`() {
        // §2.14, and the failure it prevents is concrete: a catchable rival wave hands the player a
        // Pokémon the run was never supposed to let them keep.
        val plan = roster().planFor(8, seed = 7L, composition = composition)
        assertEquals(RunOpponent.RIVAL, plan.kind)
        assertFalse(plan.catchable, "wave 8 was promoted and stayed catchable")
    }

    @Test
    fun `no meeting wave is catchable`() {
        val roster = roster()
        RivalLadder.POKEROGUE_MEETING_WAVES.forEach { wave ->
            assertFalse(roster.planFor(wave, 7L, composition).catchable, "wave $wave was catchable")
        }
    }

    /**
     * A rival takes the ordinary trainer level, not the boss ×1.2 — [RivalLadder] argues why.
     *
     * Asserted from both directions, because "no multiplier" is only meaningful against a number that
     * would have had one: the level must equal the curve's non-boss answer and must *not* equal its boss
     * answer, or the test would pass equally well against a curve with no multiplier at all.
     */
    @Test
    fun `a rival wave takes the trainer level and not the boss multiplier`() {
        val roster = roster()
        listOf(8, 25, 95).forEach { wave ->
            val rng = { WaveRandom.forDraw(7L, wave, WaveDrawStream.LEVEL) }
            val plan = roster.planFor(wave, 7L, composition)
            assertEquals(schedule.curve.levelFor(wave, boss = false, rng = rng()), plan.level, "wave $wave")
            assertNotEquals(
                schedule.curve.levelFor(wave, boss = true, rng = rng()),
                plan.level,
                "wave $wave took the boss multiplier",
            )
        }
    }

    /**
     * A rival rewards as the wave it was *scheduled* as, like every other promotion.
     *
     * Pinned as a decision rather than left as a detail, because it is the one place the rival looks
     * unfinished and is not. `RunProgressTest` already pins the same rule for a promoted Elite Four
     * member, reasoned as: re-pointing a reward table from a roster file would be a balance change made
     * by data, and §2.12 puts routing in the operator's hands. Making the rival the one exception would
     * put two answers in the codebase for one question.
     *
     * It does mean wave 8 rolls the **wild** table, which is a wart and is fixable today with a one-wave
     * `RewardBand`. Asserted so that anyone who decides promotions should route by effective kind changes
     * it deliberately, here and in `RunProgressTest` together.
     */
    @Test
    fun `a rival wave rewards as the wave the schedule called it`() {
        assertEquals(composition.planFor(55, 7L).rewardTable, roster().planFor(55, 7L, composition).rewardTable)
        assertEquals(composition.planFor(8, 7L).rewardTable, roster().planFor(8, 7L, composition).rewardTable)
        assertNull(schedule.rewards.byKind[RunOpponent.RIVAL], "an unreachable byKind entry would be dead config")
    }

    @Test
    fun `a rival takes no boss shields even where the tiers would give a boss four`() {
        val shielded = TeamGenerationRules(bossShields = listOf(BossShieldTier(minWave = 1, shields = 4, members = 6)))
        val roster = roster().copy(generation = shielded)
        val plan = roster.planFor(195, 7L, composition)
        val built = assertNotNull(roster.teamFor(stage(6), 195, plan.level, plan.kind == RunOpponent.BOSS, 7L))
        assertTrue(built.members.all { it.shields == 0 })
    }

    // ─── who a meeting wave summons ────────────────────────────────────────

    @Test
    fun `each meeting summons its own stage, in order`() {
        val roster = roster()
        RivalLadder.POKEROGUE_MEETING_WAVES.forEachIndexed { index, wave ->
            val pick = assertNotNull(roster.pickFor(wave, RunOpponent.RIVAL, seed = 7L), "wave $wave")
            assertEquals(stage(index + 1), pick.trainerId)
            assertEquals(TrainerPickSource.RIVAL, pick.source)
            assertNull(pick.bandId, "a rival did not come from a band")
        }
    }

    @Test
    fun `the same stage turns up for every seed, because a rival is not a draw`() {
        val roster = roster()
        assertEquals(setOf(stage(3)), (1L..40L).map { roster.trainerFor(55, RunOpponent.RIVAL, it)!! }.toSet())
    }

    @Test
    fun `a meeting resolves even when the caller passes the scheduled kind`() {
        // Forgiving in the same way a declared promotion is: a meeting always promotes — there is no
        // "replace the trainer but leave the wave alone" reading of a ladder — so wave 8 must answer even
        // when asked as the WILD wave the schedule called it.
        val roster = roster()
        assertEquals(stage(1), roster.pickFor(8, RunOpponent.WILD, seed = 7L)?.trainerId)
        assertEquals(stage(2), roster.pickFor(25, RunOpponent.TRAINER, seed = 7L)?.trainerId)
    }

    @Test
    fun `a fixed encounter still beats the ladder`() {
        // "Fixed beats everything" kept without an exception. The combination is a validation error, so
        // a loaded roster never reaches this — but a roster built in code must not resolve by field order.
        val roster = roster(fixed = listOf(FixedEncounter(55, id("e4_1"), RunOpponent.BOSS)))
        val pick = assertNotNull(roster.pickFor(55, RunOpponent.BOSS, seed = 7L))
        assertEquals(id("e4_1"), pick.trainerId)
        assertEquals(TrainerPickSource.FIXED, pick.source)
        assertNull(roster.teamFor(id("e4_1"), 55, 60, boss = true, seed = 7L), "the fixed trainer took the rival's team")
    }

    // ─── the team the roster hands over ────────────────────────────────────

    @Test
    fun `the roster builds the rival's team from the ladder and not from generated`() {
        val roster = roster()
        val built = assertNotNull(roster.teamFor(stage(3), 55, level = 60, boss = false, seed = 7L))
        assertEquals(4, built.members.size, "meeting 3 should bring four")
        assertTrue(built.members.all { it.level == 60 })
    }

    @Test
    fun `a different trainer on a meeting wave gets no rival team`() {
        // The id guard. Without it a fixed encounter that beat the ladder would be handed the rival's
        // growing party, which is the one way this could go wrong and still look like it worked.
        assertNull(roster().teamFor(id("someone_else"), 55, 60, boss = false, seed = 7L))
    }

    @Test
    fun `a generated leader on a non-meeting wave is untouched by the ladder`() {
        val leader = TrainerEntry(id("t_a"), listOf(slot("onix", "steelix"), slot("geodude", "golem")))
        val roster = roster(generated = mapOf(leader.trainerId to leader))
        val built = assertNotNull(roster.teamFor(id("t_a"), 15, 30, boss = false, seed = 7L))
        assertEquals(listOf("onix", "geodude"), built.members.map { it.species.id.path })
    }

    // ─── things the type system refuses outright ───────────────────────────

    @Test
    fun `a band cannot be a rival band`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            TrainerBand("r", RunOpponent.RIVAL, 1, null, listOf(id("rgl_rival_1")))
        }
        assertTrue("rival" in failure.message!! && "pool" in failure.message!!, failure.message!!)
    }

    @Test
    fun `a fixed encounter cannot promote a wave to a rival`() {
        // Because promotion carries no meeting index, so there would be no party size and no team.
        val failure = assertFailsWith<IllegalArgumentException> {
            FixedEncounter(8, id("rgl_rival_1"), RunOpponent.RIVAL)
        }
        assertTrue("rival" in failure.message!!, failure.message!!)
    }

    @Test
    fun `an out-of-order ladder is refused rather than sorted`() {
        // Sorting for the author would move the party sizes onto different waves — silently, and
        // differently from what they wrote, because the POSITION is the meeting number.
        val failure = assertFailsWith<IllegalArgumentException> {
            RivalLadder(listOf(RivalMeeting(25, stage(1)), RivalMeeting(8, stage(2))), listOf(team()))
        }
        assertTrue("ascend" in failure.message!!, failure.message!!)
    }

    // ─── validation ────────────────────────────────────────────────────────

    private fun problems(roster: TrainerRoster, on: WaveComposition = composition) = roster.validate(on)

    private fun List<String>.mentions(vararg fragments: String) =
        any { line -> fragments.all { it in line } }

    @Test
    fun `a sound rival ladder reports nothing`() {
        assertTrue(problems(roster()).isEmpty(), problems(roster()).toString())
    }

    @Test
    fun `rival meetings count as band coverage`() {
        // Five of the six meetings land on scheduled TRAINER waves, so a roster whose trainer band stops
        // short would otherwise be told to extend it over waves the rival already owns.
        val short = roster(
            bands = listOf(
                TrainerBand("t", RunOpponent.TRAINER, 1, 190, listOf(id("t_a"))),
                TrainerBand("b", RunOpponent.BOSS, 1, null, listOf(id("b_a"))),
            ),
        )
        // Wave 195 is the last trainer-scheduled wave past 190 and is a meeting, so no gap is reported.
        assertTrue(problems(short).isEmpty(), problems(short).toString())

        // Without the ladder the same roster does have a hole there, which is what proves the exemption
        // is doing the work rather than the gap check having gone quiet.
        assertTrue(problems(short.copy(rival = null)).mentions("no trainer band covers", "195"), problems(short.copy(rival = null)).toString())
    }

    @Test
    fun `a meeting past the end of the run is named`() {
        val shortRun = schedule.copy(runLength = 150)
        val roster = roster(authoredFor = shortRun)
        val found = problems(roster, WaveComposition(shortRun))
        assertTrue(found.mentions("rival meeting 6", "195", "past the end"), found.toString())
    }

    @Test
    fun `a meeting sharing a wave with a fixed encounter is rejected`() {
        val roster = roster(fixed = listOf(FixedEncounter(55, id("e4_1"))))
        val found = problems(roster)
        assertTrue(found.mentions("wave 55", "rival meeting 3", "fixed encounter"), found.toString())
    }

    @Test
    fun `a meeting on a boss wave is named as a boss removed from the run`() {
        // §2.19 sizes the whole roster against the count of boss battles, so this is the same class of
        // problem as a fixed trainer overriding a boss wave — and it is reachable by moving one interval.
        val roster = roster(rival = ladder(meetings = listOf(RivalMeeting(20, stage(1)))))
        val found = problems(roster)
        assertTrue(found.mentions("wave 20", "boss wave", "removes a boss"), found.toString())
    }

    @Test
    fun `a rival stage in a band pool is named`() {
        // The expensive one to diagnose: the same character, out of order, with the wrong meeting's team.
        val roster = roster(
            bands = listOf(
                TrainerBand("t", RunOpponent.TRAINER, 1, null, listOf(id("t_a"), stage(4))),
                TrainerBand("b", RunOpponent.BOSS, 1, null, listOf(id("b_a"))),
            ),
        )
        val found = problems(roster)
        assertTrue(found.mentions("rgl_rival_4", "band pool", "out of order"), found.toString())
    }

    @Test
    fun `a rival stage with a generated entry is named once, not twice`() {
        val roster = roster(generated = mapOf(stage(2) to TrainerEntry(stage(2), listOf(slot("onix")))))
        val found = problems(roster)
        assertTrue(found.mentions("rgl_rival_2", "generated entry"), found.toString())
        // The generic "never fought" message must not also fire: one mistake earning two messages is how
        // the sharper of the two gets skimmed past.
        assertFalse(found.mentions("never fought"), found.toString())
    }

    @Test
    fun `a rival team too short for its last meeting is named with the number it needed`() {
        val roster = roster(rival = ladder(teams = listOf(team("kanto", slots = 4))))
        val found = problems(roster)
        assertTrue(found.mentions("'kanto'", "4 slots", "asks for 6"), found.toString())
    }

    @Test
    fun `shortening the run does not also demand longer rival teams`() {
        // Measured against the deepest REACHABLE meeting. An operator who cut the run to 30 waves has two
        // meetings left and needs three slots, not six — being told otherwise would send them to fix
        // content for meetings they just deleted.
        val shortRun = schedule.copy(runLength = 30)
        val roster = roster(
            rival = ladder(meetings = meetings.take(2), teams = listOf(team("kanto", slots = 3))),
            authoredFor = shortRun,
        )
        assertTrue(problems(roster, WaveComposition(shortRun)).isEmpty(), problems(roster, WaveComposition(shortRun)).toString())
    }

    @Test
    fun `waveCount cannot answer for rivals, and says so by answering zero`() {
        // Not a bug to route around here: a rival count is roster.rival.meetings.size, and answering it
        // from the composition would mean the composition reading datapack content.
        assertEquals(0, composition.waveCount(RunOpponent.RIVAL))
        assertEquals(6, roster().rival!!.meetings.size)
    }
}
