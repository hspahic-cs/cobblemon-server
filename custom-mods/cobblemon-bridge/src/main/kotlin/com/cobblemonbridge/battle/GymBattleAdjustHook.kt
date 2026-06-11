package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.util.party
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.quests.GymCaps
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Asymmetric downlevel for gym battles. RCT's `adjustPlayerLevels: true` is dead config — the
 * field is read from JSON into `BattleRules` but never consumed at runtime. Cobblemon's
 * `BattleFormat.adjustLevel` is the real scaling primitive but operates symmetrically — the
 * mainline gym leader's intentionally weaker team (Dusty at L15) should stay weak even if the
 * player is at the gym's L20 cap.
 *
 * **The unavoidable mutation.** Cobblemon's `BattlePokemon` for player-side mons uses
 * {@code effectedPokemon == originalPokemon} (no clone — that's only for trainer teams via
 * {@code safeCopyOf}). The only place to alter the level the engine sees is on that shared
 * Pokemon instance, which means we're temporarily mutating the player's real Pokemon. To make
 * this **crash-safe**:
 *
 *   1. Before mutating, we serialize {pokemonUuid → originalLevel} into the player's forge
 *      `getPersistentData()` NBT under [PENDING_NBT_KEY]. NBT is saved with the player profile
 *      on the regular Cobblemon save tick — so a crash mid-battle leaves both the downleveled
 *      Pokemon AND the restore instructions on disk together.
 *   2. On normal battle end (VICTORY / FLED) we restore + clear the NBT.
 *   3. On player login we check the NBT and restore any pending levels — covering crash,
 *      disconnect-mid-battle, and any other path that skipped step 2.
 *
 * The cap is stashed at battle entry — via [EventPriority.LOW] EntityInteract for the
 * right-click path, or via [stashCap] called from [GymBattleGate] (the Mixin gate) for the
 * force-battle path. Both end up funneling through [applyToBattle] on [BattleStartedEvent.Pre].
 *
 * Cap formula: `cap = 20 + 5 × (gymId − 1)` (see [capForGym]). Matches `LevelCap` going INTO the
 * gym (i.e., before beating it). Gym 1 → 20, gym 2 → 25, …, gym 10 → 65. Challenge gyms instead
 * carry a flat `level_cap.50` tag, so the player fights at the team's true L50.
 *
 * Both variants carry the same `gym_id.<N>` tag, but the challenge variant ALSO carries a flat
 * `level_cap.50` tag which takes precedence ([BridgeTags.findLevelCap] before the formula). So
 * mainline Dusty downlevels the player to the gym-1 formula cap (L20) against his L15 team, while
 * Challenge Dusty downlevels to a flat L50 against his L50 team — a true L50-vs-L50 fight.
 */
object GymBattleAdjustHook {

    private const val STASH_TTL_MS: Long = 5 * 60 * 1000L
    private const val PENDING_NBT_KEY = "cobblemon_bridge_gym_pending_restore"
    private const val SWEEPER_INTERVAL_TICKS = 20  // 1 second
    private var sweeperTickCounter = 0

    /** playerUuid → (resolvedCap, capturedAtMs). The cap is already resolved (formula for
     *  gym_id, or the flat value for a level_cap tag) — applyToBattle uses it directly.
     *  In-memory only — the persisted restore data lives in player NBT, not here. */
    private val pendingByPlayer: MutableMap<UUID, Pair<Int, Long>> = ConcurrentHashMap()

    /** In-battle downlevel cap for gym [gymId], from the authored [GymCaps] config (NOT the old
     *  number formula). A gym with no configured cap returns [Int.MAX_VALUE] — no mon is ever above
     *  it, so the player is effectively uncapped for that fight. */
    fun capForGym(gymId: Int): Int = GymCaps.battleCap(gymId) ?: Int.MAX_VALUE

    fun registerEvents() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL) { event ->
            applyToBattle(event)
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            restoreActors(event.winners + event.losers)
        }
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event ->
            restoreActors(listOf(event.player))
        }
    }

    /** Stash an already-resolved flat cap (e.g. from a `level_cap` tag). */
    fun stashCap(uuid: UUID, cap: Int) {
        pendingByPlayer[uuid] = cap to System.currentTimeMillis()
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide || event.isCanceled) return
        val player = event.entity as? ServerPlayer ?: return
        // A flat level_cap tag wins over the gym_id formula (pe AI-test gyms use a flat L50).
        val cap = BridgeTags.findLevelCap(event.target.tags)
            ?: BridgeTags.findGymId(event.target.tags)?.let(::capForGym)
            ?: return
        pendingByPlayer[player.uuid] = cap to System.currentTimeMillis()
    }

    /** Crash-recovery: on every login, restore any in-flight downlevel that wasn't cleared
     *  by a normal battle-end. Covers server crash mid-battle, disconnect mid-battle, and any
     *  other path that skipped [restoreActors]. */
    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val restored = restoreFromNbt(player)
        if (restored > 0) {
            player.sendSystemMessage(Component.literal(
                "§7[Gym] Restored §f$restored §7Pokémon level(s) from an interrupted gym battle."
            ))
            CobblemonBridge.logger.info(
                "Login crash-restore for {}: {} pokemon restored", player.gameProfile.name, restored,
            )
        }
    }

    /**
     * Orphan-restore sweeper. Per second, find any online player with pending-restore NBT but
     * no active battle in [BattleRegistry] — they got mutated for a battle that ended via a
     * path that didn't fire [BattleVictoryEvent] / [BattleFledEvent] (admin `/cobblemon battle
     * close`, Showdown error, external `closeBattle()`). Restore them immediately.
     *
     * The login-restore [onPlayerLoggedIn] handler covers disconnect / crash. This handler
     * covers admin-end / engine-error while the player is still online.
     */
    @SubscribeEvent
    fun onServerTickPost(event: ServerTickEvent.Post) {
        sweeperTickCounter++
        if (sweeperTickCounter < SWEEPER_INTERVAL_TICKS) return
        sweeperTickCounter = 0
        for (player in event.server.playerList.players) {
            if (!player.persistentData.contains(PENDING_NBT_KEY)) continue
            // Has pending data — is there a real battle in flight?
            if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) continue
            val restored = restoreFromNbt(player)
            if (restored > 0) {
                player.sendSystemMessage(Component.literal(
                    "§7[Gym] Restored §f$restored §7Pokémon level(s) — battle ended unexpectedly."
                ))
                CobblemonBridge.logger.info(
                    "Sweeper-restore for {}: {} pokemon restored (battle absent from registry)",
                    player.gameProfile.name, restored,
                )
            }
        }
    }

    private fun applyToBattle(event: BattleStartedEvent.Pre) {
        val battle = event.battle
        val now = System.currentTimeMillis()
        for (actor in battle.actors.filterIsInstance<PlayerBattleActor>()) {
            val player = actor.entity as? ServerPlayer ?: continue
            val stash = pendingByPlayer.remove(player.uuid) ?: continue
            val (cap, capturedAt) = stash
            if (now - capturedAt > STASH_TTL_MS) continue
            val originals = mutableMapOf<UUID, Int>()
            for (bp in actor.pokemonList) {
                val mon = bp.effectedPokemon
                if (mon.level > cap) {
                    originals[mon.uuid] = mon.level
                }
            }
            if (originals.isEmpty()) continue
            // Persist restore info BEFORE mutating, so a crash between these two operations
            // can't leave us with downleveled mons and no recovery data. Even if the save tick
            // hits between writeNbt and the level mutation, the NBT is harmless on its own
            // (just sits there until next login, which is a no-op if levels already match).
            writePendingNbt(player, originals)
            for (bp in actor.pokemonList) {
                val mon = bp.effectedPokemon
                val original = originals[mon.uuid] ?: continue
                mon.level = cap
                CobblemonBridge.logger.debug(
                    "Gym downlevel: {}'s {} L{} → L{}",
                    player.gameProfile.name, mon.species.name, original, cap,
                )
            }
        }
    }

    private fun restoreActors(actors: List<BattleActor>) {
        for (actor in actors.filterIsInstance<PlayerBattleActor>()) {
            val player = actor.entity as? ServerPlayer ?: continue
            restoreFromNbt(player)
        }
    }

    /** Reads the pending-restore NBT, restores any mons in the player's party whose level
     *  doesn't match, clears the NBT. Returns the number of mons whose level was changed. */
    private fun restoreFromNbt(player: ServerPlayer): Int {
        val originals = loadAndClearPendingNbt(player) ?: return 0
        var restored = 0
        for (mon in player.party()) {
            val original = originals[mon.uuid] ?: continue
            if (mon.level != original) {
                mon.level = original
                restored++
                CobblemonBridge.logger.debug(
                    "Gym restore: {}'s {} → L{}",
                    player.gameProfile.name, mon.species.name, original,
                )
            }
        }
        return restored
    }

    // ─── NBT plumbing ──────────────────────────────────────────────────────

    private fun writePendingNbt(player: ServerPlayer, originals: Map<UUID, Int>) {
        val tag = CompoundTag()
        originals.forEach { (uuid, lvl) -> tag.putInt(uuid.toString(), lvl) }
        player.persistentData.put(PENDING_NBT_KEY, tag)
    }

    private fun loadAndClearPendingNbt(player: ServerPlayer): Map<UUID, Int>? {
        val data = player.persistentData
        if (!data.contains(PENDING_NBT_KEY)) return null
        val tag = data.getCompound(PENDING_NBT_KEY)
        val out = mutableMapOf<UUID, Int>()
        for (key in tag.allKeys) {
            try {
                out[UUID.fromString(key)] = tag.getInt(key)
            } catch (_: IllegalArgumentException) { /* malformed key; skip */ }
        }
        data.remove(PENDING_NBT_KEY)
        return out.ifEmpty { null }
    }
}
