package com.cobblemonbridge.eggs

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonbridge.CobblemonBridge
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Tags freshly-hatched Pokémon with `cobblemon_bridge:bred=true` in their persistent NBT,
 * EXCEPT when the source egg came from `/gacha giveegg` (i.e. a crate/quest reward). Used
 * downstream by [com.cobblemonbridge.wild.TradeCapHook] and
 * [com.cobblemonbridge.trade.TradeManager] to refuse trades of bred Pokémon.
 *
 * Detection: Cobreeding's `PokemonEgg.inventoryTick` synchronously calls `hatchEgg(player,
 * props)` which then fires `CobblemonEvents.HATCH_EGG_POST` (verified against 2.2.1 bytecode).
 * [com.cobblemonbridge.mixin.PokemonEggMixin] injects at the `hatchEgg` call site, reads the
 * egg ItemStack's `custom_data.cobblemongacha_tier` (set by cobblemon-gacha's RewardGranter),
 * and on hit calls [markGachaHatch]. The HATCH_EGG_POST subscriber consumes the marker on the
 * same tick and skips the bred tag.
 *
 * Legacy mons hatched before this hook landed are NOT tagged retroactively — they remain
 * tradeable. This is by design; backfilling from heuristics is fragile.
 */
object BredTagHook {

    const val TAG_KEY = "cobblemon_bridge:bred"

    /**
     * Player UUID → server tick on which the mixin observed a gacha-tier marker on the egg
     * being hatched. The HATCH_EGG_POST subscriber removes the entry; entries older than
     * 1 tick are treated as stale and ignored (self-heals if HATCH_EGG_PRE gets cancelled and
     * the marker would otherwise leak to the player's next hatch).
     */
    private val gachaHatchTicks: ConcurrentHashMap<UUID, Long> = ConcurrentHashMap()

    @JvmStatic
    fun markGachaHatch(uuid: UUID, tickCount: Int) {
        gachaHatchTicks[uuid] = tickCount.toLong()
    }

    fun registerEvents() {
        CobblemonEvents.HATCH_EGG_POST.subscribe(Priority.NORMAL) { event ->
            val player = event.player
            val markedTick = gachaHatchTicks.remove(player.uuid)
            val currentTick = player.server.tickCount.toLong()
            val isGachaHatch = markedTick != null && abs(currentTick - markedTick) <= 1L
            if (isGachaHatch) {
                CobblemonBridge.logger.debug(
                    "Skipped bred tag for gacha-hatched {} (uuid={}, player={})",
                    event.pokemon.species.name, event.pokemon.uuid, player.uuid,
                )
                return@subscribe
            }
            event.pokemon.persistentData.putBoolean(TAG_KEY, true)
            CobblemonBridge.logger.debug(
                "Tagged hatched {} (uuid={}) as bred",
                event.pokemon.species.name, event.pokemon.uuid,
            )
        }
    }

    fun isBred(pokemon: Pokemon): Boolean =
        pokemon.persistentData.getBoolean(TAG_KEY)
}
