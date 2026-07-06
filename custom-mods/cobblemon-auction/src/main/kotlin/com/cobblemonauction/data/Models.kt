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
