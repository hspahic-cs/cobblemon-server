package com.cobblemongacha.gui

import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.reward.RewardGranter
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.item.component.ItemLore

/**
 * Read-only preview of a loot table. Opens a vanilla chest menu (1x9, 2x9, or 3x9 depending on
 * entry count). The mod does not register a custom MenuType — clients without the gacha mod
 * would otherwise be rejected during registry sync at join.
 *
 * Read-only-ness is best-effort: vanilla ChestMenu allows clicks client-side, but the menu's
 * SimpleContainer has no real persistence; once the player closes the GUI the items vanish, so
 * any "stolen" stack is throw-away. For the typical browse-and-close usage this is fine.
 */
object OddsMenu {

    fun openFor(player: ServerPlayer, tier: KeyTier, table: LootTable) {
        // Show all entries — including 0%-weight placeholders. Players were asking "what's in
        // this box?" and missing the coming-soon eggs entirely because they used to be filtered
        // out. Rollable entries first (highest chance → lowest), placeholders after, so the box
        // reads top-to-bottom as "what you'll actually get" then "what's planned".
        val rollable = table.entries.filter { it.weightPct > 0.0 }
            .sortedByDescending { it.weightPct }
        val placeholders = table.entries.filter { it.weightPct <= 0.0 }
        val all = rollable + placeholders
        val rows = ((all.size + 8) / 9).coerceAtMost(6).coerceAtLeast(1)
        val cap = rows * 9
        val display = SimpleContainer(cap)
        all.take(cap).forEachIndexed { i, entry ->
            val stack = RewardGranter.representative(entry).copy()
            if (stack.isEmpty) return@forEachIndexed
            val isPlaceholder = entry.weightPct <= 0.0
            val namePrefix = if (isPlaceholder) "§8" else tierColor(entry.lootTier.name)
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("$namePrefix${entry.label}"))
            val lore = mutableListOf<Component>(
                Component.literal("§7Tier: §f${entry.lootTier.name}"),
                if (isPlaceholder) {
                    Component.literal("§8Coming soon — not rolling yet")
                } else {
                    Component.literal("§7Chance: §a${"%.1f".format(entry.weightPct)}%")
                },
            )
            if (entry.notes.isNotBlank()) lore += Component.literal("§8${entry.notes}")
            stack.set(DataComponents.LORE, ItemLore(lore))
            display.setItem(i, stack)
        }
        val title = Component.literal("§e[${tier.displayName} Box] §7Possible Rewards")
        val provider = SimpleMenuProvider({ syncId, inv, _ ->
            GachaChestMenu(rows = rows, syncId = syncId, inv = inv, container = display)
        }, title)
        player.openMenu(provider)
    }

    private fun tierColor(name: String): String = when (name) {
        "Floor" -> "§7"
        "Mid" -> "§b"
        "High" -> "§6"
        "Jackpot" -> "§d"
        else -> "§f"
    }
}
