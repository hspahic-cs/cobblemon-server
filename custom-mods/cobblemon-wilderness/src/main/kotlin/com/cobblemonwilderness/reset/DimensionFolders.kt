package com.cobblemonwilderness.reset

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.dimension.DimensionType
import java.nio.file.Path

/** Maps a dimension id to its on-disk save folder under the world root. */
internal object DimensionFolders {

    /**
     * Resolves the per-dimension storage folder (overworld → root, nether → DIM-1,
     * end → DIM1, custom → dimensions/ns/path) for [dimensionId], or null if the id
     * is malformed. Does not require the level to be loaded.
     */
    fun resolve(worldRoot: Path, dimensionId: String): Path? {
        val loc: ResourceLocation = ResourceLocation.tryParse(dimensionId) ?: return null
        val key: ResourceKey<net.minecraft.world.level.Level> =
            ResourceKey.create(Registries.DIMENSION, loc)
        return DimensionType.getStorageFolder(key, worldRoot)
    }
}
