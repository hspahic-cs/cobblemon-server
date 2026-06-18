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
            val ok = TowerGauntletHook.mayFightFloor(player.uuid, floor)
            if (ok) BattleThemeHook.stashGymTheme(player.uuid)  // tower fights use the gym battle pool
            return ok
        }
        val flatCap = BridgeTags.findLevelCap(trainer.tags)
        val gymId = BridgeTags.findGymId(trainer.tags)
        if (gymId == null) {
            // Standalone flat-cap gym (pe AI-test) with no progression id — just cap and allow.
            // (Right-click also stashes via GymBattleAdjustHook.onEntityInteract; this covers any
            // force-battle path. Idempotent — same player, same cap.)
            flatCap?.let {
                GymBattleAdjustHook.stashCap(player.uuid, it)
                BattleThemeHook.stashGymTheme(player.uuid)  // flat-cap test gym → gym battle pool
            }
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
            if (!passCooldown(player, gymId)) return false
            GymBattleAdjustHook.stashCap(player.uuid, cap)
            BattleThemeHook.stashTrainerTheme(player.uuid, gymId)
            return true
        }

        if (gymId in 21..24) {
            if (!E4GauntletHook.canChallenge(player, gymId)) {
                player.sendSystemMessage(Component.literal(E4GauntletHook.lockedReason(gymId)))
                CobblemonBridge.logger.debug(
                    "GymBattleGate: blocked {} from gym {} via E4 gauntlet",
                    player.gameProfile.name, gymId,
                )
                return false
            }
            if (!passCooldown(player, gymId)) return false
            E4GauntletHook.stashActive(player.uuid, gymId)
            GymBattleAdjustHook.stashCap(player.uuid, cap)
            BattleThemeHook.stashTrainerTheme(player.uuid, gymId)  // per-member E4 theme
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
        if (!passCooldown(player, gymId)) return false
        GymBattleAdjustHook.stashCap(player.uuid, cap)
        BattleThemeHook.stashTrainerTheme(player.uuid, gymId)
        return true
    }

    /** Gym battle anti-spam: blocks a gym re-challenge within [GymCooldown.COOLDOWN_TICKS] of the
     *  last attempt. Applies to every gym including the E4 gauntlet members (20-24) — the cooldown
     *  is per-(player, gym), so it never blocks normal gauntlet progression (20→21→…, each a
     *  different gym fought once), only re-fighting the *same* member within 2 minutes. The
     *  trade-off: after losing to an E4 member you wait out the cooldown before retrying them.
     *  Records on pass. */
    private fun passCooldown(player: ServerPlayer, gymId: Int): Boolean {
        val now = player.serverLevel().gameTime
        val rem = GymCooldown.remainingTicks(player.uuid, gymId, now)
        if (rem > 0) {
            player.sendSystemMessage(Component.literal(
                "§c[Gym $gymId] §fOn cooldown — wait §e${(rem + 19) / 20}s§f before challenging this " +
                "gym again. §7(2-minute anti-EXP-farm cooldown.)"
            ))
            return false
        }
        GymCooldown.record(player.uuid, gymId, now)
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
