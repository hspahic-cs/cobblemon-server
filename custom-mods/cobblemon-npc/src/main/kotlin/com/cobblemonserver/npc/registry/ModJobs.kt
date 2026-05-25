package com.cobblemonserver.npc.registry

import com.cobblemonserver.npc.CobblemonNpc
import com.cobblemonserver.npc.job.JobGymLeader
import com.minecolonies.api.colony.ICitizenData
import com.minecolonies.api.colony.IColonyView
import com.minecolonies.api.colony.ICitizenDataView
import com.minecolonies.api.colony.jobs.IJob
import com.minecolonies.api.colony.jobs.IJobView
import com.minecolonies.api.colony.jobs.registry.JobEntry
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl
import com.minecolonies.core.colony.jobs.views.DefaultJobView
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier

object ModJobs {

    private val JOBS: DeferredRegister<JobEntry> =
        DeferredRegister.create(CommonMinecoloniesAPIImpl.JOBS, CobblemonNpc.MOD_ID)

    val GYM_LEADER = JOBS.register("gym_leader") { ->
        JobEntry.Builder()
            .setJobProducer(Function<ICitizenData, IJob<*>> { data -> JobGymLeader(data) })
            .setJobViewProducer(Supplier { BiFunction<IColonyView, ICitizenDataView, IJobView> { colony, citizen -> DefaultJobView(colony, citizen) } })
            .setRegistryName(JobGymLeader.JOB_ID)
            .createJobEntry()
    }

    fun register(modBus: IEventBus) {
        JOBS.register(modBus)
    }
}
