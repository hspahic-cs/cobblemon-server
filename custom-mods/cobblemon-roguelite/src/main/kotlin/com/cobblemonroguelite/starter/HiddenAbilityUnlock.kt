package com.cobblemonroguelite.starter

/**
 * Which ability a bought hidden-ability unlock actually grants (§2.27) — decided here, applied by
 * [HiddenAbilityGrant].
 *
 * ### Why the decision is separated from the grant
 *
 * The same split [StarterFactory] makes from [StarterCatalogueFactory], and for the same reason: a
 * `Species`, a `Pokemon` and Cobblemon's ability registry cannot be constructed in a plain JUnit run,
 * so anything that takes one is code that ships having never executed. What decides *which* ability
 * an unlock grants is where every interesting failure lives — a species with no hidden ability, a
 * datapack override naming an ability this server does not have — so it is expressed over
 * [PoolAbility], a flattened copy of the pool, and is tested directly.
 *
 * ### What "hidden ability" means here, given Cobblemon never rolls one
 *
 * Cobblemon defines the concept and leaves it unused: `HiddenAbility.isSatisfiedBy` returns false
 * behind a literal `TODO`, so no Pokémon this server generates ever has one by chance. That is what
 * makes it the right thing for candy to buy (§2.27) — it is a slot the game declares, balanced by
 * the people who balanced the game, and otherwise dead. It also means the *pool* is the only source
 * of truth: there is no aspect to set and no property to hope for, so a hidden entry is identified by
 * its `PotentialAbility` type and read out directly.
 */
data class PoolAbility(
    /** The ability's registry name, e.g. `speedboost`. Not a display name — this module ships no lang file. */
    val name: String,
    /** True for an entry Cobblemon marks `HiddenAbilityType`, i.e. the slot nothing ever rolls. */
    val hidden: Boolean,
)

/**
 * What an unlock resolves to for one species, on this server, with this datapack loaded.
 *
 * Four cases and not a nullable name, because two of the three failures need to be *said* rather than
 * shrugged at: a species with no hidden ability must have the purchase withdrawn from sale (§2.27's
 * whole reason for existing is removing a purchase that silently does nothing), and an override
 * naming an ability that is not installed is an operator's typo that has to reach a log.
 */
sealed interface HiddenAbilityChoice {

    /** Nothing is granted. The two failures both land here from the caller's side. */
    val ability: String? get() = when (this) {
        is FromPool -> entry.name
        is Pinned -> name
        else -> null
    }

    /**
     * Grant [entry], which is entry [index] of the started form's own ability pool.
     *
     * Being in the pool is what lets the ability survive evolution. Cobblemon's `attemptAbilityUpdate`
     * re-derives a non-forced ability from the *new* form's pool at the same priority and index, so a
     * Poliwag granted Swift Swim comes out of a Politoed evolution holding Drizzle — the evolved
     * form's hidden ability, which is what mainline does and what a player expects. Forcing it would
     * pin the pre-evolution's ability onto the evolved form instead.
     */
    data class FromPool(val entry: PoolAbility, val index: Int) : HiddenAbilityChoice

    /**
     * Grant [name], which this server has but the started form's pool does not — only reachable
     * through a datapack override (§2.27's answer to Truant).
     *
     * This one *must* be forced. There is no pool coordinate to attach, so `attemptAbilityUpdate`
     * would find no entry at that priority on the next form change and would quietly replace the
     * ability the player paid for with an ordinary one. Forcing costs the evolution behaviour above,
     * which is the correct trade: an operator who hand-assigned an ability meant that ability.
     */
    data class Pinned(val name: String) : HiddenAbilityChoice

    /**
     * The species declares no hidden ability and no override supplies one, so there is nothing to
     * sell. The purchase is refused rather than taken — see [com.cobblemonroguelite.progression.SpendResult].
     */
    data object None : HiddenAbilityChoice

    /** An override named [name] and no such ability is installed. An operator error, not a player's. */
    data class Unknown(val name: String) : HiddenAbilityChoice
}

/** The pure half of §2.27: given a pool and an override, what does the unlock grant? */
object HiddenAbilityUnlock {

    /**
     * Decide what [pool] plus [override] grants.
     *
     * @param pool the started form's abilities, in pool order.
     * @param override the datapack's hand-assigned ability for this species, or null for "use the
     *   hidden ability". §2.27 requires this: hidden abilities range from Speed Boost to Truant, and
     *   PokéRogue avoids that spread by hand-assigning passives, so the override is what recovers the
     *   judgement we lose by taking the official slot.
     * @param abilityExists whether this server has an ability by that (normalised) name — Cobblemon's
     *   `Abilities.get`, passed in so this stays testable.
     */
    fun choose(
        pool: List<PoolAbility>,
        override: String?,
        abilityExists: (String) -> Boolean,
    ): HiddenAbilityChoice {
        val wanted = override?.let(::normalise)?.takeIf { it.isNotEmpty() }
        if (wanted != null) {
            // Matched against the pool first, so an override that merely *restates* the hidden
            // ability keeps the pool coordinate and therefore keeps the evolution behaviour. Writing
            // a species' own hidden ability into the table is the natural way to document one, and it
            // would be a trap if doing so silently changed how the grant behaves on evolution.
            val index = pool.indexOfFirst { normalise(it.name) == wanted }
            if (index >= 0) return HiddenAbilityChoice.FromPool(pool[index], index)
            return if (abilityExists(wanted)) HiddenAbilityChoice.Pinned(wanted) else HiddenAbilityChoice.Unknown(override)
        }

        // The first hidden entry this server can actually resolve. The existence check is not
        // paranoia: a species file naming an ability no installed mod defines still loads, and the
        // entry it leaves behind would sell an unlock that grants nothing — precisely the failure
        // §2.27 exists to remove, arrived at from the data side instead of the code side.
        val entry = pool.withIndex().firstOrNull { (_, it) -> it.hidden && abilityExists(normalise(it.name)) }
            ?: return HiddenAbilityChoice.None
        return HiddenAbilityChoice.FromPool(entry.value, entry.index)
    }

    /**
     * Ability names as Cobblemon keys them: lowercase, no separators.
     *
     * `Abilities.get` lowercases and nothing else, so `"Speed Boost"` in a datapack would miss a
     * registry that holds `speedboost`. Stripping everything non-alphanumeric means an operator may
     * write the name the way it is printed in the game and still hit the entry — and a table that
     * silently prices an ability nobody can be granted is the expensive kind of typo.
     */
    fun normalise(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
}
