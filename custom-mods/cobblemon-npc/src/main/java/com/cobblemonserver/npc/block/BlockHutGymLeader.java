package com.cobblemonserver.npc.block;

import com.cobblemonserver.npc.CobblemonNpc;
import com.cobblemonserver.npc.registry.ModBuildings;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class BlockHutGymLeader extends AbstractBlockHut<BlockHutGymLeader>
{
    public static final String HUT_NAME = "hut_gym_leader";

    @NotNull
    @Override
    public String getHutName()
    {
        return HUT_NAME;
    }

    @Override
    public ResourceLocation getRegistryName()
    {
        return ResourceLocation.fromNamespaceAndPath(CobblemonNpc.MOD_ID, HUT_NAME);
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.GYM_LEADER.get();
    }
}
