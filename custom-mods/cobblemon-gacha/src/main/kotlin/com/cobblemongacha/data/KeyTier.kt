package com.cobblemongacha.data

/**
 * Lootbox tiers. Each tier has a key (vanilla item + custom_data tag) and one configured crate
 * coord. The order matters: higher ordinal = rarer. `POKEMON` is a special egg-only crate; it
 * sits last and is outside the common→ultra rarity ladder.
 */
enum class KeyTier(val key: String, val displayName: String) {
    COMMON("common", "Common"),
    RARE("rare", "Rare"),
    ULTRA("ultra", "Ultra"),
    POKEMON("pokemon", "Pokémon");

    companion object {
        fun fromKey(k: String): KeyTier? = entries.firstOrNull { it.key == k.lowercase() }
    }
}
