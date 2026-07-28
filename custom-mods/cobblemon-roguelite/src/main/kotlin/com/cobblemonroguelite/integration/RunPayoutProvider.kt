package com.cobblemonroguelite.integration

import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/integration")

/**
 * What a finished run is worth, in the two units the plan names (§2.2: "payout in currency/BP").
 *
 * The amounts arrive here **already decided** by the module's reward table (§2.12). A provider is
 * transport, not policy: a server plugging in its own economy gets to choose where the payment
 * lands, not what a run pays. Keeping the split on that line is what lets a published build with
 * no provider registered still run the identical reward table and still show the player a total —
 * it just has nowhere to bank it.
 *
 * Both fields are independent because our two currencies are (server money and ranked BP live in
 * different systems entirely). A provider that can only pay one of them should credit that one and
 * ignore the other rather than reject the payout; dropping half a payout is recoverable by an op,
 * dropping all of it because BP was unsupported is not obvious to anyone.
 */
data class RunPayout(
    val currency: Int = 0,
    val battlePoints: Int = 0,
) {
    /** True when there is nothing to bank — callers may skip the provider round-trip entirely. */
    fun isEmpty(): Boolean = currency <= 0 && battlePoints <= 0
}

/**
 * The single channel by which value leaves a run.
 *
 * §1.1 seals a run on every axis but this one: party, catches, inventory, and gimmicks all die
 * with the run, and the payout is the one deliberate hole. That makes this interface the whole
 * audit surface for "what can a run give a player" — if something else in this module ever hands
 * a player a real reward without going through here, the isolation contract has been broken and
 * this file will not show it.
 *
 * Implemented in `cobblemon-bridge` against our economy. Never implemented here: the module has no
 * economy of its own and must not grow one, because a mod that mints currency by itself is exactly
 * the faucet §2.2 refuses.
 */
fun interface RunPayoutProvider {

    /**
     * Bank [payout] for [player]. Returns true if anything was actually credited.
     *
     * Called at run end from the server thread. The player may be **offline** by then — a run can
     * end on a disconnect-timeout path — so implementations must resolve by UUID and must not
     * assume a live `ServerPlayer` exists.
     */
    fun pay(server: MinecraftServer, player: UUID, payout: RunPayout): Boolean
}

/**
 * The registered payout destination, defaulting to nowhere.
 *
 * ### Why the default is a no-op rather than an error
 *
 * The module has to be independently shippable (§1.2, §2.9), which means the *only* configuration
 * a standalone install has is the one it ships with. If an unregistered payout threw or refused to
 * end the run, the published mod would be broken out of the box for everyone who is not us. So the
 * default swallows the payout and says so once in the log; runs still start, still finish, and
 * still report their earnings — they simply do not bank anywhere.
 *
 * ### Threading
 *
 * [register] is expected exactly once, during another mod's setup, before any run exists. Reads
 * happen at run end on the server thread. The field is `@Volatile` anyway because "before any run
 * exists" is a convention we cannot enforce across mods, and a stale read would silently pay into
 * the no-op after a provider had been installed.
 *
 * ### Registering twice
 *
 * Last writer wins and the replacement is logged at WARN. We do not reject the second call: an op
 * reloading a server-side integration is a legitimate reason to re-register, and a hard failure
 * there would take down the mode over a swap that is harmless. The WARN exists so that two mods
 * fighting over the seam shows up in the log rather than as mysteriously missing payouts.
 */
object RunPayouts {

    /** Drops everything handed to it. See the class docs for why this is the shipped default. */
    val NONE = RunPayoutProvider { _, player, payout ->
        log.info(
            "roguelite: no payout provider registered — dropping {} currency / {} BP for {}",
            payout.currency, payout.battlePoints, player,
        )
        false
    }

    @Volatile
    private var provider: RunPayoutProvider = NONE

    /** The active provider. Exposed mainly so callers can tell "will pay" from "will drop". */
    val current: RunPayoutProvider get() = provider

    fun isRegistered(): Boolean = provider !== NONE

    fun register(provider: RunPayoutProvider) {
        val previous = this.provider
        if (previous !== NONE) {
            log.warn(
                "roguelite: payout provider {} replaced by {} — only the latter will be paid",
                previous.javaClass.name, provider.javaClass.name,
            )
        }
        this.provider = provider
    }

    /** Restore the shipped default. For tests and for unloading a server-side integration. */
    fun reset() {
        provider = NONE
    }

    /**
     * Pay [payout] through the active provider. Returns true if it was credited.
     *
     * A provider throwing is contained here rather than propagated. The caller is the run-end path,
     * which also tears the run down; letting a third-party economy exception escape would leave the
     * run half-ended — party already discarded, state still in [com.cobblemonroguelite.run.RunStore]
     * — which is worse for the player than an unpaid run.
     */
    fun pay(server: MinecraftServer, player: UUID, payout: RunPayout): Boolean {
        if (payout.isEmpty()) return false
        return runCatching { provider.pay(server, player, payout) }
            .onFailure { log.error("roguelite: payout provider failed for {} — run pays nothing", player, it) }
            .getOrDefault(false)
    }
}
