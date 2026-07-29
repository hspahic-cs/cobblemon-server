package com.cobblemonroguelite.progression

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/progression")

/**
 * The only place progression touches a live [Pokemon] — everything past this file is six numbers and
 * a species id.
 *
 * The split is the one [com.cobblemonroguelite.starter.StarterFactory] makes from the offer builder,
 * for the same reason: a `Pokemon` cannot be constructed outside a booted game, so anything that
 * takes one is code a unit test can never execute. Reading `pokemon.ivs` and `pokemon.shiny` is the
 * entire impure part of §2.15 and §2.17; the arithmetic that decides what those are *worth* lives in
 * [SpeciesProgress] and is tested there.
 *
 * ### What this is a hook into, and what it must not become
 *
 * [com.cobblemonroguelite.battle.RunCapture] calls [creditCatch] once per run catch. That call sits
 * next to the two mechanisms that keep a run catch *out* of the player's real data — the reclaim out
 * of party and PC, and [com.cobblemonroguelite.battle.RunDexGuard]'s Pokédex veto — and it is worth
 * being explicit that this is not a hole in either of them. Nothing here reads or writes the player's
 * party, PC or Pokédex; it writes a species id, a candy count and six integers into a file of our
 * own. That is precisely §1.1's restated contract: **value is sealed, progression is not.** If a
 * future change here ever needs to consult real storage, the boundary has moved and the decision
 * record has to move with it.
 */
object RunProgression {

    /**
     * How a caught Pokémon is turned into the species that gets the credit.
     *
     * **Decided 2026-07-28: the evolution line's root** (plan §2.17) — a caught Charizard candies
     * Charmander, which is PokéRogue's rule. It is also the only choice that makes candy
     * *accumulate*: crediting the caught species would scatter a line's earnings across three
     * separate ledgers, and a player would rarely reach a passive on any of them.
     *
     * Still swappable, but note a switch is **not retroactive** — candy already banked under one
     * key does not move to the other.
     *
     * `@Volatile` for [com.cobblemonroguelite.run.RunSettings]'s reason: set at setup from another
     * mod's thread, read from the server and battle threads.
     */
    @Volatile
    var speciesKey: ProgressionSpeciesKey = EvolutionLineRootKey

    /**
     * Credit a catch made inside a run.
     *
     * Called **after** the catch has been reclaimed out of the player's real storage and after the
     * wave has been confirmed catchable, and **before** the run is asked to take the Pokémon. The
     * order is deliberate on both sides:
     *
     * - After the reclaim, because a catch that leaked into real storage is a bug being reported, not
     *   a run catch being earned, and crediting it would reward the leak.
     * - Before the routing, because routing can legitimately fail — the run may have ended under the
     *   player, in which case [com.cobblemonroguelite.run.RunController.pokemonCaught] discards the
     *   Pokémon. The *catch* still happened. Progression is earned by playing, not by the bookkeeping
     *   afterwards working out, and the same argument is why a lost run keeps its candy.
     */
    fun creditCatch(server: MinecraftServer, player: UUID, pokemon: Pokemon) {
        val species = runCatching { speciesKey.keyFor(pokemon.species) }
            .onFailure { log.warn("roguelite: could not resolve a progression species for a catch", it) }
            .getOrNull() ?: return
        val progress = ProgressionStore.of(server).creditCatch(
            server = server,
            player = player,
            species = species,
            caughtIvs = ivsOf(pokemon),
            shinyVariant = shinyVariantOf(pokemon),
        )
        log.info(
            "roguelite: {} earned candy for {} in a run — now {} candy, IV floor {}",
            player, species, progress.candy, progress.floor.asList(),
        )
    }

    /**
     * Credit a cleared wave's friendship to the run party (§2.15's third candy source).
     *
     * Every member of the party the player still has, not only the ones that fought: a Pokémon on the
     * bench is still on the team, and rewarding only the leads would quietly make the mechanic a
     * bonus for whoever is fastest rather than a measure of playing with a species.
     *
     * The amount is per wave, not per turn, so this writes once between waves — see
     * [ProgressionStore.creditFriendship] for why that one does not flush unless it pays out.
     */
    fun creditWaveFriendship(server: MinecraftServer, player: UUID, party: List<Pokemon>) {
        val gained = ProgressionSettings.candy.friendshipPerWaveCleared
        if (gained <= 0 || party.isEmpty()) return
        val store = ProgressionStore.of(server)
        for (pokemon in party) {
            val species = runCatching { speciesKey.keyFor(pokemon.species) }.getOrNull() ?: continue
            store.creditFriendship(server, player, species, gained)
        }
    }

    /**
     * §2.17's floor for a species, for whoever is building a starter. [IvFloor.BASE] — a flat 10 —
     * until an in-run catch raises it, which is the answer for every species a new player picks.
     */
    fun ivFloor(server: MinecraftServer, player: UUID, species: ResourceLocation): IvFloor =
        ProgressionStore.of(server).ivFloor(player, species)

    /**
     * §2.13's starter cost for a species after the cost reductions this player has bought for it.
     *
     * Takes [baseCost] rather than looking it up: the cost table is the starter side's data and, per
     * §2.13, is PokéRogue's data that stays server-side. This module knows how to *discount* a cost
     * and deliberately does not know what any cost is.
     */
    fun effectiveStarterCost(
        server: MinecraftServer,
        player: UUID,
        species: ResourceLocation,
        baseCost: Int,
    ): Int = ProgressionStore.of(server).progressFor(player, species).effectiveStarterCost(baseCost)

    /** Whether this player has bought [species]' passive ability (§2.15). */
    fun passiveUnlocked(server: MinecraftServer, player: UUID, species: ResourceLocation): Boolean =
        ProgressionStore.of(server).progressFor(player, species).passiveUnlocked

    /**
     * A caught Pokémon's IVs as the primitive form §2.17 stores.
     *
     * `getOrDefault` and not `get`: the map is missing a stat only if something has gone wrong with
     * the Pokémon, and defaulting there costs the player a floor they might have earned rather than
     * throwing out of a capture.
     */
    private fun ivsOf(pokemon: Pokemon): IvFloor {
        val ivs = pokemon.ivs
        return IvFloor(
            hp = ivs.getOrDefault(Stats.HP),
            attack = ivs.getOrDefault(Stats.ATTACK),
            defence = ivs.getOrDefault(Stats.DEFENCE),
            specialAttack = ivs.getOrDefault(Stats.SPECIAL_ATTACK),
            specialDefence = ivs.getOrDefault(Stats.SPECIAL_DEFENCE),
            speed = ivs.getOrDefault(Stats.SPEED),
        )
    }

    /**
     * PokéRogue's shiny candy is tiered by *variant*, and Cobblemon has no variants — `Pokemon.shiny`
     * is a boolean. So every shiny here is tier 0 and worth [CandyRules.shinyCandy]`[0]`. The tier is
     * carried as an int anyway so that the rule does not have to change if a host mod ever supplies
     * one; see [CandyRules.shinyCandy].
     */
    private fun shinyVariantOf(pokemon: Pokemon): Int = if (pokemon.shiny) 0 else NOT_SHINY

    private const val NOT_SHINY = -1
}

/**
 * Which species a catch credits — the caught one, or the root of its evolution line.
 *
 * **Settled 2026-07-28: [EvolutionLineRootKey].** PokéRogue credits the *starter* species — a
 * caught Charizard candies Charmander, because Charmander is the thing you can start with and
 * therefore the thing candy is spent on.
 *
 * The deciding argument is accumulation. Crediting the caught species scatters a line's earnings
 * across as many ledgers as the line has stages, so a player who catches Charmander, Charmeleon and
 * Charizard across a run banks three separate piles and rarely reaches a passive on any of them.
 * Crediting the root means every catch in the line pays into the thing the candy is spent on.
 *
 * [CaughtSpeciesKey] remains implemented and tested — it is not obviously wrong for us, since
 * §2.15 lets a player start as any species they caught on the server, so a Charizard floor would be
 * directly usable in a way it is not in PokéRogue. Note the choice is **not retroactive**:
 * switching leaves candy already banked where it was banked.
 */
fun interface ProgressionSpeciesKey {
    fun keyFor(species: Species): ResourceLocation
}

/** Credit the species that was actually caught. Not the default; see [ProgressionSpeciesKey]. */
object CaughtSpeciesKey : ProgressionSpeciesKey {
    override fun keyFor(species: Species): ResourceLocation = species.resourceIdentifier
}

/**
 * Credit the base form of the evolution line, PokéRogue-style: a caught Charizard candies Charmander.
 *
 * The walk is depth-capped rather than trusting the data. `preEvolution` is datapack-supplied and an
 * addon (or a broken override) can produce a cycle, which would hang the capture thread — a species
 * chain nobody can see is not worth an unbounded loop, and stopping early merely credits the wrong
 * species rather than freezing the server.
 */
object EvolutionLineRootKey : ProgressionSpeciesKey {

    /** Deeper than any real line. Bulbasaur→Ivysaur→Venusaur is 3; nothing in Cobblemon exceeds 4. */
    private const val MAX_DEPTH = 8

    override fun keyFor(species: Species): ResourceLocation {
        var current = species
        var depth = 0
        var exhausted = true
        while (depth < MAX_DEPTH) {
            val previous = runCatching { current.preEvolution?.species }.getOrNull()
            if (previous == null || previous.resourceIdentifier == current.resourceIdentifier) {
                exhausted = false
                break
            }
            current = previous
            depth++
        }
        if (exhausted) {
            log.warn(
                "roguelite: evolution line for '{}' is deeper than {} — crediting '{}' and stopping. " +
                    "A cycle in preEvolution data is the likely cause.",
                species.resourceIdentifier, MAX_DEPTH, current.resourceIdentifier,
            )
        }
        return current.resourceIdentifier
    }
}
