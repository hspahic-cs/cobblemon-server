package com.cobblemonserver.npc

import com.cobblemonserver.npc.battle.NpcBattleHandler
import com.cobblemonserver.npc.command.GymCommands
import com.cobblemonserver.npc.data.NpcTeamStore
import com.cobblemonserver.npc.data.ProfessionPoolLoader
import com.cobblemonserver.npc.economy.EconomyBridge
import com.cobblemonserver.npc.economy.RewardsConfig
import com.cobblemonserver.npc.gym.GymAssignmentListener
import com.cobblemonserver.npc.gym.GymLeaderPoolLoader
import com.cobblemonserver.npc.progression.DimensionSpawnBlocker
import com.cobblemonserver.npc.registry.ModBlocks
import com.cobblemonserver.npc.registry.ModBuildings
import com.cobblemonserver.npc.registry.ModJobs
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import org.slf4j.LoggerFactory

@Mod(CobblemonNpc.MOD_ID)
class CobblemonNpc(modBus: IEventBus) {

    init {
        logger.info("cobblemon-npc (NeoForge) initializing")

        // Mod-bus registrations (attachment types, blocks, items, building/job entries).
        NpcTeamStore.register(modBus)
        ModBlocks.register(modBus)
        ModBuildings.register(modBus)
        ModJobs.register(modBus)

        // Game-bus subscriptions (commands, server lifecycle).
        val gameBus = NeoForge.EVENT_BUS
        gameBus.register(GymCommands)
        gameBus.register(this)

        // Cobblemon event subscriptions (battle victories, dimension spawn blocking).
        NpcBattleHandler.register()
        DimensionSpawnBlocker.register()

        // Minecolonies event subscriptions (job assignment → gym-leader hire/fire).
        GymAssignmentListener.register()

        logger.info("cobblemon-npc initialized")
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        val server = event.server
        ProfessionPoolLoader.load(server)
        GymLeaderPoolLoader.load(server)
        RewardsConfig.load()
        DimensionSpawnBlocker.load()
        EconomyBridge.attach(server)
        if (EconomyBridge.isAvailable()) {
            logger.info("cobblemon-npc: CobbleDollars detected — battle rewards active")
        } else {
            logger.info("cobblemon-npc: CobbleDollars not loaded — rewards disabled at runtime")
        }
    }

    companion object {
        const val MOD_ID = "cobblemon_npc"
        val logger = LoggerFactory.getLogger(MOD_ID)

        fun id(path: String): ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
    }
}
