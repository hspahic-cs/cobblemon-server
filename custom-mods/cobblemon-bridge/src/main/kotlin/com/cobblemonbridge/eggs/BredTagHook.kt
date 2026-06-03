package com.cobblemonbridge.eggs

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonbridge.CobblemonBridge

/**
 * Tags freshly-hatched Pokémon with `cobblemon_bridge:bred=true` in their persistent NBT.
 * Used downstream by [com.cobblemonbridge.wild.TradeCapHook] and
 * [com.cobblemonbridge.trade.TradeManager] to refuse trades of bred Pokémon.
 *
 * Cobreeding's `PokemonEgg.hatchEgg` fires `CobblemonEvents.HATCH_EGG_POST` after construction
 * (verified against 2.2.1 bytecode), so a single subscriber on that event covers every bred mon
 * regardless of which egg item / breeding path produced it.
 *
 * Legacy mons hatched before this hook landed are NOT tagged retroactively — they remain
 * tradeable. This is by design; backfilling from heuristics (OT-via-egg + Egg ribbon) is
 * fragile and not worth the false-positive risk.
 */
object BredTagHook {

    const val TAG_KEY = "cobblemon_bridge:bred"

    fun registerEvents() {
        CobblemonEvents.HATCH_EGG_POST.subscribe(Priority.NORMAL) { event ->
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
