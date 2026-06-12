package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gives gym, Battle Tower, Elite Four, and PvP-arena battles a custom battle theme.
 *
 * Cobblemon's `InitializeInstruction` sends `BattleMusicPacket(actor.battleTheme)` to each player
 * actor's own client at battle start, loops it, and auto-pauses the exploration MUSIC category for
 * the fight (resuming after). So we just set `battleTheme` on each player actor in
 * BATTLE_STARTED_PRE; the theme is resolved client-side by the cobblemon-soundtracks mod.
 *
 * Theme source — two routes:
 *  - **Trainer battles** (gyms, Elite Four, Battle Tower): [GymBattleGate] is the single universal
 *    gate at the head of `TrainerMob.startBattleWith` — it fires for BOTH right-click and
 *    force-battle/on-sight starts, which is why detection lives there rather than on
 *    `EntityInteract` (the latter misses the force-battle path, and tower NPCs carry no `gym_id`
 *    tag at all). The gate stamps the theme per player via [stashTrainerTheme] / [stashGymTheme];
 *    we consume it here within [STASH_TTL_MS], mirroring how [GymBattleAdjustHook] stashes caps.
 *  - **PvP arenas** (no trainer, so no gate): detected here by dimension (`multiworld:arena*`).
 *
 * Selection:
 *   - Elite Four → per-member theme (gym id 20–24).
 *   - Regular gyms (incl. challenge / flat-cap test gyms) and the Battle Tower → shared `battle.gym`.
 *   - Arenas → shared `battle.arena`.
 * Every other battle (wild, wandering trainers) is left to Cobblemon's default.
 */
object BattleThemeHook {
    private const val SOUNDTRACKS_NS = "cobblemon_soundtracks"
    private const val MULTIWORLD = "multiworld"

    /** Same freshness window [GymBattleAdjustHook] uses for its gate→battle-start stash. */
    private const val STASH_TTL_MS: Long = 5 * 60 * 1000L

    private fun theme(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(SOUNDTRACKS_NS, path)

    /** Gym id (20–24) -> per-member Elite Four battle theme. Must match the
     *  `battle.e4_*` ids in cobblemon-soundtracks' sounds.json. */
    private val E4_THEMES: Map<Int, ResourceLocation> = mapOf(
        20 to theme("battle.e4_alder"),    // E4 #1 — Alder
        21 to theme("battle.e4_cynthia"),  // E4 #2 — Cynthia
        22 to theme("battle.e4_ash"),      // E4 #3 — Ash
        23 to theme("battle.e4_lance"),    // E4 #4 — Lance
        24 to theme("battle.e4_n"),        // Champion — N
    )

    /** Shared rotating pool for regular gyms AND Battle Tower fights. */
    private val GYM_THEME: ResourceLocation = theme("battle.gym")

    /** Shared rotating pool for PvP arena battles. */
    private val ARENA_THEME: ResourceLocation = theme("battle.arena")

    /** Player -> (theme, stamp ms), set by [GymBattleGate] at battle-start, consumed at
     *  BATTLE_STARTED_PRE within [STASH_TTL_MS]. */
    private val pendingTheme: MutableMap<UUID, Pair<ResourceLocation, Long>> = ConcurrentHashMap()

    fun registerEvents() {
        // Priority.LOW so gating hooks that may cancel a battle on HIGH/NORMAL run first.
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.LOW) { event -> onBattleStarted(event) }
    }

    /** Stamp the theme for a gym / Elite Four trainer battle — per-member for the E4 (gym 20–24),
     *  the shared gym pool otherwise. Called by [GymBattleGate]. */
    fun stashTrainerTheme(uuid: UUID, gymId: Int) {
        val t = if (E4GauntletHook.isE4Gym(gymId)) (E4_THEMES[gymId] ?: GYM_THEME) else GYM_THEME
        pendingTheme[uuid] = t to System.currentTimeMillis()
    }

    /** Stamp the shared gym pool theme — Battle Tower fights and flat-cap (no gym_id) test gyms.
     *  Called by [GymBattleGate]. */
    fun stashGymTheme(uuid: UUID) {
        pendingTheme[uuid] = GYM_THEME to System.currentTimeMillis()
    }

    private fun onBattleStarted(event: BattleStartedEvent.Pre) {
        val now = System.currentTimeMillis()
        for (actor in event.battle.actors) {
            val playerActor = actor as? PlayerBattleActor ?: continue
            val player = playerActor.entity as? ServerPlayer ?: continue

            val stashed = pendingTheme.remove(player.uuid)
            val theme = when {
                stashed != null && now - stashed.second <= STASH_TTL_MS -> stashed.first
                // PvP arenas have no trainer (so no gate) — detect by dimension.
                else -> {
                    val dim = player.level().dimension().location()
                    if (dim.namespace == MULTIWORLD && dim.path.startsWith("arena")) ARENA_THEME else null
                }
            } ?: continue

            playerActor.battleTheme = theme
        }
    }
}
