package com.cobblemonbridge.profile

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.item.PokemonItem
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

/**
 * Read-only chest GUI showing a player's profile snapshot.
 *
 * 6 rows × 9 cols layout:
 *   row 0:  9 black-pane separators with a player-head in slot 4 carrying the header lore
 *           (display name + colony + total badges + level cap as a one-glance digest)
 *   row 1:  badges & level cap & ELO & income — one labelled token per stat
 *   row 2:  favorite pokemon + last team — pokemon-model items
 *   rows 3-5: spacer panes (kept for future expansion)
 */
object ProfileMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9

    fun open(viewer: ServerPlayer, snapshot: ProfileSnapshot) {
        val container = SimpleContainer(SLOTS)
        populate(container, snapshot)
        val title = Component.literal("§0§lProfile — ${snapshot.displayName}")
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, viewer) },
            title,
        )
        viewer.openMenu(provider)
    }

    private fun line(s: String): MutableComponent =
        Component.literal(s).setStyle(Style.EMPTY.withItalic(false))

    private fun populate(container: Container, p: ProfileSnapshot) {
        // Background — gray glass panes everywhere first, then overwrite specific tiles.
        val pane = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).also {
            it.set(DataComponents.CUSTOM_NAME, line(" "))
        }
        for (i in 0 until SLOTS) container.setItem(i, pane)

        // Row 0 — header: player head with summary lore in the centre slot.
        container.setItem(4, headerStack(p))

        // Row 1 — badges (slot 10), level cap (12), ELO (14), income (16). Each its own tile.
        container.setItem(10, badgesStack(p))
        container.setItem(12, levelCapStack(p))
        container.setItem(14, eloStack(p))
        container.setItem(16, incomeStack(p))

        // Row 2 — favourite Pokemon (slot 20) + colony (22).
        container.setItem(20, favouriteStack(p))
        container.setItem(22, colonyStack(p))

        // Row 3 — last ranked team across slots 27..32 (6 slots).
        for (i in 0 until 6) {
            container.setItem(27 + i, teamSlotStack(p.lastTeam.getOrNull(i)))
        }

        container.setChanged()
    }

    private fun headerStack(p: ProfileSnapshot): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        // Attach the target player's GameProfile so the head renders with their actual skin.
        // ResolvableProfile takes (Optional<name>, Optional<UUID>, PropertyMap) — passing the
        // UUID is enough; the client resolves the texture URL from session servers (or its
        // local cache) on receipt. Name is included too as a hint for clients without UUID-
        // based skin lookups configured.
        val profile = net.minecraft.world.item.component.ResolvableProfile(
            java.util.Optional.of(p.displayName),
            java.util.Optional.of(p.playerUuid),
            com.mojang.authlib.properties.PropertyMap(),
        )
        stack.set(DataComponents.PROFILE, profile)
        stack.set(DataComponents.CUSTOM_NAME, line("§e§l${p.displayName}"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line(""),
            line("§7Tap a tile below for details."),
        )))
        return stack
    }

    private fun badgesStack(p: ProfileSnapshot): ItemStack {
        val stack = ItemStack(Items.GOLDEN_HORSE_ARMOR)
        stack.set(DataComponents.CUSTOM_NAME, line("§6Gym Badges"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§f${p.gymBadgeCount} §7/ §f${p.gymBadgeTotal}"),
            line(""),
            line("§7Earned by defeating the server's gym leaders."),
        )))
        return stack
    }

    private fun levelCapStack(p: ProfileSnapshot): ItemStack {
        val stack = ItemStack(Items.EXPERIENCE_BOTTLE)
        stack.set(DataComponents.CUSTOM_NAME, line("§aLevel Cap"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§fLv. ${p.levelCap}"),
            line(""),
            line("§720 + 5 per mainline gym beaten."),
        )))
        return stack
    }

    private fun eloStack(p: ProfileSnapshot): ItemStack {
        val stack = ItemStack(Items.DIAMOND_SWORD)
        stack.set(DataComponents.CUSTOM_NAME, line("§bRanked ELO"))
        val lore = if (p.elo == null)
            listOf(line("§8No ranked record yet"))
        else
            listOf(
                line("§f${p.elo}"),
                line("§a${p.wins ?: 0}W §7/ §c${p.losses ?: 0}L"),
            )
        stack.set(DataComponents.LORE, ItemLore(lore))
        return stack
    }

    private fun incomeStack(p: ProfileSnapshot): ItemStack {
        val stack = ItemStack(Items.GOLD_INGOT)
        stack.set(DataComponents.CUSTOM_NAME, line("§6Balance"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§f\$${p.income}"),
            line(""),
            line("§7Cobble dollars currently in this player's wallet."),
        )))
        return stack
    }

    private fun favouriteStack(p: ProfileSnapshot): ItemStack {
        val fav = p.favorite
        if (fav == null) {
            val placeholder = ItemStack(Items.CARROT)
            placeholder.set(DataComponents.CUSTOM_NAME, line("§7No favourite yet"))
            placeholder.set(DataComponents.LORE, ItemLore(listOf(
                line("§8Feed a Pokémon carrots to get started.")
            )))
            return placeholder
        }
        // Render with the Pokemon's own model. Properties with just the species name is enough
        // to satisfy PokemonItem.from(props) — aspects defaults to the empty set inside the
        // PokemonItemComponent constructor.
        val props = PokemonProperties.parse(fav.species)
        val stack = try { PokemonItem.from(props) } catch (e: Throwable) { ItemStack(Items.CARROT) }
        stack.set(DataComponents.CUSTOM_NAME, line("§e§lFavourite: ${fav.species.replaceFirstChar { it.uppercase() }}"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Total HP fed (carrots + healer): §f${fav.hpFed}"),
        )))
        return stack
    }

    private fun colonyStack(p: ProfileSnapshot): ItemStack {
        val stack = ItemStack(Items.OAK_DOOR)
        if (p.colony == null) {
            stack.set(DataComponents.CUSTOM_NAME, line("§7No MineColony"))
            stack.set(DataComponents.LORE, ItemLore(listOf(line("§8Has not founded a colony."))))
        } else {
            stack.set(DataComponents.CUSTOM_NAME, line("§aColony: §f${p.colony}"))
        }
        return stack
    }

    private fun teamSlotStack(species: String?): ItemStack {
        if (species.isNullOrBlank()) {
            val pane = ItemStack(Items.GRAY_STAINED_GLASS_PANE)
            pane.set(DataComponents.CUSTOM_NAME, line("§8Empty slot"))
            return pane
        }
        return try {
            val props = PokemonProperties.parse(species)
            val stack = PokemonItem.from(props)
            stack.set(DataComponents.CUSTOM_NAME, line("§f${species.replaceFirstChar { it.uppercase() }}"))
            stack
        } catch (e: Throwable) {
            val fallback = ItemStack(Items.PAPER)
            fallback.set(DataComponents.CUSTOM_NAME, line("§f$species"))
            fallback
        }
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {
        // Fully read-only — no clicks should mutate the chest or the viewer's inventory.
        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId in 0 until SLOTS) return
            super.clicked(slotId, button, clickType, player)
        }
        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY
    }
}
