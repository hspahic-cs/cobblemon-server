package com.cobblemonroguelite.run

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.data.trainer.FixedEncounter
import com.cobblemonroguelite.data.trainer.RivalLadder
import com.cobblemonroguelite.data.trainer.RivalMeeting
import com.cobblemonroguelite.data.trainer.RivalTeam
import com.cobblemonroguelite.data.trainer.SignatureSlot
import com.cobblemonroguelite.data.trainer.SpeciesLine
import com.cobblemonroguelite.data.trainer.TeamSpecies
import com.cobblemonroguelite.data.trainer.TrainerBand
import com.cobblemonroguelite.data.trainer.TrainerPickSource
import com.cobblemonroguelite.data.trainer.TrainerRoster
import com.cobblemonroguelite.integration.RunOpponent
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The no-repeat rule, and the guarantee it must not cost.
 *
 * §2.19 is what makes this worth code: twenty trainer waves and twenty boss waves drawn from bands an
 * author has to keep small enough to write means visible repeats, and "I fought that same trainer
 * three waves ago" is the first thing that makes a two-hundred-wave run feel thin. What the rule must
 * *not* cost is §2.3's — a run resumed from a checkpoint meets the same opponent it would have met —
 * which is why half of these tests are about replay rather than about variety.
 */
class RunTrainerSelectionTest {

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("test", path)

    private val schedule = WaveCompositionConfig(runLength = 200, trainerInterval = 5, bossInterval = 10)
    private val composition = WaveComposition(schedule)

    private fun roster(
        trainers: List<String> = listOf("t_a", "t_b", "t_c", "t_d", "t_e", "t_f"),
        bosses: List<String> = listOf("b_a", "b_b", "b_c", "b_d", "b_e"),
        fixed: List<FixedEncounter> = emptyList(),
    ) = TrainerRoster(
        id = id("roster"),
        authoredFor = schedule,
        bands = listOf(
            TrainerBand("t", RunOpponent.TRAINER, 1, null, trainers.map { id(it) }),
            TrainerBand("b", RunOpponent.BOSS, 1, null, bosses.map { id(it) }),
        ),
        fixed = fixed.associateBy { it.wave },
    )

    /**
     * A whole run as the controller drives it: plan the wave, then record the wave once it is won.
     * Returns the opponent of every wave that had one.
     */
    private fun sweep(
        roster: TrainerRoster,
        seed: Long,
        memory: RunTrainerMemory = RunTrainerMemory(),
        waves: IntRange = 1..200,
    ): Map<Int, ResourceLocation> {
        val loaded = RunRoster.Loaded(roster.id, roster)
        val met = linkedMapOf<Int, ResourceLocation>()
        for (wave in waves) {
            val trainer = RunProgress.planFor(wave, seed, composition, loaded, memory).trainer ?: continue
            met[wave] = trainer.trainerId
            memory.record(wave, trainer.trainerId)
        }
        return met
    }

    @Test
    fun `with no history the answer is the roster's own`() {
        // The memory must be an addition and not a replacement: a run that has met nobody yet has to
        // draw exactly what TrainerRoster.pickFor draws, or the roster's own tests stop describing the
        // run loop's behaviour at all.
        val roster = roster()
        listOf(5 to RunOpponent.TRAINER, 10 to RunOpponent.BOSS, 155 to RunOpponent.TRAINER).forEach { (wave, kind) ->
            assertEquals(
                roster.pickFor(wave, kind, seed = 7L),
                RunTrainerSelection.pick(roster, wave, kind, seed = 7L, recent = emptyList()),
            )
        }
    }

    @Test
    fun `a trainer inside the window is not drawn again`() {
        val roster = roster()
        val met = sweep(roster, seed = 4242L)
        val trainerWaves = met.keys.filter { composition.kindOf(it) == RunOpponent.TRAINER }
        // Adjacent *within a kind*, which is what a shared pool means — trainer waves are ten apart
        // under 5/10 because every other multiple of five is a boss.
        trainerWaves.zipWithNext().forEach { (a, b) ->
            assertNotEquals(met[a], met[b], "waves $a and $b drew the same trainer")
        }
    }

    @Test
    fun `the memory measurably reduces repeats`() {
        // Stated as a count rather than as an absolute, because a pool of six over twenty waves must
        // repeat *somewhere* — the claim is that it stops repeating close together, not at all.
        val roster = roster()
        val withMemory = sweep(roster, seed = 99L)
        val without = (1..200).mapNotNull { wave ->
            RunTrainerSelection.pick(roster, wave, composition.kindOf(wave), 99L, recent = emptyList())
                ?.let { wave to it.trainerId }
        }.toMap()
        // Counted within a kind. Trainer waves and boss waves interleave and draw from different
        // bands, so a run of "t_a, b_c, t_a" is two trainer waves in a row as far as the player is
        // concerned, and counting raw adjacency would score it as no repeat at all.
        fun nearRepeats(met: Map<Int, ResourceLocation>) =
            listOf(RunOpponent.TRAINER, RunOpponent.BOSS).sumOf { kind ->
                val waves = met.keys.sorted().filter { composition.kindOf(it) == kind }
                waves.zipWithNext { a, b -> if (met[a] == met[b]) 1 else 0 }.sum()
            }
        assertTrue(
            nearRepeats(withMemory) < nearRepeats(without),
            "the window did not reduce back-to-back repeats (${nearRepeats(withMemory)} vs ${nearRepeats(without)})",
        )
    }

    @Test
    fun `a pool smaller than the window still avoids the last one`() {
        // The case the window shrinks for. Falling straight back to the whole pool here would let the
        // same trainer appear twice running in the roster least able to afford it.
        val roster = roster(trainers = listOf("t_a", "t_b"), bosses = listOf("b_a", "b_b"))
        val met = sweep(roster, seed = 11L)
        met.keys.sorted().filter { composition.kindOf(it) == RunOpponent.TRAINER }
            .zipWithNext().forEach { (a, b) -> assertNotEquals(met[a], met[b], "waves $a and $b repeated") }
    }

    @Test
    fun `a single-trainer pool answers rather than refusing`() {
        // There is no other trainer to give, and returning null would leave the wave with no opponent
        // — a hole the validator explicitly says is not one.
        val roster = roster(trainers = listOf("only"))
        val met = sweep(roster, seed = 3L)
        val trainerWaves = met.filterKeys { composition.kindOf(it) == RunOpponent.TRAINER }
        assertTrue(trainerWaves.isNotEmpty())
        assertEquals(setOf(id("only")), trainerWaves.values.toSet())
    }

    @Test
    fun `a fixed encounter ignores the history`() {
        // An override is an author naming one trainer for one wave. Honouring the window here would
        // silently drop the pin, which is the only thing the mechanism does.
        val roster = roster(fixed = listOf(FixedEncounter(50, id("rival"), RunOpponent.BOSS)))
        val recent = List(RunTrainerMemory.WINDOW) { id("rival") }
        val pick = RunTrainerSelection.pick(roster, 50, RunOpponent.BOSS, seed = 7L, recent = recent)!!
        assertEquals(id("rival"), pick.trainerId)
        assertEquals(TrainerPickSource.FIXED, pick.source)
    }

    @Test
    fun `an undeclared fixed encounter on a wild wave still does not fire`() {
        val roster = roster(fixed = listOf(FixedEncounter(183, id("e4"))))
        assertNull(RunTrainerSelection.pick(roster, 183, RunOpponent.WILD, 7L, recent = emptyList()))
    }

    /**
     * §2.36's rival is the one opponent a run is *supposed* to meet repeatedly, so the no-repeat window
     * must not touch it.
     *
     * The window would not in fact drop it today — a rival has no pool, so there is nothing for
     * [RunTrainerSelection] to exclude and it would answer the same way by accident. Pinned because the
     * accident stops holding the moment a second history-aware rule is added here, and the symptom would
     * be a run-long thread silently missing a meeting or two.
     */
    @Test
    fun `a rival meeting ignores the history, even a window full of itself`() {
        val ladder = RivalLadder(
            meetings = listOf(RivalMeeting(8, id("rgl_rival_1")), RivalMeeting(25, id("rgl_rival_2"))),
            teams = listOf(RivalTeam("kanto", listOf(SignatureSlot(listOf(SpeciesLine(listOf(
                TeamSpecies(ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur")),
            ))))))),
            partySizes = listOf(1, 1),
        )
        val roster = roster().copy(rival = ladder)
        val recent = List(RunTrainerMemory.WINDOW) { id("rgl_rival_2") }
        val pick = RunTrainerSelection.pick(roster, 25, RunOpponent.RIVAL, seed = 7L, recent = recent)!!
        assertEquals(id("rgl_rival_2"), pick.trainerId)
        assertEquals(TrainerPickSource.RIVAL, pick.source)
    }

    @Test
    fun `a rival meeting on a wild wave is still summoned`() {
        // Wave 8 is wild under 5/10, so this is the promotion reaching the run loop. Contrast the
        // undeclared fixed encounter above, which deliberately does NOT fire on a wild wave: a ladder
        // always promotes, and a fixed entry with no `kind` deliberately does not.
        val ladder = RivalLadder(
            meetings = listOf(RivalMeeting(8, id("rgl_rival_1"))),
            teams = listOf(RivalTeam("kanto", listOf(SignatureSlot(listOf(SpeciesLine(listOf(
                TeamSpecies(ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur")),
            ))))))),
            partySizes = listOf(1),
        )
        val roster = roster().copy(rival = ladder)
        val loaded = RunRoster.Loaded(roster.id, roster)
        val fight = RunProgress.planFor(8, seed = 7L, composition, loaded, RunTrainerMemory())
        assertEquals(RunOpponent.RIVAL, fight.plan.kind)
        assertEquals(id("rgl_rival_1"), fight.trainer?.trainerId)
    }

    @Test
    fun `a full run replays identically`() {
        // §2.3, stated over the whole run: two independent two-hundred-wave sweeps from the same seed,
        // no shared state. This is the test the memory could most easily have broken, because it adds
        // the run's own history to a draw that used to depend on nothing but the seed and the wave.
        assertEquals(sweep(roster(), seed = 0x51DE_51DEL), sweep(roster(), seed = 0x51DE_51DEL))
    }

    @Test
    fun `a run resumed from a checkpoint finishes the run it started`() {
        // The reason the memory is persisted rather than recomputed. A resume that began from an empty
        // history would draw a different second half — and would look completely normal doing it.
        val whole = sweep(roster(), seed = 777L)

        val firstHalf = RunTrainerMemory()
        sweep(roster(), seed = 777L, memory = firstHalf, waves = 1..120)
        val restored = RunTrainerMemory.fromNbt(firstHalf.toNbt())
        val secondHalf = sweep(roster(), seed = 777L, memory = restored, waves = 121..200)

        assertEquals(whole.filterKeys { it > 120 }, secondHalf)
    }

    @Test
    fun `an interrupted wave is the same fight when it is fought again`() {
        // §2.10 promises exactly this: the disconnect penalty takes a Pokémon and hands the *same*
        // wave back. Nothing records until a wave is won, so re-planning it must not move it.
        val roster = roster()
        val memory = RunTrainerMemory()
        sweep(roster, seed = 5L, memory = memory, waves = 1..44)
        val loaded = RunRoster.Loaded(roster.id, roster)
        val first = RunProgress.planFor(45, 5L, composition, loaded, memory)
        val again = RunProgress.planFor(45, 5L, composition, loaded, memory)
        assertEquals(first, again)
    }

    @Test
    fun `a different seed still gives a different run`() {
        // The window narrows the pool, so it could in principle push two seeds onto the same ladder.
        val a = sweep(roster(), seed = 1L)
        val b = sweep(roster(), seed = 2L)
        assertTrue(a.keys.count { a[it] != b[it] } > a.size / 4, "two seeds produced near-identical runs")
    }
}
