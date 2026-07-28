package com.cobblemonroguelite.data.trainer

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
 *   ]
 * }
 * ```
 *
 * A roster holds **trainer ids and nothing else** — no teams, no species, no levels. Teams are
 * authored as RCT trainers in their own datapack and the level comes from the wave curve (§2.6), so
 * everything this file says is *which* trainer, never *what* trainer. That is also what keeps the
 * whole layer compilable without RCT on the classpath (§1.2: their licence is unverified, so they
 * stay a soft dependency).
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

        // Only worth running once the pieces parsed — schedule checks over a roster that already
        // lost a band would report holes the author did not create, on top of the real error.
        if (problems.count != before) return null

        val roster = TrainerRoster(id, authoredFor, bands, fixed)
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
}
