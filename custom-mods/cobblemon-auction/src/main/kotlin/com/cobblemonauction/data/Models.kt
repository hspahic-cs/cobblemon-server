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
