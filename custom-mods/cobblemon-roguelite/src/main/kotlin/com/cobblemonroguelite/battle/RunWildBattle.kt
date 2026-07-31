package com.cobblemonroguelite.battle

import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleRules
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty
import com.cobblemonroguelite.arena.ArenaResult
import com.cobblemonroguelite.arena.RunArenas
import com.cobblemonroguelite.composition.WavePlan
import com.cobblemonroguelite.integration.RunBattleAi
import com.cobblemonroguelite.integration.RunBattleAiRequest
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.run.RunSettings
import com.cobblemonroguelite.run.RunState
import com.cobblemonroguelite.wave.WildEncounterFactory
import com.cobblemonroguelite.wave.WildPools
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import kotlin.math.cos
import kotlin.math.sin

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * The ~160 wild waves of a run (§2.14), built and fought entirely inside this module.
 *
 * ### Why this is a real wild Pokémon standing in the arena
 *
 * §2.13 makes catching the party system: a run starts with one Pokémon and grows by catching, so a
 * wild wave that is not catchable is a run that cannot grow. Cobblemon's capture flow keys off a
 * `PokemonEntity` in the world carrying the battle's id — that is what a thrown ball looks for — so
 * the opponent is spawned as an ordinary wild entity rather than conjured straight into a battle
 * team. Nothing here marks it as ours; it is wild in exactly the sense the rest of Cobblemon means.
 *
 * ### What a capture does, and where the rest of it lives
 *
 * Cobblemon's capture flow puts the caught Pokémon in the **player's own party or PC** and marks the
 * species `CAUGHT` in their **real Pokédex**, because that is what catching means everywhere else.
 * Both are forbidden here — §1.1 keeps run Pokémon out of the player's storage, §2.15 keeps in-run
 * catching from unlocking starters — and neither is fixed in this file, because neither is a property
 * of how the wave was built. [RunCapture] takes the Pokémon back out of real storage and hands it to
 * the run; [RunDexGuard] stops the dex write happening at all. What this file owes them is the one
 * fact they cannot recover afterwards: whether the wave was catchable, carried into [RunBattles.track].
 *
 * ### Why the battle is assembled by hand instead of through `BattleBuilder.pve`
 *
 * `pve` is the same three objects with two of them wrong for us. It builds the wild actor with
 * Cobblemon's `RandomBattleAI` and offers no way to pass another, which would make §2.8's AI seam
 * unreachable on the only wave kind that ships working — a run would be 160 waves of an opponent
 * picking moves at random. It also sources the player's team from a `PartyStore` it heals and clones
 * by default. Assembling the sides directly costs the participant checks `pve` does, which are
 * re-stated below, and buys the AI seam and the party contract in [RunBattleParty].
 */
object RunWildBattle {

    /**
     * How far in front of the arena entry the opponent is put, in blocks.
     *
     * Not configurable, and deliberately not read from the player's position either. The player was
     * teleported to the entry a moment ago by [RunArenas.enter], but a handler that measured from
     * where they *are* would spawn the opponent wherever they had wandered — including outside the
     * arena box, in a world where they were not supposed to be. Measuring from the entry keeps the
     * opponent inside the box by construction, for any arena at least this wide.
     */
    private const val OPPONENT_DISTANCE = 6.0

    /**
     * **Minus one** means the wild Pokémon cannot flee. Not zero.
     *
     * This was 0f, on the stated belief that zero was the "cannot flee" sentinel, and the first wave
     * anybody fought ended instantly with the opponent fleeing. `PokemonBattle.checkFlee` reads:
     *
     * ```
     * fleeDistance == -1f            -> stays (this is the sentinel)
     * distanceToOpponent < fleeDistance -> stays
     * ```
     *
     * so 0f asks "is the distance less than zero", which nothing is — the actor was treated as having
     * fled before the first turn. The sentinel is a magic number in someone else's jar with no
     * constant to import, which is exactly the kind of value worth writing the mechanism down for.
     *
     * A wave that could be ended by stepping backwards is a wave with a free exit, and §2.10 spent a
     * whole decision making sure leaving a fight costs more than fighting it — so "never flees" is the
     * intent either way, and only the encoding was wrong.
     */
    private const val FLEE_DISTANCE = -1f

    /**
     * Start [plan] for [player]. False means the wave did not start and the run is untouched.
     *
     * Every refusal below is a *configuration* fault rather than a gameplay outcome, which is why
     * none of them ends or advances the run: an empty species pool, a species the server does not
     * have, an arena that did not come up. The player keeps their party and the wave stays owed.
     */
    fun start(server: MinecraftServer, player: ServerPlayer, run: RunState, plan: WavePlan): Boolean {
        // Re-taken rather than assumed. The controller called `enter` immediately before this, so the
        // chunks are already held and the build is already stamped — but `prepare` is also the only
        // public way to learn *where* the arena is, and it is idempotent, so asking is free and the
        // answer is the one thing a battle in an arena cannot proceed without.
        val placement = when (val arena = RunArenas.prepare(server, run)) {
            is ArenaResult.Failure -> {
                log.error(
                    "roguelite: wave {} for {} has no arena to fight in ({})",
                    plan.wave, player.gameProfile.name, arena.error,
                )
                return false
            }

            is ArenaResult.Success -> arena.value
        }
        val level = server.getLevel(player.level().dimension())
            ?.takeIf { it.dimension().location() == placement.dimension }
            ?: run {
                // The player is not in their arena, so spawning the opponent would put a wild Pokémon
                // wherever they actually are — the overworld, somebody's base. Refusing is the only
                // option that cannot leak a wave into the shared world.
                log.error(
                    "roguelite: {} is in {} but their arena is in {} — refusing wave {} rather than " +
                        "spawning the opponent outside it",
                    player.gameProfile.name, player.level().dimension().location(), placement.dimension, plan.wave,
                )
                return false
            }

        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            // `pve` checks this and we no longer go through it. Without the check the second battle
            // starts, both write to the same run, and the faints of one become the permadeath of the
            // other.
            log.warn("roguelite: {} is already in a battle — wave {} not started", player.gameProfile.name, plan.wave)
            return false
        }

        val wild = createOpponent(plan, run) ?: return false
        val team = RunBattleParty.teamFor(player, run) ?: return false

        val at = placement.entry(RunSettings.current.arena.entryOffset)
        val entity = spawnOpponent(level, wild, at.x + 0.5, at.y.toDouble(), at.z + 0.5)
            ?: return false

        val ai = RunBattleAi.create(RunBattleAiRequest(wave = plan.wave, opponent = RunOpponent.WILD))
        val result = BattleRegistry.startBattle(
            battleFormat = runFormat(),
            side1 = BattleSide(PlayerBattleActor(player.uuid, team)),
            side2 = BattleSide(PokemonBattleActor(wild.uuid, BattlePokemon(wild, wild), FLEE_DISTANCE, ai)),
        )
        val battle = (result as? SuccessfulBattleStart)?.battle
        if (battle == null) {
            // The opponent is already standing in the arena at this point, so it has to go — a wild
            // Pokémon left in a slot outlives the wave, is still there when the next one starts, and
            // is catchable by a player who is not in a battle at all.
            entity.discard()
            log.warn("roguelite: wave {} for {} was refused by Cobblemon", plan.wave, player.gameProfile.name)
            return false
        }

        // What `pve` does in its success handler, and the one line the capture flow depends on: a
        // thrown ball reads the entity's battle id to decide whether it is interrupting a battle or
        // catching something in the open. Without it §2.13's whole party system silently does nothing.
        entity.battleId = battle.battleId

        // `plan.catchable` and not `plan.kind == WILD`, for the reason [RunWaveBattles] routes off the
        // plan: a roster promotion can make this wave a boss, and the plan is the only thing that
        // knows. [RunCapture] reads it back to decide whether a thrown ball may reach the run party.
        RunBattles.track(server, battle, player.uuid, plan.wave, entity, catchable = plan.catchable)
        log.info(
            "roguelite: {} started wild wave {} against {} (level {})",
            player.gameProfile.name, plan.wave, wild.species.name, wild.level,
        )
        return true
    }

    /**
     * The Pokémon for this wave, or null when the pool cannot answer for it.
     *
     * The level comes off [plan] rather than off the generator even though the two are the same
     * number by construction — the composition delegates to the same curve on the same `(seed, wave)`
     * stream. Taking the plan's makes that agreement enforced rather than coincidental: if a later
     * layer adjusts a wave's level, this follows it instead of quietly disagreeing.
     */
    private fun createOpponent(plan: WavePlan, run: RunState) =
        WildPools.generator(RunSettings.composition.config.curve)
            .generate(run.seed, plan.wave, boss = plan.kind == RunOpponent.BOSS)
            ?.copy(level = plan.level)
            ?.let(WildEncounterFactory::create)
            ?.also { pokemon ->
                // §2.14's rule, applied from the plan and not re-derived. A wild wave is catchable and
                // this is a no-op on it; the flag exists so that if anything ever routes a
                // non-catchable wave down this path, the mistake is a Pokémon that cannot be caught
                // rather than a boss in somebody's party.
                if (!plan.catchable) UncatchableProperty.uncatchable().apply(pokemon)
            }

    private fun spawnOpponent(
        level: ServerLevel,
        pokemon: com.cobblemon.mod.common.pokemon.Pokemon,
        x: Double,
        y: Double,
        z: Double,
    ): PokemonEntity? {
        val yaw = Math.toRadians(RunSettings.current.arena.entryYaw.toDouble())
        // Placed along the arena's own entry facing rather than at a fixed offset, so an owner who
        // rotated their build gets the opponent in front of the player instead of behind them.
        val position = Vec3(x - sin(yaw) * OPPONENT_DISTANCE, y, z + cos(yaw) * OPPONENT_DISTANCE)
        return runCatching { pokemon.sendOut(level, position, illusion = null) }
            .onFailure { log.error("roguelite: could not spawn the wave opponent at {}", position, it) }
            .getOrNull()
    }

    /**
     * Gen 9 singles plus §2.11's bag ban.
     *
     * `Bag Clause` is Cobblemon's own rule and it is checked where an item is *offered* to a battle,
     * before the stack is consumed, so a player who tries gets told rather than silently losing a
     * potion. It is stripped out of the format before it reaches Showdown, so it costs the simulator
     * nothing. It is not the whole of §2.11 on its own — see [RunBagGuard] for the paths it does not
     * cover — but it is the half the client can see, and a greyed-out bag beats a rejected click.
     *
     * Copied from the shared `GEN_9_SINGLES` rather than mutating it: that instance is one object for
     * the entire server, and adding a rule to it in place would ban bag items in every battle on it,
     * ranked and wild alike.
     */
    private fun runFormat(): BattleFormat =
        BattleFormat.GEN_9_SINGLES.let { it.copy(ruleSet = it.ruleSet + BattleRules.BAG_CLAUSE) }
}
