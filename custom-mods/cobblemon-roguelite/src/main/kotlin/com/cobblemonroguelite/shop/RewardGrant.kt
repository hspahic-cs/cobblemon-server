package com.cobblemonroguelite.shop

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.stats.SidemodEvSource
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonroguelite.data.reward.RunReward
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/shop")

/**
 * Writes a resolved reward onto a live Pokémon — the impure end of the between-wave step.
 *
 * ### What is deliberately not here
 *
 * No targeting (that is [RewardTargeting], which is pure and tested), no pricing, no offer. This file
 * is the thin layer that a unit test cannot reach, because a `Pokemon` cannot be constructed outside a
 * booted game. It is kept as small as the Cobblemon API allows for exactly that reason — the same split
 * [com.cobblemonroguelite.starter.HiddenAbilityGrant] makes from `HiddenAbilityUnlock`, and the reason
 * both of those files exist separately.
 *
 * ### Every branch reports rather than throws
 *
 * A reward names ids that were only syntax-checked at load ([RunReward]'s docs explain why: Cobblemon's
 * `Moves` and `Abilities` are themselves datapack registries reloading in the same cycle, and an id
 * from an optional mod is legitimate on one server and absent on another). So an unresolvable id is a
 * real, expected outcome here rather than a bug, and it must not take the run down: the player has
 * already spent credits or already taken their one free option by the time this runs. Each branch
 * returns a [GrantResult] and logs, so the failure names the id an operator has to fix.
 */
object RewardGrant {

    /**
     * Apply [reward] to [party] at [target].
     *
     * The caller is expected to have charged credits already — see the ordering note on
     * [com.cobblemonroguelite.run.RunController]'s shop entry points. That ordering is deliberate and
     * uncomfortable: charging first means a [GrantResult.Failed] costs the player their credits. The
     * alternative is worse, because granting first means a crash between grant and charge is a free
     * item, and an item that cannot be granted is an operator error a refund would hide.
     */
    fun apply(reward: RunReward, target: RewardTarget, party: List<Pokemon>): GrantResult = when (target) {
        is RewardTarget.Unresolved -> GrantResult.Failed(target.reason)

        // Party-wide is only ever a bag item today ([RewardTargeting.needsMember]), and a bag item does
        // not touch a Pokémon at all — so this branch is about the run, not the party.
        RewardTarget.WholeParty -> when (reward) {
            is RunReward.BagItem -> grantBagItem(reward)
            else -> GrantResult.Failed("reward ${reward::class.simpleName} needs a party member")
        }

        is RewardTarget.Member -> {
            val pokemon = party.getOrNull(target.index)
            if (pokemon == null) {
                // Reachable if the party shrank between targeting and applying — a faint that emptied a
                // slot, or a swap-or-release resolved in between.
                GrantResult.Failed("party slot ${target.index + 1} is no longer there")
            } else {
                applyToMember(reward, pokemon)
            }
        }
    }

    private fun applyToMember(reward: RunReward, pokemon: Pokemon): GrantResult = runCatching {
        when (reward) {
            is RunReward.Evs -> grantEvs(reward, pokemon)
            is RunReward.Levels -> grantLevels(reward, pokemon)
            is RunReward.Mint -> grantMint(reward, pokemon)
            is RunReward.AbilityPatch -> grantAbility(reward, pokemon)
            is RunReward.HeldItem -> grantHeldItem(reward, pokemon)
            is RunReward.TechnicalMachine -> grantMove(reward, pokemon)
            // A bag item targeted at a member: legal input, since [RewardTargeting] ignores a slot on a
            // party-wide reward, so treat it as the bag grant it is rather than refusing.
            is RunReward.BagItem -> grantBagItem(reward)
        }
    }.getOrElse { failure ->
        log.warn("roguelite: granting {} to {} threw", reward, pokemon.species.resourceIdentifier, failure)
        GrantResult.Failed("could not apply that reward — see the server log")
    }

    /**
     * EVs, via Cobblemon's own `add`, which enforces both caps (252 per stat, 510 total) itself.
     *
     * Returning the *actual* delta rather than the requested one is the point: a Pokémon already at 252
     * Attack gains nothing from a Protein, and telling the player "+10 Attack EVs" when nothing changed
     * is the kind of lie that gets reported as a bug. `add` returns the new value, so the difference is
     * the truth.
     */
    private fun grantEvs(reward: RunReward.Evs, pokemon: Pokemon): GrantResult {
        val before = pokemon.evs.getOrDefault(reward.stat)
        // The three-argument overload, because the two-argument one is deprecated and because the
        // source is a real API concept: Cobblemon uses it to decide whether the gain counts as a battle
        // gain. SidemodEvSource is the hook it provides for exactly this — a mod granting EVs directly.
        pokemon.evs.add(reward.stat, reward.amount, SidemodEvSource(SIDEMOD_ID, pokemon))
        val gained = pokemon.evs.getOrDefault(reward.stat) - before
        if (gained == 0) {
            return GrantResult.NoEffect("${pokemon.species.name} cannot gain more ${reward.stat.identifier.path} EVs")
        }
        return GrantResult.Ok("${pokemon.species.name}: ${plus(gained)} ${reward.stat.identifier.path} EVs")
    }

    /**
     * Levels, clamped at 100.
     *
     * Clamped here rather than trusted to Cobblemon: §2.19's curve already flattens at 100 for the
     * *enemy* side, and a party member pushed past it by a Rare Candy would be a party that outscales
     * the ceiling the whole difficulty curve is built on.
     */
    private fun grantLevels(reward: RunReward.Levels, pokemon: Pokemon): GrantResult {
        val before = pokemon.level
        val after = (before + reward.amount).coerceIn(1, MAX_LEVEL)
        if (after == before) {
            return GrantResult.NoEffect("${pokemon.species.name} is already level $before")
        }
        pokemon.level = after
        return GrantResult.Ok("${pokemon.species.name}: level $before -> $after")
    }

    private fun grantMint(reward: RunReward.Mint, pokemon: Pokemon): GrantResult {
        val nature = runCatching { Natures.getNature(reward.nature) }.getOrNull()
            ?: return unresolved("nature", reward.nature.toString())
        val before = pokemon.nature.name
        pokemon.nature = nature
        return GrantResult.Ok("${pokemon.species.name}: nature ${before.path} -> ${nature.name.path}")
    }

    /**
     * An ability patch.
     *
     * A **named** ability is forced, for the reason [com.cobblemonroguelite.starter.HiddenAbilityUnlock]
     * documents at length: there is no pool coordinate for an ability the form's pool does not list, so
     * a non-forced grant would be silently replaced by an ordinary ability at the next form change.
     *
     * A **null** ability means "the hidden ability", which is precisely what `HiddenAbilityGrant`
     * already resolves — including the pool-index trick that lets the ability survive evolution. It is
     * delegated rather than reimplemented, because a second copy of that reasoning would drift.
     */
    private fun grantAbility(reward: RunReward.AbilityPatch, pokemon: Pokemon): GrantResult {
        val named = reward.ability
            ?: return when (val granted = com.cobblemonroguelite.starter.HiddenAbilityGrant.applyTo(pokemon)) {
                null -> GrantResult.NoEffect("${pokemon.species.name} has no hidden ability to patch to")
                else -> GrantResult.Ok("${pokemon.species.name}: ability -> $granted")
            }
        val template = runCatching { Abilities.get(named) }.getOrNull()
            ?: return unresolved("ability", named)
        pokemon.updateAbility(template.create(true, Priority.HIGHEST))
        return GrantResult.Ok("${pokemon.species.name}: ability -> ${template.name}")
    }

    /**
     * A held item, via `swapHeldItem`, which returns whatever was displaced.
     *
     * The displaced item is **reported, not recovered**. A run has one bag and no storage for a loose
     * item (§2.11), so there is nowhere to put it; saying so is the honest outcome, and it lets a player
     * decide not to overwrite the Leftovers they were relying on. Silently destroying it would be the
     * same act without the warning.
     */
    private fun grantHeldItem(reward: RunReward.HeldItem, pokemon: Pokemon): GrantResult {
        val item = BuiltInRegistries.ITEM.getOptional(reward.item).orElse(null)
            ?: return unresolved("item", reward.item.toString())
        val displaced = pokemon.swapHeldItem(ItemStack(item, 1))
        val note = if (displaced.isEmpty) "" else " (replaced ${displaced.hoverName.string}, which is gone)"
        return GrantResult.Ok("${pokemon.species.name}: holding ${item.description.string}$note")
    }

    /**
     * A TM.
     *
     * Added to a free move slot when there is one, and otherwise **refused** rather than overwriting a
     * move. Overwriting is what mainline does, but mainline asks first; a command that silently replaced
     * a move would be the single most destructive thing in this file, since a move is not recoverable
     * inside a run. Refusing tells the player to make room, which is a decision they can act on.
     */
    private fun grantMove(reward: RunReward.TechnicalMachine, pokemon: Pokemon): GrantResult {
        val template = runCatching { Moves.getByName(reward.move) }.getOrNull()
            ?: return unresolved("move", reward.move)
        val moveSet = pokemon.moveSet
        if (moveSet.getMoves().any { it.name.equals(template.name, ignoreCase = true) }) {
            return GrantResult.NoEffect("${pokemon.species.name} already knows ${template.displayName.string}")
        }
        if (!moveSet.hasSpace()) {
            return GrantResult.NoEffect(
                "${pokemon.species.name} knows four moves already — forget one before using this",
            )
        }
        moveSet.add(template.create())
        return GrantResult.Ok("${pokemon.species.name} learned ${template.displayName.string}")
    }

    /**
     * A bag item.
     *
     * **Not implemented yet, and it reports that rather than pretending.** §2.11 gives a run its own bag
     * and §2.12 makes evolution items bag items, but the bag itself does not exist — there is no store
     * to put this in. Returning [GrantResult.Failed] means an operator who ships a bag-item entry finds
     * out immediately, which is much better than a purchase that takes credits and does nothing.
     */
    private fun grantBagItem(reward: RunReward.BagItem): GrantResult {
        log.warn("roguelite: a bag-item reward was granted and the run bag does not exist yet: {}", reward)
        return GrantResult.Failed("bag items are not implemented yet — remove this entry from the table")
    }

    private fun unresolved(kind: String, id: String): GrantResult {
        log.warn("roguelite: a reward names a {} this server does not have: '{}'", kind, id)
        return GrantResult.Failed("this server has no $kind '$id' — the table needs fixing")
    }

    private fun plus(amount: Int) = if (amount >= 0) "+$amount" else "$amount"

    private const val MAX_LEVEL = 100

    /** Names this mod as the EV source, which is what [SidemodEvSource] is for. */
    private const val SIDEMOD_ID = "cobblemon_roguelite"
}

/**
 * What applying a reward did. Three outcomes and not a boolean, because they need different words and
 * two of them are not failures.
 */
sealed interface GrantResult {

    /** Applied. [message] describes the change, and is written to be shown to the player. */
    data class Ok(val message: String) : GrantResult

    /**
     * Nothing changed, and that is legitimate — capped EVs, a level-100 Pokémon, a move already known.
     * Distinct from [Failed] because nothing is broken and nobody needs to fix anything.
     */
    data class NoEffect(val message: String) : GrantResult

    /** Could not be applied. [reason] names what an operator has to change. */
    data class Failed(val reason: String) : GrantResult
}
