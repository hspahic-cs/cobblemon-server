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
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
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
 *   - Floors strictly in order. Both the interact gate AND the battle-start gate
 *     ([com.cobblemonbridge.battle.GymBattleGate] via [mayFightFloor]) refuse any floor that
 *     isn't the player's current one — so a sethome/teleport to a higher floor, or an on-sight
 *     force-battle, can't skip floor 1.
 *   - Party locked for the run ([partySnapshot], same mechanism as [E4GauntletHook]) — a PC
 *     swap is a free heal, so any roster change fails the run.
 *   - Items ARE allowed mid-battle, but using a healing machine mid-run RESETS the run (back to
 *     floor 1), same as a loss — no cheap full heal between floors.
 *   - Losing / fleeing / disconnecting / healing resets the run. Unlimited retries until the
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

    /** Player-NBT key holding a resumable snapshot of an in-flight tower run (next floor +
     *  difficulty + party + the epoch-day it started on), so a disconnect mid-run resumes on
     *  reconnect — unless the daily rotation has elapsed, which voids it. */
    private const val RUN_NBT_KEY = "cobblemon_bridge_tower_run"

    /** The tower is gated on beating mainline gym 10 (same advancement that unlocks gyms 11-23
     *  in [GymPrereqHook]). Checked both here (floor-1 interact, defence-in-depth) and at the
     *  entry NPC ([com.cobblemonbridge.battle.TowerEntryHook]). */
    private val GATE_ADVANCEMENT = ResourceLocation.fromNamespaceAndPath("server", "beat_gym_10")
    private val GATE_LOCKED_MSG = "${PREFIX}Locked — clear the §e10th Gym§f first to enter the Battle Tower."

    /** Next floor the player must fight. Present = run active. */
    private val nextFloor: MutableMap<UUID, Int> = ConcurrentHashMap()
    /** Floor currently being fought — set on interact, consumed on battle end. */
    private val activeFloor: MutableMap<UUID, Int> = ConcurrentHashMap()
    /** Party (Pokémon UUIDs) at run start. Any mismatch at a tower battle start fails the run. */
    private val partySnapshot: MutableMap<UUID, Set<UUID>> = ConcurrentHashMap()
    /** Difficulty the run is locked to ("hard"/"normal"), set when the player engages a floor-1
     *  leader. Every later floor must be the SAME difficulty — no mixing. */
    private val runDifficulty: MutableMap<UUID, String> = ConcurrentHashMap()

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
        runDifficulty.clear()
    }

    /**
     * Force-battle-proof floor gate, called from [com.cobblemonbridge.battle.GymBattleGate] at
     * the `startBattleWith` choke point (covers BOTH right-click and on-sight force-battle). A
     * tower battle may only begin at floor 1 (which starts a run via the interact gate) or at the
     * player's current [nextFloor]. Anything else — a teleport to a higher floor, a force-battle
     * from a leader who locked eyes — is refused, so floor 1 can never be skipped. Stateless wrt
     * event ordering: it reads [nextFloor], which is advanced by [onVictory], not by the interact
     * that triggers this battle.
     */
    fun mayFightFloor(uuid: UUID, floor: Int): Boolean {
        if (clearedToday(uuid)) return false
        val expected = nextFloor[uuid]
        return if (expected == null) floor == 1 else floor == expected
    }

    private fun snapshotPartyUuids(player: ServerPlayer): Set<UUID> {
        val party = Cobblemon.storage.getParty(player)
        return (0 until party.size()).mapNotNull { party.get(it)?.uuid }.toSet()
    }

    private fun clearRun(uuid: UUID) {
        nextFloor.remove(uuid)
        activeFloor.remove(uuid)
        partySnapshot.remove(uuid)
        runDifficulty.remove(uuid)
    }

    private fun clearedToday(uuid: UUID): Boolean =
        TowerManager.store().clearedEpochDay(uuid) == TowerManager.todayEpochDay()

    /** True once the player has beaten mainline gym 10 — the tower entry gate. */
    fun hasClearedGate(player: ServerPlayer): Boolean {
        val adv = player.server.advancements.get(GATE_ADVANCEMENT) ?: return false
        return player.advancements.getOrStartProgress(adv).isDone
    }

    /** Entry-NPC path ([TowerEntryHook]): gate-check, warp to floor 1, arm the run. Messages the
     *  player and returns false if the gate isn't cleared. The single public way in from the NPC,
     *  so all run state stays owned here. */
    fun startFromEntry(player: ServerPlayer): Boolean {
        if (clearedToday(player.uuid)) {
            player.sendSystemMessage(Component.literal(
                "${PREFIX}Already cleared today — new leaders arrive at midnight."
            ))
            return false
        }
        if (!hasClearedGate(player)) {
            player.sendSystemMessage(Component.literal(GATE_LOCKED_MSG))
            return false
        }
        // Warp to floor 1 (both leaders stand there); the difficulty is chosen by which one the
        // player engages. beginRun arms the gauntlet without locking a difficulty yet.
        teleportToFloor(player, 1, null)
        beginRun(player)
        return true
    }

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

        val difficulty = BridgeTags.findTowerDifficulty(event.target.tags) ?: BridgeTags.DIFFICULTY_HARD

        var expected = nextFloor[player.uuid]
        if (expected == null) {
            // No active run — only a floor-1 leader starts one, past the beat_gym_10 gate.
            if (floor != 1) {
                event.isCanceled = true
                player.sendSystemMessage(Component.literal(
                    "${PREFIX}Start at §efloor 1§f — the tower is fought bottom-up."
                ))
                return
            }
            if (!hasClearedGate(player)) {
                event.isCanceled = true
                player.sendSystemMessage(Component.literal(GATE_LOCKED_MSG))
                return
            }
            beginRun(player)
            expected = 1
        }

        // Lock the run to the difficulty of the FIRST leader engaged; every later floor must match
        // (no hard-then-normal cheesing). Difficulty is committed at floor 1.
        val locked = runDifficulty[player.uuid]
        if (locked == null) {
            runDifficulty[player.uuid] = difficulty
        } else if (difficulty != locked) {
            event.isCanceled = true
            val want = if (locked == BridgeTags.DIFFICULTY_HARD) "§cHard§f" else "§aNormal§f"
            player.sendSystemMessage(Component.literal(
                "${PREFIX}You're on the $want run — fight the $want leader on every floor (no switching mid-run)."
            ))
            return
        }

        // Only the next floor's leader accepts.
        if (floor != expected) {
            event.isCanceled = true
            val msg = if (floor < expected!!) "You already beat floor $floor — head up to §efloor $expected§f."
                      else "Beat §efloor $expected§f first."
            player.sendSystemMessage(Component.literal(PREFIX + msg))
            return
        }
        activeFloor[player.uuid] = floor
        persist(player)
    }

    /** Arm a fresh tower run: lock the party and set the next floor to 1. Idempotent re-arm is
     *  harmless. Called from the floor-1 interact gate and from [TowerEntryHook] after the warp.
     *  Callers are responsible for the [hasClearedGate] check. */
    fun beginRun(player: ServerPlayer) {
        nextFloor[player.uuid] = 1
        partySnapshot[player.uuid] = snapshotPartyUuids(player)
        player.sendSystemMessage(Component.literal(
            "${PREFIX}Run started! Beat all §e3 floors§f in order §7— party locked, items allowed, " +
            "§cno healing machine§7 §ffor a §eRare Key§f. Losing §7or healing§f resets the run."
        ))
        CobblemonBridge.logger.info(
            "tower: run started for {} (party size={})",
            player.gameProfile.name, partySnapshot[player.uuid]?.size,
        )
        persist(player)
    }

    // ─── Healing machine resets the run ────────────────────────────────────
    /** HIGHEST priority for the same reason as [com.cobblemonbridge.quests.HealQuestHook]:
     *  Cobblemon's own healing-machine handler SUCCEEDs (cancels) the event at NORMAL, so we must
     *  observe the click before it's swallowed. Healing mid-run RESETS the run — no cheap full
     *  heal between floors. The heal itself still goes through; the player is just back at floor 1. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onUseBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        if (!nextFloor.containsKey(player.uuid)) return  // no active run
        val blockId = BuiltInRegistries.BLOCK.getKey(event.level.getBlockState(event.pos).block)
        if (blockId != HEALING_MACHINE_ID) return
        failRun(player, "healing machine")
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
                persist(player)
                teleportToFloor(player, floor + 1, runDifficulty[player.uuid])
                player.sendSystemMessage(Component.literal(
                    "${PREFIX}Floor $floor cleared! Taking you up to §efloor ${floor + 1}§f…"
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

    /** The tower's floors are physically separate — teleports move the player through them.
     *  Win floor N → warp up to floor N+1's spot; lose/flee/heal → warp to the entry/start spot
     *  ([teleportToEntry]); clear floor 3 → warp to the return spot. The floor positions double as
     *  the NPC anchors, so arrival is right next to the leader. */
    private fun teleportToFloor(player: ServerPlayer, floor: Int, difficulty: String?) {
        val store = TowerManager.store()
        val diff = difficulty ?: BridgeTags.DIFFICULTY_HARD
        (store.floor(floor, diff) ?: store.floor(floor, BridgeTags.DIFFICULTY_HARD))?.let {
            com.cobblemonbridge.util.DelayedTeleports.schedule(player, it)
        }
    }

    /** Run complete → warp to the tower's return spot (or floor 1). */
    private fun teleportOut(player: ServerPlayer) {
        TowerManager.store().returnPos()?.let {
            com.cobblemonbridge.util.DelayedTeleports.schedule(player, it)
        }
    }

    /** Run failed → warp back to the entry/start spot (where the entry NPC is), not floor 1, so the
     *  player restarts the gauntlet from the bottom. Falls back to the return spot, then floor 1. */
    private fun teleportToEntry(player: ServerPlayer) {
        val store = TowerManager.store()
        (store.entryPos() ?: store.returnPos() ?: store.floor(1, BridgeTags.DIFFICULTY_HARD))?.let {
            com.cobblemonbridge.util.DelayedTeleports.schedule(player, it)
        }
    }

    private fun completeRun(player: ServerPlayer) {
        // Capture the run difficulty BEFORE clearing it — it picks the key tier.
        val hard = runDifficulty[player.uuid] != BridgeTags.DIFFICULTY_NORMAL
        clearRun(player.uuid)
        player.persistentData.remove(RUN_NBT_KEY)
        teleportOut(player)
        if (clearedToday(player.uuid)) return  // belt-and-braces; interact gate already blocks
        TowerManager.store().markCleared(player.uuid, TowerManager.todayEpochDay())
        val keyTier = if (hard) "rare" else "common"
        val src = player.createCommandSourceStack().withPermission(4).withSuppressedOutput()
        player.server.commands.performPrefixedCommand(src, "gacha grant ${player.gameProfile.name} $keyTier 1")
        val label = if (hard) "§6§l✦ Rare Key" else "§a§l✦ Common Key"
        val mode = if (hard) "§cHard" else "§aNormal"
        player.sendSystemMessage(Component.literal(
            "${PREFIX}§6§lTower cleared §r§7($mode§7)! $label §fawarded. No more tower keys today — new leaders at midnight."
        ))
        CobblemonBridge.logger.info(
            "tower: cleared by {} ({}) — {} key granted", player.gameProfile.name, if (hard) "hard" else "normal", keyTier,
        )
    }

    private fun failRun(player: ServerPlayer, reason: String) {
        val hadRun = nextFloor.containsKey(player.uuid)
        clearRun(player.uuid)
        player.persistentData.remove(RUN_NBT_KEY)
        if (hadRun) {
            teleportToEntry(player)
            player.sendSystemMessage(Component.literal(
                "${PREFIX}Run over ($reason). §7Back to the start — talk to the Receptionist to try again before midnight."
            ))
            CobblemonBridge.logger.info("tower: run {} for {}", reason, player.gameProfile.name)
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        // Drop the in-memory live state only — the resumable snapshot stays in player NBT (written
        // at every checkpoint by [persist]) so the run resumes on reconnect, not restarts. The
        // epoch-day stamp in the snapshot voids it if the daily rotation passes while offline.
        if (nextFloor.containsKey(event.entity.uuid)) {
            CobblemonBridge.logger.info(
                "tower: run suspended for {} on disconnect — will resume on reconnect (same day)",
                event.entity.gameProfile.name,
            )
        }
        clearRun(event.entity.uuid)
    }

    /** Resume a suspended tower run on reconnect (or server restart). Restores the snapshot written
     *  by [persist], UNLESS the daily rotation has elapsed since the run started (epoch-day moved)
     *  or the player already cleared today — either of which forces a fresh start from floor 1. */
    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val data = player.persistentData
        if (!data.contains(RUN_NBT_KEY)) return
        val tag = data.getCompound(RUN_NBT_KEY)
        val floor = tag.getInt("nextFloor")
        val epochDay = tag.getLong("epochDay")
        if (floor !in 1..LAST_FLOOR || clearedToday(player.uuid)) {
            data.remove(RUN_NBT_KEY)
            return
        }
        if (epochDay != TowerManager.todayEpochDay()) {
            // Leaders rotated while the player was away — the run is void; restart from floor 1.
            data.remove(RUN_NBT_KEY)
            player.sendSystemMessage(Component.literal(
                "${PREFIX}The tower's leaders rotated while you were away — your run was reset. " +
                "Start again from §efloor 1§f before midnight."
            ))
            return
        }
        val party = tag.getList("party", 8 /* TAG_STRING */)
            .mapNotNull { runCatching { UUID.fromString(it.asString) }.getOrNull() }.toSet()
        nextFloor[player.uuid] = floor
        partySnapshot[player.uuid] = party
        if (tag.contains("difficulty")) runDifficulty[player.uuid] = tag.getString("difficulty")
        player.sendSystemMessage(Component.literal(
            "${PREFIX}Welcome back — your run continues at §efloor $floor§f. " +
            "§7Head to the floor-$floor leader to keep going (party still locked)."
        ))
        CobblemonBridge.logger.info(
            "tower: run resumed for {} at floor {}", player.gameProfile.name, floor,
        )
    }

    /** Write a resumable snapshot (next floor + difficulty + party + the run's epoch-day) of the
     *  current run to player NBT. Called at every progression checkpoint; player NBT is saved on the
     *  regular tick and on logout, so the snapshot survives disconnect, clean restart, and crash. */
    private fun persist(player: ServerPlayer) {
        val uuid = player.uuid
        val floor = nextFloor[uuid] ?: return
        val tag = CompoundTag()
        tag.putInt("nextFloor", floor)
        tag.putLong("epochDay", TowerManager.todayEpochDay())
        runDifficulty[uuid]?.let { tag.putString("difficulty", it) }
        val list = ListTag()
        partySnapshot[uuid]?.forEach { list.add(StringTag.valueOf(it.toString())) }
        tag.put("party", list)
        player.persistentData.put(RUN_NBT_KEY, tag)
    }
}
