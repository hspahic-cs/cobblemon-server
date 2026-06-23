package com.cobblemonbridge.mixin;

import com.cobblemonbridge.trainer.TrainerLevelBridge;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the {@code server_bosses} gym/E4 trainers fightable from <em>any</em> series.
 *
 * <p>We give the bosses their own card by putting them in {@code series: ["server_bosses"]} only.
 * But RCT couples a trainer's card with battle eligibility: {@code canBattleAgainst} ends with
 * {@code tmd.isOfSeries(currentSeries) || alreadyDefeated}, so a player in a pack series (bdsp/
 * radicalred/unbound) would be refused when they walk up to a gym. This rewrites just that
 * {@code isOfSeries} return to {@code true} when the trainer is a {@code server_bosses} member, so
 * the bosses can be battled regardless of the player's current series. The bridge's own
 * {@code GymPrereqHook} / {@code GymBattleGate} still enforce the actual gym prerequisites.
 *
 * <p>Scope: the {@code isOfSeries} call inside the lambda on the line above (the requiredDefeats
 * filter) compiles to a separate synthetic method, so this {@code @ModifyExpressionValue} on
 * {@code canBattleAgainst} only touches the final series gate. We OR in the original value, so
 * non-boss trainers keep their normal series gating untouched.
 *
 * <p>{@code remap = false} — RCTmod is a NeoForge mod (Mojmap at runtime).
 */
@Mixin(targets = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob", remap = false)
public class TrainerSeriesGateMixin {

    private static final String BOSS_SERIES = "server_bosses";

    @ModifyExpressionValue(
        method = "canBattleAgainst",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gitlab/srcmc/rctmod/api/data/pack/TrainerMobData;isOfSeries(Ljava/lang/String;)Z"
        ),
        require = 0
    )
    private boolean cobblemonbridge$allowBossesFromAnySeries(boolean isOfCurrentSeries) {
        if (isOfCurrentSeries) return true;
        Entity self = (Entity)(Object)this;
        return TrainerLevelBridge.INSTANCE.isOfSeries(self, BOSS_SERIES);
    }
}
