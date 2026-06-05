package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.adapters.RctBridge
import com.cobblemonbridge.spawn.SpawnStore
import com.cobblemonbridge.util.DelayedTeleports
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLPaths

/**
 * Teleports the player to a configured return point after beating a gym leader (gyms 1-10,
 * mainline or challenge — design call 2026-06-05). Saves the walk back from the gym arena
 * after every win, not just the first (this keys off BATTLE_VICTORY + RCT trainer-id, not
 * the once-only advancement).
 *
 * Exclusions:
 *   - **Tower fights.** Tower leaders share trainer ids with the gym-area challenge NPCs,
 *     so trainer-id alone can't tell them apart — the discriminator is the player's tower
 *     run state. We subscribe at [Priority.HIGH] so the check runs BEFORE
 *     [TowerGauntletHook]'s NORMAL-priority handler consumes its activeFloor entry; the
 *     tower does its own run-end teleport.
 *   - **Gyms 11+.** Rotating gyms, E4 (whose gauntlet requires walking to the next leader),
 *     and the Champion keep their normal flow.
 *
 * Store reuses [SpawnStore] — it's a generic single-WarpPos JSON store despite the name;
 * this one lives at `config/cobblemon-bridge/runtime/gym-return.json`. Unset → no teleport
 * (the feature is opt-in via `/gymreturn set`).
 */
object GymReturnHook {

    private const val MAX_GYM = 10

    @Volatile
    private var store: SpawnStore? = null

    fun init() {
        val file = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve("gym-return.json")
        store = SpawnStore.load(file)
        CobblemonBridge.logger.info("gym-return: loaded ({})", store?.get()?.let { "set" } ?: "unset")
    }

    fun store(): SpawnStore = store
        ?: error("GymReturnHook not initialized — CobblemonBridge should call GymReturnHook.init()")

    fun registerEvents() {
        // HIGH so we observe TowerGauntletHook's activeFloor before its NORMAL handler clears it.
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.HIGH) { event -> onVictory(event) }
    }

    private fun onVictory(event: BattleVictoryEvent) {
        val target = store?.get() ?: return  // opt-in: nothing configured, nothing to do
        val trainerId = RctBridge.trainerIdForBattle(event.battle.battleId) ?: return
        val (gymId, _) = RctBridge.parseGymTrainerId(trainerId) ?: return
        if (gymId > MAX_GYM) return

        for (actor in event.winners) {
            val player = (actor as? PlayerBattleActor)?.entity as? ServerPlayer ?: continue
            if (TowerGauntletHook.isFightingTower(player.uuid)) continue  // tower handles its own
            DelayedTeleports.schedule(player, target)
            player.sendSystemMessage(Component.literal(
                "§6[Gym] §fVictory! Returning you to the gym lobby…"
            ))
        }
    }
}
