package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.util.Random

private val log = LoggerFactory.getLogger("cobblemon_roguelite/starter")

/** What building a starting team came to. */
sealed interface StarterTeamResult {

    data class Built(val team: List<Pokemon>) : StarterTeamResult

    /**
     * [species] does not exist on this server. A data fault rather than a gameplay outcome, and the
     * caller must not turn it into a failed run start — the player has already been charged.
     */
    data class Unavailable(val species: ResourceLocation) : StarterTeamResult
}

/**
 * Turns the team a player bought into the Pokémon their run begins with.
 *
 * The split from [StarterCatalogueFactory] is the same one
 * [com.cobblemonroguelite.wave.WildEncounterFactory] makes from
 * [com.cobblemonroguelite.wave.WildWaveGenerator], for the same reason: choosing *what* is pure and
 * testable, building it needs a booted server's registries. Keeping the line here is what lets the
 * budget arithmetic and the IV roll be tested at all.
 */
object StarterFactory {

    /**
     * Build every member of [species], in order, or name the first one that does not resolve.
     *
     * All-or-nothing: a team that lost a member would be a player who paid points for a Pokémon they
     * never got, and there is no refund seam (§2.16) to give the points back with. Better to refuse
     * the whole selection and leave the pending start intact, which is what [StarterTeamResult] lets
     * the caller do.
     *
     * @param ivFloor per-species floor from §2.17, supplied by the caller because it is per *player*
     *   and this object deliberately never sees one — see [StarterProgression].
     * @param hiddenAbilityUnlocked whether this player has bought §2.27's unlock for that species.
     *   Per *player* for the same reason and supplied the same way. Note the caller resolves §2.17's
     *   evolution-line root; what this object grants is the ability of the species in front of it.
     */
    fun createTeam(
        species: List<ResourceLocation>,
        level: Int,
        runSeed: Long,
        ivFloor: (ResourceLocation) -> StarterIvFloor,
        hiddenAbilityUnlocked: (ResourceLocation) -> Boolean = { false },
    ): StarterTeamResult {
        val built = ArrayList<Pokemon>(species.size)
        species.forEachIndexed { slot, id ->
            val pokemon = create(id, level, runSeed, slot, ivFloor(id), hiddenAbilityUnlocked(id))
                ?: return StarterTeamResult.Unavailable(id)
            built += pokemon
        }
        return StarterTeamResult.Built(built)
    }

    /**
     * Build one party member, or null if [species] does not resolve on this server.
     *
     * `PokemonProperties.parse` does not fail on an unknown name — it silently yields the default
     * species — so the existence check has to happen here or a typo in the starter pool hands somebody
     * a Bulbasaur they did not buy.
     *
     * @param slot the member's index in the team, which is part of the IV seed. Without it two copies
     *   of a team would be fine but two *different* species in one team would still roll from
     *   distinct streams only by accident of their ids; with it the streams are distinct by
     *   construction.
     */
    fun create(
        species: ResourceLocation,
        level: Int,
        runSeed: Long,
        slot: Int,
        ivFloor: StarterIvFloor,
        hiddenAbilityUnlocked: Boolean = false,
    ): Pokemon? {
        // getByIdentifier, not getByName: the latter forces the `cobblemon` namespace, so an addon
        // species would report missing here and then resolve fine in the properties string.
        if (PokemonSpecies.getByIdentifier(species) == null) {
            log.warn("roguelite: starter pool names unknown species '{}'", species)
            return null
        }
        val properties = "species=$species level=$level"
        val pokemon = runCatching { PokemonProperties.parse(properties).create() }
            .onFailure { log.warn("roguelite: failed to create starter '{}'", properties, it) }
            .getOrNull() ?: return null

        // Applied after creation rather than written into the properties string. `create()` rolls IVs
        // itself and the string has no syntax for a *floor*, so the choice is between overwriting six
        // values here or reimplementing the roll in text; this way the seeded roll below is the only
        // thing that decides them, and it is the thing under test.
        StarterIvRoll.roll(runSeed, species, slot, ivFloor).forEach { (stat, value) ->
            runCatching { pokemon.setIV(stat, value) }
                .onFailure { log.warn("roguelite: could not set {} IV on starter '{}'", stat, species, it) }
        }

        // §2.27. Applied after creation for the same reason the IVs are: `create()` has already
        // rolled an ordinary ability, and the properties string has no syntax for "this exact one"
        // — Cobblemon's `hiddenability=true` picks at random among hidden entries and knows nothing
        // about the datapack override, so it is the wrong tool even though it is the obvious one.
        //
        // A failure here does not fail the starter. The unlock is permanent and the player keeps it;
        // a run that begins with the ordinary ability is a worse run that still plays, whereas
        // refusing the build would cost them a start they have already paid for (§2.16).
        if (hiddenAbilityUnlocked) {
            val granted = HiddenAbilityGrant.applyTo(pokemon)
            if (granted != null) {
                log.info("roguelite: starter '{}' begins with its unlocked ability '{}'", species, granted)
            }
        }

        // `create()` leaves the moveset empty on some paths, and a Pokémon with no moves takes the
        // battle to a state where neither side can act. Same guard as the wild encounter path.
        if (pokemon.moveSet.getMoves().isEmpty()) pokemon.initializeMoveset()
        return pokemon
    }
}

/**
 * The starter's IVs: a seeded roll, floored by §2.17's per-species high-water mark.
 *
 * ### Why this is seeded, when the catalogue no longer is
 *
 * The superseded offer draw was random and therefore needed a seed to stop a player rerolling it by
 * disconnecting. A budget catalogue is not random at all, so that use of the seed is gone — but §2.16
 * requires *everything* in a run to be derivable from the seed or persisted, and IVs were the
 * outstanding case. [StarterFactory] used to leave them to Cobblemon's unseeded roll, with a comment
 * saying that could not be fixed until the IV floor existed. It exists now ([StarterProgression]), so
 * this is where the seed went: the same run seed, the same team, the same six numbers, every time.
 *
 * That matters beyond tidiness. A starter is persisted the instant the run is created, so a
 * disconnect was never a reroll — but a *crash* between charging and creating was, and a player who
 * learned to pull the plug on a bad roll would have found it.
 *
 * ### Why the stream is salted, and salted per member
 *
 * `wave/` derives its own streams from the same run seed, and `java.util.Random` seeded with the same
 * value twice produces the same sequence — so an unsalted starter roll would be a visible function of
 * the wave-1 draw. The splitmix64 finaliser then spreads the salted value, because `Random`'s own
 * scrambler leaves nearby seeds producing correlated first draws, and consecutive run seeds are
 * exactly what a counter-based or time-based seed generator produces. Species and slot are mixed in
 * so that two members of one team do not share a stream, which would have them roll identical IVs.
 *
 * Changing either constant re-rolls the IVs of every starter not yet created. That is only visible to
 * a player mid-selection, but it is not free.
 */
object StarterIvRoll {

    /**
     * Roll each of the six permanent stats into `floor..31`.
     *
     * Rolling *within* the floor rather than rolling freely and taking the maximum is the difference
     * between a floor and a bonus: a player whose mark is 25 should never see a 4, and `max(roll,
     * floor)` would give them one three times in eight while still calling it a floor.
     */
    fun roll(
        runSeed: Long,
        species: ResourceLocation,
        slot: Int,
        floor: StarterIvFloor,
    ): Map<Stats, Int> {
        val rng = Random(starterSeed(runSeed, species, slot))
        // Fixed order, from CobblemonBaseStatTotal.STAT_ORDER — see there for why it is written out
        // rather than taken from Cobblemon's `Stats.PERMANENT` set.
        return CobblemonBaseStatTotal.STAT_ORDER.associateWith { stat ->
            val min = floor.floorFor(stat)
            min + rng.nextInt(StarterIvFloor.MAX_IV - min + 1)
        }
    }

    internal fun starterSeed(runSeed: Long, species: ResourceLocation, slot: Int): Long {
        var z = runSeed xor STARTER_SALT xor (species.toString().hashCode().toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor (slot.toLong() shl 48)
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private const val STARTER_SALT = 0x5354_4152_5445_5231L // "STARTER1"
}
