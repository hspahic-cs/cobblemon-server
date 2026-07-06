package com.cobblemonauction.gui

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

/** Shared chest-GUI helpers for the auction menus. */
internal object Gui {

    /** A non-italic literal — vanilla auto-italicizes custom item names/lore otherwise. */
    fun line(s: String): MutableComponent =
        Component.literal(s).setStyle(Style.EMPTY.withItalic(false))

    /** Build a labelled button/icon stack with the given name + lore lines. */
    fun button(item: Item, name: String, vararg lore: String): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, line(name))
        if (lore.isNotEmpty()) {
            stack.set(DataComponents.LORE, ItemLore(lore.map { line(it) as Component }))
        }
        return stack
    }

    /** Overwrite a stack's lore with [lines] (leaves its custom name / components intact). */
    fun withLore(stack: ItemStack, lines: List<String>): ItemStack {
        stack.set(DataComponents.LORE, ItemLore(lines.map { line(it) as Component }))
        return stack
    }

    /** "itemid:some_thing" -> "Some Thing" for chat messages. */
    fun prettyItemName(itemId: String): String =
        itemId.substringAfterLast(':').split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    /** Human "time left" from a millisecond duration: "3d 4h", "5h 12m", "42m", or — for the final
     *  stretch — "under a minute" (a pending-expiry cue; listings are removed a minute early, so a
     *  bare zeroed countdown never shows). */
    fun timeLeft(millis: Long): String {
        if (millis < 60_000) return "under a minute"
        val totalMin = millis / 60_000
        val d = totalMin / 1440
        val h = (totalMin % 1440) / 60
        val m = totalMin % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }
}
