package com.cobblemongacha.data

/**
 * Per-player gacha record. `lastLoginGrantDate` and `lastRankedGrantDate` are `LocalDate.toString()`
 * values (yyyy-MM-dd) so each is a single field and they compare with `!=`. `null` means the player
 * has never received that grant.
 */
data class PlayerGachaData(
    var name: String,
    var lastLoginGrantDate: String? = null,
    var lastRankedGrantDate: String? = null,
    /**
     * One-time welcome grant (1 Rare Key + 1 Pokémon Key). Set `true` the first time it is
     * granted and NEVER reset — so a player can never receive it twice. Defaults `false`, and
     * existing records (no such key in players.json) deserialize to `false`, so every current
     * player receives it exactly once on their next login.
     */
    var grantedWelcomeKeys: Boolean = false,
)
