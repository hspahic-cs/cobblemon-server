package com.cobblemonauction

import com.cobblemonauction.commands.AuctionAdminCommands
import com.cobblemonauction.config.AuctionConfig
import com.cobblemonauction.data.AuctionStore
import com.cobblemonauction.data.MailboxStore
import com.cobblemonauction.data.RequestReceiptStore
import com.cobblemonauction.data.RequestStore
import com.cobblemonauction.data.SalesReceiptStore
import net.minecraft.network.chat.Component
import com.cobblemonauction.gui.AuctionNpcHook
import com.cobblemonauction.service.AuctionService
import com.cobblemonauction.service.RequestService
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Persistent player-to-player auction house. Server-only, chest-GUI driven, no custom packets.
 * Opened by right-clicking an entity tagged `cobblemon_auction.auctioneer` (see [AuctionNpcHook]).
 */
@Mod(value = CobblemonAuction.MOD_ID, dist = [Dist.DEDICATED_SERVER])
class CobblemonAuction(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Auction House initializing...")
        val configDir = FMLPaths.CONFIGDIR.get()
        config = AuctionConfig.load(configDir)
        auctionStore = AuctionStore(configDir).also { it.load() }
        requestStore = RequestStore(configDir).also { it.load() }
        mailboxStore = MailboxStore(configDir).also { it.load() }
        salesReceiptStore = SalesReceiptStore(configDir).also { it.load() }
        requestReceiptStore = RequestReceiptStore(configDir).also { it.load() }

        NeoForge.EVENT_BUS.register(AuctionNpcHook)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerTickPost)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)

        logger.info("Cobblemon Auction House initialized (${auctionStore.all().size} active listings, " +
            "${requestStore.all().size} active requests).")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        AuctionAdminCommands.register(event.dispatcher)
    }

    private var tickCounter = 0

    private fun onServerTickPost(event: ServerTickEvent.Post) {
        if (++tickCounter % SWEEP_INTERVAL_TICKS != 0) return
        val now = System.currentTimeMillis()
        val swept = AuctionService.sweepExpired(now)
        if (swept > 0) logger.info("Expired $swept listing(s) back to sellers' mailboxes")
        val refunded = RequestService.sweepExpired(now)
        if (refunded > 0) logger.info("Expired $refunded request(s), escrow refunded to requesters")
    }

    /** If a player logs out mid-sell (item escrowed, price not yet set), park it in their mailbox
     *  rather than risk losing it — an inventory add on a departing player may not persist. */
    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val sp = event.entity as? ServerPlayer ?: return
        AuctionService.stashPendingToMailbox(sp)
    }

    /** On login, deliver a summary of any sales and filled requests that completed while offline. */
    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val sp = event.entity as? ServerPlayer ?: return
        summarizeSales(sp)
        summarizeFilledRequests(sp)
    }

    private fun summarizeSales(sp: ServerPlayer) {
        val receipts = salesReceiptStore.pending(sp.uuid)
        if (receipts.isEmpty()) return
        val total = receipts.sumOf { it.price }
        sp.sendSystemMessage(Component.literal(
            "§a§l[AH] While you were away, ${receipts.size} listing(s) sold for §e\$$total§a total:"))
        receipts.take(10).forEach { r ->
            sp.sendSystemMessage(Component.literal(
                "§7  • ${r.count}× ${com.cobblemonauction.gui.Gui.prettyItemName(r.itemId)} → §e\$${r.price} §7(to ${r.buyerName})"))
        }
        if (receipts.size > 10) sp.sendSystemMessage(Component.literal("§7  …and ${receipts.size - 10} more."))
        sp.sendSystemMessage(Component.literal("§7The money is already in your balance; buyers collected the items."))
        salesReceiptStore.clear(sp.uuid)
    }

    private fun summarizeFilledRequests(sp: ServerPlayer) {
        val receipts = requestReceiptStore.pending(sp.uuid)
        if (receipts.isEmpty()) return
        sp.sendSystemMessage(Component.literal(
            "§a§l[AH] While you were away, ${receipts.size} of your request(s) were filled:"))
        receipts.take(10).forEach { r ->
            sp.sendSystemMessage(Component.literal(
                "§7  • ${r.count}× ${com.cobblemonauction.gui.Gui.prettyItemName(r.itemId)} §7(from ${r.sellerName})"))
        }
        if (receipts.size > 10) sp.sendSystemMessage(Component.literal("§7  …and ${receipts.size - 10} more."))
        sp.sendSystemMessage(Component.literal("§7The items are waiting in your §bMailbox§7 — open the Auctioneer to collect them."))
        requestReceiptStore.clear(sp.uuid)
    }

    companion object {
        const val MOD_ID = "cobblemon_auction"
        private const val SWEEP_INTERVAL_TICKS = 1200   // ~1 minute at 20 TPS
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var config: AuctionConfig
        lateinit var auctionStore: AuctionStore
        lateinit var requestStore: RequestStore
        lateinit var mailboxStore: MailboxStore
        lateinit var salesReceiptStore: SalesReceiptStore
        lateinit var requestReceiptStore: RequestReceiptStore
    }
}
