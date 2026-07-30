package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.integration.RunOpponent
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §2.36's rival: the one opponent whose content at wave 145 is constrained by what it brought at wave 25.
 *
 * ### Why these are the tests that matter
 *
 * Every other opponent in the mode is correct if it is *reproducible* — same `(seed, wave)`, same team
 * (§2.3). A rival has a second obligation on top of that, and it is the one that cannot be checked by
 * looking at one wave: the team it brings to meeting three must **contain** the team it brought to
 * meeting two. Get that wrong and nothing crashes, nothing logs, and no single-wave assertion notices —
 * the player just meets six strangers wearing the same face, which is precisely the thing §2.36 says a
 * rival is the opposite of.
 *
 * So the property under test throughout is a *relation between meetings*, not a fact about one. The two
 * halves of it are separated deliberately, because they are keyed on different things and the whole
 * design turns on that: **which** Pokémon is run-scoped ([RivalLadder.RUN_SCOPED]) and must not move
 * between meetings, while **how evolved** is wave-scoped and must.
 *
 * Nothing here boots a server — the same split [TrainerTeamGeneratorTest] describes. What still needs
 * the dev VM is that the properties strings build, which is an assertion about Cobblemon rather than
 * about us.
 */
class RivalTeamGeneratorTest {

    private fun species(path: String) = TeamSpecies(ResourceLocation.fromNamespaceAndPath("cobblemon", path))

    private fun line(vararg stages: String, weight: Double = 1.0) =
        SpeciesLine(stages.map { species(it) }, weight)

    private fun slot(vararg alternatives: SpeciesLine) = SignatureSlot(alternatives.toList())

    private fun stage(index: Int) = ResourceLocation.fromNamespaceAndPath("test", "rgl_rival_$index")

    /** §2.36's schedule, which is what the mechanism has to fit. */
    private val meetings = RivalLadder.POKEROGUE_MEETING_WAVES.mapIndexed { index, wave ->
        RivalMeeting(wave, stage(index + 1))
    }

    /**
     * EXAMPLE DATA, not content. Six single-stage slots, each a genuine coin flip, chosen so that the
     * continuity assertions are about the *draw* and not about the evolution schedule — a single-stage
     * line brings the same Pokémon at every wave, so any difference between meetings is the slot pick
     * moving, which is the failure being hunted. Multi-stage lines get their own fixture below.
     */
    private fun flatTeam(id: String) = RivalTeam(
        id = id,
        slots = (1..6).map { n -> slot(line("flat${n}a"), line("flat${n}b")) },
    )

    private val ladder = RivalLadder(meetings = meetings, teams = listOf(flatTeam("solo")))

    private val rules = TeamGenerationRules()

    private fun teamAt(wave: Int, seed: Long = 7L, on: RivalLadder = ladder, using: TeamGenerationRules = rules) =
        RivalTeamGenerator.generate(on, wave, level = 50, seed = seed, rules = using)

    private fun names(wave: Int, seed: Long = 7L, on: RivalLadder = ladder) =
        teamAt(wave, seed, on)!!.members.map { it.species.id.path }

    // ─── the growing team ──────────────────────────────────────────────────

    /**
     * The whole mechanic in one assertion: every meeting's team is a prefix of the next one's.
     *
     * This is what would break if anyone keyed [com.cobblemonroguelite.wave.WaveDrawStream.RIVAL_TEAM] on
     * the wave "for consistency with the rest of the module", or made
     * [TrainerTeamGenerator.pickAlternative] skip its draw for a one-alternative slot.
     */
    @Test
    fun `each meeting brings the previous meeting's team plus one`() {
        val byMeeting = RivalLadder.POKEROGUE_MEETING_WAVES.map { names(it) }
        byMeeting.zipWithNext { earlier, later ->
            assertTrue(
                later.size >= earlier.size && later.take(earlier.size) == earlier,
                "the rival's team stopped being a prefix: $earlier then $later",
            )
        }
        assertEquals(listOf(2, 3, 4, 5, 6, 6), byMeeting.map { it.size })
    }

    @Test
    fun `the rival's starter is the same Pokemon at every meeting`() {
        // The single most visible symptom of a wave-keyed draw, and the one a player would report as
        // "the rival isn't the same person".
        val starters = RivalLadder.POKEROGUE_MEETING_WAVES.map { names(it).first() }.toSet()
        assertEquals(1, starters.size, "the starter changed species between meetings: $starters")
    }

    /**
     * The other axis, and it must *not* be frozen: the same Pokémon, further evolved.
     *
     * Asserted with the shared [EvolutionSchedule] rather than a bespoke one, because the point of
     * sharing it with generated trainers is that a rival at wave 95 is as far evolved as a leader at
     * wave 95 — a rival with its own schedule would drift out of step with the fights around it.
     */
    @Test
    fun `the rival's Pokemon evolve with the wave while staying the same line`() {
        val lines = RivalTeam("evolving", listOf(slot(line("mareep", "flaaffy", "ampharos"))))
        val single = RivalLadder(meetings = meetings, teams = listOf(lines), partySizes = listOf(1))

        // stage_waves = [20], fully_evolved_from = 80 — §2.30's defaults, shared with trainers.
        assertEquals("mareep", names(8, on = single).single(), "wave 8 is below the first stage threshold")
        assertEquals("flaaffy", names(25, on = single).single(), "wave 25 is past it")
        assertEquals("ampharos", names(95, on = single).single(), "wave 95 is past fully_evolved_from")
    }

    @Test
    fun `a team shorter than the ramp arrives short rather than failing`() {
        // The roster loader reports this (TrainerRoster.validate) precisely so it does not have to be an
        // exception here: refusing at the sixth meeting would cost a player a run they had nearly
        // finished, over a roster one slot too short.
        val short = RivalLadder(meetings = meetings, teams = listOf(RivalTeam("short", listOf(slot(line("only"))))))
        assertEquals(1, teamAt(195, on = short)!!.members.size)
    }

    // ─── determinism ───────────────────────────────────────────────────────

    @Test
    fun `the same seed produces the same rival team at every meeting`() {
        RivalLadder.POKEROGUE_MEETING_WAVES.forEach { wave ->
            assertEquals(teamAt(wave), teamAt(wave), "meeting at wave $wave is not reproducible")
        }
    }

    /**
     * The replay a resumed run performs, over a full 200-wave run and both generators at once.
     *
     * Together rather than separately because the two share [com.cobblemonroguelite.wave.WaveDrawStream]
     * and a stream collision is exactly the bug that would not show up in either half alone: a rival
     * drawing on the trainer stream would still be reproducible, and would still be a rival whose team
     * moves whenever a trainer band is edited.
     */
    @Test
    fun `a full run replays identically for both generators`() {
        val schedule = WaveCompositionConfig()
        val composition = WaveComposition(schedule)
        val seed = -8_123_456_789L
        val brock = TrainerEntry(
            trainerId = ResourceLocation.fromNamespaceAndPath("test", "rgl_leader"),
            signature = listOf(slot(line("onix", "steelix")), slot(line("geodude", "graveler", "golem"))),
        )

        fun play(): Map<Int, GeneratedTeam> = (1..schedule.runLength).mapNotNull { wave ->
            val team = when {
                ladder.isMeeting(wave) -> teamAt(wave, seed)
                composition.kindOf(wave) != RunOpponent.WILD ->
                    TrainerTeamGenerator.generate(brock, wave, 50, boss = false, seed = seed, rules = rules)
                else -> null
            }
            team?.let { wave to it }
        }.toMap()

        val first = play()
        // Six rival meetings, and 40 non-wild waves of which five are rival meetings too (only wave 8
        // is a wild wave under 5/10), so the union is 41 waves that put a team on the field.
        assertEquals(41, first.size)
        assertEquals(first, play())
    }

    @Test
    fun `a wave that is not a meeting has no rival team`() {
        assertNull(teamAt(9), "wave 9 is not a meeting")
        assertNull(teamAt(24), "wave 24 is one before a meeting — the off-by-one worth pinning")
        assertNull(teamAt(200), "the final wave is not a meeting under §2.36's schedule")
    }

    @Test
    fun `different runs meet different rival teams`() {
        // If the slot draw were a constant dressed up as a draw, this set would hold one entry.
        val leads = (1L..60L).map { seed -> names(55, seed).first() }.toSet()
        assertEquals(setOf("flat1a", "flat1b"), leads)
    }

    // ─── which rival ───────────────────────────────────────────────────────

    @Test
    fun `a one-team ladder gives every run the same rival`() {
        val picked = (1L..40L).map { ladder.teamFor(it).id }.toSet()
        assertEquals(setOf("solo"), picked)
    }

    @Test
    fun `several teams are drawn per run and never per meeting`() {
        val many = RivalLadder(
            meetings = meetings,
            teams = listOf(flatTeam("kanto"), flatTeam("johto"), flatTeam("hoenn")),
        )
        // Varies across runs...
        assertTrue((1L..80L).map { many.teamFor(it).id }.toSet().size > 1, "the identity draw is a constant")
        // ...and is fixed inside one. Asserted on the ladder rather than through the team, because two
        // identities with the same slots would be indistinguishable downstream — which is the point:
        // this is the assertion that the DRAW is run-scoped, not that its consequences happen to agree.
        repeat(40) { seed ->
            val chosen = many.teamFor(seed.toLong()).id
            RivalLadder.POKEROGUE_MEETING_WAVES.forEach {
                assertEquals(chosen, many.teamFor(seed.toLong()).id, "seed $seed changed rival by wave $it")
            }
        }
    }

    // ─── what a rival is not given ─────────────────────────────────────────

    /**
     * §2.32 shields belong to boss waves, and a rival is not one — [RivalLadder] argues why.
     *
     * The tier below starts at wave 1, so a rival that took shields would take four of them at its first
     * meeting and lose its item slot for the whole run.
     */
    @Test
    fun `a rival never carries boss shields, however low the tier starts`() {
        val shielded = TeamGenerationRules(bossShields = listOf(BossShieldTier(minWave = 1, shields = 4, members = 6)))
        RivalLadder.POKEROGUE_MEETING_WAVES.forEach { wave ->
            assertTrue(
                teamAt(wave, using = shielded)!!.members.all { it.shields == 0 },
                "wave $wave gave the rival shields",
            )
        }
    }

    @Test
    fun `a rival draws held items from the trainer tier and not the boss one`() {
        val bossOnly = TeamGenerationRules(
            heldItems = listOf(
                HeldItemTier(
                    minWave = 1,
                    boss = true,
                    chance = 1.0,
                    items = listOf(HeldItemChoice(ResourceLocation.fromNamespaceAndPath("cobblemon", "leftovers"))),
                ),
            ),
        )
        assertTrue(teamAt(55, using = bossOnly)!!.members.all { it.heldItem == null })

        val everyone = TeamGenerationRules(
            heldItems = listOf(
                HeldItemTier(
                    minWave = 1,
                    chance = 1.0,
                    items = listOf(HeldItemChoice(ResourceLocation.fromNamespaceAndPath("cobblemon", "leftovers"))),
                ),
            ),
        )
        assertTrue(teamAt(55, using = everyone)!!.members.all { it.heldItem != null })
    }

    /**
     * Items are the one part of a rival's team allowed to move between meetings, so they are drawn on the
     * per-wave stream. Pinned because the opposite mistake — freezing items along with the species — would
     * make a rival's berry as immutable as its starter for no reason anyone wrote down.
     */
    @Test
    fun `held items are allowed to differ between meetings`() {
        val varied = TeamGenerationRules(
            heldItems = listOf(
                HeldItemTier(
                    minWave = 1,
                    chance = 0.5,
                    items = (1..4).map { HeldItemChoice(ResourceLocation.fromNamespaceAndPath("cobblemon", "item$it")) },
                ),
            ),
        )
        val perMeeting = RivalLadder.POKEROGUE_MEETING_WAVES.map { wave ->
            teamAt(wave, using = varied)!!.members.first().heldItem?.path
        }
        assertNotEquals(1, perMeeting.toSet().size, "every meeting drew the same item — is the stream wave-keyed?")
    }

    // ─── the ramp ──────────────────────────────────────────────────────────

    @Test
    fun `the default ramp is two at the first meeting and one more each time, capped at six`() {
        assertEquals(listOf(2, 3, 4, 5, 6, 6), meetings.indices.map { ladder.partySizeAt(it) })
    }

    @Test
    fun `the default ramp is derived, so a ladder of any length gets a coherent one`() {
        val eight = RivalLadder(
            meetings = (1..8).map { RivalMeeting(it * 10 - 5, stage(it)) },
            teams = listOf(flatTeam("solo")),
        )
        assertEquals(listOf(2, 3, 4, 5, 6, 6, 6, 6), eight.meetings.indices.map { eight.partySizeAt(it) })

        val two = RivalLadder(meetings = meetings.take(2), teams = listOf(flatTeam("solo")))
        assertEquals(listOf(2, 3), two.meetings.indices.map { two.partySizeAt(it) })
    }

    @Test
    fun `an explicit ramp plateaus past its last entry rather than throwing`() {
        // Authored separately from the meetings, so the shorter list must not be able to break a run —
        // the same clamp SpeciesLine.stageAt makes.
        val stated = RivalLadder(meetings = meetings, teams = listOf(flatTeam("solo")), partySizes = listOf(1, 4))
        assertEquals(listOf(1, 4, 4, 4, 4, 4), meetings.indices.map { stated.partySizeAt(it) })
        assertEquals(4, teamAt(195, on = stated)!!.members.size)
    }

    // ─── the schedule against the shipping composition ─────────────────────

    /**
     * The fact the whole promotion story rests on: under 200/5/10 exactly one of §2.36's six meetings
     * lands on a wild wave, and none lands on a boss wave.
     *
     * Worth asserting rather than remembering, because it decides what the ladder has to do. Five
     * meetings only re-point waves that were already fights; wave 8 is the one that has to be promoted
     * out of a catchable wild encounter. If the boss interval ever moved so that a meeting collided with
     * a boss wave, this would fail — and [TrainerRoster.validate] reports that collision for exactly the
     * reason this test exists.
     */
    @Test
    fun `only wave 8 of the rival schedule needs promoting, and none of them steals a boss`() {
        val composition = WaveComposition(WaveCompositionConfig())
        val kinds = RivalLadder.POKEROGUE_MEETING_WAVES.associateWith { composition.kindOf(it) }
        assertEquals(listOf(8), kinds.filterValues { it == RunOpponent.WILD }.keys.toList())
        assertEquals(emptyList(), kinds.filterValues { it == RunOpponent.BOSS }.keys.toList())
        assertEquals(5, kinds.count { it.value == RunOpponent.TRAINER })
    }
}
