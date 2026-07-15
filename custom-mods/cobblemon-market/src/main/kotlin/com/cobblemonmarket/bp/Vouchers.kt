package com.cobblemonmarket.bp

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

/**
 * Self-contained voucher items for the BP economy. A voucher is a Paper item tagged with
 * `custom_data { bp_voucher = <type> }` so it is a real, persistent, distinguishable item — not the
 * old stub that treated *any* paper as a voucher of *any* type.
 *
 * Recognised types: `tr`, `held_item`, `shiny`. TR and held-item vendors check for (and consume) a
 * matching voucher before charging money; the shiny voucher is consumed by an admin command.
 */
object Vouchers {

    private const val TAG_NAME = "bp_voucher"
    val VALID_TYPES = setOf("tr", "held_item", "shiny")

    private fun displayFor(type: String): String = when (type) {
        "tr" -> "§b§lTR Voucher"
        "held_item" -> "§a§lHeld Item Voucher"
        "shiny" -> "§d§lShiny Voucher"
        else -> "§f§lVoucher"
    }

    private fun loreFor(type: String): List<Component> = when (type) {
        "tr" -> listOf(
            line("§7Redeem at a §fTR Merchant §7for any TR,"),
            line("§7instead of paying money."),
        )
        "held_item" -> listOf(
            line("§7Redeem at a §fHeld-Item Vendor §7for any"),
            line("§7held item, instead of paying money."),
        )
        "shiny" -> listOf(
            line("§7Hand to an admin to turn one of your"),
            line("§7Pokémon shiny."),
        )
        else -> emptyList()
    }

    /** Component with italics off — vanilla auto-italicizes custom names/lore. */
    private fun line(s: String): Component =
        Component.literal(s).setStyle(Style.EMPTY.withItalic(false))

    /** Build a voucher ItemStack of [count] for [type] (must be in [VALID_TYPES]). */
    fun create(type: String, count: Int = 1): ItemStack {
        val stack = ItemStack(Items.PAPER, count)
        stack.set(DataComponents.CUSTOM_NAME, line(displayFor(type)))
        stack.set(DataComponents.LORE, ItemLore(loreFor(type)))
        val tag = CompoundTag()
        tag.putString(TAG_NAME, type)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        return stack
    }

    /** The voucher type encoded in [stack]'s custom_data, or null if it isn't a voucher. */
    fun typeOf(stack: ItemStack): String? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = data.copyTag()
        if (!tag.contains(TAG_NAME)) return null
        val type = tag.getString(TAG_NAME)
        return if (type in VALID_TYPES) type else null
    }

    fun isVoucher(stack: ItemStack, expectedType: String): Boolean = typeOf(stack) == expectedType

    fun hasVoucher(player: Player, type: String): Boolean {
        for (i in 0 until player.inventory.containerSize) {
            if (isVoucher(player.inventory.getItem(i), type)) return true
        }
        return false
    }

    /** Consume exactly one [type] voucher from [player]'s inventory. Returns true if one was found. */
    fun consume(player: Player, type: String): Boolean {
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (isVoucher(stack, type)) {
                stack.shrink(1)
                return true
            }
        }
        return false
    }
}
