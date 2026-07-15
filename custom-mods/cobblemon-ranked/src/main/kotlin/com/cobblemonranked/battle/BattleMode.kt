package com.cobblemonranked.battle

import com.cobblemon.mod.common.battles.BattleFormat

/**
 * Format of a ranked match: 1v1 Singles (one active Pokémon per side) or 2v2 Doubles (two active).
 *
 * Both modes share the **same** ELO rating — a doubles win/loss moves the same number a singles
 * result would (there is no separate doubles leaderboard). The mode only changes the Showdown
 * battle format and how many Pokémon each player brings/selects:
 *
 *  | Mode    | 1v1 challenge pick | Tournament roster (pre-select) | Tournament per-match pick |
 *  |---------|--------------------|--------------------------------|---------------------------|
 *  | Singles | 6                  | 9                              | 6                         |
 *  | Doubles | 4                  | 6                              | 4                         |
 *
 * [fromId] defaults to [SINGLES] for a missing/unknown token, so commands stay singles unless the
 * player explicitly types `doubles`.
 */
enum class BattleMode(
    val id: String,
    val displayName: String,
    /** Pokémon a player picks for a 1v1 `/challenge` match. */
    val pick1v1: Int,
    /** Pokémon a player pre-selects as their tournament roster (the pool they draw from). */
    val tournamentRoster: Int,
    /** Pokémon a player picks from their roster for each tournament match. */
    val tournamentPick: Int,
) {
    SINGLES("singles", "Singles", pick1v1 = 6, tournamentRoster = 9, tournamentPick = 6),
    DOUBLES("doubles", "Doubles", pick1v1 = 4, tournamentRoster = 6, tournamentPick = 4);

    /** The Cobblemon/Showdown battle format for this mode, at the given adjust-level cap. */
    fun format(adjustLevel: Int): BattleFormat = when (this) {
        SINGLES -> BattleFormat.GEN_9_SINGLES.copy(adjustLevel = adjustLevel)
        DOUBLES -> BattleFormat.GEN_9_DOUBLES.copy(adjustLevel = adjustLevel)
    }

    companion object {
        /** Parse a user token (e.g. from `/challenge x doubles`); unknown/blank → [SINGLES]. */
        fun fromId(token: String?): BattleMode =
            entries.firstOrNull { it.id.equals(token, ignoreCase = true) } ?: SINGLES
    }
}
