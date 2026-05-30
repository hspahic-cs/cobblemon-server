package com.cobblemonbridge.npc

import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps market-vendor villagers rooted to their spawn position WITHOUT freezing the AI that
 * drives natural head/body movement (`LookAtPlayer`, `LookAround`, trade-look behaviours).
 *
 * Why event-based and not a per-tick world scan? The previous 0.7.6 implementation walked
 * `level.getEntitiesOfClass(Villager, AABB)` with a 60M×60M world-spanning AABB every tick.
 * NeoForge/lithium's `LongAVLTreeSet.subSet` over the loaded entity-section index made each
 * tick take seconds; the server watchdog killed the process and systemd crash-looped it.
 * Switched to a lazy registry populated by [EntityJoinLevelEvent] / [EntityLeaveLevelEvent] —
 * the tick iterates only the ~10 vendors actually loaded, with no level-wide scan.
 *
 * Behaviour:
 *   1. On entity-join, if the villager carries a `cobblemon_bridge.market_vendor[.<scope>]`
 *      tag, add it to [vendors]. Fires on chunk load too (resurrection from save), so
 *      restart-persisted vendors come back into the registry automatically.
 *   2. On entity-leave (chunk unload, removal, dimension transfer), drop it.
 *   3. Each tick, for each registered vendor:
 *      - Prune if removed/dead.
 *      - Flip `noAi` off (pre-0.7.6 vendors had `NoAI:1b` in the spawn function — auto-upgrade).
 *      - If the vendor drifted past [DRIFT_TOLERANCE_SQ] from its captured anchor on the
 *        horizontal plane, snap it back with zero momentum. Rotation is preserved so the AI's
 *        head/body animation isn't visibly interrupted.
 *
 * Anchor coords are stashed in entity NBT ([persistentData]), so they survive chunk unloads
 * and restarts. Captured once on the first time the entity is observed (either on join or on
 * tick if join was missed for any reason — defensive).
 */
object MarketVendorAnchor {

    /** Tag prefix that opts a villager into anchoring. Matches the unscoped market vendor
     *  tag (`cobblemon_bridge.market_vendor`) and every scoped variant
     *  (`cobblemon_bridge.market_vendor.tm_fire`, etc.). */
    private const val VENDOR_TAG_PREFIX = "cobblemon_bridge.market_vendor"

    /** Snap-back threshold (blocks, horizontal). Tiny — fires on the first sub-tick the AI
     *  tries to step, so the body never visibly leaves the anchor. */
    private const val DRIFT_TOLERANCE_SQ = 0.05 * 0.05

    /** Anchor coordinates persist in entity NBT under these keys (cb = cobblemon-bridge). */
    private const val ANCHOR_X = "cb_anchor_x"
    private const val ANCHOR_Y = "cb_anchor_y"
    private const val ANCHOR_Z = "cb_anchor_z"

    /** UUID → live Villager reference. Populated by [onEntityJoin], pruned by [onEntityLeave]
     *  and defensively by the tick when an entry's entity becomes invalid. */
    private val vendors: MutableMap<UUID, Villager> = ConcurrentHashMap()

    private fun isVendor(v: Villager): Boolean =
        v.tags.any { it == VENDOR_TAG_PREFIX || it.startsWith("$VENDOR_TAG_PREFIX.") }

    @SubscribeEvent
    fun onEntityJoin(event: EntityJoinLevelEvent) {
        val v = event.entity as? Villager ?: return
        if (isVendor(v)) vendors[v.uuid] = v
    }

    @SubscribeEvent
    fun onEntityLeave(event: EntityLeaveLevelEvent) {
        val v = event.entity as? Villager ?: return
        vendors.remove(v.uuid)
    }

    @SubscribeEvent
    fun onServerTickPost(event: ServerTickEvent.Post) {
        val iter = vendors.entries.iterator()
        while (iter.hasNext()) {
            val (_, vendor) = iter.next()
            if (vendor.isRemoved || !vendor.isAlive) {
                iter.remove()
                continue
            }
            if (vendor.isNoAi) vendor.isNoAi = false

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
