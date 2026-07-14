package com.cobblemonwilderness.commands

import com.cobblemonwilderness.CobblemonWilderness
import com.cobblemonwilderness.reset.DimensionFolders
import com.cobblemonwilderness.reset.RegionDisposition
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
                .then(
                    Commands.literal("now")
                        .then(Commands.literal("force").executes(::armNowForce))
                        .executes(::armNow),
                )
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
        src.sendSuccess({ Component.literal("enabled: ${cfg.enabled}   dryRun: ${cfg.dryRun}") }, false)
        val box = cfg.effectiveBox()
        src.sendSuccess({ Component.literal("box (configured): X[${cfg.box.minX}..${cfg.box.maxX}] Z[${cfg.box.minZ}..${cfg.box.maxZ}]") }, false)
        val snapNote = if (cfg.snapToRegions) " §7(region-aligned)" else ""
        src.sendSuccess({ Component.literal("box (enforced): X[${box.minX}..${box.maxX}] Z[${box.minZ}..${box.maxZ}]$snapNote") }, false)
        src.sendSuccess({ Component.literal("armed (forceNextBoot): ${state.forceNextBoot}   scheduleTimeZone: ${cfg.scheduleTimeZone}") }, false)
        for (dimId in cfg.dimensions) {
            val last = state.lastResetEpochMillis[dimId] ?: 0L
            val line = if (last == 0L) {
                "  $dimId: no baseline yet (prunes only when armed via /wildreset now)"
            } else {
                val daysSince = (now - last) / CobblemonWilderness.MILLIS_PER_DAY
                "  $dimId: last reset ${daysSince}d ago"
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
                dimId, folder, box, dryRun = true, minBoxSideBlocks = cfg.minKeepBoxSideBlocks,
                backupTarget = null, log = CobblemonWilderness.logger,
            )
            val mb = report.bytesFreed / (1024 * 1024)
            if (report.aborted) {
                src.sendSuccess({
                    Component.literal("  §c$dimId: keep-box is degenerate (a side < ${cfg.minKeepBoxSideBlocks} blocks); a real run would ABORT and delete nothing. Check the box.")
                }, false)
            } else {
                src.sendSuccess({
                    Component.literal("  $dimId: would delete ${report.regionsDeleted} region(s) (~${mb} MB), keep ${report.regionsKept} (inside ${report.keptInside}, straddle ${report.keptStraddle}, monument ${report.keptMonument})")
                }, false)
            }

            // Per-candidate rows so the operator sees WHY each outside region is kept vs deleted.
            // Monuments first (the ones that matter), then deletables; cap so a huge frontier doesn't flood chat.
            val ordered = report.scans.sortedByDescending { it.disposition.ordinal }
            for (scan in ordered.take(MAX_PREVIEW_ROWS)) {
                val tag = when (scan.disposition) {
                    RegionDisposition.DELETABLE -> "§cdelete"
                    RegionDisposition.KEPT_MONUMENT -> "§akept (monument)"
                }
                src.sendSuccess({ Component.literal("    §7r.${scan.rx}.${scan.rz}: $tag") }, false)
            }
            if (ordered.size > MAX_PREVIEW_ROWS) {
                src.sendSuccess({ Component.literal("    §7… ${ordered.size - MAX_PREVIEW_ROWS} more outside region(s) not shown") }, false)
            }
        }
        return 1
    }

    /** Cap on per-region rows printed by /wildreset preview (keeps chat readable on a big frontier). */
    private const val MAX_PREVIEW_ROWS = 40

    private fun armNow(ctx: CommandContext<CommandSourceStack>): Int {
        val src = ctx.source
        val cfg = CobblemonWilderness.config
        val state = CobblemonWilderness.state
        state.forceNextBoot = true
        state.forceBreakerOverride = false // plain arm keeps the fraction breaker enforced
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

    /**
     * `/wildreset now force` — arms the next boot AND bypasses the degenerate-box safety breaker for
     * that one run (a deliberate override). Everything else — `dryRun`, baseline-skip — still applies.
     * Both flags are consumed after the run.
     */
    private fun armNowForce(ctx: CommandContext<CommandSourceStack>): Int {
        val src = ctx.source
        val cfg = CobblemonWilderness.config
        val state = CobblemonWilderness.state
        state.forceNextBoot = true
        state.forceBreakerOverride = true
        state.save()

        src.sendSuccess({ Component.literal("§aReset armed WITH breaker override — it will run on the next server restart.") }, false)
        src.sendSuccess({ Component.literal("§c⚠ The degenerate-box safety breaker (min side ${cfg.minKeepBoxSideBlocks} blocks) will be BYPASSED for this one run.") }, false)
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
        state.forceBreakerOverride = false
        state.save()
        ctx.source.sendSuccess({ Component.literal("§aArmed reset cancelled.") }, false)
        return 1
    }
}
