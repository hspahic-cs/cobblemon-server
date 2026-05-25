package com.cobblemonserver.npc.building

import com.minecolonies.api.colony.IColony
import com.minecolonies.core.colony.buildings.AbstractBuilding
import net.minecraft.core.BlockPos

/**
 * Minecolonies building that houses a Pokémon Gym Leader. Five levels; each level
 * caps the max team tier the assigned citizen can promote to (see
 * `TeamProgressionManager.onLoss`).
 */
class BuildingGymLeader(colony: IColony, pos: BlockPos) : AbstractBuilding(colony, pos) {

    override fun getSchematicName(): String = SCHEMATIC_NAME

    override fun getMaxBuildingLevel(): Int = MAX_BUILDING_LEVEL

    companion object {
        const val SCHEMATIC_NAME = "gymleader"
        const val MAX_BUILDING_LEVEL = 5
    }
}
