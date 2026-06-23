package com.cobblemonbridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes RCT trainer spawn-weighting key off the player's actual team level instead of clamping it
 * to their (now low) RCT level cap.
 *
 * <p>{@code TrainerSpawner.computeWeight} / {@code computeChance} center spawn-eligible trainers on
 * {@code Math.min(PlayerState.getLevelCap(), TrainerManager.getPlayerLevel(player))}. We keep the
 * server's global {@code initialLevelCap} low (15) so the progress-graph nodes render real trainer
 * levels — but that same value also dragged this spawn target down to the cap, so trainers spawned
 * scaled to quest progress rather than the player's actual party.
 *
 * <p>We rewrite only the {@code getLevelCap()} return inside those two methods to
 * {@code Integer.MAX_VALUE}, so the {@code min(...)} collapses to the real team level and spawns
 * track the party again — without affecting the graph display, which reads {@code initialLevelCap}
 * on its own. Same {@code @ModifyExpressionValue} approach as {@code TrainerMobMixin}'s level-cap
 * gate bypass.
 *
 * <p>{@code require = 0}: if the target ever moves, this no-ops (spawns fall back to cap-based)
 * rather than failing the whole server-only bridge at load. {@code remap = false} — RCTmod is a
 * NeoForge mod (Mojmap at runtime).
 */
@Mixin(targets = "com.gitlab.srcmc.rctmod.api.service.TrainerSpawner", remap = false)
public class TrainerSpawnLevelMixin {

    @ModifyExpressionValue(
        method = {"computeWeight", "computeChance"},
        at = @At(
            value = "INVOKE",
            target = "Lcom/gitlab/srcmc/rctmod/api/data/sync/PlayerState;getLevelCap()I"
        ),
        require = 0
    )
    private int cobblemonbridge$spawnByActualTeamLevel(int originalLevelCap) {
        return Integer.MAX_VALUE;
    }
}
