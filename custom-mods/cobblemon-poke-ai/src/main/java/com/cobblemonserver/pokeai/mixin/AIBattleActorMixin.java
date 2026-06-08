package com.cobblemonserver.pokeai.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemonserver.pokeai.ai.AsyncChoiceDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes PokeEngineAI move selection non-blocking.
 *
 * Cobblemon calls {@link AIBattleActor#onChoiceRequested()} on the server thread.
 * For our bridge-backed AI that means a ~1-2s HTTP+MCTS call freezes the whole
 * server every AI turn. We hand the choice to {@link AsyncChoiceDispatcher},
 * which computes it off-thread and submits it back on the server thread; the
 * battle waits for the response just like it waits for a human player. If the
 * dispatcher takes over we cancel the vanilla synchronous path.
 *
 * Non-PokeEngineAI actors (and any case the dispatcher declines) fall through to
 * the original behaviour unchanged.
 */
@Mixin(AIBattleActor.class)
public abstract class AIBattleActorMixin {
    @Inject(method = "onChoiceRequested", at = @At("HEAD"), cancellable = true)
    private void pokeai$asyncChoice(CallbackInfo ci) {
        if (AsyncChoiceDispatcher.INSTANCE.tryDispatch(this)) {
            ci.cancel();
        }
    }
}
