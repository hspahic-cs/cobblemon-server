package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Where a player was standing before their run took them away, and where they are put back.
 *
 * ### Why this is persisted on the run and not recomputed
 *
 * The alternative is "send them to world spawn when the run ends", which is what the fallback does
 * and which is a bad default: a player who started a run from their base is entitled to be returned
 * to it, and a mode that relocates you every time you play it is a mode people stop playing near
 * anything they care about.
 *
 * It has to be on [RunState] rather than in memory because the exit paths that matter are the ones
 * that cross a restart. A run ended by a wipe on the wave after a server bounce, or a player who logs
 * in inside an arena whose run was voided while they were offline, both need this and neither has
 * anything in memory to read.
 *
 * ### The dimension is stored as an id, and may not exist by the time it is read
 *
 * A run is a multi-day commitment (§2.19) and dimensions come from datapacks and mods, so the
 * dimension a player entered from can be gone when they leave. That is why the ejection path falls
 * back to world spawn rather than treating an unresolvable dimension as an error — the alternative is
 * a player stuck in an arena because the place they came from was uninstalled.
 */
data class RunEntryPoint(
    val dimension: ResourceLocation,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
) {
    fun toNbt(): CompoundTag = CompoundTag().apply {
        putString("dimension", dimension.toString())
        putDouble("x", x)
        putDouble("y", y)
        putDouble("z", z)
        putFloat("yaw", yaw)
        putFloat("pitch", pitch)
    }

    companion object {

        fun of(player: ServerPlayer): RunEntryPoint = RunEntryPoint(
            dimension = player.level().dimension().location(),
            x = player.x,
            y = player.y,
            z = player.z,
            yaw = player.yRot,
            pitch = player.xRot,
        )

        /**
         * Null when the tag is absent or its dimension id is unparseable.
         *
         * Returning null rather than throwing is the same call [RunState] makes about `payoutTable`:
         * a run whose entry point cannot be read is a run whose player gets sent to world spawn, and
         * discarding the whole checkpoint over it would cost them the run instead.
         */
        fun fromNbt(tag: CompoundTag): RunEntryPoint? {
            // The emptiness check is not redundant with tryParse: an absent key reads as "", and
            // ResourceLocation validates *characters*, so "" parses happily into `minecraft:` — an id
            // that resolves to no dimension and would send the player to world spawn by a longer
            // route than the null this returns.
            val raw = tag.getString("dimension").takeIf { it.isNotEmpty() } ?: return null
            val dimension = ResourceLocation.tryParse(raw) ?: return null
            return RunEntryPoint(
                dimension = dimension,
                x = tag.getDouble("x"),
                y = tag.getDouble("y"),
                z = tag.getDouble("z"),
                yaw = tag.getFloat("yaw"),
                pitch = tag.getFloat("pitch"),
            )
        }
    }
}
