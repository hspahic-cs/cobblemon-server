package com.cobblemonbridge.tower

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.battle.RctTrainerBridge
import com.cobblemonbridge.gymtp.WarpPos
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
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
 * Summon mechanics: the no-coords `rctmod trainer summon_persistent <id>` spawns at the player's
 * feet and needs a *player* command source, so it can't run headless. The POSITIONAL variant
 * `summon_persistent <id> <x> <y> <z>` takes an explicit BlockPos and needs no player — and the
 * command does the full persist + TrainerSpawner.register that a hand-rolled spawn misses. So
 * [rotate] queues the three summons and [advanceRotationPipeline] summons them STRICTLY ONE AT A
 * TIME — each materialising ([MATERIALIZE_TICKS]) before the next — then tags all three together.
 * Issuing a second summon, or tagging/unloading a floor, while an earlier trainer is still
 * materialising makes RCTmod's spawner drop the earlier one. Tower NPCs get
 * [BridgeTags.TOWER], [BridgeTags.TOWER_FLOOR].N, a flat [BridgeTags.LEVEL_CAP].50 (the same
 * asymmetric L50 down-cap the challenge gyms use, via [GymBattleAdjustHook]), and the
 * [com.cobblemonbridge.npc.EntityAnchor] tag. They deliberately do NOT get gym_id /
 * gym_challenge tags — see the note on [BridgeTags.TOWER].
 *
 * Chunk loading: the tower lives in `multiworld:spawn`, which is NOT always-loaded like the
 * overworld spawn chunks, so the unattended midnight rotation `forceload add`s each floor's
 * chunk for the duration of its summon and `forceload remove`s it after (the summoned trainer
 * is PersistenceRequired, so it survives the chunk unloading until a player arrives).
 *
 * Run rules (enforced by [com.cobblemonbridge.battle.TowerGauntletHook]):
 * floors in order 1→2→3, party locked for the run, items allowed, healing machine voids
 * the reward, losing/fleeing resets the run (retry until midnight), clearing all three
 * unhealed grants one rare key per player per day.
 */
object TowerManager {

    /** The 18 battle-tower leaders — challenge-track trainer id (datapack filename) + display name.
     *  The normal-track id is this with the `_challenge` suffix dropped (`bt_NN_type`). */
    val POOL: List<Pair<String, String>> = listOf(
        "bt_01_ground_challenge" to "Dusty",
        "bt_02_grass_challenge" to "Gardenia",
        "bt_03_fighting_challenge" to "Lee Sin",
        "bt_04_steel_challenge" to "Jarvis",
        "bt_05_fire_challenge" to "Zuko",
        "bt_06_electric_challenge" to "Stan",
        "bt_07_water_challenge" to "Kai",
        "bt_08_psychic_challenge" to "Juniper",
        "bt_09_dragon_challenge" to "Quinn",
        "bt_10_ghost_challenge" to "Grimm",
        "bt_11_bug_challenge" to "Flik",
        "bt_12_normal_challenge" to "Penny",
        "bt_13_poison_challenge" to "Koga",
        "bt_14_rock_challenge" to "Caesar",
        "bt_15_flying_challenge" to "Amos",
        "bt_16_ice_challenge" to "Tux",
        "bt_17_fairy_challenge" to "Flora",
        "bt_18_dark_challenge" to "Hobie",
        "bt_19_faker_challenge" to "Faker",
        "bt_20_oak_challenge" to "Professor Oak",
    )

    /** Server-local timezone — midnight in this zone is the rotation + reward-reset boundary. */
    private val ZONE: ZoneId = ZoneId.systemDefault()

    /** Tick poll cadence for the midnight check (~5s at 20 TPS). Coarse on purpose — the
     *  rotation only needs day granularity. */
    private const val CHECK_EVERY_TICKS = 100

    /** Ticks to let a freshly-summoned floor settle before tagging it. RCTmod's summon_persistent
     *  materialises the trainer over the following ticks (persistent-chunk-ticket load + a spawner
     *  pass), not instantly, so an immediate tag selector matches nothing. Critically, the floors
     *  are summoned STRICTLY ONE AT A TIME, each fully settling before the next: registering a
     *  second trainer while the first is still materialising makes RCTmod's spawner drop the first. */
    private const val MATERIALIZE_TICKS = 40

    private var tickCounter = 0

    /** A floor's trainer in the rotation pipeline: summoned, then (after settling) tagged.
     *  [difficulty] is "hard" (challenge variant, flat-L50) or "normal" (regular variant, uncapped);
     *  [displayName] is the shared leader name (e.g. "Tux") used for the name tag. */
    private data class PendingFloor(
        val pos: WarpPos, val floor: Int, val trainerId: String,
        val difficulty: String, val displayName: String,
    )

    /** Floors still to summon, processed strictly one at a time (see [MATERIALIZE_TICKS]). */
    private val spawnQueue = ArrayDeque<PendingFloor>()
    /** The floor that was just summoned and is settling before it gets tagged; null when idle. */
    private var settling: PendingFloor? = null
    private var settleCountdown = 0
    /** Ticks to wait after [rotate] force-loads the floor chunks before the first summon: RCTmod's
     *  summon searches for a spawnable position and mis-places the trainer far away if the target
     *  chunk's block data isn't loaded yet. */
    private var warmupCountdown = 0

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
        // While a rotation is in flight, drive its one-at-a-time pipeline and do nothing else.
        if (advanceRotationPipeline(event.server)) return

        if (++tickCounter < CHECK_EVERY_TICKS) return
        tickCounter = 0
        val s = store ?: return
        if (!s.floorsConfigured()) return
        val today = todayEpochDay()
        if (s.lastRotatedEpochDay() == today) return
        rotate(event.server, today)
    }

    /** Drive the rotation pipeline ONE FLOOR AT A TIME: summon a floor, let it materialise for
     *  [MATERIALIZE_TICKS], then immediately tag + reposition it (while it's fresh on its spot in
     *  the ticking force-loaded chunk, so [EntityAnchor] pins it before its AI can wander it off),
     *  then move to the next. Returns true while a rotation is in progress, so [onServerTick] skips
     *  its poll. (Forceloads are NOT removed — see [tagFloor].) */
    private fun advanceRotationPipeline(server: MinecraftServer): Boolean {
        // Let the freshly force-loaded floor chunks finish loading before the first summon.
        if (warmupCountdown > 0 && spawnQueue.isNotEmpty()) {
            warmupCountdown--
            return true
        }
        settling?.let { p ->
            if (--settleCountdown > 0) return true   // still materialising
            tagFloor(server, p)                       // settled + still on-spot → tag, pin, advance
            settling = null
            return true
        }
        spawnQueue.removeFirstOrNull()?.let { next ->
            summonFloor(server, next)
            settling = next
            settleCountdown = MATERIALIZE_TICKS
            return true
        }
        return false
    }

    /** Summon one floor's trainer via RCTmod's positional summon_persistent (explicit BlockPos,
     *  no player source needed). */
    private fun summonFloor(server: MinecraftServer, p: PendingFloor) {
        val src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
        val bx = Math.floor(p.pos.x).toInt()
        val by = Math.floor(p.pos.y).toInt()
        val bz = Math.floor(p.pos.z).toInt()
        server.commands.performPrefixedCommand(
            src, "execute in ${p.pos.world} run rctmod trainer summon_persistent ${p.trainerId} $bx $by $bz",
        )
        CobblemonBridge.logger.info("tower: floor {} ({}) summon issued ← {}", p.floor, p.difficulty, p.trainerId)
    }

    /** Tag, reposition, and harden the (now-settled) trainer at floor [p]. Called the moment it
     *  finishes materialising, while it's still on its summon spot in the ticking force-loaded
     *  chunk — so after the `tp` onto the exact floor, [EntityAnchor] captures THAT spot as its
     *  anchor on the next tick and pins it there before the AI can wander it off (the gym pattern).
     *
     *  The floor chunk stays force-loaded (re-added idempotently each rotation) — removing the
     *  forceload was observed to drop the trainer, and keeping the three chunks loaded is cheap and
     *  matches the tower being a fixed always-present fixture at spawn. The selector matches by
     *  TrainerId within a wide radius to tolerate any small drift during materialisation. */
    private fun tagFloor(server: MinecraftServer, p: PendingFloor) {
        val level = levelFor(server, p.pos.world) ?: run {
            CobblemonBridge.logger.warn("tower: floor {} — unknown dimension '{}'", p.floor, p.pos.world)
            return
        }
        // Find the trainer we summoned for this floor. It has AI and may have walked off its spot
        // while it sat untagged, so search a wide box; with strays cleared and floors summoned one
        // at a time, the nearest TrainerMob is ours.
        val box = AABB(p.pos.x - 64, p.pos.y - 32, p.pos.z - 64, p.pos.x + 64, p.pos.y + 32, p.pos.z + 64)
        val mob = level.getEntitiesOfClass(Mob::class.java, box) { RctTrainerBridge.trainerIdOf(it) == p.trainerId }
            .minByOrNull { it.distanceToSqr(p.pos.x, p.pos.y, p.pos.z) }
        if (mob == null) {
            CobblemonBridge.logger.warn("tower: floor {} — no trainer found to tag ({})", p.floor, p.trainerId)
            return
        }
        val hard = p.difficulty == BridgeTags.DIFFICULTY_HARD
        mob.addTag(BridgeTags.TOWER)
        mob.addTag("${BridgeTags.TOWER_FLOOR}.${p.floor}")
        mob.addTag("cobblemon_bridge.anchor.tower")
        mob.addTag("${BridgeTags.TOWER_DIFFICULTY}.${p.difficulty}")
        // HARD = flat-L50 down-cap vs the competitive challenge team. NORMAL = no cap (the player's
        // own team vs the regular gym team), so it gets no level_cap tag.
        if (hard) mob.addTag("${BridgeTags.LEVEL_CAP}.50")
        mob.isInvulnerable = true
        // Name tag distinguishes the two tracks: hard = red ☠[HARD] + glow, normal = green [Normal].
        mob.customName = net.minecraft.network.chat.Component.literal(
            if (hard) "§4§l☠ ${p.displayName} §r§c[HARD]" else "§a${p.displayName} §r§7[Normal]"
        )
        mob.isCustomNameVisible = true
        mob.setGlowingTag(hard)
        // Put it on the exact floor AND pin EntityAnchor's anchor there (it reads persistentData),
        // overriding whatever spot it had wandered to — otherwise it snaps back to the wrong place.
        mob.moveTo(p.pos.x, p.pos.y, p.pos.z, p.pos.yaw, 0f)
        mob.deltaMovement = Vec3.ZERO
        mob.persistentData.putDouble("cb_anchor_x", p.pos.x)
        mob.persistentData.putDouble("cb_anchor_y", p.pos.y)
        mob.persistentData.putDouble("cb_anchor_z", p.pos.z)
        CobblemonBridge.logger.info("tower: floor {} ({}) tagged + anchored ← {}", p.floor, p.difficulty, p.trainerId)
    }

    private fun levelFor(server: MinecraftServer, world: String): ServerLevel? =
        server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(world)))

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

        val leaders = leadersForDay(epochDay)

        // Cancel any rotation pipeline still in flight from a previous rotate.
        spawnQueue.clear()
        settling = null
        warmupCountdown = MATERIALIZE_TICKS

        // Forceload each floor's chunk, clear strays, and queue its summon. The tower is in
        // multiworld:spawn, which is NOT always-loaded like the overworld spawn chunks, so the
        // headless rotation needs the chunk loaded for the kill + summon to take effect; the
        // forceload is released by [tagFloor] once that floor is tagged. Summons are queued (not
        // issued here) and processed strictly one at a time — see [advanceRotationPipeline].
        val floorPositions = ArrayList<WarpPos>()
        for (index in leaders.indices) {
            val floor = index + 1
            val (challengeId, name) = leaders[index]
            // Both tracks use ShepskyDad's competitive L50 team. HARD = the challenge variant
            // `bt_NN_type_challenge` (`pe` AI, flat-L50 cap). NORMAL = `bt_NN_type` (same team, Run &
            // Bun `rb` AI, no cap) from ops/gen_battle_tower_teams.py — easier both because the AI is
            // weaker and because the player can out-level it.
            val normalId = challengeId.removeSuffix("_challenge")
            // Hard always (legacy spot falls back); normal only where an op has placed a normal spot.
            for ((difficulty, trainerId) in listOf("hard" to challengeId, "normal" to normalId)) {
                val pos = s.floor(floor, difficulty) ?: continue
                run("execute in ${pos.world} run forceload add ${Math.floor(pos.x).toInt()} ${Math.floor(pos.z).toInt()}")
                // Clear any stray already standing here so the spot ends up with exactly our summon.
                run("execute in ${pos.world} positioned ${pos.x} ${pos.y} ${pos.z} run kill @e[type=rctmod:trainer,distance=..6]")
                floorPositions.add(pos)
                spawnQueue.add(PendingFloor(pos, floor, trainerId, difficulty, name))
            }
        }
        // Sweep any tower-tagged leftover elsewhere in the floor dimensions (e.g. a moved floor).
        for (dim in floorPositions.map { it.world }.distinct()) {
            run("execute in $dim run kill @e[type=rctmod:trainer,tag=${BridgeTags.TOWER}]")
        }

        s.setLastRotatedEpochDay(epochDay)
        // New lineup invalidates every in-flight run (the NPCs just changed under them).
        com.cobblemonbridge.battle.TowerGauntletHook.resetAllRuns()
        CobblemonBridge.logger.info(
            "tower: rotation queued for epoch-day {} — {} (one floor at a time, ~{}t each)",
            epochDay, leaders.joinToString { it.second }, MATERIALIZE_TICKS,
        )
    }
}
