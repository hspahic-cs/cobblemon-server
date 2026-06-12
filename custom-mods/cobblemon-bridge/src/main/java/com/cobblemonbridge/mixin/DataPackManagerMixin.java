package com.cobblemonbridge.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.command.ReloadCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadCommand.class)
public class DataPackManagerMixin {
    @Inject(method = "method_41218", at = @At("RETURN"))
    private static void onReload(ResourceManager resourceManager, ResourceType resourceType, CompletableFuture future, Executor executor, Executor executor2, CallbackInfoReturnable<CompletableFuture> cir) {
        future.thenRun(() -> {
            PokemonSpecies.getByName().values().forEach(species -> species.reloadAssets());
        });
    }
}