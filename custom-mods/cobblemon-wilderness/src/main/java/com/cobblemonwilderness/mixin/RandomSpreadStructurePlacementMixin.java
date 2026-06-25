package com.cobblemonwilderness.mixin;

import com.cobblemonwilderness.gen.WildernessGenState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Relocates random-spread structures (villages, monuments, etc.) that fall in the wilderness.
 *
 * Every structure-set placement bottoms out in {@code getPotentialStructureChunk}, which seeds
 * its RNG once via {@code setLargeFeatureWithSalt(seed, cellX, cellZ, salt)} where (cellX,cellZ)
 * is the spacing grid cell. We XOR a per-cycle salt into that call for cells lying wholly outside
 * the keep-box, so the chosen chunk within the cell moves each reset cycle. Cells touching the box
 * (and everything when the feature is off) get 0 — i.e. byte-identical vanilla placement.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {

    @WrapOperation(
        method = "getPotentialStructureChunk(JII)Lnet/minecraft/world/level/ChunkPos;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"
        )
    )
    private void cobblemonwilderness$reseedWilderness(
        WorldgenRandom rng, long seed, int cellX, int cellZ, int salt, Operation<Void> original
    ) {
        int spacing = ((RandomSpreadStructurePlacement) (Object) this).spacing();
        int extra = WildernessGenState.cellSalt(cellX, cellZ, spacing);
        original.call(rng, seed, cellX, cellZ, salt ^ extra);
    }
}
