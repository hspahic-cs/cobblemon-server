package com.cobblemonbridge.battle

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

/**
 * Single gate for gym battles, called from [com.cobblemonbridge.mixin.TrainerMobBattleMixin] at
 * the head of {@code TrainerMob.startBattleWith}. Covers BOTH ways a gym battle can begin:
 *   - Player right-clicks the trainer ({@code EntityInteract} → RCT's interact → startBattleWith)
 *   - Trainer wanders into LOS and force-battles the player (RCT AI → startBattleWith)
 *
 * The EntityInteract subscribers in [GymPrereqHook] / [GymBattleAdjustHook] / [E4GauntletHook]
 * are still useful — they fire earlier than the Mixin and avoid a brief "battle starting" flash
 * on right-click — but they miss the force-battle path entirely. This gate is the backstop that
 * covers what they don't.
 *
 * Responsibilities (in order):
 *   1. Read gym_id from the trainer's tags. No gym tag → not our problem, allow.
 *   2. Prereq check (challenge / linear / E4 gauntlet). Fail → message + return false.
 *   3. Stash gym_id for [GymBattleAdjustHook] (downlevel) and [E4GauntletHook] (gauntlet
 *      outcome tracking). Idempotent with the EntityInteract stashes — same player UUID,
 *      same gym_id, no harm in writing twice.
 *
 * Returns {@code true} if the battle should proceed, {@code false} if the Mixin should cancel.
 */
object GymBattleGate {

    fun beforeStartBattle(trainer: Entity, player: ServerPlayer): Boolean {
        // Tower NPCs (tower_floor tag, no gym_id): floor-ordered and force-battle-proof. A tower
        // battle may only begin at floor 1 or the player's current next floor — so a sethome /
        // teleport to a higher floor, or an on-sight force-battle, can't skip floor 1. Their L50
        // scaling rides a separate adjust_level.50 tag, so no cap is stashed here.
        BridgeTags.findTowerFloor(trainer.tags)?.let { floor ->
            return TowerGauntletHook.mayFightFloor(player.uuid, floor)
        }
        val flatCap = BridgeTags.findLevelCap(trainer.tags)
        val gymId = BridgeTags.findGymId(trainer.tags)
        if (gymId == null) {
            // Standalone flat-cap gym (pe AI-test) with no progression id — just cap and allow.
            // (Right-click also stashes via GymBattleAdjustHook.onEntityInteract; this covers any
            // force-battle path. Idempotent — same player, same cap.)
            flatCap?.let { GymBattleAdjustHook.stashCap(player.uuid, it) }
            return true
        }
        // A flat level_cap tag overrides the gym_id formula (challenge gyms carry level_cap.50 so
        // the player fights at the team's true L50), but must NOT bypass the prereq gates below.
        val cap = flatCap ?: GymBattleAdjustHook.capForGym(gymId)
        val isChallenge = BridgeTags.isGymChallenge(trainer.tags)

        if (isChallenge) {
            val mainId = ResourceLocation.fromNamespaceAndPath("server", "beat_gym_$gymId")
            val main = player.server.advancements.get(mainId)
            if (main != null && !player.advancements.getOrStartProgress(main).isDone) {
                player.sendSystemMessage(Component.literal(
                    "§c[Challenge Gym $gymId] §fLocked. Beat the regular §eGym $gymId§f first."
                ))
                CobblemonBridge.logger.debug(
                    "GymBattleGate: blocked {} from challenge gym {}",
                    player.gameProfile.name, gymId,
                )
                return false
            }
            GymBattleAdjustHook.stashCap(player.uuid, cap)
            return true
        }

        if (gymId in 21..23) {
            if (!E4GauntletHook.canChallenge(player, gymId)) {
                player.sendSystemMessage(Component.literal(E4GauntletHook.lockedReason(gymId)))
                CobblemonBridge.logger.debug(
                    "GymBattleGate: blocked {} from gym {} via E4 gauntlet",
                    player.gameProfile.name, gymId,
                )
                return false
            }
            E4GauntletHook.stashActive(player.uuid, gymId)
            GymBattleAdjustHook.stashCap(player.uuid, cap)
            return true
        }

        val prereqGym = prereqGymFor(gymId)
        if (prereqGym != null) {
            val prereqId = ResourceLocation.fromNamespaceAndPath("server", "beat_gym_$prereqGym")
            val prereq = player.server.advancements.get(prereqId)
            if (prereq != null && !player.advancements.getOrStartProgress(prereq).isDone) {
                player.sendSystemMessage(Component.literal(
                    "§c[Gym $gymId] §fLocked. Beat §eGym $prereqGym§f first."
                ))
                CobblemonBridge.logger.debug(
                    "GymBattleGate: blocked {} from gym {} — missing {}",
                    player.gameProfile.name, gymId, prereqId,
                )
                return false
            }
        }
        GymBattleAdjustHook.stashCap(player.uuid, cap)
        return true
    }

    private fun prereqGymFor(gymId: Int): Int? = when (gymId) {
        in 1..1 -> null
        in 2..10 -> gymId - 1
        in 11..23 -> 10
        24 -> 23
        else -> gymId - 1
    }
}
