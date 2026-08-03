package com.cobblemonroguelite.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemonroguelite.run.RunPartySwap
import com.cobblemonroguelite.run.RunState
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * Turns a run party into the team a wave battle is fought with.
 *
 * ### The player's real party is never involved, in either direction
 *
 * The battle is handed a store that exists for the length of one wave and is registered with nothing
 * — not `Cobblemon.storage`, not the player's `PlayerPartyStore`, not the save file. That is design
 * decision 1 made mechanical: there is no code path from a run battle to the player's Pokémon, so no
 * crash, restart or botched restore can reach them. The run party's only home is [RunState], and
 * [RunStore][com.cobblemonroguelite.run.RunStore] is the only thing that writes it down.
 *
 * ### Uncloned, and that is the load-bearing part
 *
 * `RankedBattle.buildTempParty()` is the shape this follows and it differs in three ways, all of
 * which are the difference between a roguelite and a scrimmage:
 *
 * 1. **No `clone()` at all**, not even `clone(newUUID = false)`. The UUID warning everyone repeats
 *    is only half of it. A clone *with* the right UUID still takes the wave's damage on the copy —
 *    the run Pokémon in [RunState.party] finishes the wave at the HP it started with, the next
 *    checkpoint writes that down, and the run is silently a full-heal-every-wave mode. Attrition is
 *    the mode (§2.11 removes the bag precisely so it cannot be undone), so the battle has to mutate
 *    the real objects. Handing them over uncloned is the option [RunState.kill] explicitly allows,
 *    and it makes the identity contract trivially true rather than carefully maintained.
 * 2. **No `heal()`.** Same reason, stated in the other direction, and the one the design docs repeat.
 * 3. **A plain [PartyStore], not a `PlayerPartyStore`.** `PlayerPartyStore.add` is not a container
 *    operation: it stamps itself as the Pokémon's original trainer, fires `POKEMON_GAINED`, trips
 *    the party advancement criterion, and overflows to the player's **real PC** when the store is
 *    full. Every one of those is something leaving the run (§1.1) through a method that looks like
 *    a list insert. [PartyStore] is the same container with none of the ownership behaviour.
 *
 * What that costs: nothing in a plain store answers `getOwnerPlayer()`, so anything in Cobblemon
 * that resolves a Pokémon's owner through its store sees none for a run Pokémon. Experience and the
 * battle's own player-side hooks take the player explicitly and are unaffected; this is called out
 * because it is the one behaviour the swap gives up, and it needs a dev VM to confirm nothing else
 * leans on it.
 */
object RunBattleParty {

    /**
     * The battle team for [run], lead first, or null when the run has nothing to fight with.
     *
     * A null here is not "an empty party" — an empty party is a wipe, and by the time a wave starts
     * the controller has already ended any run that reached one. It means the store refused the
     * party, which at six or fewer members means something is wrong with the party itself, and
     * starting a battle with a partial team would silently disqualify the members that were dropped.
     */
    fun teamFor(player: ServerPlayer, run: RunState): List<BattlePokemon>? {
        // The player's OWN store, since §2.2's reversal put the run party in it. Building a second
        // store here and adding the same objects to it — which is what this did before the reversal —
        // would re-stamp their `storeCoordinates` while they are still sitting in the player's party
        // slots, which is one Pokémon in two stores and the shortest path from a duplicate to a
        // deletion. See [RunPartySwap], which refuses to do the same thing in the other direction.
        val store = runCatching { Cobblemon.storage.getParty(player) }
            .onFailure { log.error("roguelite: could not reach {}'s party for a wave", player.gameProfile.name, it) }
            .getOrNull() ?: return null

        val members = store.toList()
        if (members.isEmpty()) return null

        // THE GUARD THAT MATTERS. If anything in the party is not the run's, the swap did not take —
        // and fighting the wave anyway would put the player's real Pokémon into a battle that mutates
        // them uncloned and can permanently kill them. Refusing costs a wave; the alternative costs a
        // team, so this refuses even though the run is otherwise fine.
        val foreign = members.filterNot(RunPartySwap::isRunPokemon)
        if (foreign.isNotEmpty()) {
            log.error(
                "roguelite: refusing {}'s wave — their party holds {} Pokémon the run did not put there " +
                    "({}). The run party swap did not take; nothing has been risked.",
                player.gameProfile.name, foreign.size, foreign.joinToString { it.species.name },
            )
            return null
        }

        // The lead is the Pokémon that ended the previous wave on the field (PokéRogue's
        // continuity, [RunState.lastLead]) when it is still standing, else party slot 1. Fainted
        // leads fall through: sending out a fainted Pokémon is a refusal Cobblemon makes for us,
        // as a wave that cannot start.
        val lead = run.lastLead
            ?.let { uuid -> members.firstOrNull { it.uuid == uuid } }
            ?.takeIf { runCatching { !it.isFainted() }.getOrDefault(true) }
            ?: members.first()

        // clone = false is what makes the battle mutate the run party rather than a copy of it, and
        // healPokemon = false is what stops the wave transition undoing the last one. Both defaults
        // in Cobblemon are the other way round, which is why they are spelled out.
        val team = store.toBattleTeam(clone = false, healPokemon = false, leadingPokemon = lead.uuid)
        return team.takeIf { it.isNotEmpty() }
    }
}
