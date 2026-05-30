package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent
import com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor
import com.cobblemonbridge.CobblemonBridge

/**
 * Doubles the EXP gain from any battle whose losing side included a `TrainerBattleActor`
 * (gym leaders, wild RCT trainers, E4, etc.). Wild Pokémon battles, candy, /command exp,
 * and sidemod-driven grants are unaffected.
 *
 * Detection: subscribe to `ExperienceGainedEvent.Pre` (Cobblemon fires this BEFORE the
 * exp is applied, with the calculated amount and a mutable `setExperience` setter). When
 * the source is a [BattleExperienceSource], walk `getFacedPokemon()` — these are the
 * Pokémon the winning mon defeated — and check each one's `actor`. If any of them is a
 * `TrainerBattleActor`, we're looking at a trainer-battle exp grant; multiply by
 * [EXP_BOOST_MULTIPLIER].
 *
 * Stacks multiplicatively with Cobblemon's global `experienceMultiplier` (currently 2.0
 * in main.json), Lucky Egg (1.5×), and EXP Share (0.5× per non-participant). End-to-end
 * a trainer-defeat exp gain is `base × 2.0 × 2.0 = 4×` of vanilla; with Lucky Egg `× 1.5`
 * = 6× vanilla.
 *
 * Pre-event is cancelable but we don't cancel; only mutate the amount. The handler logs
 * at debug level so the multiplier path is visible during playtest tuning.
 */
object TrainerExpBoostHook {

    /** Bonus multiplier applied to trainer-battle EXP. 2.0 = double. */
    private const val EXP_BOOST_MULTIPLIER: Double = 2.0

    fun registerEvents() {
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.NORMAL) { event ->
            handle(event)
        }
    }

    internal fun handle(event: ExperienceGainedEvent.Pre) {
        val src = event.source as? BattleExperienceSource ?: return
        val facedATrainer = src.facedPokemon.any { it.actor is TrainerBattleActor }
        if (!facedATrainer) return
        val before = event.experience
        val boosted = (before * EXP_BOOST_MULTIPLIER).toInt()
        event.experience = boosted
        CobblemonBridge.logger.debug(
            "trainer-exp-boost: {} L{} {} -> {} (x{})",
            event.pokemon.species.name, event.pokemon.level, before, boosted, EXP_BOOST_MULTIPLIER,
        )
    }
}
