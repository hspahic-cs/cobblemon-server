package com.cobblemonroguelite.data.reward

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.modifier.PlayerModifier
import com.cobblemonroguelite.run.RunPassive
import net.minecraft.resources.ResourceLocation

/**
 * One thing a run can hand a player.
 *
 * ### The surface, and why it is exactly this
 *
 * §2.4 of the plan rules out PokéRogue's stacking in-battle modifiers — patching Cobblemon's
 * Showdown bundle is a maintenance tax on every upstream bump, feeds illegal states to the AI, and
 * cannot ship to anyone else. What replaces it is *persistent run-party state applied between
 * waves*, and the plan enumerates it: EVs/vitamins, levels, nature mints, ability patches, evolution
 * items, held items, TMs. The subtypes below are that list, one for one. A reward that is not on
 * that list is not expressible here, which is the point — the schema is the boundary that keeps
 * "rewards are data" from quietly becoming "rewards are arbitrary".
 *
 * ### Why sealed, i.e. why a datapack cannot add a reward type
 *
 * Every type here needs code that applies it to a Pokémon. A datapack can add an *entry*, a *tier*,
 * a whole *table*; it cannot add a kind of reward, because there would be nothing to run. Sealing
 * makes that honest, and it makes the apply-side `when` exhaustive, so adding a type later is a
 * compile error at every site that has to handle it rather than a silently ignored reward.
 *
 * ### What is deliberately not here
 *
 * **Targeting.** Nothing says *which* party member gets the EVs. That is a run-time choice — the
 * player picks, or the run picks — and baking it into the table would make every entry need
 * duplicating per slot.
 *
 * **Existence of the ids.** [Mint], [BagItem], [HeldItem], [AbilityPatch] and [TechnicalMachine]
 * carry ids that are only checked for *syntax* at load. Two reasons: Cobblemon's `Moves` and
 * `Abilities` are themselves datapack registries reloading in the same cycle, so a check here would
 * be answering from whatever half of them had loaded; and an id belonging to an optional third-party
 * mod is legitimate on a server that has it and a false alarm on one that does not. An unresolvable
 * id is therefore reported when the reward is *applied*, where the failure is real. The gap this
 * leaves is a misspelt-but-well-formed id, e.g. `cobblemon:leftover`.
 */
sealed interface RunReward {

    /** EVs, i.e. what a vitamin does. [amount] may be negative to model an EV-reducing berry. */
    data class Evs(val stat: Stats, val amount: Int) : RunReward

    /** Levels, i.e. what a rare candy does. */
    data class Levels(val amount: Int) : RunReward

    /** A nature mint. */
    data class Mint(val nature: ResourceLocation) : RunReward

    /**
     * An ability patch or capsule. [ability] null means "the hidden ability", which is the patch;
     * a named ability is the capsule pointed at a specific slot.
     */
    data class AbilityPatch(val ability: String?) : RunReward

    /**
     * An item into the run bag. Evolution items are this: the run bag is the only bag a run has
     * (§2.11 bans the player's own), so "give them a Metal Coat" and "give them a Revive" are the
     * same mechanism.
     */
    data class BagItem(val item: ResourceLocation, val count: Int) : RunReward

    /** An item attached to a party member. Never stacks, hence no count. */
    data class HeldItem(val item: ResourceLocation) : RunReward

    /**
     * A tiered modifier held item — §2.33's line, distinct from [HeldItem] because granting one is
     * not idempotent placement but a *ladder step*: a holder already on the line goes up a tier
     * rather than gaining a second copy (§2.34). The id names a [PlayerModifier], a closed set like
     * the reward types themselves, because each line needs a JS file and mint code to exist —
     * a datapack cannot invent one, so an unknown id here is always a typo worth naming at load.
     */
    data class ModifierItem(val modifier: PlayerModifier) : RunReward

    /** A TM: teach [move] to a party member. */
    data class TechnicalMachine(val move: String) : RunReward

    /**
     * Run credits, i.e. what PokéRogue's Nugget is (2026-07-31: their money items restored as this,
     * one type for all three). [multiplier] scales the shared wave-money curve
     * ([com.cobblemonroguelite.shop.WaveMoneyCurve]) at the wave the reward is granted — a multiplier
     * rather than an amount for the same reason the shop's `cost_multiplier` is one: a flat number is
     * decisive early and pocket change late, and the curve is the thing both halves of the economy
     * already price against. Theirs are 1x (Nugget), 2.5x (Big Nugget) and 10x (Relic Gold) of the
     * same formula.
     */
    data class Credits(val multiplier: Double) : RunReward

    /**
     * One stack of a run passive — §2.43's team-wide permanent buff, PokéRogue's EXP items being
     * the first three. No party target and no item: the stack lands on
     * [com.cobblemonroguelite.run.RunState.passiveStacks] and is read by the battle layer's EXP
     * hook. The set of kinds is [RunPassive]'s closed enum, for the same reason this interface is
     * sealed — a passive nobody's code reads is a purchase that does nothing.
     */
    data class Passive(val passive: RunPassive) : RunReward

    companion object {

        /**
         * Read the `reward` object of a table entry. Returns null with problems recorded if it is
         * unusable.
         *
         * Type ids are bare strings rather than namespaced ones because the set is closed (see the
         * class docs) — there is no second mod that could collide with `ev`, so a namespace would be
         * ceremony that every hand-written table has to type.
         */
        fun parse(view: JsonView): RunReward? {
            val type = view.requireString("type")
            val reward = when (type) {
                null -> null
                "ev" -> parseEvs(view)
                "level" -> view.requireInt("amount")?.let { amount ->
                    if (amount == 0) {
                        view.problem("amount", "must not be 0 — a reward of no levels is not a reward")
                        null
                    } else {
                        Levels(amount)
                    }
                }
                "nature" -> parseId(view, "nature", COBBLEMON)?.let { Mint(it) }
                "ability" -> parseAbilityPatch(view)
                "item" -> parseBagItem(view)
                "held_item" -> parseId(view, "item", MINECRAFT)?.let { HeldItem(it) }
                "modifier_item" -> parseModifierItem(view)
                "move" -> nonBlank(view, "move")?.let { TechnicalMachine(it) }
                "credits" -> parseCredits(view)
                "passive" -> parsePassive(view)
                else -> {
                    view.problem("type", "unknown reward type '$type' (expected one of: ${TYPES.joinToString(", ")})")
                    null
                }
            }
            // Runs even on the failure paths above: a file with both a bad type *and* a stray field
            // should report both, so the author fixes the file once.
            view.expectNoUnknownKeys()
            return reward
        }

        private fun parseEvs(view: JsonView): RunReward? {
            val statName = view.requireString("stat")
            val amount = view.requireInt("amount")
            val stat = statName?.let { name ->
                EV_STATS[name.lowercase()] ?: run {
                    // Naming the accepted values matters more here than anywhere else in the schema:
                    // "defence" vs "defense" is a coin flip for the author, and both are accepted for
                    // exactly that reason.
                    view.problem("stat", "'$name' is not an EV stat (expected one of: ${EV_STATS.keys.joinToString(", ")})")
                    null
                }
            }
            if (amount != null && amount == 0) {
                view.problem("amount", "must not be 0 — a reward of no EVs is not a reward")
                return null
            }
            if (amount != null && (amount > EV_LIMIT || amount < -EV_LIMIT)) {
                // Not a balance opinion: 252 is the game's own per-stat ceiling, so a larger number
                // cannot mean anything and is a slipped digit.
                view.problem("amount", "$amount is outside the possible EV range (-$EV_LIMIT..$EV_LIMIT)")
                return null
            }
            return if (stat != null && amount != null) Evs(stat, amount) else null
        }

        /**
         * Absent `ability` is the ability *patch* (hidden ability), which is the common case, so it
         * is the default rather than something the author has to spell out. Present-but-blank is a
         * mistake, not a shorthand for absent — treating it as absent would hand back the hidden
         * ability to someone who meant to name a specific one and left the value empty.
         */
        private fun parseAbilityPatch(view: JsonView): RunReward? {
            val ability = view.optionalString("ability") ?: return AbilityPatch(null)
            if (ability.isBlank()) {
                view.problem("ability", "must not be blank — omit the field entirely for the hidden ability")
                return null
            }
            return AbilityPatch(ability)
        }

        private fun parseCredits(view: JsonView): RunReward? {
            val multiplier = view.requireDouble("multiplier") ?: return null
            if (multiplier <= 0.0) {
                view.problem("multiplier", "must be greater than 0, was $multiplier — a reward of no credits is not a reward")
                return null
            }
            return Credits(multiplier)
        }

        /**
         * `{ "type": "passive", "passive": "exp_charm" }`. The set is closed ([RunPassive]), so an
         * unknown name is rejected *naming the whole valid set* — with three members, listing them
         * is worth more to the table's author than any guess at what they meant.
         */
        private fun parsePassive(view: JsonView): RunReward? {
            val name = nonBlank(view, "passive") ?: return null
            val passive = RunPassive.byId(name)
            if (passive == null) {
                view.problem("passive", "'$name' is not a run passive (expected one of: ${RunPassive.ids.joinToString(", ")})")
                return null
            }
            return Passive(passive)
        }

        /**
         * A [ModifierItem], whose id — unlike every open-set id in this file — CAN be checked at
         * load: the set is closed (see the subtype docs), so an unknown value is a mistake now, not
         * an optional mod later, and it is named with the accepted values the way the EV stats are.
         */
        private fun parseModifierItem(view: JsonView): RunReward? {
            val raw = nonBlank(view, "item") ?: return null
            val modifier = PlayerModifier.byId(raw)
            if (modifier == null) {
                view.problem(
                    "item",
                    "'$raw' is not a tiered modifier item (expected one of: " +
                        "${PlayerModifier.entries.joinToString(", ") { it.id }})",
                )
                return null
            }
            return ModifierItem(modifier)
        }

        private fun nonBlank(view: JsonView, key: String): String? {
            val value = view.requireString(key) ?: return null
            if (value.isBlank()) {
                view.problem(key, "must not be blank")
                return null
            }
            return value
        }

        private fun parseBagItem(view: JsonView): RunReward? {
            val item = parseId(view, "item", MINECRAFT)
            val count = view.optionalInt("count") ?: 1
            if (count < 1) {
                view.problem("count", "must be at least 1, was $count")
                return null
            }
            return item?.let { BagItem(it, count) }
        }

        /**
         * Read a namespaced id, defaulting the namespace when the author omitted it.
         *
         * The default differs per field on purpose: an unqualified item is `minecraft:` because that
         * is what every other datapack in the game means by it, while an unqualified nature is
         * `cobblemon:` because there are no vanilla natures and defaulting one to `minecraft:` would
         * only ever produce a confusing failure later.
         */
        private fun parseId(view: JsonView, key: String, defaultNamespace: String): ResourceLocation? {
            val raw = view.requireString(key) ?: return null
            val qualified = if (raw.contains(':')) raw else "$defaultNamespace:$raw"
            val id = ResourceLocation.tryParse(qualified)
            if (id == null) {
                view.problem(key, "'$raw' is not a valid id (letters, digits, '_', '-', '.', '/' only, optionally 'namespace:path')")
                return null
            }
            return id
        }

        private const val COBBLEMON = "cobblemon"
        private const val MINECRAFT = "minecraft"

        /** The per-stat EV ceiling in the games. Used only to catch a slipped digit, not to balance. */
        private const val EV_LIMIT = 252

        private val TYPES = listOf("ev", "level", "nature", "ability", "item", "held_item", "modifier_item", "move", "credits", "passive")

        /**
         * EV-bearing stats only. `evasion` and `accuracy` are [Stats] entries but are battle-only and
         * have no EVs, so accepting them would produce a reward that does nothing.
         */
        private val EV_STATS: Map<String, Stats> = linkedMapOf(
            "hp" to Stats.HP,
            "attack" to Stats.ATTACK,
            "defence" to Stats.DEFENCE,
            "defense" to Stats.DEFENCE,
            "special_attack" to Stats.SPECIAL_ATTACK,
            "special_defence" to Stats.SPECIAL_DEFENCE,
            "special_defense" to Stats.SPECIAL_DEFENCE,
            "speed" to Stats.SPEED,
        )
    }
}
