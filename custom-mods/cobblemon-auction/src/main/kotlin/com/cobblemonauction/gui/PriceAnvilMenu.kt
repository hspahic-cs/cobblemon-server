package com.cobblemonauction.gui

import com.cobblemonauction.CobblemonAuction
import com.cobblemonauction.service.AuctionService
import net.minecraft.network.chat.Component
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
        val price = parsePrice(typed)
        if (price == null) { sp.closeContainer(); return }
        confirmed = true                     // confirmSell consumes or returns the escrow itself
        report(sp, AuctionService.confirmSell(sp, price))
        getSlot(INPUT).set(ItemStack.EMPTY)
        sp.closeContainer()
    }

    override fun removed(player: Player) {
        getSlot(INPUT).set(ItemStack.EMPTY)
        getSlot(RESULT).set(ItemStack.EMPTY)
        super.removed(player)
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

    private fun placeholder(): ItemStack = Gui.button(Items.NAME_TAG, "§fPrice")

    private fun report(sp: ServerPlayer, res: AuctionService.ListResult) {
        val msg = when (res) {
            is AuctionService.ListResult.Success -> {
                val feeNote = if (res.listing.fee > 0) " §7(fee \$${res.listing.fee}, refunded if it sells)" else ""
                "§a[AH] Listed ${res.listing.count}× ${Gui.prettyItemName(res.listing.itemId)} for \$${res.listing.price}.$feeNote"
            }
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
                Component.literal("§0Set a price — type a number"),
            )
            player.openMenu(provider)
        }
    }
}
