package com.cobblemonbridge.voice

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.adapters.RctBridge
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gives RCT trainers Elite-Four-style voice lines, shown **only to the player(s) in that battle**
 * (never a server broadcast). Lines come from [VoiceLines] keyed by the trainer's id; a trainer
 * with no entry (or an empty list for a trigger) stays silent.
 *
 * Triggers:
 *  - `intro`   — battle start ([CobblemonEvents.BATTLE_STARTED_POST]).
 *  - `taunt`   — the moment the trainer is reduced to their last Pokémon
 *                ([CobblemonEvents.BATTLE_FAINTED]; once per battle).
 *  - `victory` — the trainer beat the player / `defeat` — the player beat the trainer
 *                ([CobblemonEvents.BATTLE_VICTORY]).
 *
 * Trainer detection reuses [RctBridge.trainerIdForBattle]; the trainer side is the
 * [AIBattleActor] (covers Cobblemon's and rctapi's trainer actors — see [com.cobblemonbridge.battle.GymDefeatHook]).
 */
object TrainerVoiceHook {

    /** Battle ids that have already fired their last-Pokémon taunt (one taunt per battle). */
    private val taunted = ConcurrentHashMap.newKeySet<UUID>()

    fun registerEvents() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL) { event ->
            speak(event.battle, Trigger.INTRO)
        }
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL) { event ->
            val battle = event.battle
            val trainer = battle.actors.firstOrNull { it is AIBattleActor } ?: return@subscribe
            // Only when the TRAINER just lost a Pokémon (not the player's), and it left them with one.
            if (event.killed.actor !== trainer) return@subscribe
            val alive = trainer.pokemonList.count { it.health > 0 }
            if (alive == 1 && taunted.add(battle.battleId)) speak(battle, Trigger.TAUNT)
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            val battle = event.battle
            val trainerWon = event.winners.any { it is AIBattleActor }
            speak(battle, if (trainerWon) Trigger.VICTORY else Trigger.DEFEAT)
            taunted.remove(battle.battleId)
        }
    }

    private enum class Trigger { INTRO, TAUNT, VICTORY, DEFEAT }

    private fun speak(battle: PokemonBattle, trigger: Trigger) {
        val trainerId = RctBridge.trainerIdForBattle(battle.battleId) ?: return
        val voice = VoiceLines.get(trainerId) ?: return
        val lines = when (trigger) {
            Trigger.INTRO -> voice.intro
            Trigger.TAUNT -> voice.taunt
            Trigger.VICTORY -> voice.victory
            Trigger.DEFEAT -> voice.defeat
        }
        if (lines.isEmpty()) return
        val message = format(voice.name ?: trainerId, voice.color, lines.random())
        // Per-player: only the human battlers see it.
        battle.actors.filterIsInstance<PlayerBattleActor>()
            .mapNotNull { it.entity as? ServerPlayer }
            .forEach { it.sendSystemMessage(message) }
    }

    private fun format(name: String, colorName: String?, line: String): Component {
        val color = colorName?.let { ChatFormatting.getByName(it.lowercase()) }
        val namePart = if (color != null) Component.literal(name).withStyle(color, ChatFormatting.BOLD)
                       else Component.literal(name).withStyle(ChatFormatting.BOLD)
        return namePart
            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("“$line”").withStyle(ChatFormatting.WHITE))
    }
}
