package com.cobblemonbridge.quests

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Awards [ADVANCEMENT_ID] once the player has marked [THRESHOLD] species as `CAUGHT` in their
 * Pokédex. Side quest — branches off `server:catch_pokemon` ("Gotta Catch One") but is NOT
 * in the linear quest chain, doesn't appear in the HUD ticker, and isn't blocking for any
 * downstream quest. The reward function's `tellraw` is prefixed with `[Side Quest Complete]`
 * so it visually separates from main-line completions.
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
    private val warnedOnce = AtomicBoolean(false)

    fun registerEvents() {
        CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe(Priority.NORMAL) { event ->
            // `Post.getPlayerUUID()` gives us the UUID; resolve to a live ServerPlayer via the
            // current server. In practice POKEDEX_DATA_CHANGED_POST only fires for online
            // players, so this lookup should always succeed.
            val player = currentServer()?.playerList?.getPlayer(event.playerUUID) ?: return@subscribe
            checkAndAward(player)
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
        if (count < THRESHOLD) return
        val awarded = QuestAdvancements.award(player, ADVANCEMENT_ID, criterion = "done")
        if (awarded) {
            CobblemonBridge.logger.info(
                "pokedex: awarded {} to {} at count={}",
                ADVANCEMENT_ID, player.gameProfile.name, count,
            )
        }
    }

    private fun currentServer(): net.minecraft.server.MinecraftServer? =
        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
}
