package com.cobblemonroguelite.integration

import com.cobblemonroguelite.data.payout.PayoutGrant
import com.cobblemonroguelite.data.payout.RunOutcome
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/integration")

/**
 * What a finished run came to, handed to a host that wants to add something of its own.
 *
 * ### Why this is a summary and not an amount
 *
 * It used to be a single abstract `Int` that a host banked, on the theory that the payout was one
 * metered channel and a provider was the only way through it. §2.20 removed that theory: the payout
 * is **not currency**, it is items resolved from a datapack table
 * ([com.cobblemonroguelite.data.payout.PayoutTables]), and the module hands those over itself. An
 * abstract amount now describes nothing the module actually pays — it would be a number invented at
 * the seam, denominated in a unit no table names.
 *
 * So what crosses here is a *description of the run that just ended*: how it ended, how deep it got,
 * and what the module already granted. That is the minimum a host needs to price a bonus of its own
 * without this module knowing what the bonus is.
 *
 * [granted] is included rather than left out because a host that wants to top up "one dollar per
 * item the run paid" can, and because a provider that logs payouts for audit gets the whole picture
 * from one call rather than having to shadow the table.
 */
data class RunPayout(
    val outcome: RunOutcome,
    val wave: Int,
    val table: ResourceLocation?,
    val granted: List<PayoutGrant> = emptyList(),
)

/**
 * An **optional** extra on top of the payout the module already made.
 *
 * ### What this is no longer
 *
 * This is not the route by which value leaves a run any more, and treating it as one is the mistake
 * this paragraph exists to prevent. §1.1 still seals a run on every axis but the payout, and the
 * payout is still the audit surface — it just lives in
 * [com.cobblemonroguelite.data.payout.PayoutTable] now, where it is data an owner can read, rather
 * than in whatever a third-party provider decided to do with a number. Anything registered here is
 * *additional*: our currency, ranked BP, a title, an advancement.
 *
 * ### Why the module cannot just do this part too
 *
 * §2.2: this module has no economy and must never grow one, because a mod that mints currency by
 * itself is the faucet the whole design refuses. A host that wants a run to pay currency is making
 * that decision for its own server, with its own faucet accounting, and that is exactly the kind of
 * decision that belongs on the other side of an interface (§2.9).
 *
 * Note the asymmetry with [RunCharges] and that it is deliberate: money may enter through the charge
 * seam by default and may not leave through this one by default. A published build with nothing
 * registered plays a full run and pays a full payout; it just does not move any currency in either
 * direction, which is the correct behaviour for a mod that does not know what currency is.
 */
fun interface RunPayoutProvider {

    /**
     * Add whatever this host pays on top of [payout]. Returns true if anything was actually granted.
     *
     * Called at run end from the server thread, **after** the module's own grants have been handed
     * over — so a provider can assume the table payout happened and does not have to reproduce it.
     * The player may be **offline** by then (a run can end on a disconnect-timeout path), so
     * implementations must resolve by UUID and must not assume a live `ServerPlayer` exists.
     */
    fun pay(server: MinecraftServer, player: UUID, payout: RunPayout): Boolean
}

/**
 * The registered bonus payout hook, defaulting to nothing.
 *
 * ### Why the default is a no-op, and why that is now uncontroversial
 *
 * It used to be a compromise: with the payout modelled as an amount only a host could bank, an
 * unregistered provider meant a run that finished and paid nothing, and the no-op was chosen only
 * because throwing would have been worse. Under §2.20 there is nothing to compromise about. The
 * module resolves and grants the payout table by itself, so a published build with no provider
 * registered pays out *fully*; this seam adds a server's own extras or adds nothing.
 *
 * That also changes what the absence deserves in the log. Dropping a payout was worth an INFO line
 * every time it happened; declining to add a bonus that no one configured is the ordinary state of
 * every install that is not ours, and logging it per run would be noise around the one message that
 * matters (what the run actually paid, which the run-end path writes).
 *
 * ### Threading
 *
 * [register] is expected exactly once, during another mod's setup, before any run exists. Reads
 * happen at run end on the server thread. The field is `@Volatile` anyway because "before any run
 * exists" is a convention we cannot enforce across mods, and a stale read would silently skip a
 * provider that had been installed.
 *
 * ### Registering twice
 *
 * Last writer wins and the replacement is logged at WARN. We do not reject the second call: an op
 * reloading a server-side integration is a legitimate reason to re-register, and a hard failure
 * there would take down the mode over a swap that is harmless. The WARN exists so that two mods
 * fighting over the seam shows up in the log rather than as mysteriously missing bonuses.
 */
object RunPayouts {

    /** Adds nothing. See the class docs for why this is the shipped default and why it is quiet. */
    val NONE = RunPayoutProvider { _, player, payout ->
        log.debug(
            "roguelite: no bonus payout provider registered — run for {} pays its table only ({} grant(s))",
            player, payout.granted.size,
        )
        false
    }

    @Volatile
    private var provider: RunPayoutProvider = NONE

    /** The active provider. Exposed mainly so callers can tell "will add" from "will not". */
    val current: RunPayoutProvider get() = provider

    fun isRegistered(): Boolean = provider !== NONE

    fun register(provider: RunPayoutProvider) {
        val previous = this.provider
        if (previous !== NONE) {
            log.warn(
                "roguelite: bonus payout provider {} replaced by {} — only the latter will be paid",
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
     * Offer [payout] to the active provider. Returns true if it granted anything extra.
     *
     * A provider throwing is contained here rather than propagated. The caller is the run-end path,
     * which also tears the run down; letting a third-party economy exception escape would leave the
     * run half-ended — party already discarded, state still in [com.cobblemonroguelite.run.RunStore]
     * — which is worse for the player than a missing bonus. It is safe to swallow precisely because
     * the real payout has already been handed over by the time this is called.
     */
    fun pay(server: MinecraftServer, player: UUID, payout: RunPayout): Boolean =
        runCatching { provider.pay(server, player, payout) }
            .onFailure { log.error("roguelite: bonus payout provider failed for {} — run pays its table only", player, it) }
            .getOrDefault(false)
}
