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

    // ─── §2.30: generated teams ────────────────────────────────────────────

    /** [minimal], with `test:a` generated from a two-slot signature. */
    private val generated = """
        {
          "authored_for": { "run_length": 20 },
          "bands": [
            { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a", "test:b" ] },
            { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
          ],
          "generated": [
            {
              "trainer": "test:a",
              "signature": [
                { "alternatives": [ { "line": [ "cobblemon:onix", "cobblemon:steelix" ] } ] },
                { "alternatives": [
                    { "line": [ "cobblemon:corsola galarian", "cobblemon:cursola" ], "weight": 2.0 },
                    { "line": [ "cobblemon:kabuto" ] }
                ] }
              ],
              "filler": [ { "alternatives": [ { "line": [ "cobblemon:rhyhorn" ] } ] } ]
            }
          ],
          "generation": {
            "party_size": [ { "min_wave": 1, "size": 4 }, { "min_wave": 10, "size": 5 } ],
            "evolution": { "stage_waves": [ 5, 9 ], "fully_evolved_from": 15 },
            "held_items": [
              { "min_wave": 3, "boss": true, "chance": 0.5, "count": 2,
                "items": [ { "item": "cobblemon:leftovers", "weight": 2.0 }, { "item": "cobblemon:oran_berry" } ] }
            ]
          }
        }
    """

    @Test
    fun `a generated block parses into signature lines, filler and rules`() {
        val parsed = parse(generated)
        val roster = assertNotNull(parsed.roster, parsed.messages.toString())
        assertTrue(parsed.problems.isEmpty(), parsed.messages.toString())

        val entry = assertNotNull(roster.generated[ResourceLocation.parse("test:a")])
        assertEquals(2, entry.signature.size)
        assertEquals(1, entry.filler.size)

        // The regional form must survive as a properties fragment, not be flattened into the id: a
        // Galarian Corsola is a Ghost type and a plain one is not.
        val galarian = entry.signature[1].alternatives.first().stages.first()
        assertEquals("cobblemon:corsola", galarian.id.toString())
        assertEquals("galarian", galarian.properties)
        assertEquals(2.0, entry.signature[1].alternatives.first().weight)
        assertNull(entry.signature[0].alternatives.first().stages.first().properties)

        assertEquals(5, roster.generation.partySizeFor(10))
        assertEquals(listOf(5, 9), roster.generation.evolution.stageWaves)
        assertEquals(15, roster.generation.evolution.fullyEvolvedFrom)
        val tier = assertNotNull(roster.generation.heldItemsFor(wave = 5, boss = true))
        assertEquals(2, tier.count)
        assertNull(roster.generation.heldItemsFor(wave = 5, boss = false), "the tier is boss-only")
    }

    @Test
    fun `a roster with no generated block is every fight authored`() {
        val roster = assertNotNull(parse(minimal).roster)
        assertTrue(roster.generated.isEmpty())
        // The defaults are §2.30's, so an untouched roster behaves the way the decision describes.
        assertEquals(4, roster.generation.partySizeFor(1))
        assertEquals(6, roster.generation.partySizeFor(200))
        assertTrue(roster.generation.heldItems.isEmpty(), "item choices are content; none ship")
    }

    @Test
    fun `a generated entry nobody fights is named`() {
        val parsed = parse(
            generated.replace("\"trainer\": \"test:a\"", "\"trainer\": \"test:aa\""),
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("test:aa", "never fought"), parsed.messages.toString())
    }

    @Test
    fun `a signature slot with no alternatives is rejected rather than silently dropped`() {
        val parsed = parse(
            generated.replace(
                "{ \"alternatives\": [ { \"line\": [ \"cobblemon:onix\", \"cobblemon:steelix\" ] } ] }",
                "{ \"alternatives\": [] }",
            ),
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("alternatives", "never be filled"), parsed.messages.toString())
    }

    @Test
    fun `a misspelt species id is named with its slot`() {
        val parsed = parse(generated.replace("\"cobblemon:onix\"", "\"COBBLEMON:Onix\""))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("line[0]", "not a valid species id"), parsed.messages.toString())
    }

    @Test
    fun `an out-of-range party size is rejected, not clamped`() {
        // Cobblemon's party limit is six. A 7 that loads and is silently truncated later is a
        // difficulty change nobody wrote down.
        val parsed = parse(generated.replace("\"size\": 5", "\"size\": 7"))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("size", "1..6"), parsed.messages.toString())
    }

    @Test
    fun `a broken generation block rejects the file instead of defaulting past it`() {
        val parsed = parse(generated.replace("\"chance\": 0.5", "\"chance\": 4"))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("chance", "between 0 and 1"), parsed.messages.toString())
    }

    @Test
    fun `unknown fields inside generated data are still errors`() {
        val parsed = parse(generated.replace("\"weight\": 2.0", "\"wieght\": 2.0"))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("wieght", "unknown field"), parsed.messages.toString())
    }

    // ─── §2.36: the rival block ────────────────────────────────────────────

    /**
     * A twenty-wave run with a two-meeting ladder. Wave 8 is a wild wave under 5/10 and wave 15 a trainer
     * wave, which is the same one-wild-five-scheduled split §2.36's real ladder has in miniature.
     */
    private val withRival = """
        {
          "authored_for": { "run_length": 20 },
          "bands": [
            { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a" ] },
            { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
          ],
          "rival": {
            "meetings": [
              { "wave": 8, "trainer": "test:rgl_rival_1" },
              { "wave": 15, "trainer": "test:rgl_rival_2" }
            ],
            "teams": [
              { "id": "kanto", "slots": [
                  { "alternatives": [ { "line": [ "cobblemon:bulbasaur", "cobblemon:ivysaur" ] } ] },
                  { "alternatives": [ { "line": [ "cobblemon:pidgey" ] } ] },
                  { "alternatives": [ { "line": [ "cobblemon:rattata" ] } ] }
              ] }
            ]
          }
        }
    """

    @Test
    fun `a rival ladder loads with the ramp defaulted`() {
        val roster = assertNotNull(parse(withRival).roster)
        val ladder = assertNotNull(roster.rival)
        assertEquals(listOf(8, 15), ladder.waves())
        assertEquals("test:rgl_rival_1", ladder.meetings.first().trainerId.toString())
        assertEquals(listOf("kanto"), ladder.teams.map { it.id })
        assertTrue(ladder.partySizes.isEmpty(), "an omitted party_size must mean the derived ramp")
        assertEquals(listOf(2, 3), ladder.meetings.indices.map { ladder.partySizeAt(it) })
    }

    @Test
    fun `an absent rival block is not a hole`() {
        // Unlike an absent band. §2.14's mode is complete without a rival, so nothing is reported — which
        // is what let this ship without touching a roster anyone had already written.
        val roster = assertNotNull(parse(minimal).roster)
        assertNull(roster.rival)
    }

    @Test
    fun `an explicit party_size is read positionally`() {
        val roster = assertNotNull(parse(withRival.replace("\"teams\":", "\"party_size\": [ 1, 3 ], \"teams\":")).roster)
        val ladder = assertNotNull(roster.rival)
        assertEquals(listOf(1, 3), ladder.partySizes)
    }

    @Test
    fun `a party size past Cobblemon's limit is rejected, not clamped`() {
        val parsed = parse(withRival.replace("\"teams\":", "\"party_size\": [ 2, 7 ], \"teams\":"))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("party_size[1]", "1..6"), parsed.messages.toString())
    }

    @Test
    fun `out-of-order meetings are named rather than sorted`() {
        // The position in the list IS the meeting number, so sorting for the author would move the party
        // sizes onto different waves — silently, and differently from what they wrote.
        val parsed = parse(withRival.replace("\"wave\": 8", "\"wave\": 18"))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("meetings", "ascending"), parsed.messages.toString())
    }

    @Test
    fun `two meetings on one wave are rejected as ambiguous`() {
        val parsed = parse(withRival.replace("\"wave\": 15", "\"wave\": 8"))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("wave 8", "ambiguous"), parsed.messages.toString())
    }

    /** The same roster with an arbitrary `rival` body, so a test can vary one part without regex surgery. */
    private fun withRivalBody(body: String) = """
        {
          "authored_for": { "run_length": 20 },
          "bands": [
            { "id": "t", "kind": "trainer", "min_wave": 1, "trainers": [ "test:a" ] },
            { "id": "b", "kind": "boss", "min_wave": 1, "trainers": [ "test:boss" ] }
          ],
          "rival": { $body }
        }
    """

    private val twoMeetings =
        """"meetings": [ { "wave": 8, "trainer": "test:r1" }, { "wave": 15, "trainer": "test:r2" } ]"""

    @Test
    fun `a rival with no teams is rejected`() {
        val parsed = parse(withRivalBody("""$twoMeetings, "teams": []"""))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("teams", "at least one team"), parsed.messages.toString())
    }

    @Test
    fun `a rival with no meetings is rejected rather than treated as absent`() {
        // Deleting the block and writing an empty one are different acts, and only one of them is a
        // roster with no rival. An empty ladder loaded as "no rival" would be a rival block that does
        // nothing for as long as nobody checked.
        val parsed = parse(withRivalBody(""""meetings": [], "teams": []"""))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("meetings", "never met"), parsed.messages.toString())
    }

    @Test
    fun `duplicate rival team ids are fatal, not last-wins`() {
        // The id is how every validation message names a team, so two called 'kanto' make all of them
        // ambiguous — the same reasoning band ids get.
        val duplicated = withRival.replace(
            "\"teams\": [",
            "\"teams\": [ { \"id\": \"kanto\", \"slots\": [ { \"alternatives\": [ { \"line\": [ \"cobblemon:pidgey\" ] } ] } ] },",
        )
        val parsed = parse(duplicated)
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("duplicate rival team id", "kanto"), parsed.messages.toString())
    }

    @Test
    fun `a rival team with no slots is rejected`() {
        val parsed = parse(withRivalBody("""$twoMeetings, "teams": [ { "id": "kanto", "slots": [] } ]"""))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("slots", "at least one slot"), parsed.messages.toString())
    }

    @Test
    fun `a rival stage id that is not an id is named with its field`() {
        val parsed = parse(withRival.replace("\"test:rgl_rival_1\"", "\"Test:RGL_Rival_1\""))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("trainer", "not a valid id"), parsed.messages.toString())
    }

    @Test
    fun `unknown fields inside the rival block are still errors`() {
        val parsed = parse(withRival.replace("\"meetings\":", "\"meetngs\":"))
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("meetngs", "unknown field"), parsed.messages.toString())
    }

    @Test
    fun `kind rival is refused with a pointer at the rival block rather than as a typo`() {
        // The plausible wrong guess, not a misspelling: RIVAL is a real wave kind, so an author will try
        // to declare one. A band or a fixed entry cannot, because neither carries a meeting number.
        val band = parse(minimal.replace("\"kind\": \"trainer\"", "\"kind\": \"rival\""))
        assertNull(band.roster)
        assertTrue(band.mentions("kind", "'rival' is not a kind", "'rival' block"), band.messages.toString())

        val fixed = parse(
            minimal.trimEnd().dropLast(1) + ", \"fixed\": [ { \"wave\": 8, \"kind\": \"rival\", \"trainer\": \"test:r\" } ] }",
        )
        assertNull(fixed.roster)
        assertTrue(fixed.mentions("kind", "'rival' is not a kind"), fixed.messages.toString())
    }

    @Test
    fun `a rival team too short for its deepest meeting is reported at load`() {
        // Not at the meeting. A rival that stops growing is the mechanic quietly not happening, and the
        // only place it is visible is against the ramp — which is here, while the author has the file open.
        val parsed = parse(
            withRivalBody(
                """
                $twoMeetings,
                "teams": [ { "id": "kanto", "slots": [
                  { "alternatives": [ { "line": [ "cobblemon:bulbasaur" ] } ] },
                  { "alternatives": [ { "line": [ "cobblemon:pidgey" ] } ] }
                ] } ]
                """,
            ),
        )
        assertNull(parsed.roster)
        assertTrue(parsed.mentions("'kanto'", "2 slots", "asks for 3"), parsed.messages.toString())
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
