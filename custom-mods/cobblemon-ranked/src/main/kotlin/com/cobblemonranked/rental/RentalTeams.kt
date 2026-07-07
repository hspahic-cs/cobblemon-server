package com.cobblemonranked.rental

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.internal.ConfigPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Prebuilt "rental" teams offered as an alternative to bringing your own party in the ranked
 * team-select flow. Lets players try competitive singles without breeding/EV-training a squad.
 *
 * Each team is one recognised OU archetype (hyper-offense, balance, stall, trick-room) with exactly
 * one monument-obtainable legendary + one Mega, no Paradox (Paradox counts as legendary here), no
 * banned power-forms. Sets are deliberately de-tuned to keep rentals off "perfect": EVs cap at 168
 * per stat (⅔ of 252) and IVs are a flat 25 — measurably below a hand-optimised mon.
 *
 * A Mega Pokémon just holds its Mega Showdown stone (e.g. `mega_showdown:charizardite_x`) and carries
 * its BASE-form ability; the battle engine swaps to the Mega's form/ability when the player evolves.
 *
 * The four teams below are the built-in defaults. Operators/designers may override them by editing
 * `config/cobblemon-ranked/authored/rentals.json` (written on first boot); a malformed file falls
 * back to these defaults rather than crashing team-select.
 */
object RentalTeams {

    /** A flat 168-cap EV spread. Any stat left at 0 is unused. */
    data class RentalEVs(
        val hp: Int = 0, val atk: Int = 0, val def: Int = 0,
        val spa: Int = 0, val spd: Int = 0, val spe: Int = 0,
    )

    /** One rental Pokémon, compiled to a Cobblemon [PokemonProperties] string at build time. */
    data class RentalMon(
        val species: String,
        val form: String? = null,          // regional form showdown-id, e.g. "galar"
        val ability: String,               // showdown ability id, e.g. "supremeoverlord"
        val nature: String,
        val item: String,                  // namespaced held item, e.g. "cobblemon:leftovers"
        val moves: List<String>,           // up to 4 showdown move ids
        val evs: RentalEVs,
        val ivs: Int = 25,
        val level: Int = 50,
        val gender: String? = null,
    ) {
        fun toProperties(): String = buildString {
            append(species)
            form?.let { append(" form=").append(it) }
            append(" level=").append(level)
            append(" nature=").append(nature)
            append(" ability=").append(ability)
            append(" helditem=").append(item)
            gender?.let { append(" gender=").append(it) }
            append(" moves=").append(moves.joinToString(","))
            append(" hp_iv=$ivs attack_iv=$ivs defence_iv=$ivs special_attack_iv=$ivs special_defence_iv=$ivs speed_iv=$ivs")
            append(" hp_ev=${evs.hp} attack_ev=${evs.atk} defence_ev=${evs.def}")
            append(" special_attack_ev=${evs.spa} special_defence_ev=${evs.spd} speed_ev=${evs.spe}")
        }
    }

    /** A named rental team of (normally) six [RentalMon]. */
    data class RentalTeam(
        val id: String,
        val name: String,
        val archetype: String,             // one-line strategy blurb for the menu
        val difficulty: String = "Moderate", // Beginner | Moderate | Hard | Expert (menu badge)
        val icon: String,                  // namespaced vanilla item id for the menu button
        val members: List<RentalMon>,
    )

    @Volatile
    private var teams: List<RentalTeam>? = null

    fun all(): List<RentalTeam> = teams ?: DEFAULT_TEAMS
    fun byId(id: String): RentalTeam? = all().find { it.id == id }

    /**
     * Build live [Pokemon] for a rental team. Each mon is created fresh (never touches a player's
     * storage) and healed. Throws if any spec fails to compile so callers can abort the pick.
     */
    fun build(team: RentalTeam): List<Pokemon> = team.members.map { mon ->
        val pokemon = PokemonProperties.parse(mon.toProperties()).create()
        pokemon.heal()
        pokemon
    }

    fun load(configDir: Path) {
        val file = ConfigPaths.authored(configDir, "rentals.json")
        if (!file.exists()) {
            try {
                file.parent.createDirectories()
                file.writeText(GSON.toJson(DEFAULT_TEAMS))
            } catch (e: Exception) {
                CobblemonRanked.logger.warn("Couldn't seed default rentals.json: ${e.message}")
            }
            teams = DEFAULT_TEAMS
            return
        }
        teams = try {
            val type = object : TypeToken<List<RentalTeam>>() {}.type
            GSON.fromJson<List<RentalTeam>>(file.readText(), type)?.takeIf { it.isNotEmpty() } ?: DEFAULT_TEAMS
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to load rentals.json, using built-in defaults", e)
            DEFAULT_TEAMS
        }
        CobblemonRanked.logger.info("Loaded ${all().size} rental teams.")
    }

    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    // ---- Built-in default teams -------------------------------------------------------------

    private val DEFAULT_TEAMS: List<RentalTeam> = listOf(
        RentalTeam(
            id = "hyper_offense",
            name = "Hyper Offense",
            archetype = "All-out attack — set traps, then overwhelm before they can dig in.",
            difficulty = "Hard",
            icon = "minecraft:blaze_powder",
            members = listOf(
                RentalMon("glimmora", ability = "toxicdebris", nature = "timid", item = "cobblemon:focus_sash",
                    moves = listOf("stealthrock", "spikes", "mortalspin", "powergem"),
                    evs = RentalEVs(hp = 168, spa = 168, spe = 168)),
                RentalMon("charizard", ability = "blaze", nature = "jolly", item = "mega_showdown:charizardite_x",
                    moves = listOf("dragondance", "flareblitz", "dragonclaw", "roost"),
                    evs = RentalEVs(hp = 168, atk = 168, spe = 168)),
                RentalMon("chiyu", ability = "beadsofruin", nature = "timid", item = "cobblemon:choice_specs",
                    moves = listOf("overheat", "darkpulse", "psychic", "flamethrower"),
                    evs = RentalEVs(hp = 168, spa = 168, spe = 168)),
                RentalMon("kingambit", ability = "supremeoverlord", nature = "adamant", item = "cobblemon:black_glasses",
                    moves = listOf("swordsdance", "kowtowcleave", "suckerpunch", "ironhead"),
                    evs = RentalEVs(hp = 168, atk = 168, spd = 168)),
                RentalMon("dragapult", ability = "infiltrator", nature = "jolly", item = "cobblemon:choice_band",
                    moves = listOf("dragondarts", "uturn", "suckerpunch", "fireblast"),
                    evs = RentalEVs(hp = 168, atk = 168, spe = 168)),
                RentalMon("meowscarada", ability = "protean", nature = "jolly", item = "cobblemon:choice_band",
                    moves = listOf("flowertrick", "knockoff", "uturn", "tripleaxel"),
                    evs = RentalEVs(hp = 168, atk = 168, spe = 168)),
            ),
        ),
        RentalTeam(
            id = "balance",
            name = "Balance",
            archetype = "The safe, flexible all-rounder — the best team to learn on.",
            difficulty = "Beginner",
            icon = "minecraft:iron_sword",
            members = listOf(
                RentalMon("garchomp", ability = "roughskin", nature = "impish", item = "cobblemon:rocky_helmet",
                    moves = listOf("stealthrock", "earthquake", "spikes", "dragontail"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
                RentalMon("scizor", ability = "technician", nature = "adamant", item = "mega_showdown:scizorite",
                    moves = listOf("swordsdance", "bulletpunch", "uturn", "roost"),
                    evs = RentalEVs(hp = 168, atk = 168, spd = 168)),
                RentalMon("zapdos", ability = "static", nature = "bold", item = "cobblemon:heavy_duty_boots",
                    moves = listOf("voltswitch", "hurricane", "discharge", "roost"),
                    evs = RentalEVs(hp = 168, def = 168, spe = 168)),
                RentalMon("gholdengo", ability = "goodasgold", nature = "timid", item = "cobblemon:leftovers",
                    moves = listOf("nastyplot", "makeitrain", "shadowball", "recover"),
                    evs = RentalEVs(hp = 168, spa = 168, spe = 168)),
                RentalMon("slowking", form = "galar", ability = "regenerator", nature = "sassy", item = "cobblemon:assault_vest",
                    moves = listOf("futuresight", "sludgebomb", "flamethrower", "psyshock"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
                RentalMon("dondozo", ability = "unaware", nature = "impish", item = "cobblemon:leftovers",
                    moves = listOf("bodypress", "curse", "rest", "sleeptalk"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
            ),
        ),
        RentalTeam(
            id = "stall",
            name = "Stall",
            archetype = "Outlast everything — wall, poison, heal, repeat.",
            difficulty = "Expert",
            icon = "minecraft:shield",
            members = listOf(
                RentalMon("sableye", ability = "prankster", nature = "bold", item = "mega_showdown:sablenite",
                    moves = listOf("recover", "foulplay", "willowisp", "knockoff"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
                RentalMon("tinglu", ability = "vesselofruin", nature = "impish", item = "cobblemon:leftovers",
                    moves = listOf("stealthrock", "whirlwind", "earthquake", "ruination"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
                RentalMon("toxapex", ability = "regenerator", nature = "bold", item = "cobblemon:black_sludge",
                    moves = listOf("toxic", "recover", "haze", "surf"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
                RentalMon("chansey", ability = "naturalcure", nature = "bold", item = "cobblemon:eviolite",
                    moves = listOf("softboiled", "seismictoss", "toxic", "teleport"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
                RentalMon("corviknight", ability = "pressure", nature = "impish", item = "cobblemon:leftovers",
                    moves = listOf("defog", "roost", "bodypress", "uturn"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
                RentalMon("gliscor", ability = "poisonheal", nature = "impish", item = "cobblemon:toxic_orb",
                    moves = listOf("protect", "toxic", "earthquake", "spikes"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
            ),
        ),
        RentalTeam(
            id = "trick_room",
            name = "Trick Room",
            archetype = "Flip the speed rules so your slow bruisers strike first.",
            difficulty = "Moderate",
            icon = "minecraft:clock",
            members = listOf(
                RentalMon("cresselia", ability = "levitate", nature = "sassy", item = "cobblemon:leftovers",
                    moves = listOf("trickroom", "moonblast", "lunardance", "psychic"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
                RentalMon("hatterene", ability = "magicbounce", nature = "quiet", item = "cobblemon:leftovers",
                    moves = listOf("trickroom", "psyshock", "drainingkiss", "mysticalfire"),
                    evs = RentalEVs(hp = 168, spa = 168, def = 168)),
                RentalMon("mawile", ability = "intimidate", nature = "brave", item = "mega_showdown:mawilite",
                    moves = listOf("swordsdance", "playrough", "suckerpunch", "firefang"),
                    evs = RentalEVs(hp = 168, atk = 168, def = 168)),
                RentalMon("conkeldurr", ability = "guts", nature = "brave", item = "cobblemon:flame_orb",
                    moves = listOf("facade", "drainpunch", "machpunch", "knockoff"),
                    evs = RentalEVs(hp = 168, atk = 168, spd = 168)),
                RentalMon("ursaluna", ability = "guts", nature = "brave", item = "cobblemon:flame_orb",
                    moves = listOf("facade", "headlongrush", "earthquake", "swordsdance"),
                    evs = RentalEVs(hp = 168, atk = 168, spd = 168)),
                RentalMon("skeledirge", ability = "unaware", nature = "bold", item = "cobblemon:heavy_duty_boots",
                    moves = listOf("torchsong", "shadowball", "slackoff", "willowisp"),
                    evs = RentalEVs(hp = 168, def = 168, spd = 168)),
            ),
        ),
    )
}
