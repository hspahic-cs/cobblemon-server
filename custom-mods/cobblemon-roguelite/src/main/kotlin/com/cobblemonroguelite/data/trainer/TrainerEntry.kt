package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.boss.BossShields
import net.minecraft.resources.ResourceLocation

/**
 * One Pokémon a generated team could contain, as a species and an optional properties fragment.
 *
 * The fragment is the same convention [com.cobblemonroguelite.wave.WaveSpecies] uses and for the same
 * reason: a regional form is not a species in Cobblemon, it is an **aspect** on one, and aspects are
 * carried in a `PokemonProperties` string. `cobblemon:corsola galarian` is a Ghost-type Corsola;
 * `cobblemon:corsola` is a Water-type one, and nothing downstream can tell that the second was meant
 * to be the first. So the fragment travels with the species from the moment the roster is written.
 *
 * It is deliberately opaque here — `punk_form=amped`, `blazing_mode=standard` and `galarian` are all
 * just text this module hands to Cobblemon. Parsing it would mean this module owning a copy of
 * Cobblemon's property grammar, which is a copy that goes stale.
 *
 * @property properties null and blank mean the same thing; null is what a roster with no fragment
 *   parses to, and [propertiesString] treats them identically so callers never have to check.
 */
data class TeamSpecies(
    val id: ResourceLocation,
    val properties: String? = null,
) {
    /**
     * The full `PokemonProperties` string for this species at [level], with [heldItem] if it has one.
     *
     * **No EVs, and that is the decision rather than an omission** (§2.30). PokéRogue removed EVs from
     * stat calculation entirely, so their trainers have none; ours have none either. Our *players*
     * still earn EVs, because EVs are our stand-in for PokéRogue's stacking modifiers — the asymmetry
     * is the two sides staying consistent with each other, not an oversight. Anyone adding `_ev` keys
     * here is re-opening §2.4, not fixing a bug.
     *
     * No moves either, and that is the point of generating at all: `level=` is enough for Cobblemon to
     * derive the level-appropriate moveset when it creates the Pokémon. An authored team cannot do
     * that — level-scaling it moves a number and leaves a wave-10 moveset on a level-100 Pokémon
     * (§2.30's whole argument).
     */
    fun propertiesString(
        level: Int,
        heldItem: ResourceLocation? = null,
        shields: Int = 0,
    ): String = buildString {
        append("species=").append(id)
        properties?.takeIf { it.isNotBlank() }?.let { append(' ').append(it.trim()) }
        append(" level=").append(level)
        // Shields win the item slot outright, and that is the decision rather than a precedence
        // accident (§2.32). A Pokémon holds one item; the shields ARE this boss's power, so they
        // are what the budget is spent on. Silently keeping the rolled Leftovers instead would give
        // a boss the item and not the mechanic, which is the failure nobody would notice.
        when {
            shields > 0 -> append(' ').append(BossShields.heldItemProperty(shields))
            heldItem != null -> append(" held_item=").append(heldItem)
        }
    }
}

/**
 * One species and every stage of its evolution line, base form first.
 *
 * ### Why a line and not a species
 *
 * PokéRogue's signature table names *one* species per slot, and their trainers show it at a stage
 * that depends on the wave — a Geodude early, a Golem from wave 80 (§2.30's "fully evolved from wave
 * 80"). Storing the line is what lets one roster entry answer both, and storing it as **data** rather
 * than walking Cobblemon's evolution graph at the encounter is what keeps a Cobblemon version bump
 * from silently changing which Pokémon a checkpointed run meets at a wave it has not reached yet.
 *
 * @property stages at least one, ordered base → final. A single-stage line (Lapras, Aerodactyl) is
 *   normal and means the same Pokémon at every wave.
 * @property weight relative likelihood against the other alternatives in the same slot. It exists for
 *   exactly one case: a species that branches (Tyrogue into three Hitmons) becomes three lines, and
 *   without weights a slot PokéRogue wrote as a coin flip between two species would silently become
 *   one-in-four for the species that does not branch. `ops/gen_pokerogue_roster.py` splits one unit
 *   of weight across a species' branches for that reason.
 */
data class SpeciesLine(
    val stages: List<TeamSpecies>,
    val weight: Double = 1.0,
) {
    init {
        require(stages.isNotEmpty()) { "a species line needs at least one stage" }
    }

    /**
     * The stage to bring at [index], clamped.
     *
     * Clamping rather than requiring an in-range index: the index comes from a wave-to-stage schedule
     * that knows nothing about how long any particular line is, and every line is a different length.
     * The clamp is the rule "a two-stage line is already fully evolved when a three-stage one is
     * halfway", which is what we want, expressed where it cannot be forgotten.
     */
    fun stageAt(index: Int): TeamSpecies = stages[index.coerceIn(0, stages.lastIndex)]
}

/**
 * One party slot, and the alternatives that could fill it.
 *
 * PokéRogue writes a slot as either one species or a set — `[OMANYTE, KABUTO]` — and §2.30 settles
 * that the set is resolved by a **seeded pick at the encounter**, not by authoring Brock-A and
 * Brock-B as separate roster entries. The rejected alternative is worth remembering: two entries in
 * one pool means the pool can draw both, and meeting Brock twice in a run reads as a bug.
 */
data class SignatureSlot(val alternatives: List<SpeciesLine>) {
    init {
        require(alternatives.isNotEmpty()) { "a signature slot needs at least one alternative" }
    }
}

/**
 * A trainer whose team is **generated at the encounter** rather than authored.
 *
 * ### What this replaces, and why
 *
 * Until §2.30 a roster held trainer ids and nothing else, and the team was an authored RCT trainer.
 * That was never a design preference — §2.6 assumed RCT would build the battle, so the team had to be
 * RCT's. It does not: the bridge provider assembles the battle itself (it has to, because a run's
 * party is not the player's real party), so the opponent's team was ours to decide all along.
 *
 * Generating it fixes the thing bands existed to work around. Level-scaling an authored team does not
 * scale its **moveset**, so a team written for wave 10 arrives at wave 150 at level 100 still throwing
 * wave-10 moves. Generating at the encounter derives the moveset for the level being generated, so
 * that problem disappears and bands go back to meaning only "which leaders appear when".
 *
 * ### An entry is data about a trainer, not a replacement for one
 *
 * [trainerId] still names an RCT trainer, and it still has to exist: the NPC, its name, its skin, its
 * bag and its AI all come from there (see `RctTrainerParts` on the bridge side). What generation
 * replaces is only the *team*. A roster that names a trainer with no entry here is the **authored**
 * path, untouched — which is how the Elite Four, the champion and any curated rival stay hand-made
 * (§2.30). A generated team is uniform by nature; the fights players remember are the tuned ones.
 *
 * @property filler extra slots for the bands whose party size is 5 or 6. A signature table has four
 *   slots and §2.19 leaves the last third of a run at flat level 100, where party size is one of the
 *   few difficulty levers left — so the fifth and sixth Pokémon have to come from somewhere. Empty is
 *   legal and means a band asking for six gets four: a smaller party, not an invented one.
 */
data class TrainerEntry(
    val trainerId: ResourceLocation,
    val signature: List<SignatureSlot>,
    val filler: List<SignatureSlot> = emptyList(),
) {
    init {
        require(signature.isNotEmpty()) {
            "trainer entry '$trainerId' has no signature slots — omit the entry entirely for an " +
                "authored fight, rather than declaring a generator with nothing to generate from"
        }
    }
}
