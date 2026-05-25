package com.cobblemonserver.npc.job

import com.cobblemonserver.npc.CobblemonNpc
import com.minecolonies.api.client.render.modeltype.ModModelTypes
import com.minecolonies.api.colony.ICitizenData
import com.minecolonies.core.colony.jobs.AbstractJob
import com.minecolonies.core.entity.ai.workers.AbstractAISkeleton
import net.minecraft.resources.ResourceLocation

/**
 * Gym Leader job. No work loop — citizens just stand in the arena and battle players
 * on right-click. Assignment drives the NpcTeamData gym-leader flag via GymAssignmentListener.
 */
class JobGymLeader(entity: ICitizenData?) : AbstractJob<AbstractAISkeleton<JobGymLeader>, JobGymLeader>(entity) {

    override fun generateAI(): AbstractAISkeleton<JobGymLeader>? = null

    override fun getModel(): ResourceLocation = ModModelTypes.CITIZEN_ID

    companion object {
        val JOB_ID: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(CobblemonNpc.MOD_ID, "gym_leader")
    }
}
