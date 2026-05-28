package com.cobblemonbridge.gymtp

import com.cobblemonbridge.CobblemonBridge
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
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
import net.minecraft.world.level.Level
import net.neoforged.fml.loading.FMLPaths

/**
 * 6-row chest GUI listing the player's visible warp targets. Click → teleport + close.
 *
 * Layout (54-slot GENERIC_9x6):
 *   Row 0:  [_][_][_][_][header banner][_][_][_][close hint]
 *   Rows 1+ One slot per visible entry, in display order from [GymTpVisibility].
 *
 * Slot items are inert: every click except a valid warp slot is a no-op (no item movement,
 * no drag, no shift). The chest is read-only.
 */
object GymTpMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val HEADER_SLOT = 4         // center of row 0
    private const val FIRST_WARP_SLOT = 9     // row 1 col 0
    private const val MAX_WARPS = SLOTS - FIRST_WARP_SLOT

    fun open(player: ServerPlayer) {
        val container = SimpleContainer(SLOTS)
        val visible = visibleFor(player)
        populate(container, visible)
        val title = Component.literal("§0Gym Warps")
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player, visible) },
            title,
        )
        player.openMenu(provider)
    }

    private fun visibleFor(player: ServerPlayer): List<VisibleEntry> {
        val store = GymTpRegistry.store()
        val checker = AdvancementChecker { advId ->
            val rl = ResourceLocation.tryParse(advId) ?: return@AdvancementChecker false
            val holder: AdvancementHolder = player.server.advancements.get(rl) ?: return@AdvancementChecker false
            player.advancements.getOrStartProgress(holder).isDone
        }
        return GymTpVisibility.visibleFor(store.entries(), checker)
    }

    private fun populate(container: Container, visible: List<VisibleEntry>) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)
        container.setItem(HEADER_SLOT, headerStack(visible.size))
        if (visible.isEmpty()) {
            container.setItem(FIRST_WARP_SLOT, emptyStateStack())
        } else {
            for ((i, v) in visible.withIndex()) {
                if (i >= MAX_WARPS) break
                container.setItem(FIRST_WARP_SLOT + i, warpStack(v))
            }
        }
        container.setChanged()
    }

    private fun line(s: String): MutableComponent =
        Component.literal(s).setStyle(Style.EMPTY.withItalic(false))

    private fun headerStack(count: Int): ItemStack {
        val stack = ItemStack(Items.COMPASS)
        stack.set(DataComponents.CUSTOM_NAME, line("§6§lGym Warps"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7$count destination${if (count == 1) "" else "s"} available."),
            line("§7Click a banner to teleport."),
        )))
        return stack
    }

    private fun emptyStateStack(): ItemStack {
        val stack = ItemStack(Items.BARRIER)
        stack.set(DataComponents.CUSTOM_NAME, line("§cNo gyms configured"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Ask an op to run §f/gymtp set <id>§7."),
        )))
        return stack
    }

    private fun warpStack(v: VisibleEntry): ItemStack {
        val stack = ItemStack(Items.WHITE_BANNER)
        val nameColor = when (v.state) {
            VisibleEntry.State.BEATEN    -> "§a"
            VisibleEntry.State.AVAILABLE -> "§e"
            VisibleEntry.State.OTHER     -> "§b"
        }
        stack.set(DataComponents.CUSTOM_NAME, line("$nameColor§l${v.label}"))
        val statusLine = when (v.state) {
            VisibleEntry.State.BEATEN    -> "§a✓ Beaten — click to revisit"
            VisibleEntry.State.AVAILABLE -> "§e→ Available — click to challenge"
            VisibleEntry.State.OTHER     -> "§b• Unlocked — click to warp"
        }
        val pos = v.entry.position
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line(statusLine),
            line(""),
            line("§7${"%.0f".format(pos.x)}, ${"%.0f".format(pos.y)}, ${"%.0f".format(pos.z)}  §8(${pos.world})"),
        )))
        return stack
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
        private val visible: List<VisibleEntry>,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            // Block every movement-like interaction so display items can never escape.
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) {
                super.clicked(slotId, button, clickType, player)
                return
            }
            if (slotId == HEADER_SLOT) return
            val sp = player as? ServerPlayer ?: return
            if (sp.uuid != viewer.uuid) return
            val index = slotId - FIRST_WARP_SLOT
            if (index !in visible.indices) return
            val target = visible[index]
            sp.closeContainer()
            teleport(sp, target)
        }

        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY
    }

    private fun teleport(player: ServerPlayer, target: VisibleEntry) {
        val pos = target.entry.position
        val rl = ResourceLocation.tryParse(pos.world)
        if (rl == null) {
            player.sendSystemMessage(Component.literal("§c[Gym Warp] Invalid world id: ${pos.world}"))
            return
        }
        val key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl)
        val level: ServerLevel? = player.server.getLevel(key)
        if (level == null) {
            player.sendSystemMessage(Component.literal("§c[Gym Warp] Dimension not loaded: ${pos.world}"))
            return
        }
        player.teleportTo(level, pos.x, pos.y, pos.z, pos.yaw, pos.pitch)
        player.sendSystemMessage(Component.literal("§a[Gym Warp] → §f${target.label}"))
    }
}

/**
 * Singleton holder for the live [GymTpStore]. Loaded once at server start by
 * `CobblemonBridge`, exposed read-only here so menus / commands don't have to thread the
 * instance through every call site.
 */
object GymTpRegistry {
    @Volatile
    private var instance: GymTpStore? = null

    fun init() {
        val file = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve("gym_tps.json")
        val store = GymTpStore.load(file)
        GymTpVisibility.warnUnlockMissing(store.entries())
        instance = store
        CobblemonBridge.logger.info("gym-tp: loaded {} entries from {}", store.entries().size, file)
    }

    fun store(): GymTpStore = instance
        ?: error("GymTpRegistry not initialized — CobblemonBridge.init() should have called GymTpRegistry.init()")
}
