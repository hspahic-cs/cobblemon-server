package com.cobblemonserver.npc.gym

import com.cobblemonserver.npc.CobblemonNpc
import com.cobblemonserver.npc.job.JobGymLeader
import com.minecolonies.api.IMinecoloniesAPI
import com.minecolonies.api.colony.ICitizenData
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenJobChangedModEvent
import com.minecolonies.core.entity.citizen.EntityCitizen

/**
 * Bridges Minecolonies' [CitizenJobChangedModEvent] into [GymLeaderManager]'s hire/fire flow.
 *
 * A citizen newly assigned to [JobGymLeader] is treated as hired — their NpcTeamData gets a
 * `gymHirerUuid` set to the colony owner, and the theme picker is pushed to the owner if
 * they're online.
 *
 * A citizen leaving [JobGymLeader] (reassigned, or hut destroyed) is treated as fired —
 * all gym-leader state clears, their original name is restored, and their theme is released.
 */
object GymAssignmentListener {

    fun register() {
        IMinecoloniesAPI.getInstance().eventBus.subscribe(
            CitizenJobChangedModEvent::class.java
        ) { event ->
            handle(event)
        }
    }

    private fun handle(event: CitizenJobChangedModEvent) {
        // AbstractCitizenModEvent exposes the citizen as ICitizen (read-only), but the backing
        // field is ICitizenData. Cast to recover full access — CitizenData implements both.
        val citizenData = event.citizen as? ICitizenData ?: return
        val entity = citizenData.entity.orElse(null) as? EntityCitizen ?: return

        val previousJobKey = event.previousJob?.key?.path
        val currentJobKey = citizenData.job?.jobRegistryEntry?.key?.path

        val wasGymLeader = previousJobKey == "gym_leader"
        val isGymLeader = currentJobKey == "gym_leader"

        when {
            !wasGymLeader && isGymLeader -> {
                val result = GymLeaderManager.onJobAssigned(entity)
                if (result is GymLeaderManager.HireResult.Failed) {
                    CobblemonNpc.logger.warn(
                        "cobblemon-npc: could not promote ${entity.name.string} to Gym Leader — ${result.reason}"
                    )
                }
            }
            wasGymLeader && !isGymLeader -> {
                val result = GymLeaderManager.onJobRemoved(entity)
                if (result is GymLeaderManager.FireResult.Failed) {
                    CobblemonNpc.logger.info(
                        "cobblemon-npc: ${entity.name.string} left Gym Leader — ${result.reason}"
                    )
                }
            }
        }
    }
}
