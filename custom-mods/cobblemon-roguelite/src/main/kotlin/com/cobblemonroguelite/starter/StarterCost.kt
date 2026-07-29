package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemonroguelite.data.starter.StarterCostTables
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/starter")

/**
 * What one species costs out of a run's starting budget (§2.13).
 *
 * ### The signature is the point, and it is inherited
 *
 * This takes a species id and nothing else — no player, no Pokédex, no flag saying whether the
 * species arrived from the baseline pool or from an unlock. That is the same constraint the old
 * offer weighting carried, kept for the same reason: §2.15 lets the Pokédex decide *which* species a
 * player may start with and forbids it from deciding *how strong* the start is. Under a budget the
 * price **is** the power statement, so a price that could see the player's dex would be exactly the
 * failure §2.15 names — catch a pseudo-legendary on the server, get it discounted at wave 1.
 *
 * Per-player discounts are a real feature and they do not go here. Candy-bought cost reductions
 * (§2.15) are applied on top, through [StarterProgression], whose input is what the player did
 * *inside runs* rather than what they caught on the server. Two seams, because they are two
 * different sources of authority, and merging them would lose the distinction permanently.
 *
 * ### Null means unpriced, and unpriced is loud
 *
 * Returning null says "I have no price for this", not "this is free". [StarterCatalogueFactory] drops
 * an unpriced species from the catalogue and logs it at ERROR: a species that fell through to zero
 * would be a free pick, and a free pick in a budget system is not a small bug — it is the whole
 * mechanic switched off for that species, silently, for as long as nobody notices.
 */
fun interface StarterCostSource {

    /** Points [species] costs, or null if this source has no price for it. Never zero or negative. */
    fun costOf(species: ResourceLocation): Int?
}

/**
 * First source that has a price wins.
 *
 * The shipped composition is [DefaultStarterCosts]: the datapack table first, the derived default
 * behind it. That ordering is §2.7's licensing split expressed as code — our server drops in the
 * transcribed table and it takes precedence everywhere it has an opinion; a published build has no
 * such table and every species falls through to a number this mod computed itself. Neither build has
 * a branch for "am I the private one", which is what keeps the private data out of the jar by
 * construction rather than by remembering.
 */
class LayeredStarterCostSource(private val layers: List<StarterCostSource>) : StarterCostSource {

    constructor(vararg layers: StarterCostSource) : this(layers.toList())

    override fun costOf(species: ResourceLocation): Int? =
        layers.firstNotNullOfOrNull { it.costOf(species) }
}

/** A fixed table. For tests, and for a server that wants to price a handful of species in code. */
class FixedStarterCostSource(private val costs: Map<ResourceLocation, Int>) : StarterCostSource {
    override fun costOf(species: ResourceLocation): Int? = costs[species]
}

/**
 * The species' total base stats, or null if this server has never heard of it.
 *
 * An interface for the same reason [StarterPoolSource] is one: [PokemonSpecies] needs a booted
 * server, and the band arithmetic in [DerivedStarterCost] is the part worth testing.
 */
fun interface SpeciesBaseStatTotal {
    fun baseStatTotal(species: ResourceLocation): Int?
}

/**
 * Reads Cobblemon's own species data.
 *
 * `getByIdentifier` rather than `getByName`: the latter forces the `cobblemon` namespace, so an
 * addon species would price as unknown here and then resolve perfectly well when the run tried to
 * create it — see [StarterFactory.create], which makes the same distinction.
 */
object CobblemonBaseStatTotal : SpeciesBaseStatTotal {

    override fun baseStatTotal(species: ResourceLocation): Int? {
        val found = runCatching { PokemonSpecies.getByIdentifier(species) }.getOrNull() ?: return null
        // Only the six permanent stats. `baseStats` is keyed by `Stat`, and accuracy/evasion are
        // `Stat`s too — summing the map wholesale would quietly add battle-only entries on any
        // species datapack that set them.
        return STAT_ORDER.sumOf { found.baseStats[it] ?: 0 }.takeIf { it > 0 }
    }

    /**
     * The six stats a base stat total is made of, in a fixed order.
     *
     * Written out rather than taken from `Stats.PERMANENT` because that is a `Set` and this is a sum
     * that also seeds the IV roll ([StarterIvRoll]) — an iteration order that shifted with a
     * Cobblemon version would silently re-roll every starter for a given seed.
     */
    val STAT_ORDER: List<Stats> = listOf(
        Stats.HP,
        Stats.ATTACK,
        Stats.DEFENCE,
        Stats.SPECIAL_ATTACK,
        Stats.SPECIAL_DEFENCE,
        Stats.SPEED,
    )
}

/**
 * The cost a species gets when nothing has priced it — **a stand-in, not a balance statement**.
 *
 * ### Why this exists at all
 *
 * §2.7's licensing split: PokéRogue's per-species costs are *their data*, so they live in a
 * server-side datapack and are never shipped. A published build therefore starts with no prices at
 * all, and a budget system with no prices is not a degraded mode — it does not run. This is what
 * fills that gap, and it is deliberately something this mod can derive on its own from data
 * Cobblemon already ships.
 *
 * ### What it actually measures, and where it is wrong
 *
 * Base stat total, banded. That is honest about *what a species is* and says nothing about what it
 * does — Speed Boost, Regenerator, a signature move, a good typing are all invisible here, and those
 * are precisely what a real price is paid for. It is also blind to evolution: it prices Bulbasaur by
 * Bulbasaur, where a real table prices the *line*, because a run levels. So first-stage species come
 * out cheap and fully-evolved ones expensive, which is defensible for a dex-gated pool (most of what
 * a player unlocks by catching is already evolved) and is simply wrong for a baseline pool of
 * starters.
 *
 * The bands are set so the cheapest species is 3 and the dearest is 7, which puts a 10-point budget
 * at two or three Pokémon — the range §2.13 states. That is the one property here chosen on purpose;
 * every individual number is a placeholder. **Replace this with a table before calling anything
 * balanced.**
 */
object DerivedStarterCost {

    /**
     * Upper bound of each band, in ascending order, with the cost that band pays. The last entry's
     * bound is deliberately `Int.MAX_VALUE` so there is no species this cannot answer for — a hole
     * here would become an unpriced species, and an unpriced species is a run someone cannot start.
     */
    private val BANDS: List<Pair<Int, Int>> = listOf(
        399 to 3,
        469 to 4,
        519 to 5,
        579 to 6,
        Int.MAX_VALUE to 7,
    )

    /** Cheapest and dearest a derived price can be. Exposed so tests pin the range, not the bands. */
    const val MIN = 3
    const val MAX = 7

    fun fromBaseStatTotal(baseStatTotal: Int): Int =
        BANDS.first { baseStatTotal <= it.first }.second
}

/** [DerivedStarterCost] as a source, reading base stats from [stats]. */
class DerivedStarterCostSource(private val stats: SpeciesBaseStatTotal) : StarterCostSource {

    override fun costOf(species: ResourceLocation): Int? =
        stats.baseStatTotal(species)?.let(DerivedStarterCost::fromBaseStatTotal)
}

/**
 * The shipped cost source: the datapack table, then the derived default.
 *
 * The datapack half is reached through [StarterCostTables] rather than being given to this object,
 * so that a `/reload` is picked up on the next lookup with nothing to re-wire — the registry swaps
 * its own contents (see [com.cobblemonroguelite.data.RogueliteDataRegistry]) and this reads through
 * to whatever is there now.
 */
object DefaultStarterCosts : StarterCostSource {

    private val delegate = LayeredStarterCostSource(
        StarterCostSource { StarterCostTables.costOf(it) },
        DerivedStarterCostSource(CobblemonBaseStatTotal),
    )

    override fun costOf(species: ResourceLocation): Int? = delegate.costOf(species)
}

/** Shared by the catalogue and the loader so "what is a legal price" is stated once. */
internal fun invalidCostReason(cost: Int): String? = when {
    cost < 1 -> "must be at least 1, was $cost — a free starter is not a price"
    else -> null
}

internal fun logUnpriced(species: ResourceLocation) {
    // ERROR, not WARN. A species with no price is a species no player can pick, and the only place
    // that shows up in play is somebody's catalogue being one shorter than they expected.
    log.error(
        "roguelite: no starter cost for '{}' and none could be derived — it is excluded from every " +
            "starter catalogue; price it in a starter_costs table",
        species,
    )
}
