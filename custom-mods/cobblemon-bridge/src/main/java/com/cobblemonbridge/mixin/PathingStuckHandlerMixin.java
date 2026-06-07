package com.cobblemonbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Crash guard for MineColonies' stuck-citizen handler.
 *
 * <p>Prod incident 2026-06-07 (crash reports 01:54 + 02:13): a stuck citizen's
 * {@code PathingStuckHandler.tryUnstuck} read {@code getNodePos(getNextNodeIndex() - 1)} with
 * {@code nextNodeIndex == 2} on a <em>1-node</em> path → {@code ArrayIndexOutOfBoundsException}
 * ("Index 1 out of bounds for length 1") on the server thread → "Ticking entity" crash, hung
 * save, watchdog kill, systemd restart — a crash loop while the citizen stayed stuck.
 *
 * <p>Upstream's fix (ldtteam/minecolonies@fe40f38, in 1.1.1310+) only guards
 * {@code getNextNodeIndex() > 0} — the <em>negative</em> overflow. Our variant is the index
 * running past the path <em>end</em>, which still passes that guard. Until upstream bounds-checks
 * both sides, we wrap the whole stuck check: an out-of-bounds path read logs a warning and skips
 * this tick's unstuck attempt instead of killing the server. The citizen simply stays stuck one
 * more tick — the handler re-runs next tick with fresh path state.
 *
 * <p>String target + {@code require = 0} (same pattern as {@link TrainerMobMixin}): compiles
 * without MineColonies on the classpath and degrades to a no-op if the mod is removed from the
 * pack or the class is renamed. {@code checkStuck(NAV)}'s generic erases to
 * {@code (PathNavigation)V} — NAV's first bound — which is what the wrapper must match.
 */
@Mixin(targets = "com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler", remap = false)
public class PathingStuckHandlerMixin {

    private static final Logger cobblemonbridge$LOG =
        LoggerFactory.getLogger("cobblemon_bridge/mixin/stuck_handler");

    @WrapMethod(method = "checkStuck", require = 0)
    private void cobblemonbridge$guardCheckStuck(PathNavigation navigator, Operation<Void> original) {
        try {
            original.call(navigator);
        } catch (IndexOutOfBoundsException e) {
            // Known MineColonies pathing race (see class javadoc). Skip this tick's unstuck
            // attempt. (PathNavigation has no public mob accessor in mojmaps, so the target
            // node position is the best locator we can cheaply log.)
            final var path = navigator.getPath();
            cobblemonbridge$LOG.warn(
                "Suppressed MineColonies stuck-handler crash ({}) near {} — citizen stays stuck this tick",
                e.getMessage(), path != null ? path.getTarget().toShortString() : "<no path>"
            );
        }
    }
}
