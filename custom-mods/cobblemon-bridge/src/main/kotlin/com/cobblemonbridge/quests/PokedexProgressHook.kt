package com.cobblemonbridge.quests

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Awards [ADVANCEMENT_ID] (Centurion) once the player has marked [THRESHOLD] species as `CAUGHT`
 * in their Pokédex, and [ADVANCEMENT_300_ID] (Master Collector — a Master Ball) at [THRESHOLD_300].
 * Side quests — they branch off `server:catch_pokemon` ("Gotta Catch One") but are NOT in the
 * linear quest chain, don't appear in the HUD ticker, and aren't blocking for any downstream
 * quest. The reward functions' `tellraw` is prefixed with `[Side Quest Complete]` so it visually
 * separates from main-line completions.
 *
 * Trigger: `CobblemonEvents.POKEDEX_DATA_CHANGED_POST` — fires on any update to the player's
 * dex (new species seen, captured, etc.). We re-count caught species via reflection into
 * `Cobblemon.playerDataManager.getPokedexData(player).speciesRecords` and award if the
 * threshold is crossed. The advancement system handles dedup, so re-awarding is a no-op.
 *
 * Reflection (not compile-time linked because the Cobblemon Pokédex API surface has shifted
 * between minor versions and we want this to degrade silently if it moves again):
 * ```
 *   Cobblemon.playerDataManager: PlayerInstancedDataStoreManager
 *     .getPokedexData(ServerPlayer): PokedexManager
 *     .getSpeciesRecords(): Map<ResourceLocation, SpeciesDexRecord>
 *   SpeciesDexRecord.getKnowledge(): PokedexEntryProgress  // enum: NONE / ENCOUNTERED / CAUGHT
 * ```
 */
object PokedexProgressHook {

    private const val THRESHOLD = 100
    private const val ADVANCEMENT_ID = "server:reach_pokedex_100"
    /** Follow-up to Centurion: 300 caught species. Its Master Ball reward is granted by the
     *  reward mcfunction (a vanilla `give` is reliable for a Cobblemon item, unlike the PokéNav),
     *  so this hook only needs to award the advancement. */
    private const val THRESHOLD_300 = 300
    private const val ADVANCEMENT_300_ID = "server:reach_pokedex_300"
    /** Cobblenav registers ONLY colored variants as concrete items (no base `pokenav_item`).
     *  `pokenav_item_red` was confirmed by ops as a valid id that works with `/give`. Any
     *  other color would also work — red is the player-facing default. */
    private const val POKENAV_ITEM_ID = "cobblenav:pokenav_item_red"

    /** Persistent NBT key on the player marking that the Centurion PokéNav has been granted.
     *  Idempotency lives here, not on the advancement — the advancement was completed for many
     *  players before the reward changed from Master Ball + Ultra Key to PokéNav, and we need
     *  to backfill them on their next dex update. */
    private const val POKENAV_AWARDED_KEY = "cobblemon_bridge:centurion_pokenav_awarded"

    private val warnedOnce = AtomicBoolean(false)
    private val pokenavMissingWarnedOnce = AtomicBoolean(false)

    fun registerEvents() {
        CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe(Priority.NORMAL) { event ->
            // `Post.getPlayerUUID()` gives us the UUID; resolve to a live ServerPlayer via the
            // current server. In practice POKEDEX_DATA_CHANGED_POST only fires for online
            // players, so this lookup should always succeed.
            val player = currentServer()?.playerList?.getPlayer(event.playerUUID) ?: return@subscribe
            checkAndAward(player)
        }
    }

    /**
     * Carry the "PokéNav already granted" flag across a respawn. NeoForge does NOT copy a player's
     * top-level [ServerPlayer.persistentData] when a new player entity is created on death, so
     * without this the flag is wiped on every death and the next Pokédex update re-grants the
     * PokéNav — the duplicate-item bug. (Registered on the NeoForge event bus in CobblemonBridge.)
     */
    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        val newPlayer = event.entity as? ServerPlayer ?: return
        if (event.original.persistentData.getBoolean(POKENAV_AWARDED_KEY)) {
            newPlayer.persistentData.putBoolean(POKENAV_AWARDED_KEY, true)
        }
    }

    /** Walks the player's pokedex record map + counts entries with knowledge == CAUGHT.
     *  Returns 0 on any reflection failure. */
    fun caughtCount(player: ServerPlayer): Int = try {
        val pdm = Cobblemon.playerDataManager
        val pdxMethod = pdm.javaClass.getMethod("getPokedexData", ServerPlayer::class.java)
        val pokedex = pdxMethod.invoke(pdm, player) ?: return 0
        @Suppress("UNCHECKED_CAST")
        val records = pokedex.javaClass.getMethod("getSpeciesRecords").invoke(pokedex) as? Map<*, *>
            ?: return 0
        records.values.count { rec ->
            val knowledge = rec?.javaClass?.getMethod("getKnowledge")?.invoke(rec) ?: return@count false
            (knowledge as? Enum<*>)?.name == "CAUGHT"
        }
    } catch (e: Throwable) {
        if (warnedOnce.compareAndSet(false, true)) {
            CobblemonBridge.logger.warn("PokedexProgressHook reflection failed: {}", e.message)
        }
        0
    }

    private fun checkAndAward(player: ServerPlayer) {
        val count = caughtCount(player)
        if (count < THRESHOLD) return  // below the first milestone — nothing to do

        // Centurion (100 caught species) — advancement + PokéNav.
        if (QuestAdvancements.award(player, ADVANCEMENT_ID, criterion = "done")) {
            CobblemonBridge.logger.info(
                "pokedex: awarded {} to {} at count={}",
                ADVANCEMENT_ID, player.gameProfile.name, count,
            )
        }
        // Grant the PokéNav directly from Kotlin. The mcfunction give path was unreliable for
        // cobblenav:pokenav_item (silent failures in prod despite the tellraw firing), and any
        // player who completed the advancement before the reward changed from Master Ball +
        // Ultra Key never got a PokéNav at all. Both classes are handled here:
        //   - new completers: advancement fires for the first time, this runs immediately
        //   - retro completers: advancement is already done, this runs on next dex update
        // Idempotency via a per-player persistent NBT flag (see POKENAV_AWARDED_KEY).
        grantPokenavOnce(player)

        // Master Collector (300 caught species) — follow-up to Centurion. We only award the
        // advancement; its reward mcfunction gives the Master Ball, and the advancement system's
        // own dedup makes the grant fire exactly once.
        if (count >= THRESHOLD_300) {
            if (QuestAdvancements.award(player, ADVANCEMENT_300_ID, criterion = "done")) {
                CobblemonBridge.logger.info(
                    "pokedex: awarded {} to {} at count={}",
                    ADVANCEMENT_300_ID, player.gameProfile.name, count,
                )
            }
        }
    }

    private fun grantPokenavOnce(player: ServerPlayer) {
        if (player.persistentData.getBoolean(POKENAV_AWARDED_KEY)) return
        val itemId = ResourceLocation.parse(POKENAV_ITEM_ID)
        val item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null)
        if (item == null) {
            if (pokenavMissingWarnedOnce.compareAndSet(false, true)) {
                CobblemonBridge.logger.warn(
                    "centurion: {} not registered (Cobblenav mod missing?) — skipping grant",
                    POKENAV_ITEM_ID,
                )
            }
            return
        }
        val stack = ItemStack(item, 1)
        val added = player.inventory.add(stack)
        if (!added || !stack.isEmpty) {
            // Inventory full — drop the leftover at the player's feet.
            val drop = ItemEntity(player.level(), player.x, player.y, player.z, stack.copy())
            player.level().addFreshEntity(drop)
            player.sendSystemMessage(Component.literal(
                "§6[Centurion] §fYour PokéNav was dropped at your feet (inventory full)."
            ))
        }
        player.persistentData.putBoolean(POKENAV_AWARDED_KEY, true)
        CobblemonBridge.logger.info(
            "centurion: granted PokéNav to {}", player.gameProfile.name,
        )
    }

    private fun currentServer(): net.minecraft.server.MinecraftServer? =
        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
}
