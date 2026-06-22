package com.cobblemonbridge.mixin;

import com.cobblemonbridge.eggs.BredTagHook;
import com.cobblemonbridge.eggs.HatchAnnounceHook;
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

        // Read the egg's gacha tier + breeder stamp off the stack now (both gone by
        // HATCH_EGG_POST). Tier is empty for daycare-bred eggs; the breeder stamp is present only
        // on bred eggs (set at lay-time by BreedingParentTagHook).
        String tier = "";
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            CompoundTag tag = cd.copyTag();
            if (tag.contains("cobblemongacha_tier")) {
                tier = tag.getString("cobblemongacha_tier");
                BredTagHook.markGachaHatch(player.getUUID(), tick);
            }
            if (tag.contains(BredTagHook.EGG_BREEDER_KEY)) {
                BredTagHook.markBreederHatch(player.getUUID(), tick, tag.getString(BredTagHook.EGG_BREEDER_KEY));
            }
        }
        HatchAnnounceHook.markHatchTier(player.getUUID(), tick, tier);
    }
}
