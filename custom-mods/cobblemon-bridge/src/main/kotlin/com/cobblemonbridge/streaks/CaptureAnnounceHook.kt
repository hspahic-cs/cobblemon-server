package com.cobblemonbridge.streaks

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Shows a clean, rarity-coloured "Captured a <Pokémon>" / "Resurrected a <Pokémon>" line on the
 * acting player's action bar, replacing Cobbled Counter's `(Count/Streak)` broadcasts (which are
 * suppressed for these events via `noBroadcastFor` in `config/cobbled_counter.json`).
 *
 * Covers wild **and** fished catches — both fire `POKEMON_CAPTURED` — plus fossil revives
 * (`FOSSIL_REVIVED`). Goes to the action bar, not chat: captures are frequent, so a per-catch chat
 * line would re-bloat global chat (which is the whole point of this pass). KO and snack counters are
 * intentionally left on Cobbled Counter's action bar — the KO count is actually useful for shiny/HA
 * chaining (KO streaks feed the spawn shiny/hidden boosters).
 *
 * Colour reflects the species' rarity tier; shinies always win and get a ✨ prefix.
 */
object CaptureAnnounceHook {

    fun registerEvents() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.LOW) { event ->
            event.player?.let { announce(it, event.pokemon, "Captured a ") }
        }
        CobblemonEvents.FOSSIL_REVIVED.subscribe(Priority.LOW) { event ->
            event.player?.let { announce(it, event.pokemon, "Resurrected a ") }
        }
    }

    private fun announce(player: ServerPlayer, pokemon: Pokemon, verb: String) {
        val color = rarityColor(pokemon)
        val name = pokemon.species.translatedName.copy()
        val body = if (pokemon.shiny) Component.literal("✨").append(name) else name
        player.displayClientMessage(
            Component.literal(verb).append(body.withStyle(color)),
            /* actionBar = */ true,
        )
    }

    /** Shiny=gold, legendary=gold, mythical=light-purple, paradox=red, ultra-beast=aqua, else white. */
    private fun rarityColor(pokemon: Pokemon): ChatFormatting = when {
        pokemon.shiny -> ChatFormatting.GOLD
        pokemon.isLegendary() -> ChatFormatting.GOLD
        pokemon.isMythical() -> ChatFormatting.LIGHT_PURPLE
        pokemon.species.labels.contains("paradox") -> ChatFormatting.RED
        pokemon.isUltraBeast() -> ChatFormatting.AQUA
        else -> ChatFormatting.WHITE
    }
}
