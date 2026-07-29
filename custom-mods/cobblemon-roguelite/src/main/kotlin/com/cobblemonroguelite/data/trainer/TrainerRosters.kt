package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.boss.BossShields
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.data.RogueliteDataRegistry
import com.cobblemonroguelite.integration.RunOpponent
import net.minecraft.resources.ResourceLocation

/**
 * Every trainer roster on the server, loaded from
 * `data/<namespace>/roguelite/trainer_rosters/<name>.json`.
 *
 * ### The file
 *
 * ```json
 * {
 *   "authored_for": { "run_length": 200, "trainer_interval": 5, "boss_interval": 10 },
 *   "bands": [
 *     { "id": "early", "kind": "trainer", "min_wave": 1, "max_wave": 60,
 *       "trainers": [ "ns:rgl_youngster", "ns:rgl_lass" ] },
 *     { "id": "early_boss", "kind": "boss", "min_wave": 1, "max_wave": 60,
 *       "trainers": [ "ns:rgl_boss_early" ] }
 *   ],
 *   "fixed": [
 *     { "wave": 50, "trainer": "ns:rgl_rival" },
 *     { "wave": 182, "kind": "boss", "trainer": "ns:rgl_e4_1" }
 *   ],
 *   "generated": [
 *     { "trainer": "ns:rgl_brock", "signature": [
 *         { "alternatives": [ { "line": [ "cobblemon:onix", "cobblemon:steelix" ] } ] }
 *     ] }
 *   ],
 *   "generation": { "party_size": [ { "min_wave": 1, "size": 4 } ], "held_items": [] }
 * }
 * ```
 *
 * A band and a fixed encounter hold **trainer ids and nothing else** — never a team. What changed with
 * §2.30 is what an id can mean: an id listed in `generated` fights a team built at the encounter from
 * its signature species, and an id that is not fights its RCT trainer's authored team, as every id did
 * before. Either way this file names *which* trainer and never reaches into RCT, which is what keeps
 * the layer compilable without RCT on the classpath (§1.2: their licence is unverified, so they stay a
 * soft dependency) and unit-testable with no server at all.
 *
 * The species ids in a `line` are not checked against anything here for the same reason the trainer
 * ids are not: this module cannot see a registry at parse time. A line naming a species the server
 * does not have fails when the Pokémon is built, and is reported there.
 *
 * ### Containment: a bad roster is rejected whole, unlike a reward table
 *
 * [com.cobblemonroguelite.data.reward.RewardTables] drops a bad entry and loads the rest, because a
 * reward table with one entry missing is a narrower table that still works. A roster with one band
 * missing is **a run that stops at wave 61 with no opponent** — the dropped band is a hole, not a
 * narrowing. So any problem here costs the file, and the schedule checks in [TrainerRoster.validate]
 * are run at parse time on the same footing as a type error: a roster that cannot serve wave 150 is
 * as unloadable as one whose `min_wave` is a string.
 *
 * The cost is that one typo takes the whole roster out and a run cannot start. That is the intended
 * trade — a run that cannot start says so at load, in the log, with the field named; a run that
 * starts on a half-loaded roster fails hours later in a player's session.
 *
 * ### What is deliberately *not* checked
 *
 * That the trainer ids name real trainers. RCT owns that registry and this module may not import it,
 * so an id naming nothing loads clean here and fails at summon time. `ops/gen_roguelite_roster.py`
 * closes that gap outside the game by cross-checking a roster against a trainer datapack directory,
 * which is where an author can act on it anyway.
 */
object TrainerRosters : RogueliteDataRegistry<TrainerRoster>("trainer_rosters") {

    /**
     * Kind names as an author writes them. Lower-case because the rest of the format is, and mapped
     * explicitly rather than through `RunOpponent.valueOf` so that `wild` is rejected by the same
     * message that rejects `boos` — a band or a promotion to WILD is meaningless, and letting the
     * enum answer for it would produce "no enum constant" for one and silent nonsense for the other.
     */
    private val KINDS = mapOf("trainer" to RunOpponent.TRAINER, "boss" to RunOpponent.BOSS)

    public override fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): TrainerRoster? {
        val before = problems.count

        val authoredFor = parseSchedule(root)
        val bandViews = root.requireObjectList("bands")
        val fixedViews = root.optionalObjectList("fixed") ?: emptyList()
        val generatedViews = root.optionalObjectList("generated") ?: emptyList()
        val generation = parseGeneration(root)
        root.expectNoUnknownKeys()
        if (authoredFor == null || bandViews == null) return null

        if (bandViews.isEmpty()) {
            root.problem("bands", "a roster with no bands cannot serve any wave")
            return null
        }

        val bands = mutableListOf<TrainerBand>()
        val seenBandIds = mutableSetOf<String>()
        for (view in bandViews) {
            val band = parseBand(view) ?: continue
            // Fatal rather than last-wins: band ids are how every validation message and every log
            // line identifies a band, and two bands called 'mid' make all of them ambiguous.
            if (!seenBandIds.add(band.id)) {
                view.problem("id", "duplicate band id '${band.id}'")
                continue
            }
            bands += band
        }

        val fixed = linkedMapOf<Int, FixedEncounter>()
        for (view in fixedViews) {
            val entry = parseFixed(view) ?: continue
            val clash = fixed.put(entry.wave, entry)
            if (clash != null) {
                // Two entries on one wave: which trainer the author meant is not guessable, and
                // last-wins would depend on file order, which nothing else in this format does.
                view.problem("wave", "wave ${entry.wave} already has a fixed encounter (${clash.trainerId})")
            }
        }

        val generated = linkedMapOf<ResourceLocation, TrainerEntry>()
        for (view in generatedViews) {
            val entry = parseEntry(view) ?: continue
            val clash = generated.put(entry.trainerId, entry)
            if (clash != null) {
                // Two signature blocks for one trainer: last-wins would depend on file order, and the
                // two blocks are by definition different teams for the same NPC.
                view.problem("trainer", "trainer '${entry.trainerId}' already has a generated entry")
            }
        }

        // Only worth running once the pieces parsed — schedule checks over a roster that already
        // lost a band would report holes the author did not create, on top of the real error.
        if (problems.count != before || generation == null) return null

        val roster = TrainerRoster(id, authoredFor, bands, fixed, generated, generation)
        roster.validate().forEach { problems.add("", it) }
        return if (problems.count == before) roster else null
    }

    /**
     * The `authored_for` block: the schedule the roster's coverage is checked against.
     *
     * Optional, defaulting to [WaveCompositionConfig]'s own defaults — which are the shipping
     * schedule (200/5/10), so a roster written for the standard run says nothing and gets checked
     * against the standard run. It exists at all because coverage is only meaningful relative to a
     * schedule, and a server running a short campaign needs its roster checked against *that* one
     * rather than told it is missing bands for waves it will never reach.
     *
     * The curve and reward routing are not readable here on purpose. They are the run's business,
     * they change nothing about which waves need a trainer, and letting a roster file carry them
     * would give a server two places that answer "how long is a run".
     */
    private fun parseSchedule(root: JsonView): WaveCompositionConfig? {
        val view = root.optionalObject("authored_for")
        if (view == null) {
            // `"authored_for": 5` has already been reported as a type error; falling through to the
            // defaults there would validate the roster against a schedule the author did not write.
            return if (root.hasField("authored_for")) null else WaveCompositionConfig()
        }
        val defaults = WaveCompositionConfig()
        val runLength = view.optionalInt("run_length") ?: defaults.runLength
        val trainerInterval = view.optionalInt("trainer_interval") ?: defaults.trainerInterval
        val bossInterval = view.optionalInt("boss_interval") ?: defaults.bossInterval
        view.expectNoUnknownKeys()

        var ok = true
        // Checked here rather than left to WaveCompositionConfig's `require`: an exception out of a
        // datapack parse takes the whole reload with it, and the author would get a stack trace
        // whose top frame is a Kotlin init block instead of the name of the field they got wrong.
        if (runLength < 1) {
            view.problem("run_length", "must be at least 1, was $runLength")
            ok = false
        }
        if (trainerInterval < 1) {
            view.problem("trainer_interval", "must be at least 1, was $trainerInterval")
            ok = false
        }
        if (bossInterval < 1) {
            view.problem("boss_interval", "must be at least 1, was $bossInterval")
            ok = false
        }
        if (!ok) return null
        return WaveCompositionConfig(
            runLength = runLength,
            trainerInterval = trainerInterval,
            bossInterval = bossInterval,
        )
    }

    private fun parseBand(view: JsonView): TrainerBand? {
        val bandId = view.requireString("id")
        val kind = parseKind(view, "kind", required = true)
        val minWave = view.optionalInt("min_wave") ?: 1
        val maxWave = view.optionalInt("max_wave")
        val trainers = parseTrainerList(view)
        view.expectNoUnknownKeys()

        var ok = bandId != null && kind != null && trainers != null
        if (bandId != null && bandId.isBlank()) {
            view.problem("id", "must not be blank — it is how validation messages name this band")
            ok = false
        }
        if (minWave < 1) {
            view.problem("min_wave", "must be at least 1, was $minWave")
            ok = false
        }
        if (maxWave != null && maxWave < minWave) {
            view.problem("max_wave", "$maxWave is before min_wave $minWave, so this band could never match")
            ok = false
        }
        if (!ok) return null

        return TrainerBand(
            id = bandId!!,
            kind = kind!!,
            minWave = minWave,
            maxWave = maxWave,
            trainers = trainers!!,
        )
    }

    private fun parseFixed(view: JsonView): FixedEncounter? {
        val wave = view.requireInt("wave")
        val trainer = parseTrainerId(view, "trainer")
        val kind = parseKind(view, "kind", required = false)
        view.expectNoUnknownKeys()

        if (wave != null && wave < 1) {
            view.problem("wave", "waves are 1-based, was $wave")
            return null
        }
        if (wave == null || trainer == null) return null
        // kind == null is legitimate (replace, do not promote) and is indistinguishable here from a
        // kind that failed to parse — but a failed parse has already recorded a problem, and any
        // problem rejects the file, so the ambiguity never reaches a loaded roster.
        return FixedEncounter(wave = wave, trainerId = trainer, kind = kind)
    }

    private fun parseKind(view: JsonView, field: String, required: Boolean): RunOpponent? {
        val raw = if (required) view.requireString(field) else view.optionalString(field)
        if (raw == null) return null
        return KINDS[raw.lowercase()] ?: run {
            view.problem(
                field,
                "unknown kind '$raw' (expected ${KINDS.keys.joinToString(" or ")}) — wild waves are " +
                    "generated, not authored, so they have no roster entry",
            )
            null
        }
    }

    private fun parseTrainerList(view: JsonView): List<ResourceLocation>? {
        val raw = view.requireStringList("trainers") ?: return null
        if (raw.isEmpty()) {
            view.problem("trainers", "must name at least one trainer — an empty pool leaves the band's waves unfought")
            return null
        }
        var ok = true
        val ids = mutableListOf<ResourceLocation>()
        raw.forEachIndexed { index, text ->
            val parsed = ResourceLocation.tryParse(text)
            if (parsed == null) {
                view.problem("trainers[$index]", "'$text' is not a valid id (expected namespace:path)")
                ok = false
            } else {
                ids += parsed
            }
        }
        return if (ok) ids else null
    }

    private fun parseTrainerId(view: JsonView, field: String): ResourceLocation? {
        val text = view.requireString(field) ?: return null
        return ResourceLocation.tryParse(text) ?: run {
            view.problem(field, "'$text' is not a valid id (expected namespace:path)")
            null
        }
    }

    // ─── §2.30: generated teams ────────────────────────────────────────────

    /**
     * One `generated` entry: a trainer id and the signature species its team is built from.
     *
     * `signature` is required and must be non-empty. An entry with an empty one is rejected rather
     * than treated as "authored after all": the two are written differently on purpose — an authored
     * fight has *no entry here at all* — and silently accepting an empty block would let a
     * half-written entry look like a deliberate authored fight for as long as nobody checked.
     */
    private fun parseEntry(view: JsonView): TrainerEntry? {
        val trainerId = parseTrainerId(view, "trainer")
        val signature = parseSlots(view, "signature", required = true)
        val filler = parseSlots(view, "filler", required = false) ?: emptyList()
        view.expectNoUnknownKeys()

        if (trainerId == null || signature == null) return null
        if (signature.isEmpty()) {
            view.problem(
                "signature",
                "must name at least one slot — a trainer whose team is generated from nothing would " +
                    "arrive empty. For an authored fight, delete this entry and leave the id in its band",
            )
            return null
        }
        return TrainerEntry(trainerId, signature, filler)
    }

    private fun parseSlots(view: JsonView, field: String, required: Boolean): List<SignatureSlot>? {
        val views = (if (required) view.requireObjectList(field) else view.optionalObjectList(field)) ?: return null
        var ok = true
        val slots = mutableListOf<SignatureSlot>()
        views.forEach { slotView ->
            val slot = parseSlot(slotView)
            if (slot == null) ok = false else slots += slot
        }
        return if (ok) slots else null
    }

    private fun parseSlot(view: JsonView): SignatureSlot? {
        val alternativeViews = view.requireObjectList("alternatives")
        view.expectNoUnknownKeys()
        if (alternativeViews == null) return null
        if (alternativeViews.isEmpty()) {
            view.problem("alternatives", "a slot with no alternatives can never be filled")
            return null
        }
        var ok = true
        val lines = mutableListOf<SpeciesLine>()
        alternativeViews.forEach { alternativeView ->
            val line = parseLine(alternativeView)
            if (line == null) ok = false else lines += line
        }
        return if (ok && lines.isNotEmpty()) SignatureSlot(lines) else null
    }

    /**
     * One alternative: an evolution line, base form first, plus its weight against the others.
     *
     * A stage is written as a `PokemonProperties` fragment — `cobblemon:corsola galarian` — because a
     * regional form is an *aspect* in Cobblemon and not a species of its own. Only the first token is
     * validated as an id; the rest is passed through untouched, since checking it would mean this
     * module carrying its own copy of Cobblemon's property grammar.
     */
    private fun parseLine(view: JsonView): SpeciesLine? {
        val stages = view.requireStringList("line")
        val weight = view.optionalDouble("weight") ?: 1.0
        view.expectNoUnknownKeys()
        if (stages == null) return null
        if (stages.isEmpty()) {
            view.problem("line", "must name at least one species")
            return null
        }

        var ok = true
        val parsed = mutableListOf<TeamSpecies>()
        stages.forEachIndexed { index, text ->
            val tokens = text.trim().split(Regex("\\s+"))
            val speciesId = ResourceLocation.tryParse(tokens.first())
            if (speciesId == null) {
                view.problem(
                    "line[$index]",
                    "'${tokens.first()}' is not a valid species id (expected namespace:path, " +
                        "optionally followed by properties such as 'galarian')",
                )
                ok = false
            } else {
                parsed += TeamSpecies(speciesId, tokens.drop(1).joinToString(" ").takeIf { it.isNotBlank() })
            }
        }
        if (weight < 0.0) {
            view.problem("weight", "must not be negative, was $weight")
            ok = false
        }
        return if (ok) SpeciesLine(parsed, weight) else null
    }

    /**
     * The `generation` block, or the shipped defaults when it is absent.
     *
     * Absent is the ordinary case and means §2.30's numbers with no held items — a roster does not
     * have to restate the design to get it. Present-but-broken returns null, which rejects the file:
     * defaulting past an author's own tuning would run their content at party sizes they did not ask
     * for, which is exactly the silent-wrong-answer this reader exists to prevent.
     */
    private fun parseGeneration(root: JsonView): TeamGenerationRules? {
        val view = root.optionalObject("generation")
        if (view == null) return if (root.hasField("generation")) null else TeamGenerationRules()

        val sizeViews = view.optionalObjectList("party_size")
        val evolutionView = view.optionalObject("evolution")
        val itemViews = view.optionalObjectList("held_items")
        val shieldViews = view.optionalObjectList("boss_shields")
        view.expectNoUnknownKeys()

        var ok = true
        val sizes = mutableListOf<PartySizeTier>()
        sizeViews?.forEach { tierView ->
            val minWave = tierView.optionalInt("min_wave") ?: 1
            val size = tierView.requireInt("size")
            tierView.expectNoUnknownKeys()
            if (minWave < 1) {
                tierView.problem("min_wave", "must be at least 1, was $minWave")
                ok = false
            }
            if (size != null && size !in 1..6) {
                // Cobblemon's own party limit. A 7 here would be accepted by the format and then
                // silently truncated wherever the team is built, which is a difficulty change nobody
                // wrote down.
                tierView.problem("size", "must be 1..6, was $size")
                ok = false
            }
            if (size != null && minWave >= 1 && size in 1..6) sizes += PartySizeTier(minWave, size)
            else ok = false
        }

        var evolution = EvolutionSchedule()
        if (evolutionView != null) {
            val waves = evolutionView.optionalIntList("stage_waves") ?: EvolutionSchedule.DEFAULT_STAGE_WAVES
            val fully = evolutionView.optionalInt("fully_evolved_from") ?: EvolutionSchedule.DEFAULT_FULLY_EVOLVED_FROM
            evolutionView.expectNoUnknownKeys()
            when {
                waves.any { it < 1 } -> {
                    evolutionView.problem("stage_waves", "waves are 1-based, got $waves")
                    ok = false
                }
                waves != waves.sorted() -> {
                    evolutionView.problem("stage_waves", "must ascend, got $waves")
                    ok = false
                }
                fully < 1 -> {
                    evolutionView.problem("fully_evolved_from", "must be at least 1, was $fully")
                    ok = false
                }
                else -> evolution = EvolutionSchedule(waves, fully)
            }
        }

        val tiers = mutableListOf<HeldItemTier>()
        itemViews?.forEach { tierView ->
            val tier = parseHeldItemTier(tierView)
            if (tier == null) ok = false else tiers += tier
        }

        val shields = mutableListOf<BossShieldTier>()
        shieldViews?.forEach { tierView ->
            val tier = parseBossShieldTier(tierView)
            if (tier == null) ok = false else shields += tier
        }

        if (!ok) return null
        return TeamGenerationRules(
            partySizes = sizes.ifEmpty { TeamGenerationRules.DEFAULT_PARTY_SIZES },
            evolution = evolution,
            heldItems = tiers,
            bossShields = shields,
        )
    }

    /**
     * One §2.32 shield tier.
     *
     * The `shields` bound is checked here rather than left to the clamp in
     * [TrainerTeamGenerator.generate] because the two failures are not the same failure. A clamp
     * keeps the run playable; this tells the operator that the number they wrote is not the number
     * they will get, at load time, while they are still looking at the file. Without it a roster
     * asking for eight shields is silently a roster asking for five.
     */
    private fun parseBossShieldTier(view: JsonView): BossShieldTier? {
        val minWave = view.optionalInt("min_wave") ?: 1
        val shields = view.requireInt("shields")
        val members = view.optionalInt("members") ?: 1
        view.expectNoUnknownKeys()

        var ok = true
        if (minWave < 1) {
            view.problem("min_wave", "must be at least 1, was $minWave")
            ok = false
        }
        if (shields != null && shields !in 1..BossShields.MAX_SHIELDS) {
            view.problem(
                "shields",
                "must be 1..${BossShields.MAX_SHIELDS}, was $shields — there is one held-item script " +
                    "per shield count, so a higher number is an item Showdown does not have and the " +
                    "boss would fight with no shields at all",
            )
            ok = false
        }
        if (members < 1) {
            view.problem("members", "must be at least 1, was $members — omit the tier for an unshielded boss")
            ok = false
        }

        if (!ok || shields == null) return null
        return BossShieldTier(minWave, shields, members)
    }

    private fun parseHeldItemTier(view: JsonView): HeldItemTier? {
        val minWave = view.optionalInt("min_wave") ?: 1
        val maxWave = view.optionalInt("max_wave")
        val boss = view.optionalBoolean("boss")
        val chance = view.requireDouble("chance")
        val count = view.optionalInt("count") ?: 1
        val itemViews = view.requireObjectList("items")
        view.expectNoUnknownKeys()

        var ok = true
        if (minWave < 1) {
            view.problem("min_wave", "must be at least 1, was $minWave")
            ok = false
        }
        if (maxWave != null && maxWave < minWave) {
            view.problem("max_wave", "$maxWave is before min_wave $minWave, so this tier could never match")
            ok = false
        }
        if (chance != null && chance !in 0.0..1.0) {
            view.problem("chance", "must be between 0 and 1, was $chance")
            ok = false
        }
        if (count < 1) {
            view.problem("count", "must be at least 1, was $count")
            ok = false
        }

        val items = mutableListOf<HeldItemChoice>()
        itemViews?.forEach { itemView ->
            val id = itemView.requireString("item")?.let { text ->
                ResourceLocation.tryParse(text) ?: run {
                    itemView.problem("item", "'$text' is not a valid item id (expected namespace:path)")
                    null
                }
            }
            val weight = itemView.optionalDouble("weight") ?: 1.0
            itemView.expectNoUnknownKeys()
            if (weight < 0.0) {
                itemView.problem("weight", "must not be negative, was $weight")
                ok = false
            }
            if (id == null) ok = false else items += HeldItemChoice(id, weight)
        }
        if (itemViews != null && items.isEmpty()) {
            view.problem("items", "must name at least one item — a tier with none would never place anything")
            ok = false
        }

        if (!ok || chance == null || itemViews == null) return null
        return HeldItemTier(minWave, maxWave, boss, chance, count, items)
    }
}
