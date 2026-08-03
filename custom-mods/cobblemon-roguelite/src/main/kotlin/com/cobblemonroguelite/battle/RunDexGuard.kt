package com.cobblemonroguelite.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.PokedexDataChangedEvent
import com.cobblemon.mod.common.util.getPlayer
import com.cobblemonroguelite.arena.RunArenas
import com.cobblemonroguelite.run.RunPartySwap
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * §2.15: what happens inside a run never reaches the player's real Pokédex.
 *
 * ### Why this is a separate mechanism from [RunCapture] and not part of it
 *
 * They look like one leak and are two. Catching writes the Pokémon into the player's storage, and
 * separately it writes `CAUGHT` into their dex — through a different call chain, at a different
 * moment, into a different file. `PlayerPartyStore.add` emits `POKEMON_GAINED`, Cobblemon's own
 * `PokedexHandler` turns that into `PokedexManager.catch`, and the record is written **before**
 * `POKEMON_CAPTURED` is emitted at all. So the party correction cannot cover it: by the time
 * [RunCapture] is called the dex is already changed, and nothing hands back what the previous value
 * was, which makes undoing it after the fact impossible rather than merely awkward.
 *
 * What matters about that is not tidiness. §2.15 makes the **server** Pokédex the meta-progression:
 * species caught on the server unlock starters for future runs. A run that wrote to it would unlock
 * its own starters, and the loop that decision exists to create — overworld catching earns run
 * variety — would close on itself in an afternoon.
 *
 * ### The lever
 *
 * `POKEDEX_DATA_CHANGED_PRE` is Cobblemon's own cancellable hook and it is checked at the top of
 * `FormDexRecord.addInformation`, before the knowledge level is raised and before the record is
 * propagated. Cancelling it means the write never happens, which is why this is a guard and not a
 * repair.
 *
 * ### Two gates, and why neither alone is enough
 *
 * **In a wave battle** ([RunBattles.isFighting]) is the one that catches the capture. It is exact —
 * the dex write happens inside `party.add`, which happens while the battle is still live, because
 * the capture's effect on the battle is dispatched rather than applied inline.
 *
 * **Standing in a run arena** ([RunArenas.isInArena]) is the one that catches everything else a run
 * does to a Pokédex: an evolution applied at the end of a wave, a dex scan pointed at the opponent.
 * Those happen after the battle has left the index, and the battle gate would miss all of them
 * silently — nothing about a dex entry appearing looks like a bug.
 *
 * Neither gate is "the player has a run", and that is deliberate. §2.3 makes runs checkpointable, so
 * a player can pause a run, walk back into the world and catch something legitimately — and a
 * run-shaped gate would eat the dex entry they actually earned, which is a worse failure than the one
 * being prevented and one they would never attribute to the roguelite.
 */
object RunDexGuard {

    private val registered = AtomicBoolean(false)

    /** Subscribe once. A second subscription cancels an already-cancelled event, i.e. costs only time. */
    fun register() {
        if (!registered.compareAndSet(false, true)) return
        // HIGHEST rather than [RunBattles]'s LOWEST, and for the opposite reason. This does not
        // observe the event, it vetoes it — so any other subscriber should be looking at an event
        // that is already cancelled, rather than reacting to a dex change that is about to be undone.
        CobblemonEvents.POKEDEX_DATA_CHANGED_PRE.subscribe(Priority.HIGHEST) { refuse(it) }
        log.debug("roguelite: run Pokédex isolation active")
    }

    private fun refuse(event: PokedexDataChangedEvent.Pre) {
        val player = event.playerUUID.getPlayer() ?: return
        if (!isInsideARun(player)) return
        event.cancel()
        log.debug(
            "roguelite: refused a Pokédex change for {} — it happened inside a run (§2.15)",
            player.gameProfile.name,
        )
    }

    private fun isInsideARun(player: ServerPlayer): Boolean =
        RunBattles.isFighting(player.uuid) ||
            RunArenas.isInArena(player) ||
            // §2.2-reversed. Installing a run party calls `PlayerPartyStore.add` six times, and that is
            // a dex write per Pokémon — so a player would unlock species by DRAFTING them, never having
            // caught one, which hands them §2.15's meta-progression for free. Neither gate above covers
            // it: the first install runs a moment before the arena teleport, and the login reconcile can
            // run with the player standing anywhere. Scoped to the operation rather than to "has a run",
            // so it still cannot eat a dex entry earned legitimately while paused.
            RunPartySwap.isSwapping(player.uuid)
}
