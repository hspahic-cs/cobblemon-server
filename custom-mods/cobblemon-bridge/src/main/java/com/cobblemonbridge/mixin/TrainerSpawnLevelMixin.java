package com.cobblemonbridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.lang.reflect.Method;

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
 *
 * <p>A second injector ({@link #cobblemonbridge$skewBelowTeamLevel}) biases the (otherwise
 * symmetric) spawn weighting downward, so players mostly meet trainers at or just below their team
 * level with only a thin tail of tougher ones.
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

    // --- Downward level skew (favor trainers at/below the player's team level) ---

    /** Spawn weight is multiplied by this per level a trainer sits ABOVE the team level. */
    private static final double ABOVE_LEVEL_DECAY = 0.8;

    private static boolean cobblemonbridge$reflectInit;
    private static boolean cobblemonbridge$reflectOk;
    private static Method cobblemonbridge$mGetInstance;
    private static Method cobblemonbridge$mGetTrainerManager;
    private static Method cobblemonbridge$mGetPlayerLevel;
    private static Method cobblemonbridge$mTrainerLevel;

    /**
     * RCT's {@code computeWeight} weights candidates symmetrically on {@code |trainerLevel -
     * teamLevel|}. We reshape that downward: trainers at/below the team level keep full weight;
     * trainers above decay by {@link #ABOVE_LEVEL_DECAY} per level above (e.g. +5 ≈ 33%, +10 ≈ 11%).
     * RCT's own {@code maxLevelDiff} band still bounds eligibility, so this only redistributes within
     * it. Resolved by reflection (this bridge keeps no compile dep on RCT); fails open to the
     * unmodified weight if anything is missing.
     */
    @ModifyReturnValue(method = "computeWeight", at = @At("RETURN"), require = 0)
    private double cobblemonbridge$skewBelowTeamLevel(double weight, Player player, String trainerId, @Coerce Object data) {
        if (weight <= 0.0) {
            return weight;
        }
        try {
            cobblemonbridge$initSkewReflection();
            if (!cobblemonbridge$reflectOk) {
                return weight;
            }
            Object mgr = cobblemonbridge$mGetTrainerManager.invoke(cobblemonbridge$mGetInstance.invoke(null));
            int teamLevel = ((Integer) cobblemonbridge$mGetPlayerLevel.invoke(mgr, player)).intValue();
            int trainerLevel = ((Integer) cobblemonbridge$mTrainerLevel.invoke(null, data)).intValue();
            if (teamLevel > 0 && trainerLevel > teamLevel) {
                weight *= Math.pow(ABOVE_LEVEL_DECAY, trainerLevel - teamLevel);
            }
        } catch (Throwable ignored) {
            // Fail open: a weighting tweak must never break trainer spawning.
        }
        return weight;
    }

    private static void cobblemonbridge$initSkewReflection() {
        if (cobblemonbridge$reflectInit) {
            return;
        }
        cobblemonbridge$reflectInit = true;
        try {
            Class<?> rctMod = Class.forName("com.gitlab.srcmc.rctmod.api.RCTMod");
            cobblemonbridge$mGetInstance = rctMod.getDeclaredMethod("getInstance");
            cobblemonbridge$mGetTrainerManager = rctMod.getDeclaredMethod("getTrainerManager");
            Class<?> trainerManager = Class.forName("com.gitlab.srcmc.rctmod.api.service.TrainerManager");
            cobblemonbridge$mGetPlayerLevel = trainerManager.getDeclaredMethod("getPlayerLevel", Player.class);
            Class<?> levelUtils = Class.forName("com.gitlab.srcmc.rctmod.api.utils.LevelUtils");
            Class<?> trainerMobData = Class.forName("com.gitlab.srcmc.rctmod.api.data.pack.TrainerMobData");
            cobblemonbridge$mTrainerLevel = levelUtils.getDeclaredMethod("trainerLevel", trainerMobData);
            cobblemonbridge$mGetInstance.setAccessible(true);
            cobblemonbridge$mGetTrainerManager.setAccessible(true);
            cobblemonbridge$mGetPlayerLevel.setAccessible(true);
            cobblemonbridge$mTrainerLevel.setAccessible(true);
            cobblemonbridge$reflectOk = true;
        } catch (Throwable t) {
            cobblemonbridge$reflectOk = false;
        }
    }
}
