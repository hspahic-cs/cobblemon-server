package com.cobblemonbridge.eggs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Caps how many Cobreeding eggs can incubate in a player's inventory at once, and stamps each egg
 * with a synced lore line showing its status.
 *
 * <p>Cobreeding's {@code PokemonEgg.inventoryTick} runs the hatch countdown (a per-tick {@code
 * SECOND} counter that rolls a {@code TIMER} decrement) only while the egg sits in a player's main
 * inventory. {@link com.cobblemonbridge.mixin.PokemonEggMixin} consults {@link #incubationRank} at
 * the HEAD of that method: eggs ranked beyond {@link #MAX_INCUBATING} have their tick cancelled, so
 * their countdown is frozen, while the first {@value #MAX_INCUBATING} (in slot order) tick normally.
 * Source is irrelevant: crate/gacha eggs and daycare-bred eggs are all just {@code PokemonEgg}
 * stacks and share the one cap.
 *
 * <p>Eggs are identified by their item's <b>class</b> ({@code getItem().getClass() == PokemonEgg}),
 * not by item instance: Cobreeding registers a separate {@code PokemonEgg} item per Pokémon type
 * ({@code bug_egg}, {@code water_egg}, … plus {@code pokemon_egg}, {@code manaphy_egg}, and shiny
 * variants), so a fire egg and a water egg are different {@code Item} instances of the same class.
 * Comparing by class counts them all as eggs while still needing no compile-time dependency on
 * Cobreeding. Status is shown through the vanilla {@code LORE} data component, which the dedicated
 * server syncs to clients — the bridge is server-only, so a client-side tooltip mixin isn't an option.
 */
public final class EggIncubationLimit {

    private EggIncubationLimit() {}

    /** Most eggs allowed to count down in one inventory at once. */
    public static final int MAX_INCUBATING = 6;

    /** Number of main-inventory slots (hotbar + storage). Eggs only tick here, and {@code slot} from
     *  {@code inventoryTick} is an index into this range. */
    private static final int MAIN_INVENTORY_SIZE = 36;

    /**
     * Rank of the egg at {@code slot} among all egg stacks in {@code player}'s main inventory, ordered
     * by slot index.
     *
     * @return {@code 1..MAX_INCUBATING} if this egg is within the incubating cap, {@code 0} if it is
     *         capped out (should be frozen), or {@code -1} if the slot can't be resolved to this very
     *         stack (e.g. an off-hand tick) — callers treat {@code -1} as "leave Cobreeding alone".
     */
    public static int incubationRank(ServerPlayer player, ItemStack stack, int slot) {
        if (slot < 0 || slot >= MAIN_INVENTORY_SIZE) return -1;
        Inventory inv = player.getInventory();
        // Confirm `slot` indexes the very stack being ticked; if not, this isn't a main-inventory tick
        // we can reason about, so we don't interfere.
        if (inv.getItem(slot) != stack) return -1;

        // Count by PokemonEgg *class*, not item instance: every Cobreeding egg variant
        // (per-type, manaphy, shiny) is a distinct Item but the same class, and they all share one cap.
        Class<?> eggClass = stack.getItem().getClass();
        int eggsBefore = 0;
        for (int i = 0; i < slot; i++) {
            if (inv.getItem(i).getItem().getClass() == eggClass) eggsBefore++;
        }
        int rank = eggsBefore + 1;
        return rank <= MAX_INCUBATING ? rank : 0;
    }

    /**
     * Set the egg's status lore to match {@code rank} ({@code >0} = incubating slot N, {@code 0} = not
     * incubating). Idempotent: only writes when the lore would actually change, so it doesn't churn
     * the stack every tick.
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

    /** Hidden custom-data key toggled each tick on a capped egg (see {@link #pinFrozenTimer}). */
    private static final String RESYNC_KEY = "cobblemonbridge_egg_resync";

    /**
     * Force this egg's inventory slot to re-broadcast to the client this tick by toggling a hidden
     * custom-data byte. Needed because the hatch timer is rendered client-side from Cobreeding's
     * {@code TIMER} component: when we freeze a capped egg (cancel its server tick), the server stack
     * stops changing, so the vanilla container sync never corrects the client — which keeps running
     * Cobreeding's {@code inventoryTick} and decrements its own local copy, showing a timer that
     * appears to count down. Changing a component every tick makes {@code broadcastChanges} resend the
     * slot, overwriting the client's drift with the frozen server value. Call once per tick on capped
     * eggs (after {@link #applyStatusLore}, before cancelling the tick).
     */
    public static void pinFrozenTimer(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putByte(RESYNC_KEY, (byte) (tag.getByte(RESYNC_KEY) == 0 ? 1 : 0));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Drop the resync flag once an egg is incubating again — from then on Cobreeding's own per-tick
     * {@code SECOND} updates keep the slot in sync. No-op when the flag is absent, so it never churns
     * the stack for eggs that were never capped.
     */
    public static void clearFrozenTimerPin(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.copyTag().contains(RESYNC_KEY)) return;
        CompoundTag tag = cd.copyTag();
        tag.remove(RESYNC_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
