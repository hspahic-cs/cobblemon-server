package com.cobblemonserver.pokeai.mixin;

import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemonserver.pokeai.ai.PokeEngineAI;
import com.gitlab.srcmc.rctapi.api.battle.BattleFormatProvider;
import com.gitlab.srcmc.rctapi.api.battle.BattleManager;
import com.gitlab.srcmc.rctapi.api.battle.BattleRules;
import com.gitlab.srcmc.rctapi.api.trainer.Trainer;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import java.util.List;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies a per-gym player level cap for {@code pe} (PokeEngineAI) trainer battles.
 *
 * <p>RCT only clones — and therefore safely re-levels — the player team when the
 * battle format's {@code adjustLevel} is &gt; 0 at build time. We can't flip that
 * after the build without mutating the player's real Pokemon (BattlePokemon's
 * {@code effectedPokemon} aliases {@code originalPokemon} unless cloned). So we
 * hook RCT's own build path:
 *
 * <ul>
 *   <li>At {@code startBattle} we scan both sides for a {@link PokeEngineAI} NPC
 *       with {@code levelCap > 0} and stash it (thread-local; battle starts run
 *       on the server thread, and {@code startBattle} runs both {@code
 *       toBattleSide} calls synchronously before returning).</li>
 *   <li>We redirect {@code toBattleSide}'s read of {@link BattleFormat#getAdjustLevel()}
 *       to that cap, so RCT clones + clamps the player team natively via its own
 *       {@code initParty} logic.</li>
 * </ul>
 *
 * The gym JSONs already set {@code adjustPlayerLevels: true} /
 * {@code adjustNPCLevels: false}, so only the player side is re-leveled; gym mons
 * are L50 already (clamping them to the same value is a no-op).
 */
@Mixin(BattleManager.class)
public class BattleManagerMixin {
    private static final ThreadLocal<Integer> POKEAI_LEVEL_CAP = new ThreadLocal<>();

    private static final String START_BATTLE =
        "startBattle(Ljava/util/List;Ljava/util/List;"
            + "Lcom/gitlab/srcmc/rctapi/api/battle/BattleFormatProvider;"
            + "Lcom/gitlab/srcmc/rctapi/api/battle/BattleRules;)Ljava/util/UUID;";

    @Inject(method = START_BATTLE, at = @At("HEAD"))
    private static void pokeai$captureLevelCap(
            List<Trainer> side1,
            List<Trainer> side2,
            BattleFormatProvider format,
            BattleRules rules,
            CallbackInfoReturnable<UUID> cir) {
        Integer cap = null;
        for (List<Trainer> side : List.of(side1, side2)) {
            for (Trainer t : side) {
                if (t instanceof TrainerNPC npc
                        && npc.getBattleAI() instanceof PokeEngineAI ai
                        && ai.getLevelCap() > 0) {
                    cap = ai.getLevelCap();
                    break;
                }
            }
            if (cap != null) {
                break;
            }
        }
        if (cap != null) {
            POKEAI_LEVEL_CAP.set(cap);
        } else {
            POKEAI_LEVEL_CAP.remove();
        }
    }

    @Inject(method = START_BATTLE, at = @At("RETURN"))
    private static void pokeai$clearLevelCap(
            List<Trainer> side1,
            List<Trainer> side2,
            BattleFormatProvider format,
            BattleRules rules,
            CallbackInfoReturnable<UUID> cir) {
        POKEAI_LEVEL_CAP.remove();
    }

    @Redirect(
        method = "toBattleSide(Ljava/util/List;"
            + "Lcom/gitlab/srcmc/rctapi/api/battle/BattleFormatProvider;"
            + "Lcom/gitlab/srcmc/rctapi/api/battle/BattleRules;)"
            + "Lcom/cobblemon/mod/common/battles/BattleSide;",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/battles/BattleFormat;getAdjustLevel()I"))
    private static int pokeai$overrideAdjustLevel(BattleFormat instance) {
        Integer cap = POKEAI_LEVEL_CAP.get();
        return cap != null ? cap : instance.getAdjustLevel();
    }
}
