package com.cobblemonroguelite.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemonroguelite.run.RunController
import com.cobblemonroguelite.run.RunStore
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * Every wave battle currently being fought, and the four things the run needs to hear about them.
 *
 * ### Why the battle layer keeps its own index
 *
 * Cobblemon's events describe a *battle*; the run loop needs to know which **run** a battle belongs
 * to, and no Cobblemon type carries that. So a wave battle is registered here when it starts and
 * removed when it resolves, and everything below is a lookup against that index. A battle that is
 * not in it is somebody else's battle and nothing here touches it — which matters because a server
 * running this mod is also running ranked, gyms and ordinary wild encounters.
 *
 * ### What it reports, and what breaks without each
 *
 * - **Faints** → [RunController.pokemonFainted]. Permadeath is enforced on the faint and not on the
 *   party's HP at battle end, because a revive used mid-battle legitimately brings a Pokémon back —
 *   an end-of-battle HP check would kill it anyway ([RunState.kill][com.cobblemonroguelite.run.RunState.kill]
 *   says so). Reporting at the moment of the faint is also what makes a disconnect a second later
 *   attribute correctly rather than double-charging for a Pokémon that was already gone.
 * - **Who is on the field** → [RunController.battleFieldChanged]. §2.10's penalty kills whatever the
 *   run says is out, and the run can only be told the *lead* at wave start. Until the switch is
 *   reported, a player who switches and then drops loses the wrong Pokémon — right size of loss,
 *   wrong Pokémon, and nothing in the log to say so.
 * - **The result** → [RunController.waveCleared] / [RunController.waveLost]. The controller owns the
 *   advance, the checkpoint and the run end; a battle layer that advanced the wave itself would
 *   produce runs that are never persisted.
 * - **A battle that vanished** → also [RunController.waveLost]. See [reconcile]: the marker is what
 *   §2.10 charges a disconnect against, so a battle that ends without any of the above leaves it
 *   set, and the player's next ordinary logout is billed as a rage-quit.
 *
 * ### Adoption, and why trainer waves need it
 *
 * [com.cobblemonroguelite.integration.RunTrainerBattles] is on the far side of a licence boundary and
 * may summon asynchronously, so a trainer wave's battle appears some ticks after `beginWave` returned
 * true and this layer never sees it built. Rather than widen that seam to hand a `PokemonBattle`
 * back — which would put Cobblemon battle types in an interface whose whole point is that the other
 * side owns the battle — any battle that starts while its player's run carries a battle marker is
 * adopted. The marker is only set between the wave transition and the wave resolving, so the window
 * is the wave itself; a player who somehow starts an unrelated battle inside it gets it treated as
 * the wave, which is the honest cost and is preferable to trainer waves having no permadeath.
 *
 * ### Threading
 *
 * Cobblemon's battle events arrive on whatever thread the dispatch ran on, and [RunController] is
 * server-thread-only — it checkpoints, teleports and pays out. So every call into it hops through
 * `server.execute`, and the index itself is concurrent because the events that write it do not wait
 * for a tick. The `execute` queue is FIFO, so faints stay ordered ahead of the result that follows
 * them, which is what keeps a wipe from being reported before the Pokémon that caused it.
 */
object RunBattles {

    private val registered = AtomicBoolean(false)

    private val battles = ConcurrentHashMap<UUID, LiveBattle>()

    /** Player → battle, so [isFighting] does not have to walk the index on every item interaction. */
    private val byPlayer = ConcurrentHashMap<UUID, UUID>()

    /**
     * One wave battle in progress.
     *
     * [opponent] is the wild entity this module spawned, held so it can be cleared up when the wave
     * ends. Null for an adopted battle — a trainer wave's NPC belongs to whoever summoned it, and
     * discarding somebody else's entity is not ours to do.
     */
    private class LiveBattle(
        val server: MinecraftServer,
        val battleId: UUID,
        val player: UUID,
        val wave: Int,
        val opponent: PokemonEntity?,
    ) {
        /** Last field reported to the run, so an unchanged tick writes nothing. */
        @Volatile
        var reportedField: List<UUID> = emptyList()
    }

    /** True while [player] is fighting a wave. Read by [RunBagGuard] on every item interaction. */
    fun isFighting(player: UUID): Boolean = byPlayer.containsKey(player)

    /**
     * Register a battle as this run's wave.
     *
     * Replaces rather than refuses, because the explicit registration is always the better-informed
     * one: it carries the opponent entity and the wave the caller actually planned, where an adopted
     * entry has to infer the wave from the marker and knows of no entity to clean up. The race is
     * real — `startBattle` fires `BATTLE_STARTED_POST` *during* the call, i.e. before [RunWildBattle]
     * gets to register anything — and it is closed twice over: [adopt] defers to the server thread
     * and re-checks, and this overwrites if it somehow got there first.
     */
    fun track(
        server: MinecraftServer,
        battle: PokemonBattle,
        player: UUID,
        wave: Int,
        opponent: PokemonEntity? = null,
    ) {
        battles[battle.battleId] = LiveBattle(server, battle.battleId, player, wave, opponent)
        byPlayer[player] = battle.battleId
    }

    /** Subscribe once. A second subscription would report every faint twice, i.e. kill twice. */
    fun register() {
        if (!registered.compareAndSet(false, true)) return

        // LOWEST for the same reason [ArenaSpawnSuppressor] uses it: everything else on the server
        // should have seen the battle start before we decide it is a wave.
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.LOWEST) { adopt(it.battle) }
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL) { onFainted(it.battle, it.killed) }
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            onResolved(event.battle, event.winners.map { it.uuid }, event.wasWildCapture)
        }
        // A flee is not a loss and not a win: the wave is simply not fought. It still has to come
        // through here, because the §2.10 marker is set and a marker nobody clears turns the player's
        // next logout into a disconnect penalty for a battle that ended cleanly.
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event ->
            log.warn(
                "roguelite: a wave battle was fled — the run keeps its damage and re-fights the wave " +
                    "against a fresh opponent. Whether that should cost anything is §2.10's question " +
                    "and has not been answered for fleeing.",
            )
            onResolved(event.battle, winners = emptyList(), captured = false)
        }

        NeoForge.EVENT_BUS.addListener<ServerTickEvent.Post> { reconcile(it.server) }
        log.debug("roguelite: wave battle tracking active")
    }

    /** Drop everything. For tests and for a server shutting down under live battles. */
    fun reset() {
        battles.clear()
        byPlayer.clear()
    }

    /**
     * Take over a battle that started while its player's run was mid-wave. See the class docs.
     *
     * Deferred to the server thread before it looks anything up: [RunStore.of] goes through the
     * world's `dataStorage`, which is not safe to touch from a battle thread, and a provider that
     * starts its battle off-thread would otherwise race the save-data map open.
     */
    private fun adopt(battle: PokemonBattle) {
        if (battles.containsKey(battle.battleId)) return
        val player = battle.players.firstOrNull() ?: return
        val server = player.server
        server.execute {
            if (battles.containsKey(battle.battleId) || byPlayer.containsKey(player.uuid)) return@execute
            val marker = RunStore.of(server).get(player.uuid)?.battle ?: return@execute
            track(server, battle, player.uuid, marker.wave)
            log.info(
                "roguelite: adopted {}'s battle as wave {} — it was started behind a seam",
                player.gameProfile.name, marker.wave,
            )
        }
    }

    /**
     * Permadeath, one Pokémon at a time (§2.13).
     *
     * Only the player's side is reported. The opponent fainting is how a wave is *won*, and handing
     * it to [RunController.pokemonFainted] would ask the run to kill a Pokémon it has never heard of
     * — which it would refuse, loudly and correctly, once per wave.
     */
    private fun onFainted(battle: PokemonBattle, killed: BattlePokemon) {
        val live = battles[battle.battleId] ?: return
        // `actor` is a lateinit on Cobblemon's side, so it is read through a guard rather than
        // trusted: a faint that arrives before the Pokémon has been bound to an actor would throw out
        // of an event subscriber and take the battle's dispatch with it, to kill nothing.
        val owner = runCatching { killed.actor.uuid }.getOrNull() ?: return
        if (owner != live.player) return
        // originalPokemon, not effectedPokemon: the run party is handed over uncloned, so the two are
        // the same object here — and they would not be if anyone ever reintroduced a clone, at which
        // point this is the one that still matches [RunState.kill]'s UUID.
        val pokemon = killed.originalPokemon
        live.server.execute { RunController.pokemonFainted(live.server, live.player, pokemon) }
    }

    /**
     * The wave is over, however it ended.
     *
     * The entity cleanup happens here rather than in the controller because the controller has no
     * idea an entity exists: [RunController.waveCleared] advances a number and writes a file. A wild
     * opponent left standing outlives the wave, is still in the slot when the next one is stamped,
     * and can be caught by a player who is not in a battle at all.
     */
    private fun onResolved(battle: PokemonBattle, winners: List<UUID>, captured: Boolean) {
        val live = battles.remove(battle.battleId) ?: return
        byPlayer.remove(live.player, live.battleId)
        val won = live.player in winners
        live.server.execute {
            // Not when it was captured: the capture already removed the entity, and discarding on top
            // of that is a discard of whatever now occupies the reference.
            if (!captured) live.opponent?.takeIf { !it.isRemoved }?.discard()
            if (!stillOnline(live)) return@execute
            if (won) {
                RunController.waveCleared(live.server, live.player)
            } else {
                RunController.waveLost(live.server, live.player)
            }
        }
    }

    /**
     * Whether the run's owner is still connected — and the reason nothing is reported when they are
     * not.
     *
     * Both [RunController.waveCleared] and [RunController.waveLost] clear §2.10's battle marker,
     * which is correct for a battle the player was present for and is precisely wrong for one they
     * dropped out of. Cobblemon ends a disconnected player's battle itself (`BattleRegistry`'s
     * disconnect hook calls `PokemonBattle.stop`), so without this guard the marker would be cleared
     * by the very event the disconnect caused — and §2.10's penalty would never fire for anybody.
     * That failure is completely silent: quitting a losing wave would simply be free.
     *
     * Leaving the marker set is the whole of what is owed. The attribution runs on their next login,
     * compares the boot id, and decides between "the server restarted" and "they pulled the plug"
     * with better information than this layer has.
     */
    private fun stillOnline(live: LiveBattle): Boolean {
        if (live.server.playerList.getPlayer(live.player) != null) return true
        log.info(
            "roguelite: wave {} ended while its player was offline — leaving the battle marker for " +
                "§2.10 to attribute on their next login",
            live.wave,
        )
        return false
    }

    /**
     * Report the field, and notice battles that ended without telling us.
     *
     * ### Why the field is polled rather than reported from a switch event
     *
     * Cobblemon has no switch event. It has a send-out event, which fires for the entity half of a
     * switch and not for the half that has no entity, and it says nothing about forced swaps or
     * faint replacements. The actor's own active slots are the authority for all of those at once, so
     * this reads them and writes only when they change. The cost is one tick of staleness on the
     * §2.10 marker — a player would have to drop their connection inside the same tick they switched
     * to land the penalty on the wrong Pokémon — against an event-based version that would be wrong
     * for whole categories of switch, silently, forever.
     *
     * ### The orphan branch
     *
     * A tracked battle that Cobblemon no longer has, or that has ended without a victory or a flee
     * reaching us, is a wave the run will never hear the end of: the marker stays set and the next
     * ordinary logout is charged as a rage-quit. Routing it through [RunController.waveLost] clears
     * the marker and leaves the run exactly where it is unless the party is already gone, which is
     * the reconciliation that method exists for.
     */
    private fun reconcile(server: MinecraftServer) {
        if (battles.isEmpty()) return
        for (live in battles.values) {
            if (live.server !== server) continue
            val battle = BattleRegistry.getBattle(live.battleId)
            if (battle == null || battle.ended) {
                battles.remove(live.battleId, live)
                byPlayer.remove(live.player, live.battleId)
                live.opponent?.takeIf { !it.isRemoved }?.discard()
                // The disconnect case reaches here rather than through a victory — Cobblemon stops a
                // disconnected player's battle outright, which produces no result at all — so the
                // same guard applies and for the same reason. See [stillOnline].
                if (!stillOnline(live)) continue
                log.warn(
                    "roguelite: wave {} for {} ended without a result reaching the run — clearing the " +
                        "battle marker so the next logout is not charged for it",
                    live.wave, live.player,
                )
                RunController.waveLost(server, live.player)
                continue
            }
            reportField(live, battle)
        }
    }

    private fun reportField(live: LiveBattle, battle: PokemonBattle) {
        val actor = battle.actors.firstOrNull { it is PlayerBattleActor && it.uuid == live.player } ?: return
        val onField = actor.activePokemon.mapNotNull { it.battlePokemon?.originalPokemon }
        val uuids = onField.map { it.uuid }
        if (uuids == live.reportedField) return
        live.reportedField = uuids
        RunController.battleFieldChanged(live.server, live.player, onField)
    }
}
