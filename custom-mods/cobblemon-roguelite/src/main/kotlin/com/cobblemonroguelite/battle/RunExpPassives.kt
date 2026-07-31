package com.cobblemonroguelite.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent
import com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonroguelite.run.RunPassive
import com.cobblemonroguelite.run.RunPartySwap
import com.cobblemonroguelite.run.RunState
import com.cobblemonroguelite.run.RunStore
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * §2.43's run passives, applied where they actually do something: Cobblemon's experience events.
 *
 * ### The hook, and why it is these two events
 *
 * Cobblemon fires `EXPERIENCE_GAINED_EVENT_PRE` from inside `Pokemon.addExperience` — before any
 * EXP lands — with a settable `experience` field, and `..._POST` after it has been applied. That
 * makes PRE the exact seam for the charms (multiply what is about to be granted) and POST the seam
 * for the EXP Share (the participant's *final* granted amount is the base the share is computed
 * from, so the share inherits the charm boost the way PokéRogue's does — their charm applies to
 * every member's own number, and multiplication commutes).
 *
 * The alternative for the share was a Cobblemon-side path that awards non-participants natively —
 * there is none: `PlayerBattleActor.awardExperience` only ever reaches Pokémon that fought (their
 * held Exp. Share *item* is the exception, and held items are exactly what the playtest ruled
 * out). So the share is computed here and awarded through `Pokemon.addExperienceWithPlayer`, which
 * is the same API Cobblemon's own award path uses — level-ups, move learns and evolution checks
 * all come for free. That is the least invasive correct path: one event pair, no mixin, no copy of
 * Cobblemon's EXP formula.
 *
 * ### Scope: run Pokémon only, ever
 *
 * Both handlers gate on three facts that must all hold: the gain is *battle* EXP
 * ([BattleExperienceSource] — a candy or a command is not a battle and PokéRogue's charms do not
 * boost their candies either); the Pokémon carries the run marker ([RunPartySwap.isRunPokemon]);
 * and the marker's seed matches the owner's *current* run. The third is what keeps a stray run
 * Pokémon from a previous run — or anybody else's — from reading this run's stacks. A player with
 * no active run fails the lookup and nothing here touches them, so the real party outside runs is
 * structurally unreachable.
 *
 * ### Re-entrancy
 *
 * The share award fires these same events for the recipient, synchronously. Its source is a
 * [SidemodExperienceSource] naming this mod, which the `BattleExperienceSource` gate filters out —
 * so a share is never charm-boosted twice and never spawns a share of itself.
 *
 * ### Threading
 *
 * Battle EXP is awarded from the battle's victory dispatch, which Cobblemon runs on the server
 * thread — but the run lookup goes through world `dataStorage` ([RunStore.of]), which must never be
 * touched off it ([RunBattles.adopt] documents the same rule). PRE cannot hop threads (the value
 * has to be set before the event returns), so an off-thread event is *skipped, loudly*: one wave's
 * boost is lost and the log names the bug, which beats corrupting the save-data map to apply it.
 */
object RunExpPassives {

    private val registered = AtomicBoolean(false)

    /** Names this mod on share awards — both the re-entrancy filter and honest attribution. */
    private val SHARE_SOURCE = SidemodExperienceSource("cobblemon_roguelite")

    /** Subscribe once. A second subscription would double every boost and every share. */
    fun register() {
        if (!registered.compareAndSet(false, true)) return
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.NORMAL) { onPre(it) }
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_POST.subscribe(Priority.NORMAL) { onPost(it) }
        log.debug("roguelite: run EXP passives active")
    }

    /** The charms: multiply battle EXP before it lands. */
    private fun onPre(event: ExperienceGainedEvent.Pre) {
        if (event.source !is BattleExperienceSource) return
        val (_, run) = runFor(event.pokemon) ?: return
        val boosted = RunPassive.boostedExp(event.experience, run.passiveStacks)
        if (boosted != event.experience) event.experience = boosted
    }

    /** The EXP Share: after a participant's grant resolves, hand the non-participants their cut. */
    private fun onPost(event: ExperienceGainedEvent.Post) {
        val source = event.source as? BattleExperienceSource ?: return
        val (player, run) = runFor(event.pokemon) ?: return
        if ((run.passiveStacks[RunPassive.EXP_SHARE.id] ?: 0) < 1) return

        // Participants by Cobblemon's own criterion — the battle Pokémon on the player's side that
        // faced an opponent — because those are exactly the ones its award loop reaches, i.e. the
        // ones that must NOT also receive a share.
        val actor = source.battle.actors
            .firstOrNull { it is PlayerBattleActor && it.uuid == player.uuid } ?: return
        val participants = actor.pokemonList
            .filter { it.facedOpponents.isNotEmpty() }
            .map { it.effectedPokemon.uuid }
            .toSet()
        // A grant landing on a non-participant is not one to fan out from: it is Cobblemon's own
        // held-item share (or something equally exotic), and sharing a share compounds.
        if (event.pokemon.uuid !in participants) return

        val share = RunPassive.sharedExp(event.experience, run.passiveStacks, participants.size)
        if (share < 1) return
        run.partySnapshot()
            .filter { it.uuid != event.pokemon.uuid && it.uuid !in participants }
            // Fainted members earn nothing, PokéRogue's own rule (`nonFaintedPartyMembers`) — and
            // the guarded read is [RunState.isWiped]'s, for its reason.
            .filterNot { member -> runCatching { member.isFainted() }.getOrDefault(true) }
            .forEach { member ->
                runCatching { member.addExperienceWithPlayer(player, SHARE_SOURCE, share) }
                    .onFailure {
                        log.warn(
                            "roguelite: EXP Share could not grant {} exp to {}",
                            share, member.species.resourceIdentifier, it,
                        )
                    }
            }
    }

    /**
     * The player and run this gain belongs to, or null when it is not a run gain at all.
     *
     * Null is the common, silent case — every ordinary Pokémon on the server exits here on its
     * first check. The one *loud* null is the off-thread skip; see the class docs on threading.
     */
    private fun runFor(pokemon: Pokemon): Pair<ServerPlayer, RunState>? {
        if (!RunPartySwap.isRunPokemon(pokemon)) return null
        val player = pokemon.getOwnerPlayer() ?: return null
        val server = player.server
        if (!server.isSameThread) {
            log.warn(
                "roguelite: an EXP event for a run Pokémon arrived off the server thread — " +
                    "skipping its passives rather than touching dataStorage from here",
            )
            return null
        }
        val run = RunStore.of(server).get(player.uuid) ?: return null
        // THIS run's Pokémon, not a stray marked one from an earlier run (the seed is the marker).
        if (pokemon.persistentData.getLong(RunPartySwap.RUN_MARKER_KEY) != run.seed) return null
        return player to run
    }
}
