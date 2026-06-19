package com.cobblemonbridge.eggs

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Sends a clean "Hatched a <Pokémon>" line to the hatching player, with the species name coloured
 * by the egg's rarity tier. Replaces Cobbled Counter's chat broadcast, which we suppress for hatches
 * (`noBroadcastFor: ["HATCH"]` in `config/cobbled_counter.json`) because it tacked on a
 * "(Count N/Streak M)" suffix that implied breeding streaks still exist (they don't — see 0.22.2).
 *
 * The egg's tier (`cobblemongacha_tier` NBT) is only readable off the egg ItemStack, which is gone
 * by the time `HATCH_EGG_POST` fires — so [com.cobblemonbridge.mixin.PokemonEggMixin] captures it at
 * the `hatchEgg(...)` call and stashes it here via [markHatchTier], keyed by player UUID + server
 * tick. We read it back on the next tick (same synchronous hatch chain as [BredTagHook]). Daycare-bred
 * eggs carry no tier, so they hatch as a neutral white name.
 *
 * The line goes only to the hatching player — hatches are not server-wide announcements.
 */
object HatchAnnounceHook {

    /** player UUID -> (server tick at capture, tier string). Tier is "" for bred/daycare eggs. */
    private val pendingTier = ConcurrentHashMap<UUID, Pair<Long, String>>()

    @JvmStatic
    fun markHatchTier(uuid: UUID, tickCount: Int, tier: String) {
        pendingTier[uuid] = tickCount.toLong() to tier
    }

    fun registerEvents() {
        CobblemonEvents.HATCH_EGG_POST.subscribe(Priority.LOW) { event ->
            val player = event.player
            val marked = pendingTier.remove(player.uuid)
            // Only trust a marker from the same synchronous hatch tick (guards against a leaked
            // marker from a cancelled HATCH_EGG_PRE bleeding into the player's next hatch).
            val tier = marked
                ?.takeIf { abs(player.server.tickCount.toLong() - it.first) <= 1L }
                ?.second
                ?: ""
            val name = event.pokemon.species.translatedName.copy().withStyle(tierColor(tier))
            player.sendSystemMessage(Component.literal("Hatched a ").append(name))
        }
    }

    /** Gacha tier palette (Common=gray, Uncommon=green, Rare=blue, Epic=purple, Legendary=gold). */
    private fun tierColor(tier: String): ChatFormatting = when (tier.lowercase()) {
        "common", "beginner" -> ChatFormatting.GRAY
        "uncommon" -> ChatFormatting.GREEN
        "rare" -> ChatFormatting.BLUE
        "ultra" -> ChatFormatting.DARK_PURPLE
        "ultra_rare", "shiny" -> ChatFormatting.GOLD
        else -> ChatFormatting.WHITE // bred / daycare eggs — no gacha tier
    }
}
