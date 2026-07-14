package com.cobblemonmarket.bp

import com.cobblemonmarket.CobblemonMarket
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

/**
 * Maps a [BpItemEntry] id to a concrete deliverable. Three delivery mechanisms:
 *
 *  - **Vouchers** (`tr_voucher`, `held_item_voucher`, `shiny_voucher`) → a [Vouchers] paper item.
 *  - **Gacha crate keys** (`ultra_key`, `rare_key`, `pokemon_crate`) → the same vanilla-item +
 *    `custom_data { gacha_key = <tier> }` scheme the gacha mod's `KeyItems` uses, so a shop-bought
 *    key is indistinguishable from a rolled one and works at the crates. The market module has no
 *    compile dependency on gacha, so the tag is replicated here rather than imported.
 *  - **Plain registry items** (`master_ball`, `rare_candy`, `ability_patch`, `totem_of_undying`) →
 *    a straight `BuiltInRegistries.ITEM` lookup.
 *  - **`shiny_egg`** has no item form; it is granted by dispatching the gacha `giveegg … shiny`
 *    command (permission-elevated), reusing gacha's egg logic.
 */
object BpItems {

    private const val GACHA_KEY_TAG = "gacha_key"

    /** A shop id backed by a gacha crate key: (vanilla item, gacha tier key, display name). */
    private data class KeySpec(val item: net.minecraft.world.item.Item, val tier: String, val name: String)

    private val KEY_SPECS: Map<String, KeySpec> = mapOf(
        "ultra_key" to KeySpec(Items.NETHER_STAR, "ultra", "§6§lUltra Key"),
        "rare_key" to KeySpec(Items.OMINOUS_TRIAL_KEY, "rare", "§5§lRare Key"),
        "pokemon_crate" to KeySpec(Items.TURTLE_EGG, "pokemon", "§a§lPokémon Key"),
    )

    /** Plain registry-item shop ids → their ResourceLocation string. */
    private val REGISTRY_ITEMS: Map<String, String> = mapOf(
        "master_ball" to "cobblemon:master_ball",
        "rare_candy" to "cobblemon:rare_candy",
        "ability_patch" to "cobblemon:ability_patch",
        "totem_of_undying" to "minecraft:totem_of_undying",
    )

    private fun line(s: String): Component =
        Component.literal(s).setStyle(Style.EMPTY.withItalic(false))

    private fun registryItem(rlStr: String): ItemStack {
        val rl = ResourceLocation.tryParse(rlStr) ?: return ItemStack.EMPTY
        val item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null) ?: return ItemStack.EMPTY
        return ItemStack(item)
    }

    private fun crateKey(spec: KeySpec): ItemStack {
        val stack = ItemStack(spec.item)
        stack.set(DataComponents.CUSTOM_NAME, line(spec.name))
        val tag = CompoundTag()
        tag.putString(GACHA_KEY_TAG, spec.tier)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        return stack
    }

    /**
     * The icon shown in the BP shop GUI for [entry]. Never empty — falls back to a labelled paper so
     * a mis-typed config id still renders a clickable (if inert) slot rather than a hole in the grid.
     */
    fun displayStack(entry: BpItemEntry): ItemStack {
        val stack = when {
            entry.isVoucher && entry.voucherType != null -> Vouchers.create(entry.voucherType)
            entry.id in KEY_SPECS -> crateKey(KEY_SPECS.getValue(entry.id))
            entry.id == "shiny_egg" -> ItemStack(Items.TURTLE_EGG).also {
                it.set(DataComponents.CUSTOM_NAME, line("§d§lShiny Egg"))
                it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            }
            entry.id in REGISTRY_ITEMS -> registryItem(REGISTRY_ITEMS.getValue(entry.id))
            else -> ItemStack(Items.PAPER).also { it.set(DataComponents.CUSTOM_NAME, line("§f${entry.displayName}")) }
        }
        val display = if (stack.isEmpty) ItemStack(Items.PAPER) else stack
        display.set(DataComponents.CUSTOM_NAME, line("§e${entry.displayName}"))
        display.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Cost: §b${entry.cost} BP"),
            line("§8Left-click to buy."),
        )))
        return display
    }

    /**
     * Actually deliver [entry] to [player]. Returns true on success; false means nothing was given
     * and the caller must refund the BP. Overflow items are dropped at the player's feet.
     */
    fun grant(player: ServerPlayer, entry: BpItemEntry): Boolean {
        // Command-dispatched: shiny egg reuses the gacha mod's egg logic.
        if (entry.id == "shiny_egg") return dispatchShinyEgg(player)

        val stack: ItemStack = when {
            entry.isVoucher && entry.voucherType != null -> Vouchers.create(entry.voucherType)
            entry.id in KEY_SPECS -> crateKey(KEY_SPECS.getValue(entry.id))
            entry.id in REGISTRY_ITEMS -> registryItem(REGISTRY_ITEMS.getValue(entry.id))
            else -> ItemStack.EMPTY
        }
        if (stack.isEmpty) {
            CobblemonMarket.logger.warn("BP shop: no delivery mapping for item id '{}'", entry.id)
            return false
        }
        if (!player.inventory.add(stack)) player.drop(stack, false)
        return true
    }

    /**
     * Grant a guaranteed-shiny egg by dispatching the gacha `giveegg … shiny` command (permission
     * elevated). Returns true if the command dispatched without throwing; the gacha command reports
     * its own success/failure to the player. Gacha is always present on this server.
     */
    private fun dispatchShinyEgg(player: ServerPlayer): Boolean {
        return try {
            val server = player.server
            val source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
            server.commands.performPrefixedCommand(source, "gacha giveegg ${player.name.string} rare shiny")
            true
        } catch (t: Throwable) {
            CobblemonMarket.logger.error("BP shop: failed to grant shiny egg to ${player.name.string}", t)
            false
        }
    }
}
