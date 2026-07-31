package com.cobblemonroguelite.data.reward

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemonroguelite.data.DataProblems
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parse rules, which is where a server owner's mistake either gets named or gets swallowed.
 *
 * Everything here runs without a booted game: [RewardTables.parseJson] takes a reader rather than a
 * [net.minecraft.server.packs.resources.ResourceManager] for exactly this reason. What is *not*
 * covered is the resource-manager walk itself — pack precedence, id derivation from a nested path —
 * which needs a real server and is verified on the dev VM.
 *
 * Assertions on message wording are deliberate. A validator that rejects the right file while naming
 * the wrong field has failed at its only job, and a test that checks nothing but "returned null"
 * would not notice.
 */
class RewardTableParseTest {

    private val file = ResourceLocation.fromNamespaceAndPath("test", "example")

    private class Parsed(val table: RewardTable?, val problems: DataProblems) {
        val messages: List<String> get() = problems.messages()
        fun mentions(vararg fragments: String) = messages.any { line -> fragments.all { it in line } }
    }

    private fun parse(json: String): Parsed {
        val problems = DataProblems(file)
        return Parsed(RewardTables.parseJson(file, json.reader(), problems), problems)
    }

    // A table with one of everything the loader has to fill in itself, so the defaults are pinned.
    private val flatTable = """
        {
          "entries": [
            { "id": "candy", "weight": 2.5, "reward": { "type": "level", "amount": 1 } }
          ]
        }
    """

    @Test
    fun `a table with no tiers loads flat, with defaults filled in`() {
        val parsed = parse(flatTable)
        val table = assertNotNull(parsed.table)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
        assertEquals(file, table.id)
        assertEquals(listOf(RewardTables.DEFAULT_TIER), table.tiers.map { it.id })

        val entry = table.entries.single()
        assertEquals("candy", entry.id)
        assertEquals(RewardTables.DEFAULT_TIER, entry.tier)
        assertEquals(2.5, entry.weight)
        assertEquals(1, entry.minWave)
        assertNull(entry.maxWave, "an absent max_wave must mean unbounded, not wave 0")
        assertEquals(RunReward.Levels(1), entry.reward)
    }

    @Test
    fun `underscore-prefixed fields are ignored so tables can carry comments`() {
        val parsed = parse(
            """
            {
              "_comment": "not a field",
              "entries": [
                { "_note": "nor this", "id": "candy", "weight": 1, "reward": { "type": "level", "amount": 1 } }
              ]
            }
            """
        )
        assertNotNull(parsed.table)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
    }

    @Test
    fun `a misspelt field is named rather than silently defaulted`() {
        // The failure this exists for: "wieght" under a lenient reader loads fine, takes a default,
        // and shows up months later as "my table does not roll what I wrote".
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "candy", "wieght": 1, "weight": 1, "reward": { "type": "level", "amount": 1 } }
              ]
            }
            """
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("test:example", "entries[0].wieght", "unknown field"), parsed.messages.toString())
    }

    @Test
    fun `an unknown reward type lists the ones that exist`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "x", "weight": 1, "reward": { "type": "evs", "stat": "attack", "amount": 4 } }
              ]
            }
            """
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("entries[0].reward.type", "'evs'", "held_item"), parsed.messages.toString())
    }

    @Test
    fun `a bad entry is dropped without taking the rest of the table with it`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "good", "weight": 1, "reward": { "type": "level", "amount": 1 } },
                { "id": "bad", "weight": 0, "reward": { "type": "level", "amount": 1 } }
              ]
            }
            """
        )
        val table = assertNotNull(parsed.table)
        assertEquals(listOf("good"), table.entries.map { it.id })
        assertTrue(parsed.mentions("entries[1].weight", "greater than 0"), parsed.messages.toString())
    }

    @Test
    fun `a table whose entries all failed is rejected rather than loaded empty`() {
        // An empty table would roll nothing forever and read in the log exactly like a working one.
        val parsed = parse("""{ "entries": [ { "id": "bad", "weight": -1, "reward": { "type": "level", "amount": 1 } } ] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("entries", "no usable entries"), parsed.messages.toString())
    }

    @Test
    fun `a wrong-typed field fails the entry instead of falling back to a default`() {
        val parsed = parse("""{ "entries": [ { "id": "x", "weight": "lots", "reward": { "type": "level", "amount": 1 } } ] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("entries[0].weight", "expected a number"), parsed.messages.toString())
    }

    @Test
    fun `an entry naming a tier that does not exist rejects the table`() {
        val parsed = parse(
            """
            {
              "tiers": [ { "id": "common", "curve": [ { "wave": 1, "weight": 1 } ] } ],
              "entries": [
                { "id": "x", "tier": "comon", "weight": 1, "reward": { "type": "level", "amount": 1 } }
              ]
            }
            """
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("entries[0].tier", "'comon'", "common"), parsed.messages.toString())
    }

    @Test
    fun `entries must not name a tier when the table declares none`() {
        val parsed = parse("""{ "entries": [ { "id": "x", "tier": "common", "weight": 1, "reward": { "type": "level", "amount": 1 } } ] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("entries[0].tier", "declares no"), parsed.messages.toString())
    }

    @Test
    fun `a table declaring tiers requires every entry to name one`() {
        val parsed = parse(
            """
            {
              "tiers": [ { "id": "common", "curve": [ { "wave": 1, "weight": 1 } ] } ],
              "entries": [ { "id": "x", "weight": 1, "reward": { "type": "level", "amount": 1 } } ]
            }
            """
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("entries[0].tier", "missing required field", "common"), parsed.messages.toString())
    }

    @Test
    fun `curve points must run forwards`() {
        val parsed = parse(
            """
            {
              "tiers": [ { "id": "common", "curve": [ { "wave": 10, "weight": 1 }, { "wave": 5, "weight": 2 } ] } ],
              "entries": [ { "id": "x", "tier": "common", "weight": 1, "reward": { "type": "level", "amount": 1 } } ]
            }
            """
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("tiers[0].curve[1].wave", "increasing"), parsed.messages.toString())
    }

    @Test
    fun `a tier that is zero at every wave loads but says so`() {
        val parsed = parse(
            """
            {
              "tiers": [ { "id": "shelved", "curve": [ { "wave": 1, "weight": 0 } ] } ],
              "entries": [ { "id": "x", "tier": "shelved", "weight": 1, "reward": { "type": "level", "amount": 1 } } ]
            }
            """
        )
        assertNotNull(parsed.table, "shelving a tier is legitimate and must not reject the file")
        assertTrue(parsed.mentions("tiers[0].curve", "can never be drawn"), parsed.messages.toString())
    }

    @Test
    fun `duplicate entry ids reject the table`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "x", "weight": 1, "reward": { "type": "level", "amount": 1 } },
                { "id": "x", "weight": 1, "reward": { "type": "level", "amount": 2 } }
              ]
            }
            """
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("entries[1].id", "duplicate"), parsed.messages.toString())
    }

    @Test
    fun `a wave band that can never open is rejected`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "x", "weight": 1, "min_wave": 10, "max_wave": 5, "reward": { "type": "level", "amount": 1 } }
              ]
            }
            """
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("entries[0].max_wave", "could never appear"), parsed.messages.toString())
    }

    @Test
    fun `malformed JSON is reported against the file, not thrown`() {
        val parsed = parse("""{ "entries": [ }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("test:example", "not valid JSON"), parsed.messages.toString())
    }

    @Test
    fun `a top-level list is reported rather than parsed`() {
        val parsed = parse("""[ { "id": "x" } ]""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("test:example", "JSON object at the top level"), parsed.messages.toString())
    }

    @Test
    fun `every reward the plan lists is expressible`() {
        // §2.4 mechanism 1, one for one: EVs/vitamins, levels, nature mints, ability patches,
        // evolution items, held items, TMs. If this stops compiling or parsing, the schema has
        // drifted from the decision it implements.
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "a", "weight": 1, "reward": { "type": "ev", "stat": "special_defense", "amount": -4 } },
                { "id": "b", "weight": 1, "reward": { "type": "level", "amount": 3 } },
                { "id": "c", "weight": 1, "reward": { "type": "nature", "nature": "adamant" } },
                { "id": "d", "weight": 1, "reward": { "type": "ability" } },
                { "id": "e", "weight": 1, "reward": { "type": "ability", "ability": "intimidate" } },
                { "id": "f", "weight": 1, "reward": { "type": "item", "item": "cobblemon:metal_coat", "count": 2 } },
                { "id": "g", "weight": 1, "reward": { "type": "held_item", "item": "cobblemon:leftovers" } },
                { "id": "h", "weight": 1, "reward": { "type": "move", "move": "flamethrower" } }
              ]
            }
            """
        )
        val table = assertNotNull(parsed.table)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
        val byId = table.entries.associate { it.id to it.reward }
        // "defense" and "defence" both resolve: which spelling an author reaches for is a coin flip.
        assertEquals(RunReward.Evs(Stats.SPECIAL_DEFENCE, -4), byId["a"])
        assertEquals(RunReward.Levels(3), byId["b"])
        // An unqualified nature defaults to cobblemon:, an unqualified item to minecraft:.
        assertEquals(RunReward.Mint(ResourceLocation.fromNamespaceAndPath("cobblemon", "adamant")), byId["c"])
        assertEquals(RunReward.AbilityPatch(null), byId["d"])
        assertEquals(RunReward.AbilityPatch("intimidate"), byId["e"])
        assertEquals(RunReward.BagItem(ResourceLocation.fromNamespaceAndPath("cobblemon", "metal_coat"), 2), byId["f"])
        assertEquals(RunReward.HeldItem(ResourceLocation.fromNamespaceAndPath("cobblemon", "leftovers")), byId["g"])
        assertEquals(RunReward.TechnicalMachine("flamethrower"), byId["h"])
    }

    @Test
    fun `a credits reward parses as a multiplier of the wave curve`() {
        // The 2026-07-31 restoration of PokéRogue's money items: a multiplier, never a flat amount —
        // the amount is resolved against the shared wave-money curve at grant time.
        val parsed = parse("""{ "entries": [ { "id": "big_nugget", "weight": 12, "reward": { "type": "credits", "multiplier": 2.5 } } ] }""")
        val table = assertNotNull(parsed.table)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
        assertEquals(RunReward.Credits(2.5), table.entries.single().reward)
    }

    @Test
    fun `a credits reward of nothing is rejected`() {
        val zero = parse("""{ "entries": [ { "id": "x", "weight": 1, "reward": { "type": "credits", "multiplier": 0 } } ] }""")
        assertNull(zero.table)
        assertTrue(zero.mentions("reward.multiplier", "greater than 0"), zero.messages.toString())

        val negative = parse("""{ "entries": [ { "id": "x", "weight": 1, "reward": { "type": "credits", "multiplier": -1.5 } } ] }""")
        assertNull(negative.table)
        assertTrue(negative.mentions("reward.multiplier", "greater than 0", "-1.5"), negative.messages.toString())
    }

    @Test
    fun `a credits reward with a flat amount is rejected as an unknown field`() {
        // The mistake this schema invites: someone writes "amount" expecting a constant payout. There
        // is no constant form on purpose — see WaveMoneyCurve — so the field must be named, not ignored.
        val parsed = parse("""{ "entries": [ { "id": "x", "weight": 1, "reward": { "type": "credits", "multiplier": 1.0, "amount": 500 } } ] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("reward.amount", "unknown field"), parsed.messages.toString())
    }

    @Test
    fun `battle-only stats are not EV stats`() {
        val parsed = parse("""{ "entries": [ { "id": "x", "weight": 1, "reward": { "type": "ev", "stat": "evasion", "amount": 4 } } ] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("reward.stat", "'evasion'", "special_attack"), parsed.messages.toString())
    }

    @Test
    fun `an unqualified item id defaults to minecraft, as it does everywhere else in a datapack`() {
        val parsed = parse("""{ "entries": [ { "id": "x", "weight": 1, "reward": { "type": "held_item", "item": "stick" } } ] }""")
        val table = assertNotNull(parsed.table)
        assertEquals(RunReward.HeldItem(ResourceLocation.fromNamespaceAndPath("minecraft", "stick")), table.entries.single().reward)
    }

    @Test
    fun `scaled_by parses case-insensitively and an unknown condition is named`() {
        val parsed = parse(
            """{ "entries": [ { "id": "revive", "weight": 3, "scaled_by": "fainted",
                 "reward": { "type": "item", "item": "cobblemon:revive", "count": 1 } } ] }""",
        )
        assertEquals(PartyCondition.FAINTED, assertNotNull(parsed.table).entries.single().scaledBy)

        // Rejected, not ignored: a misspelt condition silently loading flat would hand Revives to a
        // full party — the exact behaviour the field exists to prevent.
        val bad = parse(
            """{ "entries": [ { "id": "revive", "weight": 3, "scaled_by": "fainted ",
                 "reward": { "type": "item", "item": "cobblemon:revive", "count": 1 } } ] }""",
        )
        assertNull(bad.table)
        assertTrue(bad.mentions("scaled_by", "injured", "fainted"), bad.messages.toString())
    }

    @Test
    fun `the shipped example table is a valid table`() {
        // It ships in the jar's data folder, so if it stops parsing every server logs a rejected
        // file on boot — and the one file an owner copies to start from would be broken.
        val stream = javaClass.getResourceAsStream("/data/cobblemon_roguelite/roguelite/reward_tables/example.json")
        val json = assertNotNull(stream, "example.json is missing from the mod's resources").reader().readText()
        val parsed = parse(json)
        assertNotNull(parsed.table, "shipped example failed to parse: ${parsed.messages}")
        assertTrue(parsed.problems.isEmpty(), "shipped example has problems: ${parsed.messages}")
    }
}
