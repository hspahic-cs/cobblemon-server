package com.cobblemonbridge.mixin;

import com.cobblemon.mod.common.api.moves.animations.keyframes.AnimationActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityMoLangActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityParticlesActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntitySoundActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.MoLangActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.PauseActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.RemoveHoldsActionEffectKeyframe;
import com.cobblemon.mod.common.api.scheduling.SchedulingFunctionsKt;
import com.cobblemonbridge.battle.BattleSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

/**
 * Scales move-animation pacing by the same {@link BattleSpeed} multiplier the battle flow uses.
 *
 * <p>A move plays an {@code ActionEffectTimeline} of keyframes while the interpreter parks on an
 * {@code UntilDispatch} waiting for the timeline to release its holds. Every keyframe that waits
 * does so through {@code SchedulingFunctionsKt.delayedFuture(seconds)} — the pause keyframe
 * itself plus the {@code delay} on the animation, particle, sound and MoLang keyframes, and the
 * delayed hold release. These seven classes are every caller inside the keyframes package.
 *
 * <p>Scaling these alongside {@code WaitDispatch} matters: if only the flow were sped up, the
 * interpreter would arrive at each animation sooner and then wait out an unshortened timeline,
 * so battles would barely speed up while looking choppier.
 *
 * <p>The redirect is scoped to these classes, so the other {@code delayedFuture} callers
 * (PokemonEntity, EmptyPokeBallEntity, NPCEntity, GenericBedrockEntity) keep stock timing —
 * a Poké Ball throw outside battle is not battle pacing.
 *
 * <p>{@code require = 0} for the same reason as {@link BattleWaitDispatchMixin}: degrade to
 * stock speed on a Cobblemon refactor rather than failing the mixin apply and crash-looping.
 */
@Mixin({
    PauseActionEffectKeyframe.class,
    AnimationActionEffectKeyframe.class,
    EntityMoLangActionEffectKeyframe.class,
    EntityParticlesActionEffectKeyframe.class,
    EntitySoundActionEffectKeyframe.class,
    MoLangActionEffectKeyframe.class,
    RemoveHoldsActionEffectKeyframe.class,
})
public abstract class BattleAnimationDelayMixin {

    @Redirect(
        method = {"playWhenTrue", "play"},
        at = @At(
            value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/scheduling/SchedulingFunctionsKt;"
                + "delayedFuture(F)Ljava/util/concurrent/CompletableFuture;"
        ),
        remap = false,
        require = 0
    )
    private CompletableFuture<?> cobblemonbridge$scaleAnimationDelay(float seconds) {
        return SchedulingFunctionsKt.delayedFuture(BattleSpeed.scale(seconds));
    }
}
