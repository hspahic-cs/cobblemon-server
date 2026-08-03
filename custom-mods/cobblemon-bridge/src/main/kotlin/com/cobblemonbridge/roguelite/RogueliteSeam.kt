package com.cobblemonbridge.roguelite

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One trainer or boss wave, restated in types this mod owns.
 *
 * Everything [RogueliteSeam] hands out is a Minecraft or Cobblemon type or one of ours — never a
 * `com.cobblemonroguelite.*` one. That is the whole point of the seam class: exactly one file in
 * this mod knows roguelite's class names, and the provider that uses them reads like ordinary code
 * instead of like reflection.
 */
data class TrainerWave(
    val wave: Int,
    val level: Int,
    /** `TRAINER` or `BOSS`, carried as a string for the log line. The boss multiplier is already in
     *  [level] — the curve applied it — so nothing here branches on this. */
    val kind: String,
    val trainerId: ResourceLocation,
    /**
     * The opponent's team as Cobblemon `PokemonProperties` strings, or **empty for an authored fight**.
     *
     * Roguelite decides which of the two a wave is (its plan §2.30: a roster generates most trainers
     * from their signature species and leaves the Elite Four and the champion hand-made), and this
     * side does as it is told. Empty must therefore mean "fight the RCT trainer's authored team" —
     * the behaviour of every wave before generated teams existed — and never "no team".
     *
     * Strings, not objects, because the whole seam is reflective: one call over the boundary returning
     * a `List<String>` beats walking four roguelite types by name. Each string is complete — species,
     * aspects, level, held item — so building the Pokémon is a `PokemonProperties.parse().create()`
     * and nothing else.
     */
    val teamProperties: List<String> = emptyList(),
)

/**
 * Everything this mod needs from `cobblemon-roguelite`, resolved by reflection.
 *
 * ### Why reflection and not a compile-time dependency
 *
 * This is the first time one of our mods links against another, and the linking method is
 * constrained by how the repo builds rather than by taste. Each `custom-mods/<mod>/` is an
 * independent Gradle build with no `project(":...")` available, and `.github/actions/build-modpack`
 * builds them by iterating a bash associative array — **unordered** — while caching each mod's
 * `build/libs` against a hash of *its own* `src/`. Two consequences decide this:
 *
 * 1. A `compileOnly(files(...))` pointing at roguelite's built jar needs roguelite built first.
 *    Nothing guarantees that, and on a cache hit roguelite is not built at all — bridge would
 *    compile against a stale jar or none.
 * 2. Worse, it would be *silently* stale in the other direction. Change the seam in roguelite and
 *    bridge's source hash does not move, so bridge is a cache hit, is never recompiled, and ships a
 *    jar compiled against the old interface. A build that fails is fine; a build that quietly ships
 *    the wrong jar is not.
 *
 * A checked-in stub (the `libs/terrablender-api-stubs.jar` pattern next door) survives both, and it
 * is the right call *there* — three classes of a frozen third-party release. Here the stub would
 * have to cover eleven types of a module under active development, and what it buys is thinner than
 * it looks: compiling against our own copy verifies bridge against bridge, not against roguelite.
 * A rename still lands at runtime either way — as a `NoSuchMethodError` from a stub, or as the
 * logged resolve failure below. The stub also has to stay out of the shipped jar, because NeoForge
 * loads every mod in one class loader and a duplicate `com.cobblemonroguelite.*` FQCN would race
 * roguelite's real one.
 *
 * So: reflection, the same shape bridge already uses three times for RCTmod
 * ([com.cobblemonbridge.adapters.RctBridge], [com.cobblemonbridge.battle.RctTrainerBridge],
 * [com.cobblemonbridge.trainer.TrainerLevelBridge]). Bridge builds and tests with roguelite absent,
 * CI needs no ordering, and §2.9's rule — bridge may depend on roguelite, never the reverse — is
 * enforced by there being no dependency edge in the build at all.
 *
 * **What it costs**, stated plainly so the next person can re-decide: no compile-time check on any
 * of these names. The mitigation is that [install] resolves the *whole* chain eagerly at setup
 * rather than lazily at the first trainer wave, so a rename is an ERROR in the boot log naming the
 * missing member — and the seam is left at roguelite's own `UNIMPLEMENTED`, which refuses trainer
 * waves and leaves runs intact. It degrades to the state roguelite already designed for.
 */
object RogueliteSeam {

    private val log = LoggerFactory.getLogger("cobblemon-bridge/roguelite")

    // Every roguelite name this mod knows, in one block. A rename upstream is fixed here and
    // nowhere else.
    private const val PROVIDER = "com.cobblemonroguelite.integration.RunTrainerBattleProvider"
    private const val TRAINER_BATTLES = "com.cobblemonroguelite.integration.RunTrainerBattles"
    private const val REQUEST = "com.cobblemonroguelite.integration.RunTrainerBattleRequest"
    private const val WAVE_PLAN = "com.cobblemonroguelite.composition.WavePlan"
    private const val TRAINER_PICK = "com.cobblemonroguelite.data.trainer.TrainerPick"
    private const val RUN_STORE = "com.cobblemonroguelite.run.RunStore"
    private const val RUN_STATE = "com.cobblemonroguelite.run.RunState"
    private const val RUN_ARENAS = "com.cobblemonroguelite.arena.RunArenas"
    private const val ARENA_SUCCESS = "com.cobblemonroguelite.arena.ArenaResult\$Success"
    private const val ARENA_PLACEMENT = "com.cobblemonroguelite.arena.ArenaPlacement"
    private const val RUN_BATTLE_PARTY = "com.cobblemonroguelite.battle.RunBattleParty"

    private val installed = AtomicBoolean(false)

    // Resolved once by [install]; all null until then, and all non-null after it returns true.
    private var providerItf: Class<*>? = null
    private var trainerBattles: Any? = null
    private var registerM: Method? = null
    private var currentM: Method? = null
    private var planM: Method? = null
    private var pickM: Method? = null
    private var teamM: Method? = null
    private var waveM: Method? = null
    private var levelM: Method? = null
    private var kindM: Method? = null
    private var trainerIdM: Method? = null
    private var storeOf: Method? = null
    private var storeCompanion: Any? = null
    private var storeGet: Method? = null
    private var arenas: Any? = null
    private var prepareM: Method? = null
    private var successCls: Class<*>? = null
    private var successValueM: Method? = null
    private var placementDimM: Method? = null
    private var battleParty: Any? = null
    private var teamForM: Method? = null

    /** The proxy we registered, so a second [install] can tell "already ours" from "someone else's". */
    private var registered: Any? = null

    /** True once [install] has resolved the chain and registered a provider. */
    fun isInstalled(): Boolean = registered != null

    /**
     * Resolve the whole chain and register [handler] as roguelite's trainer-battle provider.
     *
     * Eager on purpose — see the class docs. Returns false when roguelite is not on the server (the
     * ordinary case for a modpack without it) or when anything in the chain has moved, and in both
     * cases roguelite keeps its own refusing default: trainer and boss waves do not start, the run
     * stops where it is with its party intact, and the other ~160 waves of a run still play.
     */
    fun install(handler: (MinecraftServer, ServerPlayer, TrainerWave) -> Boolean): Boolean {
        if (!installed.compareAndSet(false, true)) return isInstalled()
        try {
            val itf = Class.forName(PROVIDER)
            val batteriesCls = Class.forName(TRAINER_BATTLES)
            val batteries = batteriesCls.getField("INSTANCE").get(null)
            val register = batteriesCls.getMethod("register", itf)
            val current = batteriesCls.getMethod("getCurrent")

            val requestCls = Class.forName(REQUEST)
            val planCls = Class.forName(WAVE_PLAN)
            val pickCls = Class.forName(TRAINER_PICK)
            val stateCls = Class.forName(RUN_STATE)
            val storeCls = Class.forName(RUN_STORE)
            val companion = storeCls.getField("Companion").get(null)
            val arenasCls = Class.forName(RUN_ARENAS)
            val successClass = Class.forName(ARENA_SUCCESS)
            val placementCls = Class.forName(ARENA_PLACEMENT)
            val partyCls = Class.forName(RUN_BATTLE_PARTY)

            providerItf = itf
            trainerBattles = batteries
            registerM = register
            currentM = current
            planM = requestCls.getMethod("getPlan")
            pickM = requestCls.getMethod("getTrainer")
            teamM = requestCls.getMethod("teamProperties")
            waveM = planCls.getMethod("getWave")
            levelM = planCls.getMethod("getLevel")
            kindM = planCls.getMethod("getKind")
            trainerIdM = pickCls.getMethod("getTrainerId")
            storeCompanion = companion
            storeOf = companion.javaClass.getMethod("of", MinecraftServer::class.java)
            storeGet = storeCls.getMethod("get", UUID::class.java)
            arenas = arenasCls.getField("INSTANCE").get(null)
            prepareM = arenasCls.getMethod("prepare", MinecraftServer::class.java, stateCls)
            successCls = successClass
            successValueM = successClass.getMethod("getValue")
            placementDimM = placementCls.getMethod("getDimension")
            battleParty = partyCls.getField("INSTANCE").get(null)
            teamForM = partyCls.getMethod("teamFor", ServerPlayer::class.java, stateCls)

            // Don't re-register over ourselves. `register` logs a WARN when it replaces a live
            // provider — a real signal that two mods are fighting over the seam — and a second
            // install from our own side would fake that signal.
            val existing = current.invoke(batteries)
            if (existing != null && Proxy.isProxyClass(existing.javaClass) &&
                Proxy.getInvocationHandler(existing) is ProviderProxy
            ) {
                registered = existing
                return true
            }
            val proxy = Proxy.newProxyInstance(itf.classLoader, arrayOf(itf), ProviderProxy(handler))
            register.invoke(batteries, proxy)
            registered = proxy
            log.info("cobblemon-roguelite seam resolved — trainer and boss waves will be fought through RCT")
            return true
        } catch (e: ClassNotFoundException) {
            log.info("cobblemon-roguelite not loaded — trainer-battle provider not registered ({})", e.message)
        } catch (e: Throwable) {
            // A moved member, not a missing mod. ERROR because this is the case an operator can
            // actually act on, and because the symptom otherwise is "trainer waves refuse" with
            // nothing in the log tying it to a version skew between two of our own mods.
            log.error(
                "cobblemon-roguelite is loaded but its trainer-battle seam did not resolve — trainer " +
                    "and boss waves will refuse. Bridge and roguelite are built independently, so this " +
                    "is a version skew: re-check the names in RogueliteSeam against the module.",
                e,
            )
        }
        return false
    }

    /** Unpack the request roguelite handed us. Null if any accessor has moved. */
    @Suppress("UNCHECKED_CAST")
    fun waveOf(request: Any): TrainerWave? = try {
        val plan = planM!!.invoke(request)
        val pick = pickM!!.invoke(request)
        TrainerWave(
            wave = waveM!!.invoke(plan) as Int,
            level = levelM!!.invoke(plan) as Int,
            kind = kindM!!.invoke(plan).toString(),
            trainerId = trainerIdM!!.invoke(pick) as ResourceLocation,
            // Filtered rather than trusted whole: a blank entry would parse into Cobblemon's default
            // species, and a Bulbasaur appearing in a gym leader's team is the kind of bug that gets
            // attributed to the roster rather than to the one empty string that caused it.
            teamProperties = (teamM!!.invoke(request) as? List<String>)
                ?.filter { it.isNotBlank() }
                .orEmpty(),
        )
    } catch (e: Throwable) {
        log.error("could not read a roguelite trainer wave request", e)
        null
    }

    /** The live run for [player], or null if they have none. */
    fun runOf(server: MinecraftServer, player: UUID): Any? = try {
        val store = storeOf!!.invoke(storeCompanion, server)
        storeGet!!.invoke(store, player)
    } catch (e: Throwable) {
        log.error("could not read the roguelite run store", e)
        null
    }

    /**
     * Re-take [run]'s arena chunk ticket and answer which dimension the arena is in.
     *
     * The provider is required to do this before it puts anything in the arena — see
     * `RunTrainerBattleProvider.begin`. `prepare` is idempotent and re-takes the ticket every call,
     * which is the half that matters: an arena can go cold between the wave transition and anything
     * being summoned into it, and a summon into a cold arena fails *silently*.
     */
    fun holdArena(server: MinecraftServer, run: Any): ResourceLocation? = try {
        val result = prepareM!!.invoke(arenas, server, run)
        if (result != null && successCls!!.isInstance(result)) {
            placementDimM!!.invoke(successValueM!!.invoke(result)) as ResourceLocation
        } else {
            log.error("roguelite refused to prepare the arena for a trainer wave: {}", result)
            null
        }
    } catch (e: Throwable) {
        log.error("could not prepare a roguelite arena", e)
        null
    }

    /**
     * The run party as a battle team — uncloned, unhealed, exactly what the wild path fights with.
     *
     * This is why a trainer wave cannot simply be handed to RCT to run. RCT builds the player's side
     * from `TrainerPlayer.getTeam()`, which is the player's **real** party out of Cobblemon storage;
     * a run's party lives only in roguelite's run store and is deliberately never in it (§1.1). A
     * trainer wave fought RCT's way would put the player's overworld Pokémon in the arena, damage
     * them, and leave permadeath aimed at Pokémon the run has never heard of.
     */
    @Suppress("UNCHECKED_CAST")
    fun runTeam(player: ServerPlayer, run: Any): List<BattlePokemon>? = try {
        teamForM!!.invoke(battleParty, player, run) as? List<BattlePokemon>
    } catch (e: Throwable) {
        log.error("could not build a roguelite run battle team", e)
        null
    }

    /**
     * Adapts roguelite's `fun interface` to a Kotlin lambda without either side naming the other's
     * types. `begin` is the only abstract method on it; the three `Object` methods are answered here
     * because a proxy that throws on `toString` is a proxy that crashes whoever logs it — including
     * roguelite's own "provider replaced by" warning.
     */
    private class ProviderProxy(
        private val handler: (MinecraftServer, ServerPlayer, TrainerWave) -> Boolean,
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any {
            return when {
                method.name == "begin" && args != null && args.size == 3 -> {
                    val server = args[0] as MinecraftServer
                    val player = args[1] as ServerPlayer
                    val wave = waveOf(args[2]!!)
                    if (wave == null) false else handler(server, player, wave)
                }
                method.name == "toString" -> "cobblemon-bridge RogueliteTrainerBattles"
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "equals" -> proxy === args?.getOrNull(0)
                // Anything else is a default method we do not implement. Refusing is the safe
                // answer for a seam whose only question is "did the wave start".
                else -> false
            }
        }
    }
}
