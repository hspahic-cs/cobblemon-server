package com.cobblemonbridge.mixin;

import com.cobblemonbridge.trainer.TrainerLevelBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends the trainer's strongest-Pokemon level to its nameplate, e.g. {@code Ace Trainer Lv.45},
 * so players can read a trainer's level on hover instead of right-clicking every team.
 *
 * <p><b>Why custom name and not {@code getDisplayName()}:</b> cobblemon-bridge is a
 * <i>server-only</i> mod (see the {@code SERVER_ONLY} list in the build + neoforge.mods.toml).
 * Nameplates are drawn <i>client-side</i> from the client's own {@code getDisplayName()}, which
 * RCT builds from the entity's <i>synced custom name</i>. Since the bridge isn't on the client, a
 * mixin on {@code getDisplayName()} never runs where the plate is rendered. So we instead bake the
 * level into the synced custom name server-side: RCT sets it in {@code udpateCustomName()} (a
 * server-only path), and the change rides the normal entity-data sync to every client — vanilla or
 * modded — with no client mod required.
 *
 * <p>RCT's client {@code getDisplayName()} applies the type color to the name root only, so our
 * explicitly-gray {@code " Lv.N"} sibling stays gray next to the colored name.
 *
 * <p>{@code require = 0} on both handlers: RCT's method is spelled {@code udpateCustomName} (sic) in
 * the version we target, but we also hedge the corrected spelling. Whichever exists gets the
 * injection; the other no-ops instead of failing mod load. The level itself comes from
 * {@link TrainerLevelBridge} by reflection (RCTmod isn't a compile dep), degrading silently.
 *
 * <p>{@code remap = false} because RCTmod is a NeoForge mod (Mojmap names at runtime).
 */
@Mixin(targets = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob", remap = false)
public class TrainerNameplateMixin {

    @Inject(method = "udpateCustomName", at = @At("TAIL"), require = 0)
    private void cobblemonbridge$appendTeamLevelTypo(CallbackInfo ci) {
        cobblemonbridge$appendTeamLevel();
    }

    @Inject(method = "updateCustomName", at = @At("TAIL"), require = 0)
    private void cobblemonbridge$appendTeamLevelFixed(CallbackInfo ci) {
        cobblemonbridge$appendTeamLevel();
    }

    private void cobblemonbridge$appendTeamLevel() {
        Entity self = (Entity)(Object)this;
        Component current = self.getCustomName();
        if (current == null) return;
        // udpateCustomName resets the name to the bare team name before this TAIL runs, so we
        // append exactly once. Guard anyway in case a future RCT calls it without resetting first.
        if (current.getString().contains(" Lv.")) return;
        int level = TrainerLevelBridge.INSTANCE.maxTeamLevel(self);
        if (level == TrainerLevelBridge.UNAVAILABLE) return;
        self.setCustomName(current.copy()
            .append(Component.literal(" Lv." + level).withStyle(ChatFormatting.GRAY)));
    }
}
