package com.cobblemonranked.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.config.ArenaPos
import com.cobblemonranked.config.RankedConfig
import com.cobblemonranked.decay.DecayManager
import com.cobblemonranked.elo.EloCalculator
import com.cobblemonranked.gui.TeamSelectionGui
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot of a player's location captured before arena teleport, so we can put them back.
 */
data class OriginalLocation(
    val worldId: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

/**
 * Which arena (if any) a match occupies. The mutex on ARENA_1 / ARENA_2 is "at most one
 * active match each at a time"; SPAWN can be shared by any number of overflow matches; NONE
 * means no teleport happened (no arena was configured at start) so the battle runs wherever
 * the players were.
 */
enum class ArenaSlot { ARENA_1, ARENA_2, SPAWN, NONE }

data class ActiveRankedMatch(
    val player1Uuid: UUID,
    val player2Uuid: UUID,
    val battleId: UUID? = null,
    /** Original locations keyed by player UUID; empty when arena teleport is disabled. */
    val originalLocations: Map<UUID, OriginalLocation> = emptyMap(),
    /** Which arena this match is occupying — used for the spectate hint + the slot mutex. */
    val slot: ArenaSlot = ArenaSlot.NONE,
    /**
     * Cobble dollars escrowed PER SIDE at team-select time. Total pool is `2 * wagerPerSide`;
     * winner receives it all. Voided matches (both flee, both disconnect) refund both sides.
     */
    val wagerPerSide: Int = 0,
)

/** Result of [RankedBattleManager.applyMatchResult]. */
data class MatchOutcome(
    val winnerName: String, val loserName: String,
    val oldWinnerElo: Int, val newWinnerElo: Int,
    val oldLoserElo: Int, val newLoserElo: Int,
)

/** Result of [RankedBattleManager.simulateMatch]; carries the dice roll for transparency. */
data class SimulateOutcome(
    val name1: String, val name2: String,
    val elo1: Int, val elo2: Int,
    /** Probability that player1 wins given the ELO gap (in 0.0..1.0). */
    val expected1: Double,
    /** The random number drawn (in 0.0..1.0). player1 wins when roll < expected1. */
    val roll: Double,
    val player1Wins: Boolean,
    val applied: MatchOutcome,
)

object RankedBattleManager {
    private val rankedBattles: ConcurrentHashMap<UUID, ActiveRankedMatch> = ConcurrentHashMap()
    private val pendingTeams: ConcurrentHashMap<UUID, List<Pokemon>> = ConcurrentHashMap()
    private val pendingMatches: ConcurrentHashMap<UUID, UUID> = ConcurrentHashMap()

    /**
     * Players currently in [startBattle]'s `BattleBuilder.pvp1v1` call. The BATTLE_STARTED_PRE
     * subscriber in [registerEvents] uses this to allow our own ranked battles through while
     * vetoing any other PvP path (Cobblemon's right-click → Battle menu, future mod-added /battle
     * commands, etc.) — that way `/ranked challenge` is the only player-vs-player flow that
     * actually starts a battle. Keyed both directions so either actor's UUID looks up the other.
     */
    private val expectedRankedMatch: ConcurrentHashMap<UUID, UUID> = ConcurrentHashMap()

    /**
     * Admin (host) who forced a pending match, keyed by each participant's UUID — so a cancel
     * during team-select also notifies them. Absent for player-initiated challenges/queue.
     */
    private val matchHost: ConcurrentHashMap<UUID, UUID> = ConcurrentHashMap()

    /**
     * Begin the team-select phase of a ranked match. [wagerPerSide] is the cobble-dollar
     * amount each player will be charged when both confirm their teams. Escrow happens at
     * startBattle time, not here, so a cancelled team-select doesn't touch money. [hostUuid] is
     * the admin who forced the match (if any), notified if the team-select is cancelled.
     */
    fun startTeamSelection(player1: ServerPlayer, player2: ServerPlayer, wagerPerSide: Int = 0, hostUuid: UUID? = null) {
        val config = CobblemonRanked.config
        pendingMatches[player1.uuid] = player2.uuid
        pendingMatches[player2.uuid] = player1.uuid
        if (hostUuid != null) {
            matchHost[player1.uuid] = hostUuid
            matchHost[player2.uuid] = hostUuid
        }
        if (wagerPerSide > 0) {
            pendingWager[player1.uuid] = wagerPerSide
            pendingWager[player2.uuid] = wagerPerSide
        }

        openSelectionGui(player1, config.maxLegendaries)
        openSelectionGui(player2, config.maxLegendaries)
    }

    /**
     * Begin a tournament match between two entered players. Each picks a subset of 6 (max 1
     * Legendary/Paradox/Ultra-Beast) from their locked 9-roster; on both confirm the normal ranked
     * [startBattle] runs (ELO applies, arena teleport, etc.). [hostUuid] is the admin who ran the
     * command, notified on cancel. Returns an error string if either player isn't a valid entrant,
     * or null on success.
     */
    fun startTournamentMatch(player1: ServerPlayer, player2: ServerPlayer, hostUuid: UUID?): String? {
        val tm = com.cobblemonranked.tournament.TournamentManager
        val roster1 = tm.resolveRoster(player1) ?: return "${player1.name.string} hasn't entered the tournament."
        val roster2 = tm.resolveRoster(player2) ?: return "${player2.name.string} hasn't entered the tournament."
        if (roster1.isEmpty()) return "${player1.name.string}'s roster Pokémon couldn't be found (released/traded?)."
        if (roster2.isEmpty()) return "${player2.name.string}'s roster Pokémon couldn't be found (released/traded?)."
        if (pendingMatches.containsKey(player1.uuid) || pendingMatches.containsKey(player2.uuid) ||
            findActiveMatchByPlayer(player1.uuid) != null || findActiveMatchByPlayer(player2.uuid) != null) {
            return "One of them is already in a match or team-selecting."
        }

        pendingMatches[player1.uuid] = player2.uuid
        pendingMatches[player2.uuid] = player1.uuid
        if (hostUuid != null) {
            matchHost[player1.uuid] = hostUuid
            matchHost[player2.uuid] = hostUuid
        }
        openTournamentSelectionGui(player1, roster1, roster2)
        openTournamentSelectionGui(player2, roster2, roster1)
        return null
    }

    private fun openTournamentSelectionGui(player: ServerPlayer, pool: List<Pokemon>, opponentRoster: List<Pokemon>) {
        player.openMenu(com.cobblemonranked.gui.TournamentBattleMenuProvider(
            player = player,
            pool = pool,
            opponentRoster = opponentRoster,
            onConfirm = { team ->
                pendingTeams[player.uuid] = team.map { it.clone() }
                player.sendSystemMessage(Component.literal("§a[Tournament] Team locked in! Waiting for opponent..."))
                checkBothReady(player)
            },
            onCancel = { cancelMatch(player) },
        ))
    }

    /** Wager per side for the next-starting match, keyed by either player's UUID. */
    private val pendingWager: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap()

    private fun openSelectionGui(player: ServerPlayer, maxLegendaries: Int) {
        TeamSelectionGui(
            player = player,
            maxLegendaries = maxLegendaries,
            onConfirm = { team ->
                pendingTeams[player.uuid] = team.map { it.clone() }
                player.sendSystemMessage(Component.literal("[Ranked] Team locked in! Waiting for opponent..."))
                checkBothReady(player)
            },
            onCancel = {
                cancelMatch(player)
            }
        ).open()
    }

    private fun checkBothReady(player: ServerPlayer) {
        val opponentUuid = pendingMatches[player.uuid] ?: return
        val myTeam = pendingTeams[player.uuid] ?: return
        val opponentTeam = pendingTeams[opponentUuid] ?: return

        val server = player.server
        val opponent = server.playerList.getPlayer(opponentUuid) ?: run {
            player.sendSystemMessage(Component.literal("[Ranked] Opponent disconnected. Match cancelled."))
            cleanup(player.uuid, opponentUuid)
            return
        }

        startBattle(player, myTeam, opponent, opponentTeam)
    }

    private fun startBattle(
        player1: ServerPlayer, team1: List<Pokemon>,
        player2: ServerPlayer, team2: List<Pokemon>
    ) {
        val config = CobblemonRanked.config

        // Legality check. countsAsLegendary() also counts Mythical (Arceus, Mew, …) and Paradox
        // Pokémon, which Cobblemon's isLegendary() does not — all are Ubers/restricted tier.
        val p1Legendaries = team1.count { it.countsAsLegendary() }
        val p2Legendaries = team2.count { it.countsAsLegendary() }

        if (p1Legendaries > config.maxLegendaries && p2Legendaries > config.maxLegendaries) {
            broadcast(player1.server,
                "[Ranked] Both players had illegal teams (too many legendaries). Match voided.")
            cleanup(player1.uuid, player2.uuid)
            return
        }
        if (p1Legendaries > config.maxLegendaries) {
            player1.sendSystemMessage(Component.literal(
                "[Ranked] Your team has $p1Legendaries legendaries (max ${config.maxLegendaries}). You auto-lose."))
            resolveMatch(player2, player1)
            cleanup(player1.uuid, player2.uuid)
            return
        }
        if (p2Legendaries > config.maxLegendaries) {
            player2.sendSystemMessage(Component.literal(
                "[Ranked] Your team has $p2Legendaries legendaries (max ${config.maxLegendaries}). You auto-lose."))
            resolveMatch(player1, player2)
            cleanup(player1.uuid, player2.uuid)
            return
        }

        // PvP banlist backstop. The selection GUIs already block banned power-forms (Mega Mewtwo,
        // Mega Rayquaza, Primal Kyogre/Groudon, Ultra Necrozma, Crowned Zacian/Zamazenta, Calyrex
        // Shadow, Miraidon), so this only catches a team that reaches here some other way. Cancel
        // (no ELO, no escrow) rather than auto-lose — it's a legality issue, not cheating.
        val p1Ban = team1.firstNotNullOfOrNull { p -> p.rankedBanReason()?.let { "${p.species.name} ($it)" } }
        val p2Ban = team2.firstNotNullOfOrNull { p -> p.rankedBanReason()?.let { "${p.species.name} ($it)" } }
        if (p1Ban != null || p2Ban != null) {
            val msg = Component.literal(
                "[Ranked] Match cancelled — PvP-banned Pokémon: ${listOfNotNull(p1Ban, p2Ban).joinToString(", ")}.")
            player1.sendSystemMessage(msg)
            player2.sendSystemMessage(msg)
            cleanup(player1.uuid, player2.uuid)
            return
        }

        // Escrow wager: withdraw from BOTH players. Re-cap to the per-player limits in case
        // balances changed between challenge and accept. If either withdraw fails, refund
        // any partial deduction and void the match.
        val intendedWager = pendingWager[player1.uuid] ?: pendingWager[player2.uuid] ?: 0
        val actualWager = if (intendedWager > 0) {
            val challengerCap = com.cobblemonranked.economy.EconomyBridge.getBalance(player1.uuid) / 2
            val targetCap = com.cobblemonranked.economy.EconomyBridge.getBalance(player2.uuid) / 4
            val effective = minOf(intendedWager, challengerCap, targetCap).coerceAtLeast(0)
            if (effective <= 0) {
                player1.sendSystemMessage(Component.literal(
                    "§c[Ranked] Wager voided — one of you no longer has enough to cover \$$intendedWager."))
                player2.sendSystemMessage(Component.literal(
                    "§c[Ranked] Wager voided — one of you no longer has enough to cover \$$intendedWager."))
                0
            } else {
                val w1Ok = com.cobblemonranked.economy.EconomyBridge.withdraw(player1.uuid, effective)
                val w2Ok = if (w1Ok) com.cobblemonranked.economy.EconomyBridge.withdraw(player2.uuid, effective) else false
                if (!w1Ok || !w2Ok) {
                    if (w1Ok && !w2Ok) com.cobblemonranked.economy.EconomyBridge.deposit(player1.uuid, effective)
                    player1.sendSystemMessage(Component.literal("§c[Ranked] Wager escrow failed — match voided."))
                    player2.sendSystemMessage(Component.literal("§c[Ranked] Wager escrow failed — match voided."))
                    pendingWager.remove(player1.uuid); pendingWager.remove(player2.uuid)
                    cleanup(player1.uuid, player2.uuid)
                    return
                }
                broadcast(player1.server,
                    "[Ranked] Wager \$$effective from each player escrowed — winner takes the pool.")
                effective
            }
        } else 0
        pendingWager.remove(player1.uuid); pendingWager.remove(player2.uuid)

        // Save teams for showcase
        CobblemonRanked.teamStore.saveTeam(player1.uuid, team1)
        CobblemonRanked.teamStore.saveTeam(player2.uuid, team2)

        // Pick an arena slot for this match. Allocation priority: arena 1 → arena 2 → spawn.
        // ARENA_1 / ARENA_2 are mutexed (one active match at a time, gated by [rankedBattles]),
        // SPAWN is a shared overflow with no mutex so a 3rd+ concurrent match always has
        // somewhere to land. If no arena is configured at all, [ArenaSlot.NONE] means players
        // battle in place — no teleport, no teleport-back.
        val slot = allocateSlot(config)
        val originals: Map<UUID, OriginalLocation> = if (slot != ArenaSlot.NONE) {
            val map = mapOf(
                player1.uuid to captureLocation(player1),
                player2.uuid to captureLocation(player2),
            )
            val (p1Pos, p2Pos) = positionsFor(slot, config)
            teleport(player1, p1Pos)
            teleport(player2, p2Pos)
            map
        } else {
            emptyMap()
        }

        // Build temporary party stores with selected teams
        val tempParty1 = buildTempParty(player1.uuid, team1)
        val tempParty2 = buildTempParty(player2.uuid, team2)
        val teamMap = mapOf(player1.uuid to tempParty1, player2.uuid to tempParty2)

        val format = BattleFormat.GEN_9_SINGLES.copy(adjustLevel = config.levelCap)

        // Flag the about-to-start match so our BATTLE_STARTED_PRE veto in [registerEvents]
        // lets it through. The flag must be set BEFORE pvp1v1 because the PRE event fires
        // synchronously during that call, before we'd otherwise have a battleId to track.
        expectedRankedMatch[player1.uuid] = player2.uuid
        expectedRankedMatch[player2.uuid] = player1.uuid
        val result = try {
            BattleBuilder.pvp1v1(
                player1 = player1,
                player2 = player2,
                battleFormat = format,
                healFirst = true,
                cloneParties = true,
                partyAccessor = { teamMap[it.uuid] ?: Cobblemon.storage.getParty(it) }
            )
        } finally {
            expectedRankedMatch.remove(player1.uuid)
            expectedRankedMatch.remove(player2.uuid)
        }

        result.ifSuccessful { battle ->
            val match = ActiveRankedMatch(
                player1.uuid, player2.uuid, battle.battleId, originals, slot, actualWager,
            )
            rankedBattles[battle.battleId] = match

            // Match announcement: tell every online player who's fighting and where to spectate.
            val store = CobblemonRanked.eloStore
            val e1 = store.getOrCreate(player1.uuid, player1.name.string).elo
            val e2 = store.getOrCreate(player2.uuid, player2.name.string).elo
            val warp = warpNameFor(slot)
            val spectate = if (slot == ArenaSlot.NONE)
                "§7 (no spectate location — battling in place)"
            else
                "§7 — Spectate at §f/warp $warp"
            broadcast(player1.server, "§6§l[Ranked Match] §r§f" +
                "${player1.name.string} §7($e1) §fvs §f${player2.name.string} §7($e2)" +
                spectate)
        }

        result.ifErrored {
            player1.sendSystemMessage(Component.literal("[Ranked] Failed to start battle."))
            player2.sendSystemMessage(Component.literal("[Ranked] Failed to start battle."))
            // Battle never started — teleport back immediately if we already moved them.
            originals[player1.uuid]?.let { restore(player1, it) }
            originals[player2.uuid]?.let { restore(player2, it) }
            // …and refund the escrowed wager, since no actual battle happened.
            if (actualWager > 0) {
                com.cobblemonranked.economy.EconomyBridge.deposit(player1.uuid, actualWager)
                com.cobblemonranked.economy.EconomyBridge.deposit(player2.uuid, actualWager)
                player1.sendSystemMessage(Component.literal("§7[Ranked] Wager \$$actualWager refunded."))
                player2.sendSystemMessage(Component.literal("§7[Ranked] Wager \$$actualWager refunded."))
            }
        }

        cleanup(player1.uuid, player2.uuid)
    }

    /**
     * Pick the next arena for a new match. Priority: arena 1 → arena 2 → spawn → NONE.
     * The two arenas are mutexed (a slot is "in use" iff some [ActiveRankedMatch] in
     * [rankedBattles] currently holds it); spawn is shared and never mutexed.
     */
    private fun allocateSlot(config: RankedConfig): ArenaSlot {
        val inUse = rankedBattles.values.mapTo(hashSetOf()) { it.slot }
        return when {
            config.isArenaConfigured() && ArenaSlot.ARENA_1 !in inUse -> ArenaSlot.ARENA_1
            config.isArena2Configured() && ArenaSlot.ARENA_2 !in inUse -> ArenaSlot.ARENA_2
            config.isSpawnConfigured() -> ArenaSlot.SPAWN
            else -> ArenaSlot.NONE
        }
    }

    /** Returns (player1 landing pos, player2 landing pos) for the given slot. */
    private fun positionsFor(slot: ArenaSlot, config: RankedConfig): Pair<ArenaPos, ArenaPos> =
        when (slot) {
            ArenaSlot.ARENA_1 -> config.arenaPos1!! to config.arenaPos2!!
            ArenaSlot.ARENA_2 -> config.arena2Pos1!! to config.arena2Pos2!!
            // Spawn is a single shared point — both players land on the same coords.
            ArenaSlot.SPAWN -> config.spawnPos!! to config.spawnPos
            ArenaSlot.NONE -> error("positionsFor(NONE) shouldn't be called — gate on slot first")
        }

    /** Maps a slot to the warp name shown in the match-start broadcast / spectate hint. */
    fun warpNameFor(slot: ArenaSlot): String = when (slot) {
        ArenaSlot.ARENA_1 -> "arena1"
        ArenaSlot.ARENA_2 -> "arena2"
        ArenaSlot.SPAWN -> "spawn"
        ArenaSlot.NONE -> "(no arena)"
    }

    private fun captureLocation(player: ServerPlayer): OriginalLocation {
        val level = player.serverLevel()
        return OriginalLocation(
            worldId = level.dimension().location().toString(),
            x = player.x, y = player.y, z = player.z,
            yaw = player.yRot, pitch = player.xRot,
        )
    }

    private fun teleport(player: ServerPlayer, pos: ArenaPos) {
        val level = resolveLevel(player.server, pos.world) ?: run {
            CobblemonRanked.logger.warn("Unknown arena dimension '{}' — skipping teleport for {}",
                pos.world, player.name.string)
            return
        }
        player.teleportTo(level, pos.x, pos.y, pos.z, pos.yaw, pos.pitch)
    }

    private fun restore(player: ServerPlayer, loc: OriginalLocation) {
        val level = resolveLevel(player.server, loc.worldId) ?: run {
            CobblemonRanked.logger.warn("Cannot restore {} — original dimension '{}' not loaded",
                player.name.string, loc.worldId)
            return
        }
        player.teleportTo(level, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
    }

    private fun resolveLevel(server: MinecraftServer, worldId: String): ServerLevel? {
        val rl = ResourceLocation.tryParse(worldId) ?: return null
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, rl))
    }

    /**
     * Teleports the two players in [match] back to their captured starting locations.
     * No-op when the match has no captured locations (arena was disabled at start).
     * Players who logged out are skipped — they'll wake up at the arena, a known limitation.
     */
    /**
     * Called whenever a ranked match concludes (victory, flee, forfeit, disconnect). Lets
     * [QueueManager] mark the pair as having played each other and re-queue either player
     * who had /queue auto enabled.
     */
    private fun notifyMatchEnded(server: MinecraftServer, match: ActiveRankedMatch) {
        com.cobblemonranked.queue.QueueManager.onMatchEnded(server, match.player1Uuid, match.player2Uuid)
    }

    /** Pay the entire 2× wager pool to the winner; no-op for non-wager matches. */
    private fun payWagerToWinner(match: ActiveRankedMatch, winner: ServerPlayer) {
        if (match.wagerPerSide <= 0) return
        val pool = match.wagerPerSide * 2
        com.cobblemonranked.economy.EconomyBridge.deposit(winner.uuid, pool)
        winner.sendSystemMessage(Component.literal(
            "§6[Ranked] §fWager pool §e\$$pool §fcredited to your balance."))
    }

    /** Refund each side their escrowed wager — used on voided matches. */
    private fun refundWager(match: ActiveRankedMatch, server: MinecraftServer) {
        if (match.wagerPerSide <= 0) return
        com.cobblemonranked.economy.EconomyBridge.deposit(match.player1Uuid, match.wagerPerSide)
        com.cobblemonranked.economy.EconomyBridge.deposit(match.player2Uuid, match.wagerPerSide)
        for (uuid in listOf(match.player1Uuid, match.player2Uuid)) {
            server.playerList.getPlayer(uuid)?.sendSystemMessage(Component.literal(
                "§7[Ranked] Wager \$${match.wagerPerSide} refunded."))
        }
    }

    private fun teleportBack(server: MinecraftServer, match: ActiveRankedMatch) {
        if (match.originalLocations.isEmpty()) return
        for ((uuid, loc) in match.originalLocations) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            restore(player, loc)
        }
    }

    private fun buildTempParty(ownerUuid: UUID, team: List<Pokemon>): PlayerPartyStore {
        val store = PlayerPartyStore(ownerUuid)
        team.forEach { pokemon ->
            val clone = pokemon.clone()
            clone.heal()
            store.add(clone)
        }
        return store
    }

    fun registerEvents() {
        // Route every PvP battle through the ranked system. The Cobblemon right-click → Battle
        // flow ends in BattleBuilder.pvp1v1 (same as our own [startBattle]) which fires
        // BATTLE_STARTED_PRE; if that battle isn't one we started (checked via
        // [expectedRankedMatch]) we cancel the Cobblemon path and redirect both players into
        // our team-select flow. The two clicks they already performed in Cobblemon's UI
        // (initiator's "Battle" + target's accept) are treated as the equivalent of
        // /ranked challenge + /ranked accept — they explicitly consented to fight each other,
        // we just enforce that the fight uses ranked rules + ELO + arena teleport.
        //
        // Single-PlayerBattleActor battles (wild, NPC trainer, gym, E4 gauntlet) fall through
        // the size < 2 early exit untouched.
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST) { event ->
            val playerActors = event.battle.actors.filterIsInstance<PlayerBattleActor>()
            if (playerActors.size < 2) return@subscribe
            // Ranked is strictly 1v1 singles. Anything else — a Doubles (or Triples/Multi) PvP
            // battle, or a >2-player multi battle — is left to run as a normal, UNRANKED Cobblemon
            // battle. Players who pick Doubles in the battle-request menu get a casual doubles match;
            // we neither cancel it nor touch ELO. Only the 1v1-singles path routes to ranked below.
            val isSingles = event.battle.format.battleType.pokemonPerSide == 1
            if (!isSingles || playerActors.size > 2) return@subscribe
            val a = playerActors[0].entity ?: return@subscribe
            val b = playerActors[1].entity ?: return@subscribe
            if (expectedRankedMatch[a.uuid] == b.uuid) return@subscribe  // it's our ranked match

            event.cancel()

            // Already mid-flow check — don't double-trigger team-select if these players are
            // already in a ranked match or team-selecting elsewhere.
            val alreadyBusy = pendingMatches.containsKey(a.uuid) ||
                pendingMatches.containsKey(b.uuid) ||
                rankedBattles.values.any { m ->
                    m.player1Uuid == a.uuid || m.player2Uuid == a.uuid ||
                        m.player1Uuid == b.uuid || m.player2Uuid == b.uuid
                }
            if (alreadyBusy) {
                a.sendSystemMessage(Component.literal(
                    "§c[Ranked] One of you is already in another ranked flow — finish it first."))
                return@subscribe
            }

            a.sendSystemMessage(Component.literal(
                "§a[Ranked] §fRouting to ranked team selection. Pick up to 6 Pokémon, then Confirm."))
            b.sendSystemMessage(Component.literal(
                "§a[Ranked] §fRouting to ranked team selection. Pick up to 6 Pokémon, then Confirm."))
            startTeamSelection(a, b)
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            val match = rankedBattles.remove(event.battle.battleId) ?: return@subscribe
            val winners = event.winners.filterIsInstance<PlayerBattleActor>()
            val losers = event.losers.filterIsInstance<PlayerBattleActor>()

            if (winners.isNotEmpty() && losers.isNotEmpty()) {
                val winnerPlayer = winners.first().entity
                val loserPlayer = losers.first().entity
                if (winnerPlayer != null && loserPlayer != null) {
                    resolveMatch(winnerPlayer, loserPlayer)
                    payWagerToWinner(match, winnerPlayer)
                    teleportBack(winnerPlayer.server, match)
                    notifyMatchEnded(winnerPlayer.server, match)
                }
            }
        }

        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event ->
            val match = rankedBattles.remove(event.battle.battleId) ?: return@subscribe
            // Outer scope so the when-branches below can pay or refund without re-looking-up.
            // Determine who fled — the player who is no longer in the battle
            val actors = event.battle.actors.filterIsInstance<PlayerBattleActor>()
            val server = actors.firstOrNull()?.entity?.server ?: return@subscribe

            val p1 = server.playerList.getPlayer(match.player1Uuid)
            val p2 = server.playerList.getPlayer(match.player2Uuid)

            // The player who fled loses
            if (p1 != null && p2 != null) {
                // Check who is still in battle vs who fled
                val p1InBattle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(p1) != null
                val p2InBattle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(p2) != null

                when {
                    !p1InBattle && p2InBattle -> { resolveMatch(p2, p1); payWagerToWinner(match, p2) }
                    !p2InBattle && p1InBattle -> { resolveMatch(p1, p2); payWagerToWinner(match, p1) }
                    else -> {
                        // Both left — void the match and refund.
                        broadcast(server, "[Ranked] Both players fled. Match voided.")
                        refundWager(match, server)
                    }
                }
            }
            teleportBack(server, match)
            notifyMatchEnded(server, match)
        }
    }

    private fun resolveMatch(winner: ServerPlayer, loser: ServerPlayer) {
        applyMatchResult(
            server = winner.server,
            winnerUuid = winner.uuid, winnerName = winner.name.string,
            loserUuid = loser.uuid, loserName = loser.name.string,
        )
    }

    /**
     * Applies an ELO match outcome: updates ELO/wins/losses, persists, marks the day
     * for decay tracking, and broadcasts a 3-line result message — header + winner ELO
     * (green) + loser ELO (red).
     *
     * The pre-0.7.36 implementation also auto-broadcast the top-N leaderboard after every
     * match. Pulled because post-match chat spam was too noisy; players now run
     * `/ranked leaderboard` (already wired in [com.cobblemonranked.commands.RankedCommands])
     * on demand instead.
     *
     * Shared by the real battle path ([resolveMatch]) and the simulate-from-console path
     * ([simulateMatch]) so both paths exercise the exact same ELO/persistence code.
     */
    fun applyMatchResult(
        server: MinecraftServer,
        winnerUuid: UUID, winnerName: String,
        loserUuid: UUID, loserName: String,
    ): MatchOutcome {
        val store = CobblemonRanked.eloStore
        val config = CobblemonRanked.config
        val winnerData = store.getOrCreate(winnerUuid, winnerName)
        val loserData = store.getOrCreate(loserUuid, loserName)

        val oldWinnerElo = winnerData.elo
        val oldLoserElo = loserData.elo

        val (newWinnerElo, newLoserElo) = EloCalculator.calculate(
            winnerElo = oldWinnerElo,
            loserElo = oldLoserElo,
            kFactor = config.kFactor,
            minimumElo = config.minimumElo,
        )

        winnerData.elo = newWinnerElo
        winnerData.wins++
        winnerData.lastBattleDate = LocalDate.now().toString()

        loserData.elo = newLoserElo
        loserData.losses++
        loserData.lastBattleDate = LocalDate.now().toString()

        store.save()
        DecayManager.recordBattle()

        // Quest advancements for the winner: first_pvp_win + any newly-crossed ELO thresholds.
        // Skips silently when the player is offline (e.g., during simulateMatch from console)
        // or when the datapack hasn't loaded the advancement.
        server.playerList.getPlayer(winnerUuid)?.let { winnerPlayer ->
            awardQuest(winnerPlayer, "server:first_pvp_win")
            for (threshold in ELO_THRESHOLDS) {
                if (newWinnerElo >= threshold && oldWinnerElo < threshold) {
                    awardQuest(winnerPlayer, "server:reach_elo_$threshold")
                }
            }
        }

        val winnerDelta = newWinnerElo - oldWinnerElo
        val loserDelta = newLoserElo - oldLoserElo
        // Three-line broadcast: header + winner ELO (green) + loser ELO (red).
        // Players run /ranked leaderboard on demand — the auto-leaderboard broadcast was
        // pulled in 0.7.36 because the post-match spam was too noisy.
        // U+2192 → for the ELO transition. Sign-aware delta formatter so the loser line
        // never shows `(-0)` at the ELO floor and the winner line never shows `(+-N)` if
        // the calculator ever returns something weird.
        broadcast(server, "[Ranked] $winnerName defeated $loserName!")
        broadcast(server, "§a$winnerName: $oldWinnerElo → $newWinnerElo (${signed(winnerDelta)})§r")
        broadcast(server, "§c$loserName: $oldLoserElo → $newLoserElo (${signed(loserDelta)})§r")

        return MatchOutcome(
            winnerName = winnerName, loserName = loserName,
            oldWinnerElo = oldWinnerElo, newWinnerElo = newWinnerElo,
            oldLoserElo = oldLoserElo, newLoserElo = newLoserElo,
        )
    }

    /**
     * Simulates a ranked match between two players (online or not) using offline-mode
     * UUIDs derived from names. Winner is chosen randomly weighted by ELO win probability:
     * `expected1 = 1 / (1 + 10^((elo2 - elo1) / 400))`. Then [applyMatchResult] runs the
     * usual ELO/persistence/leaderboard path.
     */
    fun simulateMatch(server: MinecraftServer, name1: String, name2: String): SimulateOutcome {
        val store = CobblemonRanked.eloStore
        val uuid1 = offlineUuid(name1)
        val uuid2 = offlineUuid(name2)
        val data1 = store.getOrCreate(uuid1, name1)
        val data2 = store.getOrCreate(uuid2, name2)

        // Snapshot pre-match ELOs *before* applyMatchResult mutates the records, so the
        // returned SimulateOutcome reflects the state used to compute the win probability.
        val preElo1 = data1.elo
        val preElo2 = data2.elo
        val expected1 = 1.0 / (1.0 + Math.pow(10.0, (preElo2 - preElo1) / 400.0))
        val roll = Math.random()
        val player1Wins = roll < expected1

        val winnerName: String; val loserName: String
        val winnerUuid: UUID; val loserUuid: UUID
        if (player1Wins) {
            winnerName = name1; winnerUuid = uuid1
            loserName = name2; loserUuid = uuid2
        } else {
            winnerName = name2; winnerUuid = uuid2
            loserName = name1; loserUuid = uuid1
        }
        val outcome = applyMatchResult(server, winnerUuid, winnerName, loserUuid, loserName)
        return SimulateOutcome(
            name1 = name1, name2 = name2,
            elo1 = preElo1, elo2 = preElo2,
            expected1 = expected1, roll = roll, player1Wins = player1Wins,
            applied = outcome,
        )
    }

    private fun offlineUuid(name: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

    /**
     * Cancel a pending (team-select phase) match. Sends a uniform "Match cancelled." to BOTH
     * players and the admin host (if any), and closes the OTHER player's still-open selection
     * menu. Deliberately does nothing else — no ELO change, no auto-rematch (manual rerun).
     *
     * State is cleared before closing the opponent's container, so the re-entrant cancel that
     * fires from their menu's `removed()` hook finds no pending match and no-ops.
     */
    private fun cancelMatch(player: ServerPlayer) {
        // If a real battle is already in flight for this player, treat the cancel as a forfeit
        // — the team-select menu shouldn't have been re-openable past startBattle, but cover it.
        if (findActiveMatchByPlayer(player.uuid) != null) {
            forfeitMatch(player, reason = "cancelled the match")
            return
        }
        val opponentUuid = pendingMatches[player.uuid] ?: return  // already cancelled → no-op
        val hostUuid = matchHost[player.uuid]
        cleanup(player.uuid, opponentUuid)

        val server = player.server
        val opponent = server.playerList.getPlayer(opponentUuid)
        val msg = Component.literal("§c[Ranked] Match cancelled.")
        player.sendSystemMessage(msg)
        opponent?.sendSystemMessage(msg)
        hostUuid?.let { server.playerList.getPlayer(it) }
            ?.takeIf { it.uuid != player.uuid && it.uuid != opponentUuid }
            ?.sendSystemMessage(msg)
        // Close the opponent's still-open selection menu (the canceller's is already closing).
        opponent?.closeContainer()
    }

    /**
     * Returns the active match containing [playerUuid], or null if the player isn't in one.
     * Used by the leave/forfeit handlers (logout, /ranked admin forfeit) to find the right
     * match without having to know its battleId.
     */
    private fun findActiveMatchByPlayer(playerUuid: UUID): Pair<UUID, ActiveRankedMatch>? {
        return rankedBattles.entries.firstNotNullOfOrNull { (battleId, match) ->
            if (match.player1Uuid == playerUuid || match.player2Uuid == playerUuid)
                battleId to match else null
        }
    }

    /**
     * Public: treat [leaver] as having forfeited any active ranked battle they're in. The
     * opponent (still online) gets the ELO win, both players are teleported back to their
     * captured pre-arena positions, and the match is removed from [rankedBattles] so the
     * Cobblemon-side BATTLE_FLED / BATTLE_VICTORY handlers won't double-resolve it.
     *
     * Returns true iff a match was found and resolved. Safe to call when the player isn't in
     * a match (returns false).
     *
     * Called from:
     *  - [onPlayerLogOut]: disconnect mid-battle
     *  - `/ranked admin forfeit <player>`: admin unblocks a hung match
     *  - [cancelMatch] edge-case: cancel triggered after battle already started
     */
    fun forfeitMatch(leaver: ServerPlayer, reason: String = "left the match"): Boolean {
        val (battleId, match) = findActiveMatchByPlayer(leaver.uuid) ?: return false
        val server = leaver.server
        val opponentUuid = if (match.player1Uuid == leaver.uuid) match.player2Uuid else match.player1Uuid
        val opponent = server.playerList.getPlayer(opponentUuid)

        // End the underlying Cobblemon battle so it doesn't keep ticking (and so its own
        // BATTLE_VICTORY / BATTLE_FLED can't fire afterwards for the same battleId).
        rankedBattles.remove(battleId)
        val cobBattle = Cobblemon.battleRegistry.getBattle(battleId)
        try { cobBattle?.end() } catch (e: Exception) {
            CobblemonRanked.logger.warn("forfeit: failed to end Cobblemon battle {}: {}", battleId, e.message)
        }

        if (opponent != null) {
            // Real ELO update: leaver loses, opponent gains.
            resolveMatch(opponent, leaver)
            payWagerToWinner(match, opponent)
            opponent.sendSystemMessage(Component.literal("[Ranked] ${leaver.name.string} $reason. You win by forfeit."))
        } else {
            // Both gone — void and refund.
            broadcast(server, "[Ranked] ${leaver.name.string} $reason and opponent is offline. Match voided.")
            refundWager(match, server)
        }
        leaver.sendSystemMessage(Component.literal("[Ranked] You forfeited the match."))

        teleportBack(server, match)
        notifyMatchEnded(server, match)
        return true
    }

    /**
     * Disconnect-during-battle = forfeit. NeoForge subscriber registered via the EVENT_BUS so we
     * see logout before the player object is invalidated. [forfeitMatch] handles the rest;
     * we also clean up pendingMatches for the team-select-stage logout case (no ELO change there).
     */
    @SubscribeEvent
    fun onPlayerLogOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (!forfeitMatch(player, reason = "disconnected")) {
            // Not in an active battle — but might be in team-select.
            val opponentUuid = pendingMatches[player.uuid] ?: return
            val opponent = player.server.playerList.getPlayer(opponentUuid)
            opponent?.sendSystemMessage(Component.literal("[Ranked] ${player.name.string} disconnected. Match cancelled."))
            cleanup(player.uuid, opponentUuid)
        }
    }

    private fun cleanup(uuid1: UUID, uuid2: UUID) {
        pendingTeams.remove(uuid1)
        pendingTeams.remove(uuid2)
        pendingMatches.remove(uuid1)
        pendingMatches.remove(uuid2)
        matchHost.remove(uuid1)
        matchHost.remove(uuid2)
    }

    private fun broadcast(server: MinecraftServer, message: String) {
        server.playerList.players.forEach {
            it.sendSystemMessage(Component.literal(message))
        }
    }

    /** Render an ELO delta with an explicit sign and U+2212 minus (so loser deltas read
     *  as `−16`, not `-16`). Renders zero as `±0` so an ELO-floor case doesn't visually
     *  collapse with a positive delta. */
    private fun signed(delta: Int): String = when {
        delta > 0 -> "+$delta"
        delta < 0 -> "−${-delta}"
        else -> "±0"
    }

    /**
     * ELO thresholds that grant `server:reach_elo_<N>` advancements. Set deliberately as
     * milestones; the current "active quest goal" is 1100 (see HUD cascade in the server-quests
     * datapack). The remaining thresholds award silently with `goal`-framed advancements until
     * promoted.
     */
    private val ELO_THRESHOLDS = listOf(1100, 1200, 1300, 1500, 2000)

    /**
     * Award `advancementId`'s default criterion to [player] if the advancement exists and the
     * player hasn't completed it. Used to grant the quest-datapack advancements from event
     * handlers. No-op on missing datapack (server starts up without quest pack loaded).
     */
    private fun awardQuest(player: ServerPlayer, advancementId: String, criterion: String = "done") {
        val rl = ResourceLocation.parse(advancementId)
        val holder = player.server.advancements.get(rl) ?: return
        val progress = player.advancements.getOrStartProgress(holder)
        if (progress.isDone) return
        player.advancements.award(holder, criterion)
    }
}
