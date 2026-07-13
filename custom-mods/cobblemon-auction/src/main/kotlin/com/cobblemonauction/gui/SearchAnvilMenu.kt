package com.cobblemonauction.gui

import com.cobblemonauction.CobblemonAuction
import com.cobblemonauction.service.RequestService
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Free-text item search for the create-request flow. Same borrowed-anvil text-input mechanic as
 * [PriceAnvilMenu]: a placeholder sits in input slot 0 so the client enables the rename box, the
 * player types an item name there, and the result slot (slot 2) becomes a "search" token whenever
 * the box is non-blank. Taking it runs [RequestService.searchItems] and opens the results grid
 * ([CatalogMenu.openResults]); a blank box just closes, and a zero-hit query reopens this search
 * with a chat hint. Nothing is escrowed here, so abandoning the screen costs nothing.
 */
class SearchAnvilMenu(
    syncId: Int,
    playerInv: Inventory,
    private val viewer: ServerPlayer,
) : AnvilMenu(syncId, playerInv) {

    private var typed: String = ""

    /** Player's XP level when the box opened — the vanilla anvil visually charges a level on take even
     *  though we never really do; we resync on close (see [removed]), mirroring [PriceAnvilMenu]. */
    private val openXpLevel: Int = viewer.experienceLevel

    init {
        getSlot(INPUT).set(placeholder())
        refreshResult()
    }

    override fun setItemName(name: String): Boolean {
        typed = name
        createResult()
        return true
    }

    override fun createResult() {
        refreshResult()
        broadcastChanges()
    }

    /** Takeable only once something is typed. */
    override fun mayPickup(player: Player, hasResult: Boolean): Boolean = typed.trim().isNotEmpty()

    override fun onTake(player: Player, stack: ItemStack) {
        setCarried(ItemStack.EMPTY)          // never hand the search token to the player's cursor
        getSlot(RESULT).set(ItemStack.EMPTY)
        val sp = player as? ServerPlayer ?: return
        val query = typed.trim()
        getSlot(INPUT).set(ItemStack.EMPTY)
        // Defer the follow-up menu one task so it opens cleanly after this anvil finishes closing.
        try {
            if (query.isEmpty()) return                       // nothing typed — just close
            val results = RequestService.searchItems(query, CatalogMenu.SEARCH_LIMIT)
            if (results.isEmpty()) {
                sp.sendSystemMessage(Component.literal(
                    "§e[AH] No items match \"$query\" — try a different name."))
                sp.server.execute { SearchAnvilMenu.open(sp) }   // reopen so they can retype
                return
            }
            val capped = results.size >= CatalogMenu.SEARCH_LIMIT
            sp.server.execute { CatalogMenu.openResults(sp, results, query, capped) }
        } catch (e: Throwable) {
            CobblemonAuction.logger.error("Item search failed for query '$query'", e)
        } finally {
            sp.closeContainer()
        }
    }

    /** Block shift-click / quick-transfer — the vanilla anvil's quick-move misbehaves on our hijacked
     *  slots (see [PriceAnvilMenu.quickMoveStack]). Confirming is a plain left-click on the result. */
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    override fun removed(player: Player) {
        getSlot(INPUT).set(ItemStack.EMPTY)
        getSlot(RESULT).set(ItemStack.EMPTY)
        super.removed(player)
        if (player is ServerPlayer) {
            // Resync the client's XP display — the anvil UI drops a "rename cost" level client-side
            // even though the server never charges it (identical to PriceAnvilMenu).
            val sp = player
            sp.server.execute {
                if (sp.experienceLevel < openXpLevel) {
                    sp.giveExperienceLevels(openXpLevel - sp.experienceLevel)
                }
                sp.connection.send(ClientboundSetExperiencePacket(
                    sp.experienceProgress, sp.totalExperience, sp.experienceLevel))
            }
        }
    }

    private fun refreshResult() {
        getSlot(RESULT).set(if (typed.trim().isNotEmpty()) confirmStack() else hintStack())
    }

    private fun confirmStack(): ItemStack = Gui.button(
        Items.COMPASS, "§a§lSearch \"${typed.trim().take(24)}\"",
        "§7Click to search every item for this name.",
    )

    private fun hintStack(): ItemStack = Gui.button(
        Items.COMPASS, "§eType an item name in the box above",
        "§7e.g. §fenchanted book§7, §frare candy§7, §fnetherite§7.",
        "§7Then click here to search.",
    )

    /** Placeholder in the anvil input slot with a blank custom name, so the rename box opens empty
     *  (see [PriceAnvilMenu.placeholder]). */
    private fun placeholder(): ItemStack {
        val stack = ItemStack(Items.NAME_TAG)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("").setStyle(Style.EMPTY.withItalic(false)))
        return stack
    }

    companion object {
        // ItemCombinerMenu slot layout: 0,1 = inputs, 2 = result output.
        private const val INPUT = 0
        private const val RESULT = 2

        fun open(player: ServerPlayer) {
            val provider = SimpleMenuProvider(
                { syncId, inv, _ -> SearchAnvilMenu(syncId, inv, player) },
                Component.literal("§0Type an item name"),
            )
            player.openMenu(provider)
        }
    }
}
