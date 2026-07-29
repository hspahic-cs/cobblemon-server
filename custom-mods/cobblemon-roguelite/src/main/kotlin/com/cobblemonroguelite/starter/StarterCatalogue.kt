package com.cobblemonroguelite.starter

import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/starter")

/** One species a player may buy into their starting team, at the price *they* pay for it. */
data class StarterOption(val species: ResourceLocation, val cost: Int)

/**
 * Everything a player may spend their starting budget on, priced (§2.13).
 *
 * ### Why the whole eligible set, and not a shortlist
 *
 * The superseded design showed a small random offer, which meant the *contents* of the choice were
 * rolled and therefore worth rerolling — the reason that design needed a seed, a persisted one, and a
 * comment about pulling the plug at the selection screen. A budget moves the decision from "what was
 * I shown" to "what can I afford", so there is nothing left to roll: two players with the same
 * unlocks and the same progression see the same catalogue, always, and a disconnect changes nothing.
 * That is a smaller surface, not just a different one.
 *
 * ### What is in here and what deliberately is not
 *
 * Species ids and prices. No rarity band, no "you unlocked this" marker, no note of whether a price
 * came from the datapack or was derived. Provenance of that kind would be read by the selection UI,
 * then by the shop, and by then the Pokédex would be deciding how strong a run is instead of what is
 * in it (§2.15). [unpriced] is the one exception and it is not gameplay: it is an operator fault the
 * command layer has to be able to name.
 *
 * @property budget points this player has to spend. Carried on the catalogue rather than looked up
 *   again at validation time so that the numbers a player was shown and the numbers they are judged
 *   against are provably the same ones.
 * @property options every eligible, priced species, cheapest first and then by id — a stable order so
 *   that two builds of the same catalogue render identically.
 * @property unpriced eligible species that no cost source could price. Excluded from [options]; see
 *   [StarterCostSource] for why they are not free.
 */
data class StarterCatalogue(
    val budget: Int,
    val options: List<StarterOption>,
    val unpriced: List<ResourceLocation> = emptyList(),
) {

    val isEmpty: Boolean get() = options.isEmpty()

    fun costOf(species: ResourceLocation): Int? = options.firstOrNull { it.species == species }?.cost

    fun contains(species: ResourceLocation): Boolean = costOf(species) != null

    /** Cheapest single pick, or null on an empty catalogue. */
    val cheapest: Int? get() = options.minOfOrNull { it.cost }

    /**
     * Options that fit the budget on their own.
     *
     * A catalogue whose every entry costs more than the budget is a startable-looking screen where
     * nothing can be bought, so [StarterCatalogueFactory] refuses it up front rather than letting a
     * player discover it one failed pick at a time.
     */
    fun affordable(): List<StarterOption> = options.filter { it.cost <= budget }
}

/**
 * Builds a player's catalogue: baseline pool ∪ Pokédex unlocks, minus exclusions, priced.
 *
 * ### Why the inputs are four separate objects
 *
 * Each answers a different question and each is allowed to fail differently:
 *
 * - [pool] — "what is always available". Empty is a server misconfiguration.
 * - [unlocks] — "what has this player earned on the server" (§2.15). Empty is a new player, normal.
 * - [exclusion] — "what may nobody have" (§2.13's legendary ban). A rule, not a price.
 * - [costs] — "what does this species cost". Sees a species id and never a player.
 *
 * The union of the first two is taken in one place and produces a plain set of ids; after that line
 * nothing downstream can tell a baseline species from an earned one, which is how §2.15's "unlocking
 * is not power" survives contact with a pricing table. The old design made the same separation
 * against *weighting*; the budget inherits it against *cost*, where it matters more, because under a
 * budget the price is the balance statement rather than a tiebreak.
 *
 * ### Where the player does enter pricing
 *
 * Exactly once, through [StarterProgression.effectiveCost], and the discount it applies comes from
 * candy earned inside runs (§2.15, §2.17) — never from the Pokédex. The result is clamped into
 * `1..baseCost`: a progression that could raise a price would let a player be priced out of a species
 * they used to afford, and a progression that could reach 0 would delete the budget for that species.
 */
class StarterCatalogueFactory(
    private val pool: StarterPoolSource,
    private val unlocks: CaughtSpeciesSource,
    private val costs: StarterCostSource,
    private val budget: Int,
    private val exclusion: StarterExclusion = LegendaryStarterExclusion,
    private val progression: () -> StarterProgression = { StarterProgression.current },
) {

    /**
     * Every species [player] may buy, before pricing.
     *
     * Returns a set, unordered on purpose: eligibility must not be able to depend on whether a
     * species was baseline or earned, and a `List` here would tempt exactly that. Ordering happens
     * once, in [priceFrom], off the species id.
     */
    fun eligibleSpecies(player: UUID): Set<ResourceLocation> {
        val baseline = pool.baselinePool()
        if (baseline.isEmpty()) {
            // Not a warning. §2.15 makes the baseline mandatory because a dex-only pool is worst for
            // the player meeting the mode for the first time, and a server in this state hands a new
            // player nothing at all.
            log.error("roguelite: starter baseline pool is empty — new players will get no catalogue; check the starter config")
        }
        val eligible = baseline.toMutableSet().apply { addAll(unlocks.caughtSpecies(player)) }
        // Removed here rather than filtered during pricing, so that an excluded species is never
        // handed to a cost source at all. §2.13 bans legendaries outright, and the cheapest way to
        // keep that absolute is to make "priced but banned" a state that cannot be represented.
        return eligible.filterNotTo(mutableSetOf(), exclusion::isExcluded)
    }

    fun catalogueFor(player: UUID): StarterCatalogue = priceFrom(player, eligibleSpecies(player))

    /**
     * Pricing on its own, taking the eligible set directly.
     *
     * Split out so pricing is testable without a Pokédex, and — more usefully — so the signature
     * records that the eligible set arrives already decided. Nothing here can add a species.
     */
    fun priceFrom(player: UUID, eligible: Set<ResourceLocation>): StarterCatalogue {
        val progression = progression()
        val options = mutableListOf<StarterOption>()
        val unpriced = mutableListOf<ResourceLocation>()

        // Sorted before pricing, not after. `eligible` arrives from a set union over Cobblemon's
        // Pokédex map, whose iteration order tracks insertion and so differs between a fresh login
        // and a resumed session; sorting first means the catalogue, the log lines and any
        // discount-clamp warning all come out in the same order every time.
        for (species in eligible.sortedBy { it.toString() }) {
            val base = costs.costOf(species)
            if (base == null || invalidCostReason(base) != null) {
                if (base != null) log.error("roguelite: starter cost for '{}' {}", species, invalidCostReason(base))
                else logUnpriced(species)
                unpriced += species
                continue
            }
            options += StarterOption(species, discounted(progression, player, species, base))
        }

        return StarterCatalogue(
            budget = budget,
            // Cheapest first so the affordable end of the list is the end a player reads first; ties
            // fall back to the id, which is what makes the order total rather than merely stable.
            options = options.sortedWith(compareBy({ it.cost }, { it.species.toString() })),
            unpriced = unpriced,
        )
    }

    private fun discounted(
        progression: StarterProgression,
        player: UUID,
        species: ResourceLocation,
        baseCost: Int,
    ): Int {
        val effective = runCatching { progression.effectiveCost(player, species, baseCost) }
            // A progression store that throws must not stop a run starting. Base price is the
            // conservative answer: the player loses a discount they earned, which is visible and
            // fixable, rather than being handed a species free.
            .onFailure { log.error("roguelite: progression failed to price '{}' — using the base cost", species, it) }
            .getOrDefault(baseCost)
        if (effective !in 1..baseCost) {
            log.warn(
                "roguelite: progression returned {} for '{}' against a base cost of {} — clamped; " +
                    "candy may only discount, never raise, and never to free",
                effective, species, baseCost,
            )
        }
        return effective.coerceIn(1, baseCost)
    }
}
