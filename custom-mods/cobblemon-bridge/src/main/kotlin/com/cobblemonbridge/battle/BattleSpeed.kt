package com.cobblemonbridge.battle

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import net.neoforged.fml.loading.FMLPaths
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val log = LoggerFactory.getLogger("cobblemon_bridge/battle/speed")

/** Persisted shape of `config/cobblemon-bridge/runtime/battle_speed.json`. */
private data class BattleSpeedConfig(val multiplier: Float = 1.0f)

/**
 * Server-wide battle pacing multiplier. 1.0 = stock Cobblemon, 2.0 = twice as fast (every
 * pause halved), 0.5 = half speed. Applied by [BattleWaitDispatchMixin] and
 * [BattleAnimationDelayMixin]; changed live with `/battlespeed`.
 *
 * ## Why this works with two hooks
 *
 * All battle pacing in Cobblemon 1.7.3 is server-side and funnels through exactly two places:
 *
 *  1. **Battle flow** — `WaitDispatch(seconds)` computes `readyTime = now + seconds*1000` and
 *     `PokemonBattle.tick()` drains its dispatch deque while `canProceed()` is true. Every fixed
 *     pause in a battle is one of these: send-out (`SwitchInstruction` 1.5s + two 0.5s), faints,
 *     ability pop-ups, win banner. Scaling the constructor's seconds scales the whole flow.
 *     Because tick() drains in a `while` loop rather than one dispatch per tick, shortened
 *     delays genuinely run back-to-back instead of hitting a 20/sec ceiling.
 *
 *  2. **Move animations** — a move plays an `ActionEffectTimeline` of keyframes and the
 *     interpreter parks on an `UntilDispatch` until the timeline releases its holds. Every
 *     keyframe that waits does so via `SchedulingFunctionsKt.delayedFuture(seconds)`. Scaling
 *     those keeps animation pacing in step with the flow instead of the flow racing ahead of
 *     the animation it is supposed to be waiting for.
 *
 * ## The ceiling
 *
 * The client's Bedrock model animations are NOT scaled — they play at their authored rate no
 * matter what this is set to. So the server-side pauses shrink while the visuals do not, and
 * past roughly 2x moves start visibly clipping into each other and battle text scrolls faster
 * than it reads. [MAX_MULTIPLIER] allows more for experimentation, but 1.5–2.0 is the usable
 * band. Nothing here changes battle *logic* — turn order, damage and RNG are untouched, this is
 * purely how long the server sits on each step.
 *
 * Ranked note: the time bank counts real seconds, so faster battles mean more turns per bank.
 * Changing this materially changes how much thinking time a ranked match affords.
 */
object BattleSpeed {

    /** Below 1.0 is slower than stock — allowed, it makes A/B testing on dev easy. */
    const val MIN_MULTIPLIER = 0.25f

    /** Well past the usable band on purpose; the cap exists to stop typos, not to endorse 5x. */
    const val MAX_MULTIPLIER = 5.0f

    /**
     * A scaled delay never drops below this. Some instructions sequence purely by dispatch
     * order, but others rely on a nonzero gap for the client to have applied the previous
     * packet; collapsing everything to 0 makes health bars and swap animations visibly desync.
     * Delays already shorter than this are left alone rather than being lengthened to it.
     */
    private const val MIN_DELAY_SECONDS = 0.05f

    @Volatile
    var multiplier: Float = 1.0f
        private set

    /**
     * Set the first time either mixin calls [scale]. Reported by `/battlespeed` so a silently
     * unapplied mixin (both are `require = 0`, so a future Cobblemon refactor no-ops instead of
     * crash-looping the server) is diagnosable without reading the boot log.
     */
    @Volatile
    private var applied: Boolean = false

    private var store: BattleSpeedStore? = null

    /** Called from CobblemonBridge.onServerStarting. */
    fun init() {
        val file = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve("battle_speed.json")
        val s = BattleSpeedStore.load(file)
        store = s
        multiplier = clamp(s.get().multiplier)
        log.info("battle speed: {}x (file: {})", multiplier, file)
    }

    /**
     * Scales one pause. Called from the mixins on the battle thread for every delay in every
     * battle, so it stays allocation-free and reads one volatile.
     */
    @JvmStatic
    fun scale(seconds: Float): Float {
        applied = true
        val m = multiplier
        if (m == 1.0f || seconds <= 0f) return seconds
        return maxOf(seconds / m, minOf(seconds, MIN_DELAY_SECONDS))
    }

    /** True once a battle delay has actually passed through [scale] — i.e. the mixins applied. */
    fun isApplied(): Boolean = applied

    /** Sets and persists the multiplier. Returns the clamped value actually applied. */
    fun set(value: Float): Float {
        val clamped = clamp(value)
        multiplier = clamped
        store?.update { it.copy(multiplier = clamped) }
            ?: log.warn("battle speed set to {}x before init — not persisted", clamped)
        log.info("battle speed set to {}x", clamped)
        return clamped
    }

    private fun clamp(value: Float): Float =
        value.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
}

/**
 * Single-file JSON store for [BattleSpeed], same atomic-write pattern as
 * [com.cobblemonbridge.wild.WildStore].
 */
private class BattleSpeedStore private constructor(
    private val file: Path,
    private var data: BattleSpeedConfig,
) {
    fun get(): BattleSpeedConfig = data

    fun update(transform: (BattleSpeedConfig) -> BattleSpeedConfig) {
        data = transform(data)
        save()
    }

    private fun save() {
        file.parent.createDirectories()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(gson.toJson(data))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(file: Path): BattleSpeedStore {
            if (!file.exists()) return BattleSpeedStore(file, BattleSpeedConfig())
            return try {
                val parsed = gson.fromJson(file.readText(), BattleSpeedConfig::class.java)
                    ?: BattleSpeedConfig()
                BattleSpeedStore(file, parsed)
            } catch (e: JsonSyntaxException) {
                log.warn("battle_speed.json malformed; using defaults", e)
                BattleSpeedStore(file, BattleSpeedConfig())
            } catch (e: Exception) {
                log.warn("battle_speed.json load failed; using defaults", e)
                BattleSpeedStore(file, BattleSpeedConfig())
            }
        }
    }
}
