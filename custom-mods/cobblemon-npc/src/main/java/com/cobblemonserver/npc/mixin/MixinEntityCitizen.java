package com.cobblemonserver.npc.mixin;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemonserver.npc.battle.NpcBattleHandler;
import com.cobblemonserver.npc.data.NpcTeamData;
import com.cobblemonserver.npc.data.NpcTeamStore;
import com.cobblemonserver.npc.gym.GymLeaderManager;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts EntityCitizen.checkAndHandleImportantInteractions() — the first method
 * called on a right-click — so we can start a Cobblemon battle when the player is
 * holding a Poke Ball, or re-send the gym-leader theme picker if the hirer hasn't
 * locked one in yet.
 *
 * When neither trigger matches, control falls through to the vanilla Minecolonies
 * interaction (inventory, job, rename, etc.) unchanged.
 */
@Mixin(EntityCitizen.class)
public class MixinEntityCitizen {

    @Inject(method = "checkAndHandleImportantInteractions", at = @At("HEAD"), cancellable = true)
    private void cobblemonNpc$interceptBattleChallenge(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (hand != InteractionHand.MAIN_HAND) return;
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PokeBallItem)) return;

        NpcBattleHandler.startBattle(serverPlayer, (EntityCitizen) (Object) this);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    @Inject(method = "checkAndHandleImportantInteractions", at = @At("HEAD"), cancellable = true)
    private void cobblemonNpc$resendGymPickerIfOwed(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (hand != InteractionHand.MAIN_HAND) return;
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        EntityCitizen self = (EntityCitizen) (Object) this;
        NpcTeamData data = NpcTeamStore.INSTANCE.get(self);
        if (data == null) return;
        if (data.getGymHirerUuid() == null || data.getGymLeaderTheme() != null) return;
        if (!serverPlayer.getUUID().equals(data.getGymHirerUuid())) return;

        if (GymLeaderManager.INSTANCE.resendPickerIfOwed(serverPlayer, self)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
