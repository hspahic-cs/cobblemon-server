package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gives gym, Elite Four, and PvP-arena battles a custom battle theme.
 *
 * Two music layers cooperate (see also the client-only `cobblemon-soundtracks`
 * mod, which handles per-dimension *exploration* music): Cobblemon's
 * `InitializeInstruction` sends `BattleMusicPacket(actor.battleTheme)` to each
 * player actor's own client at battle start, loops it, and auto-pauses the
 * exploration MUSIC category for the fight, resuming it when the battle ends. So
 * we only set `battleTheme` on each player actor in BATTLE_STARTED_PRE (the
 * setter just stores it pre-start; Cobblemon delivers it at init). This works
 * for PvP too — each player's own actor carries their theme.
 *
 * Theme selection:
 *   - **Elite Four** (gym 20–24): per-member, keyed on [E4GauntletHook.activeGym].
 *     Each member gets its own track (`battle.e4_*`).
 *   - **Regular gyms** (gym 1–19): one shared rotating pool (`battle.gym`) — the
 *     client picks a random track from the pool each battle. Detected by the
 *     `gym_id` tag on the trainer, stamped at interaction and consumed at battle
 *     start, mirroring [GymBattleAdjustHook]'s level-cap stash.
 *   - **Arenas** (PvP, no trainer): one shared rotating pool (`battle.arena`),
 *     keyed on dimension (`multiworld:arena*`).
 *   - Everything else (wild battles, etc.): Cobblemon's default.
 *
 * The themes are sound ids resolved client-side by `cobblemon-soundtracks`
 * (shipped in the .mrpack); we only reference them as [ResourceLocation]s, so
 * there's no compile/load-order coupling between the two mods.
 */
object BattleThemeHook {
    private const val SOUNDTRACKS_NS = "cobblemon_soundtracks"
    private const val MULTIWORLD = "multiworld"

    /** Same freshness window [GymBattleAdjustHook] uses for its interact→battle-start stash. */
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

    /** Shared rotating pool for all regular gym battles (gym 1–19). */
    private val GYM_THEME: ResourceLocation = theme("battle.gym")

    /** Shared rotating pool for PvP arena battles. */
    private val ARENA_THEME: ResourceLocation = theme("battle.arena")

    /** Player -> tick (ms) they last interacted with a *regular* gym trainer.
     *  Consumed (and cleared) at the next battle start within [STASH_TTL_MS]. */
    private val pendingGym: MutableMap<UUID, Long> = ConcurrentHashMap()

    fun registerEvents() {
        // Priority.LOW so the gating hooks that may cancel a battle on
        // HIGH/NORMAL (E4 party-stability, gym prereqs) run first — no point
        // theming a battle that's about to be cancelled.
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.LOW) { event -> onBattleStarted(event) }
    }

    /** Stamp regular-gym interactions. E4 is tracked separately (per-member) via
     *  [E4GauntletHook.activeGym], so we skip E4 gyms here. */
    @SubscribeEvent(priority = EventPriority.LOW)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide || event.isCanceled) return
        val player = event.entity as? ServerPlayer ?: return
        val gymId = BridgeTags.findGymId(event.target.tags) ?: return
        if (E4GauntletHook.isE4Gym(gymId)) return
        pendingGym[player.uuid] = System.currentTimeMillis()
    }

    private fun onBattleStarted(event: BattleStartedEvent.Pre) {
        val now = System.currentTimeMillis()
        for (actor in event.battle.actors) {
            val playerActor = actor as? PlayerBattleActor ?: continue
            val player = playerActor.entity as? ServerPlayer ?: continue
            val uuid = player.uuid

            val gymStamp = pendingGym.remove(uuid)
            val e4Gym = E4GauntletHook.activeGym(uuid)

            val theme = when {
                // Elite Four — per member.
                e4Gym != null -> E4_THEMES[e4Gym]
                // Regular gym — shared rotating pool (fresh interaction).
                gymStamp != null && now - gymStamp <= STASH_TTL_MS -> GYM_THEME
                // PvP arena (arena1, arena2, any future arenaN) — shared pool, by dimension.
                else -> {
                    val dim = player.level().dimension().location()
                    if (dim.namespace == MULTIWORLD && dim.path.startsWith("arena")) ARENA_THEME else null
                }
            } ?: continue

            playerActor.battleTheme = theme
        }
    }
}
