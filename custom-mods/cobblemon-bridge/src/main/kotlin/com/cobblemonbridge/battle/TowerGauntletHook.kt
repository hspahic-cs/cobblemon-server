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
import com.cobblemonbridge.tower.TowerManager
import net.minecraft.core.registries.BuiltInRegistries
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
 * Daily battle-tower gauntlet — three challenge-mode leaders ([TowerManager.leadersForDay])
 * fought in floor order 1→2→3. Clearing all three in one run WITHOUT using a healing
 * machine grants one rare key per player per day.
 *
 * Rules (player-facing):
 *   - Floors strictly in order; floor N+1's leader refuses the interact until floor N falls.
 *   - Party locked for the run ([partySnapshot], same mechanism as [E4GauntletHook]) — a PC
 *     swap is a free heal, so any roster change fails the run.
 *   - Items ARE allowed mid-battle; only the healing machine is banned. Using one mid-run
 *     doesn't stop the fights — it just voids the key ([voided]).
 *   - Losing / fleeing / disconnecting resets the run. Unlimited retries until the
 *     midnight rotation; the key itself stays once-per-day ([TowerStore.clearedEpochDay]).
 *
 * Differences from the E4 gauntlet, deliberate:
 *   - No dimension leash — the tower is at spawn; walking out to restock items is fine
 *     (the party lock still blocks PC healing while away).
 *   - Loss resets the run but not the day — E4 has no daily dimension.
 *
 * Tower wins ALSO fire the trainer's `beat_gym_N_challenge` advancement via RCT's
 * defeat_count (shared trainer ids — design call 2026-06-05), so a player's first-ever win
 * against a given challenge leader pays that one-time rare key + cash on top of the
 * tower's daily key. Expected, not a bug.
 */
object TowerGauntletHook {

    private const val LAST_FLOOR = 3
    private const val PREFIX = "§d§l[Battle Tower] §f"
    private val HEALING_MACHINE_ID = ResourceLocation.fromNamespaceAndPath("cobblemon", "healing_machine")

    /** Next floor the player must fight. Present = run active. */
    private val nextFloor: MutableMap<UUID, Int> = ConcurrentHashMap()
    /** Floor currently being fought — set on interact, consumed on battle end. */
    private val activeFloor: MutableMap<UUID, Int> = ConcurrentHashMap()
    /** Party (Pokémon UUIDs) at run start. Any mismatch at a tower battle start fails the run. */
    private val partySnapshot: MutableMap<UUID, Set<UUID>> = ConcurrentHashMap()
    /** Players who used a healing machine mid-run — run continues, key is voided. */
    private val voided: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun registerEvents() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event -> onVictory(event) }
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event -> onFled(event) }
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGH) { event -> onBattleStarted(event) }
    }

    /** True while the player is mid-tower-battle. Read by [GymReturnHook] (at HIGH priority,
     *  before our NORMAL victory handler consumes the entry) to tell tower fights apart from
     *  gym-area fights against the same trainer ids. */
    fun isFightingTower(uuid: UUID): Boolean = activeFloor.containsKey(uuid)

    /** Rotation swapped the NPCs — every in-flight run is void. Called by [TowerManager.rotate]. */
    fun resetAllRuns() {
        nextFloor.clear()
        activeFloor.clear()
        partySnapshot.clear()
        voided.clear()
    }

    private fun snapshotPartyUuids(player: ServerPlayer): Set<UUID> {
        val party = Cobblemon.storage.getParty(player)
        return (0 until party.size()).mapNotNull { party.get(it)?.uuid }.toSet()
    }

    private fun clearRun(uuid: UUID) {
        nextFloor.remove(uuid)
        activeFloor.remove(uuid)
        partySnapshot.remove(uuid)
        voided.remove(uuid)
    }

    private fun clearedToday(uuid: UUID): Boolean =
        TowerManager.store().clearedEpochDay(uuid) == TowerManager.todayEpochDay()

    // ─── Interact gate: floors in order, once-per-day ──────────────────────
    @SubscribeEvent(priority = EventPriority.LOW)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide || event.isCanceled) return
        val player = event.entity as? ServerPlayer ?: return
        val floor = BridgeTags.findTowerFloor(event.target.tags) ?: return

        if (clearedToday(player.uuid)) {
            event.isCanceled = true
            player.sendSystemMessage(Component.literal(
                "${PREFIX}Already cleared today — new leaders arrive at midnight."
            ))
            return
        }

        val expected = nextFloor[player.uuid]
        when {
            // No active run: floor 1 starts one, anything else bounces.
            expected == null && floor == 1 -> startRun(player)
            expected == null -> {
                event.isCanceled = true
                player.sendSystemMessage(Component.literal(
                    "${PREFIX}Start at §efloor 1§f — the tower is fought bottom-up."
                ))
                return
            }
            // Active run: only the next floor's leader accepts.
            floor != expected -> {
                event.isCanceled = true
                val msg = if (floor < expected) "You already beat floor $floor — head up to §efloor $expected§f."
                          else "Beat §efloor $expected§f first."
                player.sendSystemMessage(Component.literal(PREFIX + msg))
                return
            }
        }
        activeFloor[player.uuid] = floor
    }

    private fun startRun(player: ServerPlayer) {
        nextFloor[player.uuid] = 1
        partySnapshot[player.uuid] = snapshotPartyUuids(player)
        voided.remove(player.uuid)
        player.sendSystemMessage(Component.literal(
            "${PREFIX}Run started! Beat all §e3 floors§f in order §7— party locked, items allowed, " +
            "§cno healing machine§7 — §ffor a §eRare Key§f. Losing just resets the run."
        ))
        CobblemonBridge.logger.info(
            "tower: run started for {} (party size={})",
            player.gameProfile.name, partySnapshot[player.uuid]?.size,
        )
    }

    // ─── Healing machine voids the key ─────────────────────────────────────
    /** HIGHEST priority for the same reason as [com.cobblemonbridge.quests.HealQuestHook]:
     *  Cobblemon's own healing-machine handler SUCCEEDs (cancels) the event at NORMAL. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onUseBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        if (!nextFloor.containsKey(player.uuid)) return  // no active run
        val blockId = BuiltInRegistries.BLOCK.getKey(event.level.getBlockState(event.pos).block)
        if (blockId != HEALING_MACHINE_ID) return
        if (voided.add(player.uuid)) {
            player.sendSystemMessage(Component.literal(
                "${PREFIX}§cHealing machine used — today's key is voided. §7You can still finish the run for practice, or wait for tomorrow's leaders."
            ))
            CobblemonBridge.logger.info("tower: key voided for {} (healing machine)", player.gameProfile.name)
        }
    }

    // ─── Party stability at battle start ───────────────────────────────────
    private fun onBattleStarted(event: BattleStartedEvent.Pre) {
        for (actor in event.battle.actors) {
            val player = (actor as? PlayerBattleActor)?.entity as? ServerPlayer ?: continue
            if (!activeFloor.containsKey(player.uuid)) continue
            val snapshot = partySnapshot[player.uuid] ?: continue
            if (snapshotPartyUuids(player) != snapshot) {
                event.cancel()
                failRun(player, "party changed mid-run — Pokémon must stay the same for the whole tower")
                return
            }
        }
    }

    // ─── Battle outcomes ───────────────────────────────────────────────────
    private fun onVictory(event: BattleVictoryEvent) {
        for (actor in event.winners) {
            val player = (actor as? PlayerBattleActor)?.entity as? ServerPlayer ?: continue
            val floor = activeFloor.remove(player.uuid) ?: continue
            if (floor < LAST_FLOOR) {
                nextFloor[player.uuid] = floor + 1
                player.sendSystemMessage(Component.literal(
                    "${PREFIX}Floor $floor cleared! Next: §efloor ${floor + 1}§f."
                ))
            } else {
                completeRun(player)
            }
        }
        for (actor in event.losers) {
            val player = (actor as? PlayerBattleActor)?.entity as? ServerPlayer ?: continue
            activeFloor.remove(player.uuid) ?: continue
            failRun(player, "lost")
        }
    }

    private fun onFled(event: BattleFledEvent) {
        val player = event.player.entity as? ServerPlayer ?: return
        activeFloor.remove(player.uuid) ?: return
        failRun(player, "fled")
    }

    /** Run ended (clear or loss) → warp back to the tower's return spot (or floor 1). */
    private fun teleportOut(player: ServerPlayer) {
        TowerManager.store().returnPos()?.let {
            com.cobblemonbridge.util.DelayedTeleports.schedule(player, it)
        }
    }

    private fun completeRun(player: ServerPlayer) {
        val wasVoided = player.uuid in voided
        clearRun(player.uuid)
        teleportOut(player)
        if (wasVoided) {
            player.sendSystemMessage(Component.literal(
                "${PREFIX}Tower cleared — but the healing machine voided today's key. Come back tomorrow!"
            ))
            return
        }
        if (clearedToday(player.uuid)) return  // belt-and-braces; interact gate already blocks
        TowerManager.store().markCleared(player.uuid, TowerManager.todayEpochDay())
        val src = player.createCommandSourceStack().withPermission(4).withSuppressedOutput()
        player.server.commands.performPrefixedCommand(src, "gacha grant ${player.gameProfile.name} rare 1")
        player.sendSystemMessage(Component.literal(
            "${PREFIX}§6§lTower cleared! §e§l✦ Rare Key §fawarded. New leaders at midnight."
        ))
        CobblemonBridge.logger.info("tower: cleared by {} — rare key granted", player.gameProfile.name)
    }

    private fun failRun(player: ServerPlayer, reason: String) {
        val hadRun = nextFloor.containsKey(player.uuid)
        clearRun(player.uuid)
        if (hadRun) {
            teleportOut(player)
            player.sendSystemMessage(Component.literal(
                "${PREFIX}Run over ($reason). §7Back to the bottom — start again any time before midnight."
            ))
            CobblemonBridge.logger.info("tower: run {} for {}", reason, player.gameProfile.name)
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        clearRun(event.entity.uuid)
    }
}
