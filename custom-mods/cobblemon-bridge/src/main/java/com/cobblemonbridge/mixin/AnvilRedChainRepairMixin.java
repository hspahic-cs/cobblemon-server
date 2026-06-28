package com.cobblemonbridge.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the Legendary Monuments Red Chain a one-time-use item by killing its anvil repair loop.
 *
 * <p>Stock LM behaviour: a {@code red_chain} (durability 100, stacks to 1) used at the Spear Pillar
 * turns into a broken {@code fragmented_red_chain}. The mod's own {@code AnvilScreenHandlerMixin}
 * then lets {@code fragmented_red_chain + origin_ingot} be combined in an anvil back into a fresh
 * {@code red_chain} — so one chain can summon Dialga/Palkia at every Spear Pillar forever. The
 * anvil is the <em>only</em> path back to a working chain ({@code /repair} and grindstone can only
 * zero durability on the held item, neither converts a fragmented chain into a red chain).
 *
 * <p>We inject at the <em>tail</em> of {@link AnvilMenu#createResult()} — after both vanilla and
 * LM's mixin have written the output slot — and blank the result whenever either input slot holds a
 * {@code red_chain} or {@code fragmented_red_chain}. Running last means we don't have to win a
 * mixin-priority fight with LM; whatever it produced is simply discarded before the player can take
 * it. Net effect: a used chain stays fragmented permanently, and players must obtain/craft a new
 * one (lake-trio items, or the Ultra crate) for each summon.
 *
 * <p>Matched by registry id (not a compile dependency on LM) and {@code require = 0} so this
 * compiles and loads fine if LM is ever removed or renamed; the body is wrapped to fail open.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilRedChainRepairMixin {

    @Shadow @Final protected Container inputSlots;
    @Shadow @Final protected ResultContainer resultSlots;

    @Inject(method = "createResult", at = @At("RETURN"), require = 0)
    private void cobblemonbridge$blockRedChainRepair(CallbackInfo ci) {
        try {
            if (cobblemonbridge$isRedChainItem(inputSlots.getItem(0))
                || cobblemonbridge$isRedChainItem(inputSlots.getItem(1))) {
                resultSlots.setItem(0, ItemStack.EMPTY);
            }
        } catch (Throwable ignored) {
            // Fail open: never let a repair-block bug break the anvil for normal items.
        }
    }

    private static boolean cobblemonbridge$isRedChainItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null
            && id.getNamespace().equals("legendarymonuments")
            && (id.getPath().equals("red_chain") || id.getPath().equals("fragmented_red_chain"));
    }
}
