package com.cobblemonbridge.npc

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/**
 * Keeps market-vendor villagers rooted to their spawn position WITHOUT freezing the AI that
 * drives natural head/body movement (`LookAtPlayer`, `LookAround`, trade-look behaviours).
 *
 * Why not `NoAI:1b` like the original spawn function? `NoAI` freezes everything — the vendor
 * becomes a head-locked statue. Players read that as broken. We want vanilla villager AI's
 * smooth head tracking but no wandering off the placement spot.
 *
 * Approach (post-tick, every tick):
 *   1. Ensure `noAi == false` on every tagged vendor — auto-upgrades pre-0.7.7 vendors that
 *      were spawned with `NoAI:1b`. The spawn function in `market/spawn_npc.mcfunction` no
 *      longer sets `NoAI`, so newly summoned vendors are already correct.
 *   2. Anchor each vendor's position. The anchor is captured once on first sighting and
 *      stashed in entity NBT (`persistentData`), so it survives server restarts and chunk
 *      unloads. Every tick, if the vendor drifted past [DRIFT_TOLERANCE_SQ] from the anchor
 *      on the horizontal plane, snap it back with zero momentum. Rotation is preserved across
 *      the snap, so the AI's continuous head/body movement is uninterrupted — what the player
 *      sees is the villager looking around naturally while staying exactly in place.
 *
 * Drift tolerance is small (~0.05 blocks) so the snap fires every tick the AI tries to step,
 * meaning the villager's body never visibly leaves the anchor. With ~10 vendors per server
 * the per-tick scan cost is negligible.
 *
 * Edge cases:
 *  - Vendor pushed by minecart / piston / player: snapped back same tick.
 *  - Night-cycle sleep behaviour: villager tries to seek bed → AI steps → snap-back → no
 *    visible wandering. May twitch slightly during the brief AI step before the snap.
 *  - Anchor in invalid terrain (mid-air etc.): the vendor is reset to the same invalid spot,
 *    which is stable — invulnerable + persistence means no consequence.
 */
object MarketVendorAnchor {

    /** Tag prefix that opts a villager into this anchoring. Matches the unscoped market
     *  vendor tag (`cobblemon_bridge.market_vendor`) and every scoped variant
     *  (`cobblemon_bridge.market_vendor.tm_fire`, etc.). */
    private const val VENDOR_TAG_PREFIX = "cobblemon_bridge.market_vendor"

    /** Snap-back threshold (blocks, horizontal). Tiny — keeps the vendor visibly stationary
     *  while letting the AI run for one tick before correction. */
    private const val DRIFT_TOLERANCE_SQ = 0.05 * 0.05

    /** Anchor coordinates persist in entity NBT under these keys (cb = cobblemon-bridge). */
    private const val ANCHOR_X = "cb_anchor_x"
    private const val ANCHOR_Y = "cb_anchor_y"
    private const val ANCHOR_Z = "cb_anchor_z"

    /** World-spanning AABB for [Level.getEntitiesOfClass] — bounded by world-border range so
     *  we don't pass infinities. The lookup walks loaded chunks only. */
    private val WORLD_BOX = AABB(-30_000_000.0, -1000.0, -30_000_000.0,
                                  30_000_000.0,  1000.0,  30_000_000.0)

    @SubscribeEvent
    fun onServerTickPost(event: ServerTickEvent.Post) {
        for (level in event.server.allLevels) {
            anchorVendorsIn(level)
        }
    }

    private fun anchorVendorsIn(level: ServerLevel) {
        val vendors = level.getEntitiesOfClass(Villager::class.java, WORLD_BOX).filter { v ->
            v.tags.any { tag -> tag == VENDOR_TAG_PREFIX || tag.startsWith("$VENDOR_TAG_PREFIX.") }
        }
        if (vendors.isEmpty()) return

        for (vendor in vendors) {
            if (vendor.isNoAi) vendor.isNoAi = false  // pre-0.7.7 vendor upgrade

            val anchor = anchorOf(vendor)
            val dx = vendor.x - anchor.x
            val dz = vendor.z - anchor.z
            if (dx * dx + dz * dz <= DRIFT_TOLERANCE_SQ) continue

            // Snap back to anchor with rotation preserved so vanilla LookAt continues seamlessly.
            vendor.moveTo(anchor.x, anchor.y, anchor.z, vendor.yRot, vendor.xRot)
            vendor.deltaMovement = Vec3.ZERO
        }
    }

    private fun anchorOf(vendor: Villager): Vec3 {
        val data = vendor.persistentData
        if (!data.contains(ANCHOR_X)) {
            data.putDouble(ANCHOR_X, vendor.x)
            data.putDouble(ANCHOR_Y, vendor.y)
            data.putDouble(ANCHOR_Z, vendor.z)
        }
        return Vec3(
            data.getDouble(ANCHOR_X),
            data.getDouble(ANCHOR_Y),
            data.getDouble(ANCHOR_Z),
        )
    }
}
