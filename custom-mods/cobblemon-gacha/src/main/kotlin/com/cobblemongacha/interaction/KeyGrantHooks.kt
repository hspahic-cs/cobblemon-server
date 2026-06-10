package com.cobblemongacha.interaction

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.item.KeyItems
import com.cobblemongacha.util.TickScheduler
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import java.time.LocalDate

/**
 * Wires the two daily key grants:
 *   - Login: PlayerEvent.PlayerLoggedInEvent (NeoForge bus)
 *   - First PvP ranked win of the day: CobblemonEvents.BATTLE_VICTORY (Cobblemon bus)
 *
 * Each grant is gated on `lastLoginGrantDate` / `lastRankedGrantDate` matching today.
 */
object KeyGrantHooks {

    fun registerCobblemonHooks() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            // Mirror the pattern from RankedBattle.kt:
            // Detect PvP: at least 2 distinct PlayerBattleActor instances in the battle.
            val allPlayerActors = event.battle.actors.filterIsInstance<PlayerBattleActor>()
            if (allPlayerActors.size < 2) return@subscribe

            // Iterate winning player actors exactly as RankedBattle.kt does.
            val winners = event.winners.filterIsInstance<PlayerBattleActor>()
            for (actor in winners) {
                val player = actor.entity ?: continue
                tryGrantRanked(player)
            }
        }
    }

    @SubscribeEvent
    fun onLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        // 10-second delay (200 ticks) so the daily key doesn't race the starter-kit grant.
        // Starter-kit fires on first-time players and rewrites slots 0–7 a few ticks after
        // login — without this delay the key lands in slot 0, then the kit overwrites it.
        TickScheduler.later(200) {
            tryGrantWelcome(player)
            tryGrantLogin(player)
        }
    }

    /**
     * One-time welcome grant: 1 Rare Key + 1 Pokémon Key, exactly once per player, ever.
     *
     * Idempotency — the part that caused double-grants before — is guaranteed by setting the
     * `grantedWelcomeKeys` flag and PERSISTING it via save() BEFORE handing out any item:
     *
     *   1. Read the (persistent) flag; bail if already set.
     *   2. Set the flag and `save()` it to disk.
     *   3. Only then build + give the keys.
     *
     * So if the inventory is full, the server crashes mid-grant, or the login event somehow fires
     * twice, the flag is already on disk and the keys are never granted again. This runs on the
     * server thread (via TickScheduler), so the read->set->save sequence is atomic with respect to
     * the daily/ranked grants — no check-then-act race. We deliberately err toward "never twice"
     * over "always once": a save failure just means the player retries next login, never a double
     * grant. The flag lives in `runtime/players.json`, which deploys never overwrite, so it also
     * survives mod updates.
     */
    private fun tryGrantWelcome(player: ServerPlayer) {
        if (!player.isAlive) return  // disconnected before the delay finished
        val data = CobblemonGacha.playerStore.getOrCreate(player.uuid, player.name.string)
        if (data.grantedWelcomeKeys) return  // already granted, ever — never repeat
        data.grantedWelcomeKeys = true       // mark first…
        CobblemonGacha.playerStore.save()    // …persist to disk BEFORE granting anything
        giveKey(player, KeyTier.RARE)
        giveKey(player, KeyTier.POKEMON)
        player.sendSystemMessage(
            Component.literal("§e[Gacha] Welcome bonus: §6+1 Rare Key §eand §a+1 Pokémon Key§e!"),
        )
        CobblemonGacha.logger.info("Granted one-time welcome keys (Rare + Pokémon) to {}", player.name.string)
    }

    private fun giveKey(player: ServerPlayer, tier: KeyTier) {
        val stack = KeyItems.build(tier)
        if (!player.inventory.add(stack)) player.drop(stack, false)
    }

    private fun tryGrantLogin(player: ServerPlayer) {
        if (!player.isAlive) return  // player disconnected before the delay finished
        val today = LocalDate.now().toString()
        val data = CobblemonGacha.playerStore.getOrCreate(player.uuid, player.name.string)
        if (data.lastLoginGrantDate == today) return
        data.lastLoginGrantDate = today
        CobblemonGacha.playerStore.save()
        val stack = KeyItems.build(KeyTier.COMMON)
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
        player.sendSystemMessage(Component.literal("§e[Gacha] Daily login bonus: §6+1 Common Key"))
        CobblemonGacha.logger.info("Granted login key to {}", player.name.string)
    }

    private fun tryGrantRanked(player: ServerPlayer) {
        val today = LocalDate.now().toString()
        val data = CobblemonGacha.playerStore.getOrCreate(player.uuid, player.name.string)
        if (data.lastRankedGrantDate == today) return
        data.lastRankedGrantDate = today
        CobblemonGacha.playerStore.save()
        val stack = KeyItems.build(KeyTier.COMMON)
        if (!player.inventory.add(stack)) player.drop(stack, false)
        player.sendSystemMessage(Component.literal("§e[Gacha] First ranked win today: §6+1 Common Key"))
        CobblemonGacha.logger.info("Granted ranked key to {}", player.name.string)
    }
}
