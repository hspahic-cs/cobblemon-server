package com.cobblemonbridge.breeding

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.CollectEggEvent
import com.cobblemon.mod.common.pokemon.OriginalTrainerType
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Rule 1 — **you can only breed Pokémon you are the Original Trainer of** (Dittos exempt).
 *
 * Enforced at egg-collection time via Cobblemon's cancelable [CollectEggEvent] (verified against
 * Cobblemon 1.7.3 bytecode: `extends Cancelable`, exposes `getMaleParent()`, `getFemaleParent()`,
 * and `getPlayer()`). When a player collects an egg from a pasture, both parents are inspected; if
 * either non-Ditto parent's Original Trainer is not the collecting player, the egg is cancelled and
 * the player is warned.
 *
 * Design note (per server owner): the warning is also surfaced the moment a non-OT Pokémon is
 * *tethered* to a pasture (see [PastureTetherWarnHook]); this hook is the authoritative gate that
 * actually stops the egg. "Misfires" — e.g. tethering a non-OT Pokémon purely for Cobbleworkers and
 * seeing the warning — are acceptable.
 *
 * Ditto is exempt because in the games a Ditto can breed with anything regardless of who owns it;
 * here that means a parent that *is* a Ditto never trips the OT check (the other parent still must
 * be the breeder's own).
 */
object BreedingOTHook {

    private const val DITTO = "ditto"

    fun registerEvents() {
        CobblemonEvents.COLLECT_EGG.subscribe(Priority.NORMAL) { event ->
            val player = event.player
            val offenders = listOf(event.maleParent, event.femaleParent)
                .filter { !isDitto(it) && !isOriginalTrainer(it, player) }
            if (offenders.isEmpty()) return@subscribe

            event.cancel()
            val names = offenders.joinToString(" & ") { it.species.translatedName.string }
            player.sendSystemMessage(Component.literal(
                "§c[Breeding] §fYou can only breed Pokémon you're the §eOriginal Trainer§f of. " +
                "§7($names — caught/owned by someone else.) Dittos are the only exception."
            ))
            CobblemonBridge.logger.info(
                "breeding-ot-block: {} tried to breed with non-OT parent(s) [{}] — egg cancelled",
                player.gameProfile.name,
                offenders.joinToString(",") { "${it.species.name}/ot=${it.originalTrainer}" },
            )
        }
    }

    private fun isDitto(p: Pokemon): Boolean = p.species.name.equals(DITTO, ignoreCase = true)

    /** True if [player] is the recorded player-type Original Trainer of [p]. */
    private fun isOriginalTrainer(p: Pokemon, player: ServerPlayer): Boolean =
        p.originalTrainerType == OriginalTrainerType.PLAYER &&
            p.originalTrainer == player.uuid.toString()
}
