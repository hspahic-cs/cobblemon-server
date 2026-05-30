package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.economy.EconomyBridge
import com.cobblemonbridge.quests.QuestAdvancements
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridge a `cobblemon_bridge.gym_id.<N>` tag on an NPC to `server:beat_gym_<N>` advancement
 * award on the player who beats them in battle.
 *
 * Two ways the stash gets populated, both consumed by [applyToVictory]:
 *
 *  1. **[onEntityInteract]** — player right-clicked the trainer. Precise; this is the primary
 *     path for the host who placed the NPC and is fighting them deliberately.
 *  2. **[stashFromBattleStart]** at `BATTLE_STARTED_PRE` — fallback for RCT's line-of-sight
 *     auto-engagement (the trainer challenges the player by walking into view). No right-click
 *     happens in that case, so the interact stash is empty. We scan entities within
 *     [PROXIMITY_RADIUS] of each player actor at battle start, pick the nearest one carrying
 *     a `cobblemon_bridge.gym_id.*` tag, and stash that. Bug uncovered when a second
 *     playtester engaged Clay via LOS and never got the achievement.
 *
 * Entries auto-expire after [STASH_TTL_MS] in case the battle drags out or never resolves.
 * Lost/fled battles never reach BATTLE_VICTORY for the player as winner, so a stale stash
 * naturally times out.
 */
object GymDefeatHook {

    private const val STASH_TTL_MS: Long = 5 * 60 * 1000L  // 5 minutes — gym fights can drag

    /** Radius around the player to scan for a tagged gym NPC when the EntityInteract stash is
     *  empty. RCT line-of-sight engagement walks the trainer adjacent to the player, so a
     *  small radius is enough; the larger it is, the higher the chance of picking the wrong
     *  trainer if two are in the same area. */
    private const val PROXIMITY_RADIUS: Double = 8.0

    private data class Pending(val gymId: Int, val isChallenge: Boolean, val capturedAtMs: Long)

    /** playerUuid → pending stash from EntityInteract or BATTLE_STARTED_PRE proximity. */
    private val pendingByPlayer: MutableMap<UUID, Pending> = ConcurrentHashMap()

    fun registerEvents() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL) { event ->
            stashFromBattleStart(event.battle)
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            applyToVictory(event)
        }
    }

    /**
     * Fallback for RCT line-of-sight engagement. Runs when the battle is being constructed —
     * for each player actor on a side with a TrainerBattleActor opposite, if the EntityInteract
     * stash is empty, scan nearby entities for the nearest one carrying a
     * `cobblemon_bridge.gym_id.*` tag and stash it. Doesn't overwrite an existing stash (the
     * EntityInteract path is more specific).
     */
    internal fun stashFromBattleStart(battle: PokemonBattle) {
        val now = System.currentTimeMillis()
        val allActors = battle.sides.flatMap { it.actors.toList() }
        val hasTrainer = allActors.any { it is TrainerBattleActor }
        if (!hasTrainer) return  // wild battle, nothing to detect
        for (actor in allActors) {
            val playerActor = actor as? PlayerBattleActor ?: continue
            val player = playerActor.entity as? ServerPlayer ?: continue
            if (pendingByPlayer.containsKey(player.uuid)) continue  // interact already stashed
            val nearby = findNearbyGymTrainer(player) ?: continue
            pendingByPlayer[player.uuid] = Pending(nearby.first, nearby.second, now)
            CobblemonBridge.logger.debug(
                "cobblemon-bridge: stashed gym_id={}{} for {} via proximity fallback",
                nearby.first, if (nearby.second) " (challenge)" else "", player.gameProfile.name,
            )
        }
    }

    private fun findNearbyGymTrainer(player: ServerPlayer): Pair<Int, Boolean>? {
        val box = player.boundingBox.inflate(PROXIMITY_RADIUS)
        val candidates = player.level().getEntitiesOfClass(Entity::class.java, box) { e ->
            BridgeTags.findGymId(e.tags) != null
        }
        val best = candidates.minByOrNull { it.distanceToSqr(player) } ?: return null
        val gymId = BridgeTags.findGymId(best.tags) ?: return null
        return gymId to BridgeTags.isGymChallenge(best.tags)
    }

    @SubscribeEvent
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val gymId = BridgeTags.findGymId(event.target.tags) ?: return
        val isChallenge = BridgeTags.isGymChallenge(event.target.tags)
        pendingByPlayer[player.uuid] = Pending(gymId, isChallenge, System.currentTimeMillis())
        CobblemonBridge.logger.debug(
            "cobblemon-bridge: stashed gym_id={}{} for player {}",
            gymId, if (isChallenge) " (challenge)" else "", player.uuid,
        )
    }

    private fun applyToVictory(event: BattleVictoryEvent) {
        val now = System.currentTimeMillis()
        val losersIncludeTrainer = event.losers.any { it is TrainerBattleActor }

        // Primary: ask RCT directly which trainer was fighting in this battle. Deterministic —
        // doesn't depend on stash population or proximity. Returns null if RCT isn't loaded /
        // the battle wasn't a trainer battle / lookup fails, in which case we fall through to
        // the legacy stash + proximity paths below as defensive fallbacks.
        val rctMatch = com.cobblemonbridge.adapters.RctBridge
            .trainerIdForBattle(event.battle.battleId)
            ?.let(com.cobblemonbridge.adapters.RctBridge::parseGymTrainerId)

        for (winner in event.winners) {
            val playerActor = winner as? PlayerBattleActor ?: continue
            val player = playerActor.entity as? ServerPlayer ?: continue

            // Branch 0 (preferred): RCT lookup told us the exact gym.
            if (rctMatch != null) {
                val (gymId, isChallenge) = rctMatch
                pendingByPlayer.remove(player.uuid)  // consume any stale stash
                val advancementId = if (isChallenge) "server:beat_gym_${gymId}_challenge"
                                    else "server:beat_gym_$gymId"
                val awarded = QuestAdvancements.award(player, advancementId, criterion = "done")
                if (awarded) {
                    CobblemonBridge.logger.info(
                        "cobblemon-bridge: awarded {} to {} (rct-direct)",
                        advancementId, player.gameProfile.name,
                    )
                    payGymBounty(player, gymId, isChallenge)
                }
                continue
            }

            // Branch 1 (fallback): gym defeat via stashed gym_id tag from EntityInteract or
            // the BATTLE_STARTED_PRE proximity scan. Kept as defense-in-depth in case RCT
            // changes its API or isn't loaded.
            val pending = pendingByPlayer.remove(player.uuid)
            if (pending != null && now - pending.capturedAtMs <= STASH_TTL_MS) {
                val advancementId = if (pending.isChallenge) {
                    "server:beat_gym_${pending.gymId}_challenge"
                } else {
                    "server:beat_gym_${pending.gymId}"
                }
                val awarded = QuestAdvancements.award(player, advancementId, criterion = "done")
                if (awarded) {
                    CobblemonBridge.logger.info(
                        "cobblemon-bridge: awarded {} to {} (stash fallback)",
                        advancementId, player.gameProfile.name,
                    )
                    payGymBounty(player, pending.gymId, pending.isChallenge)
                }
                continue
            }

            // Branch 2 (last resort): any other RCT/Cobblemon trainer NPC defeat fires the
            // wild-trainer quest. The advancement system makes this a one-time grant per player.
            if (losersIncludeTrainer) {
                val awarded = QuestAdvancements.award(player, "server:beat_wild_trainer", criterion = "done")
                if (awarded) {
                    CobblemonBridge.logger.info(
                        "cobblemon-bridge: awarded server:beat_wild_trainer to {}", player.gameProfile.name,
                    )
                }
            }
        }
    }

    /**
     * Income payout for a first-time gym defeat. Flat linear scaling per 0.7.8 user spec:
     * `$150 × gymId`. Gym 1 = $150, Gym 12 = $1,800, Gym 24 (Champion) = $3,600.
     *
     * Challenge (Hard Mode) variants match the base reward — silent on the user spec, so
     * defaulting to "rematch pays again" rather than zero. Bump to a separate table here if
     * Hard Mode should pay differently.
     *
     * Called only when [QuestAdvancements.award] returns true, so already gated to first-beat.
     * RCT trainers themselves are re-fightable (they reset after defeat) but the advancement
     * gate ensures the payout fires exactly once per player per gym.
     */
    internal fun gymBounty(gymId: Int, isChallenge: Boolean): Int =
        if (gymId in 1..24) 150 * gymId else 0

    private fun payGymBounty(player: ServerPlayer, gymId: Int, isChallenge: Boolean) {
        val amount = gymBounty(gymId, isChallenge)
        if (amount <= 0) return
        EconomyBridge.deposit(player.uuid, amount)
        player.sendSystemMessage(Component.literal(
            "§6§l+ §e§l\$$amount §6§lfor defeating gym ${gymId}${if (isChallenge) " (Hard Mode)" else ""}"
        ))
    }

    /** Test seam. */
    internal fun clearStashForTests() = pendingByPlayer.clear()
    internal fun stashedGymIdFor(uuid: UUID): Int? = pendingByPlayer[uuid]?.gymId
}
