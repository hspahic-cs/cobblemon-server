package com.cobblemonwilderness.mixin;

import com.cobblemonwilderness.gen.WildernessDimensionAware;
import com.cobblemonwilderness.gen.WildernessGenState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Registered via {@code cobblemon_wilderness.gate.mixins.json} (required=false): if a future
 * MC/mapping break stops this from applying, the gate degrades to "no relocation" rather than
 * crash-looping boot — the salt hook stays loaded but the overworld flag is never set.
 *
 * The GEN-PATH half of the overworld gate. {@code isPlacementChunk(state, x, z)} is where chunk
 * generation asks whether a structure lands in a chunk; it receives the per-dimension
 * {@code ChunkGeneratorStructureState}. We read that state's overworld tag (set by {@link ChunkMapMixin})
 * and mark the current worldgen thread around the inner {@code getPotentialStructureChunk} call, so the
 * existing salt hook ({@link RandomSpreadStructurePlacementMixin}) applies the relocation salt only in
 * the overworld. Clears the mark in a finally so nested/other placements are unaffected.
 *
 * <p>Do not enable until dev structure-dump verification confirms nether/end placement is unchanged
 * and /locate stays consistent with generated positions.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadPlacementDimensionMixin {

    @WrapOperation(
        method = "isPlacementChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;getPotentialStructureChunk(JII)Lnet/minecraft/world/level/ChunkPos;"
        )
    )
    private ChunkPos cobblemonwilderness$markOverworldForGen(
        RandomSpreadStructurePlacement self, long seed, int x, int z, Operation<ChunkPos> original,
        @Local(argsOnly = true) ChunkGeneratorStructureState state
    ) {
        boolean overworld = state instanceof WildernessDimensionAware aware && aware.cobblemonwilderness$isOverworld();
        if (overworld) {
            WildernessGenState.beginOverworld();
        }
        try {
            return original.call(self, seed, x, z);
        } finally {
            if (overworld) {
                WildernessGenState.endOverworld();
            }
        }
    }
}
