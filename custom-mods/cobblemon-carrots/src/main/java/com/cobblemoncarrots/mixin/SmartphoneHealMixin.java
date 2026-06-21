package com.cobblemoncarrots.mixin;

import com.cobblemoncarrots.healer.HealerHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes the Cobblemon Smartphone "Heal" app through the same carrot+money quote flow as the
 * Poké Healer block, instead of healing the party for free.
 *
 * <p>Without this, the phone's heal button ({@code cobblemon_smartphone:heal_pokemon} packet →
 * {@code HealPokemonHandler.handle}) calls Cobblemon's party heal directly and never touches
 * cobblemon-carrots' {@link HealerHandler}, giving free, unlimited, anywhere healing — which
 * completely undercuts the carrot/money sink the healer block exists to be.
 *
 * <p>We inject at HEAD of the handler, cancel the free heal, and instead schedule
 * {@link HealerHandler#promptCost(ServerPlayer)} on the server thread (the handler runs off-thread
 * and defers its own work via {@code server.execute}, so we mirror that). The player then gets the
 * usual quote with clickable [CONFIRM]/[CANCEL]; confirming consumes carrots + charges money for
 * the shortfall exactly as the block does. Net effect: the phone becomes paid mobile healing.
 *
 * <p>{@code remap = false} because Smartphone is a NeoForge mod (Mojmap names at runtime) and its
 * classes aren't on our compile classpath — the target and packet type are referenced by string
 * descriptor only. {@code required = true}: Smartphone is a fixed part of the pack, so a target
 * that ever stops resolving should surface loudly on boot rather than silently re-opening the
 * free-heal hole. Bump/verify this mixin whenever the Smartphone mod version changes.
 */
@Mixin(targets = "com.nbp.cobblemon_smartphone.network.handler.HealPokemonHandler", remap = false)
public class SmartphoneHealMixin {

    @Inject(
        method = "handle(Lcom/nbp/cobblemon_smartphone/network/packet/HealPokemonPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void cobblemoncarrots$gatePhoneHeal(Object packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        server.execute(() -> HealerHandler.INSTANCE.promptCost(player));
        ci.cancel();
    }
}
