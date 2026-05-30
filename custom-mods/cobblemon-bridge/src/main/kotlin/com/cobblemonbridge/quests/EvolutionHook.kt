package com.cobblemonbridge.quests

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.server.level.ServerPlayer

/**
 * Fires the `server:evolve_exeggutor` advancement criterion when a player evolves any
 * Pokémon INTO Exeggutor. Part of the 0.7.25 Exeggcute / Cobbleworkers onboarding quest
 * chain (the parent `receive_leaf_stone` gives the player a Leaf Stone; this hook is
 * how we detect they actually used it).
 *
 * Why this exists: vanilla MC advancements have no native "evolved a Pokémon" trigger.
 * Cobblemon fires `EvolutionCompleteEvent` after the species swap completes; we
 * subscribe, resolve the post-evolution species name, and grant the criterion via the
 * same `QuestAdvancements.award` plumbing used by PokedexProgressHook, GymDefeatHook,
 * HealQuestHook, etc.
 *
 * Pokemon ownership is read from `pokemon.getOwnerPlayer()` (returns the ServerPlayer
 * or null). NPC-owned trainer pokemon evolving (RCT trainers in battle) is gracefully
 * a no-op because their owner is null.
 *
 * Reusing this for future species-evolve quests is just adding another `when` arm.
 */
object EvolutionHook {

    fun registerEvents() {
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL) { event ->
            handle(event)
        }
    }

    internal fun handle(event: EvolutionCompleteEvent) {
        val player = event.pokemon.getOwnerPlayer() as? ServerPlayer ?: return
        val speciesName = event.pokemon.species.name
        when (speciesName.lowercase()) {
            "exeggutor" -> awardOrLog(player, "server:evolve_exeggutor")
            // Future species-specific evolution quests slot in here.
        }
    }

    private fun awardOrLog(player: ServerPlayer, advancementId: String) {
        val awarded = QuestAdvancements.award(player, advancementId, criterion = "done")
        if (awarded) {
            CobblemonBridge.logger.info(
                "evolution-hook: awarded {} to {}", advancementId, player.gameProfile.name,
            )
        }
    }
}
