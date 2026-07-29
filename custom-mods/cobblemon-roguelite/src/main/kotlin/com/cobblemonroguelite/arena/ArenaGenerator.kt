package com.cobblemonroguelite.arena

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.BoundingBox
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/arena")

/**
 * §2.29's generated arena: a plan, resolved against the block registry, made true in the world.
 *
 * ### The three steps are separate on purpose
 *
 * [ArenaPlan.of] decides the shape with no world. [resolve] turns block ids into states and is where
 * an unknown id is caught. [apply] is the only part that touches the level. That split is what lets
 * every failure be found **before a single block is written** — an arena that half-generated and then
 * discovered a typo would be worse than one that refused, because the player is standing in the
 * difference.
 *
 * ### An unknown block id refuses the arena
 *
 * Naming a block that is not registered — a typo, or a mod that was removed from the pack — refuses
 * the stamp and names the id. It does not substitute stone and it does not place nothing: a
 * substitution is a palette that quietly stopped meaning what it says, and placing nothing is a run
 * in a void dimension with no floor. Both are silent; the refusal is not, and [RunArenas] surfaces it
 * as a run that will not start rather than a run that cannot be played.
 *
 * **The power spot is the single exception**, and the asymmetry is deliberate. Mega Showdown is a
 * soft dependency (§2.5) — the mode has to run without it — so `mega_showdown:power_spot` being
 * absent is an ordinary server configuration, not a mistake. It is logged at ERROR because §2.5's
 * whole gimmick ladder silently stops working, and then the arena is built anyway: a Dynamax-less
 * arena is playable, a floorless one is not.
 *
 * ### Idempotence
 *
 * Running this twice with the same palette and box writes nothing the second time. [ArenaPlan] is a
 * pure function of its inputs, [apply] clears exactly what the plan does not claim and writes exactly
 * what differs, so a repeat run finds every cell already correct. That matters because §2.19's band
 * transitions and §2.23's session resumes both re-run it, and because "re-running produces the same
 * arena" is what makes a crash mid-generation self-healing rather than a slot that has to be repaired.
 */
object ArenaGenerator {

    /**
     * The same flags [ArenaStamper] places a template with, and for its reasons: clients are told, no
     * neighbour updates run. An arena is placed as a finished thing, and neighbour updates across a
     * whole platform would cost more than the placement while changing nothing in a void dimension.
     */
    private const val PLACE_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS

    /**
     * A plan whose block ids have all been found. Nothing here can fail any more.
     *
     * @property powerSpot null when the palette opted out **or** when Mega Showdown is not installed;
     *   the two are indistinguishable from here and are distinguished in the log, where the second one
     *   is an ERROR.
     */
    class Resolved internal constructor(
        val plan: ArenaPlan,
        val states: Map<BlockPos, BlockState>,
        val powerSpot: Pair<BlockPos, BlockState>?,
    )

    /**
     * Look up every block the plan names.
     *
     * Returns null and logs when any of them is unknown, having first collected **all** of them:
     * [com.cobblemonroguelite.data.DataProblems]' argument applied one layer down, because an owner
     * whose modpack lost a mod has as many broken ids as that mod had blocks, and reporting them one
     * server restart at a time is not a fix cycle anybody completes.
     */
    fun resolve(plan: ArenaPlan): Resolved? {
        val ids = plan.blockIds
        val missing = ids.filterNot(::isRegistered).sortedBy { it.toString() }
        if (missing.isNotEmpty()) {
            log.error(
                "roguelite: arena palette '{}' names {} block(s) that this server does not have: {}. " +
                    "The arena is NOT built and no player will be sent to it — fix the id, or install " +
                    "the mod that provides it.",
                plan.palette, missing.size, missing.joinToString(", "),
            )
            return null
        }

        val states = LinkedHashMap<BlockPos, BlockState>(plan.blocks.size)
        // One state per distinct id, then shared. A 62x62 platform is ~3,800 cells off maybe four
        // ids, and `defaultBlockState()` is a field read rather than a computation — this is about
        // the map staying obviously one-block-per-cell, not about the microseconds.
        val byId = ids.associateWith { stateOf(it) }
        plan.blocks.forEach { (pos, id) -> states[pos] = byId.getValue(id) }

        val powerSpot = plan.powerSpot?.let { pos ->
            val id = ResourceLocation.parse(ArenaPlan.POWER_SPOT_BLOCK)
            if (!isRegistered(id)) {
                log.error(
                    "roguelite: arena palette '{}' wants a '{}' but no such block is registered — Mega " +
                        "Showdown is not installed, or has renamed it. The arena is built without one, " +
                        "which means Dynamax and Tera do not work inside runs (plan section 2.5).",
                    plan.palette, ArenaPlan.POWER_SPOT_BLOCK,
                )
                null
            } else {
                pos to stateOf(id)
            }
        }
        return Resolved(plan, states, powerSpot)
    }

    /**
     * Make [box] match [resolved]: clear what the plan does not claim, then place what differs.
     *
     * ### Clear-what-is-not-claimed, rather than clear-everything
     *
     * The template path clears the whole box before placing, because a `.nbt` only writes the cells it
     * contains and a smaller build over a larger one leaves the larger one's walls standing
     * ([ArenaStamper]). A plan knows every cell it wants, so the same guarantee is available for far
     * less: anything non-air that the plan does not claim is by definition the previous build, and
     * anything the plan does claim is about to be overwritten anyway. [ArenaBoxScan] then keeps the
     * search off the empty sections, which in a void dimension is nearly all of them.
     *
     * The clear runs **before** the placement rather than after. Both orders end in the same world —
     * the passes touch disjoint cells — but this one never has the arena briefly holding two builds
     * at once, which is what a player watching a band transition would see.
     */
    fun apply(level: ServerLevel, box: BoundingBox, resolved: Resolved) {
        val air = Blocks.AIR.defaultBlockState()
        var cleared = 0
        ArenaBoxScan.forEachNonAir(level, box) { pos ->
            if (!resolved.plan.claims(pos)) {
                level.setBlock(pos, air, PLACE_FLAGS)
                cleared++
            }
        }

        var placed = 0
        resolved.states.forEach { (pos, state) ->
            // Only what differs. A re-stamp of the same palette therefore writes nothing at all, which
            // is what makes §2.23's every-session re-stamp cheap rather than merely correct.
            if (level.getBlockState(pos) != state) {
                level.setBlock(pos, state, PLACE_FLAGS)
                placed++
            }
        }
        resolved.powerSpot?.let { (pos, state) ->
            if (level.getBlockState(pos) != state) {
                level.setBlock(pos, state, PLACE_FLAGS)
                placed++
            }
        }

        log.debug(
            "roguelite: generated arena from palette '{}' — {} block(s) placed, {} cleared, power spot {}",
            resolved.plan.palette, placed, cleared,
            resolved.powerSpot?.first ?: "absent",
        )
    }

    /**
     * Whether the registry actually holds [id].
     *
     * `containsKey` and not `get`, because the block registry is *defaulted*: `get` on an unknown id
     * returns `minecraft:air` rather than null, so the obvious null check would turn every typo into
     * a silently empty arena — the exact failure this whole path exists to make loud.
     */
    private fun isRegistered(id: ResourceLocation): Boolean = BuiltInRegistries.BLOCK.containsKey(id)

    private fun stateOf(id: ResourceLocation): BlockState = BuiltInRegistries.BLOCK.get(id).defaultBlockState()
}
