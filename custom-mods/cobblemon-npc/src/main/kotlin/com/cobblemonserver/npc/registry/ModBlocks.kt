package com.cobblemonserver.npc.registry

import com.cobblemonserver.npc.CobblemonNpc
import com.cobblemonserver.npc.block.BlockHutGymLeader
import com.minecolonies.api.items.ItemBlockHut
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

object ModBlocks {

    private val BLOCKS: DeferredRegister.Blocks =
        DeferredRegister.createBlocks(CobblemonNpc.MOD_ID)

    private val ITEMS: DeferredRegister.Items =
        DeferredRegister.createItems(CobblemonNpc.MOD_ID)

    val HUT_GYM_LEADER = BLOCKS.register(BlockHutGymLeader.HUT_NAME) { _ -> BlockHutGymLeader() }

    val HUT_GYM_LEADER_ITEM = ITEMS.register(BlockHutGymLeader.HUT_NAME) { _ ->
        ItemBlockHut(HUT_GYM_LEADER.get() as BlockHutGymLeader, Item.Properties())
    }

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
        ITEMS.register(modBus)
    }
}
