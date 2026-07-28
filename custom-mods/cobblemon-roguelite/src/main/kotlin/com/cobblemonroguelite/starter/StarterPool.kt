package com.cobblemonroguelite.starter

import net.minecraft.resources.ResourceLocation

/**
 * Where the starter offer's *configuration* comes from — the baseline pool and how many species an
 * offer shows.
 *
 * This is an interface and not a file reader because the datapack-loading convention for this
 * module is owned elsewhere; the offer code must not grow a second, incompatible one. It is also
 * what makes the offer testable at all: every determinism test below depends on holding the
 * configuration fixed, which is impossible if the configuration is read from disk at call time.
 *
 * **The baseline pool is mandatory, not a fallback** (design decision §2.15). A player who has
 * caught nothing must still be handed a real offer, because that player is precisely the one
 * meeting the mode for the first time. An implementation that returns an empty baseline does not
 * degrade the offer — it deletes it, and [StarterOfferFactory] logs that as an error rather than
 * quietly starting a run with no starter to pick.
 */
interface StarterPoolSource {

    /**
     * Species offered to every player regardless of Pokédex. Must not be empty; see above.
     *
     * Returned as a collection of raw species ids rather than resolved species so this stays
     * callable without a booted server. Whether every id names a species that actually exists is
     * the loader's problem, not the offer's — a typo here should be reported when the pack loads,
     * where an op can see it, not silently narrow someone's offer at run start.
     */
    fun baselinePool(): Collection<ResourceLocation>

    /**
     * How many species one offer shows. Clamped against the eligible set by the caller, since a
     * fresh player can legitimately have fewer eligible species than this asks for.
     */
    fun offerSize(): Int
}

/**
 * Stand-in configuration so the offer path is exercisable before the datapack layer lands.
 *
 * **Every value here is a placeholder, not a design decision.** The baseline pool contents and the
 * offer size are listed as open questions in the plan (§5, "Starter offer") and belong to the
 * server owner. Three Kanto starters are here because they are the least surprising thing that can
 * occupy the slot, not because anything argues for them; the loader is expected to replace this
 * wholesale rather than extend it.
 */
object PlaceholderStarterPoolSource : StarterPoolSource {

    /** PLACEHOLDER — see the class comment. Awaiting the server owner's baseline pool. */
    private val PLACEHOLDER_BASELINE = listOf(
        ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur"),
        ResourceLocation.fromNamespaceAndPath("cobblemon", "charmander"),
        ResourceLocation.fromNamespaceAndPath("cobblemon", "squirtle"),
    )

    /** PLACEHOLDER — see the class comment. Awaiting the server owner's offer size. */
    private const val PLACEHOLDER_OFFER_SIZE = 3

    override fun baselinePool(): Collection<ResourceLocation> = PLACEHOLDER_BASELINE

    override fun offerSize(): Int = PLACEHOLDER_OFFER_SIZE
}

/**
 * How likely each species is to appear in an offer.
 *
 * **The signature is the point.** It takes a species id and nothing else — no player, no Pokédex,
 * no flag saying whether this species arrived via the baseline pool or via an unlock. That is
 * deliberate and load-bearing: §2.15 allows the Pokédex to decide *which* species can appear and
 * forbids it from deciding *how strong* the offer is, and the cheapest way to keep a future
 * weighting system honest is to never hand it the information it would have to misuse. If a
 * weighting table ever needs to say "rarer species appear less often", that is a property of the
 * species, readable from a config table; it is not a property of what the player has caught.
 *
 * Anyone tempted to widen this to `weight(player, species)` is about to make catching a
 * pseudo-legendary on the server hand that pseudo-legendary to the player at wave 1.
 */
fun interface StarterWeighting {

    /** Relative weight of [species]. Zero or negative excludes it from the draw. */
    fun weight(species: ResourceLocation): Int

    companion object {
        /**
         * Every eligible species equally likely. The honest default while the weighting table is an
         * open question — a made-up curve would read as a decision to whoever finds it next.
         */
        val Uniform = StarterWeighting { 1 }
    }
}
