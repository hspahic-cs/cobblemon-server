package com.cobblemonbridge.trade

import net.minecraft.world.SimpleContainer
import java.util.UUID

/**
 * An active trade between two players. Lives in memory only — trades don't outlive a logout
 * (disconnects refund + cancel via [TradeManager.handleDisconnect]).
 *
 * The [container] is a single shared SimpleContainer; both players open a `ChestMenu` backed
 * by it so vanilla container-sync pushes updates to both client GUIs in real time. The slot
 * layout (which slots are P1's items / P2's items / display tiles / buttons) is owned by
 * [TradeMenu]; this class doesn't care.
 *
 * [offer1] and [offer2] hold the *non-item* parts of each side's offer (pokemon list, money
 * intent, confirmed flag) — see [TradeOffer] for why those don't live in the container.
 */
class TradeSession(
    val sessionId: UUID,
    val p1Uuid: UUID,
    val p2Uuid: UUID,
    val p1Name: String,
    val p2Name: String,
    val container: SimpleContainer,
    val offer1: TradeOffer = TradeOffer(),
    val offer2: TradeOffer = TradeOffer(),
    val createdAtMs: Long = System.currentTimeMillis(),
) {
    /** Returns the [TradeOffer] belonging to the player with this UUID, or null if the UUID
     *  isn't a participant in this session. */
    fun offerOf(uuid: UUID): TradeOffer? = when (uuid) {
        p1Uuid -> offer1
        p2Uuid -> offer2
        else -> null
    }

    /** Returns the OTHER player's UUID, or null if the input isn't a participant. */
    fun partnerOf(uuid: UUID): UUID? = when (uuid) {
        p1Uuid -> p2Uuid
        p2Uuid -> p1Uuid
        else -> null
    }

    fun bothConfirmed(): Boolean = offer1.confirmed && offer2.confirmed

    /** Any change to either offer must un-confirm BOTH sides so the trade can't accidentally
     *  commit with stale terms. Both players need to re-click confirm. */
    fun unconfirmBoth() {
        offer1.confirmed = false
        offer2.confirmed = false
    }
}
