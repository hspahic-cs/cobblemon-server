package com.cobblemonauction.gui

import com.cobblemonauction.CobblemonAuction
import com.cobblemonauction.service.AuctionService
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Price entry for a sell. The item is already escrowed in [AuctionService] (see
 * [AuctionService.beginSell]); this menu only collects a number.
 *
 * We reuse the vanilla anvil purely for its text field. A placeholder sits in input slot 0 so the
 * client enables the rename box; the player types the price there. We override the result pipeline
 * so the output slot (slot 2) becomes a "confirm" token only when the typed text is a valid price,
 * and taking it lists the item. Nothing about anvil XP cost or item repair applies — those paths
 * are overridden or short-circuited (the menu is built with the NULL level access).
 *
 * If the player closes the anvil without confirming, [removed] hands the escrowed item back.
 */
class PriceAnvilMenu(
    syncId: Int,
    playerInv: Inventory,
    private val viewer: ServerPlayer,
) : AnvilMenu(syncId, playerInv) {

    private var typed: String = ""
    private var confirmed = false

    /** The player's XP level when the price box opened. The vanilla anvil charges XP levels when you
     *  take its result; we only borrow the anvil for its text field, so we refund anything it took
     *  (see [removed]). */
    private val openXpLevel: Int = viewer.experienceLevel

    init {
        getSlot(INPUT).set(placeholder())
        refreshResult()
    }

    /** Called by the server on every keystroke in the rename box. We keep our own copy of the
     *  text and rebuild the result; vanilla's private field is intentionally left unused. */
    override fun setItemName(name: String): Boolean {
        typed = name
        createResult()
        return true
    }

    override fun createResult() {
        refreshResult()
        broadcastChanges()
    }

    /** The output is takeable only when a valid price is typed. */
    override fun mayPickup(player: Player, hasResult: Boolean): Boolean = parsePrice(typed) != null

    override fun onTake(player: Player, stack: ItemStack) {
        setCarried(ItemStack.EMPTY)          // never hand the confirm token to the player's cursor
        getSlot(RESULT).set(ItemStack.EMPTY)
        val sp = player as? ServerPlayer ?: return
        try {
            val price = parsePrice(typed)
            if (price == null) { sp.closeContainer(); return }
            confirmed = true                 // confirmSell consumes or returns the escrow itself
            report(sp, AuctionService.confirmSell(sp, price))
            getSlot(INPUT).set(ItemStack.EMPTY)
        } catch (e: Throwable) {
            CobblemonAuction.logger.error("Price-anvil confirm failed", e)
        } finally {
            sp.closeContainer()
        }
    }

    /** Block shift-click / quick-transfer entirely. The vanilla anvil's quick-move logic operates on
     *  its repair slots and misbehaves (crashes) on our hijacked slot layout — e.g. shift-clicking the
     *  result "paper". Confirming is a plain left-click on the result (see [onTake]), so nothing here
     *  needs quick-move. */
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    override fun removed(player: Player) {
        getSlot(INPUT).set(ItemStack.EMPTY)
        getSlot(RESULT).set(ItemStack.EMPTY)
        super.removed(player)
        if (player is ServerPlayer) {
            // Some XP charge lands during this tick's packet handling — sometimes AFTER removed()
            // runs — so refund on the next server task instead of synchronously here, and log what
            // (if anything) was taken so we can pin the source. Listing should never cost XP.
            val sp = player
            val before = openXpLevel
            sp.server.execute {
                val after = sp.experienceLevel
                if (after < before) {
                    sp.giveExperienceLevels(before - after)
                    CobblemonAuction.logger.info(
                        "Refunded ${before - after} XP level(s) charged while listing (open=$before, after=$after) for ${sp.gameProfile.name}")
                }
            }
        }
        if (!confirmed && player is ServerPlayer) AuctionService.cancelSell(player)
    }

    private fun refreshResult() {
        val price = parsePrice(typed)
        getSlot(RESULT).set(if (price != null) confirmStack(price) else hintStack())
    }

    private fun parsePrice(text: String): Int? {
        val cfg = CobblemonAuction.config
        val n = text.trim().toIntOrNull() ?: return null
        return if (n in cfg.minPrice..cfg.maxPrice) n else null
    }

    private fun confirmStack(price: Int): ItemStack {
        val fee = CobblemonAuction.config.listingFee(price)
        val lore = mutableListOf("§7Click to put it on the market.")
        if (fee > 0) lore += "§7Listing fee: §f\$$fee §8(refunded if it sells)"
        return Gui.button(Items.PAPER, "§a§lList for \$$price", *lore.toTypedArray())
    }

    private fun hintStack(): ItemStack {
        val cfg = CobblemonAuction.config
        return Gui.button(Items.PAPER, "§eType a price in the box above",
            "§7Enter a whole number between", "§7\$${cfg.minPrice} and \$${cfg.maxPrice}.")
    }

    /** Placeholder in the anvil input slot. The vanilla anvil seeds its rename box from this item's
     *  name, so we give it an EMPTY custom name — the box opens blank instead of showing literal
     *  "§fPrice" for the player to delete. The RESULT slot carries the "type a price" hint. */
    private fun placeholder(): ItemStack {
        val stack = ItemStack(Items.NAME_TAG)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("").setStyle(Style.EMPTY.withItalic(false)))
        return stack
    }

    private fun report(sp: ServerPlayer, res: AuctionService.ListResult) {
        // Success gets an explicit second line about the fee so the player can't miss the charge.
        if (res is AuctionService.ListResult.Success) {
            sp.sendSystemMessage(Component.literal(
                "§a[AH] Listed ${res.listing.count}× ${Gui.prettyItemName(res.listing.itemId)} for \$${res.listing.price}."))
            if (res.listing.fee > 0) {
                sp.sendSystemMessage(Component.literal(
                    "§7A §f\$${res.listing.fee} §7listing fee was charged — §arefunded if it sells§7, kept if it expires or you cancel."))
            }
            return
        }
        val msg = when (res) {
            is AuctionService.ListResult.Success -> ""   // handled above
            is AuctionService.ListResult.PriceOutOfRange ->
                "§c[AH] Price must be between \$${res.min} and \$${res.max}."
            is AuctionService.ListResult.FeeUnaffordable ->
                "§c[AH] The \$${res.fee} listing fee is more than your \$${res.have} — item returned."
            AuctionService.ListResult.EconomyUnavailable ->
                "§c[AH] The economy is unavailable right now — item returned, try again later."
            AuctionService.ListResult.NothingEscrowed -> "§c[AH] Nothing to list — start again from the Sell button."
            AuctionService.ListResult.Error -> "§c[AH] Couldn't create that listing — your item was returned."
        }
        sp.sendSystemMessage(Component.literal(msg))
    }

    companion object {
        // ItemCombinerMenu slot layout: 0,1 = inputs, 2 = result output.
        private const val INPUT = 0
        private const val RESULT = 2

        fun open(player: ServerPlayer) {
            val provider = SimpleMenuProvider(
                { syncId, inv, _ -> PriceAnvilMenu(syncId, inv, player) },
                Component.literal("§0Type price here"),
            )
            player.openMenu(provider)
        }
    }
}
