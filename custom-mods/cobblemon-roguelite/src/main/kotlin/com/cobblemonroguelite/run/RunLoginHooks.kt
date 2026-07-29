package com.cobblemonroguelite.run

import com.cobblemonroguelite.arena.RunArenas
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/run")

/**
 * Server start, login and logout — where an interrupted run is attributed and picked back up.
 *
 * ### Where §2.10 lands, and where it does not
 *
 * A disconnect never ends a run. On reconnect [RunController.reconcileOnLogin] compares the run's
 * battle-in-progress marker against the server's boot identity, and only where the server has *not*
 * restarted since — i.e. the drop was the player's own — are the on-field Pokémon killed. All of the
 * deciding is there and in [DisconnectAttribution]; what happens here is the telling.
 *
 * Note the two things this file does **not** do. [onLogout] deliberately does not clear the marker:
 * the marker surviving the logout is the entire mechanism, and a tidy-up there would erase the
 * evidence of exactly the disconnect it exists to catch. And nothing here decides anything from the
 * logout side — a logout is not proof of a rage-quit, since a server shutdown logs everybody out too,
 * and the boot comparison at the next login is what tells those apart.
 *
 * ### Where §2.23 lands
 *
 * Both of its halves are session boundaries, so both are here. [onLogout] hands the arena back, which
 * is what makes `maxConcurrentRuns` a bound on concurrent *play* rather than on saved runs.
 * [onServerStarted] sweeps runs nobody has played inside their retention period. Neither decides
 * anything: the lease rules are [RunArenas]' and the periods are [RunExpiry]'s.
 */
object RunLoginHooks {

    /**
     * Mint this process's boot identity before anyone can connect.
     *
     * Subscribed here rather than in the mod class because this object is the module's game-bus
     * listener and the identity is only ever used by the login path beside it. It also keeps the
     * ordering plain: the mint happens once, at start, before the first login can compare against it.
     */
    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        ServerBootId.remint()
        log.debug("roguelite: boot identity minted")
    }

    /**
     * §2.23's sweep, once, before anybody can be affected by it.
     *
     * [ServerStartedEvent] rather than [ServerStartingEvent] beside it: the sweep reads world save data
     * through [RunStore.of], which needs the overworld's `DimensionDataStorage`, and the boot identity
     * next door needs neither. Both still happen before the first login, which is the only ordering
     * either of them depends on.
     *
     * Failure is caught rather than propagated. A sweep that throws — one damaged run in the file — must
     * not take the server start with it, because the thing it is tidying is storage and the thing it
     * would break is everybody's session.
     */
    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        runCatching { RunController.expireStaleRuns(event.server) }
            .onSuccess { if (it > 0) log.info("roguelite: expiry sweep discarded {} unplayed run(s) (§2.23)", it) }
            .onFailure { log.error("roguelite: the run expiry sweep failed — no runs were discarded", it) }
    }

    @SubscribeEvent
    fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val reconciliation = RunController.reconcileOnLogin(player.server, player)
        // First of everything, because it is about a run that no longer exists and every line below it
        // describes the world as it is now. Reversed, a player reads "you have no run" and then finds
        // out why, having already concluded the server lost it.
        reconciliation.expired?.let { player.sendSystemMessage(RunLifecycleMessages.expired(it)) }
        // Before the status line, because it explains it: "wave 13, 2 Pokémon alive" is a different
        // sentence to somebody who has just been told why it is not 3.
        reconciliation.interrupted?.let { player.sendSystemMessage(RunMessages.interrupted(it)) }
        // The payout report of a run the penalty wiped. Sent separately rather than folded into the
        // penalty message because it is the same thing every other run end says, and a player who
        // lost a run this way is owed the ordinary accounting of it.
        (reconciliation.interrupted as? DisconnectOutcome.Penalised)?.ended
            ?.let { player.sendSystemMessage(RunMessages.ended(it)) }
        when (val status = reconciliation.status) {
            RunStatus.None -> Unit
            is RunStatus.AwaitingStarter -> player.sendSystemMessage(RunMessages.catalogue(status.catalogue))
            is RunStatus.InProgress -> {
                player.sendSystemMessage(
                    RunMessages.atWave(status.run.wave, status.run.partySnapshot().size, status.depthCap),
                )
                // §2.13. A held catch stops the run advancing, so a player who logs in and finds
                // `/roguelite resume` refusing has to be told why here — the alternative is somebody
                // concluding their run is stuck and abandoning it, which destroys the party.
                status.run.pendingCatch?.let { player.sendSystemMessage(RunMessages.catchPending(it)) }
                // §2.23. After the wave line rather than before it, because the warning names a wave
                // and reads as an answer to the line above it.
                reconciliation.expiring?.let {
                    player.sendSystemMessage(RunLifecycleMessages.expiringSoon(status.run.wave, it))
                }
            }
        }
        // Last, because it is the only line about where they are standing rather than about the run.
        // Said only to somebody who still has one: a player ejected from an arena with no run has either
        // just been told it expired or has nothing to resume, and "your run is untouched" would be a lie
        // in both cases.
        if (reconciliation.returnedFromArena && reconciliation.status is RunStatus.InProgress) {
            player.sendSystemMessage(RunLifecycleMessages.returnedFromArena())
        }
    }

    /**
     * Hand the arena back (§2.23), then checkpoint.
     *
     * ### The release
     *
     * A run occupies an arena only while its player is online and in it, so the lease ends here. The
     * run is untouched by it — the party, the wave and the biome all stay exactly as they are — and the
     * next entry takes a slot, stamps it and repaints it. See [RunArenas.release].
     *
     * **The player is deliberately not teleported out.** A cross-dimension teleport during a disconnect
     * is the sort of thing that half-works: the event fires before `PlayerList.save`, so it *would*
     * persist, but it moves an entity that the server is in the middle of removing. Ejecting them at
     * the next login instead ([RunController.reconcileOnLogin]) is one branch, on a player who is fully
     * in the world, and it is a branch that has to exist anyway for anyone whose session ended in a
     * crash rather than a clean quit.
     *
     * ### The checkpoint
     *
     * Redundant on the clean path — the run was already checkpointed at the last wave boundary — and
     * kept because the shop and reward steps that will sit between waves mutate a run without one,
     * and a logout is exactly when that delta is lost. Cheap: it serializes one party.
     *
     * It follows the release rather than preceding it, though nothing on disk can tell: none of the
     * three fields the release clears is written to a checkpoint, precisely so that a crash — which
     * skips this handler entirely — cannot restore a lease either.
     */
    @SubscribeEvent
    fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val server = player.server ?: return
        val store = RunStore.of(server)
        val run = store.get(player.uuid) ?: return
        val slot = run.arenaSlot
        RunArenas.release(server, run)
        store.checkpoint(server, player.uuid)
        if (slot != null) {
            log.info(
                "roguelite: {} logged out at wave {} — arena slot {} released (§2.23)",
                player.gameProfile.name, run.wave, slot,
            )
        } else {
            log.debug("roguelite: checkpointed {} on logout", player.gameProfile.name)
        }
    }
}
