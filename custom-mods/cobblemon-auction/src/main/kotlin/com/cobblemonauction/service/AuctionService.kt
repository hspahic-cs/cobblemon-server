package com.cobblemonauction.service

import com.cobblemonauction.CobblemonAuction
import com.cobblemonauction.data.ItemStacks
import com.cobblemonauction.data.Listing
import com.cobblemonauction.data.MailEntry
import com.cobblemonauction.economy.EconomyBridge
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * All auction business logic. Everything runs on the server thread (menu clicks, ticks), so the
 * read-then-mutate sequences here need no locking; stores save-on-mutate.
 *
 * Selling is two-phase because the price is typed into an anvil, and the anvil screen lets the
 * player move their own inventory around — so we can't trust "what's in your hand" at confirm
 * time. [beginSell] escrows the held stack into [pendingSells] immediately; [confirmSell] turns
 * it into a listing; [cancelSell] hands it back. The escrow is in-memory: a mid-price-entry crash
 * loses the stack, but a clean logout returns it to the mailbox (see [CobblemonAuction]).
 */
object AuctionService {

    /** Player uuid -> the stack pulled from their hand while they type a price. */
    private val pendingSells = ConcurrentHashMap<UUID, ItemStack>()

    // ---- Sell (phase 1: escrow) ------------------------------------------------------------

    sealed interface BeginResult {
        data class Ready(val stack: ItemStack) : BeginResult
        data object NoItemInHand : BeginResult
        data class Blocked(val itemId: String) : BeginResult
        data class TooManyListings(val max: Int) : BeginResult
        data object AlreadySelling : BeginResult
    }

    fun beginSell(player: ServerPlayer): BeginResult {
        if (pendingSells.containsKey(player.uuid)) return BeginResult.AlreadySelling
        val stack = player.mainHandItem
        if (stack.isEmpty) return BeginResult.NoItemInHand
        val itemId = itemId(stack)
        if (CobblemonAuction.config.isBlocked(itemId)) return BeginResult.Blocked(itemId)
        val max = CobblemonAuction.config.maxListingsPerPlayer
        if (CobblemonAuction.auctionStore.countBySeller(player.uuid) >= max) return BeginResult.TooManyListings(max)

        val escrow = stack.copy()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        pendingSells[player.uuid] = escrow
        return BeginResult.Ready(escrow)
    }

    fun hasPendingSell(player: ServerPlayer): Boolean = pendingSells.containsKey(player.uuid)

    fun peekPendingSell(player: ServerPlayer): ItemStack? = pendingSells[player.uuid]

    // ---- Sell (phase 2: confirm / cancel) --------------------------------------------------

    sealed interface ListResult {
        data class Success(val listing: Listing) : ListResult
        data class PriceOutOfRange(val min: Int, val max: Int) : ListResult
        data class FeeUnaffordable(val fee: Int, val have: Int) : ListResult
        data object EconomyUnavailable : ListResult
        data object NothingEscrowed : ListResult
        data object Error : ListResult
    }

    fun confirmSell(player: ServerPlayer, price: Int): ListResult {
        val cfg = CobblemonAuction.config
        // Once we take the escrow out, EVERY return path must either list the item or hand it back.
        val escrow = pendingSells.remove(player.uuid) ?: return ListResult.NothingEscrowed
        if (price < cfg.minPrice || price > cfg.maxPrice) {
            returnToPlayer(player, escrow)
            return ListResult.PriceOutOfRange(cfg.minPrice, cfg.maxPrice)
        }

        // Charge the listing fee up front — a deposit, refunded to the seller on sale (see buy())
        // and kept (a currency sink) on expiry/cancel. Fail closed if economy is down or the seller
        // can't cover it, and give the item back.
        val fee = cfg.listingFee(price)
        if (fee > 0 && !EconomyBridge.withdraw(player.uuid, fee)) {
            returnToPlayer(player, escrow)
            return if (!EconomyBridge.isAvailable()) ListResult.EconomyUnavailable
                   else ListResult.FeeUnaffordable(fee, EconomyBridge.getBalance(player.uuid))
        }

        return try {
            val snbt = ItemStacks.encode(escrow, player.level().registryAccess())
            val now = System.currentTimeMillis()
            val listing = Listing(
                id = UUID.randomUUID().toString(),
                sellerUuid = player.uuid.toString(),
                sellerName = player.gameProfile.name,
                itemId = itemId(escrow),
                count = escrow.count,
                item = snbt,
                price = price,
                fee = fee,
                createdAt = now,
                expiresAt = now + cfg.ttlMillis(),
            )
            CobblemonAuction.auctionStore.add(listing)
            ListResult.Success(listing)
        } catch (e: Throwable) {
            CobblemonAuction.logger.error("Failed to create listing for ${player.gameProfile.name}", e)
            if (fee > 0) EconomyBridge.deposit(player.uuid, fee)   // undo the fee; the listing didn't happen
            returnToPlayer(player, escrow)   // don't eat the item on a serialization failure
            ListResult.Error
        }
    }

    /** Abort an in-progress sell (anvil closed without confirming) and hand the stack back. */
    fun cancelSell(player: ServerPlayer) {
        val escrow = pendingSells.remove(player.uuid) ?: return
        returnToPlayer(player, escrow)
    }

    /** Logout handler: park any escrowed sell straight into the mailbox (an inventory add on a
     *  departing player may not persist). No-op if the player has no pending sell. */
    fun stashPendingToMailbox(player: ServerPlayer) {
        val escrow = pendingSells.remove(player.uuid) ?: return
        try {
            CobblemonAuction.mailboxStore.add(
                player.uuid,
                MailEntry(
                    id = UUID.randomUUID().toString(),
                    itemId = itemId(escrow),
                    count = escrow.count,
                    item = ItemStacks.encode(escrow, player.level().registryAccess()),
                    addedAt = System.currentTimeMillis(),
                    note = "Unfinished listing returned",
                ),
            )
        } catch (e: Throwable) {
            CobblemonAuction.logger.error("Failed to stash pending sell for ${player.gameProfile.name}", e)
        }
    }

    // ---- Buy -------------------------------------------------------------------------------

    sealed interface BuyResult {
        data class Success(val listing: Listing) : BuyResult
        data object Gone : BuyResult
        data object OwnListing : BuyResult
        data class InsufficientBalance(val need: Int, val have: Int) : BuyResult
        data object EconomyUnavailable : BuyResult
    }

    fun buy(player: ServerPlayer, listingId: String): BuyResult {
        val listing = CobblemonAuction.auctionStore.get(listingId) ?: return BuyResult.Gone
        if (listing.sellerUuid == player.uuid.toString()) return BuyResult.OwnListing

        // Debit the buyer first (fail closed). withdraw() returns false for BOTH "can't afford"
        // and "economy unavailable"; disambiguate via isAvailable().
        if (!EconomyBridge.withdraw(player.uuid, listing.price)) {
            return if (!EconomyBridge.isAvailable()) BuyResult.EconomyUnavailable
                   else BuyResult.InsufficientBalance(listing.price, EconomyBridge.getBalance(player.uuid))
        }

        val removed = CobblemonAuction.auctionStore.remove(listingId)
        if (removed == null) {
            // Lost a race (shouldn't happen single-threaded) — refund and bail rather than
            // charge for a listing that's already gone.
            EconomyBridge.deposit(player.uuid, listing.price)
            return BuyResult.Gone
        }

        // Seller gets the sale price plus their listing fee back (the deposit is refunded on sale).
        EconomyBridge.deposit(UUID.fromString(listing.sellerUuid), listing.price + listing.fee)
        CobblemonAuction.mailboxStore.add(player.uuid, mailFrom(listing, "Purchased from ${listing.sellerName}"))
        CobblemonAuction.logger.info(
            "SALE ${listing.count}x ${listing.itemId} for \$${listing.price} (fee \$${listing.fee} refunded): " +
                "${listing.sellerName} -> ${player.gameProfile.name}"
        )
        return BuyResult.Success(listing)
    }

    // ---- Cancel a live listing -------------------------------------------------------------

    sealed interface CancelResult {
        data class Success(val listing: Listing) : CancelResult
        data object Gone : CancelResult
        data object NotOwner : CancelResult
    }

    fun cancel(player: ServerPlayer, listingId: String): CancelResult {
        val listing = CobblemonAuction.auctionStore.get(listingId) ?: return CancelResult.Gone
        if (listing.sellerUuid != player.uuid.toString()) return CancelResult.NotOwner
        val removed = CobblemonAuction.auctionStore.remove(listingId) ?: return CancelResult.Gone
        CobblemonAuction.mailboxStore.add(player.uuid, mailFrom(removed, "Listing cancelled"))
        return CancelResult.Success(removed)
    }

    // ---- Expiry sweep ----------------------------------------------------------------------

    /** Listings are removed this long BEFORE their nominal `expiresAt`. Both the browse/my-listings
     *  filters and the sweep use this lead, so a listing disappears during its final minute instead
     *  of ever showing a zeroed-out countdown. */
    const val REMOVAL_LEAD_MS = 60_000L

    /** The moment a listing effectively leaves the market (a minute before its nominal expiry). */
    fun effectiveExpiry(listing: Listing): Long = listing.expiresAt - REMOVAL_LEAD_MS

    /** Move every effectively-expired listing into its seller's mailbox. Returns how many were swept. */
    fun sweepExpired(now: Long): Int {
        // now + lead ≡ effectiveExpiry <= now, i.e. sweep a minute early.
        val expired = CobblemonAuction.auctionStore.expired(now + REMOVAL_LEAD_MS)
        for (listing in expired) {
            val removed = CobblemonAuction.auctionStore.remove(listing.id) ?: continue
            CobblemonAuction.mailboxStore.add(
                UUID.fromString(removed.sellerUuid),
                mailFrom(removed, "Listing expired"),
            )
        }
        return expired.size
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /** Add a stack back to the player's inventory; overflow goes to their mailbox. */
    fun returnToPlayer(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        val leftover = stack.copy()
        player.inventory.add(leftover)   // mutates leftover, shrinking it as it fits
        if (!leftover.isEmpty) {
            CobblemonAuction.mailboxStore.add(
                player.uuid,
                MailEntry(
                    id = UUID.randomUUID().toString(),
                    itemId = itemId(leftover),
                    count = leftover.count,
                    item = ItemStacks.encode(leftover, player.level().registryAccess()),
                    addedAt = System.currentTimeMillis(),
                    note = "Returned (inventory full)",
                ),
            )
        }
    }

    private fun mailFrom(listing: Listing, note: String) = MailEntry(
        id = UUID.randomUUID().toString(),
        itemId = listing.itemId,
        count = listing.count,
        item = listing.item,
        addedAt = System.currentTimeMillis(),
        note = note,
    )

    private fun itemId(stack: ItemStack): String =
        BuiltInRegistries.ITEM.getKey(stack.item).toString()
}
