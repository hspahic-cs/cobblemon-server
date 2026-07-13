package com.cobblemonauction.data

/**
 * One live sell order. `item` is the full stack as SNBT (see [ItemStacks]); `itemId` and
 * `count` are denormalized snapshots for cheap filtering, logging, and blocklist re-checks
 * without decoding. `price` is the whole-bundle total in server currency.
 */
data class Listing(
    val id: String,
    val sellerUuid: String,
    val sellerName: String,
    val itemId: String,
    val count: Int,
    val item: String,
    val price: Int,
    /** Listing fee charged at creation. Refunded to the seller on sale; kept on expiry/cancel.
     *  Stored per-listing so a refund is exact even if the config fee changes later. */
    val fee: Int,
    val createdAt: Long,
    val expiresAt: Long,
)

/** One stack waiting in a player's mailbox — a purchase, or a listing returned by
 *  cancellation/expiry. `note` is a short human label shown in the mailbox lore. */
data class MailEntry(
    val id: String,
    val itemId: String,
    val count: Int,
    val item: String,
    val addedAt: Long,
    val note: String,
)

/** A record that one of a seller's listings sold, kept only while the seller is OFFLINE so we can
 *  summarize it on their next login (online sellers are told immediately and never get a receipt).
 *  Proceeds are already in their balance; this is purely a notification. */
data class SaleReceipt(
    val id: String,
    val itemId: String,
    val count: Int,
    val price: Int,
    val buyerName: String,
    val soldAt: Long,
)

/**
 * One live buy order (the inversion of [Listing]). `itemId` + `count` describe the wanted TYPE —
 * there is no serialized stack, because no concrete item exists yet; the stack a fulfilling seller
 * provides is what gets encoded into the requester's mailbox at fulfill time. `price` is the whole
 * escrow, already withdrawn from the requester's balance at creation and held here — on fulfill it
 * is paid to the seller, on cancel/expiry it is refunded to the requester. No `fee` field: there is
 * no sink in v1 (full refund on cancel/expiry).
 */
data class Request(
    val id: String,
    val requesterUuid: String,
    val requesterName: String,
    val itemId: String,
    val count: Int,
    val price: Int,
    val createdAt: Long,
    val expiresAt: Long,
)

/** Fulfillment notification for an OFFLINE requester (mirror of [SaleReceipt]). The item is already
 *  in their mailbox; this explains why on their next login. The escrow left their balance at
 *  creation, so nothing money-side changes here — it's purely a notification. */
data class RequestReceipt(
    val id: String,
    val itemId: String,
    val count: Int,
    val price: Int,
    val sellerName: String,
    val filledAt: Long,
)
