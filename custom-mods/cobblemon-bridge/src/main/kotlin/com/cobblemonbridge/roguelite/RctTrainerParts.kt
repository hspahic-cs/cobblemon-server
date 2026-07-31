package com.cobblemonbridge.roguelite

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.MoveActionResponse
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.battles.ShowdownMoveset
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import org.slf4j.LoggerFactory
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The parts of an RCT trainer a roguelite wave needs, without RCT running the battle.
 *
 * ### Why we take RCT apart instead of calling `startBattleWith`
 *
 * `TrainerMob.startBattleWith` → `RCTMod.makeBattle` builds the player's side from
 * `TrainerPlayer.getTeam()`, which reads the player's **real** Cobblemon party. A roguelite run's
 * party is not there and must never be (§1.1) — it lives in the run store and is handed to a battle
 * uncloned so the wave's damage sticks. So RCT's own entry point would put the player's overworld
 * Pokémon in the arena, and permadeath would fire at Pokémon the run has never heard of.
 *
 * `startBattleWith` is also gated by `canBattleAgainst`, which asks questions that are all wrong for
 * a run: has this trainer been beaten before (every roster repeat would refuse), does the player have
 * a non-fainted **real** party, are they under RCT's level cap, have they cleared the trainer's
 * series requirements. A wave must not be refused because of the player's overworld progress.
 *
 * What is left is exactly what §2.6 wanted from RCT and nothing else: an authored team, an authored
 * AI, and a visible NPC with the right name and skin. All three are reachable through public API —
 * `TrainerRegistry.getById(id, TrainerNPC.class)` for the first two,
 * `BattleManager$TrainerEntityBattleActor`'s public constructor to wrap them — so the battle itself
 * is an ordinary Cobblemon `BattleRegistry.startBattle`, the same call the wild path makes.
 *
 * Reflection for the same reason as everywhere else in this mod: RCTmod is a soft dependency and is
 * not on our compile classpath. Everything degrades to "no trainer waves" when it is absent.
 */
object RctTrainerParts {

    private val log = LoggerFactory.getLogger("cobblemon-bridge/roguelite")

    private const val RCTMOD = "com.gitlab.srcmc.rctmod.api.RCTMod"
    private const val MOD_COMMON = "com.gitlab.srcmc.rctmod.ModCommon"
    private const val TRAINER_MOB = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob"
    private const val TRAINER_REGISTRY = "com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry"
    private const val TRAINER_NPC = "com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC"
    private const val TRAINER_BAG = "com.gitlab.srcmc.rctapi.api.trainer.TrainerBag"
    private const val TRAINER_ACTOR = "com.gitlab.srcmc.rctapi.api.battle.BattleManager\$TrainerEntityBattleActor"

    private val resolveOnce = AtomicBoolean(false)
    @Volatile private var ok = false

    private var rctGetInstance: Method? = null
    private var getTrainerManager: Method? = null
    private var isValidIdM: Method? = null
    private var rctApi: Any? = null
    private var getTrainerRegistry: Method? = null
    private var getByIdM: Method? = null
    private var npcCls: Class<*>? = null
    private var npcTeamM: Method? = null
    private var npcAiM: Method? = null
    private var actorCtor: Constructor<*>? = null
    private var emptyBagCtor: Constructor<*>? = null

    /** RCT display names end in the authored team's level — see [actorFor] for why it goes. */
    private val LEVEL_SUFFIX = Regex(""" ?Lv\.\d+$""")
    private var mobEntityTypeM: Method? = null
    private var mobSetTrainerIdM: Method? = null
    private var mobSetPersistentM: Method? = null

    private fun resolve(): Boolean {
        if (resolveOnce.get()) return ok
        synchronized(this) {
            if (resolveOnce.get()) return ok
            try {
                val rctMod = Class.forName(RCTMOD)
                rctGetInstance = rctMod.getMethod("getInstance")
                getTrainerManager = rctMod.getMethod("getTrainerManager")
                isValidIdM = getTrainerManager!!.returnType.getMethod("isValidId", String::class.java)

                // ModCommon.RCT is the mod's single RCTApi instance. RCTApi.getInstance(String) also
                // exists but wants the instance key RCTmod chose; reading the field asks nobody.
                rctApi = Class.forName(MOD_COMMON).getField("RCT").get(null)
                getTrainerRegistry = Class.forName("com.gitlab.srcmc.rctapi.api.RCTApi")
                    .getMethod("getTrainerRegistry")
                getByIdM = Class.forName(TRAINER_REGISTRY)
                    .getMethod("getById", String::class.java, Class::class.java)

                npcCls = Class.forName(TRAINER_NPC)
                npcTeamM = npcCls!!.getMethod("getTeam")
                npcAiM = npcCls!!.getMethod("getBattleAI")
                // The Q4 ruling (strip the bag) is implemented as an EMPTY bag, never null.
                // Learned on dev 2026-07-31: RCT's own RCTBattleAI calls getBag().getItems()
                // unconditionally (ResponseBuilder.suggestItems), so a null bag NPEs the battle
                // tick on the first turn of any trainer whose JSON declares the rct AI — the wave
                // dies two seconds in with "a battle error has occurred". An empty bag has nothing
                // to suggest, which is the ruling, minus the crash.
                val bagCls = Class.forName(TRAINER_BAG)
                emptyBagCtor = bagCls.getConstructor()

                val aiCls = Class.forName("com.cobblemon.mod.common.api.battles.model.ai.BattleAI")
                actorCtor = Class.forName(TRAINER_ACTOR).getConstructor(
                    String::class.java, LivingEntity::class.java, UUID::class.java,
                    List::class.java, bagCls, aiCls,
                )

                val mobCls = Class.forName(TRAINER_MOB)
                mobEntityTypeM = mobCls.getMethod("getEntityType")
                mobSetTrainerIdM = mobCls.getMethod("setTrainerId", String::class.java)
                // The two-arg overload; the second argument suppresses TrainerSpawner registration.
                // We want persistence (so the NPC is not swept mid-battle) without RCT's spawner
                // adopting it — the arena NPC is ours to place and ours to discard, and a registered
                // persistent trainer also drags its own chunk ticket around for a run's lifetime.
                mobSetPersistentM = mobCls.getMethod(
                    "setPersistent", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                )

                ok = true
                log.info("RCT trainer parts resolved — roguelite trainer waves can summon and fight")
            } catch (e: ClassNotFoundException) {
                log.warn("RCTmod/rctapi not loaded — roguelite trainer and boss waves cannot be fought")
            } catch (e: Throwable) {
                log.error("RCT trainer-parts reflection failed — roguelite trainer waves will refuse", e)
            }
            resolveOnce.set(true)
            return ok
        }
    }

    fun available(): Boolean = resolve()

    /**
     * The RCT trainer id [id] names, or null when it names nothing.
     *
     * RCT keys its registry by a **bare string** (`data/rctmod/trainers/<id>.json`), while roguelite's
     * roster carries a [ResourceLocation] because that is what a datapack-authored id parses to. The
     * two conventions meet here rather than in the roster, because only this side can ask RCT which
     * spelling it actually has: a bare `gym_01_clay` in a roster file becomes `minecraft:gym_01_clay`
     * on the way in, and an author who wrote `rctmod:gym_01_clay` meant the same trainer.
     *
     * So both spellings are offered to `isValidId` and the one RCT recognises wins. Guessing a single
     * mapping would turn one authoring convention into "that trainer does not exist", which is the
     * failure this whole function exists to report honestly.
     */
    fun resolveTrainerId(id: ResourceLocation): String? {
        if (!resolve()) return null
        val manager = manager() ?: return null
        for (candidate in trainerIdCandidates(id.namespace, id.path)) {
            val valid = runCatching { isValidIdM!!.invoke(manager, candidate) as? Boolean }.getOrNull()
            if (valid == true) return candidate
        }
        return null
    }

    /**
     * The spellings of `<namespace>:<path>` that might be an RCT trainer id, best guess first.
     *
     * Split out from [resolveTrainerId] and kept free of Minecraft types so it can be tested at all:
     * the bridge test source set is a plain JVM run with no modding classpath. The ordering is the
     * whole content — `minecraft` is what a *bare* id in a roster file parses to and therefore never
     * means a namespace, while any other namespace was written on purpose and is tried first.
     */
    internal fun trainerIdCandidates(namespace: String, path: String): List<String> =
        if (namespace == "minecraft" || namespace == "rctmod") {
            listOf(path, "$namespace:$path")
        } else {
            listOf("$namespace:$path", path)
        }

    /** The authored team behind [rctId]. These are the registry's own Pokémon — copy before use. */
    fun teamOf(rctId: String): List<Pokemon>? {
        val npc = npc(rctId) ?: return null
        val team = runCatching { npcTeamM!!.invoke(npc) as? Array<*> }.getOrNull() ?: return null
        return team.filterIsInstance<Pokemon>().takeIf { it.isNotEmpty() }
    }

    /**
     * The opponent-side actor: RCT's own, so the battle behaves like an RCT trainer battle in every
     * way except who built it.
     *
     * [team] is ours — built with `BattlePokemon.safeCopyOf`, which is what makes the wave's level
     * mutation land on a battle clone and never on the authored trainer (§2.6). The bag is an
     * **empty** `TrainerBag` (§4 Q4, ruled 2026-07-31): PokéRogue trainers never heal or item
     * mid-fight — their difficulty is party composition — and RCT's authored bags would make wave
     * difficulty depend on which RCT trainer id a roster happened to name. Empty rather than null
     * because RCT's `RCTBattleAI` reads `getBag().getItems()` without a null check and a null bag
     * killed every rct-AI battle on its first turn (dev, 2026-07-31).
     *
     * The actor's name strips RCT's ` Lv.N` suffix. That N is the *authored* team's level — the
     * skier's JSON says 36 — and the team actually fielded is the generated one at the wave's
     * level, so "Skier Kaitlyn Lv.36" sending out a level-5 Koffing reads as a bug in every battle
     * message. The overworld nameplate is rctmod's renderer and out of reach from here.
     */
    fun actorFor(rctId: String, entity: Entity, team: List<BattlePokemon>): BattleActor? {
        val npc = npc(rctId) ?: return null
        return try {
            val ai = npcAiM!!.invoke(npc) as? BattleAI ?: return null
            val name = (entity.displayName?.string ?: rctId).replace(LEVEL_SUFFIX, "")
            actorCtor!!.newInstance(
                name, entity, entity.uuid, team, emptyBagCtor!!.newInstance(), NoGimmickAI(ai),
            ) as BattleActor
        } catch (e: Throwable) {
            log.error("could not build an RCT battle actor for trainer '{}'", rctId, e)
            null
        }
    }

    /**
     * The authored AI with its gimmick choices stripped — wave opponents never Dynamax, Tera or
     * Mega, whatever their brain asks for.
     *
     * Two reasons, found the same evening (dev, 2026-07-31). The design one: PokéRogue's wave
     * opponents do not use gimmicks — a boss's step-up is §2.32's shields — and a wave-10 Gigantamax
     * is a wall nobody authored. The mechanical one is worse: Misty's AI Dynamaxed, then chose
     * Dynamax **again**, and Showdown's refusal (`|error|[Invalid choice] Can't move: You can only
     * Dynamax once per battle`) is a protocol line Cobblemon has no interpretation for — the choice
     * is never re-made and the battle hangs with the player unable to move. Removing the power spot
     * from the arena palettes did not touch either: the AI side never needed one.
     *
     * Stripping the CHOICE rather than configuring the gimmick away is deliberate: Mega Showdown's
     * enable flags are global (they serve ranked and the gyms), and `BattleFormat.ruleSet` is
     * already known not to be the lever. The response object is ours to edit between the brain and
     * the engine, and a `MoveActionResponse` with `gimmickID = null` is exactly the same move,
     * un-gimmicked.
     */
    private class NoGimmickAI(private val inner: BattleAI) : BattleAI {
        override fun choose(
            activeBattlePokemon: ActiveBattlePokemon,
            battle: PokemonBattle,
            side: BattleSide,
            moveset: ShowdownMoveset?,
            forceSwitch: Boolean,
        ): ShowdownActionResponse {
            val choice = inner.choose(activeBattlePokemon, battle, side, moveset, forceSwitch)
            if (choice is MoveActionResponse && choice.gimmickID != null) choice.gimmickID = null
            return choice
        }

        override fun onHealthChange(packet: BattleHealthChangePacket) = inner.onHealthChange(packet)
    }

    /**
     * Put the trainer in the arena, synchronously.
     *
     * Deliberately **not** `rctmod trainer summon_persistent`, which is what
     * [com.cobblemonbridge.tower.TowerManager] uses. That command materialises over the following
     * ticks and has to be found again with a box search afterwards, which is why the tower needs a
     * settle window and a one-at-a-time pipeline. A wave cannot afford that: the provider has to
     * answer "did this wave start" before it returns, or a failed summon leaves the run holding a
     * §2.10 battle marker for a battle that never happens — party intact, wave never resolving, and
     * the player's next ordinary logout charged as a rage-quit.
     *
     * Spawning the entity directly is synchronous, returns the handle, and skips the spawner
     * entirely. The two behaviours the command would have given us are set explicitly: persistence,
     * so nothing sweeps the NPC mid-battle, and — the one that is not obvious — [Mob.setNoAi],
     * because a live `TrainerMob` walks up to players and starts battles on sight. In a one-player
     * arena that is a second, RCT-driven battle attempt against the run's own wave.
     */
    /**
     * Every wave NPC carries this **command tag**, which vanilla serialises with the entity. It is
     * the leftover-trainer fix (dev, 2026-07-31): a wipe teleports the player out and drops the
     * arena's chunk ticket inside the sweep's 20-tick window, the NPC unloads to disk reading as
     * `isRemoved` (so the sweep skips it), and the *saved* trainer re-materialises the next time
     * that arena slot loads — after the stamp sweep has already run, because entities load a beat
     * behind their chunks. The tag is what lets [RogueliteTrainerBattles] recognise and discard a
     * resurrected NPC at `EntityJoinLevelEvent`, which fires for disk loads exactly.
     */
    const val WAVE_NPC_TAG = "cobblemon_roguelite_wave_npc"

    fun spawn(level: ServerLevel, rctId: String, x: Double, y: Double, z: Double, yaw: Float): Entity? {
        if (!resolve()) return null
        return try {
            val type = mobEntityTypeM!!.invoke(null) as EntityType<*>
            val mob = type.create(level) ?: return null
            mobSetTrainerIdM!!.invoke(mob, rctId)
            mob.moveTo(x, y, z, yaw, 0f)
            mobSetPersistentM!!.invoke(mob, true, true)
            (mob as? Mob)?.isNoAi = true
            mob.addTag(WAVE_NPC_TAG)
            if (!level.addFreshEntity(mob)) {
                mob.discard()
                log.error("the world refused the trainer entity for '{}' at {} {} {}", rctId, x, y, z)
                return null
            }
            mob
        } catch (e: Throwable) {
            log.error("could not spawn trainer '{}'", rctId, e)
            null
        }
    }

    private fun manager(): Any? = runCatching {
        getTrainerManager!!.invoke(rctGetInstance!!.invoke(null))
    }.getOrNull()

    private fun npc(rctId: String): Any? {
        if (!resolve()) return null
        return try {
            val registry = getTrainerRegistry!!.invoke(rctApi)
            getByIdM!!.invoke(registry, rctId, npcCls)
        } catch (e: Throwable) {
            log.error("RCT has no registered trainer for id '{}'", rctId, e)
            null
        }
    }
}
