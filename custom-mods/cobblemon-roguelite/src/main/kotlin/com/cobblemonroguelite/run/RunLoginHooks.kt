package com.cobblemonroguelite.run

import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
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

    @SubscribeEvent
    fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val reconciliation = RunController.reconcileOnLogin(player.server, player)
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
            }
        }
    }

    /**
     * Checkpoint on the way out.
     *
     * Redundant on the clean path — the run was already checkpointed at the last wave boundary — and
     * kept because the shop and reward steps that will sit between waves mutate a run without one,
     * and a logout is exactly when that delta is lost. Cheap: it serializes one party.
     */
    @SubscribeEvent
    fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val store = RunStore.of(player.server)
        if (!store.hasRun(player.uuid)) return
        store.checkpoint(player.server, player.uuid)
        log.debug("roguelite: checkpointed {} on logout", player.gameProfile.name)
    }
}
