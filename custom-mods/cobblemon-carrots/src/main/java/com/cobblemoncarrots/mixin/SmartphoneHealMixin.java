package com.cobblemoncarrots.mixin;

import com.cobblemoncarrots.healer.HealerHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
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
 * descriptor only. The packet param is {@code @Coerce Object} because {@code HealPokemonPacket}
 * isn't referenceable at compile time and Mixin requires the exact target descriptor otherwise.
 *
 * <p>The mixin config is fail-open (non-required, {@code defaultRequire: 0}): if a future Smartphone
 * version renames/moves this handler the injection silently no-ops (heal reverts to free) instead of
 * crashing the whole server on boot — a balance bug is recoverable, a crash-loop is not. Verify the
 * phone heal still prompts (the in-game test) whenever the Smartphone mod version changes.
 */
@Mixin(targets = "com.nbp.cobblemon_smartphone.network.handler.HealPokemonHandler", remap = false)
public class SmartphoneHealMixin {

    @Inject(
        method = "handle(Lcom/nbp/cobblemon_smartphone/network/packet/HealPokemonPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void cobblemoncarrots$gatePhoneHeal(@Coerce Object packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        server.execute(() -> HealerHandler.INSTANCE.promptCost(player));
        ci.cancel();
    }
}
