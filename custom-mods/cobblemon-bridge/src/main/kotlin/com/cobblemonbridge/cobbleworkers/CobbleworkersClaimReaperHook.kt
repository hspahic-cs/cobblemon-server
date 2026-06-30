package com.cobblemonbridge.cobbleworkers

import com.cobblemonbridge.CobblemonBridge
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.lang.reflect.Method

/**
 * Periodically resets Cobbleworkers' global, server-wide harvest state so workers (e.g. a Scizor
 * apricorn harvester) don't permanently stop claiming ripe targets after long server uptime. This
 * is the in-process equivalent of the server restart that has, until now, been the only fix —
 * without the restart.
 *
 * ## The two stall vectors this resets (verified against the 2.0.4 jar)
 *
 * **1. Leaked claims — `accieo.cobbleworkers.services.ClaimService` (the confirmed one).**
 * Holds a server-GLOBAL `blockClaims: Map<BlockPos, UUID>` (+ an `entityClaims` sibling). A worker
 * reserves a target with `tryClaim(pos)` (a `putIfAbsent`) and is meant to release it from the
 * `JobStateMachine`'s `handleCompletion`/`abort` paths. Those only run if the state machine reaches
 * them; any interruption that strands a worker mid-EXECUTING while holding a claim — entity unload
 * without the pasture `invalidate` firing, recall, an executor exception, navigation that never
 * arrives — leaks the entry. There is **no TTL or eviction** on `blockClaims`, and because
 * `tryClaim` is `putIfAbsent`, a leaked BlockPos can NEVER be re-claimed by any worker. It sits
 * ripe forever. Leaks accumulate with uptime until harvesting grinds down.
 *
 * **2. Wedged area scan — `PastureDeferredScanSystem` / `PastureEntityScanSystem` (belt-and-suspenders).**
 * Each keeps a per-pasture in-progress scan in `activeScans` and the cooldown gate in `lastScanTicks`.
 * The scan steps a fixed number of blocks per tick and only refreshes `TargetCache` as it goes. The
 * tick is try/caught and normally resumes next tick, but a block whose state read *persistently*
 * throws would pin the scan mid-way, so `TargetCache` never gets the targets past it — workers idle
 * with ripe apricorns present. Clearing both maps drops any wedged scan and the cooldown, so the
 * next pasture tick starts a clean full scan.
 *
 * ## Why this is safe
 * Every one of these is a global singleton with a public `clear()` that a restart effectively calls
 * (they all start empty). Clearing them mid-uptime self-heals within one scan cycle (~15s at our
 * `areaScanCooldown`):
 * - Clearing an *active* claim is harmless: a worker in EXECUTING keeps its own
 *   `JobContext.claimedTarget` and finishes; its later `release` just no-ops against the empty map.
 *   The only effect is a brief window where two idle workers might claim the same block — the first
 *   harvests it, the block is no longer ripe, the second's executor returns FAILURE and aborts. No
 *   duplication (harvesting is gated by live block state), just a little wasted pathing.
 * - Clearing the scan maps restarts a pasture's scan from scratch; `TargetCache` repopulates on the
 *   next cycle. Harvested-but-stale `TargetCache` entries are filtered live (must be a ripe
 *   apricorn), so we deliberately do NOT clear `TargetCache`/`InfrastructureCache` — leaving the
 *   deposit-chest cache intact avoids a needless deposit hiccup.
 *
 * Runs on `ServerTickEvent.Post` — between pasture ticks, on the server thread — so there's no
 * concurrent access with the scanner's own (non-thread-safe) maps.
 *
 * ## Robustness
 * Resolved entirely by reflection and fully fail-open, per [each target][TARGETS] independently. If
 * Cobbleworkers is absent, or a future version renames a class/method, that target disables itself
 * after one log line rather than throwing — a failed cross-mod call must never crash-loop boot. The
 * proper fix lives in the mod's claim/scan lifecycle (a TTL on claims, release-on-unload); this is a
 * mitigation until then.
 */
object CobbleworkersClaimReaperHook {

    /** How often to reset, in server ticks. Default 6000 ticks = 5 minutes. */
    private val resetIntervalTicks: Int =
        System.getProperty("cobbleworkers.claimReapTicks")?.toIntOrNull()?.coerceAtLeast(20) ?: 6000

    /** Global singletons to `clear()` each cycle. ClaimService is the confirmed fix; the scan
     *  systems are insurance against a wedged scan. Order is harmless (all independent). */
    private val TARGETS = listOf(
        "accieo.cobbleworkers.services.ClaimService",
        "accieo.cobbleworkers.systems.PastureDeferredScanSystem",
        "accieo.cobbleworkers.systems.PastureEntityScanSystem",
    )

    /** Per-target resolution: resolved (instance+method) or null once disabled. */
    private class Target(val name: String) {
        var instance: Any? = null
        var clear: Method? = null
        var disabled = false
    }

    private val targets: List<Target> by lazy { TARGETS.map { Target(it) } }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        if (event.server.tickCount % resetIntervalTicks != 0) return
        for (t in targets) reset(t)
    }

    private fun reset(t: Target) {
        if (t.disabled) return
        try {
            if (t.clear == null) {
                val cls = Class.forName(t.name)
                // Kotlin `object` → public static final INSTANCE field.
                t.instance = cls.getField("INSTANCE").get(null)
                t.clear = cls.getMethod("clear")
                CobblemonBridge.logger.info(
                    "Cobbleworkers reaper: {} resolved — clearing every {} ticks.",
                    t.name, resetIntervalTicks,
                )
            }
            t.clear!!.invoke(t.instance)
            CobblemonBridge.logger.debug("Cobbleworkers reaper: cleared {}", t.name)
        } catch (_: ClassNotFoundException) {
            t.disabled = true
            CobblemonBridge.logger.info(
                "Cobbleworkers reaper: {} not present — that reset is inactive.", t.name,
            )
        } catch (e: Throwable) {
            t.disabled = true
            CobblemonBridge.logger.warn(
                "Cobbleworkers reaper: disabling {} after an error invoking clear() — " +
                    "that mitigation is off (harmless; mod may have changed).",
                t.name, e,
            )
        }
    }
}
