package com.cobblemonbridge.mixin;

import com.cobblemonbridge.eggs.BredTagHook;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two hooks into Cobreeding's {@code PokemonEgg.inventoryTick}:
 *
 * <ol>
 *   <li><b>Gacha/breeder source capture</b> — at the {@code hatchEgg(player, props)} call, records
 *       the player UUID + server tick in {@link BredTagHook} so the synchronously-fired
 *       {@code HATCH_EGG_POST} subscriber can skip the bred tag for crate/gacha eggs and route bred
 *       mons to the breeder's PC.</li>
 *   <li><b>Native timer suppression</b> — Cobreeding's {@code inventoryTick} decrements its own
 *       {@code cobbreeding:timer} component once per second (the {@code -20}/{@code -40} writes).
 *       For eggs the bridge manages ({@link com.cobblemonbridge.eggs.EggDefeatHook} stamps
 *       {@code cobblemongacha_bridge_initialized}) the bridge is the SINGLE source of truth for the
 *       hatch timer — it re-pins {@code cobbreeding:timer} from its own per-second counter, gated on
 *       non-AFK playtime and the incubation cap. Cobreeding's native decrement running in between
 *       fought that re-sync: it made "Not incubating" (capped) eggs still tick down, and produced
 *       hotbar-vs-inventory and chest-vs-inventory timer divergence (the native decrement raced the
 *       bridge's once-per-second slot sync). The {@link #cobblemonbridge$suppressNativeTimerDecrement}
 *       redirect no-ops ONLY the {@code cobbreeding:timer} writes on bridge-managed eggs, so nothing
 *       moves that timer except the bridge. The {@code second} sub-tick counter, the {@code <= 0}
 *       hatch check, and the native {@code hatchEgg} call all stay intact — when the bridge wants an
 *       egg to hatch it pins the timer to {@code 0} and Cobreeding hatches it on its next tick.</li>
 * </ol>
 *
 * <p>{@code remap = false} because Cobreeding is a NeoForge mod (Mojmap names at runtime); the
 * vanilla {@code ItemStack.set} target resolves under the same Mojmap-native names.
 *
 * <p>The mark→consume window for (1) is a single synchronous call chain on the server tick thread
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

    /**
     * Intercept every {@code ItemStack.set} inside {@code inventoryTick} and no-op the ones writing
     * {@code cobbreeding:timer} on a bridge-managed egg — Cobreeding's native timer decrement. The
     * {@code cobbreeding:second} sub-tick counter and any non-bridge egg pass through untouched, so
     * native behaviour is fully preserved for anything the bridge isn't driving (defensive: if an
     * egg never gets bridge-initialized, it still hatches the vanilla Cobreeding way).
     */
    @Redirect(
        method = "inventoryTick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"
        )
    )
    @SuppressWarnings({"rawtypes", "unchecked"})  // raw DataComponentType matches the erased call site
    private Object cobblemonbridge$suppressNativeTimerDecrement(ItemStack self, DataComponentType type, Object value) {
        if (cobblemonbridge$isCobbreedingTimer(type) && cobblemonbridge$isBridgeManagedEgg(self)) {
            return self.get(type);  // leave the component exactly as the bridge last set it
        }
        return self.set(type, value);
    }

    private static boolean cobblemonbridge$isCobbreedingTimer(DataComponentType<?> type) {
        ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return key != null && "cobbreeding".equals(key.getNamespace()) && "timer".equals(key.getPath());
    }

    private static boolean cobblemonbridge$isBridgeManagedEgg(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().getBoolean("cobblemongacha_bridge_initialized");
    }
}
