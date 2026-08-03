package com.cobblemonroguelite.arena

import com.cobblemonroguelite.data.arena.ArenaPalettes
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.phys.AABB
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/arena")

/** What a stamp came to. Failures are named because each one needs a different fix from an operator. */
sealed interface StampResult {

    data object Stamped : StampResult

    /** The dimension in config does not exist on this server. */
    data class NoSuchDimension(val dimension: ResourceLocation) : StampResult

    /** Nothing at `data/<ns>/structure/<path>.nbt`. The one an owner using the `.nbt` override will hit. */
    data class TemplateMissing(val template: ResourceLocation) : StampResult

    /** Nothing at `data/<ns>/roguelite/arena_palettes/<path>.json`, or that file was rejected at load. */
    data class PaletteMissing(val palette: ResourceLocation) : StampResult

    /** The palette is bigger than [ArenaConfig.box] in one dimension or another. [detail] says which. */
    data class PaletteDoesNotFit(val palette: ResourceLocation, val detail: String) : StampResult

    /** The palette names a block this server does not have. Logged with every offending id. */
    data class PaletteBlockMissing(val palette: ResourceLocation) : StampResult

    /** Chunks could not be force-loaded, so anything we wrote would have gone nowhere. */
    data object NotLoaded : StampResult

    /** `StructureTemplate.placeInWorld` refused. */
    data class PlacementFailed(val template: ResourceLocation) : StampResult
}

/**
 * Putting an arena into a slot: check, clear, sweep, build.
 *
 * ### On assignment, never on release
 *
 * The obvious design is to tidy an arena up when its run ends. It is wrong for the same reason
 * §2.10 gives about interrupted battles: cleanup that runs on the way out is cleanup that a crash
 * between run-end and cleanup skips, and the next player to be handed that slot arrives in the last
 * player's wreckage — a half-broken build, a fainted opponent, an item on the floor. Doing it on the
 * way *in* makes the arena's state a function of the assignment that just happened rather than of a
 * previous assignment having ended tidily, so there is no window in which it can be wrong.
 *
 * That makes this callable more than once per run and it has to be, because §2.19 re-stamps at wave
 * band boundaries and §2.23 re-stamps on every session resume: the slot stays allocated for the whole
 * run and only the build changes. Nothing here reads or writes any state of its own, so a second call
 * is simply a first call again.
 *
 * ### Two kinds of build, one entry point
 *
 * [ArenaBuild.Palette] is §2.29's generated platform and is the one a server owner who cannot build
 * can author — see [ArenaGenerator]. [ArenaBuild.Template] is a hand-made `.nbt` and stays supported
 * for the owner who can. They are dispatched here rather than at the caller so that the entity sweep,
 * the chunk hold and the failure vocabulary are shared: a run does not care which kind of arena it is
 * standing in, and neither does anything above this.
 *
 * ### A build that is not there fails loudly
 *
 * Neither a missing `.nbt` nor a missing palette places anything and carries on. The distinction is
 * not academic: the arena dimension is void, so "placed nothing" means the next thing that happens is
 * a player falling out of the world. The shipped default names a template nobody has made (see
 * [ArenaBuilds]), so an unconfigured server hits this deliberately rather than silently.
 *
 * ### Clear before place, and only as much as is needed
 *
 * [net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.placeInWorld] only
 * writes the blocks the template contains, so stamping a small template over a large one leaves the
 * large one's walls standing. The template path therefore clears the whole declared [ArenaBox] — see
 * there for why the box is declared in config rather than taken from the template. The palette path
 * does not have to: it knows every cell it wants, so it clears only what the new arena does not claim.
 * Both go through [ArenaBoxScan], which skips chunk sections that hold nothing at all — in a void
 * dimension, nearly all of them.
 */
object ArenaStamper {

    /**
     * Flags for both the clear and the placement. `UPDATE_CLIENTS` (2) is what vanilla's own structure
     * block placement uses; neighbour updates are deliberately **not** requested — an arena is placed
     * as a finished thing, and running block updates across a whole build would cost far more than the
     * placement itself while changing nothing about the result in a void dimension.
     */
    private const val PLACE_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS

    /**
     * Empty [placement] of entities and make [build] stand in it.
     *
     * Call on the server thread. It force-loads, edits blocks and discards entities, none of which is
     * safe from a battle callback thread.
     */
    fun stamp(level: ServerLevel, placement: ArenaPlacement, build: ArenaBuild): StampResult = when (build) {
        is ArenaBuild.Template -> stampTemplate(level, placement, build.id)
        is ArenaBuild.Palette -> generate(level, placement, build.id)
    }

    /**
     * §2.29's generated arena.
     *
     * Everything that can fail is decided **before** the sweep and the first block write: the palette
     * has to exist, it has to fit the box, and every block it names has to be registered. An arena
     * that got halfway and then found a typo would leave the slot holding two builds at once, and the
     * player is standing in the difference.
     */
    private fun generate(level: ServerLevel, placement: ArenaPlacement, id: ResourceLocation): StampResult {
        val palette = ArenaPalettes[id]
        if (palette == null) {
            log.error(
                "roguelite: arena palette '{}' is not loaded — expected data/{}/roguelite/arena_palettes/{}.json " +
                    "in a datapack or a mod jar, or it was rejected at load (look above for its problems). " +
                    "Slot {} is NOT usable and no player will be sent to it.",
                id, id.namespace, id.path, placement.slot,
            )
            return StampResult.PaletteMissing(id)
        }

        val plan = when (val planned = ArenaPlan.of(palette, placement.box)) {
            is ArenaPlanResult.Planned -> planned.plan
            is ArenaPlanResult.DoesNotFit -> {
                log.error(
                    "roguelite: arena palette '{}' does not fit the configured arena box: {}. Slot {} is " +
                        "NOT usable — shrink the palette or grow ArenaConfig.box.",
                    id, planned.detail, placement.slot,
                )
                return StampResult.PaletteDoesNotFit(id, planned.detail)
            }
        }

        val resolved = ArenaGenerator.resolve(plan) ?: return StampResult.PaletteBlockMissing(id)

        if (!ArenaChunks.hold(level, placement.box)) return StampResult.NotLoaded
        sweep(level, placement.box)
        ArenaGenerator.apply(level, placement.box, resolved)
        return StampResult.Stamped
    }

    /** The hand-built override: clear the whole box, then let vanilla place the `.nbt`. */
    private fun stampTemplate(level: ServerLevel, placement: ArenaPlacement, template: ResourceLocation): StampResult {
        val structure = level.server.structureManager.get(template).orElse(null)
        if (structure == null) {
            log.error(
                "roguelite: arena template '{}' not found — expected data/{}/structure/{}.nbt in a " +
                    "datapack or a mod jar. Slot {} is NOT usable and no player will be sent to it.",
                template, template.namespace, template.path, placement.slot,
            )
            return StampResult.TemplateMissing(template)
        }

        if (!ArenaChunks.hold(level, placement.box)) return StampResult.NotLoaded

        sweep(level, placement.box)
        clear(level, placement.box)

        val settings = StructurePlaceSettings().setIgnoreEntities(false).setBoundingBox(placement.box)
        val placed = structure.placeInWorld(level, placement.origin, placement.origin, settings, level.random, PLACE_FLAGS)
        if (!placed) {
            log.error("roguelite: arena template '{}' refused to place at {}", template, placement.origin)
            return StampResult.PlacementFailed(template)
        }

        val size = structure.size
        // Not an error — an owner is allowed to ship a build smaller than the box, and usually will.
        // Larger is the one that matters: the overspill lands outside the volume the next stamp
        // clears and the entity sweep empties, so it accumulates across band transitions and, at
        // spacing this template was not checked against, could reach the arena next door. A palette
        // cannot do this — [ArenaPlan] refuses rather than warns, because it knows its own extent.
        if (size.x > placement.box.xSpan || size.y > placement.box.ySpan || size.z > placement.box.zSpan) {
            log.warn(
                "roguelite: arena template '{}' is {}x{}x{}, larger than the configured arena box " +
                    "{}x{}x{} — the overspill will not be cleared by later stamps",
                template, size.x, size.y, size.z,
                placement.box.xSpan, placement.box.ySpan, placement.box.zSpan,
            )
        }
        return StampResult.Stamped
    }

    /**
     * Remove everything in the box that is not a player.
     *
     * **Players are excluded and that is load-bearing**, not politeness. A band transition re-stamps
     * a slot that its own player is standing in, and an unfiltered sweep would `discard()` them —
     * which for a `ServerPlayer` is removal without a disconnect, i.e. the worst possible outcome of a
     * cosmetic scenery change. Everything else in an arena got there because of a run and has no
     * reason to outlive the stamp: opponents, dropped items, projectiles.
     */
    private fun sweep(level: ServerLevel, box: BoundingBox) {
        val aabb = AABB(
            box.minX().toDouble(), box.minY().toDouble(), box.minZ().toDouble(),
            (box.maxX() + 1).toDouble(), (box.maxY() + 1).toDouble(), (box.maxZ() + 1).toDouble(),
        )
        val victims = level.getEntitiesOfClass(Entity::class.java, aabb) { it !is Player }
        victims.forEach { it.discard() }
        if (victims.isNotEmpty()) log.debug("roguelite: swept {} entities from arena box {}", victims.size, box)
    }

    private fun clear(level: ServerLevel, box: BoundingBox) {
        val air = Blocks.AIR.defaultBlockState()
        ArenaBoxScan.forEachNonAir(level, box) { pos -> level.setBlock(pos, air, PLACE_FLAGS) }
    }
}
