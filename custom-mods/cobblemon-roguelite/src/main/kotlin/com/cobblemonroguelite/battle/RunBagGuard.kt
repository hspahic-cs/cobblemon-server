package com.cobblemonroguelite.battle

import com.cobblemon.mod.common.api.item.PokemonSelectingItem
import com.cobblemon.mod.common.battles.BagItems
import com.cobblemon.mod.common.item.battle.BagItemLike
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.ICancellableEvent
import com.cobblemonroguelite.run.RunInventoryStash
import com.cobblemonroguelite.run.RunItems
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * §2.11: a player may not use their own items inside a wave battle.
 *
 * ### Why the isolation matters more than it looks
 *
 * Permadeath is the mode. A potion from the player's own inventory makes it negotiable, a revive
 * undoes it outright, and both bypass the reward loop entirely — §1.1's isolation contract means
 * nothing if healing is unlimited and supplied from outside the run. The bag a run *does* have is
 * the one the run granted.
 *
 * ### Why this is three interaction cancels and not one switch
 *
 * `Bag Clause` on the battle format ([RunWildBattle]) is the switch, and it covers exactly one of the
 * two ways an item reaches a battle: `BagItemLike.handleInteraction`, which is what right-clicking a
 * battling Pokémon with a berry goes through. The other way is `PokemonSelectingItem` — a potion or a
 * revive used from the hotbar, which opens a party picker over the **battle actor's** team and never
 * consults the format's rule set at all. There is no third place both meet on the way in, and
 * `BagItem.canUse` is per-item and belongs to items this module does not own.
 *
 * What both paths *do* share is their origin: the player used an item. So the rejection sits there,
 * on NeoForge's interaction events, which are cancellable and fire before either path begins. That is
 * the "action layer" §2.11 asks for, and it is a blanket rejection rather than an allowlist because
 * the decision permits one and an allowlist would have to be re-audited every time Cobblemon or an
 * addon adds an item.
 *
 * ### What is deliberately not blocked
 *
 * **Poké Balls.** §2.13 makes catching the party system, so a guard that blocked every item would
 * block the mode's own progression. Balls are neither [BagItemLike] nor [PokemonSelectingItem], so
 * the predicate lets them through by construction rather than by exception.
 *
 * **Everything outside a wave battle.** The gate is [RunBattles.isFighting], not "is in a run": the
 * between-wave steps of §2.12 are where run-granted items are meant to be used, and a guard that
 * covered the whole run would block those too.
 */
object RunBagGuard {

    private val registered = AtomicBoolean(false)

    /** Register once. A second registration would cancel twice, which is the same but costs twice. */
    fun register() {
        if (!registered.compareAndSet(false, true)) return
        // All three, because which one fires depends on what the player happened to be pointing at
        // when they right-clicked. Blocking only RightClickItem would leave the bag fully usable to
        // anyone standing close enough to the arena floor to be targeting a block.
        NeoForge.EVENT_BUS.addListener<PlayerInteractEvent.RightClickItem> { refuse(it, it.entity as? ServerPlayer, it.itemStack) }
        NeoForge.EVENT_BUS.addListener<PlayerInteractEvent.RightClickBlock> { refuse(it, it.entity as? ServerPlayer, it.itemStack) }
        NeoForge.EVENT_BUS.addListener<PlayerInteractEvent.EntityInteract> { refuse(it, it.entity as? ServerPlayer, it.itemStack) }
        log.debug("roguelite: run bag isolation active")
    }

    private fun refuse(event: ICancellableEvent, player: ServerPlayer?, stack: ItemStack) {
        if (player == null || stack.isEmpty) return
        val fighting = RunBattles.isFighting(player.uuid)
        val tagged = RunInventoryStash.isTagged(player)
        if (!fighting && !tagged) return
        if (!isBagItem(stack)) return
        // The §2.11 reversal, implemented (user decision 2026-07-31): run-ISSUED bag items — marked
        // with the run's seed at their mint — are usable anywhere in a run, including battle, where
        // Cobblemon itself charges the turn for the throw. Player-owned (unmarked) bag items stay
        // refused everywhere: they are the free-healing economy §2.11 was written against, and while
        // swapped they should not even exist — an unmarked bag item here slipped past the stash.
        if (!RunItems.isRunItem(stack)) event.isCanceled = true
    }

    /**
     * Whether [stack] is something a battle would accept as a bag item.
     *
     * Three tests and not one, because Cobblemon has three ways of being one: the interface an item
     * implements to be usable on a battling Pokémon, the interface it implements to open a party
     * picker, and the datapack registry a server owner can add to without writing an item at all.
     * Missing the third is how a server that adds its own healing item finds §2.11 quietly not
     * applying to it.
     */
    private fun isBagItem(stack: ItemStack): Boolean =
        stack.item is BagItemLike ||
            stack.item is PokemonSelectingItem ||
            BagItems.getConvertibleForStack(stack) != null
}
