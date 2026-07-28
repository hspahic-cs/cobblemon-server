package com.cobblemonroguelite.run

import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/run")

/**
 * Login and logout, where an interrupted run is picked back up.
 *
 * ### This is the hook point for §2.10, not §2.10
 *
 * The decision is that a disconnect never ends a run: on reconnect the run's battle-in-progress
 * marker is compared against the **server's boot identity**, and only if the server has not
 * restarted since — i.e. the drop was the player's own — are the Pokémon that were on the field
 * killed. Neither side of that comparison is forgeable, which is what makes it work.
 *
 * None of it is implemented here. It needs [RunState] to carry the marker, the boot identity and the
 * on-field party, which is a schema change and a separate piece of work, and there is nothing to
 * attribute yet because nothing can start a battle ([RunWaves]). What exists is the two places it
 * has to happen — [onLogin] compares and penalises, [onLogout] would be where a marker is *not*
 * cleared — so landing it does not mean going looking for where it goes.
 *
 * Getting it half-right is worse than not having it: attribution that ignores the boot identity
 * penalises players for our restarts, and attribution that never fires makes quitting a losing
 * battle free.
 */
object RunLoginHooks {

    @SubscribeEvent
    fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val status = RunController.reconcileOnLogin(player.server, player)
        when (status) {
            RunStatus.None -> Unit
            is RunStatus.AwaitingStarter -> player.sendSystemMessage(RunMessages.offer(status.offer))
            is RunStatus.InProgress -> player.sendSystemMessage(
                RunMessages.atWave(status.run.wave, status.run.partySnapshot().size, status.depthCap),
            )
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
