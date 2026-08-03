package com.cobblemonroguelite.integration

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/integration")

/**
 * What the module is asking for when it calls a charge provider.
 *
 * ### Why a quote is part of the seam and not a second interface
 *
 * A run start has to be able to tell the player what it will cost *before* it takes anything —
 * §2.16 mints the seed at start and a confirm prompt that cannot name the price is a prompt the
 * player cannot make a decision from. The price lives entirely on the host side (see
 * [RunChargeProvider]), so the module has no way to render it except by asking.
 *
 * @property quoteOnly ask what a run would cost and whether the player can afford it, and **take
 *   nothing**. A provider that debits on a quote turns every confirm prompt into a charge, and a
 *   provider that consumes a free allowance (§2.16) on a quote burns it on a player who then said
 *   no. Both are silent from here, which is why this is stated as a contract rather than a hint.
 */
data class RunChargeRequest(
    val quoteOnly: Boolean = false,
)

/**
 * Whether a run may start, and what to tell the player.
 *
 * Messages are [Component]s rather than strings because only the provider knows what the price is
 * denominated in and only the provider can translate it — a message assembled here would either be
 * in English or would need a translation key this module does not ship.
 */
sealed interface RunChargeResult {

    /** True when the run may proceed. */
    val allowed: Boolean get() = this is Paid

    /**
     * The fee was taken, or there was nothing to take.
     *
     * [detail] is a line for the player — "charged $5,000", "used your free daily run" — or null
     * when there is nothing worth saying. A waived run is deliberately *not* a separate variant: a
     * free allowance is a way of paying, and splitting it out would put a condition in every caller
     * that only ever wanted to know whether the run may start.
     *
     * Under [RunChargeRequest.quoteOnly] this means "affordable", and nothing has been taken.
     */
    data class Paid(val detail: Component? = null) : RunChargeResult

    /**
     * The run may not start. [reason] is shown to the player and must say what is missing, since it
     * is the only explanation they will get.
     */
    data class Refused(val reason: Component) : RunChargeResult
}

/**
 * Charges a player for starting a run. The inverse of [RunPayoutProvider], and the reason that one
 * changed shape.
 *
 * ### Why the module cannot hold the fee itself, not even as a number
 *
 * §2.18 prices a run in currency and §2.2 forbids this module from having an economy. Those two
 * together leave no room for a fee *amount* here either: a number in this module's config is
 * denominated in a currency it cannot name — 5,000 is dollars on our server, BP on another, and
 * nothing at all on a published build with no economy mod installed. So the host owns the whole
 * decision: what the fee is, what it is in, whether this player is exempt, and whether they can
 * afford it. The module owns only "may this run start", which is the one part of it the module has
 * to act on.
 *
 * ### The free allowance is consumed *here*, at run start
 *
 * §2.16 is explicit and it is the trap this seam is most likely to be implemented into: a free daily
 * or weekly run that is only consumed when a run *completes* is an unlimited free reroll — start,
 * dislike the draft, abandon, start again, forever, because the allowance was never spent. Any
 * provider implementing an allowance must decrement it on a non-quote [charge] call, at the door.
 *
 * ### There is deliberately no refund seam
 *
 * Abandoning a run does not give the fee back, and no interface here lets a host do that. The fee is
 * what prices restarting (§2.16); a refundable fee prices nothing, and adding the hook would make
 * the reroll loop a config option.
 */
fun interface RunChargeProvider {

    /**
     * Charge [player] for starting a run, or quote what it would cost.
     *
     * Called on the server thread, at run start, **before** the run's seed is minted and before
     * anything derived from it is shown — a player must never see a starter offer they then get
     * refused for. The player is online by definition, but implementations should still resolve by
     * UUID rather than assuming a `ServerPlayer` they were not handed.
     */
    fun charge(server: MinecraftServer, player: UUID, request: RunChargeRequest): RunChargeResult
}

/**
 * The registered charge destination, defaulting to **free**.
 *
 * ### Why free and not refuse
 *
 * The two candidate defaults are the two ways of being wrong. Refusing every run makes a published
 * build (§1.2) unplayable out of the box for everyone who is not us: the only configuration a
 * standalone install has is the one it ships with, so "no economy registered" would mean "the mode
 * does not start", and the first thing anyone downloading it would see is a refusal they have no way
 * to satisfy. Free makes runs cost nothing until someone plugs an economy in — which is also the
 * only *coherent* price on a server that has no currency, since a fee that cannot be denominated is
 * not a fee.
 *
 * Both defaults are chosen so the mode stays playable rather than safe, the same call
 * [RunBattleAi] makes: a degraded run is a worse run, a refused run is no mode at all.
 *
 * ### What the choice costs, and what pays for it
 *
 * On **our** server, where the fee is doing real work, forgetting to register a provider silently
 * makes runs free and reopens exactly the reroll loop §2.16 closed — and a silent free-for-all is a
 * worse failure than a loud one. Two things pay for that: the default logs once at WARN the first
 * time it lets a run through free, so an unconfigured server announces itself in the log rather than
 * in the economy; and [isRegistered] exists so the host mod can assert at boot that its own provider
 * won, instead of finding out from player behaviour.
 *
 * ### Threading and registering twice
 *
 * Same contract as [RunPayouts]: register once during another mod's setup, read at run start on the
 * server thread, `@Volatile` because we cannot enforce the ordering across mods, last writer wins
 * with a WARN so two mods fighting over the seam is visible.
 */
object RunCharges {

    /** Set the first time [FREE] lets a run through, so the WARN is once per boot and not per run. */
    private val warned = AtomicBoolean(false)

    /** Charges nothing. See the class docs for why this is the shipped default. */
    val FREE = RunChargeProvider { _, player, request ->
        if (!request.quoteOnly && warned.compareAndSet(false, true)) {
            log.warn(
                "roguelite: no charge provider registered — runs are FREE to start (first was {}). " +
                    "This is the shipped default; a server with an economy must register one, or the " +
                    "entry fee of §2.18 does not exist and runs can be rerolled at no cost.",
                player,
            )
        }
        RunChargeResult.Paid()
    }

    @Volatile
    private var provider: RunChargeProvider = FREE

    /** The active provider. Exposed so a host can assert its own registration won. */
    val current: RunChargeProvider get() = provider

    fun isRegistered(): Boolean = provider !== FREE

    fun register(provider: RunChargeProvider) {
        val previous = this.provider
        if (previous !== FREE) {
            log.warn(
                "roguelite: charge provider {} replaced by {} — only the latter will be charged",
                previous.javaClass.name, provider.javaClass.name,
            )
        }
        this.provider = provider
    }

    /** Restore the shipped default. For tests and for unloading a server-side integration. */
    fun reset() {
        provider = FREE
        warned.set(false)
    }

    /** Take the fee. A [RunChargeResult.Paid] result is the caller's permission to start the run. */
    fun charge(server: MinecraftServer, player: UUID): RunChargeResult =
        invoke(server, player, RunChargeRequest(quoteOnly = false))

    /** Ask the price without paying it, for a confirm prompt. Never consumes a free allowance. */
    fun quote(server: MinecraftServer, player: UUID): RunChargeResult =
        invoke(server, player, RunChargeRequest(quoteOnly = true))

    /**
     * Run the provider, treating a thrown exception as **paid**.
     *
     * Failing open is the deliberate choice and it is not the obvious one. A provider that throws
     * has left us unable to tell whether the player was debited. If it threw *after* taking the fee,
     * refusing the run costs them real currency and gives them nothing — an unrecoverable loss on
     * our side of the ledger. If it threw *before*, letting the run through costs one free run,
     * which is a rounding error against that. So the failure is absorbed at ERROR, loudly enough
     * that a provider failing on every start is visible long before it is expensive.
     */
    private fun invoke(server: MinecraftServer, player: UUID, request: RunChargeRequest): RunChargeResult =
        runCatching { provider.charge(server, player, request) }
            .onFailure {
                log.error(
                    "roguelite: charge provider failed for {} (quoteOnly={}) — allowing the run through unpaid",
                    player, request.quoteOnly, it,
                )
            }
            .getOrElse { RunChargeResult.Paid() }
}
