package com.cobblemonauction.service

import com.cobblemonauction.CobblemonAuction
import com.cobblemonauction.data.ItemStacks
import com.cobblemonauction.data.MailEntry
import com.cobblemonauction.data.Request
import com.cobblemonauction.data.RequestReceipt
import com.cobblemonauction.economy.EconomyBridge
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * All buy-order (request) business logic — the inversion of [AuctionService]. Everything runs on the
 * server thread (menu clicks, ticks), so the read-then-mutate sequences here need no locking; the
 * store saves-on-mutate; the economy fails closed.
 *
 * Creation is NOT two-phase-with-escrow like selling — there's no item to hold across screens, so
 * money is withdrawn only at the final anvil confirm and abandoning the flow mid-way costs nothing.
 * The escrow is money that has already left the requester's balance ([Request.price]); on fulfill it
 * is paid to the seller, on cancel/expiry it is refunded to the requester. Every terminal edge is
 * gated on a SUCCESSFUL [EconomyBridge.deposit] so there is never a removed-but-unpaid state.
 */
object RequestService {

    // ---- Create ---------------------------------------------------------------------------

    sealed interface CreateResult {
        data class Success(val request: Request) : CreateResult
        data object NotRequestable : CreateResult
        /** [max] is the item's own stack size — the per-request ceiling (see [maxStackFor]). */
        data class CountOutOfRange(val max: Int) : CreateResult
        data class PriceOutOfRange(val min: Int, val max: Int) : CreateResult
        data class TooMany(val max: Int) : CreateResult
        data class InsufficientBalance(val need: Int, val have: Int) : CreateResult
        data object EconomyUnavailable : CreateResult
        data object Error : CreateResult
    }

    fun createRequest(player: ServerPlayer, itemId: String, count: Int, price: Int): CreateResult {
        val cfg = CobblemonAuction.config
        // Any REAL, non-blocklisted item may be requested (the curated list is suggestions only, not
        // a gate). Reject ids that don't resolve to a registered item or that are on the blocklist.
        if (resolveItem(itemId) == null || cfg.isBlocked(itemId)) return CreateResult.NotRequestable
        // Held-hand fulfillment takes the whole count from a SINGLE main-hand stack, so a request for
        // more than the item's stack size would be structurally unfulfillable — cap at maxStackSize.
        val maxStack = maxStackFor(itemId)
        if (count < 1 || count > maxStack) return CreateResult.CountOutOfRange(maxStack)
        if (price < cfg.minPrice || price > cfg.maxPrice) return CreateResult.PriceOutOfRange(cfg.minPrice, cfg.maxPrice)
        val max = cfg.maxRequestsPerPlayer
        if (CobblemonAuction.requestStore.countByRequester(player.uuid) >= max) return CreateResult.TooMany(max)

        // Escrow the money up front (fail closed). withdraw() returns false for BOTH "can't afford"
        // and "economy unavailable"; disambiguate via isAvailable().
        if (!EconomyBridge.withdraw(player.uuid, price)) {
            return if (!EconomyBridge.isAvailable()) CreateResult.EconomyUnavailable
                   else CreateResult.InsufficientBalance(price, EconomyBridge.getBalance(player.uuid))
        }

        return try {
            val now = System.currentTimeMillis()
            val request = Request(
                id = UUID.randomUUID().toString(),
                requesterUuid = player.uuid.toString(),
                requesterName = player.gameProfile.name,
                itemId = itemId,
                count = count,
                price = price,
                createdAt = now,
                expiresAt = now + cfg.requestTtlMillis(),
            )
            CobblemonAuction.requestStore.add(request)
            CreateResult.Success(request)
        } catch (e: Throwable) {
            CobblemonAuction.logger.error("Failed to create request for ${player.gameProfile.name}", e)
            EconomyBridge.deposit(player.uuid, price)   // refund the escrow; the request didn't happen
            CreateResult.Error
        }
    }

    // ---- Fulfill (held-hand, two-click arm/confirm) ---------------------------------------

    sealed interface FulfillResult {
        data class Success(val request: Request) : FulfillResult
        data object Gone : FulfillResult
        data object OwnRequest : FulfillResult
        /** The seller's hand didn't match on the confirm click: they need [count]× [itemId], have [have]. */
        data class NeedItem(val itemId: String, val count: Int, val have: Int) : FulfillResult
        data object EconomyUnavailable : FulfillResult
        data object Error : FulfillResult
    }

    /**
     * Settle a request against the stack in the seller's main hand. **Ordering is load-bearing**:
     * every fallible step runs before the irreversible ones, so the seller's item leaves their hand
     * only after the snapshot encoded, the request was claimed, and the seller was paid.
     */
    fun fulfill(seller: ServerPlayer, requestId: String): FulfillResult {
        val req = CobblemonAuction.requestStore.get(requestId) ?: return FulfillResult.Gone
        if (req.requesterUuid == seller.uuid.toString()) return FulfillResult.OwnRequest

        val hand = seller.mainHandItem
        // Re-validated on the CONFIRM click — the hand may have changed since arming.
        if (itemId(hand) != req.itemId || hand.count < req.count) {
            return FulfillResult.NeedItem(req.itemId, req.count, if (itemId(hand) == req.itemId) hand.count else 0)
        }

        // (1) Encode a SNAPSHOT first — fallible; nothing taken/paid yet.
        val snbt = try {
            ItemStacks.encode(hand.copyWithCount(req.count), seller.level().registryAccess())
        } catch (e: Throwable) {
            CobblemonAuction.logger.error("Failed to encode fulfilling stack for request ${req.id}", e)
            return FulfillResult.Error
        }
        // (2) Fail closed before taking the item.
        if (!EconomyBridge.isAvailable()) return FulfillResult.EconomyUnavailable
        // (3) Claim it (race guard, buy() parity).
        val removed = CobblemonAuction.requestStore.remove(requestId) ?: return FulfillResult.Gone
        // (4) Pay the seller from escrow — deposit reports success; on failure re-list, seller keeps item.
        if (!EconomyBridge.deposit(seller.uuid, removed.price)) {
            CobblemonAuction.requestStore.add(removed)
            CobblemonAuction.logger.error(
                "FULFILL unwound: seller payout failed for request ${removed.id} — re-listed, item untouched")
            return FulfillResult.EconomyUnavailable
        }
        // (5) ONLY NOW take from the hand and deliver the pre-encoded snapshot to the requester.
        hand.shrink(removed.count)
        CobblemonAuction.mailboxStore.add(
            UUID.fromString(removed.requesterUuid),
            MailEntry(
                id = UUID.randomUUID().toString(),
                itemId = removed.itemId,
                count = removed.count,
                item = snbt,
                addedAt = System.currentTimeMillis(),
                note = "Request filled by ${seller.gameProfile.name}",
            ),
        )
        CobblemonAuction.logger.info(
            "REQUEST FILLED ${removed.count}x ${removed.itemId} for \$${removed.price}: " +
                "${seller.gameProfile.name} -> ${removed.requesterName}"
        )
        notifyRequester(seller, removed)
        return FulfillResult.Success(removed)
    }

    /** Tell the requester their order was filled: immediately if they're online, otherwise queue a
     *  receipt summarized on their next login. The item is already in their mailbox either way. */
    private fun notifyRequester(seller: ServerPlayer, request: Request) {
        val requesterUuid = UUID.fromString(request.requesterUuid)
        val online = seller.server.playerList.getPlayer(requesterUuid)
        val name = com.cobblemonauction.gui.Gui.prettyItemName(request.itemId)
        if (online != null) {
            online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§a§l[AH] Request filled! §r§aYour ${request.count}× $name arrived from ${seller.gameProfile.name} " +
                    "— open the Auctioneer and click §bMailbox§a to collect it."))
        } else {
            CobblemonAuction.requestReceiptStore.add(requesterUuid, RequestReceipt(
                id = UUID.randomUUID().toString(),
                itemId = request.itemId,
                count = request.count,
                price = request.price,
                sellerName = seller.gameProfile.name,
                filledAt = System.currentTimeMillis(),
            ))
        }
    }

    // ---- Cancel a live request ------------------------------------------------------------

    sealed interface CancelResult {
        data class Success(val request: Request) : CancelResult
        data object Gone : CancelResult
        data object NotOwner : CancelResult
        data object EconomyUnavailable : CancelResult
    }

    /** Owner-only. Claim, then refund; if the refund reports failure, re-list so the escrow is never
     *  destroyed with the request already gone (mirror of [fulfill]'s recovery). */
    fun cancel(player: ServerPlayer, requestId: String): CancelResult {
        val req = CobblemonAuction.requestStore.get(requestId) ?: return CancelResult.Gone
        if (req.requesterUuid != player.uuid.toString()) return CancelResult.NotOwner
        val removed = CobblemonAuction.requestStore.remove(requestId) ?: return CancelResult.Gone
        if (!EconomyBridge.deposit(player.uuid, removed.price)) {
            CobblemonAuction.requestStore.add(removed)
            return CancelResult.EconomyUnavailable
        }
        return CancelResult.Success(removed)
    }

    // ---- Expiry sweep ---------------------------------------------------------------------

    /** The moment a request effectively leaves the market (a minute before its nominal expiry),
     *  reusing the listing sweep's lead so a request vanishes during its final minute rather than
     *  showing a zeroed-out countdown. */
    fun effectiveExpiry(request: Request): Long = request.expiresAt - AuctionService.REMOVAL_LEAD_MS

    /**
     * Refund every effectively-expired request's escrow to its requester. If a deposit returns false
     * (economy down), the request is re-added and left for the next sweep rather than dropping the
     * escrow. Successful refunds are SILENT — the money simply reappears in the balance. Returns how
     * many were refunded.
     */
    fun sweepExpired(now: Long): Int {
        // now + lead ≡ effectiveExpiry <= now, i.e. sweep a minute early (matches listings).
        val expired = CobblemonAuction.requestStore.expired(now + AuctionService.REMOVAL_LEAD_MS)
        var refunded = 0
        for (request in expired) {
            val removed = CobblemonAuction.requestStore.remove(request.id) ?: continue
            if (!EconomyBridge.deposit(UUID.fromString(removed.requesterUuid), removed.price)) {
                CobblemonAuction.requestStore.add(removed)   // couldn't refund — leave it live, retry next sweep
                continue
            }
            refunded++
        }
        return refunded
    }

    // ---- Search ---------------------------------------------------------------------------

    /**
     * Free-text search over the full item registry for the create-request picker. Matches the trimmed
     * [query] (case-insensitive **contains**) against either an item's id (`namespace:path`) or its
     * display name, excluding blocklisted ids and `minecraft:air`. Results are ranked so the most
     * obvious matches surface first — exact name, then name/id starts-with, then contains — ties
     * broken alphabetically by display name, and capped at [limit].
     *
     * Iterating the ~1500-item registry per call is fine: it's one pass per user action, on the
     * server thread. Returns the matching item ids.
     */
    fun searchItems(query: String, limit: Int): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty() || limit <= 0) return emptyList()
        val cfg = CobblemonAuction.config

        data class Match(val id: String, val name: String, val rank: Int)

        val matches = ArrayList<Match>()
        for (item in BuiltInRegistries.ITEM) {
            val id = BuiltInRegistries.ITEM.getKey(item).toString()
            if (id == "minecraft:air" || cfg.isBlocked(id)) continue
            val name = ItemStack(item).hoverName.string
            val idLc = id.lowercase()
            val nameLc = name.lowercase()
            if (q !in idLc && q !in nameLc) continue
            val rank = when {
                nameLc == q -> 0
                nameLc.startsWith(q) || idLc.startsWith(q) -> 1
                else -> 2
            }
            matches.add(Match(id, name, rank))
        }
        matches.sortWith(compareBy({ it.rank }, { it.name.lowercase() }, { it.id }))
        return matches.take(limit).map { it.id }
    }

    // ---- Helpers --------------------------------------------------------------------------

    /** Resolve an item id to its [Item], or null if it isn't a registered item. */
    fun resolveItem(itemId: String): Item? =
        ResourceLocation.tryParse(itemId)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }

    /** The per-request count ceiling for [itemId]: the item's own max stack size (1 if unresolved). */
    fun maxStackFor(itemId: String): Int = resolveItem(itemId)?.let { ItemStack(it).maxStackSize } ?: 1

    private fun itemId(stack: ItemStack): String =
        BuiltInRegistries.ITEM.getKey(stack.item).toString()
}
