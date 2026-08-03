package com.cobblemonroguelite.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.pc
import com.cobblemonroguelite.progression.RunProgression
import com.cobblemonroguelite.run.RunController
import com.cobblemonroguelite.run.RunMessages
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * §2.13's other half: a ball thrown in a wild wave builds the **run** party, not the player's.
 *
 * ### Why this undoes a write rather than preventing one
 *
 * Cobblemon has no cancellable hook on the way in. `EmptyPokeBallEntity` finishes a successful
 * capture by calling `player.party().add(pokemon)` — which stamps the player as original trainer,
 * overflows into their **real PC** when the party is full, and fires `POKEMON_GAINED` — and only
 * *then* emits `POKEMON_CAPTURED`, which is a plain event observable with nothing to cancel. So by
 * the time anything of ours can run, the Pokémon is already in the player's storage. The only
 * available correction is to take it straight back out, which is what [reclaim] does, and the
 * correctness of everything after it depends on that having worked.
 *
 * ### Why it must not be deferred to the next tick
 *
 * A `server.execute` hop here would leave a run Pokémon sitting in the player's real party for a
 * tick, and a tick is long enough for the world autosave, a logout, or a second capture to see it —
 * at which point it is on disk in the player's own file and §1.1 is broken in the durable direction.
 * The capture flow runs from the ball entity's scheduled task, i.e. on the server thread already, so
 * doing the work inline costs nothing and removes the window entirely.
 *
 * ### Why a failed reclaim refuses instead of pressing on
 *
 * If the Pokémon is in neither the party nor the PC, something else has moved it, and adding it to
 * the run anyway would put one object in two stores with two owners mutating it — the run would take
 * the battle damage and the real store would take the save. That is strictly worse than the leak it
 * was trying to fix, so the leak is reported and left alone for an operator to unpick.
 *
 * ### Only wild waves, and the order the two questions are asked in
 *
 * §2.14 makes trainer and boss Pokémon uncatchable, and the check is
 * [WavePlan.catchable][com.cobblemonroguelite.composition.WavePlan.catchable] as the wave was
 * *planned* — not the wave number, because a roster promotion turns a scheduled wild wave into a boss
 * and the arithmetic would still call it wild.
 *
 * The order matters more than the check. **Every** capture in a wave battle is reclaimed first, and
 * only then is the wave asked whether the run may keep it. The reverse order would make a
 * non-catchable wave leak the Pokémon into the player's real party — §1.1's defining failure — over
 * what would be a bug in whichever layer said the wave was catchable. This way that bug costs the
 * player a Pokémon that was never supposed to be catchable, which is recoverable by fixing the bug;
 * a leak into real storage is not.
 *
 * There is a second, independent guard on the same rule: [RunWildBattle] applies `UncatchableProperty`
 * to a non-catchable opponent, so on the shipped path a ball never lands at all. Both are kept —
 * that one protects the entity, this one protects the run party — and this one is what still holds
 * for a host mod that supplies its own wave handler.
 */
object RunCapture {

    private val registered = AtomicBoolean(false)

    /** Subscribe once. A second subscription would try to reclaim an already-reclaimed catch. */
    fun register() {
        if (!registered.compareAndSet(false, true)) return
        // LOWEST, so anything else on the server that reacts to a capture has already seen it as the
        // ordinary capture it looks like. Ours is the correction, and it is the last word.
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.LOWEST) { onCaptured(it.player, it.pokemon) }
        log.debug("roguelite: run capture routing active")
    }

    private fun onCaptured(player: ServerPlayer, pokemon: Pokemon) {
        // Not in a wave battle at all: an ordinary catch, somewhere else on the server, by somebody
        // who may or may not have a paused run. Nothing here has any business touching it.
        if (!RunBattles.isFighting(player.uuid)) return
        if (!reclaim(player, pokemon)) return

        if (!RunBattles.isCatchableWave(player.uuid)) {
            // Reachable only through a bug — §2.14's opponents are spawned uncatchable — so it is an
            // error rather than a rule being enforced. The Pokémon is already out of the player's
            // storage by this point and there is nowhere for it to go, which is the deliberate half:
            // see the class docs on why this direction is the safe one to fail in.
            log.error(
                "roguelite: {} captured {} on a wave that is not catchable (§2.14) — the catch is " +
                    "discarded. Check the wave was tracked with the plan's catchable flag.",
                player.gameProfile.name, pokemon.species.name,
            )
            player.sendSystemMessage(RunMessages.uncatchableWave())
            return
        }

        // §1.1 as restated: the Pokémon never leaves the run, but the *fact that it was caught* does —
        // candy for the species and its IVs into that species' floor (§2.15, §2.17). Credited here,
        // above the routing, because the routing is allowed to fail and the catch still happened; see
        // [RunProgression.creditCatch] for both halves of the ordering. Nothing in that call touches
        // the player's party, PC or Pokédex — it writes to a store of our own, which is what keeps
        // this from being a hole in the two isolation mechanisms either side of it.
        RunProgression.creditCatch(player.server, player.uuid, pokemon)

        val routing = RunController.pokemonCaught(player.server, player.uuid, pokemon)
        if (routing == null) {
            // The wave said there was a run and the store says there is not, so the Pokémon belongs
            // nowhere: it is out of the player's storage by now and there is no run to put it in.
            // Losing it is correct — it was never theirs to keep (§2.2) — but it is worth saying so,
            // because the alternative reading is that a catch silently failed.
            log.warn(
                "roguelite: {} caught {} in a wave whose run has already ended — the catch is discarded",
                player.gameProfile.name, pokemon.species.name,
            )
            return
        }
        player.sendSystemMessage(RunMessages.caught(routing, pokemon))
    }

    /**
     * Take [pokemon] back out of the player's real storage. False means it was not found in either.
     *
     * The PC branch is not a fallback for tidiness: `PlayerPartyStore.add` overflows there whenever
     * the player's own party is full, which is the ordinary state of anyone who plays the server, so
     * for most players this is the branch that actually runs. `PokemonStore.remove` clears the
     * Pokémon's store coordinates and recalls it, which is exactly the detached state the rest of the
     * run party is already in.
     */
    private fun reclaim(player: ServerPlayer, pokemon: Pokemon): Boolean {
        if (player.party().remove(pokemon)) return true
        if (player.pc().remove(pokemon)) return true
        log.error(
            "roguelite: {} caught {} inside a run and it is in neither their party nor their PC — " +
                "refusing to add it to the run, because a Pokémon in two stores is worse than one in " +
                "the wrong store. It has leaked out of the run (§1.1) and needs removing by hand.",
            player.gameProfile.name, pokemon.species.name,
        )
        return false
    }
}
