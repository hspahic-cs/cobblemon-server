package com.cobblemonwilderness.commands

import com.cobblemonwilderness.CobblemonWilderness
import com.cobblemonwilderness.reset.DimensionFolders
import com.cobblemonwilderness.reset.RegionResetter
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.level.storage.LevelResource

/**
 * `/wildreset` — op-only (permission level 4) controls. Nothing here deletes on a live
 * world: `preview` is read-only and `now` only arms the next boot's pass.
 */
object WildernessCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("wildreset")
                .requires { it.hasPermission(4) }
                .then(Commands.literal("status").executes(::status))
                .then(Commands.literal("preview").executes(::preview))
                .then(Commands.literal("now").executes(::armNow))
                .then(Commands.literal("cancel").executes(::cancel))
                .executes(::status),
        )
    }

    private fun status(ctx: CommandContext<CommandSourceStack>): Int {
        val src = ctx.source
        val cfg = CobblemonWilderness.config
        val state = CobblemonWilderness.state
        val now = System.currentTimeMillis()

        src.sendSuccess({ Component.literal("§6=== Wilderness Reset ===") }, false)
        src.sendSuccess({ Component.literal("enabled: ${cfg.enabled}   dryRun: ${cfg.dryRun}   intervalDays: ${cfg.intervalDays}") }, false)
        val box = cfg.effectiveBox()
        src.sendSuccess({ Component.literal("box (configured): X[${cfg.box.minX}..${cfg.box.maxX}] Z[${cfg.box.minZ}..${cfg.box.maxZ}]") }, false)
        val snapNote = if (cfg.snapToRegions) " §7(region-aligned)" else ""
        src.sendSuccess({ Component.literal("box (enforced): X[${box.minX}..${box.maxX}] Z[${box.minZ}..${box.maxZ}]$snapNote") }, false)
        src.sendSuccess({ Component.literal("forceNextBoot: ${state.forceNextBoot}") }, false)
        for (dimId in cfg.dimensions) {
            val last = state.lastResetEpochMillis[dimId] ?: 0L
            val line = if (last == 0L) {
                "  $dimId: no baseline yet (resets on first boot with enabled=true)"
            } else {
                val daysSince = (now - last) / CobblemonWilderness.MILLIS_PER_DAY
                val daysLeft = cfg.intervalDays - daysSince
                "  $dimId: last reset ${daysSince}d ago, next in ~${daysLeft.coerceAtLeast(0)}d"
            }
            src.sendSuccess({ Component.literal(line) }, false)
        }
        return 1
    }

    /** Read-only scan of the current world using the configured box — safe on a live server. */
    private fun preview(ctx: CommandContext<CommandSourceStack>): Int {
        val src = ctx.source
        val cfg = CobblemonWilderness.config
        val worldRoot = src.server.getWorldPath(LevelResource.ROOT)
        val box = cfg.effectiveBox()

        src.sendSuccess({ Component.literal("§6Wilderness reset preview (read-only):") }, false)
        for (dimId in cfg.dimensions) {
            val folder = DimensionFolders.resolve(worldRoot, dimId)
            if (folder == null) {
                src.sendSuccess({ Component.literal("  §c$dimId: unresolved dimension id") }, false)
                continue
            }
            val report = RegionResetter.run(
                dimId, folder, box, dryRun = true, maxDeleteFraction = cfg.maxDeleteFraction, log = CobblemonWilderness.logger,
            )
            val mb = report.bytesFreed / (1024 * 1024)
            if (report.aborted) {
                src.sendSuccess({
                    Component.literal("  §c$dimId: would delete ${report.regionsDeleted}/${report.regionsKept + report.regionsDeleted} regions — exceeds maxDeleteFraction (${cfg.maxDeleteFraction}); a real run would ABORT. Check the box.")
                }, false)
            } else {
                src.sendSuccess({
                    Component.literal("  $dimId: would delete ${report.regionsDeleted} region(s) (~${mb} MB), keep ${report.regionsKept}")
                }, false)
            }
        }
        return 1
    }

    private fun armNow(ctx: CommandContext<CommandSourceStack>): Int {
        val src = ctx.source
        val cfg = CobblemonWilderness.config
        val state = CobblemonWilderness.state
        state.forceNextBoot = true
        state.save()

        src.sendSuccess({ Component.literal("§aReset armed — it will run on the next server restart.") }, false)
        if (!cfg.enabled) {
            src.sendSuccess({ Component.literal("§e⚠ enabled=false in config — the armed reset will NOT run until you set enabled=true.") }, false)
        }
        if (cfg.dryRun) {
            src.sendSuccess({ Component.literal("§e⚠ dryRun=true — next boot will only LOG what it would delete, not delete it.") }, false)
        }
        return 1
    }

    private fun cancel(ctx: CommandContext<CommandSourceStack>): Int {
        val state = CobblemonWilderness.state
        state.forceNextBoot = false
        state.save()
        ctx.source.sendSuccess({ Component.literal("§aArmed reset cancelled.") }, false)
        return 1
    }
}
