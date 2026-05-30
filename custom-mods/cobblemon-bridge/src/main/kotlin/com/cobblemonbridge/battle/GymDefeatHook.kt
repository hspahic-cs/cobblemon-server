package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.economy.EconomyBridge
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Per-defeat NPC trainer bounty for non-gym trainer battles. Hook scope shrunk
 * dramatically in 0.7.26 — the gym advancement award + flat gym-bounty payment used to
 * live here too, but were migrated to the authoritative `rctmod:defeat_count` advancement
 * trigger + `/eco give @s <amount>` in each `beat_gym_*.mcfunction`. RCT fires
 * defeat_count itself on every trainer defeat with the exact trainer id, so we no longer
 * need our reflection / stash / proximity dance for gym credit.
 *
 * What remains here:
 *  - **Per-defeat NPC bounty** (formula: `max(1, ⌈multiplier × maxLevel × numPokemon / 6⌉)`,
 *    multiplier ∈ {1, 2, 3} uniform random per defeat — see [npcBounty]). Fires on every
 *    non-gym trainer defeat. Gym defeats are intentionally excluded because they already
 *    get the big flat `$150 × gymId` bounty from the mcfunction.
 *  - **Gym-detection** via [com.cobblemonbridge.adapters.RctBridge.trainerIdForBattle]
 *    — used solely to suppress the NPC bounty when the loser was actually a gym leader.
 *    If RCT reflection fails (unlikely after the 0.7.24 fix), the worst case is that
 *    gyms ALSO pay a small NPC bounty on top — a minor cosmetic issue vs. the prior
 *    "gym defeats granted no credit at all" bug.
 *
 * The `beat_wild_trainer` advancement is awarded directly by RCT's defeat_count trigger
 * (empty trainer_ids list = matches any trainer defeat including gyms — by design, it's
 * the "first ever trainer defeated" first-time grant).
 */
object GymDefeatHook {

    fun registerEvents() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            applyToVictory(event)
        }
    }

    private fun applyToVictory(event: BattleVictoryEvent) {
        // 0.7.30 diagnostic: log every BATTLE_VICTORY arrival unconditionally so we can prove
        // whether Cobblemon is delivering the event at all. After a Titan1190X test fight on
        // dev (one-shot KO of a non-gym RCT trainer routed through rbrctai), no `npc-defeat:`
        // log appeared and the player wasn't paid. AI-routing hypothesis ruled out — battle
        // had a normal winner but downstream side effects didn't fire. Possibilities:
        //   (a) Cobblemon's BATTLE_VICTORY isn't firing on this server at all
        //   (b) the event fires but our subscriber lambda was dropped (cold-start / classloader)
        //   (c) the event fires but a higher-priority subscriber threw and broke dispatch
        // This single line distinguishes (a) from (b)/(c). If absent after a confirmed win,
        // the next fix is a mixin on the battle-end path; if present, the bug is downstream
        // in the existing logic. Remove once root-caused.
        val winners = event.winners.joinToString(",") { (it as? PlayerBattleActor)?.entity?.gameProfile?.name ?: it::class.simpleName ?: "?" }
        val losers = event.losers.joinToString(",") { it::class.simpleName ?: "?" }
        CobblemonBridge.logger.info(
            "battle-victory-event: battleId={} winners=[{}] loser-kinds=[{}]",
            event.battle.battleId, winners, losers,
        )

        val losersIncludeTrainer = event.losers.any { it is TrainerBattleActor }
        if (!losersIncludeTrainer) return  // wild battle — nothing for us to do here

        // Gym-detection lives on so we DON'T double-pay: gym defeats get the big flat
        // gym bounty from the mcfunction; non-gym defeats get the per-defeat NPC bounty
        // from here.
        val trainerId = com.cobblemonbridge.adapters.RctBridge
            .trainerIdForBattle(event.battle.battleId)
        val isGymBattle = trainerId
            ?.let(com.cobblemonbridge.adapters.RctBridge::parseGymTrainerId) != null

        if (isGymBattle) return  // gym path handled entirely by RCT trigger + mcfunction

        for (winner in event.winners) {
            val playerActor = winner as? PlayerBattleActor ?: continue
            val player = playerActor.entity as? ServerPlayer ?: continue
            payNpcBounty(player, trainerId, event.losers)
        }
    }

    /**
     * Income payout for defeating a non-gym trainer NPC (random RCT trainers, etc.). Formula:
     *
     *   `bounty = max(1, ⌈multiplier × maxLevel × numPokemon / 6⌉)`,  multiplier ∈ {1, 2, 3} uniform
     *
     * Ceiling division + min-of-1 floor guarantee ≥ $1 for any positive-input defeat — fixes
     * the 0.7.24 silent-zero case where `(1 × 5 × 1) / 6 = 0` paid nothing.
     *
     * Pure-math seam at [computeNpcBounty] for unit-testing without mocking actors.
     */
    internal fun npcBounty(losers: Iterable<BattleActor>): Int {
        val trainer = losers.filterIsInstance<TrainerBattleActor>().firstOrNull() ?: return 0
        val pokemon = trainer.pokemonList
        if (pokemon.isEmpty()) return 0
        val maxLevel = pokemon.maxOf { it.effectedPokemon.level }
        val multiplier = (1..3).random()
        return computeNpcBounty(maxLevel, pokemon.size, multiplier)
    }

    /** Pure-math seam — same formula as [npcBounty] without Cobblemon battle types or
     *  randomness so the arithmetic is unit-testable. `multiplier` defaults to 2 (the
     *  mid-roll); pass 1 or 3 to verify the random branches. Returns 0 only when an input
     *  is non-positive; for any positive (level, count, multiplier) the result is ≥ 1. */
    internal fun computeNpcBounty(maxLevel: Int, numPokemon: Int, multiplier: Int = 2): Int {
        if (maxLevel <= 0 || numPokemon <= 0 || multiplier <= 0) return 0
        val numerator = multiplier * maxLevel * numPokemon
        return kotlin.math.max(1, (numerator + 5) / 6)
    }

    private fun payNpcBounty(player: ServerPlayer, trainerId: String?, losers: Iterable<BattleActor>) {
        val amount = npcBounty(losers)
        CobblemonBridge.logger.info(
            "npc-defeat: trainer={} player={} bounty=\${}",
            trainerId ?: "<unknown>", player.gameProfile.name, amount,
        )
        if (amount <= 0) return
        EconomyBridge.deposit(player.uuid, amount)
        player.sendSystemMessage(Component.literal(
            "§6+ §e\$$amount §7for defeating trainer"
        ))
    }
}
