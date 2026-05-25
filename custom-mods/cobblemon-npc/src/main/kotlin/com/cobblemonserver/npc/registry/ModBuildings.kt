package com.cobblemonserver.npc.registry

import com.cobblemonserver.npc.CobblemonNpc
import com.cobblemonserver.npc.block.BlockHutGymLeader
import com.cobblemonserver.npc.building.BuildingGymLeader
import com.cobblemonserver.npc.building.GymLeaderModules
import com.minecolonies.api.colony.buildings.registry.BuildingEntry
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl
import com.minecolonies.core.colony.buildings.modules.BuildingModules
import com.minecolonies.core.colony.buildings.views.EmptyView
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.BiFunction
import java.util.function.Supplier

object ModBuildings {

    const val GYM_LEADER_ID: String = "hut_gym_leader"

    private val BUILDINGS: DeferredRegister<BuildingEntry> =
        DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, CobblemonNpc.MOD_ID)

    @JvmField
    val GYM_LEADER = BUILDINGS.register(GYM_LEADER_ID) { ->
        BuildingEntry.Builder()
            .setBuildingBlock(ModBlocks.HUT_GYM_LEADER.get() as BlockHutGymLeader)
            .setBuildingProducer(BiFunction { colony, pos -> BuildingGymLeader(colony, pos) })
            .setBuildingViewProducer(Supplier { BiFunction { view, pos -> EmptyView(view, pos) } })
            .setRegistryName(ResourceLocation.fromNamespaceAndPath(CobblemonNpc.MOD_ID, GYM_LEADER_ID))
            .addBuildingModuleProducer(GymLeaderModules.GYM_LEADER_WORK)
            .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
            .createBuildingEntry()
    }

    fun register(modBus: IEventBus) {
        BUILDINGS.register(modBus)
    }
}
