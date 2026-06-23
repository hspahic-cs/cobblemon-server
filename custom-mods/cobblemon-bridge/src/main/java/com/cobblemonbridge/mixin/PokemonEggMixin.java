package com.cobblemonbridge.mixin;

import com.cobblemonbridge.eggs.BredTagHook;
import com.cobblemonbridge.eggs.EggIncubationLimit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the source egg's gacha-tier tag at the exact moment Cobreeding's {@code
 * PokemonEgg.inventoryTick} calls {@code hatchEgg(player, props)}. Records the player UUID +
 * server tick in {@link BredTagHook#markGachaHatch} so the synchronously-fired
 * {@code HATCH_EGG_POST} subscriber can skip the bred tag for crate/gacha-sourced eggs.
 *
 * <p>{@code remap = false} because Cobreeding is a NeoForge mod (Mojmap names at runtime), and
 * because the inject target uses Cobreeding's own descriptor.
 *
 * <p>The window from mark→consume is a single synchronous call chain on the server tick thread
 * ({@code inventoryTick → hatchEgg → HATCH_EGG_POST.emit}), so cross-egg leakage isn't possible
 * under normal flow. If a subscriber cancels {@code HATCH_EGG_PRE} mid-hatch the marker would
 * leak — {@link BredTagHook} guards against that by ignoring markers older than 1 tick.
 */
@Mixin(targets = "ludichat.cobbreeding.PokemonEgg", remap = false)
public class PokemonEggMixin {

    /**
     * Caps the number of simultaneously-incubating eggs. At the HEAD of {@code inventoryTick} we rank
     * this egg among the egg stacks in the player's inventory: the first
     * {@link EggIncubationLimit#MAX_INCUBATING} (by slot) tick normally, the rest have the tick
     * cancelled so their hatch countdown freezes. Either way we stamp a synced status lore line on the
     * stack. Server-side only; clients receive the frozen TIMER + lore through normal item sync.
     */
    @Inject(
        method = "inventoryTick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void cobblemonbridge$capIncubation(
        ItemStack stack,
        Level level,
        Entity entity,
        int slot,
        boolean selected,
        CallbackInfo ci
    ) {
        if (level.isClientSide() || level.getServer() == null) return;
        if (!(entity instanceof ServerPlayer player)) return;

        int rank = EggIncubationLimit.incubationRank(player, stack, slot);
        if (rank < 0) return; // couldn't resolve the slot — let Cobreeding tick it normally
        EggIncubationLimit.applyStatusLore(stack, rank);
        if (rank == 0) {
            ci.cancel(); // over the cap: freeze the hatch countdown for this egg
        }
    }

    @Inject(
        method = "inventoryTick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lludichat/cobbreeding/PokemonEgg;hatchEgg(Lnet/minecraft/world/entity/player/Player;Lcom/cobblemon/mod/common/api/pokemon/PokemonProperties;)V",
            remap = false
        )
    )
    private void cobblemonbridge$captureGachaSource(
        ItemStack stack,
        Level level,
        Entity entity,
        int slot,
        boolean selected,
        CallbackInfo ci
    ) {
        if (level.isClientSide() || level.getServer() == null) return;
        if (!(entity instanceof ServerPlayer player)) return;
        int tick = level.getServer().getTickCount();

        // Read the gacha-source + breeder stamp off the stack now (both gone by HATCH_EGG_POST).
        // The breeder stamp is present only on bred eggs (set at lay-time by BreedingParentTagHook).
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            CompoundTag tag = cd.copyTag();
            if (tag.contains("cobblemongacha_tier")) {
                BredTagHook.markGachaHatch(player.getUUID(), tick);
            }
            if (tag.contains(BredTagHook.EGG_BREEDER_KEY)) {
                BredTagHook.markBreederHatch(player.getUUID(), tick, tag.getString(BredTagHook.EGG_BREEDER_KEY));
            }
        }
    }
}
