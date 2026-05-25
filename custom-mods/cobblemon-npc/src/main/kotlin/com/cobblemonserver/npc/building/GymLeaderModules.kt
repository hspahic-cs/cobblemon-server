package com.cobblemonserver.npc.building

import com.cobblemonserver.npc.registry.ModJobs
import com.minecolonies.api.colony.buildings.registry.BuildingEntry
import com.minecolonies.api.entity.citizen.Skill
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView
import java.util.function.Function
import java.util.function.Supplier

object GymLeaderModules {

    val GYM_LEADER_WORK: BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> =
        BuildingEntry.ModuleProducer(
            "gym_leader_work",
            Supplier {
                WorkerBuildingModule(
                    ModJobs.GYM_LEADER.get(),
                    Skill.Adaptability,
                    Skill.Focus,
                    true,
                    Function { _ -> 1 }
                )
            },
            Supplier { Supplier { WorkerBuildingModuleView() } }
        )
}
