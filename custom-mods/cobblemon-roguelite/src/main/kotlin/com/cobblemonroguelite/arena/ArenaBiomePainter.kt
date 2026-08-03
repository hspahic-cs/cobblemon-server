package com.cobblemonroguelite.arena

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.commands.FillBiomeCommand
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.levelgen.structure.BoundingBox
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/arena")

/** What a repaint came to. Named per case because each one needs a different fix from an operator. */
sealed interface BiomePaintResult {

    data object Painted : BiomePaintResult

    /** No biome is registered under that id — a typo, or a datapack that is not installed. */
    data class NoSuchBiome(val biome: ResourceLocation) : BiomePaintResult

    /** `fill` refused. [detail] is its own message, which names the limit or the unloaded chunk. */
    data class Failed(val biome: ResourceLocation, val detail: String) : BiomePaintResult
}

/**
 * Repainting the arena box's Minecraft biome — the half of §2.24 that sells it.
 *
 * ### Why vanilla's command class and not our own chunk writes
 *
 * `FillBiomeCommand.fill` is public static in 1.21.1 and does the entire job: it rewrites the biome
 * container of every chunk it touches, marks them unsaved, and — the part nobody wants to
 * reimplement — resends them to every client watching, through `ChunkMap.resendBiomesForChunks`.
 * Writing that by hand means reaching into chunk internals and getting the resend right, for a
 * feature whose whole value is that the player *sees* the change immediately.
 *
 * A stamped structure alone reads as scenery. This is what makes the arena read as somewhere else:
 * sky colour, fog, water and grass tint, ambient loops and biome music all follow the biome.
 *
 * ### What this is not allowed to be
 *
 * Fatal. A repaint that fails leaves a correctly stamped, entirely playable arena that looks wrong,
 * and the arena path it sits in is the one that puts a player somewhere they can stand. So every
 * failure here is logged and returned, and [RunArenas] carries on.
 *
 * ### Threading and preconditions
 *
 * Server thread, and **only after [ArenaChunks.hold]**: `fill` fetches chunks with `load = false` and
 * refuses outright if any of them is cold, which on a slot nobody is standing in is most of them.
 */
object ArenaBiomePainter {

    /**
     * Repaint [box] to [biome].
     *
     * Split into [BiomeFillSlices] pieces because the default arena box is four times the default
     * `commandModificationBlockLimit`; see there for why that is arithmetic rather than a game rule
     * to raise.
     *
     * A failure part-way through leaves the arena half repainted, and that is accepted rather than
     * rolled back: the next prepare repaints the whole box again (the run's painted-biome marker is
     * only written on success), so the inconsistency lasts until the next wave rather than until an
     * operator notices.
     */
    fun paint(level: ServerLevel, box: BoundingBox, biome: ResourceLocation): BiomePaintResult {
        val key = ResourceKey.create(Registries.BIOME, biome)
        val holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(key).orElse(null)
            ?: return BiomePaintResult.NoSuchBiome(biome)

        val limit = level.gameRules.getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT)
        val slices = BiomeFillSlices.slice(box, limit)
        if (slices.isEmpty()) {
            return BiomePaintResult.Failed(
                biome,
                "commandModificationBlockLimit is $limit, which is below one 4x4x4 biome cell",
            )
        }

        slices.forEachIndexed { index, slice ->
            val outcome = FillBiomeCommand.fill(
                level,
                BlockPos(slice.minX(), slice.minY(), slice.minZ()),
                BlockPos(slice.maxX(), slice.maxY(), slice.maxZ()),
                holder,
            )
            val error = outcome.right().orElse(null)
            if (error != null) {
                return BiomePaintResult.Failed(
                    biome,
                    "slice ${index + 1} of ${slices.size} was refused: ${error.message}",
                )
            }
        }
        log.debug("roguelite: repainted arena box {} to biome {} in {} slice(s)", box, biome, slices.size)
        return BiomePaintResult.Painted
    }
}
