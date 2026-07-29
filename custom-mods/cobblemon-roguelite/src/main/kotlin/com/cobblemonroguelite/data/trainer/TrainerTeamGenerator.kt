package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.wave.WaveDrawStream
import com.cobblemonroguelite.wave.WaveRandom
import net.minecraft.resources.ResourceLocation

/**
 * One Pokémon of a generated team, decided but not yet built.
 *
 * The same split [com.cobblemonroguelite.wave.WildEncounter] makes against
 * [com.cobblemonroguelite.wave.WildEncounterFactory]: everything that *decides* what the player
 * fights is a plain data type with no game objects in it, and turning the decision into a Pokémon
 * happens elsewhere — here, on the far side of the trainer-battle seam, because the Pokémon has to be
 * built by whoever is allowed to talk to RCT. Which is also what makes the decision testable at all.
 */
data class GeneratedMember(
    val species: TeamSpecies,
    val level: Int,
    val heldItem: ResourceLocation? = null,
) {
    fun propertiesString(): String = species.propertiesString(level, heldItem)
}

/**
 * A whole trainer's team for one wave of one run.
 *
 * Never persisted. It is a pure function of `(seed, wave)` and the roster, so a resumed run
 * regenerates the identical team rather than reading one back — which is the same bargain
 * [com.cobblemonroguelite.run.RunRosters] makes about the roster itself: the run stores the id, not
 * the contents, so a fixable mistake stays fixable for runs already in flight.
 */
data class GeneratedTeam(val members: List<GeneratedMember>) {

    fun propertiesStrings(): List<String> = members.map { it.propertiesString() }

    /** For the log line that says what a wave actually summoned, without dumping properties strings. */
    fun describe(): String = members.joinToString(", ") { member ->
        member.species.id.path + (member.heldItem?.let { " @${it.path}" } ?: "")
    }
}

/**
 * Builds a trainer's team from their signature species, the wave, and the run's seed.
 *
 * ### Determinism is correctness here, not polish
 *
 * A run is checkpointable (§2.3) and multi-session (§2.19), and the run records which trainer each
 * wave met. If the team were rolled at summon time, a player who did not like Brock's Omanyte could
 * disconnect on the loading screen and reconnect for a Kabuto — the exact exploit the run seed exists
 * to close — and worse, a run resumed a week later would meet a *different* team from the one its own
 * history says it was fighting.
 *
 * So every draw here is a pure function of `(seed, wave)` over a salted [WaveRandom] stream. Nothing
 * reads a clock, a player, the party, or a shared RNG, and no state is kept between calls: one
 * generator serves every concurrent run.
 *
 * Two streams, not one, and both **appended** to [WaveDrawStream] rather than slotted in beside the
 * existing constants — that enum's rule, and the reason it has one: a run checkpointed before
 * generated teams existed resumes with the same wild species and levels it had, because nothing above
 * the new constants moved.
 *
 * The inputs that are *not* `(seed, wave)` are the entry and the rules — i.e. the datapack. Editing
 * either re-points waves an in-flight run has not reached yet, exactly as editing a band's pool does
 * ([TrainerBand.trainers] documents the same trade). It does not break a run; the run stays
 * deterministic and simply answers from the new data.
 */
object TrainerTeamGenerator {

    /**
     * The team [entry] brings to [wave], or null when there is nothing to generate.
     *
     * Null is not a failure: it is the **authored** path. A roster names most trainers by id alone and
     * fights their RCT team as written, which is how the Elite Four and the champion stay hand-made
     * (§2.30). Only a trainer with a signature entry generates.
     *
     * @param level the wave's level, from the curve, already carrying §2.19's boss multiplier. Applied
     *   to every member: the bridge forces the opponent team to this level anyway, and writing it into
     *   the properties string is what makes Cobblemon derive the moveset *for that level* — the whole
     *   reason generation replaces authoring.
     * @param boss whether this wave is a boss wave **as the run sees it**, promotions included. Only
     *   held items read it; the level multiplier is already in [level].
     */
    fun generate(
        entry: TrainerEntry,
        wave: Int,
        level: Int,
        boss: Boolean,
        seed: Long,
        rules: TeamGenerationRules,
    ): GeneratedTeam {
        require(wave >= 1) { "wave is 1-based, got $wave" }

        // One stream, drawn in one fixed order: which slots, then which alternative in each. Creating
        // a second [WaveRandom] for the second half would not be independent of the first — both would
        // start from the same `(seed, wave, stream)` state and replay the same sequence, which is the
        // correlation the per-draw salts exist to prevent.
        val rng = WaveRandom.forDraw(seed, wave, WaveDrawStream.TRAINER_TEAM)
        val slots = slotsFor(entry, wave, rules, rng)
        val stageIndex = rules.evolution.stageIndexFor(wave)
        val species = slots.map { slot -> pickAlternative(slot, rng).stageAt(stageIndex) }

        val items = heldItems(species.size, wave, boss, seed, rules)
        return GeneratedTeam(
            species.mapIndexed { index, member -> GeneratedMember(member, level, items[index]) },
        )
    }

    /**
     * The slots this wave's party is built from: the signature ones, then filler if the band wants a
     * bigger party than the signature can fill.
     *
     * Signature slots are taken **in the order written**, because PokéRogue's order is meaningful —
     * their Paldea leaders' first slot is the Terastallised ace. Truncating from the end when a band
     * asks for fewer than four therefore drops the least significant one.
     *
     * Filler is drawn without replacement, so a six-Pokémon party never contains the same filler slot
     * twice. When there is not enough filler the party is simply shorter: a band asking for six with
     * four slots and no filler gets four, and that is stated in [TrainerEntry.filler] rather than
     * papered over by repeating a slot.
     */
    private fun slotsFor(
        entry: TrainerEntry,
        wave: Int,
        rules: TeamGenerationRules,
        rng: WaveRandom,
    ): List<SignatureSlot> {
        val target = rules.partySizeFor(wave)
        if (target <= entry.signature.size) return entry.signature.take(target)

        val wanted = (target - entry.signature.size).coerceAtMost(entry.filler.size)
        if (wanted <= 0) return entry.signature

        val remaining = entry.filler.toMutableList()
        val chosen = ArrayList<SignatureSlot>(wanted)
        repeat(wanted) {
            // Truncating a uniform double rather than a modulo of nextLong, the same arithmetic the
            // roster's own trainer draw uses and for the same reason: modulo of a 64-bit draw is
            // biased toward low indices for any size that does not divide 2^64.
            val index = (rng.nextDouble() * remaining.size).toInt().coerceAtMost(remaining.lastIndex)
            chosen += remaining.removeAt(index)
        }
        return entry.signature + chosen
    }

    /**
     * One alternative from a slot, by weight.
     *
     * Weighted rather than uniform because a branching species (Tyrogue) is stored as one line per
     * branch — see [SpeciesLine.weight]. Non-positive weights are dropped, which gives a roster a way
     * to disable a line without deleting it; a slot whose every weight is zero falls back to the first
     * alternative rather than failing, because a missing Pokémon at wave 150 is a worse answer than an
     * unintended one.
     */
    private fun pickAlternative(slot: SignatureSlot, rng: WaveRandom): SpeciesLine {
        val candidates = slot.alternatives.filter { it.weight > 0.0 }
        if (candidates.isEmpty()) return slot.alternatives.first()
        if (candidates.size == 1) {
            // Still consume a draw, so that a slot with one alternative does not shift the stream for
            // every slot after it. A roster edit that collapses a slot to one option would otherwise
            // silently re-roll the rest of the team.
            rng.nextDouble()
            return candidates.first()
        }
        var roll = rng.nextDouble() * candidates.sumOf { it.weight }
        for (candidate in candidates) {
            roll -= candidate.weight
            if (roll < 0.0) return candidate
        }
        // Only reachable through floating-point summation error at the very top of the range.
        return candidates.last()
    }

    /**
     * One held item slot per party member, most of them null.
     *
     * Drawn on its own stream so that turning held items on, off, or up does not change **which
     * Pokémon** a trainer brings. If both shared a stream, adding an item tier to a roster would
     * re-roll every species draw of every in-flight run — a balance edit silently rewriting content.
     *
     * [HeldItemTier.count] draws are made per member and the last success wins: Cobblemon holds one
     * item, so the extra draws buy a higher hit rate, not a stack. Every draw is made whether or not
     * it lands, which keeps members after a lucky one on the same footing as members after an unlucky
     * one — a variable number of draws per member would make the stream impossible to reason about.
     */
    private fun heldItems(
        members: Int,
        wave: Int,
        boss: Boolean,
        seed: Long,
        rules: TeamGenerationRules,
    ): List<ResourceLocation?> {
        val tier = rules.heldItemsFor(wave, boss) ?: return List(members) { null }
        val choices = tier.items.filter { it.weight > 0.0 }
        if (choices.isEmpty()) return List(members) { null }

        val rng = WaveRandom.forDraw(seed, wave, WaveDrawStream.TRAINER_ITEM)
        val total = choices.sumOf { it.weight }
        return List(members) {
            var held: ResourceLocation? = null
            repeat(tier.count) {
                val hit = rng.nextDouble() < tier.chance
                var roll = rng.nextDouble() * total
                var picked = choices.last().item
                for (choice in choices) {
                    roll -= choice.weight
                    if (roll < 0.0) {
                        picked = choice.item
                        break
                    }
                }
                if (hit) held = picked
            }
            held
        }
    }
}
