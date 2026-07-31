package com.cobblemonroguelite.run

import com.cobblemonroguelite.arena.RunArenas
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.CommandEvent
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/stash")

/**
 * The §7 guards — every one keyed on the swap tag or on [RunArenas.isInArena], **never on a
 * dimension id** (design §0's one rule, F13: a dimension-keyed guard under the `FixedArenas` layout
 * would fire on bystanders in a shared world).
 *
 * The tag is what makes each of these safe to state simply: a tagged player's live inventory is the
 * run's — the real one is durably on disk — so cancelling their drops destroys nothing, refusing
 * their teleport takes nothing, and closing their ender chest hides nothing they could reach
 * legitimately anyway. And a paused player carries no tag, so every guard ignores them for free.
 */
object RunIsolationGuards {

    private val registered = AtomicBoolean(false)

    /**
     * The displacement poll's two-strike memory (design §15's own worry: a poll that runs an exit
     * swap on a predicate must never act on one bad sample — a transient `isInArena` misread would
     * repeatedly exit-swap a player mid-wave). First strike records position, second consecutive
     * strike acts.
     */
    private val strikes = mutableMapOf<UUID, String>()

    /** One second. Was three — the live test found the exit "slightly delayed", and the human's
     *  ruling is that players should not be outside mid-run at all, so the window shrinks: two
     *  strikes at this cadence confirms displacement in one to two seconds. */
    private const val POLL_INTERVAL_TICKS = 20

    fun register() {
        if (!registered.compareAndSet(false, true)) return

        // §7.1 — death drops and XP orbs. Cancelled, not collected: everything a tagged player holds
        // is run property, and the run bag's death penalty is losing what was not checkpointed, which
        // is party-equivalent granularity. A tagless bystander keeps vanilla behaviour.
        NeoForge.EVENT_BUS.addListener<LivingDropsEvent> { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            if (RunInventoryStash.isTagged(player)) {
                event.isCanceled = true
                log.debug("roguelite: cancelled death drops for tagged {}", player.gameProfile.name)
            }
        }
        NeoForge.EVENT_BUS.addListener<LivingExperienceDropEvent> { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            if (RunInventoryStash.isTagged(player)) event.isCanceled = true
        }

        // D2 — the teleport-command refusal. Matched on the root literal against a configurable list,
        // because host command sets vary (§1.2); tested against the tag, so paused players teleport
        // freely. Refusing the command is the primary mechanism; the displacement poll below is the
        // defence in depth for whatever slips past it.
        NeoForge.EVENT_BUS.addListener<CommandEvent> { event ->
            val source = event.parseResults.context.source
            val player = source.entity as? ServerPlayer ?: return@addListener
            if (!RunInventoryStash.isTagged(player)) return@addListener
            val root = event.parseResults.context.nodes.firstOrNull()?.node?.name ?: return@addListener
            if (root.lowercase() in RunSettings.current.blockedCommandsDuringRun) {
                event.isCanceled = true
                player.sendSystemMessage(RunMessages.commandRefusedDuringRun())
                log.info("roguelite: refused /{} for tagged {}", root, player.gameProfile.name)
            }
        }

        // §7.4 — the ender chest, by container identity and never menu type (F12: NeoEssentials'
        // /enderchest opens a GENERIC_9x3 ChestMenu indistinguishable by type from every barrel).
        // The open event is not cancellable, so the menu is closed the tick it opens — D2 already
        // refuses the command; this catches mods that open the container directly.
        NeoForge.EVENT_BUS.addListener<PlayerContainerEvent.Open> { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            if (!RunInventoryStash.isTagged(player)) return@addListener
            val enderChest = player.enderChestInventory
            if (event.container.slots.any { it.container === enderChest }) {
                player.closeContainer()
                player.sendSystemMessage(RunMessages.enderChestRefusedDuringRun())
                log.info("roguelite: closed {}'s ender chest mid-run", player.gameProfile.name)
            }
        }

        // §7.3 — the displacement poll. Converts every escape route nobody predicted — op /tp, a
        // portal, another mod's mechanics — into a slightly odd pause: the exit swap runs wherever
        // the player is standing, and they walk away with their own inventory. The same sweep ejects
        // tagless players found inside arena space (row 8, the /tpahere mule).
        NeoForge.EVENT_BUS.addListener<ServerTickEvent.Post> { event ->
            // Every tick, before the poll's early-return: the delayed-task queue is what staggers a
            // battle start off a party install (bug #5), and a queue on the poll cadence would turn a
            // 20-tick delay into 60.
            RunTicks.tick(event.server)
            if (event.server.tickCount % POLL_INTERVAL_TICKS != 0) return@addListener
            for (player in event.server.playerList.players) {
                val tagged = RunInventoryStash.isTagged(player)
                val inArena = RunArenas.isInArena(player)
                val displaced = (tagged && !inArena) || (!tagged && inArena)
                if (!displaced) {
                    strikes.remove(player.uuid)
                    continue
                }
                val position = "${player.level().dimension().location()}:${player.blockPosition().toShortString()}"
                val first = strikes.put(player.uuid, position)
                if (first == null) {
                    log.info(
                        "roguelite: displacement strike one for {} (tagged={} inArena={}) at {}",
                        player.gameProfile.name, tagged, inArena, position,
                    )
                    continue
                }
                strikes.remove(player.uuid)
                log.warn(
                    "roguelite: {} confirmed displaced (tagged={} inArena={}) at {} — reconciling in place",
                    player.gameProfile.name, tagged, inArena, position,
                )
                runCatching {
                    RunIsolation.reconcile(player, RunStore.of(event.server).get(player.uuid))
                    if (tagged) player.sendSystemMessage(RunMessages.displacedExit())
                }.onFailure { log.error("roguelite: displacement reconcile failed for {}", player.gameProfile.name, it) }
            }
        }

        log.debug("roguelite: isolation guards active")
    }
}
