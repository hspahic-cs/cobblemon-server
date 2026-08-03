package com.cobblemonranked.config

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.internal.ConfigPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * A single teleport point for a ranked-match arena.
 *
 * `world` is a namespaced dimension id (`minecraft:overworld`, `minecraft:the_nether`, etc.).
 * `yaw`/`pitch` set the player's facing on arrival — typically each player faces the other.
 */
data class ArenaPos(
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
    val world: String = "minecraft:overworld",
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f
)

data class RankedConfig(
    val startingElo: Int = 1200,
    /** Floor below which ELO can't drop, period — applies to both decay and normal battle
     *  losses. Held at the historical 1000. 0.7.8 briefly raised this to 1200 conflating it
     *  with the "decay target = 1200" goal; the side effect was that anyone with current ELO
     *  below 1200 got clamped UP to 1200 on their next battle, so a loss read as a gain.
     *  Decay's target/opponent is [startingElo] (= 1200) via `EloCalculator.decayElo`, which
     *  is what actually implements "decay drags inactive players toward 1200" — independent
     *  of the battle-loss floor. */
    val minimumElo: Int = 1000,
    val kFactor: Int = 32,
    val levelCap: Int = 50,
    val maxLegendaries: Int = 1,
    /** Show the "Rent a Team" button in team-select, letting players battle with a prebuilt
     *  competitive team instead of their own party. Flip to false to remove rentals from ranked
     *  (e.g. if the ladder homogenises into mirror rental matches) without a code change. */
    val allowRentalsInRanked: Boolean = true,
    /** Wiki page explaining the rental teams. Surfaced as a clickable link via `/ranked guide`
     *  and hinted in the rental menu. Blank hides the guide command/hint. */
    val rentalGuideUrl: String = "https://hspahic-cs.github.io/cobblemon-server/rental-teams.html",
    /** Master switch for player-drafted custom rentals (`/ranked draft`, "My Drafts" in the
     *  rental picker). Requires [allowRentalsInRanked] for the picker to be reachable at all.
     *  See docs/rental-drafts-plan.md. */
    val allowDraftTeams: Boolean = true,
    /** Draft slots are PERMANENT purchases sold by the market's Upgrades vendor (which reads
     *  this ladder through `DraftTeams.slotCost`): unlocking your Nth slot costs entry N, once.
     *  Past the end of the list the price continues by the final step — the default's last two
     *  entries are equal, so slots 6+ stay flat at 100k. Each purchase also grants one free
     *  instant-swap credit. Deleting a draft empties the slot but you keep it, and filling any
     *  unlocked empty slot is FREE — the scarce resources are slots and the identity cooldown,
     *  not fill fees. */
    val draftSlotCosts: List<Int> = listOf(20_000, 40_000, 60_000, 100_000, 100_000),
    /** Flat fee per TUNE edit — one keeping at least 4 of the team's 6 species. Tunes are never
     *  cooldown-gated. */
    val draftEditCost: Int = 5_000,
    /** Price of an INSTANT team swap (an edit keeping fewer than 4 species) while the slot's
     *  identity cooldown is still running — paying (or spending a slot's free swap credit)
     *  skips the wait. Once the cooldown has elapsed, swapping (or delete + refill) is free:
     *  wait = free, pay = instant. */
    val draftSwapCost: Int = 20_000,
    /** Hours before a slot can take a NEW team identity for free (swap-edit or delete + create
     *  — deleting leaves the freed slot locked for the remainder). Paying [draftSwapCost] or a
     *  swap credit bypasses it. Tune-edits are exempt. Default 15 days. */
    val draftIdentityCooldownHours: Int = 360,
    /** How many slots a player can ever unlock. The rental picker shows at most 18 (two rows). */
    val maxDraftSlots: Int = 10,
    val forcesPerDayPerPair: Int = 1,
    /** Off by default in 0.7.8. Decay is paused while we tune; flip to `true` (and ensure
     *  [minimumElo] is the desired decay floor) to re-enable. */
    val decayEnabled: Boolean = false,
    val leaderboardSize: Int = 10,
    /**
     * Arena 1 — primary battlefield. `arenaPos1` is where player 1 lands, `arenaPos2` is
     * where player 2 lands. Both must be set for arena 1 to be usable.
     */
    val arenaPos1: ArenaPos? = null,
    val arenaPos2: ArenaPos? = null,
    /**
     * Arena 2 — secondary battlefield used when arena 1 is already in use by another
     * ranked match. Same shape: `arena2Pos1` for player 1, `arena2Pos2` for player 2.
     */
    val arena2Pos1: ArenaPos? = null,
    val arena2Pos2: ArenaPos? = null,
    /**
     * Overflow spawn point. When both arenas are in use the next concurrent match teleports
     * both players to this single position (multiple matches can share — no mutex). Distinct
     * from cobblemon-bridge's `/setspawn` so each subsystem can pick its own coords.
     */
    val spawnPos: ArenaPos? = null,
) {
    fun isArenaConfigured(): Boolean = arenaPos1 != null && arenaPos2 != null
    fun isArena2Configured(): Boolean = arena2Pos1 != null && arena2Pos2 != null
    fun isSpawnConfigured(): Boolean = spawnPos != null
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): RankedConfig {
            // config.json mixes design (ELO knobs) and per-world data (arena coords).
            // Treated as runtime so operator edits via /ranked admin setarena don't
            // get overwritten by deploys.
            val file = ConfigPaths.runtime(configDir, "config.json")
            if (!file.exists()) {
                val default = RankedConfig()
                save(configDir, default)
                return default
            }
            return try {
                gson.fromJson(file.readText(), RankedConfig::class.java)
            } catch (e: Exception) {
                CobblemonRanked.logger.error("Failed to load config, using defaults", e)
                RankedConfig()
            }
        }

        fun save(configDir: Path, config: RankedConfig) {
            val file = ConfigPaths.runtime(configDir, "config.json")
            file.parent.createDirectories()
            file.writeText(gson.toJson(config))
        }
    }
}
