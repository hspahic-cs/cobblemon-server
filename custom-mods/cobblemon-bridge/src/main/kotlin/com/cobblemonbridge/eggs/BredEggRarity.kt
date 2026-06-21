package com.cobblemonbridge.eggs

import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemonbridge.CobblemonBridge

/**
 * Hatch-time for a **bred** egg, derived from how rare its offspring species is in the wild.
 *
 * Rarity = the species' *rarest* spawn bucket in Cobblemon's live world spawn pool (so it reflects
 * our spawn-nerf datapacks automatically), mapped to:
 *   common 10m · uncommon 15m · rare 20m · ultra-rare 30m.
 * Species that don't spawn in the wild at all (crate/bred-only) fall back to [DEFAULT_SECONDS].
 *
 * [OVERRIDE_ULTRA] force-maps specific families to ultra-rare (30m) regardless of their bucket —
 * the pseudo-legendary lines and every gen starter line, which are valuable here even when their
 * base form's spawn bucket is generous. Edit that set to tune; names are normalised (lowercased,
 * non-alphanumerics stripped) so "Jangmo-o" / "jangmo_o" / "jangmoo" all match.
 */
object BredEggRarity {

    private const val MIN = 10 * 60       // common
    private const val UNCOMMON = 15 * 60
    private const val RARE = 20 * 60
    private const val ULTRA = 30 * 60     // ultra-rare (the 30-min cap)
    private const val DEFAULT_SECONDS = UNCOMMON  // no wild spawn → middle of the road

    private val BUCKET_SECONDS = mapOf(
        "common" to MIN,
        "uncommon" to UNCOMMON,
        "rare" to RARE,
        "ultra-rare" to ULTRA,
        "ultra_rare" to ULTRA,
    )

    /** Lower index = rarer. Used to pick a species' *rarest* bucket across its spawn entries. */
    private val RARITY_ORDER = listOf("ultra-rare", "ultra_rare", "rare", "uncommon", "common")

    /** Families forced to ultra-rare (30m). Full evolution lines; eggs are base forms but the whole
     *  line is listed for robustness. Normalised on load via [norm]. */
    private val OVERRIDE_ULTRA: Set<String> = buildSet {
        // Pseudo-legendary lines
        addAll(listOf(
            "dratini", "dragonair", "dragonite",
            "larvitar", "pupitar", "tyranitar",
            "bagon", "shelgon", "salamence",
            "beldum", "metang", "metagross",
            "gible", "gabite", "garchomp",
            "deino", "zweilous", "hydreigon",
            "goomy", "sliggoo", "goodra",
            "jangmo-o", "hakamo-o", "kommo-o",
            "dreepy", "drakloak", "dragapult",
            "frigibax", "arctibax", "baxcalibur",
        ))
        // Gen 1–9 starter lines
        addAll(listOf(
            "bulbasaur", "ivysaur", "venusaur", "charmander", "charmeleon", "charizard",
            "squirtle", "wartortle", "blastoise",
            "chikorita", "bayleef", "meganium", "cyndaquil", "quilava", "typhlosion",
            "totodile", "croconaw", "feraligatr",
            "treecko", "grovyle", "sceptile", "torchic", "combusken", "blaziken",
            "mudkip", "marshtomp", "swampert",
            "turtwig", "grotle", "torterra", "chimchar", "monferno", "infernape",
            "piplup", "prinplup", "empoleon",
            "snivy", "servine", "serperior", "tepig", "pignite", "emboar",
            "oshawott", "dewott", "samurott",
            "chespin", "quilladin", "chesnaught", "fennekin", "braixen", "delphox",
            "froakie", "frogadier", "greninja",
            "rowlet", "dartrix", "decidueye", "litten", "torracat", "incineroar",
            "popplio", "brionne", "primarina",
            "grookey", "thwackey", "rillaboom", "scorbunny", "raboot", "cinderace",
            "sobble", "drizzile", "inteleon",
            "sprigatito", "floragato", "meowscarada", "fuecoco", "crocalor", "skeledirge",
            "quaxly", "quaxwell", "quaquaval",
        ))
    }.mapTo(HashSet()) { norm(it) }

    /** Lazily-built species → rarest-bucket map from the live world spawn pool. */
    @Volatile private var speciesBucket: Map<String, String>? = null

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private fun buildBucketMap(): Map<String, String> {
        val rank = RARITY_ORDER.withIndex().associate { (i, name) -> name to i }
        val out = HashMap<String, String>()
        try {
            for (detail in CobblemonSpawnPools.WORLD_SPAWN_POOL.details) {
                if (detail !is PokemonSpawnDetail) continue
                val species = detail.pokemon.species?.let { norm(it) } ?: continue
                val bucket = detail.bucket.name.lowercase()
                val cur = out[species]
                // Keep the rarest (lowest-rank) bucket seen for this species.
                if (cur == null || (rank[bucket] ?: 99) < (rank[cur] ?: 99)) out[species] = bucket
            }
        } catch (e: Throwable) {
            CobblemonBridge.logger.warn("BredEggRarity: failed to read world spawn pool: {}", e.message)
        }
        CobblemonBridge.logger.info("BredEggRarity: indexed {} species from the world spawn pool", out.size)
        return out
    }

    /** Hatch seconds for a bred egg of [speciesName] (any casing). */
    fun secondsFor(speciesName: String?): Int {
        if (speciesName == null) return DEFAULT_SECONDS
        val key = norm(speciesName)
        if (key in OVERRIDE_ULTRA) return ULTRA
        val map = speciesBucket ?: buildBucketMap().also { speciesBucket = it }
        val bucket = map[key] ?: return DEFAULT_SECONDS
        return BUCKET_SECONDS[bucket] ?: DEFAULT_SECONDS
    }
}
