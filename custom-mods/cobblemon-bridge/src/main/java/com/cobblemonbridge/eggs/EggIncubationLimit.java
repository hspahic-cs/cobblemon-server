package com.cobblemonbridge.eggs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Cap + status-lore helpers for Cobreeding eggs. The cap itself is enforced in
 * {@link EggDefeatHook} — the bridge's own per-second hatch driver — which simply doesn't decrement
 * an egg's timer once it ranks beyond {@link #MAX_INCUBATING} (the same mechanism that pauses eggs
 * while a player is AFK). This class only holds the shared constant and the synced lore line.
 */
public final class EggIncubationLimit {

    private EggIncubationLimit() {}

    /** Most eggs allowed to count down in one inventory at once. */
    public static final int MAX_INCUBATING = 6;

    /**
     * Set the egg's status lore to match {@code rank} ({@code >0} = incubating slot N, {@code 0} = not
     * incubating). Idempotent: only writes when the lore would actually change, so it doesn't churn
     * the stack. Shown through the vanilla {@code LORE} component (synced by {@link EggDefeatHook}'s
     * slot broadcast), since the bridge is server-only and can't add a client tooltip mixin.
     */
    public static void applyStatusLore(ItemStack stack, int rank) {
        Component line = rank > 0
            ? Component.literal("Incubating (" + rank + "/" + MAX_INCUBATING + ")")
                .withStyle(s -> s.withColor(ChatFormatting.GREEN).withItalic(false))
            : Component.literal("Not incubating")
                .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false));
        ItemLore desired = new ItemLore(List.of(line));
        if (!desired.equals(stack.get(DataComponents.LORE))) {
            stack.set(DataComponents.LORE, desired);
        }
    }
}
