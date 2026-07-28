package com.cobblemonroguelite

import com.cobblemonroguelite.data.RogueliteData
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A run-based roguelite mode for Cobblemon. See `docs/pokerogue-mode-plan.md` for the intent and
 * the decision record, and `docs/pokerogue-mode-design.md` for the architecture.
 *
 * This module is deliberately **standalone**: it depends on Cobblemon and nothing else of ours.
 * Anything server-specific — our economy, our arenas, the poke-engine AI bridge — is reached
 * through an interface declared here and implemented in `cobblemon-bridge`, so the mode stays
 * buildable and shippable on its own. Nothing in here may import `com.cobblemonbridge`.
 *
 * Nothing is wired up yet beyond datapack loading: the run model, its store and the data
 * registries exist, the run loop does not.
 */
@Mod(CobblemonRoguelite.MOD_ID)
class CobblemonRoguelite(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Roguelite initializing (data + run model only — no run loop yet)")
        // enqueueWork, NOT the parallel body of common setup. Our registries land in Cobblemon's
        // CobblemonDataProvider, which keeps them in an unsynchronized LinkedHashSet that its own
        // parallel setup handler is populating at the same time — registering from the parallel
        // phase races that set, and the failure would be a datapack that silently never loads.
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(RogueliteData::registerAll) }
    }

    companion object {
        const val MOD_ID = "cobblemon_roguelite"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
