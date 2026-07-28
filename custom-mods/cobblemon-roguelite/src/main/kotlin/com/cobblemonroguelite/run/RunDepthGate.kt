package com.cobblemonroguelite.run

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Whether a player has earned a thing, asked as a yes/no about an id.
 *
 * The narrow signature is what keeps §2.18's promise of portability. Gym progress on our server is
 * stored as **vanilla advancements**, so the depth gate needs no seam into `cobblemon-bridge` and no
 * import this module is not allowed to have (§2.9) — and because the question is only "is this
 * advancement done", a published build can point the same config at whatever advancements its own
 * server hands out. Widening this to "how many gyms has this player beaten" would put our gym model
 * into a module that is not supposed to know we have one.
 *
 * [VanillaAdvancements] is the real implementation; tests supply a set.
 */
fun interface AdvancementCheck {

    fun isEarned(advancement: ResourceLocation): Boolean

    companion object {
        /** Nothing earned. The state a brand-new player is in, and the one worth testing against. */
        val NONE = AdvancementCheck { false }
    }
}

/** Reads advancement progress straight off the player, which is where the server already keeps it. */
object VanillaAdvancements {

    fun of(player: ServerPlayer): AdvancementCheck = AdvancementCheck { id ->
        // A null holder is an advancement id that no datapack registers. Answering "not earned"
        // rather than throwing matters because the gate is *configured* with these ids: a typo in
        // the config would otherwise crash every `/roguelite start` on the server instead of locking
        // one tier that an op can see is locked. RunDepthGate logs the unknown id at the caller.
        val holder = player.server.advancements.get(id) ?: return@AdvancementCheck false
        player.advancements.getOrStartProgress(holder).isDone
    }
}

/**
 * How deep this player may take a run, or why they may not start one.
 *
 * @property maxWave the deepest wave the run may reach, or null for the full run. Not a promise the
 *   run stops there gracefully — that is [RunEndCause.REACHED_DEPTH_CAP]'s job — just the number.
 */
sealed interface DepthGateResult {

    data class Allowed(val maxWave: Int?) : DepthGateResult

    /**
     * No configured tier is earned and the ungated depth is zero, so there is no run to start.
     *
     * [requires] lists every advancement that would open one, because any single tier is enough.
     * Naming them all is deliberate: a player who cleared gym 3 but not gym 1 is entitled to a run,
     * and a message that named only the first tier would tell them to go and re-earn something they
     * cannot re-earn.
     */
    data class Denied(val requires: List<ResourceLocation>) : DepthGateResult
}

/**
 * One rung of the badge gate: earning [advancement] entitles a player to runs [maxWave] deep.
 *
 * Tiers are **not** required to be listed in order, and depth is the maximum over the earned ones
 * rather than a count of them. Our gyms can be cleared in more than one order, and a player who beat
 * gym 4 first would otherwise sit at the depth of someone who has beaten nothing.
 */
data class DepthTier(
    val advancement: ResourceLocation,
    val maxWave: Int,
)

/**
 * §2.18's badge gate: how deep a run may go, decided by which advancements the player has.
 *
 * ### Why the shipped default is ungated
 *
 * Same call [com.cobblemonroguelite.integration.RunCharges] makes for the entry fee, for the same
 * reason. This module does not know what a badge is on somebody else's server — `gym_01`–`gym_10`
 * are *ours* — and a default that gated on advancement ids no other install has would make the mode
 * unstartable everywhere except here, with no way for a player to satisfy it. So an empty tier list
 * with no base cap means "full run, no gate", and our server supplies the ten tiers by config.
 *
 * ### Why the cap is re-read every wave rather than pinned into the run
 *
 * Unlike the payout table ([RunState.payoutTable]), which is pinned because it can be retuned in
 * either direction, a badge gate can only ever open further: advancements are not revoked. A player
 * who beats gym 5 during a run should get the deeper run immediately rather than on their next one,
 * and there is no direction in which a live read can surprise them badly.
 *
 * @property baseMaxWave depth for a player who has earned none of [tiers]. Null means the full run,
 *   which with an empty [tiers] is the shipped no-gate default; 0 means no run at all until a tier
 *   is earned, which is what our server sets.
 */
data class RunDepthGate(
    val tiers: List<DepthTier> = emptyList(),
    val baseMaxWave: Int? = null,
) {

    fun evaluate(check: AdvancementCheck): DepthGateResult {
        val earned = tiers.filter { check.isEarned(it.advancement) }
        if (earned.isEmpty()) {
            // Null base and no tiers earned is the ungated default, not a fall-through: it has to
            // stay Allowed(null) or a server that configured tiers but left the base unset would
            // silently hand unlimited depth to players who earned nothing.
            val base = baseMaxWave ?: return DepthGateResult.Allowed(null)
            return if (base >= 1) DepthGateResult.Allowed(base) else DepthGateResult.Denied(tiers.map { it.advancement })
        }
        // A tier that unlocks *less* depth than another earned tier cannot take depth away — the max
        // is over what the player has earned, so an operator reordering the config cannot demote
        // someone mid-run.
        return DepthGateResult.Allowed(earned.maxOf { it.maxWave })
    }

    companion object {
        /** No gate at all. See the class docs for why this rather than our ten gyms is what ships. */
        val UNGATED = RunDepthGate()
    }
}
