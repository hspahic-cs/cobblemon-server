package com.cobblemonbridge.breeding

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.eggs.BredTagHook

/**
 * Breeding restriction: **the parents and the child of a breeding can't be traded.**
 *
 * The child side is already handled — [BredTagHook] tags every hatched Pokémon `bred`, and the trade
 * gates ([com.cobblemonbridge.wild.TradeCapHook], [com.cobblemonbridge.trade.TradeManager]) refuse
 * bred mons. This hook covers the **parent** side: when an egg is collected, both parents are tagged
 * `bred_parent`, which the trade gates also refuse (via [BredTagHook.isTradeLocked]).
 *
 * This replaces the earlier Original-Trainer gate (which cancelled the egg if you weren't the OT of a
 * parent). Breeding itself is no longer restricted — instead, breeding "taints" the parents and the
 * offspring as untradeable, which is what closes the breed-rare-mons-and-sell-them loop.
 *
 * Note: a Ditto used as a parent is tagged too (the rule is about trading, not breeding eligibility).
 * Tagging is on the live parent Pokémon at [CollectEggEvent] time, so it persists with the mon.
 */
object BreedingTradeLockHook {

    fun registerEvents() {
        CobblemonEvents.COLLECT_EGG.subscribe(Priority.NORMAL) { event ->
            BredTagHook.markBreedingParent(event.maleParent)
            BredTagHook.markBreedingParent(event.femaleParent)
            CobblemonBridge.logger.debug(
                "breeding-trade-lock: tagged parents {} + {} as non-tradeable (collected by {})",
                event.maleParent.species.name, event.femaleParent.species.name,
                event.player.gameProfile.name,
            )
        }
    }
}
