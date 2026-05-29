package com.cobblemonbridge.npc

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import kotlin.math.atan2

/**
 * Server-side rotation tick for `NoAI:1b` market vendor villagers (and any future tagged
 * villager that should track the nearest player). The spawn function freezes the entity with
 * `NoAI:1b` so it stays exactly where it was placed; vanilla head-tracking goals don't run.
 * Without this hook the vendor renders as a static head-locked statue, which reads as broken.
 *
 * Approach: every [TICK_INTERVAL] ticks, walk loaded villagers per level, filter by the
 * [VENDOR_TAG_PREFIX] tag, find the nearest player within [SEARCH_RADIUS], and snap the
 * villager's body + head yaw to face them. Tiny constant cost: a server with ~50 vendors
 * touches ~50 entities every 0.5s. We don't try to be smooth — the vanilla
 * `LookAtPlayerGoal` animation is a 40-tick lerp; without AI we'd have to drive that manually
 * and the snap reads fine for stationary shopkeepers.
 *
 * No-op when no player is in range — the vendor keeps its last orientation.
 *
 * Scope: villagers only, because that's what the market spawn function summons. RCT trainer
 * NPCs are a different entity class; if we want them to head-track too, add a parallel branch
 * keyed off `EntityType.getKey(entity.type) == rctmod:trainer`.
 */
object NpcFaceNearestPlayer {

    /** Tag prefix that opts a villager into this rotation tick. Matches the unscoped
     *  market vendor tag (`cobblemon_bridge.market_vendor`) and every scoped variant
     *  (`cobblemon_bridge.market_vendor.tm_fire`, etc.). */
    private const val VENDOR_TAG_PREFIX = "cobblemon_bridge.market_vendor"

    /** Refresh cadence in ticks. 10 = twice per second; visually instant for a stationary NPC. */
    private const val TICK_INTERVAL = 10

    /** Max horizontal distance (blocks) to a player that triggers tracking. */
    private const val SEARCH_RADIUS = 12.0
    private const val SEARCH_RADIUS_SQ = SEARCH_RADIUS * SEARCH_RADIUS

    /** World-spanning AABB used to enumerate loaded villagers via [Level.getEntitiesOfClass].
     *  Bounded by Minecraft's world-border range so we don't pass infinities. The lookup
     *  itself only walks loaded chunks, so the cost is bounded by entities in memory. */
    private val WORLD_BOX = AABB(-30_000_000.0, -1000.0, -30_000_000.0,
                                  30_000_000.0,  1000.0,  30_000_000.0)

    private var subTickCounter = 0

    @SubscribeEvent
    fun onServerTickPost(event: ServerTickEvent.Post) {
        subTickCounter++
        if (subTickCounter < TICK_INTERVAL) return
        subTickCounter = 0

        for (level in event.server.allLevels) {
            rotateVendorsIn(level)
        }
    }

    private fun rotateVendorsIn(level: ServerLevel) {
        // World-spanning AABB → all loaded villagers in this dim; then filter by our tag.
        val vendors = level.getEntitiesOfClass(Villager::class.java, WORLD_BOX).filter { v ->
            v.tags.any { tag -> tag == VENDOR_TAG_PREFIX || tag.startsWith("$VENDOR_TAG_PREFIX.") }
        }
        if (vendors.isEmpty()) return

        for (vendor in vendors) {
            val nearest = nearestPlayer(level, vendor.x, vendor.z) ?: continue
            val yaw = yawTo(vendor.x, vendor.z, nearest.x, nearest.z)
            vendor.setYRot(yaw)
            vendor.yBodyRot = yaw
            vendor.yHeadRot = yaw
        }
    }

    private fun nearestPlayer(level: ServerLevel, x: Double, z: Double): net.minecraft.world.entity.player.Player? =
        level.players()
            .filter {
                val dx = it.x - x
                val dz = it.z - z
                dx * dx + dz * dz <= SEARCH_RADIUS_SQ
            }
            .minByOrNull {
                val dx = it.x - x
                val dz = it.z - z
                dx * dx + dz * dz
            }

    /** Standard Minecraft yaw: 0° = south (+Z), 90° = west (-X). Matches what
     *  `Entity.lookAt` produces, so the villager faces the player like vanilla. */
    private fun yawTo(fromX: Double, fromZ: Double, toX: Double, toZ: Double): Float {
        val dx = toX - fromX
        val dz = toZ - fromZ
        return Math.toDegrees(atan2(-dx, dz)).toFloat()
    }
}
