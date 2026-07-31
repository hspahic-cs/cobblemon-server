package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.boss.BossShields
import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.wave.PartyMemberStrength
import com.cobblemonroguelite.wave.WaveLevelCurve
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Team generation — §2.30's decision, and the half of it a player can feel.
 *
 * ### What is actually at stake here
 *
 * Determinism is **correctness**, not polish. A run is checkpointable (§2.3) and records which trainer
 * each wave met; if a team were rolled at summon time, a player who disliked Brock's Omanyte could
 * reconnect for a Kabuto, and a run resumed a week later would fight a team its own history disagrees
 * with. So the property under test throughout is: the same `(seed, wave)` produces the same team, on a
 * new object, in a new process, for every wave of a full 200-wave run.
 *
 * Nothing here boots a server. That is deliberate and it is why the generator is split from the thing
 * that builds Pokémon: everything that *decides* the opponent is a pure function of data, and the only
 * part that needs Cobblemon's registries lives on the bridge side. A generator that needed a world
 * would be a generator nobody could test — which is the state most of this decision would otherwise
 * have shipped in.
 *
 * ### What still needs the dev VM
 *
 * That the properties strings produced here actually *build*: that Cobblemon accepts
 * `species=cobblemon:corsola galarian level=53 held_item=cobblemon:leftovers`, that a regional aspect
 * resolves to the regional form, and that `create()` derives a level-appropriate moveset. Those are
 * assertions about Cobblemon, not about us, and they are checked where the Pokémon is built
 * (cobblemon-bridge's `RogueliteTrainerBattles.teamFor`) against a real server.
 */
class TrainerTeamGeneratorTest {

    private fun species(path: String, properties: String? = null) =
        TeamSpecies(ResourceLocation.fromNamespaceAndPath("cobblemon", path), properties)

    private fun line(vararg stages: String, weight: Double = 1.0) =
        SpeciesLine(stages.map { species(it) }, weight)

    private fun slot(vararg alternatives: SpeciesLine) = SignatureSlot(alternatives.toList())

    /**
     * EXAMPLE DATA, not content: PokéRogue's own Brock, which is the one entry §2.30's text quotes and
     * therefore the one where a reader can check the test against the decision.
     */
    private val brock = TrainerEntry(
        trainerId = ResourceLocation.fromNamespaceAndPath("test", "rgl_brock"),
        signature = listOf(
            slot(line("onix", "steelix")),
            slot(line("geodude", "graveler", "golem")),
            slot(line("omanyte", "omastar"), line("kabuto", "kabutops")),
            slot(line("aerodactyl")),
        ),
        filler = listOf(
            slot(line("rhyhorn", "rhydon", "rhyperior")),
            slot(line("larvitar", "pupitar", "tyranitar")),
            slot(line("bonsly", "sudowoodo")),
        ),
    )

    private val rules = TeamGenerationRules()

    /** The default curve is PokéRogue's Classic verbatim, which is what a real run uses (§2.19). */
    private val curve = WaveLevelCurve()

    private fun generate(wave: Int, seed: Long = 7L, boss: Boolean = false, entry: TrainerEntry = brock) =
        TrainerTeamGenerator.generate(entry, wave, curve, boss = boss, seed = seed, rules = rules)

    private fun names(team: GeneratedTeam) = team.members.map { it.species.id.path }

    // ─── determinism ───────────────────────────────────────────────────────

    @Test
    fun `the same seed and wave always produce the same team`() {
        repeat(20) { wave ->
            assertEquals(generate(wave + 1), generate(wave + 1), "wave ${wave + 1} is not reproducible")
        }
    }

    /**
     * The replay a resumed run actually performs: every wave of a full run, regenerated from nothing
     * but the seed. This is the test that would fail if anyone hung generation off a counter, a clock,
     * or the order a datapack happened to load in.
     */
    @Test
    fun `a full run replays identically`() {
        val schedule = WaveCompositionConfig()
        val composition = WaveComposition(schedule)
        val seed = -8_123_456_789L

        fun play(): Map<Int, GeneratedTeam> =
            (1..schedule.runLength)
                .filter { composition.kindOf(it) != RunOpponent.WILD }
                .associateWith { wave ->
                    val boss = composition.kindOf(wave) == RunOpponent.BOSS
                    TrainerTeamGenerator.generate(brock, wave, curve, boss = boss, seed = seed, rules = rules)
                }

        val first = play()
        // §2.19: 200/5 = 40 non-wild waves, of which the twenty multiples of ten are the bosses.
        assertEquals(40, first.size)
        assertEquals(first, play())
    }

    @Test
    fun `different runs meet different teams`() {
        // The slot PokéRogue writes as [OMANYTE, KABUTO]. Across seeds it must actually vary, or the
        // seeded pick is a constant dressed up as a draw.
        val third = (1L..60L).map { seed -> names(generate(15, seed))[2] }.toSet()
        assertEquals(setOf("omanyte", "kabuto"), third)
    }

    @Test
    fun `one wave does not decide the next`() {
        // Two adjacent trainer waves of the same run must be independently drawn, not the same team
        // twice: 20 boss waves against one leader that always brings the same four is not variety.
        val teams = (1..40).map { names(generate(it * 5)) }.toSet()
        assertTrue(teams.size > 1, "every wave produced the identical party")
    }

    // ─── party size ────────────────────────────────────────────────────────

    @Test
    fun `party size follows the band`() {
        assertEquals(4, generate(1).members.size)
        assertEquals(4, generate(59).members.size)
        assertEquals(5, generate(60).members.size)
        assertEquals(6, generate(120).members.size)
        assertEquals(6, generate(200).members.size)
    }

    @Test
    fun `signature slots come first and keep their order`() {
        val late = names(generate(150))
        assertEquals(listOf("golem", "aerodactyl"), listOf(late[1], late[3]))
        assertEquals(6, late.size)
        // The two extra ones are filler, drawn without replacement.
        assertEquals(late.drop(4).size, late.drop(4).toSet().size)
    }

    @Test
    fun `a trainer with no filler brings a smaller party rather than a repeated one`() {
        val noFiller = brock.copy(filler = emptyList())
        val team = names(generate(200, entry = noFiller))
        assertEquals(4, team.size)
        assertEquals(team.size, team.toSet().size)
    }

    // ─── evolution stage ───────────────────────────────────────────────────

    @Test
    fun `evolution stage comes from the wave`() {
        assertEquals("geodude", names(generate(5))[1])
        assertEquals("graveler", names(generate(25))[1])
        assertEquals("golem", names(generate(80))[1])
        assertEquals("golem", names(generate(200))[1])
    }

    @Test
    fun `a shorter line is fully evolved sooner, not stuck`() {
        // Onix has two stages where Geodude has three: at wave 25 the two-stage line is already final.
        assertEquals("steelix", names(generate(25))[0])
        assertEquals("aerodactyl", names(generate(5))[3])
        assertEquals("aerodactyl", names(generate(200))[3])
    }

    @Test
    fun `a line longer than the schedule still reaches its last stage`() {
        val long = TrainerEntry(
            trainerId = ResourceLocation.fromNamespaceAndPath("test", "long"),
            signature = listOf(slot(line("a", "b", "c", "d"))),
        )
        assertEquals("a", names(generate(1, entry = long))[0])
        assertEquals("b", names(generate(20, entry = long))[0])
        // Without the explicit fully-evolved clause this would be "b" forever — the stage index would
        // never exceed the number of thresholds.
        assertEquals("d", names(generate(80, entry = long))[0])
    }

    // ─── EVs, levels, held items ───────────────────────────────────────────

    @Test
    fun `no EVs are ever written`() {
        // §2.30: PokéRogue removed EVs from stat calculation, so their trainers have none and neither
        // do ours. Our players still earn them — that asymmetry is the decision, and an `_ev` key
        // appearing here would be someone re-opening §2.4 by accident.
        val properties = generate(150, boss = true).propertiesStrings()
        assertTrue(properties.none { it.contains("_ev") }, properties.toString())
        assertTrue(properties.none { it.contains("moves=") }, "movesets are Cobblemon's to derive")
    }

    /**
     * PokéRogue's `getPartyLevels`, checked against values computed by hand from their formula —
     * see docs/roguelite-trainer-battles.md §1.3 for the transcription of record.
     *
     * Wave 44: base = 1 + 44/2 + (44/25)² = 26.0976. Ace (STRONGER, ×1.25) → ceil = 33; STRONG
     * (×1.2) → 32; AVERAGE → multiplier 1.1 + 0.025·⌊44/25⌋ = 1.125, offset −⌊(44/50)·1⌋ = 0 →
     * ceil(26.0976 × 1.125) = 30. Party size 4 → strengths [STRONGER, STRONG, AVERAGE, AVERAGE].
     */
    @Test
    fun `member levels follow getPartyLevels, ace ahead of the curve`() {
        val team = TrainerTeamGenerator.generate(brock, 44, curve, boss = false, seed = 7L, rules = rules)
        assertEquals(listOf(33, 32, 30, 30), team.members.map { it.level })
        assertTrue(team.propertiesStrings()[0].contains(" level=33"), team.propertiesStrings()[0])
    }

    /** §2.19's flat tail: past ~wave 138 the whole party sits at the cap, and that is the decision. */
    @Test
    fun `late-run parties clamp to the level cap`() {
        assertTrue(generate(150).members.all { it.level == 100 })
    }

    /**
     * The strength spread is ace-first (our slot order — PokéRogue's templates put the ace last and
     * ours mirror them) and takes no draws, so retuning it can never move another roll.
     */
    @Test
    fun `the strength spread is ace-first and scales with party size`() {
        assertEquals(listOf(PartyMemberStrength.STRONGER), TrainerTeamGenerator.strengthsFor(1))
        assertEquals(
            listOf(PartyMemberStrength.STRONGER, PartyMemberStrength.AVERAGE),
            TrainerTeamGenerator.strengthsFor(2),
        )
        assertEquals(
            listOf(
                PartyMemberStrength.STRONGER, PartyMemberStrength.STRONG,
                PartyMemberStrength.AVERAGE, PartyMemberStrength.AVERAGE,
            ),
            TrainerTeamGenerator.strengthsFor(4),
        )
        // PokéRogue's six-slot gym-leader template (3 avg, 2 strong, 1 stronger), mirrored.
        assertEquals(
            listOf(
                PartyMemberStrength.STRONGER, PartyMemberStrength.STRONG, PartyMemberStrength.STRONG,
                PartyMemberStrength.AVERAGE, PartyMemberStrength.AVERAGE, PartyMemberStrength.AVERAGE,
            ),
            TrainerTeamGenerator.strengthsFor(6),
        )
    }

    @Test
    fun `held items are absent until a tier says otherwise`() {
        assertTrue(generate(150, boss = true).members.all { it.heldItem == null })
    }

    @Test
    fun `held item tiers are scaled by wave and boss status`() {
        val leftovers = ResourceLocation.fromNamespaceAndPath("cobblemon", "leftovers")
        val berry = ResourceLocation.fromNamespaceAndPath("cobblemon", "oran_berry")
        val tiered = TeamGenerationRules(
            heldItems = listOf(
                HeldItemTier(minWave = 80, boss = true, chance = 1.0, items = listOf(HeldItemChoice(leftovers))),
                HeldItemTier(minWave = 30, chance = 1.0, items = listOf(HeldItemChoice(berry))),
            ),
        )

        fun items(wave: Int, boss: Boolean) =
            TrainerTeamGenerator.generate(brock, wave, curve, boss, 7L, tiered).members.map { it.heldItem }

        assertTrue(items(10, boss = false).all { it == null }, "no tier covers wave 10")
        assertTrue(items(40, boss = false).all { it == berry })
        // The boss tier is written first, so a boss wave past 80 takes it — first match wins.
        assertTrue(items(100, boss = true).all { it == leftovers })
        assertTrue(items(100, boss = false).all { it == berry })
    }

    @Test
    fun `held item draws do not move the species draws`() {
        // The two streams are separate precisely so that tuning items is not a content change. If this
        // fails, an operator adding a held-item tier has silently re-rolled every in-flight run's
        // remaining opponents.
        val withItems = TeamGenerationRules(
            heldItems = listOf(
                HeldItemTier(
                    minWave = 1,
                    chance = 0.5,
                    count = 3,
                    items = listOf(
                        HeldItemChoice(ResourceLocation.fromNamespaceAndPath("cobblemon", "leftovers")),
                        HeldItemChoice(ResourceLocation.fromNamespaceAndPath("cobblemon", "oran_berry"), 3.0),
                    ),
                ),
            ),
        )
        for (wave in listOf(5, 45, 120, 195)) {
            assertEquals(
                names(TrainerTeamGenerator.generate(brock, wave, curve, false, 7L, rules)),
                names(TrainerTeamGenerator.generate(brock, wave, curve, false, 7L, withItems)),
                "wave $wave changed species when held items were added",
            )
        }
    }

    @Test
    fun `a slot with one alternative does not shift the slots after it`() {
        // A roster edit that collapses a slot to a single option must not re-roll the rest of the
        // team, so a single-alternative slot still consumes its draw.
        val twoWay = brock
        val collapsed = brock.copy(
            signature = listOf(
                brock.signature[0],
                brock.signature[1],
                SignatureSlot(listOf(brock.signature[2].alternatives.first())),
                brock.signature[3],
            ),
        )
        assertEquals(
            names(generate(45, entry = twoWay)).let { listOf(it[0], it[1], it[3]) },
            names(generate(45, entry = collapsed)).let { listOf(it[0], it[1], it[3]) },
        )
    }

    // ─── weights ───────────────────────────────────────────────────────────

    @Test
    fun `branch weights keep a two-species slot even`() {
        // PokéRogue's [OMANYTE, KABUTO] is a coin flip. Tyrogue expands into three lines, so without
        // weights a slot pairing it with one plain species would be 3:1 rather than 1:1.
        val branchy = TrainerEntry(
            trainerId = ResourceLocation.fromNamespaceAndPath("test", "branchy"),
            signature = listOf(
                slot(
                    line("tyrogue", "hitmonlee", weight = 1.0 / 3),
                    line("tyrogue", "hitmonchan", weight = 1.0 / 3),
                    line("tyrogue", "hitmontop", weight = 1.0 / 3),
                    line("machop", "machoke", "machamp"),
                ),
            ),
        )
        val drawn = (1L..400L).map { seed -> names(generate(90, seed, entry = branchy)).first() }
        val machamp = drawn.count { it == "machamp" }
        assertTrue(machamp in 150..250, "machamp drawn $machamp/400, expected about half")
    }

    @Test
    fun `a zero weight takes a line out without deleting it`() {
        val disabled = TrainerEntry(
            trainerId = ResourceLocation.fromNamespaceAndPath("test", "disabled"),
            signature = listOf(slot(line("omanyte", "omastar", weight = 0.0), line("kabuto", "kabutops"))),
        )
        val drawn = (1L..40L).map { seed -> names(generate(90, seed, entry = disabled)).first() }.toSet()
        assertEquals(setOf("kabutops"), drawn)
    }

    // ─── the authored path ─────────────────────────────────────────────────

    @Test
    fun `a roster generates only the trainers it has entries for`() {
        val schedule = WaveCompositionConfig()
        val generatedId = ResourceLocation.fromNamespaceAndPath("test", "rgl_brock")
        val authoredId = ResourceLocation.fromNamespaceAndPath("test", "rgl_e4_1")
        val roster = TrainerRoster(
            id = ResourceLocation.fromNamespaceAndPath("test", "roster"),
            authoredFor = schedule,
            bands = listOf(
                TrainerBand("t", RunOpponent.TRAINER, 1, null, listOf(generatedId)),
                TrainerBand("b", RunOpponent.BOSS, 1, null, listOf(authoredId)),
            ),
            fixed = emptyMap(),
            generated = mapOf(generatedId to brock),
        )

        assertTrue(roster.validate().isEmpty(), roster.validate().toString())
        assertEquals(4, roster.teamFor(generatedId, 15, curve, boss = false, seed = 7L)?.members?.size)
        // The Elite Four case: no entry, so no generated team, so the authored RCT team is fought.
        assertNull(roster.teamFor(authoredId, 20, curve, boss = true, seed = 7L))
    }

    @Test
    fun `a generated entry nobody fights is reported`() {
        val used = ResourceLocation.fromNamespaceAndPath("test", "rgl_brock")
        val stray = ResourceLocation.fromNamespaceAndPath("test", "rgl_borck")
        val roster = TrainerRoster(
            id = ResourceLocation.fromNamespaceAndPath("test", "roster"),
            authoredFor = WaveCompositionConfig(),
            bands = listOf(
                TrainerBand("t", RunOpponent.TRAINER, 1, null, listOf(used)),
                TrainerBand("b", RunOpponent.BOSS, 1, null, listOf(used)),
            ),
            fixed = emptyMap(),
            generated = mapOf(used to brock, stray to brock.copy(trainerId = stray)),
        )
        val problems = roster.validate()
        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems.single().contains("rgl_borck"), problems.single())
        assertFalse(problems.single().contains("test:rgl_brock"), "the used entry must not be reported")
    }

    // ─── rules ─────────────────────────────────────────────────────────────

    @Test
    fun `party size tiers do not depend on the order they are written in`() {
        val forwards = TeamGenerationRules(
            partySizes = listOf(PartySizeTier(1, 4), PartySizeTier(60, 5), PartySizeTier(120, 6)),
        )
        val backwards = TeamGenerationRules(
            partySizes = listOf(PartySizeTier(120, 6), PartySizeTier(1, 4), PartySizeTier(60, 5)),
        )
        for (wave in listOf(1, 59, 60, 119, 120, 200)) {
            assertEquals(forwards.partySizeFor(wave), backwards.partySizeFor(wave), "wave $wave")
        }
    }

    @Test
    fun `a wave below every tier gets the smallest party, not the biggest`() {
        val late = TeamGenerationRules(partySizes = listOf(PartySizeTier(50, 6), PartySizeTier(100, 5)))
        assertEquals(5, late.partySizeFor(1))
        assertNotEquals(6, late.partySizeFor(1))
    }

    // ─── boss shields (§2.32) ──────────────────────────────────────────────

    /**
     * The ramp used throughout: unshielded until 50, then 2, then 3 from 100, then 4 across the
     * front two from 150. Deliberately written out of order — the answer must not depend on it.
     */
    private val shieldRules = TeamGenerationRules(
        partySizes = listOf(PartySizeTier(1, 4), PartySizeTier(60, 5), PartySizeTier(120, 6)),
        bossShields = listOf(
            BossShieldTier(minWave = 100, shields = 3),
            BossShieldTier(minWave = 150, shields = 4, members = 2),
            BossShieldTier(minWave = 50, shields = 2),
        ),
    )

    private fun team(wave: Int, boss: Boolean, rules: TeamGenerationRules = shieldRules) =
        TrainerTeamGenerator.generate(brock, wave = wave, curve = curve, boss = boss, seed = 11L, rules = rules)

    /**
     * Shields are a boss-wave property and nothing else.
     *
     * A `min_wave` low enough to cover the ordinary trainer waves must still not shield them: §2.14's
     * schedule is the only thing allowed to decide which waves are bosses, and a shielded wave-55
     * Youngster would be a difficulty spike an operator did not write.
     */
    @Test
    fun `only boss waves are shielded`() {
        assertEquals(listOf(0, 0, 0, 0), team(wave = 55, boss = false).members.map { it.shields })
        assertEquals(listOf(2, 0, 0, 0), team(wave = 55, boss = true).members.map { it.shields })
    }

    /** No tiers declared is the shipped default, and it means bosses that hit hard and nothing more. */
    @Test
    fun `a roster with no shield tiers shields nothing`() {
        val bare = TeamGenerationRules()
        assertTrue(team(wave = 200, boss = true, rules = bare).members.all { it.shields == 0 })
    }

    /** Below every threshold is unshielded, not shielded-by-the-nearest-tier. */
    @Test
    fun `waves below the first tier are unshielded`() {
        assertTrue(team(wave = 49, boss = true).members.all { it.shields == 0 })
    }

    /**
     * Highest passed threshold wins, regardless of written order — the same reading
     * [PartySizeTier] gets, and for the same reason: "how deep am I" is the only sane way to read a
     * difficulty ramp.
     */
    @Test
    fun `the deepest passed tier wins whatever order it was written in`() {
        assertEquals(2, team(wave = 50, boss = true).members.first().shields)
        assertEquals(2, team(wave = 99, boss = true).members.first().shields)
        assertEquals(3, team(wave = 100, boss = true).members.first().shields)
        assertEquals(4, team(wave = 150, boss = true).members.first().shields)
        assertEquals(4, team(wave = 200, boss = true).members.first().shields)
    }

    /**
     * Shields land on the **front** of the party.
     *
     * Front because §2.30 keeps signature slots in written order precisely so slot one can be the
     * ace — where PokéRogue puts a leader's Terastallised signature Pokémon.
     */
    @Test
    fun `shields are applied from the front of the party`() {
        val late = team(wave = 150, boss = true).members.map { it.shields }
        assertEquals(listOf(4, 4, 0, 0, 0, 0), late)
    }

    /** A tier asking for more shielded Pokémon than the band's party size gets the party, not a crash. */
    @Test
    fun `more shielded members than the party has is clamped`() {
        val greedy = TeamGenerationRules(
            partySizes = listOf(PartySizeTier(1, 4)),
            bossShields = listOf(BossShieldTier(minWave = 1, shields = 2, members = 99)),
        )
        assertEquals(listOf(2, 2, 2, 2), team(wave = 10, boss = true, rules = greedy).members.map { it.shields })
    }

    /**
     * A shield count above the number of shipped scripts is clamped rather than emitted.
     *
     * The roster loader reports this as a problem at load time; this is the last line of defence,
     * and the reason it exists is that the alternative is not a crash. An id like `bossshield8`
     * resolves to no Showdown item at all, so the boss fights completely unshielded and *nothing
     * logs* — from Cobblemon's side, a Pokémon holding an unknown item is ordinary.
     */
    @Test
    fun `a shield count past the shipped scripts is clamped, not emitted`() {
        val overreach = TeamGenerationRules(
            bossShields = listOf(BossShieldTier(minWave = 1, shields = BossShields.MAX_SHIELDS + 3)),
        )
        val member = team(wave = 10, boss = true, rules = overreach).members.first()
        assertEquals(BossShields.MAX_SHIELDS, member.shields)
        assertTrue(member.propertiesString().contains(BossShields.showdownId(BossShields.MAX_SHIELDS)))
    }

    /**
     * The shield takes the item slot outright.
     *
     * §2.32's budget: a Pokémon holds one item and the shields *are* this boss's power. Keeping the
     * rolled Leftovers instead would give the boss the item and not the mechanic, which is the
     * failure nobody would notice from the outside.
     */
    @Test
    fun `shields displace a rolled held item`() {
        val both = TeamGenerationRules(
            partySizes = listOf(PartySizeTier(1, 4)),
            heldItems = listOf(
                HeldItemTier(
                    minWave = 1,
                    chance = 1.0,
                    items = listOf(HeldItemChoice(ResourceLocation.fromNamespaceAndPath("cobblemon", "leftovers"))),
                ),
            ),
            bossShields = listOf(BossShieldTier(minWave = 1, shields = 3)),
        )
        val members = team(wave = 10, boss = true, rules = both).members

        val ace = members.first()
        assertEquals(3, ace.shields)
        // The roll still happened and is still recorded — it just lost the slot. Suppressing the
        // draw instead would make turning shields on re-roll every other member's item.
        assertNotNull(ace.heldItem)
        assertFalse(ace.propertiesString().contains("held_item=cobblemon:leftovers"), ace.propertiesString())
        assertTrue(ace.propertiesString().contains("bossshield3"), ace.propertiesString())

        // Everyone behind the ace keeps their ordinary item.
        val rest = members.drop(1)
        assertTrue(rest.all { it.shields == 0 })
        assertTrue(rest.all { it.propertiesString().contains("held_item=cobblemon:leftovers") })
    }

    /**
     * The properties string a shielded boss is actually built from.
     *
     * Asserted whole because every part of it is load-bearing and each fails silently on its own:
     * the species and level decide the moveset, and the item fragment must survive a parser that
     * splits on spaces first and on the first `=` second.
     */
    @Test
    fun `a shielded member's properties string carries the shield item`() {
        // Wave 100: base = 1 + 50 + (100/25)² = 67; the ace is STRONGER → ceil(67 × 1.25) = 84.
        val ace = team(wave = 100, boss = true).members.first()
        assertEquals(
            "species=cobblemon:steelix level=84 " + BossShields.heldItemProperty(3),
            ace.propertiesString(),
        )
        // Space-separated tokens: species, level, held_item. A fourth would mean the fragment split.
        assertEquals(3, ace.propertiesString().split(' ').size, ace.propertiesString())
    }

    /**
     * Turning shields on does not disturb anything else about the team.
     *
     * The reason there is no new [com.cobblemonroguelite.wave.WaveDrawStream] constant for shields:
     * the count is a step function of the wave and consumes no draws at all, so adding, removing or
     * re-tuning a tier cannot move a species or an item roll in a run already in flight. That is a
     * property of the implementation rather than of the data, so it is worth pinning.
     */
    @Test
    fun `adding a shield tier does not move any other roll`() {
        val without = TeamGenerationRules(partySizes = shieldRules.partySizes)
        for (wave in listOf(10, 50, 100, 150, 200)) {
            val bare = team(wave, boss = true, rules = without)
            val shielded = team(wave, boss = true, rules = shieldRules)
            assertEquals(
                bare.members.map { it.species to it.heldItem },
                shielded.members.map { it.species to it.heldItem },
                "wave $wave: the shield tier changed which Pokémon or items were drawn",
            )
        }
    }

    /** Shields are part of the team, so a regenerated team has to reproduce them too. */
    @Test
    fun `shields regenerate identically for the same seed and wave`() {
        assertEquals(
            team(wave = 150, boss = true).members.map { it.shields },
            team(wave = 150, boss = true).members.map { it.shields },
        )
    }

    /** The wave log has to say what the boss is holding, not what it would have held. */
    @Test
    fun `describe names the shields rather than the item they displaced`() {
        val described = team(wave = 100, boss = true).describe()
        assertTrue(described.contains("shield×3"), described)
    }
}
