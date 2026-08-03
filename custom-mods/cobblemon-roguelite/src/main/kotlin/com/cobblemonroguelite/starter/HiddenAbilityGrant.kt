package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.abilities.AbilityPool
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbilityType
import com.cobblemonroguelite.data.starter.HiddenAbilityTables
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/starter")

/**
 * The impure half of §2.27: reads Cobblemon's ability pool, and puts the granted ability on a
 * starter.
 *
 * Everything a unit test can execute is in [HiddenAbilityUnlock]; this file is the part that needs a
 * booted server — `PokemonSpecies`, the `Abilities` registry, and a live `Pokemon` — and it is
 * deliberately thin enough that reading it is most of reviewing it.
 *
 * ### Why the ability is written with `updateAbility` and not `ability =`
 *
 * `Pokemon.ability`'s setter is `internal` to Cobblemon, so from here the only public way in is
 * `updateAbility`. That is also the *correct* way in: it calls `attachAbilityCoordinate`, which
 * locates the template in the current form's pool and records the priority and index Cobblemon later
 * uses to carry the ability across an evolution. Setting the field would skip that and leave a
 * coordinate-less ability that the first form change silently replaces.
 *
 * This is exactly what Cobblemon's own `hiddenability=true` property does, which is worth stating:
 * that property exists, applies a random hidden ability, and would be the obvious thing to write into
 * [StarterFactory]'s properties string. It is not used because it *picks at random* among hidden
 * entries and because it has no notion of an override, and §2.27 needs both a deterministic answer
 * and a datapack-assignable one.
 */
object HiddenAbilityGrant {

    /**
     * What an unlock on [species] would grant, for the shop to quote.
     *
     * Reads the **standard form's** pool. That is the form [StarterFactory] builds — its properties
     * string names a species and a level and no aspects — so quoting anything else would price one
     * ability and grant another. `FormData.abilities` falls back to the species pool when a form does
     * not override it, so this is right for the overwhelming majority that do not.
     */
    fun choiceFor(species: Species): HiddenAbilityChoice = HiddenAbilityUnlock.choose(
        pool = poolOf(species.standardForm.abilities),
        override = HiddenAbilityTables.abilityFor(species.resourceIdentifier),
        abilityExists = ::abilityExists,
    )

    /**
     * The name of the ability an unlock on [id] grants, or null when there is nothing to sell.
     *
     * Null covers both "this species has no hidden ability" and "the override names an ability this
     * server does not have", and the caller ([com.cobblemonroguelite.progression.CandyLedger]) turns
     * either into a refusal. They are one answer here because they are one answer to the player —
     * there is nothing to buy — and the difference between them is an operator's problem, which is
     * why the second is logged rather than worded.
     */
    fun offeredName(id: ResourceLocation): String? {
        val species = runCatching { PokemonSpecies.getByIdentifier(id) }
            .onFailure { log.warn("roguelite: could not read ability data for '{}'", id, it) }
            .getOrNull() ?: return null
        return when (val choice = choiceFor(species)) {
            is HiddenAbilityChoice.FromPool -> choice.entry.name
            is HiddenAbilityChoice.Pinned -> choice.name
            HiddenAbilityChoice.None -> null
            is HiddenAbilityChoice.Unknown -> {
                log.error(
                    "roguelite: hidden_abilities assigns '{}' to '{}' and no such ability is installed — " +
                        "the unlock is withdrawn from sale for that species; fix the table or install the mod",
                    choice.name, id,
                )
                null
            }
        }
    }

    /**
     * Put the granted ability on [pokemon]. Returns the ability granted, or null if nothing was.
     *
     * Called only for a species whose unlock the player has bought. A null return here after the shop
     * sold the unlock means the datapack changed between the purchase and the run — the player keeps
     * the unlock (it is permanent and per-species) and the log says why this particular starter did
     * not get it, which is the only recoverable shape for that race.
     */
    fun applyTo(pokemon: Pokemon): String? {
        val form = runCatching { pokemon.form }
            .onFailure { log.warn("roguelite: could not read a form while granting a hidden ability", it) }
            .getOrNull() ?: return null
        val speciesId = pokemon.species.resourceIdentifier
        val pool = poolOf(form.abilities)
        val choice = HiddenAbilityUnlock.choose(
            pool = pool,
            override = HiddenAbilityTables.abilityFor(speciesId),
            abilityExists = ::abilityExists,
        )

        return when (choice) {
            is HiddenAbilityChoice.FromPool -> {
                val potential = form.abilities.toList().getOrNull(choice.index)
                if (potential == null) {
                    // The flattened copy and the live pool disagreed, which can only happen if the
                    // pool changed under us mid-reload. Refusing beats guessing an index.
                    log.warn("roguelite: ability pool for '{}' moved while granting — nothing granted", speciesId)
                    return null
                }
                // forced = false, so Cobblemon attaches the pool coordinate and an evolution carries
                // the ability forward as *that form's* hidden ability. See [HiddenAbilityChoice.FromPool].
                grant(pokemon, choice.entry.name, forced = false, priority = potential.priority)
            }

            // forced = true: there is no coordinate to attach, and a non-forced out-of-pool ability is
            // replaced at the first form change. See [HiddenAbilityChoice.Pinned].
            is HiddenAbilityChoice.Pinned -> grant(pokemon, choice.name, forced = true, priority = HIDDEN_PRIORITY)

            HiddenAbilityChoice.None -> {
                log.warn(
                    "roguelite: {} has an unlocked hidden ability but '{}' declares none — nothing granted",
                    speciesId, speciesId,
                )
                null
            }

            is HiddenAbilityChoice.Unknown -> {
                log.error(
                    "roguelite: hidden_abilities assigns '{}' to '{}' and no such ability is installed — " +
                        "nothing granted; fix the table or install the mod",
                    choice.name, speciesId,
                )
                null
            }
        }
    }

    /**
     * Flatten a Cobblemon pool into the form [HiddenAbilityUnlock] reasons about.
     *
     * `AbilityPool` is a `PrioritizedList`, so iteration order is the pool's own ordering and the
     * index handed back by the decision is an index into *this* list. Both callers flatten the same
     * way for that reason.
     */
    private fun poolOf(pool: AbilityPool): List<PoolAbility> = pool.map { potential ->
        // The one clean way to identify a hidden entry: Cobblemon tags it with `HiddenAbilityType`.
        // Nothing else distinguishes it — the templates are ordinary abilities and the priority is
        // shared with any other LOW-priority entry a datapack adds.
        PoolAbility(name = potential.template.name, hidden = potential.type == HiddenAbilityType)
    }

    private fun abilityExists(name: String): Boolean =
        runCatching { Abilities.get(name) }.getOrNull() != null

    private fun grant(pokemon: Pokemon, name: String, forced: Boolean, priority: Priority): String? {
        val template = runCatching { Abilities.get(HiddenAbilityUnlock.normalise(name)) }.getOrNull()
        if (template == null) {
            log.error("roguelite: ability '{}' vanished between the decision and the grant", name)
            return null
        }
        return runCatching {
            pokemon.updateAbility(template.create(forced, priority))
            template.name
        }.onFailure { log.warn("roguelite: could not grant ability '{}'", name, it) }.getOrNull()
    }

    /**
     * The priority Cobblemon gives a `HiddenAbility`. Only used for a forced grant, where it is
     * recorded and never consulted — but recording the honest value keeps a forced ability from
     * looking like an ordinary one if a later change ever un-forces it.
     */
    private val HIDDEN_PRIORITY = Priority.LOW
}
