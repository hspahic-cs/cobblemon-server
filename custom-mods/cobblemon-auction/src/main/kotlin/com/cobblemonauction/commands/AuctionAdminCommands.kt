package com.cobblemonauction.commands

import com.cobblemonauction.permissions.StaffPermissions
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.npc.VillagerData
import net.minecraft.world.entity.npc.VillagerProfession
import net.minecraft.world.entity.npc.VillagerType
import net.minecraft.world.phys.AABB

/**
 * `/auctionadmin spawn|delete` — provisions the Auctioneer NPC. Mirrors cobblemon-market's
 * `/market admin spawn`: a persistent, invulnerable villager tagged so [
 * com.cobblemonauction.gui.AuctionNpcHook] opens the browser on right-click, plus the
 * cobblemon-bridge anchor tag so it's pinned in place while keeping its idle head movement.
 * Staff-only: op level 2, or the `moderator` group via [PERMISSION_NODE].
 */
object AuctionAdminCommands {

    /** NeoEssentials node that opens this to non-op staff. See [StaffPermissions]. */
    const val PERMISSION_NODE = "cobblemon.staff.auctionadmin"

    /** Interaction tag matched by AuctionNpcHook. */
    private const val AUCTIONEER_TAG = "cobblemon_auction.auctioneer"

    /** cobblemon-bridge generic-anchor tag: pins the mob to its spawn spot, AI still on. */
    private const val ANCHOR_TAG = "cobblemon_bridge.anchor.auction"

    private const val NPC_NAME = "Auctioneer"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("auctionadmin")
                .requires { StaffPermissions.check(it, PERMISSION_NODE, 2) }
                .then(Commands.literal("spawn").executes { spawnAuctioneer(it.source); 1 })
                .then(Commands.literal("delete").executes { deleteAuctioneers(it.source); 1 })
        )
    }

    private fun spawnAuctioneer(source: CommandSourceStack) {
        val level: ServerLevel = source.level
        val pos = source.position
        val killed = killNearbyTagged(level, pos.x, pos.y, pos.z, AUCTIONEER_TAG, radius = 4.0)

        val villager = EntityType.VILLAGER.create(level) ?: run {
            source.sendSystemMessage(Component.literal("§c[AH] Failed to create villager entity"))
            return
        }
        villager.moveTo(pos.x, pos.y, pos.z, source.rotation.y, 0f)
        villager.addTag(AUCTIONEER_TAG)
        villager.addTag(ANCHOR_TAG)
        villager.isInvulnerable = true
        villager.setPersistenceRequired()
        villager.isSilent = true
        // AI left on; the bridge anchor pins it each tick so it can't wander.
        villager.isNoAi = false
        villager.villagerData = VillagerData(VillagerType.PLAINS, VillagerProfession.CARTOGRAPHER, 5)
        villager.offers.clear()
        villager.customName = Component.literal(NPC_NAME)
            .setStyle(Style.EMPTY.withColor(0x55FFFF).withBold(true).withItalic(false))
        villager.isCustomNameVisible = true

        if (!level.addFreshEntity(villager)) {
            source.sendSystemMessage(Component.literal("§c[AH] Failed to add auctioneer to level"))
            return
        }
        val note = if (killed > 0) " §7(replaced $killed)" else ""
        source.sendSystemMessage(Component.literal("§a[AH] Spawned $NPC_NAME$note"))
    }

    private fun deleteAuctioneers(source: CommandSourceStack) {
        val killed = killNearbyTagged(source.level, source.position.x, source.position.y, source.position.z, AUCTIONEER_TAG, radius = 32.0)
        source.sendSystemMessage(Component.literal("§a[AH] Removed $killed auctioneer(s) within 32 blocks"))
    }

    private fun killNearbyTagged(level: ServerLevel, x: Double, y: Double, z: Double, tag: String, radius: Double): Int {
        val box = AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius)
        val matches = level.getEntitiesOfClass(Villager::class.java, box) { it.tags.contains(tag) }
        for (e in matches) e.discard()
        return matches.size
    }
}
