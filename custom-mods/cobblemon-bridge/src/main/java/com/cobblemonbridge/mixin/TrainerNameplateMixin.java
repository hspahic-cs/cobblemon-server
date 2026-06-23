package com.cobblemonbridge.mixin;

import com.cobblemonbridge.trainer.TrainerLevelBridge;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Appends the trainer's strongest-Pokemon level to its nameplate, e.g. {@code Ace Trainer Lv.45}.
 *
 * <p>Lets players read a trainer's level off the nameplate (which Minecraft renders from
 * {@code getDisplayName()} when you look at the entity) instead of right-clicking every team to
 * find a fightable one. Especially useful when a low-level and high-level player share an area:
 * RCT already spawns level-appropriate trainers per player, but without the level on the plate
 * you can't tell which nearby trainers are meant for you.
 *
 * <p>We use MixinExtras' {@code @ModifyReturnValue} on {@code getDisplayName()} so we layer on top
 * of RCT's own name styling (type colors/symbols) rather than fighting it. The level is computed
 * via reflection in {@link TrainerLevelBridge} because RCTmod isn't on our compile classpath; if
 * the level can't be resolved we return the name untouched.
 *
 * <p>The suffix is dim gray so it reads as metadata next to the colored trainer name. RCT's color
 * override applies {@code setStyle} to the name root only, so our explicitly-gray sibling keeps
 * its color.
 *
 * <p>{@code remap = false} because RCTmod is a NeoForge mod (Mojmap names at runtime).
 */
@Mixin(targets = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob", remap = false)
public class TrainerNameplateMixin {

    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
    private Component cobblemonbridge$appendTeamLevel(Component original) {
        if (original == null) return original;
        Entity self = (Entity)(Object)this;
        int level = TrainerLevelBridge.INSTANCE.maxTeamLevel(self);
        if (level == TrainerLevelBridge.UNAVAILABLE) return original;
        return original.copy()
            .append(Component.literal(" Lv." + level).withStyle(ChatFormatting.GRAY));
    }
}
