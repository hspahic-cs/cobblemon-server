package com.cobblemonwilderness.warn

import com.cobblemonwilderness.CobblemonWilderness
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Warns players who stray outside the keep-box that anything they build or store out there
 * will be wiped by the next reset. Fires once when they cross the boundary outward, and again
 * on login if they log in already outside. A reassurance message fires when they cross back in.
 *
 * All checks are server-thread only (server tick + login/logout events), so the tracking set
 * needs no synchronization. Warnings only show while the feature is enabled.
 */
object BoundaryWarden {

    /** Players currently known to be outside the box, so we warn on transition not every tick. */
    private val outside = HashSet<UUID>()

    /** Position check cadence — every second is plenty for a movement warning. */
    private const val CHECK_INTERVAL_TICKS = 20
    private var tickCounter = 0

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    fun onServerTick(event: ServerTickEvent.Post) {
        if (!warningsActive()) {
            if (outside.isNotEmpty()) outside.clear()
            return
        }
        tickCounter++
        if (tickCounter % CHECK_INTERVAL_TICKS != 0) return

        for (player in event.server.playerList.players) {
            evaluate(player, isLogin = false)
        }
    }

    fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (!warningsActive()) return
        evaluate(player, isLogin = true)
    }

    fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        outside.remove(player.uuid)
    }

    private fun warningsActive(): Boolean {
        val cfg = CobblemonWilderness.config
        return cfg.enabled && cfg.warnPlayersOutsideBox
    }

    private fun evaluate(player: ServerPlayer, isLogin: Boolean) {
        val cfg = CobblemonWilderness.config
        val dimId = player.level().dimension().location().toString()
        // Only dimensions subject to reset have an "outside". Elsewhere, treat as safe.
        if (dimId !in cfg.dimensions) {
            if (outside.remove(player.uuid) && !isLogin) sendBackInside(player)
            return
        }

        val pos = player.blockPosition()
        val isOutside = !cfg.effectiveBox().contains(pos.x, pos.z)
        val wasOutside = player.uuid in outside

        when {
            isOutside && (!wasOutside || isLogin) -> {
                outside.add(player.uuid)
                sendWarning(player, dimId, isLogin)
            }
            !isOutside && wasOutside -> {
                outside.remove(player.uuid)
                sendBackInside(player)
            }
            isOutside -> outside.add(player.uuid) // keep tracked (e.g. login while already flagged)
        }
    }

    private fun sendWarning(player: ServerPlayer, dimId: String, isLogin: Boolean) {
        val box = CobblemonWilderness.config.effectiveBox()
        val lead = if (isLogin) "You are logged in OUTSIDE the protected zone." else "You have left the protected zone."
        player.sendSystemMessage(Component.literal("§c⚠ $lead"))
        player.sendSystemMessage(
            Component.literal("§fAnything built or stored here will be §creset ${resetWhen(dimId)}§f.")
        )
        player.sendSystemMessage(
            Component.literal("§7Safe build zone: X[${box.minX}..${box.maxX}] Z[${box.minZ}..${box.maxZ}]")
        )
    }

    private fun sendBackInside(player: ServerPlayer) {
        player.sendSystemMessage(Component.literal("§a✔ Back inside the protected zone — builds here are safe."))
    }

    /** Human phrase for when the player's current dimension is next reset. */
    private fun resetWhen(dimId: String): String {
        val cfg = CobblemonWilderness.config
        if (cfg.intervalDays <= 0) return "during the next scheduled maintenance"
        val last = CobblemonWilderness.state.lastResetEpochMillis[dimId] ?: 0L
        if (last == 0L) return "on the next scheduled reset"

        val next = last + cfg.intervalDays.toLong() * CobblemonWilderness.MILLIS_PER_DAY
        val now = System.currentTimeMillis()
        val days = ((next - now) / CobblemonWilderness.MILLIS_PER_DAY).coerceAtLeast(0)
        val date = runCatching {
            Instant.ofEpochMilli(next).atZone(ZoneId.of(cfg.displayTimeZone)).format(dateFormat)
        }.getOrElse { Instant.ofEpochMilli(next).atZone(ZoneId.of("UTC")).format(dateFormat) }
        return "on or after $date (~$days day(s) from now)"
    }
}
