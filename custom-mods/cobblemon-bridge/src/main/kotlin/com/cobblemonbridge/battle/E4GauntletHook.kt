package com.cobblemonbridge.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Elite Four gauntlet — gyms 20-23 must be beaten consecutively in one session. Losing,
 * fleeing, disconnecting, leaving the E4 dimension, or swapping Pokémon mid-gauntlet all reset
 * progress; the player has to restart from E4-1 (gym 20).
 *
 * State:
 *   - [unlocked] — the next E4 gym the player is allowed to challenge. Set to N+1 after winning
 *     gym N (for N in 20..22). Cleared on loss / flee / disconnect / completion (after gym 23).
 *   - [active] — the gym the player is currently fighting. Stashed at [EntityInteract] for the
 *     gym leader and consumed on the battle's end event so we know which gym just ended.
 *   - [gauntletDimension] — the dimension the player was in when they entered the gauntlet.
 *     Changing dimension out of this one fails the gauntlet.
 *   - [partySnapshot] — the set of Pokémon UUIDs in the player's party at gauntlet entry. Each
 *     E4 battle start re-checks; any mismatch (PC swap, deposit, release) fails the gauntlet.
 *     Items and healing are unaffected — only the party roster is locked.
 *
 * Gating contract (used by [GymPrereqHook] for gyms 20-23):
 *   - gym 20: always allowed if base prereq met (beat_gym_10 done). [GymPrereqHook] checks that;
 *     this hook accepts every gym 20 attempt without further check.
 *   - gym 21-23: allowed only if [unlocked][uuid] == gymId. Otherwise the interact is cancelled
 *     with a chat message.
 *
 * Auto-chain (currently disabled):
 *   - On winning gym N (20..22), the player is told to challenge gym N+1 manually. The
 *     teleport-and-auto-start chain that used to fire here is parked until we revisit the
 *     gauntlet design.
 *   - On winning gym 23: clear gauntlet state and chat congrats (Champion at gym 24 is gated
 *     elsewhere on beat_gym_23, so the player walks to that one normally).
 */
object E4GauntletHook {

    private const val E4_FIRST: Int = 20
    private const val E4_LAST: Int = 23

    /** Next allowed gym in the gauntlet. Null = not in gauntlet (only gym 20 is fightable). */
    private val unlocked: MutableMap<UUID, Int> = ConcurrentHashMap()
    /** Gym currently being fought — set on [EntityInteract], cleared on battle end. */
    private val active: MutableMap<UUID, Int> = ConcurrentHashMap()
    /** Dimension the player was in when they started the gauntlet. Leaving = fail. */
    private val gauntletDimension: MutableMap<UUID, ResourceLocation> = ConcurrentHashMap()
    /** Pokémon UUIDs in the player's party at gauntlet entry. Mismatch at battle start = fail. */
    private val partySnapshot: MutableMap<UUID, Set<UUID>> = ConcurrentHashMap()

    fun registerEvents() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event -> onVictory(event) }
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event -> onFled(event) }
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGH) { event -> onBattleStarted(event) }
    }

    /** Called by [GymPrereqHook] for gyms 20-23. Returns true if the player may challenge. */
    fun canChallenge(player: ServerPlayer, gymId: Int): Boolean {
        if (gymId !in E4_FIRST..E4_LAST) return true
        if (gymId == E4_FIRST) return true  // entry into the gauntlet
        return unlocked[player.uuid] == gymId
    }

    /** Reason text shown to the player when [canChallenge] returns false. */
    fun lockedReason(gymId: Int): String = when {
        gymId == E4_FIRST -> ""  // gym 20 is never locked by us
        else -> "§c[Elite Four] §fYou must beat E4 ${gymId - E4_FIRST}§f first — start at §eE4 1 (Gym ${E4_FIRST})§f."
    }

    /** External entry — used by [GymBattleGate] to mark a player as currently fighting [gymId]
     *  when the battle starts via the force-battle path (which skips EntityInteract). */
    fun stashActive(uuid: java.util.UUID, gymId: Int) {
        if (gymId !in E4_FIRST..E4_LAST) return
        active[uuid] = gymId
        if (gymId == E4_FIRST && !unlocked.containsKey(uuid)) {
            // Force-battle path can't capture dimension/party snapshot itself — server-driven entry.
            // We mark unlocked here so canChallenge gating works, but dimension/party leashes will
            // only engage if the player started the gauntlet via the normal EntityInteract path.
            unlocked[uuid] = E4_FIRST
        }
    }

    /** Snapshot the player's current party (by Pokémon UUID) for stability enforcement. */
    private fun snapshotPartyUuids(player: ServerPlayer): Set<UUID> {
        val party = Cobblemon.storage.getParty(player)
        return (0 until party.size()).mapNotNull { party.get(it)?.uuid }.toSet()
    }

    /** Called when a player enters the gauntlet (first E4 1 interact). Captures dimension +
     *  party snapshot and tells the player the rules. */
    private fun startGauntlet(player: ServerPlayer) {
        val uuid = player.uuid
        if (gauntletDimension.containsKey(uuid)) return  // already started — no-op
        unlocked[uuid] = E4_FIRST
        gauntletDimension[uuid] = player.level().dimension().location()
        partySnapshot[uuid] = snapshotPartyUuids(player)
        player.sendSystemMessage(Component.literal(
            "§6§l[Elite Four] §fGauntlet started. §7Your party is locked for the duration. " +
            "Leaving the dimension, losing, or fleeing resets your progress to E4 1."
        ))
        CobblemonBridge.logger.info(
            "E4 gauntlet started for {} (dim={}, party size={})",
            player.gameProfile.name,
            gauntletDimension[uuid],
            partySnapshot[uuid]?.size,
        )
    }

    private fun cleanupGauntletState(uuid: UUID) {
        unlocked.remove(uuid)
        active.remove(uuid)
        gauntletDimension.remove(uuid)
        partySnapshot.remove(uuid)
    }

    // ─── Stash on interact ─────────────────────────────────────────────────
    @SubscribeEvent(priority = EventPriority.LOW)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide || event.isCanceled) return
        val player = event.entity as? ServerPlayer ?: return
        val gymId = BridgeTags.findGymId(event.target.tags) ?: return
        if (gymId !in E4_FIRST..E4_LAST) return
        active[player.uuid] = gymId
        // Stamp into the gauntlet on the first E4 interact — captures dimension + party snapshot.
        if (gymId == E4_FIRST) {
            startGauntlet(player)
        }
    }

    // ─── Battle outcomes ───────────────────────────────────────────────────
    private fun onVictory(event: BattleVictoryEvent) {
        // Winners — player wins an E4 fight
        for (actor in event.winners) {
            val player = (actor as? PlayerBattleActor)?.entity as? ServerPlayer ?: continue
            val gym = active.remove(player.uuid) ?: continue
            if (gym !in E4_FIRST..E4_LAST) continue
            if (gym < E4_LAST) {
                val next = gym + 1
                unlocked[player.uuid] = next
                // Auto-teleport + auto-battle disabled. Player walks to the next trainer manually
                // and right-clicks them to continue; the gating ([canChallenge]) still enforces
                // order, and a loss/flee still resets the gauntlet.
                player.sendSystemMessage(Component.literal(
                    "§6[Elite Four] §fNext: §eE4 ${next - E4_FIRST + 1} (Gym $next)§f — challenge them next."
                ))
            } else {
                // Won gym 23 — gauntlet complete. Clear dimension/party leashes too so the
                // player can leave the area and re-shape their party for the Champion fight.
                cleanupGauntletState(player.uuid)
                player.sendSystemMessage(Component.literal(
                    "§6§l[Elite Four] §fGauntlet complete! §7The Champion (Gym 24) is now open."
                ))
            }
        }
        // Losers — player loses an E4 fight
        for (actor in event.losers) {
            val player = (actor as? PlayerBattleActor)?.entity as? ServerPlayer ?: continue
            val gym = active.remove(player.uuid) ?: continue
            if (gym !in E4_FIRST..E4_LAST) continue
            failGauntlet(player, "lost")
        }
    }

    private fun onFled(event: BattleFledEvent) {
        val player = event.player.entity as? ServerPlayer ?: return
        val gym = active.remove(player.uuid) ?: return
        if (gym !in E4_FIRST..E4_LAST) return
        failGauntlet(player, "fled")
    }

    private fun failGauntlet(player: ServerPlayer, reason: String) {
        val hadState = unlocked.containsKey(player.uuid)
        cleanupGauntletState(player.uuid)
        if (hadState) {
            player.sendSystemMessage(Component.literal(
                "§c[Elite Four] §fGauntlet failed ($reason). Restart from §eE4 1 (Gym ${E4_FIRST})§f."
            ))
            CobblemonBridge.logger.info(
                "E4 gauntlet ${reason} for {} — state cleared", player.gameProfile.name,
            )
        }
    }

    // ─── Dimension leash ───────────────────────────────────────────────────
    /** Leaving the E4 dimension by any means (portal, /tp, /home, /spawn) fails the gauntlet. */
    @SubscribeEvent
    fun onChangeDimension(event: PlayerEvent.PlayerChangedDimensionEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val expected = gauntletDimension[player.uuid] ?: return
        if (event.to.location() == expected) return
        failGauntlet(player, "left the Elite Four area")
    }

    // ─── Party stability at battle start ───────────────────────────────────
    /** Any E4 battle start re-checks the player's party against the snapshot. PC swaps,
     *  deposits, and releases all rotate UUIDs; this catches them before the battle starts. */
    private fun onBattleStarted(event: BattleStartedEvent.Pre) {
        for (actor in event.battle.actors) {
            val player = (actor as? PlayerBattleActor)?.entity as? ServerPlayer ?: continue
            val snapshot = partySnapshot[player.uuid] ?: continue
            val current = snapshotPartyUuids(player)
            if (current != snapshot) {
                event.cancel()
                failGauntlet(
                    player,
                    "party changed mid-gauntlet — Pokémon must stay the same throughout the Elite Four",
                )
                return
            }
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val uuid = event.entity.uuid
        val hadUnlocked = unlocked.containsKey(uuid)
        cleanupGauntletState(uuid)
        if (hadUnlocked) {
            CobblemonBridge.logger.info(
                "E4 gauntlet cleared for {} on disconnect", event.entity.gameProfile.name,
            )
        }
    }

}
