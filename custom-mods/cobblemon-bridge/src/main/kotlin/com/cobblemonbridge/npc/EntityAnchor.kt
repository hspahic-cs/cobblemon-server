package com.cobblemonbridge.npc

import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

/**
 * Keeps tagged mobs rooted to their spawn position WITHOUT freezing the AI that drives natural
 * head/body movement (`LookAtPlayer`, `LookAround`, idle animation, etc.).
 *
 * Why per-entity tick (not a registry populated by EntityJoinLevelEvent)? The entity-join
 * approach has a race: when our gym mcfunctions run `rctmod trainer summon_persistent`, the
 * resulting `EntityJoinLevelEvent` fires synchronously, BEFORE the next-line `tag … add
 * cobblemon_bridge.anchor` command runs. The trainer is observed without the tag, the registry
 * skips it, and no later event re-checks. Same-tick tag-after-spawn flows are common (RCT's
 * summon command doesn't accept inline tags), so the registry approach silently no-ops.
 *
 * Why not a world-spanning per-tick AABB scan either? The 0.7.6 implementation walked
 * `level.getEntitiesOfClass(..., AABB)` with a 60M×60M AABB every tick and crashed the server
 * (NeoForge/lithium's `LongAVLTreeSet.subSet` over the loaded entity-section index made each
 * tick take seconds; the server watchdog killed the process and systemd crash-looped it).
 *
 * `EntityTickEvent.Post` is the right hammer: NeoForge dispatches it inside each entity's
 * existing tick path — no new scan work, no synchronization overhead. We add a fast-path
 * predicate (`Mob` cast + tag check) that returns in nanoseconds for everything that isn't
 * anchored. Per-tick cost scales with loaded-mob count, dominated by the entities Minecraft is
 * already ticking.
 *
 * Behaviour:
 *   - Each anchored mob's tick: capture anchor coords on first observation (NBT, persists
 *     across chunk unload + restart), reset `noAi` if set, snap back if the AI tried to step
 *     past [DRIFT_TOLERANCE_SQ] this tick. Rotation is preserved so vanilla LookAt continues
 *     seamlessly.
 *
 * Tags recognized:
 *   - `cobblemon_bridge.anchor[.<scope>]` — primary opt-in.
 *   - `cobblemon_bridge.market_vendor[.<scope>]` — legacy, keeps existing market vendors
 *     anchored without a datapack rerun.
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

    private fun isAnchored(m: Mob): Boolean =
        m.tags.any {
            it == ANCHOR_TAG_PREFIX || it.startsWith("$ANCHOR_TAG_PREFIX.") ||
                it == LEGACY_VENDOR_TAG_PREFIX || it.startsWith("$LEGACY_VENDOR_TAG_PREFIX.")
        }

    @SubscribeEvent
    fun onEntityTickPost(event: EntityTickEvent.Post) {
        val mob = event.entity as? Mob ?: return
        if (!isAnchored(mob)) return
        if (!mob.isAlive) return
        if (mob.isNoAi) mob.isNoAi = false

        val anchor = anchorOf(mob)
        val dx = mob.x - anchor.x
        val dz = mob.z - anchor.z
        if (dx * dx + dz * dz <= DRIFT_TOLERANCE_SQ) return

        // Snap back to anchor with rotation preserved so vanilla LookAt continues seamlessly.
        mob.moveTo(anchor.x, anchor.y, anchor.z, mob.yRot, mob.xRot)
        mob.deltaMovement = Vec3.ZERO
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
