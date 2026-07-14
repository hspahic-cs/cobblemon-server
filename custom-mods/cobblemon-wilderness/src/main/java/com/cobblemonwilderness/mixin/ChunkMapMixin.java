package com.cobblemonwilderness.mixin;

import com.cobblemonwilderness.gen.WildernessDimensionAware;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * CHECKPOINT DELIVERABLE — NOT YET REGISTERED in cobblemon_wilderness.mixins.json.
 *
 * Tags each dimension's {@code ChunkGeneratorStructureState} with whether it is the overworld, at the
 * tail of {@code ChunkMap}'s constructor (the state and level are both set by then). This is the
 * deterministic dimension signal the gen-path relocation mixin needs — the salt hook itself sees only
 * the shared world seed.
 *
 * <p>Do not enable until dev structure-dump verification confirms nether/end placement is unchanged.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    @Final
    private ChunkGeneratorStructureState chunkGeneratorState;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cobblemonwilderness$tagOverworld(CallbackInfo ci) {
        if (this.chunkGeneratorState instanceof WildernessDimensionAware aware) {
            aware.cobblemonwilderness$setOverworld(this.level.dimension() == Level.OVERWORLD);
        }
    }
}
