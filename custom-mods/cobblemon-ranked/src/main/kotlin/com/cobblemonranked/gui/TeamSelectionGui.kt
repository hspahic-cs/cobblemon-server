package com.cobblemonranked.gui

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer
import java.util.function.Consumer

/**
 * Public entry point for the team-selection flow. Preserves the original Fabric-era
 * constructor signature for callers (RankedBattleManager); internally delegates to the
 * vanilla-MenuType-based [TeamSelectionMenuProvider].
 */
class TeamSelectionGui(
    private val player: ServerPlayer,
    private val maxLegendaries: Int,
    private val showRental: Boolean,
    private val onConfirm: Consumer<List<Pokemon>>,
    private val onCancel: Runnable,
    /** Pokémon to pick for this match (6 Singles, 4 Doubles). */
    private val teamSize: Int = TeamSelectionMenu.TEAM_SIZE,
) {
    fun open() {
        val provider = TeamSelectionMenuProvider(
            player = player,
            maxLegendaries = maxLegendaries,
            showRental = showRental,
            onConfirm = { team -> onConfirm.accept(team) },
            onCancel = { onCancel.run() },
            teamSize = teamSize,
        )
        player.openMenu(provider)
    }
}
