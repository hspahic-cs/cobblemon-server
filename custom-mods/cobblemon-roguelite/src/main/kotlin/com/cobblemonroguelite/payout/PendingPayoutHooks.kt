package com.cobblemonroguelite.payout

import com.cobblemonroguelite.arena.RunArenas
import com.cobblemonroguelite.run.RunStore
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("cobblemon_roguelite/payout")

/**
 * Delivering payouts that were owed to somebody who was not online to take them.
 *
 * ### The shape of it
 *
 * Login **arms** a delivery; it does not make one. The tick loop makes it, once
 * [PendingPayoutGate] says the player is somewhere a dropped item will still be in a moment.
 * [PendingPayoutGate] argues why that indirection is not ceremony — the login sequence teleports
 * people, and a payout dropped mid-teleport is a payout dropped into a dimension nobody is standing
 * in, which is the failure this whole feature was written to avoid.
 *
 * ### Why the items are dropped rather than inserted
 *
 * Because there is no inventory-full case to get wrong. Insertion has to decide what to do with the
 * remainder, and every answer is either "drop it anyway" or "lose it"; dropping unconditionally is
 * the first answer with no branch in front of it. The stacks are targeted at the player
 * ([ItemEntity.setTarget]), so nobody standing at spawn can pick up somebody else's run payout —
 * which insertion would have got for free and dropping would otherwise have thrown away.
 *
 * ### Why the arming state is in memory and losing it costs nothing
 *
 * [armed] is a scheduling detail, not a record of the debt. The debt is in [PendingPayoutStore], on
 * disk; a restart that empties this map means the payout is delivered at the player's *next* login
 * instead of this one. That is the same non-event as a restart during any other wait, and it is the
 * reason nothing here needs to be persisted or recovered.
 */
object PendingPayoutHooks {

    /** Player → the tick at which their delivery may next be attempted. Usually empty. */
    private val armed = ConcurrentHashMap<UUID, Armed>()

    private data class Armed(val loginTick: Int, val nextCheckTick: Int)

    @SubscribeEvent
    fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val server = player.server ?: return
        val store = PendingPayoutStore.of(server)
        if (!store.isOwed(player.uuid)) return

        val tick = server.tickCount
        armed[player.uuid] = Armed(loginTick = tick, nextCheckTick = tick + PendingPayoutGate.SETTLE_TICKS)
        log.info(
            "roguelite: {} logged in owed {} held payout(s) — armed for delivery",
            player.gameProfile.name, store.peek(player.uuid).size,
        )
        // Said now rather than when it lands, and only for the one wait a player can do something
        // about. A player who logs straight back into a run would otherwise see nothing at all this
        // session and conclude the payout for the run that killed them never existed.
        if (RunStore.of(server).hasRun(player.uuid)) player.sendSystemMessage(waitingForRun())
    }

    /**
     * Disarm on the way out.
     *
     * Not a cleanup nicety: an armed entry for a player who is gone would have the tick loop asking
     * about them forever, and the entry is re-created by [onLogin] from the store the moment they
     * come back. The debt itself is untouched — it is in the file, not in this map.
     */
    @SubscribeEvent
    fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        armed.remove(player.uuid)
    }

    /**
     * Drop the arming state with the server it belonged to.
     *
     * An integrated server reopening a different world would otherwise start with entries pointing at
     * players of the previous one, and their tick numbers would be from a counter that has restarted.
     */
    @SubscribeEvent
    fun onServerStopped(event: ServerStoppedEvent) {
        armed.clear()
    }

    /**
     * The delivery attempt.
     *
     * Returns immediately when nothing is armed, which is every tick on a server where nobody is owed
     * anything — i.e. essentially all of them. When something *is* armed, the work is a map lookup per
     * armed player every [PendingPayoutGate.RETRY_TICKS] ticks.
     */
    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Post) {
        if (armed.isEmpty()) return
        val server = event.server
        val tick = server.tickCount
        for ((uuid, state) in armed) {
            if (tick < state.nextCheckTick) continue
            val verdict = attempt(server, uuid, state, tick)
            when {
                verdict.keepWaiting -> armed[uuid] = state.copy(nextCheckTick = tick + PendingPayoutGate.RETRY_TICKS)
                else -> armed.remove(uuid)
            }
        }
    }

    private fun attempt(server: MinecraftServer, uuid: UUID, state: Armed, tick: Int): DeliveryVerdict {
        val store = PendingPayoutStore.of(server)
        val player = server.playerList.getPlayer(uuid)
        if (player == null) {
            // Logged out between the arming and this tick without the logout event having been seen
            // yet. Not an error, and specifically not a delivery: dropping items for an absent player
            // is the mistake the store exists to prevent.
            armed.remove(uuid)
            return DeliveryVerdict.NOT_IN_THE_WORLD
        }
        val situation = DeliverySituation(
            owed = store.isOwed(uuid),
            alive = player.isAlive && !player.isRemoved,
            ticksSinceLogin = tick - state.loginTick,
            inArena = RunArenas.isInArena(player),
            hasRun = RunStore.of(server).hasRun(uuid),
        )
        val verdict = PendingPayoutGate.evaluate(situation)
        if (verdict.deliverable) deliver(server, player)
        return verdict
    }

    /**
     * Hand over everything [player] is owed, now.
     *
     * ### The order, which is the exactly-once guarantee
     *
     * [PendingPayoutStore.claim] logs the payout, removes it and flushes that removal to disk before
     * returning — so by the time anything is dropped, the ledger no longer owes it. A crash between
     * the two therefore **loses** the payout rather than paying it twice, which is the direction
     * [PendingPayoutLedger] argues for at length: a duplicate is unbounded and invisible, a loss is
     * single and is sitting in the log line that was written first.
     *
     * The drop is wrapped for the same reason. If spawning the item entities throws, the debt has
     * already been cleared and cannot be re-derived from anything but that log line, so the failure
     * says so explicitly instead of surfacing as a stack trace from a tick handler.
     *
     * Public because an operator command is the obvious next caller ("pay them now"), and because the
     * thing that would be wrong to expose — a version that skips the gate *and* the claim — is not
     * reachable from here.
     */
    fun deliver(server: MinecraftServer, player: ServerPlayer): PayoutDropPlan {
        val claimed = PendingPayoutStore.of(server).claim(server, player.uuid)
        if (claimed.isEmpty()) return PayoutDropPlan.NOTHING

        val grants = claimed.flatMap { it.grants }
        val plan = PayoutDropPlanner.plan(grants) { id ->
            val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null)
            if (item == null) null else ItemStack(item, 1).maxStackSize
        }

        val dropped = runCatching { drop(player, plan) }
            .onFailure {
                log.error(
                    "roguelite: {}'s held payout {} was claimed and then failed to drop — it is GONE and must " +
                        "be restored by hand from this line",
                    player.gameProfile.name, grants.describe(), it,
                )
            }
            .getOrDefault(0)

        log.info(
            "roguelite: delivered {}'s held payout — {} grant(s) as {} stack(s), {} unresolved, owed since {}",
            player.gameProfile.name, plan.grants.size, dropped, plan.unresolved.size,
            claimed.minOf { it.owedAtEpochMs },
        )
        player.sendSystemMessage(delivered(plan))
        return plan
    }

    /**
     * The stacks, at their feet.
     *
     * Built here rather than through `ServerPlayer.drop` for two reasons that are both about this
     * being a payout and not a player throwing something away: `drop` awards the "items dropped"
     * statistic and fires the toss event (a mod may cancel it — for a payout, silently), and it
     * throws the stack from eye height in the direction they are facing, which is how a payout ends
     * up in the lava they happened to be looking at. This spawns at the feet with the small pop the
     * `ItemEntity` constructor gives, and targets the stack at its owner so it cannot be picked up by
     * anybody else.
     */
    private fun drop(player: ServerPlayer, plan: PayoutDropPlan): Int {
        var dropped = 0
        for (planned in plan.planned) {
            // Non-null by construction: the planner only plans grants whose lookup succeeded. Written
            // as a skip rather than a `!!` because the alternative to a missing item here is a crash
            // in a tick handler holding a payout that has already been claimed.
            val item = BuiltInRegistries.ITEM.getOptional(planned.grant.item).orElse(null)
            if (item == null) {
                log.error(
                    "roguelite: '{}' resolved during planning and not during delivery — {} lost",
                    planned.grant.item, planned.total,
                )
                continue
            }
            for (count in planned.counts) {
                val entity = ItemEntity(player.level(), player.x, player.y + 0.5, player.z, ItemStack(item, count))
                entity.setPickUpDelay(PICKUP_DELAY_TICKS)
                entity.setTarget(player.uuid)
                player.level().addFreshEntity(entity)
                dropped++
            }
        }
        return dropped
    }

    /**
     * Ten ticks, not the forty `ServerPlayer.drop` uses.
     *
     * Forty exists so that a player throwing something away does not walk straight back into it. This
     * is the opposite act, and half a second is only there so the stacks are visibly *given* rather
     * than appearing already in the inventory with no explanation for the message that follows.
     */
    private const val PICKUP_DELAY_TICKS = 10

    /**
     * Said in this file rather than in `RunMessages`, which is where everything else the mode says
     * lives. Not a preference: these two lines are the only player-facing strings that belong to a
     * run that has already ended and been accounted for, and folding them into the run-end vocabulary
     * would invite the next reader to build them from a [com.cobblemonroguelite.run.RunEndReport] that
     * no longer exists by the time they are needed.
     */
    private fun delivered(plan: PayoutDropPlan): Component {
        val head = "Your run ended while you were away. Its payout — ${plan.stacks} stack(s) — is at your feet."
        val tail = if (plan.complete) "" else
            " ${plan.unresolved.size} reward(s) could not be handed over — tell an operator."
        return Component.literal(head + tail).withStyle(ChatFormatting.YELLOW)
    }

    private fun waitingForRun(): Component = Component.literal(
        "A payout from an earlier run is waiting for you. It will be handed over when your current run ends.",
    ).withStyle(ChatFormatting.YELLOW)
}
