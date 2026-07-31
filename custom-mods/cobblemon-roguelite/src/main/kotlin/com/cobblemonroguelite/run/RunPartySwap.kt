package com.cobblemonroguelite.run

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.PokemonStore
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.api.storage.pc.PCStore
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/run")

/**
 * Swaps the player's own party out to their PC for the length of a run, and the run party in.
 *
 * ### This reverses §2.2, and the thing it reversed was a safety property
 *
 * The original decision kept the run party in [RunState] and never touched the player's real
 * Pokémon, and it called that "the single largest correctness risk in the project" removed. It was
 * right. What it cost was everything Cobblemon's own party UI does — a player could not open their
 * party screen, see the run team in the HUD, or use any of it on the Pokémon they were playing, and
 * rebuilding that inside chest menus is most of what a player does between battles. So the trade was
 * taken deliberately (plan §2.2, reversed 2026-07-31) and the risk has to be paid down here instead.
 *
 * ### The four things that make it safe
 *
 * **1. The Pokémon move into the PC, and the PC is Cobblemon's own storage.** Nothing is serialised
 * into our save data. If every byte this mod writes were lost, the player's team would still be in
 * their PC where they can walk up and take it out — a worse day, not a lost team. A design that
 * stashed NBT in run state would make our file the only copy, which is the failure §2.2 feared.
 *
 * **2. The record travels on the Pokémon.** [STASH_SLOT_KEY] is written into the Pokémon's own
 * `persistentData`, so there is no second list to fall out of step with reality. "What did we take,
 * and where did it come from" is answered by reading the PC, not by trusting a file. This is why
 * there is no stash table anywhere in this mod.
 *
 * **3. Release can only ever touch Pokémon a run made.** [RUN_MARKER_KEY] is stamped when the run
 * party is installed, and [restore] deletes a party member *only* if it is stamped. A release path
 * that decided by party position instead is one off-by-one away from deleting somebody's Garchomp,
 * and no amount of care at the call site would make that safe.
 *
 * **4. A half-done swap is rolled back, not left.** [install] moves six Pokémon and any one of those
 * moves can fail; the dangerous state is not a failed swap but a partial one. Every move is undone
 * on the first failure, and the run refuses to start rather than beginning with the player's team
 * scattered between two stores.
 *
 * ### Restore runs on login, not only at run end
 *
 * The recovery path is the *normal* path — [RunLoginHooks] calls [reconcile] on every join — so it
 * is exercised constantly rather than being an error branch nobody has run. A crash between the two
 * halves of a swap, a server killed mid-run, a run deleted by an operator: all of them present as
 * "party and PC disagree with the run store on login", and all of them are fixed by the same code
 * that fixes nothing on a normal login.
 */
object RunPartySwap {

    /**
     * Marks a Pokémon as the player's own, taken out of party slot N.
     *
     * Namespaced because `persistentData` is a shared bag every mod on the server writes into, and an
     * unprefixed `slot` would be a coin flip.
     */
    const val STASH_SLOT_KEY = "cobblemon_roguelite:stashed_from_slot"

    /** Marks a Pokémon as belonging to a run, and therefore as releasable. Holds the run's seed. */
    const val RUN_MARKER_KEY = "cobblemon_roguelite:run_seed"

    /** What happened, for a caller that has to decide whether the run may proceed. */
    sealed interface SwapResult {
        data class Installed(val stashed: Int, val installed: Int) : SwapResult

        /** Nothing was moved and nothing is half-done. The run must not start. */
        data class Refused(val reason: String) : SwapResult
    }

    /**
     * Players whose party is being swapped right now, for [RunDexGuard] to veto dex writes during.
     *
     * ### Why the arena test is not enough
     *
     * `PlayerPartyStore.add` emits `POKEMON_GAINED`, Cobblemon turns that into `PokedexManager.catch`,
     * and §2.15 makes the server Pokédex the meta-progression that unlocks starters. So installing a
     * run party writes six dex entries — and a player would unlock species permanently by *drafting*
     * them, without ever catching one.
     *
     * [RunDexGuard] already vetoes dex writes, but it gates on being in a battle or in an arena, and
     * neither is true at every install: the first install runs a moment before the arena teleport, and
     * the login reconcile can run with the player standing anywhere in the world. A flag scoped to the
     * exact operation covers all of them, and — unlike widening the guard to "has a run" — cannot eat
     * a dex entry the player legitimately earned while paused (§2.3), which the guard's own docs call
     * out as the worse failure.
     */
    private val swapping = java.util.concurrent.ConcurrentHashMap.newKeySet<java.util.UUID>()

    fun isSwapping(player: java.util.UUID): Boolean = swapping.contains(player)

    /** Run [block] with dex writes for [player] vetoed. Always clears, including on a throw. */
    private fun <T> suppressingDex(player: ServerPlayer, block: () -> T): T {
        swapping.add(player.uuid)
        return try {
            block()
        } finally {
            swapping.remove(player.uuid)
        }
    }

    fun isRunPokemon(pokemon: Pokemon): Boolean = pokemon.persistentData.contains(RUN_MARKER_KEY)

    fun isStashed(pokemon: Pokemon): Boolean = pokemon.persistentData.contains(STASH_SLOT_KEY)

    /**
     * Move [player]'s own party to their PC and put [run]'s party in its place.
     *
     * Idempotent by way of [reconcile]: calling it when the swap is already in place stashes nothing,
     * because the party holds only run-marked Pokémon and there is nothing of the player's to take.
     */
    fun install(player: ServerPlayer, run: RunState): SwapResult = suppressingDex(player) {
        installUnguarded(player, run)
    }

    private fun installUnguarded(player: ServerPlayer, run: RunState): SwapResult {
        val party = partyOf(player) ?: return SwapResult.Refused("the party store is unavailable")
        val pc = pcOf(player) ?: return SwapResult.Refused("the PC store is unavailable")

        val runParty = run.partySnapshot()
        if (runParty.isEmpty()) return SwapResult.Refused("the run has no party to install")

        // The player's own Pokémon: everything in the party that a run did not put there. Read before
        // anything moves, because the party is about to change underneath.
        val owned = party.toList().filterNot(::isRunPokemon)

        // Checked up front rather than discovered halfway. Refusing is free; a run that started and
        // then could not find room is a player whose team is in two places.
        if (owned.size > MAX_PARTY) {
            return SwapResult.Refused("the party holds ${owned.size} Pokémon, which is more than a party can")
        }

        val moved = mutableListOf<Pair<Pokemon, Int>>()
        for ((slot, pokemon) in owned.withIndex()) {
            // Remove first, add second. The reverse would put one Pokémon in two stores at once with
            // its own coordinates pointing at the newer of them, which is how a duplicate becomes a
            // deletion the next time either store is written.
            if (!party.remove(pokemon)) {
                rollBackInstall(party, pc, moved)
                return SwapResult.Refused("could not take ${pokemon.species.name} out of the party")
            }
            pokemon.persistentData.putInt(STASH_SLOT_KEY, slot)
            if (!pc.add(pokemon)) {
                // The Pokémon is currently in neither store, so it goes back before anything else does.
                pokemon.persistentData.remove(STASH_SLOT_KEY)
                party.add(pokemon)
                rollBackInstall(party, pc, moved)
                return SwapResult.Refused("the PC would not accept ${pokemon.species.name} — is it full?")
            }
            moved += pokemon to slot
        }

        // Only now is the party empty of the player's own Pokémon. Marked before being added, so a
        // failure between the two leaves something that restore recognises as the run's.
        var installed = 0
        for (pokemon in runParty) {
            pokemon.persistentData.putLong(RUN_MARKER_KEY, run.seed)
            if (party.add(pokemon)) {
                installed++
            } else {
                // Not fatal and not rolled back: the run party is ours, it is still in RunState, and a
                // run that shows five of six is a visible fault the player can be told about — where an
                // aborted install would put their own team back and strand the run with no party.
                log.error(
                    "roguelite: could not install run Pokémon {} into {}'s party — the run continues without it",
                    pokemon.species.name, player.gameProfile.name,
                )
            }
        }

        log.info(
            "roguelite: swapped {}'s party for their run — {} stashed to the PC, {} installed ({})",
            player.gameProfile.name, moved.size, installed,
            // Species and levels, because the first playtest reported run levels "resetting" across
            // a pause/resume and no log line could say whether install put back the levels restore
            // took out. Every seam the party crosses now records what crossed it.
            describeLevels(runParty),
        )
        // Said out loud, which is the clearest gap prior art showed up (docs/roguelite-prior-art.md):
        // Quick Teams notifies on screen when it moves Pokémon between the party and the PC, we did
        // not, and the first playtest produced exactly the confusion that predicts — "my team is still
        // in my party". A swap nobody is told about is indistinguishable from a swap that failed.
        if (moved.isNotEmpty()) player.sendSystemMessage(RunMessages.partyStashed(moved.size))
        return SwapResult.Installed(stashed = moved.size, installed = installed)
    }

    /**
     * Put [player]'s own party back and release what the run created.
     *
     * Safe to call when nothing is swapped, which is what makes it usable from both the run-end path
     * and the login path without either needing to know what the other did.
     *
     * Returns the number of Pokémon handed back. The run's own are released — they never existed
     * outside the run (§2.2's isolation still holds for *persistence*) — but only ever by marker.
     */
    fun restore(player: ServerPlayer): Int = suppressingDex(player) {
        restoreUnguarded(player)
    }

    private fun restoreUnguarded(player: ServerPlayer): Int {
        val party = partyOf(player) ?: return 0
        val pc = pcOf(player) ?: return 0

        // H1 (isolation design §7.7): the PC is swept for run-marked Pokémon too, not only the party.
        // A run Pokémon a player deposited into a PC box mid-session was touched by neither of the
        // original sweeps and survived the run — the legendary faucet §2.2 exists to close. The window
        // is closed by construction now (run Pokémon exist in real stores only inside the arena, which
        // has no PC), so this is the crash-stranded-state backstop, and it is marker-keyed like every
        // destructive act.
        pc.toList().filter(::isRunPokemon).forEach { stray ->
            if (pc.remove(stray)) {
                log.warn(
                    "roguelite: swept run Pokémon {} out of {}'s PC — it should never have been there",
                    stray.species.name, player.gameProfile.name,
                )
            }
        }

        // Run Pokémon first, because the party has to have room before anything can come back into it.
        // `toList()` because removing while iterating a store is a concurrent modification.
        val runOwned = party.toList().filter(::isRunPokemon)
        runOwned.forEach { pokemon ->
            if (!party.remove(pokemon)) {
                log.error(
                    "roguelite: could not remove run Pokémon {} from {}'s party — it is left in place " +
                        "rather than forced, and will be removed on their next login",
                    pokemon.species.name, player.gameProfile.name,
                )
            }
        }

        // Everything of theirs still sitting in the PC, in the order it was taken from.
        val stashed = pc.toList().filter(::isStashed).sortedBy { it.persistentData.getInt(STASH_SLOT_KEY) }
        var returned = 0
        for (pokemon in stashed) {
            if (!pc.remove(pokemon)) {
                log.error("roguelite: could not take {} back out of {}'s PC", pokemon.species.name, player.gameProfile.name)
                continue
            }
            // The marker comes off only once it is actually back in the party. If `add` fails below,
            // the Pokémon is put back in the PC still marked, so the next login tries again — rather
            // than being an unmarked Pokémon in a PC box nobody knows was ever moved.
            if (party.add(pokemon)) {
                pokemon.persistentData.remove(STASH_SLOT_KEY)
                returned++
            } else {
                pc.add(pokemon)
                log.error(
                    "roguelite: could not put {} back into {}'s party — it stays in the PC, marked, and " +
                        "the next login will try again",
                    pokemon.species.name, player.gameProfile.name,
                )
            }
        }

        if (runOwned.isNotEmpty() || returned > 0) {
            log.info(
                "roguelite: restored {}'s party — {} run Pokémon released ({}), {} of their own returned",
                player.gameProfile.name, runOwned.size, describeLevels(runOwned), returned,
            )
        }
        if (returned > 0) player.sendSystemMessage(RunMessages.partyReturned(returned))
        // The failure Quick Teams also surfaces on screen rather than only in a log: some of their
        // Pokémon are sitting in the PC, still marked, and the next login will try again. A player who
        // is not told this counts their party, finds it short, and concludes the mode ate one.
        val stranded = stashed.size - returned
        if (stranded > 0) player.sendSystemMessage(RunMessages.partyStranded(stranded))
        return returned
    }

    /**
     * Make the world agree with the run store, whatever it currently says.
     *
     * The one call the login hook makes, and the reason a crash mid-swap is not a support ticket:
     *
     * - a run is active and the party is not theirs   -> install
     * - no run is active and something of theirs is stashed -> restore
     * - anything else -> nothing happens
     *
     * Deliberately decides from the *world* rather than from a flag we wrote down. A flag can be
     * wrong; a party holding a Pokémon stamped with a run seed cannot be.
     */
    fun reconcile(player: ServerPlayer, run: RunState?): Boolean {
        val party = partyOf(player) ?: return false
        val pc = pcOf(player) ?: return false
        val partyHoldsRunPokemon = party.toList().any(::isRunPokemon)
        val pcHoldsStash = pc.toList().any(::isStashed)

        return when {
            run != null && !partyHoldsRunPokemon -> {
                log.info("roguelite: {} has a run but is holding their own party — installing", player.gameProfile.name)
                install(player, run) is SwapResult.Installed
            }

            run != null -> {
                rebind(player, run, party)
                false
            }

            run == null && (partyHoldsRunPokemon || pcHoldsStash) -> {
                log.info(
                    "roguelite: {} has no run but the swap is still in place — restoring",
                    player.gameProfile.name,
                )
                restore(player) >= 0
            }

            else -> false
        }
    }

    /**
     * Point [run]'s party list at the Pokémon that are actually in the player's party.
     *
     * ### The desync this exists to stop
     *
     * [RunState] serialises its party into our save data, and Cobblemon serialises the same Pokémon
     * into the player's. After a restart there are therefore two object graphs with the same UUIDs:
     * the ones the store loaded, which the battle mutates and the player can see, and the ones
     * [RunStore] loaded, which nothing mutates. Every reward would land on the copy nobody is playing
     * — a Rare Candy that visibly does nothing — and the next checkpoint would write the stale copy
     * back over the truth.
     *
     * So on the first login of a session the run's list is re-pointed at the live objects. Matching is
     * by UUID and by the run's own seed marker: a Pokémon in the party stamped with a *different* run's
     * seed is somebody else's leftover and is left alone rather than adopted.
     */
    private fun rebind(player: ServerPlayer, run: RunState, party: PlayerPartyStore) {
        val live = party.toList().filter { it.persistentData.getLong(RUN_MARKER_KEY) == run.seed }
        if (live.isEmpty()) return

        synchronized(run.party) {
            val known = run.party.map { it.uuid }.toSet()
            // Order comes from the party, not from the run's list: the party is what the player sees
            // and rearranges, so it is the one that decides who leads.
            val rebound = live.filter { it.uuid in known }
            val missing = known - rebound.map { it.uuid }.toSet()
            if (rebound.isEmpty()) return

            run.party.clear()
            run.party.addAll(rebound)
            if (missing.isNotEmpty()) {
                // Not restored and not an error: a run Pokémon that is in the run's list but not in the
                // party has usually just died to permadeath between the last checkpoint and the crash.
                log.info(
                    "roguelite: {}'s run listed {} Pokémon that are no longer in their party — dropped on rebind",
                    player.gameProfile.name, missing.size,
                )
            }
        }
        log.info(
            "roguelite: re-pointed {}'s run party at the {} Pokémon they are holding ({})",
            player.gameProfile.name, live.size, describeLevels(live),
        )
    }

    /**
     * `species Lnn` per member, for the seam logs above. The first playtest reported run levels
     * "resetting" across a creative-mode pause and resume, and no log line recorded levels at any
     * of the three seams the party crosses — so the report could not even be localised to a seam.
     */
    private fun describeLevels(party: List<Pokemon>): String =
        party.joinToString(", ") { p ->
            runCatching { "${p.species.name} L${p.level}" }.getOrDefault("?")
        }

    /** Undo the moves already made, so a refused install leaves the party exactly as it was found. */
    private fun rollBackInstall(party: PokemonStore<*>, pc: PCStore, moved: List<Pair<Pokemon, Int>>) {
        moved.asReversed().forEach { (pokemon, _) ->
            if (pc.remove(pokemon)) {
                pokemon.persistentData.remove(STASH_SLOT_KEY)
                if (!party.add(pokemon)) {
                    // Nowhere left to put it that is not worse. Back to the PC, still marked, so the
                    // login reconcile finds it rather than it being lost to a box nobody checks.
                    pokemon.persistentData.putInt(STASH_SLOT_KEY, 0)
                    pc.add(pokemon)
                    log.error("roguelite: rollback could not return {} to the party; it stays in the PC, marked", pokemon.species.name)
                }
            }
        }
    }

    private const val MAX_PARTY = 6

    private fun partyOf(player: ServerPlayer): PlayerPartyStore? =
        runCatching { Cobblemon.storage.getParty(player) }
            .onFailure { log.error("roguelite: could not reach {}'s party store", player.gameProfile.name, it) }
            .getOrNull()

    private fun pcOf(player: ServerPlayer): PCStore? =
        runCatching { Cobblemon.storage.getPC(player) }
            .onFailure { log.error("roguelite: could not reach {}'s PC store", player.gameProfile.name, it) }
            .getOrNull()
}
