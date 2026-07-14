package com.cobblemonwilderness.mixin;

import com.cobblemonwilderness.gen.WildernessDimensionAware;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Registered via {@code cobblemon_wilderness.gate.mixins.json} (required=false): a mapping break
 * degrades to "no relocation" rather than a boot crash-loop.
 *
 * Adds a per-dimension "is overworld" tag to {@code ChunkGeneratorStructureState}. The state is
 * created once per dimension in {@code ChunkMap}'s constructor, which is where {@link ChunkMapMixin}
 * sets this flag. The gen-path relocation mixin reads it to gate the salt to the overworld only.
 *
 * <p>Do not enable until dev structure-dump verification confirms nether/end placement is unchanged.
 */
@Mixin(ChunkGeneratorStructureState.class)
public class ChunkGeneratorStructureStateMixin implements WildernessDimensionAware {

    @Unique
    private boolean cobblemonwilderness$overworld = false;

    @Override
    public boolean cobblemonwilderness$isOverworld() {
        return this.cobblemonwilderness$overworld;
    }

    @Override
    public void cobblemonwilderness$setOverworld(boolean overworld) {
        this.cobblemonwilderness$overworld = overworld;
    }
}
