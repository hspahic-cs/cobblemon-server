package com.cobblemongacha.config

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.internal.ConfigPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Pokémon-crate pity settings (docs/pokerogue-mode-plan.md §2.45). Authored config — design
 * data the admin tunes, shipped from the repo like the loot tables — persisted as
 * `config/cobblemon-gacha/authored/pity.json`.
 *
 * `pityEvery` is the roll count that triggers the guarantee: every `pityEvery`th pokemon-crate
 * roll draws ONLY from the table's Jackpot-tier entries, re-weighted by `pityWeights` (keyed by
 * entry label, matched against the pokemon table; labels absent from the table are ignored).
 * The counter resets on ANY Jackpot-tier drop — natural or pity — so a lucky jackpot can't be
 * banked alongside a pity one. Pity applies to the pokemon tier only.
 */
data class PityConfig(
    val pityEvery: Int = 10,
    val pityWeights: Map<String, Double> = mapOf(
        "Ultra Rare Pokémon Egg" to 70.0,
        "Shiny Egg" to 22.5,
        "Cosmetic Pokémon Egg" to 7.5,
    ),
) {
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): PityConfig {
            val file = ConfigPaths.authored(configDir, "pity.json")
            if (!file.exists()) {
                val default = PityConfig()
                save(configDir, default)
                return default
            }
            return try {
                val parsed = gson.fromJson(file.readText(), PityConfig::class.java) ?: PityConfig()
                // Gson bypasses constructor defaults for absent fields — guard hand-edited files.
                @Suppress("SENSELESS_COMPARISON")
                if (parsed.pityEvery <= 0 || parsed.pityWeights == null || parsed.pityWeights.isEmpty()) {
                    CobblemonGacha.logger.warn("pity.json missing pityEvery/pityWeights, using defaults")
                    PityConfig()
                } else {
                    parsed
                }
            } catch (e: Exception) {
                CobblemonGacha.logger.error("Failed to load pity config, using defaults", e)
                PityConfig()
            }
        }

        fun save(configDir: Path, config: PityConfig) {
            val file = ConfigPaths.authored(configDir, "pity.json")
            file.parent.createDirectories()
            file.writeText(gson.toJson(config))
        }
    }
}
