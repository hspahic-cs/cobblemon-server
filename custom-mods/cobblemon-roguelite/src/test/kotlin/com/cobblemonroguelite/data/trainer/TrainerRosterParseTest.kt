package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.integration.RunOpponent
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parse and validation rules — where an author's mistake either gets named or gets swallowed.
 *
 * Everything here runs without a booted game, because [TrainerRosters.parseJson] takes a reader
 * rather than a `ResourceManager`. What is *not* covered is the resource-manager walk itself (pack
 * precedence, id derivation from a nested path), which needs a real server and is verified on the
 * dev VM.
 *
 * Assertions are on message *wording*. A validator that rejects the right file while naming the
 * wrong wave has failed at its only job, and "returned null" would not notice — which matters more
 * here than in the reward tables, since the reader of these messages is looking at a two-hundred-line
 * schedule and needs to be told which line.
 */
class TrainerRosterParseTest {

    private val file = ResourceLocation.fromNamespaceAndPath("test", "roster")

    private class Parsed(val roster: TrainerRoster?, val problems: DataProblems) {
        val messages: List<String> get() = problems.messages()
        fun mentions(vararg fragments: String) = messages.any { line -> fragments.all { it in line } }
    }

    private fun parse(json: String): Parsed {
        val problems = DataProblems(file)
        return Parsed(TrainerRosters.parseJson(file, json.reader(), problems), problems)
    }

    /** A roster that covers a 20-wave run under the default 5/10 schedule. */
    private val minimal = """
        {
          "authored_for": { "run_length": 20 },
          "bands": [
            { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a", "test:b" ] },
            { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
          ]
        }
    """

    @Test
    fun `a minimal roster loads with defaults filled in`() {
        val parsed = parse(minimal)
        val roster = assertNotNull(parsed.roster)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
        assertEquals(file, roster.id)
        assertEquals(20, roster.authoredFor.runLength)
        assertEquals(5, roster.authoredFor.trainerInterval, "an omitted interval must take the shipping default")
        assertEquals(10, roster.authoredFor.bossInterval)

        val band = roster.bands.first()
        assertEquals(RunOpponent.TRAINER, band.kind)
        assertEquals(1, band.minWave)
        assertNull(band.maxWave, "an absent max_wave must mean open-ended, not wave 0")
        assertEquals(listOf("test:a", "test:b"), band.trainers.map { it.toString() })
        assertTrue(roster.fixed.isEmpty())
    }

    @Test
    fun `authored_for defaults to the shipping schedule when absent`() {
        val parsed = parse(
            """
            {
              "bands": [
                { "id": "t", "kind": "trainer", "trainers": [ "test:a" ] },
                { "id": "b", "kind": "boss", "trainers": [ "test:boss" ] }
              ]
            }
            """,
        )
        val roster = assertNotNull(parsed.roster)
        assertEquals(WaveCompositionConfig().runLength, roster.authoredFor.runLength)
    }

    @Test
    fun `a gap in coverage is reported with the wave a run would die on`() {
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 60 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "max_wave": 30, "trainers": [ "test:boss" ] }
              ]
            }
            """,
        )
        assertNull(parsed.roster, "a roster that cannot serve a wave must not load")
        assertTrue(parsed.mentions("no boss band covers", "waves 40-60"), parsed.messages.toString())
        assertTrue(parsed.mentions("wave 40 would have no opponent"), parsed.messages.toString())
    }

    @Test
    fun `a gap is reported per missing band, not per missing wave`() {
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 200 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "max_wave": 100, "trainers": [ "test:boss" ] }
              ]
            }
            """,
        )
        // Ten uncovered boss waves, one message. A line per wave would bury every other problem in
        // the file, which is how a validator gets ignored.
        assertEquals(1, parsed.messages.count { "no boss band covers" in it }, parsed.messages.toString())
    }

    @Test
    fun `a trainer gap is one message even though trainer waves are not evenly spaced`() {
        // Regression: waves of one kind are not spaced by the interval that produced them. Under
        // 5/10 the trainer waves are 5, 15, 25 — ten apart, because every other multiple of five is
        // taken by a boss — so grouping gaps by `trainerInterval` split one missing band into
        // fifteen messages and pushed the rest of the file's problems out of view.
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 200 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "max_wave": 60, "trainers": [ "test:a" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
              ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertEquals(1, parsed.messages.count { "no trainer band covers" in it }, parsed.messages.toString())
        assertTrue(parsed.mentions("waves 65-195"), parsed.messages.toString())
    }

    @Test
    fun `overlapping bands of the same kind are rejected and both are named`() {
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 200 },
              "bands": [
                { "id": "early", "kind": "trainer", "min_wave": 1, "max_wave": 60, "trainers": [ "test:a" ] },
                { "id": "mid", "kind": "trainer", "min_wave": 50, "trainers": [ "test:b" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
              ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("'early'", "'mid'", "waves 50-60"), parsed.messages.toString())
        assertTrue(parsed.mentions("can never be drawn"), parsed.messages.toString())
    }

    @Test
    fun `bands of different kinds may share a wave range`() {
        // The common case: one set of band edges, a trainer pool and a boss pool inside each. An
        // overlap check that did not split by kind would reject every roster anyone writes.
        assertNotNull(parse(minimal).roster)
    }

    @Test
    fun `an empty pool is rejected at the field, not left to the coverage check`() {
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 20 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [] },
                { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
              ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("trainers", "at least one trainer"), parsed.messages.toString())
    }

    @Test
    fun `a fixed encounter on a wild wave is reported with the waves that would have worked`() {
        // The typo this exists for: 183 instead of 182 in a hand-transcribed ladder. Both are wild
        // waves, both look identical in the file, and only one of them was meant.
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 200 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
              ],
              "fixed": [ { "wave": 183, "trainer": "test:e4" } ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("wave 183", "can never fire"), parsed.messages.toString())
        assertTrue(parsed.mentions("wave 180 or wave 185"), parsed.messages.toString())
        assertTrue(parsed.mentions("\"kind\""), "the fix must be in the message: ${parsed.messages}")
    }

    @Test
    fun `a fixed encounter that declares its kind may promote a wild wave`() {
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 200 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
              ],
              "fixed": [
                { "wave": 182, "kind": "boss", "trainer": "test:e4_1" },
                { "wave": 184, "kind": "boss", "trainer": "test:e4_2" },
                { "wave": 190, "kind": "boss", "trainer": "test:champion" }
              ]
            }
            """,
        )
        val roster = assertNotNull(parsed.roster, parsed.messages.toString())
        assertTrue(parsed.problems.isEmpty(), parsed.messages.toString())
        assertEquals(3, roster.fixed.size)
        assertEquals(RunOpponent.BOSS, roster.fixed[182]?.kind)
        // 190 is already a boss wave; declaring the kind there is redundant but not wrong, and
        // rejecting it would make a transcribed ladder unwritable as one uniform block.
        assertEquals(RunOpponent.BOSS, roster.fixed[190]?.kind)
    }

    @Test
    fun `a fixed encounter past the end of the run is reported`() {
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 50 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
              ],
              "fixed": [ { "wave": 182, "kind": "boss", "trainer": "test:e4" } ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("wave 182", "past the end of the run", "run_length=50"), parsed.messages.toString())
    }

    @Test
    fun `demoting a boss wave to a trainer wave is reported`() {
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 50 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
              ],
              "fixed": [ { "wave": 30, "kind": "trainer", "trainer": "test:someone" } ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("wave 30", "removes a boss from the run"), parsed.messages.toString())
    }

    @Test
    fun `two fixed encounters on one wave are rejected rather than resolved by file order`() {
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 50 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
              ],
              "fixed": [
                { "wave": 30, "trainer": "test:one" },
                { "wave": 30, "trainer": "test:two" }
              ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("wave 30 already has a fixed encounter"), parsed.messages.toString())
    }

    @Test
    fun `a wild band is rejected by name`() {
        val parsed = parse(
            """
            {
              "bands": [ { "id": "w", "kind": "wild", "trainers": [ "test:a" ] } ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("unknown kind 'wild'"), parsed.messages.toString())
    }

    @Test
    fun `a malformed trainer id names the index it is at`() {
        val parsed = parse(
            """
            {
              "bands": [ { "id": "t", "kind": "trainer", "trainers": [ "test:a", "NOT AN ID" ] } ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("trainers[1]", "not a valid id"), parsed.messages.toString())
    }

    @Test
    fun `duplicate band ids are rejected`() {
        val parsed = parse(
            """
            {
              "authored_for": { "run_length": 20 },
              "bands": [
                { "id": "t", "kind": "trainer", "min_wave": 1, "max_wave": 10, "trainers": [ "test:a" ] },
                { "id": "t", "kind": "trainer", "min_wave": 11, "trainers": [ "test:b" ] },
                { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
              ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("duplicate band id 't'"), parsed.messages.toString())
    }

    @Test
    fun `a typo'd field is rejected rather than silently defaulted`() {
        val parsed = parse(
            """
            {
              "bands": [ { "id": "t", "kind": "trainer", "min_wve": 5, "trainers": [ "test:a" ] } ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("min_wve", "unknown field"), parsed.messages.toString())
    }

    @Test
    fun `a roster with no bands at all is rejected`() {
        val parsed = parse("""{ "bands": [] }""")
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("cannot serve any wave"), parsed.messages.toString())
    }

    @Test
    fun `a bad interval is named rather than thrown`() {
        // WaveCompositionConfig's own `require` would take the whole datapack reload with it and
        // report a Kotlin init block instead of the field.
        val parsed = parse(
            """
            {
              "authored_for": { "boss_interval": 0 },
              "bands": [ { "id": "t", "kind": "trainer", "trainers": [ "test:a" ] } ]
            }
            """,
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("boss_interval", "must be at least 1"), parsed.messages.toString())
    }

    @Test
    fun `a roster valid for its authored schedule can be re-checked against a live one`() {
        // The operator-retunes-the-intervals case: the file did not change, the schedule did, and
        // the roster now has a hole nobody edited into it.
        val roster = assertNotNull(parse(minimal).roster)
        assertTrue(roster.validate().isEmpty())

        val retuned = WaveComposition(WaveCompositionConfig(runLength = 200, trainerInterval = 5, bossInterval = 10))
        assertTrue(
            roster.validate(retuned).isEmpty(),
            "open-ended bands should still cover a longer run: ${roster.validate(retuned)}",
        )

        val shorterBands = roster.copy(bands = roster.bands.map { it.copy(maxWave = 20) })
        val problems = shorterBands.validate(retuned)
        assertTrue(problems.any { "no trainer band covers" in it }, problems.toString())
        assertTrue(problems.any { "no boss band covers" in it }, problems.toString())
    }

    @Test
    fun `the shipped example roster loads clean`() {
        // It ships enabled, so a broken example is an ERROR in every server owner's log on first
        // boot — and the first thing anyone copies.
        val path = "/data/cobblemon_roguelite/roguelite/trainer_rosters/example.json"
        val text = assertNotNull(javaClass.getResourceAsStream(path), "missing $path").reader().readText()
        val parsed = parse(text)
        assertNotNull(parsed.roster, "the shipped example must validate: ${parsed.messages}")
        assertTrue(parsed.problems.isEmpty(), parsed.messages.toString())
    }
}
