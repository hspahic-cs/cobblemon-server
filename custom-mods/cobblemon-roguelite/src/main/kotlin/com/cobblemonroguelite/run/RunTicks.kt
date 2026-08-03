package com.cobblemonroguelite.run

import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/run")

/**
 * A tick-delayed task queue, because one thing genuinely needs a beat: starting a battle in the same
 * tick as a party install.
 *
 * The first live test produced the proof (bug #5): `resume` installs the run party and then begins
 * the wave ~300ms later in the same tick, so the client receives four party-removes, two party-adds
 * and the battle-start packets nearly simultaneously — and builds its battle GUI against the party it
 * had *before* the packets applied. No move buttons, no opponent HP bar, a battle stuck at T0. The
 * pre-isolation build never hit this because the party installed at login, seconds before any battle.
 *
 * Server thread only, drained from [RunIsolationGuards]' tick listener. Tasks are (ticks, action)
 * pairs and nothing else — anything that needs cancellation semantics should not be using a bare
 * delay, which is why [schedule]'s callers re-check their preconditions when they fire.
 */
object RunTicks {

    private data class Task(var ticksLeft: Int, val action: () -> Unit)

    private val tasks = mutableListOf<Task>()

    fun schedule(delayTicks: Int, action: () -> Unit) {
        tasks += Task(delayTicks.coerceAtLeast(1), action)
    }

    /** Called every tick — not on the poll cadence — by the guards' listener. */
    fun tick(server: MinecraftServer) {
        if (tasks.isEmpty()) return
        val due = mutableListOf<Task>()
        val iterator = tasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            task.ticksLeft--
            if (task.ticksLeft <= 0) {
                iterator.remove()
                due += task
            }
        }
        due.forEach { task ->
            runCatching(task.action).onFailure { log.error("roguelite: scheduled task failed", it) }
        }
    }
}
