package com.cobblemonbridge.npc

import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps tagged mobs rooted to their spawn position WITHOUT freezing the AI that drives natural
 * head/body movement (`LookAtPlayer`, `LookAround`, idle animation, etc.).
 *
 * Why event-based and not a per-tick world scan? The previous 0.7.6 implementation walked
 * `level.getEntitiesOfClass(..., AABB)` with a 60M×60M world-spanning AABB every tick.
 * NeoForge/lithium's `LongAVLTreeSet.subSet` over the loaded entity-section index made each
 * tick take seconds; the server watchdog killed the process and systemd crash-looped it.
 * Switched to a lazy registry populated by [EntityJoinLevelEvent] / [EntityLeaveLevelEvent] —
 * the tick iterates only the mobs actually loaded, with no level-wide scan.
 *
 * Behaviour:
 *   1. On entity-join, if the mob carries a `cobblemon_bridge.anchor[.<scope>]` tag, add it to
 *      [anchored]. Fires on chunk load too (resurrection from save), so restart-persisted mobs
 *      come back into the registry automatically. Also matches the legacy
 *      `cobblemon_bridge.market_vendor[.<scope>]` tag for backwards compatibility with vendors
 *      spawned before the rename.
 *   2. On entity-leave (chunk unload, removal, dimension transfer), drop it.
 *   3. Each tick, for each registered mob:
 *      - Prune if removed/dead.
 *      - Flip `noAi` off (defensive — keeps natural look-at animations alive).
 *      - If the mob drifted past [DRIFT_TOLERANCE_SQ] from its captured anchor on the
 *        horizontal plane, snap it back with zero momentum. Rotation is preserved so the AI's
 *        head/body animation isn't visibly interrupted.
 *
 * Anchor coords are stashed in entity NBT ([persistentData]), so they survive chunk unloads
 * and restarts. Captured once on the first time the entity is observed (either on join or on
 * tick if join was missed for any reason — defensive).
 */
object EntityAnchor {

    /** Tag prefix that opts a mob into anchoring. Matches the unscoped tag
     *  (`cobblemon_bridge.anchor`) and every scoped variant (`cobblemon_bridge.anchor.gym_1`). */
    private const val ANCHOR_TAG_PREFIX = "cobblemon_bridge.anchor"

    /** Legacy tag prefix kept so market vendors spawned before the rename keep working without
     *  re-running their spawn function. New spawns should use [ANCHOR_TAG_PREFIX]. */
    private const val LEGACY_VENDOR_TAG_PREFIX = "cobblemon_bridge.market_vendor"

    /** Snap-back threshold (blocks, horizontal). Tiny — fires on the first sub-tick the AI
     *  tries to step, so the body never visibly leaves the anchor. */
    private const val DRIFT_TOLERANCE_SQ = 0.05 * 0.05

    /** Anchor coordinates persist in entity NBT under these keys (cb = cobblemon-bridge). */
    private const val ANCHOR_X = "cb_anchor_x"
    private const val ANCHOR_Y = "cb_anchor_y"
    private const val ANCHOR_Z = "cb_anchor_z"

    /** UUID → live Mob reference. Populated by [onEntityJoin], pruned by [onEntityLeave]
     *  and defensively by the tick when an entry's entity becomes invalid. */
    private val anchored: MutableMap<UUID, Mob> = ConcurrentHashMap()

    private fun isAnchored(m: Mob): Boolean =
        m.tags.any {
            it == ANCHOR_TAG_PREFIX || it.startsWith("$ANCHOR_TAG_PREFIX.") ||
                it == LEGACY_VENDOR_TAG_PREFIX || it.startsWith("$LEGACY_VENDOR_TAG_PREFIX.")
        }

    @SubscribeEvent
    fun onEntityJoin(event: EntityJoinLevelEvent) {
        val m = event.entity as? Mob ?: return
        if (isAnchored(m)) anchored[m.uuid] = m
    }

    @SubscribeEvent
    fun onEntityLeave(event: EntityLeaveLevelEvent) {
        val m = event.entity as? Mob ?: return
        anchored.remove(m.uuid)
    }

    @SubscribeEvent
    fun onServerTickPost(event: ServerTickEvent.Post) {
        val iter = anchored.entries.iterator()
        while (iter.hasNext()) {
            val (_, mob) = iter.next()
            if (mob.isRemoved || !mob.isAlive) {
                iter.remove()
                continue
            }
            if (mob.isNoAi) mob.isNoAi = false

            val anchor = anchorOf(mob)
            val dx = mob.x - anchor.x
            val dz = mob.z - anchor.z
            if (dx * dx + dz * dz <= DRIFT_TOLERANCE_SQ) continue

            // Snap back to anchor with rotation preserved so vanilla LookAt continues seamlessly.
            mob.moveTo(anchor.x, anchor.y, anchor.z, mob.yRot, mob.xRot)
            mob.deltaMovement = Vec3.ZERO
        }
    }

    private fun anchorOf(mob: Mob): Vec3 {
        val data = mob.persistentData
        if (!data.contains(ANCHOR_X)) {
            data.putDouble(ANCHOR_X, mob.x)
            data.putDouble(ANCHOR_Y, mob.y)
            data.putDouble(ANCHOR_Z, mob.z)
        }
        return Vec3(
            data.getDouble(ANCHOR_X),
            data.getDouble(ANCHOR_Y),
            data.getDouble(ANCHOR_Z),
        )
    }
}
