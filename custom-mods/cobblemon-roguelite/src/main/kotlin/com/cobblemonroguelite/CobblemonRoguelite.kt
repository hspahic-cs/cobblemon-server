package com.cobblemonroguelite

import com.cobblemonroguelite.arena.ArenaSpawnSuppressor
import com.cobblemonroguelite.battle.RunWaveBattles
import com.cobblemonroguelite.data.RogueliteData
import com.cobblemonroguelite.run.RunCommands
import com.cobblemonroguelite.run.RunLoginHooks
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
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
 * The run lifecycle is wired, and so is the battle: [com.cobblemonroguelite.battle.RunWaveBattles]
 * fills [com.cobblemonroguelite.run.RunWaves] and fights §2.14's wild waves in full. Its trainer and
 * boss waves are not fought here and cannot be — those are authored RCT trainers and RCT's licence is
 * unverified (§1.2), so they go out through
 * [com.cobblemonroguelite.integration.RunTrainerBattles], whose default refuses rather than counting
 * the wave as won.
 */
@Mod(CobblemonRoguelite.MOD_ID)
class CobblemonRoguelite(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Roguelite initializing (run lifecycle and wild wave battles wired)")
        // enqueueWork, NOT the parallel body of common setup. Our registries land in Cobblemon's
        // CobblemonDataProvider, which keeps them in an unsynchronized LinkedHashSet that its own
        // parallel setup handler is populating at the same time — registering from the parallel
        // phase races that set, and the failure would be a datapack that silently never loads.
        modBus.addListener<FMLCommonSetupEvent> {
            it.enqueueWork(RogueliteData::registerAll)
            // Same phase and the same reason: Cobblemon's observables are plain lists behind no lock,
            // and subscribing from the parallel body races every other mod doing the same. Unlike the
            // registries this one has no visible failure if it loses — the subscription is simply
            // dropped and wild Pokémon start appearing in arenas.
            it.enqueueWork(ArenaSpawnSuppressor::register)
            // Same phase, same race, and one extra reason: this subscribes to four of Cobblemon's
            // battle observables, and a subscription dropped by that race would be a wave whose
            // faints, result and on-field Pokémon are never reported — a run that fights and never
            // finishes, with nothing in the log to say why.
            it.enqueueWork(RunWaveBattles::install)
        }

        // Game bus, not the mod bus: commands and player lifecycle are server-runtime events.
        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent> { RunCommands.register(it.dispatcher) }
        NeoForge.EVENT_BUS.register(RunLoginHooks)
    }

    companion object {
        const val MOD_ID = "cobblemon_roguelite"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
