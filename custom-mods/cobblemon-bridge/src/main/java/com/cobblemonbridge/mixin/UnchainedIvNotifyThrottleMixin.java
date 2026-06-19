package com.cobblemonbridge.mixin;

import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemonbridge.streaks.IvNotifyThrottle;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Throttles Cobblemon Unchained's per-catch IV streak notifications down to one message per five
 * boosted catches (per player + species). See {@link IvNotifyThrottle} for the why.
 *
 * <p>Unchained's {@code AbstractBoosterRunner.notifyPlayer()} runs once per boost application and
 * sends {@code unchained.notification.<key>.<source>} via {@code player.sendSystemMessage(...)}.
 * We inject at HEAD and cancel the call for 4 of every 5 IV notifications; the IV boost itself is
 * applied earlier in {@code runThrough()} and is never touched, so rewards are unchanged. Shiny and
 * hidden-ability notifications fall through untouched because we only act when the booster key
 * starts with {@code "iv"}.
 *
 * <p>{@code remap = false} because Unchained is a NeoForge mod (Mojmap at runtime, its own method
 * names). Everything is read reflectively against {@code this} so the bridge needs no compile-time
 * dependency on Unchained; any signature mismatch on a future Unchained version is swallowed and the
 * original message is allowed through unchanged (fail-open — we never silence shiny/HA on error).
 */
@Mixin(targets = "us.timinc.mc.cobblemon.unchained.booster.AbstractBoosterRunner", remap = false)
public class UnchainedIvNotifyThrottleMixin {

    @Inject(method = "notifyPlayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void cobblemonbridge$throttleIvNotify(CallbackInfo ci) {
        try {
            Object cfg = getClass().getMethod("getConfig").invoke(this);
            String key = String.valueOf(cfg.getClass().getMethod("getKey").invoke(cfg));
            // Booster keys are "iv.spawn", "shiny.fish", "hidden.egg", … — only throttle IV.
            if (!key.startsWith("iv")) return;

            ServerPlayer player = (ServerPlayer) getClass().getMethod("getPlayer").invoke(this);
            Species species = (Species) getClass().getMethod("getSpecies").invoke(this);
            if (player == null || species == null) return;

            if (!IvNotifyThrottle.shouldShow(player.getUUID(), species.getResourceIdentifier().toString())) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Fail open: leave Unchained's message untouched on any reflection mismatch.
        }
    }
}
