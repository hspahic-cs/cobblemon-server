package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/starter")

/**
 * Where the *eligible* half of starter selection comes from — which species exist to be bought at
 * all, before any of them has a price.
 *
 * This is an interface and not a file reader because the datapack-loading convention for this module
 * is owned elsewhere; the selection code must not grow a second, incompatible one. It is also what
 * makes selection testable: every determinism and budget test depends on holding the pool fixed,
 * which is impossible if it is read from disk at call time.
 *
 * **The baseline pool is mandatory, not a fallback** (§2.15). A player who has caught nothing must
 * still be handed a real catalogue, because that player is precisely the one meeting the mode for the
 * first time. An implementation that returns an empty baseline does not narrow the catalogue — for a
 * new player it deletes it, and [StarterCatalogueFactory] logs that at ERROR rather than quietly
 * starting a run nobody can pick a team for.
 *
 * Note what is **not** here any more: how many species to show. Under §2.13's budget the catalogue is
 * everything eligible, and what limits a team is the point total rather than a shown-list size. A
 * sampled shortlist would reintroduce exactly the reroll surface the budget removed.
 */
fun interface StarterPoolSource {

    /**
     * Species offered to every player regardless of Pokédex. Must not be empty; see above.
     *
     * Returned as raw species ids rather than resolved species so this stays callable without a
     * booted server. Whether every id names a species that actually exists is the loader's problem —
     * a typo here should be reported when the pack loads, where an op can see it, not silently narrow
     * someone's catalogue at run start.
     */
    fun baselinePool(): Collection<ResourceLocation>
}

/**
 * Stand-in configuration so the selection path is exercisable before a pool loader lands.
 *
 * **Every value here is a placeholder, not a design decision.** The baseline pool contents are an
 * open question in the plan (§5) and belong to the server owner. Three Kanto starters are here
 * because they are the least surprising thing that can occupy the slot; the loader is expected to
 * replace this wholesale rather than extend it.
 *
 * Worth knowing while it is in place: at derived prices ([DerivedStarterCost]) these three cost 3
 * each, so a 10-point budget buys all three with a point spare. That is a property of the
 * placeholder, not of the design.
 */
object PlaceholderStarterPoolSource : StarterPoolSource {

    /** PLACEHOLDER — see the class comment. Awaiting the server owner's baseline pool. */
    private val PLACEHOLDER_BASELINE = listOf(
        ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur"),
        ResourceLocation.fromNamespaceAndPath("cobblemon", "charmander"),
        ResourceLocation.fromNamespaceAndPath("cobblemon", "squirtle"),
    )

    override fun baselinePool(): Collection<ResourceLocation> = PLACEHOLDER_BASELINE
}

/**
 * A species nobody may start with, at any price (§2.13).
 *
 * ### Why this is a gate and not a very high cost
 *
 * A price says "expensive"; a budget makes expensive things reachable by saving. §2.13 excludes
 * legendaries *outright*, so pricing them at 10 would still hand a player a solo Rayquaza the moment
 * they spent nothing else, and pricing them at 11 would be a ban wearing a number — invisible in the
 * catalogue, silently defeated by any future budget increase or candy discount. Eligibility is the
 * honest place for a rule that has no price.
 *
 * Takes a species id and nothing else, for [StarterCostSource]'s reason: a ban that could see the
 * player is a ban somebody eventually makes conditional on the Pokédex.
 */
fun interface StarterExclusion {

    fun isExcluded(species: ResourceLocation): Boolean

    companion object {
        /** Nothing excluded. For tests that are about arithmetic rather than about the ban. */
        val None = StarterExclusion { false }
    }
}

/**
 * The species labels this server reads as "not startable", or null if the species is unknown here.
 *
 * Split out so [LabelStarterExclusion] can be tested without a booted server — the label *set* is
 * the decision, and the lookup is Cobblemon's.
 */
fun interface SpeciesLabels {
    fun labelsOf(species: ResourceLocation): Set<String>?
}

/** Reads Cobblemon's own species labels. */
object CobblemonSpeciesLabels : SpeciesLabels {
    override fun labelsOf(species: ResourceLocation): Set<String>? =
        runCatching { PokemonSpecies.getByIdentifier(species)?.labels }.getOrNull()
}

/**
 * §2.13's legendary ban, read off Cobblemon's species labels.
 *
 * ### Why the label list is wider than "legendary"
 *
 * Cobblemon's own `Pokemon.isLegendary()` flags only the `legendary` label. Mythicals carry
 * `mythical` and nothing else — Mew, Celebi, Darkrai, and Arceus with every one of its plate forms —
 * and Paradox species carry `paradox`. None of those set `legendary`, so a ban written against
 * Cobblemon's helper would let Arceus through while stopping Zapdos, which is not a rule anyone
 * meant. Ultra Beasts are in for the same reason: §2.13's argument is "too strong at any price", and
 * that argument does not stop at a label boundary Game Freak drew for flavour.
 *
 * Kept as a constant so widening or narrowing the ban is one edit in one place, and so the list is
 * readable next to the reason for it.
 *
 * ### An unknown species is excluded, not admitted
 *
 * If the species does not resolve, its labels cannot be read and the ban cannot be evaluated. Failing
 * open would mean a species that this server cannot describe is nonetheless startable, which is the
 * one direction that can leak a legendary through. It costs nothing to fail closed: an unresolvable
 * species could not have been created for the run anyway ([StarterFactory.create] rejects it), so all
 * that changes is where the player is told, and here is earlier.
 */
class LabelStarterExclusion(
    private val labels: SpeciesLabels,
    private val excluded: Set<String> = EXCLUDED_LABELS,
) : StarterExclusion {

    override fun isExcluded(species: ResourceLocation): Boolean {
        val found = labels.labelsOf(species)
        if (found == null) {
            log.warn("roguelite: starter pool names unknown species '{}' — excluded, since its labels cannot be checked", species)
            return true
        }
        return found.any { label -> excluded.any { it.equals(label, ignoreCase = true) } }
    }

    companion object {
        /** `ultrabeast` is listed alongside `ultra_beast` because both spellings appear in the wild. */
        val EXCLUDED_LABELS = setOf("legendary", "mythical", "paradox", "ultra_beast", "ultrabeast")
    }
}

/** The shipped exclusion: [LabelStarterExclusion] over Cobblemon's species data. */
object LegendaryStarterExclusion : StarterExclusion by LabelStarterExclusion(CobblemonSpeciesLabels)
