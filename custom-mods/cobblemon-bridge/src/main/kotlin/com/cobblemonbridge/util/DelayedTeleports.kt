package com.cobblemonbridge.util

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.gymtp.WarpPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tick-delayed [WarpPos] teleports. Battle-end hooks fire while the client is still tearing
 * down the battle GUI — an instant teleport there reads as a glitch and can race the
 * battle-end packet stream. Scheduling a ~1s delay lets the victory chat + GUI close land
 * first, then warps cleanly.
 *
 * One pending teleport per player — scheduling again overwrites (last write wins), which is
 * what every caller wants: if two hooks fire on the same victory, the later intent should
 * hold, not queue a double-warp.
 */
object DelayedTeleports {

    private data class Pending(val target: WarpPos, var ticksLeft: Int)

    private val pending: MutableMap<UUID, Pending> = ConcurrentHashMap()

    /** Default delay — battle GUI teardown comfortably finishes within a second. */
    const val DEFAULT_DELAY_TICKS = 20

    fun schedule(player: ServerPlayer, target: WarpPos, delayTicks: Int = DEFAULT_DELAY_TICKS) {
        pending[player.uuid] = Pending(target, delayTicks)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        if (pending.isEmpty()) return
        val due = pending.entries.filter { --it.value.ticksLeft <= 0 }
        for ((uuid, p) in due) {
            pending.remove(uuid)
            val player = event.server.playerList.getPlayer(uuid) ?: continue  // logged out — drop
            teleport(player, p.target)
        }
    }

    /** Resolve the WarpPos dimension and teleport. Same resolution dance as `/spawn`. */
    fun teleport(player: ServerPlayer, target: WarpPos) {
        val rl = ResourceLocation.tryParse(target.world) ?: run {
            CobblemonBridge.logger.warn("delayed-tp: invalid world id {}", target.world)
            return
        }
        val level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, rl)) ?: run {
            CobblemonBridge.logger.warn("delayed-tp: dimension not loaded {}", target.world)
            return
        }
        player.teleportTo(level, target.x, target.y, target.z, target.yaw, target.pitch)
    }
}
