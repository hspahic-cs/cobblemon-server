package com.cobblemonroguelite.arena

import com.cobblemonroguelite.CobblemonRoguelite
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/**
 * Everything about where a run is fought.
 *
 * ### Why a generated grid is the default and hand-built arenas are the override
 *
 * The design weighed five ways to give a run somewhere private to happen and picked one static
 * mod-declared dimension sliced into a coordinate grid, because that is the only option that works
 * with no owner action at all — including in single-player, on a world the mod has never seen. Every
 * other candidate either costs a ticking `ServerLevel` per run, puts the arena somewhere players can
 * walk to, or requires somebody to build N arenas before the mode works once.
 *
 * [fixedArenas] is the escape hatch for the case where an owner *has* built arenas and wants them
 * used — ours, later, will point at `multiworld:` rooms. It is deliberately a configuration and not
 * a second code path: setting it replaces the grid wholesale (see [layout]), so there is never a
 * question of which of the two is in force.
 *
 * @property dimension the arena dimension. Defaults to the one this mod ships as a datapack in its
 *   own jar, which NeoForge loads with no owner action and which attaches to worlds that already
 *   exist. Configurable because [fixedArenas] may live somewhere else entirely.
 * @property spacing blocks between adjacent slot origins on both axes. 1024 is past any render
 *   distance and — the load-bearing part — vastly past Mega Showdown's [POWER_SPOT_RANGE], so no
 *   arena's `power_spot` can reach into the arena next door. Lowering this below the box footprint
 *   would overlap arenas and is refused outright; lowering it below twice the power-spot range would
 *   quietly hand a neighbouring run a Dynamax they did not earn, so that is refused too.
 * @property gridWidth slots per row. Only affects which coordinates a slot index maps to, so it is
 *   free to change on a server with no live runs and must **not** be changed on one with them: a
 *   run's slot is stored as an index, and re-indexing moves an occupied arena out from under it.
 * @property maxConcurrentRuns the grid's capacity, and the reason disk growth is bounded by a number
 *   an operator picked rather than by runs ever played — slots are reused. Ignored when
 *   [fixedArenas] is set, where capacity is simply how many arenas were listed.
 * @property floorY the Y the template's corner is placed at. The arena dimension is void, so this is
 *   an arbitrary choice rather than terrain-derived; it exists so an owner pointing at a real world
 *   can match their build.
 * @property box the volume a slot owns. Declared here rather than read from the template because
 *   slot spacing has to be validated before any template is loaded, and because the sweep needs to
 *   know what to clear even when the template that dirtied it was a different one (§2.19 re-stamps
 *   at band boundaries, and a later band's build may be smaller than an earlier one's).
 * @property entryOffset where the player lands, relative to the slot origin. Defaults to the middle
 *   of [box], one block up.
 * @property entryYaw which way they face on arrival.
 * @property templates which structure is stamped, per wave band.
 * @property fixedArenas option D. Empty means the generated grid.
 * @property settleTicks how long a caller must wait after [ArenaChunks.hold] before summoning
 *   anything into a slot. Zero is wrong even though the chunks are loaded synchronously: the tower
 *   learned that RCTmod's `summon_persistent` materialises over the following ticks and an immediate
 *   selector matches nothing. 40 is the tower's figure, kept because it is the one that was observed
 *   to work rather than the one that was reasoned to.
 */
data class ArenaConfig(
    val dimension: ResourceLocation = DEFAULT_DIMENSION,
    val spacing: Int = 1024,
    val gridWidth: Int = 8,
    val maxConcurrentRuns: Int = 32,
    val floorY: Int = 64,
    val box: ArenaBox = ArenaBox(),
    val entryOffset: BlockPos = BlockPos(box.width / 2, 1, box.depth / 2),
    val entryYaw: Float = 0f,
    val templates: ArenaTemplates = ArenaTemplates(),
    val fixedArenas: List<ArenaOrigin> = emptyList(),
    val settleTicks: Int = 40,
) {
    init {
        require(gridWidth >= 1) { "gridWidth must be at least 1, was $gridWidth" }
        require(maxConcurrentRuns >= 1) { "maxConcurrentRuns must be at least 1, was $maxConcurrentRuns" }
        // Two separate floors, both fatal and neither visible in play. Overlapping boxes mean one
        // run's re-stamp deletes another run's arena mid-battle; boxes that clear that bar but sit
        // within twice the power-spot range mean a player standing in arena N can Dynamax off arena
        // N+1's spot, which looks like the gimmick confinement simply not working.
        val footprint = maxOf(box.width, box.depth)
        require(spacing > footprint) {
            "spacing ($spacing) must exceed the arena footprint ($footprint) or slots overlap"
        }
        require(spacing >= POWER_SPOT_RANGE * 2) {
            "spacing ($spacing) is within twice Mega Showdown's power spot range ($POWER_SPOT_RANGE), " +
                "so one arena's power spot would reach the next"
        }
        require(settleTicks >= 0) { "settleTicks must not be negative, was $settleTicks" }
    }

    /**
     * The layout in force. Built on demand and cached one level up, in
     * [com.cobblemonroguelite.run.RunSettings.arenaLayout], which rebuilds it whenever the config is
     * replaced — the same arrangement [com.cobblemonroguelite.composition.WaveComposition] has, and
     * for the same two reasons: a layout is stateless, and the spawn suppressor reads one per spawn.
     * Caching it on this instance instead would be equivalent; caching it in a `var` that nothing
     * invalidates is the version that quietly keeps serving the arenas configured at boot.
     */
    fun layout(): ArenaLayout =
        if (fixedArenas.isEmpty()) {
            SlotGrid(dimension, spacing, gridWidth, maxConcurrentRuns, floorY, box)
        } else {
            FixedArenas(dimension, fixedArenas, box)
        }

    companion object {
        val DEFAULT_DIMENSION: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(CobblemonRoguelite.MOD_ID, "arena")

        /**
         * Mega Showdown's `powerSpotRange`, duplicated as a constant rather than read from their
         * config. Mega Showdown is a **soft** dependency — the mode has to build and run without it —
         * so reading their config would turn a confinement check into a hard link. Duplicating it
         * means a server that raises the range has to raise [spacing] too; the default is fifty times
         * the distance, so that is a theoretical problem rather than a live one.
         */
        const val POWER_SPOT_RANGE = 20
    }
}

/**
 * The volume one slot owns, measured from the slot origin at its minimum corner.
 *
 * Not derived from the template's own size, deliberately. The stamp has to clear before it places
 * (§2.19 re-stamps a *different* template at each wave band, and a smaller one leaves the previous
 * band's walls standing), and "clear the previous template's volume" is not knowable — the previous
 * template may have come from a config that has since been edited. Clearing a declared, fixed volume
 * is knowable, and it is the same volume the entity sweep and the spawn suppressor reason about.
 */
data class ArenaBox(val width: Int = 64, val height: Int = 32, val depth: Int = 64) {
    init {
        require(width >= 1 && height >= 1 && depth >= 1) { "arena box must be at least 1x1x1, was $this" }
        // The clear pass is one setBlock per block in here, on the server thread, once per stamp.
        // 64x32x64 is ~131k blocks and is already the upper end of what belongs in a tick; a config
        // typo of 6400 would be 1.3 billion and would present as the server hanging on run start.
        require(width.toLong() * height * depth <= MAX_VOLUME) {
            "arena box $this is ${width.toLong() * height * depth} blocks; the clear pass runs on the " +
                "server thread and is capped at $MAX_VOLUME"
        }
    }

    companion object {
        const val MAX_VOLUME = 512L * 1024L
    }
}

/**
 * One hand-built arena, for [ArenaConfig.fixedArenas].
 *
 * @property origin the minimum corner of the arena's box, in the same convention the grid uses, so
 *   the sweep and the entry offset behave identically either way. An owner who thinks in terms of
 *   "where the player stands" has to subtract [ArenaConfig.entryOffset] once, which is the cost of
 *   there being exactly one convention rather than two.
 * @property dimension null means [ArenaConfig.dimension]. Per-arena because the whole point of this
 *   override is pointing at arenas that already exist, and ours are one dimension each.
 */
data class ArenaOrigin(val origin: BlockPos, val dimension: ResourceLocation? = null)

/**
 * Which structure is stamped into a slot at a given wave.
 *
 * ### Ids, not structures
 *
 * This resolves to a [ResourceLocation] and stops — the same split
 * [com.cobblemonroguelite.composition.RewardRouting] makes, and for the same reason: a template that
 * is missing at stamp time should be reported by the layer that actually looked in the resource
 * manager and found nothing, not guessed at here.
 *
 * ### The default id names a file this mod does not ship
 *
 * `data/cobblemon_roguelite/structure/arena.nbt` is content — an actual build, with a `power_spot`
 * in it if Mega Showdown is installed — and content is not this module's to invent. So the default
 * points at a well-known path and [ArenaStamper] fails **loudly** when nothing is there. The
 * alternative, placing nothing and carrying on, drops a player into a void dimension with no floor.
 *
 * @property bands checked in order, first match wins, so an author reads precedence top-down. Same
 *   convention as [com.cobblemonroguelite.composition.RewardBand], since an author will meet both.
 */
data class ArenaTemplates(
    val default: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CobblemonRoguelite.MOD_ID, "arena"),
    val bands: List<ArenaBand> = emptyList(),
) {
    fun templateFor(wave: Int): ResourceLocation = bands.firstOrNull { it.covers(wave) }?.template ?: default
}

/**
 * A wave range that gets its own arena build (§2.19).
 *
 * @property maxWave inclusive, null for open-ended.
 */
data class ArenaBand(val minWave: Int, val maxWave: Int? = null, val template: ResourceLocation) {
    init {
        require(minWave >= 1) { "minWave must be at least 1, was $minWave" }
        require(maxWave == null || maxWave >= minWave) {
            "maxWave ($maxWave) is before minWave ($minWave), so this band could never match"
        }
    }

    fun covers(wave: Int): Boolean = wave >= minWave && (maxWave == null || wave <= maxWave)
}
