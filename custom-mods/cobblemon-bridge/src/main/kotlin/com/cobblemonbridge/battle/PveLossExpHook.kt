package com.cobblemonbridge.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation

/**
 * Grants the EXP a losing player *would* have earned for the enemy Pokémon they actually defeated.
 *
 * Cobblemon computes all battle EXP in `PokemonBattle.end()`, gated to the winning side — so on a
 * loss (party wipe or flee) the player gets nothing, even for opponents they KO'd. That's the
 * old-gen rule; this restores modern-gen behavior where you keep the per-defeat EXP regardless of
 * the battle's outcome, including on Pokémon that themselves fainted.
 *
 * Approach: on `BATTLE_VICTORY` (which fires for every battle end, with the AI/winner side as the
 * victor), for each `PlayerBattleActor` on the LOSING side we replay `end()`'s participant grant
 * for the opposing Pokémon that fainted — reusing Cobblemon's own [Cobblemon.experienceCalculator]
 * (so amounts match exactly) and [com.cobblemon.mod.common.api.battles.model.actor.BattleActor.awardExperience]
 * (so the trainer-exp boost, Lucky Egg, and other `ExperienceGainedEvent` modifiers all still apply).
 *
 * No double-grant: `end()` never pays the loser, and `BATTLE_VICTORY` fires after `end()`, so this
 * is strictly additive for losers; winners are untouched (already paid by `end()`). We only grant
 * to **participants** — the player mons in each defeated enemy's `facedOpponents` — i.e. exactly the
 * mons that "would have gained EXP if the battle ended". (Bench/EXP-Share mons are intentionally
 * left out; the held-item share rule isn't replicated here.)
 *
 * Scope: all PvE — trainers, gyms, E4, and wild — any battle whose loser is a player.
 */
object PveLossExpHook {

    fun registerEvents() {
        // LOWEST so we run after Cobblemon's own end()-driven distribution has settled.
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.LOWEST) { event ->
            runCatching { grantLoserExp(event) }.onFailure {
                CobblemonBridge.logger.warn("pve-loss-exp: failed to grant loser EXP", it)
            }
        }
    }

    private fun grantLoserExp(event: BattleVictoryEvent) {
        val losingPlayers = event.losers.filterIsInstance<PlayerBattleActor>()
        if (losingPlayers.isEmpty()) return  // player won / spectator / PvP — nothing to restore

        // Enemy Pokémon the player(s) defeated = fainted mons on the winning side.
        val defeatedEnemies = event.winners.flatMap { it.pokemonList }.filter { it.health <= 0 }
        if (defeatedEnemies.isEmpty()) return

        val calc = Cobblemon.experienceCalculator

        // Mirror Cobblemon end()'s per-mon multiplier so Lucky Egg + EXP Share apply to loss-exp too.
        // calculate()'s 3rd arg folds in those item multipliers (NOT the global experienceMultiplier,
        // which calculate applies itself); awardExperience then fires the exp event so TrainerExpBoost
        // still stacks. Without this, loss-exp ignored both held items.
        val shareMult = Cobblemon.config.experienceShareMultiplier.toDouble()
        val luckyMult = Cobblemon.config.luckyEggMultiplier.toDouble()

        for (player in losingPlayers) {
            var totalGranted = 0
            for (enemy in defeatedEnemies) {
                val faced = enemy.facedOpponents  // the player mons that battled this enemy
                for (mon in player.pokemonList) {
                    val held = heldItemId(mon)
                    // Participants get full EXP; benched mons get EXP only if they hold an EXP Share.
                    val shareFactor = when {
                        mon in faced -> 1.0                          // participant (incl. fainted)
                        held == EXP_SHARE -> shareMult               // benched share-holder
                        else -> continue                             // benched, no share -> nothing
                    }
                    val luckyFactor = if (held == LUCKY_EGG) luckyMult else 1.0
                    val exp = calc.calculate(enemy, mon, shareFactor * luckyFactor)
                    if (exp <= 0) continue
                    player.awardExperience(mon, exp)
                    totalGranted += exp
                }
            }
            if (totalGranted > 0) {
                CobblemonBridge.logger.info(
                    "pve-loss-exp: granted {} EXP to {} for {} defeated enemy mon(s) after a loss",
                    totalGranted,
                    player.entity?.gameProfile?.name ?: "?",
                    defeatedEnemies.size,
                )
            }
        }
    }

    private val LUCKY_EGG = ResourceLocation.fromNamespaceAndPath("cobblemon", "lucky_egg")
    private val EXP_SHARE = ResourceLocation.fromNamespaceAndPath("cobblemon", "exp_share")

    /** Registry id of the Pokémon's held item, or null if it isn't holding anything. */
    private fun heldItemId(mon: BattlePokemon): ResourceLocation? {
        val stack = mon.effectedPokemon.heldItem()
        return if (stack.isEmpty) null else BuiltInRegistries.ITEM.getKey(stack.item)
    }
}
