package com.cobblemonwilderness

import com.cobblemonwilderness.commands.WildernessCommands
import com.cobblemonwilderness.config.ResetState
import com.cobblemonwilderness.config.WildernessConfig
import com.cobblemonwilderness.reset.DimensionFolders
import com.cobblemonwilderness.reset.RegionResetter
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Caps wilderness world growth by regenerating chunks outside a persistent keep-box.
 *
 * All destructive deletion happens exactly once per boot, in [onServerAboutToStart],
 * BEFORE any level loads — so the target chunks are guaranteed unloaded and no region
 * file is open. Live commands only preview (read-only) or arm the next boot's pass.
 */
@Mod(value = CobblemonWilderness.MOD_ID, dist = [Dist.DEDICATED_SERVER])
class CobblemonWilderness(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Wilderness Reset initializing...")

        val configDir = FMLPaths.CONFIGDIR.get()
        config = WildernessConfig.load(configDir)
        state = ResetState.load(configDir)

        NeoForge.EVENT_BUS.addListener(::onServerAboutToStart)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)

        logger.info(
            "Cobblemon Wilderness Reset initialized (enabled={}, dryRun={}, intervalDays={}, box={})",
            config.enabled, config.dryRun, config.intervalDays, config.box,
        )
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        WildernessCommands.register(event.dispatcher)
    }

    private fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        runScheduledReset(event.server)
    }

    private fun runScheduledReset(server: MinecraftServer) {
        if (!config.enabled) {
            logger.info("Wilderness reset disabled (enabled=false) — skipping.")
            return
        }

        val now = System.currentTimeMillis()
        val intervalMillis = config.intervalDays.toLong() * MILLIS_PER_DAY
        val worldRoot = server.getWorldPath(LevelResource.ROOT)
        val box = config.effectiveBox()
        val forced = state.forceNextBoot
        var stateDirty = false
        logger.info("Keep-box (effective): X[{}..{}] Z[{}..{}]", box.minX, box.maxX, box.minZ, box.maxZ)

        for (dimId in config.dimensions) {
            val folder = DimensionFolders.resolve(worldRoot, dimId)
            if (folder == null) {
                logger.warn("Skipping dimension '{}': could not resolve its save folder", dimId)
                continue
            }

            val last = state.lastResetEpochMillis[dimId] ?: 0L

            // First time we ever observe this dimension: record a baseline and skip, so
            // flipping enabled=true doesn't trigger a surprise wipe on the very next boot.
            // Use /wildreset now to force the first real reset deliberately.
            if (last == 0L && !forced) {
                logger.info("[{}] first run — recording baseline, no reset this boot.", dimId)
                state.lastResetEpochMillis[dimId] = now
                stateDirty = true
                continue
            }

            val intervalElapsed = config.intervalDays > 0 && last > 0L &&
                (now - last) >= intervalMillis
            if (!forced && !intervalElapsed) {
                val daysLeft = (intervalMillis - (now - last)) / MILLIS_PER_DAY
                logger.info("[{}] next scheduled reset in ~{} day(s) — skipping.", dimId, daysLeft)
                continue
            }

            val reason = if (forced) "manually armed" else "interval elapsed"
            logger.info("[{}] running reset ({}, dryRun={})...", dimId, reason, config.dryRun)
            RegionResetter.run(dimId, folder, box, config.dryRun, logger)

            if (!config.dryRun) {
                state.lastResetEpochMillis[dimId] = now
                stateDirty = true
            }
        }

        if (state.forceNextBoot) {
            state.forceNextBoot = false
            stateDirty = true
        }
        if (stateDirty) state.save()
    }

    companion object {
        const val MOD_ID = "cobblemon_wilderness"
        const val MILLIS_PER_DAY = 86_400_000L
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var config: WildernessConfig
        lateinit var state: ResetState
    }
}
