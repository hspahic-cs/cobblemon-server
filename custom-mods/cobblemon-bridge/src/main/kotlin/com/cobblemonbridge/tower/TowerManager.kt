package com.cobblemonbridge.tower

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random

/**
 * Daily battle tower at spawn — three floors, three rotating challenge-mode gym leaders.
 *
 * Rotation: every server-local midnight (checked on a coarse tick poll), yesterday's tower
 * NPCs are killed (selector: `tag=cobblemon_bridge.tower`) and three leaders from the
 * 18-strong challenge pool are summoned at the op-configured floor positions
 * (`/tower setfloor 1..3`). The pick is a deterministic function of the epoch-day
 * ([leadersForDay]) — no stored roll, every restart agrees on today's lineup, and
 * [TowerStore.lastRotatedEpochDay] only prevents double-summoning within the same day.
 *
 * Summon mechanics mirror the proven aitest mcfunction pattern (`ops/gen_aitest_gyms.py`
 * output): `rctmod trainer summon_persistent <id>`, then tag the nearest matching
 * `rctmod:trainer` by TrainerId NBT, then data-merge Invulnerable + PersistenceRequired.
 * Tower NPCs additionally get [BridgeTags.TOWER], [BridgeTags.TOWER_FLOOR].N, and the
 * [com.cobblemonbridge.npc.EntityAnchor] tag. They deliberately do NOT get gym_id /
 * gym_challenge tags — see the note on [BridgeTags.TOWER].
 *
 * Chunk-loading assumption: the tower is at spawn, inside the always-loaded spawn chunks.
 * If it ever moves, rotation in unloaded chunks would silently no-op (both the kill
 * selector and the summon) — revisit with forceload if that happens.
 *
 * Run rules (enforced by [com.cobblemonbridge.battle.TowerGauntletHook]):
 * floors in order 1→2→3, party locked for the run, items allowed, healing machine voids
 * the reward, losing/fleeing resets the run (retry until midnight), clearing all three
 * unhealed grants one rare key per player per day.
 */
object TowerManager {

    /** The 18 challenge-mode leaders — trainer id (datapack filename) + display name. */
    val POOL: List<Pair<String, String>> = listOf(
        "gym_01_ground_challenge" to "Clay",
        "gym_02_grass_challenge" to "Gardenia",
        "gym_03_fighting_challenge" to "Korrina",
        "gym_04_steel_challenge" to "Byron",
        "gym_05_fire_challenge" to "Blaine",
        "gym_06_electric_challenge" to "Volkner",
        "gym_07_water_challenge" to "Crasher Wake",
        "gym_08_psychic_challenge" to "Sabrina",
        "gym_09_dragon_challenge" to "Drayden",
        "gym_10_ghost_challenge" to "Morty",
        "gym_11_bug_challenge" to "Viola",
        "gym_12_normal_challenge" to "Cheren",
        "gym_13_poison_challenge" to "Koga",
        "gym_14_rock_challenge" to "Grant",
        "gym_15_flying_challenge" to "Skyla",
        "gym_16_ice_challenge" to "Brycen",
        "gym_17_fairy_challenge" to "Valerie",
        "gym_18_dark_challenge" to "Marnie",
    )

    /** Server-local timezone — midnight in this zone is the rotation + reward-reset boundary. */
    private val ZONE: ZoneId = ZoneId.systemDefault()

    /** Tick poll cadence for the midnight check (~5s at 20 TPS). Coarse on purpose — the
     *  rotation only needs day granularity. */
    private const val CHECK_EVERY_TICKS = 100

    private var tickCounter = 0

    @Volatile
    private var store: TowerStore? = null

    fun init() {
        val file = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve("tower.json")
        store = TowerStore.load(file)
        CobblemonBridge.logger.info(
            "tower: loaded (floors configured: {}, last rotated epoch-day: {})",
            store?.floorsConfigured(), store?.lastRotatedEpochDay(),
        )
    }

    fun store(): TowerStore = store
        ?: error("TowerManager not initialized — CobblemonBridge should call TowerManager.init()")

    fun todayEpochDay(): Long = LocalDate.now(ZONE).toEpochDay()

    /**
     * Deterministic pick of 3 distinct leaders for [epochDay]. Seeding [Random] with the
     * epoch-day means every call — any restart, any replica — agrees on the day's lineup
     * without persisting the roll. Returned in floor order (index 0 = floor 1).
     */
    fun leadersForDay(epochDay: Long): List<Pair<String, String>> =
        POOL.shuffled(Random(epochDay)).take(3)

    /** Midnight poll — rotate when the epoch-day moves past the last rotation. Also covers
     *  server start (lastRotated is yesterday or null) and first-ever setup (null). */
    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        if (++tickCounter < CHECK_EVERY_TICKS) return
        tickCounter = 0
        val s = store ?: return
        if (!s.floorsConfigured()) return
        val today = todayEpochDay()
        if (s.lastRotatedEpochDay() == today) return
        rotate(event.server, today)
    }

    /** Despawn yesterday's tower NPCs and summon today's three. Idempotent per day via
     *  [TowerStore.lastRotatedEpochDay]; `force` (the `/tower rotate` path) re-runs anyway —
     *  the kill selector makes a re-run safe. */
    fun rotate(server: MinecraftServer, epochDay: Long = todayEpochDay(), force: Boolean = false) {
        val s = store()
        if (!s.floorsConfigured()) {
            CobblemonBridge.logger.warn("tower: rotate skipped — floors not configured (/tower setfloor 1..3)")
            return
        }
        if (!force && s.lastRotatedEpochDay() == epochDay) return

        val src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
        fun run(cmd: String) = server.commands.performPrefixedCommand(src, cmd)

        // Yesterday's leaders out. @e spans dimensions; tag scopes it to tower NPCs only.
        run("kill @e[type=rctmod:trainer,tag=${BridgeTags.TOWER}]")

        val leaders = leadersForDay(epochDay)
        for ((index, leader) in leaders.withIndex()) {
            val floor = index + 1
            val (trainerId, name) = leader
            val pos = s.floor(floor) ?: continue  // floorsConfigured() makes this unreachable
            val at = "execute in ${pos.world} positioned ${pos.x} ${pos.y} ${pos.z} run"
            val sel = "@e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:\"$trainerId\"}]"
            run("$at rctmod trainer summon_persistent $trainerId")
            run("$at tag $sel add ${BridgeTags.TOWER}")
            run("$at tag $sel add ${BridgeTags.TOWER_FLOOR}.$floor")
            run("$at tag $sel add cobblemon_bridge.anchor.tower")
            // Flat-L50 battles via AdjustLevelHook — challenge teams are all L50, and RCT's
            // adjustPlayerLevels is dead config (see the note in CobblemonBridge.init).
            run("$at tag $sel add ${BridgeTags.ADJUST_LEVEL}.50")
            run("$at data merge entity $sel {Invulnerable:1b,PersistenceRequired:1b}")
            CobblemonBridge.logger.info("tower: floor {} ← {} ({})", floor, name, trainerId)
        }

        s.setLastRotatedEpochDay(epochDay)
        // New lineup invalidates every in-flight run (the NPCs just changed under them).
        com.cobblemonbridge.battle.TowerGauntletHook.resetAllRuns()
        CobblemonBridge.logger.info(
            "tower: rotated for epoch-day {} — {}", epochDay, leaders.joinToString { it.second },
        )
    }
}
