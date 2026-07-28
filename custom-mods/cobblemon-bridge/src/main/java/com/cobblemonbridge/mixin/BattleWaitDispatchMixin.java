package com.cobblemonbridge.mixin;

import com.cobblemon.mod.common.battles.dispatch.WaitDispatch;
import com.cobblemonbridge.battle.BattleSpeed;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales every fixed battle pause by the server-wide {@link BattleSpeed} multiplier.
 *
 * <p>{@code WaitDispatch} is the single chokepoint for timed waits in a battle — the whole class
 * is {@code readyTime = currentTimeMillis() + seconds*1000} plus a {@code canProceed()} that
 * compares against it. {@code SwitchInstruction}, {@code FaintInstruction},
 * {@code AbilityInstruction}, {@code StartInstruction} and {@code WinInstruction} all pace
 * themselves through it, so scaling the constructor argument scales the entire battle flow.
 *
 * <p>We inject at RETURN and recompute {@code readyTime} rather than modifying the constructor
 * argument: HEAD injection into a constructor is a Mixin corner case, whereas RETURN on a
 * constructor is always legal and {@code readyTime} is the only field on the class. The
 * recomputation repeats Cobblemon's own arithmetic exactly.
 *
 * <p>{@code require = 0} deliberately: this is a cosmetic pacing feature and a Cobblemon update
 * that renames or restructures the class should silently leave battles at stock speed, not
 * crash-loop the server on boot. {@code /battlespeed} reports whether the hook is live.
 */
@Mixin(WaitDispatch.class)
public abstract class BattleWaitDispatchMixin {

    @Shadow(remap = false)
    @Final
    @Mutable
    private long readyTime;

    @Inject(method = "<init>(F)V", at = @At("RETURN"), remap = false, require = 0)
    private void cobblemonbridge$scaleBattleWait(float seconds, CallbackInfo ci) {
        float scaled = BattleSpeed.scale(seconds);
        if (scaled != seconds) {
            this.readyTime = System.currentTimeMillis() + (long) (scaled * 1000.0f);
        }
    }
}
