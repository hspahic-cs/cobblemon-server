package com.cobblemonwilderness.mixin;

import com.cobblemonwilderness.gen.WildernessGenState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * CHECKPOINT DELIVERABLE — NOT YET REGISTERED in cobblemon_wilderness.mixins.json.
 *
 * The /locate half of the overworld gate. {@code getNearestGeneratedStructure} recomputes potential
 * structure chunks when searching for a structure; it receives a {@code LevelReader}. We mark the
 * thread as overworld around the {@code getPotentialStructureChunk} call using the reader's dimension,
 * so /locate applies the SAME relocation salt as generation did — otherwise it would point at the old
 * vanilla coordinates. Both call sites of {@code getPotentialStructureChunk} are instrumented (this one
 * and the gen path), which keeps the salt deterministic across contexts.
 *
 * <p>Do not enable until dev verification confirms /locate points at the relocated structures.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateMixin {

    @WrapOperation(
        method = "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;IIIZJLnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;)Lcom/mojang/datafixers/util/Pair;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;getPotentialStructureChunk(JII)Lnet/minecraft/world/level/ChunkPos;"
        )
    )
    private static ChunkPos cobblemonwilderness$markOverworldForLocate(
        RandomSpreadStructurePlacement self, long seed, int x, int z, Operation<ChunkPos> original,
        @Local(argsOnly = true) LevelReader level
    ) {
        boolean overworld = level instanceof Level lvl && lvl.dimension() == Level.OVERWORLD;
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
