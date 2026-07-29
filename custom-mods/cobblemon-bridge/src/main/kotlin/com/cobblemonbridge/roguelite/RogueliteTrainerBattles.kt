package com.cobblemonbridge.roguelite

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleRules
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ~40 trainer and boss waves of a roguelite run (§2.14), started from the side of the licence
 * boundary that is allowed to talk to RCT.
 *
 * ### Why this lives here and not in cobblemon-roguelite
 *
 * §1.2/§2.6 leave RCTmod's licence unverified, so nothing in `cobblemon-roguelite` may compile
 * against `rctapi` — which means that module can carry the roster, the schedule and the trainer id,
 * and cannot carry the summon. It declares `RunTrainerBattleProvider` and defaults it to a
 * provider that refuses; this is the implementation, registered from a mod that may name RCT.
 * Dependency direction is one-way by construction: bridge knows roguelite's class names
 * ([RogueliteSeam]), roguelite knows nothing of ours (plan §2.9).
 *
 * ### What "start the wave" means here
 *
 * Four things, and every one of them is load-bearing:
 *
 * 1. **The trainer the run handed us**, never one of our own. The run has already reconciled its
 *    pick against fixed encounters and its no-repeat memory, persisted it, and logged it. Drawing
 *    another here would make a resumed run disagree with its own history about who it fought.
 * 2. **The run party on the player's side.** This is why RCT does not run the battle — see
 *    [RctTrainerParts]. `RCTMod.makeBattle` builds the player's side out of their real Cobblemon
 *    party, and a run's party deliberately is not there (§1.1).
 * 3. **The team, from whichever of the two paths this wave uses** — see [teamFor]. Roguelite either
 *    sends generated properties strings (plan §2.30) or sends none, meaning the RCT trainer's own
 *    authored team. Both then take the wave's level on the opponent's side at `BattleStartedEvent.Pre`
 *    — verified on dev 2026-07-28: the write lands, Showdown packs the team afterwards, and RCT does
 *    not re-derive it. The NPC team is a `safeCopyOf` battle clone, so unlike the player-side gym
 *    downlevel in [com.cobblemonbridge.battle.GymBattleAdjustHook] this needs **no** NBT restore
 *    machinery: the authored trainer is never touched, and a crash mid-battle leaves nothing to put
 *    back. On the generated path the level is already in the properties string, so the forcing is a
 *    no-op there and the *moveset* is the one Cobblemon derives for that level — which is the whole
 *    reason §2.30 generates rather than authors.
 * 4. **Nothing else.** The faints, the field, the result and §2.10's disconnect marker are all
 *    roguelite's — its battle layer adopts any battle that starts while a run carries a battle
 *    marker, which is exactly the window this call sits in. Reporting them from here would double
 *    every faint. The one thing left to us is the NPC entity, because roguelite explicitly refuses
 *    to discard an entity it did not spawn.
 *
 * ### Failing closed
 *
 * Every refusal below returns false *before* a battle exists, so the run stays at the same wave with
 * its party intact and roguelite clears the marker it stamped. That is the whole reason the summon
 * is synchronous ([RctTrainerParts.spawn]) rather than going through `summon_persistent`: a wave that
 * returned true and then failed to materialise would leave a run holding a battle marker for a
 * battle that never starts — never resolving, and charging the player's next logout as a rage-quit.
 */
object RogueliteTrainerBattles {

    /**
     * How far in front of the player the trainer stands, in blocks. Same value the wild path uses,
     * measured the same way, so a trainer wave and a wild wave put the opponent in the same place.
     */
    private const val OPPONENT_DISTANCE = 6.0

    /** How long a stashed wave level stays applicable, in ms. See [applyWaveLevel]. */
    private const val STASH_TTL_MS = 10_000L

    private const val SWEEP_INTERVAL_TICKS = 20

    /** playerUuid → (waveLevel, stashedAtMs), consumed by the `BATTLE_STARTED_PRE` subscriber. */
    private val pendingLevels: MutableMap<UUID, Pair<Int, Long>> = ConcurrentHashMap()

    /** battleId → the trainer entity we spawned for it, so it can be cleaned up when the wave ends. */
    private val npcs: MutableMap<UUID, Entity> = ConcurrentHashMap()

    private var sweepCounter = 0

    /**
     * Register with roguelite and subscribe the two things a started wave still owes.
     *
     * Safe to call when roguelite is absent: [RogueliteSeam.install] returns false, and the event
     * subscriptions below are only wired when it succeeds so a server without the mode pays nothing.
     */
    fun install() {
        if (!RogueliteSeam.install(::begin)) return
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, ::applyWaveLevel)
        NeoForge.EVENT_BUS.addListener<ServerTickEvent.Post> { sweep() }
    }

    // ─── Starting the wave ─────────────────────────────────────────────────

    private fun begin(server: MinecraftServer, player: ServerPlayer, wave: TrainerWave): Boolean {
        val name = player.gameProfile.name

        // An id that names nothing is a *configuration* fault and it is reported here because this is
        // the only side of the seam that can see RCT's registry. Loud, and false: a trainer wave that
        // silently became a no-op would let a run walk past it to its payout.
        val rctId = RctTrainerParts.resolveTrainerId(wave.trainerId)
        if (rctId == null) {
            CobblemonBridge.logger.error(
                "roguelite wave {} for {} names trainer '{}', which RCT has no trainer for — refusing " +
                    "the wave. The run keeps its party and stays on this wave. Check the run's trainer " +
                    "roster against data/rctmod/trainers/.",
                wave.wave, name, wave.trainerId,
            )
            return false
        }

        val runState = RogueliteSeam.runOf(server, player.uuid)
        if (runState == null) {
            CobblemonBridge.logger.error(
                "roguelite wave {} for {} was requested but the run store has no run for them",
                wave.wave, name,
            )
            return false
        }

        // Required by the seam contract, and not a formality: `prepare` re-takes the arena's chunk
        // ticket, and an arena that has gone cold accepts a spawn silently and keeps nothing.
        val arenaDimension = RogueliteSeam.holdArena(server, runState) ?: return false
        val level: ServerLevel = server.getLevel(player.level().dimension())
            ?.takeIf { it.dimension().location() == arenaDimension }
            ?: run {
                // Same refusal the wild path makes, for the same reason: summoning against a player
                // who is not in their arena puts an RCT trainer wherever they actually are.
                CobblemonBridge.logger.error(
                    "roguelite wave {}: {} is in {} but their arena is in {} — refusing rather than " +
                        "summoning the trainer outside it",
                    wave.wave, name, player.level().dimension().location(), arenaDimension,
                )
                return false
            }

        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            CobblemonBridge.logger.warn(
                "roguelite wave {}: {} is already in a battle — not started", wave.wave, name,
            )
            return false
        }

        val playerTeam = RogueliteSeam.runTeam(player, runState)
        if (playerTeam.isNullOrEmpty()) {
            // Roguelite logs why; this says which wave it cost, because the two lines are what tie a
            // refused wave to a run party the store would not rebuild.
            CobblemonBridge.logger.error(
                "roguelite wave {}: {}'s run party could not be made into a battle team", wave.wave, name,
            )
            return false
        }

        val npcTeam = teamFor(wave, rctId) ?: return false

        val yaw = player.yRot
        val at = player.position()
        val entity = RctTrainerParts.spawn(
            level,
            rctId,
            at.x - sin(Math.toRadians(yaw.toDouble())) * OPPONENT_DISTANCE,
            at.y,
            at.z + cos(Math.toRadians(yaw.toDouble())) * OPPONENT_DISTANCE,
            // Facing back down the player's own line of sight, so the trainer looks at them.
            yaw + 180f,
        ) ?: return false

        val npcActor = RctTrainerParts.actorFor(rctId, entity, npcTeam)
        if (npcActor == null) {
            entity.discard()
            return false
        }

        // Stashed *before* startBattle, because startBattle posts BATTLE_STARTED_PRE synchronously —
        // the subscriber runs inside the call below, not after it.
        pendingLevels[player.uuid] = wave.level to System.currentTimeMillis()

        val started = BattleRegistry.startBattle(
            battleFormat = runFormat(),
            side1 = BattleSide(PlayerBattleActor(player.uuid, playerTeam)),
            side2 = BattleSide(npcActor),
        )
        val battle = (started as? SuccessfulBattleStart)?.battle
        if (battle == null) {
            pendingLevels.remove(player.uuid)
            // The trainer is already standing in the arena, so it has to go: an NPC left behind
            // outlives the wave and is still there when the next one is stamped into the slot.
            entity.discard()
            CobblemonBridge.logger.error(
                "roguelite wave {} for {} was refused by Cobblemon ({})", wave.wave, name, started,
            )
            return false
        }

        npcs[battle.battleId] = entity
        CobblemonBridge.logger.info(
            "roguelite: {} started {} wave {} against '{}' ({} {} Pokémon at L{})",
            name, wave.kind, wave.wave, rctId, npcTeam.size,
            // Which of the two paths built the team, because it is the first thing anyone reading a
            // "that leader's team was wrong" report needs to know and it is not recoverable later.
            if (wave.teamProperties.isEmpty()) "authored" else "generated", wave.level,
        )
        // True, and nothing more is reported from here: roguelite adopts this battle on
        // BATTLE_STARTED_POST because the run carries a battle marker, and from that moment the
        // faints, the field and the result are its business.
        return true
    }

    // ─── The opponent's team: generated, or the trainer's own ──────────────

    /**
     * The team for this wave — built from roguelite's generated properties strings when it sent any,
     * and otherwise the RCT trainer's authored team.
     *
     * ### The empty case is a fight, not a failure
     *
     * Roguelite decides which trainers generate (plan §2.30 keeps the Elite Four and the champion
     * hand-made), and an empty [TrainerWave.teamProperties] is how it says "this one is authored".
     * Refusing on empty would delete exactly the fights somebody tuned.
     *
     * ### Why generated members are built here rather than sent as Pokémon
     *
     * Because roguelite may not build them. Its module compiles against Cobblemon but the *battle* is
     * ours — and more to the point, a `PokemonProperties.parse().create()` needs the species and move
     * registries of a booted server, which is precisely what keeps roguelite's own generator pure and
     * unit-testable. It decides; this builds.
     *
     * A member that fails to build is fatal to the wave rather than skipped. A trainer arriving one
     * Pokémon short is invisible to the player and looks like a balance problem forever after; a
     * refused wave leaves the run where it is, with its party, and puts the bad species id in the log.
     */
    private fun teamFor(wave: TrainerWave, rctId: String): List<BattlePokemon>? {
        if (wave.teamProperties.isEmpty()) {
            val authored = RctTrainerParts.teamOf(rctId)
            if (authored.isNullOrEmpty()) {
                CobblemonBridge.logger.error(
                    "roguelite wave {}: RCT trainer '{}' resolved but has no team, and the run generated " +
                        "none for it — refusing the wave",
                    wave.wave, rctId,
                )
                return null
            }
            // safeCopyOf is what §2.6 relies on: the opponent fights a battle clone, so forcing the
            // wave's level onto it never reaches the authored trainer on disk.
            return authored.map { BattlePokemon.safeCopyOf(it) }
        }

        val team = mutableListOf<BattlePokemon>()
        for (properties in wave.teamProperties) {
            val pokemon = runCatching { PokemonProperties.parse(properties).create() }
                .onFailure {
                    CobblemonBridge.logger.error(
                        "roguelite wave {}: could not build '{}' for trainer '{}' — refusing the wave",
                        wave.wave, properties, rctId, it,
                    )
                }
                .getOrNull() ?: return null
            // `create()` leaves the moveset empty on some paths, and a Pokémon with no moves takes the
            // battle to a state where neither side can act. Same guard the wild path and the monument
            // respawn path use; here the level is already in the properties string, so the moves it
            // initialises are the ones for the wave's level — which is the entire point of generating.
            if (pokemon.moveSet.getMoves().isEmpty()) pokemon.initializeMoveset()
            team += BattlePokemon.safeCopyOf(pokemon)
        }
        return team
    }

    /**
     * Gen 9 singles plus §2.11's bag ban — the same format the wild path builds, copied rather than
     * mutated.
     *
     * Deliberately not RCT's own per-trainer `BattleFormat`/`BattleRules`: a run is singles by
     * §2.14, and an authored trainer that happens to declare doubles would otherwise change the
     * shape of one wave in a run. `GEN_9_SINGLES` is one shared instance for the whole server, so
     * adding the rule in place would ban bag items in ranked too.
     */
    private fun runFormat(): BattleFormat =
        BattleFormat.GEN_9_SINGLES.let { it.copy(ruleSet = it.ruleSet + BattleRules.BAG_CLAUSE) }

    // ─── The wave's level, on the opponent only ────────────────────────────

    /**
     * Force every opponent Pokémon to the wave's level at `BattleStartedEvent.Pre`.
     *
     * The timing is structural rather than lucky: `startBattle` posts this event and only then calls
     * `startShowdown`, which packs each team by reading `effectedPokemon` fresh — so every Pre
     * subscriber has run before the engine has seen a team. Verified on dev 2026-07-28 against a live
     * RCT trainer; a recheck three seconds in still read the forced level, so nothing re-derives it.
     *
     * Only non-player actors are touched, and only for the player we stashed a level for. Reaching
     * the player's side would be the [com.cobblemonbridge.battle.GymBattleAdjustHook] problem — their
     * `BattlePokemon` aliases the real Pokémon, which is why that hook needs crash-safe NBT restore.
     * Here `effectedPokemon` is our own `safeCopyOf` clone and there is nothing to restore.
     */
    private fun applyWaveLevel(event: BattleStartedEvent.Pre) {
        val battle = event.battle
        val now = System.currentTimeMillis()
        for (actor in battle.actors.filterIsInstance<PlayerBattleActor>()) {
            val stash = pendingLevels.remove(actor.uuid) ?: continue
            val (waveLevel, stashedAt) = stash
            // A stash older than the TTL belongs to a battle that never started. Dropping it stops it
            // rescaling an unrelated battle the same player begins later.
            if (now - stashedAt > STASH_TTL_MS) continue
            for (opponent in battle.actors) {
                if (opponent is PlayerBattleActor) continue
                for (bp in opponent.pokemonList) {
                    bp.effectedPokemon.level = waveLevel
                }
            }
            CobblemonBridge.logger.debug(
                "roguelite: opponent team in battle {} forced to L{}", battle.battleId, waveLevel,
            )
            return
        }
    }

    // ─── Cleaning up the NPC ───────────────────────────────────────────────

    /**
     * Discard the trainer of any wave battle that is over.
     *
     * Polled rather than done on `BATTLE_VICTORY`, because victory is not the only way a wave ends:
     * a flee, a disconnect (Cobblemon stops a disconnected player's battle outright, producing no
     * result at all), an admin `/cobblemon battle close` and a Showdown error all end the battle
     * without one. This is roguelite's own reconcile loop applied to the one thing it will not do
     * for us — it holds no reference to an NPC somebody else spawned, and says so.
     *
     * An NPC left standing is not cosmetic: it survives into the next wave's arena stamp, and it is
     * an RCT trainer, which means a player can walk into one and start a battle that is not a wave.
     */
    private fun sweep() {
        if (npcs.isEmpty()) return
        if (++sweepCounter < SWEEP_INTERVAL_TICKS) return
        sweepCounter = 0
        val done = npcs.entries.filter { (battleId, _) ->
            val battle = BattleRegistry.getBattle(battleId)
            battle == null || battle.ended
        }
        for ((battleId, entity) in done) {
            npcs.remove(battleId, entity)
            if (!entity.isRemoved) entity.discard()
        }
    }
}
