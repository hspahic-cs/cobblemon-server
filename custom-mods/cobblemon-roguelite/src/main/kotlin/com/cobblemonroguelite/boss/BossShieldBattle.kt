package com.cobblemonroguelite.boss

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/boss")

/**
 * The half of the boss-shield mechanic that has to happen in Kotlin: the words, and the name.
 *
 * ### Signalling is the feature, not the polish (§2.32)
 *
 * A damage floor nobody explains is *worse* than no damage floor. A player who watches a hit that
 * should have killed land for 80% and hears nothing does not conclude "shields"; they conclude the
 * mod is broken, and they are being reasonable. So the JS narrates itself, and this is where that
 * narration becomes something a player can read.
 *
 * ### Why a custom protocol id, and what the alternatives actually do
 *
 * Showdown talks to Cobblemon in protocol lines, and Cobblemon's [ShowdownInterpreter] holds a map
 * from protocol id to a parser. An id that is not in the map becomes an `UnknownInstruction`, whose
 * entire behaviour is to broadcast the **raw line, in red** — so `|-message|Onix's shield held!`
 * reaches the player as literally that, pipes and all, coloured like an error. The path exists;
 * it just looks like a stack trace.
 *
 * The two stock ids that *are* handled and could carry this — `-activate` and `-item` — render
 * through Cobblemon's client-side lang files. We would be asking a client for
 * `cobblemon.battle.activate.bossshield3`, which no client has, and a missing key renders as the
 * key. That is the same failure wearing a different hat, and it is unfixable from a server-side
 * mod: §2.32 deliberately did *not* buy a client mod for this.
 *
 * [ShowdownInterpreter.registerUpdateInstructionParser] is a public, stock extension point that
 * settles both problems at once. We claim one namespaced id, and the text is built here, on the
 * server, as a literal [Component] — no lang key, no client asset, no dependency on anything the
 * player has installed. It matches [com.cobblemonroguelite.run.RunMessages]' reasoning about
 * literal English for the same reason.
 *
 * ### The shape of the lines
 *
 * The JS emits three, all under [PROTOCOL_ID]:
 *
 * ```
 * |-rogueliteshield|start|<name>|<shields>
 * |-rogueliteshield|absorb|<name>|<discarded damage>|<shields left>
 * |-rogueliteshield|break|<name>|<shields left>|<stat or empty>
 * ```
 *
 * The Pokémon is identified by its **display name as a plain string**, not by Showdown's `p2a: X`
 * position token. That is deliberate: resolving a position token means going through
 * `BattleMessage.battlePokemon`, which in Cobblemon's fork also expects a UUID that only Cobblemon's
 * own emitting code appends. Passing the name means our three lines depend on nothing but string
 * splitting, which is the part of the interpreter least likely to move under us.
 *
 * The `-boost` that follows a break is **not** one of ours — the JS calls Showdown's own `boost()`
 * and the ordinary interpreter renders it. Our sentence supplies the cause; Cobblemon supplies the
 * effect, in the words the player already knows from every other stat boost in the game.
 */
object BossShieldBattle {

    /**
     * Our protocol id.
     *
     * Namespaced rather than something like `-shield` because the id space is global to Showdown and
     * shared with every other mod that has the same idea. A collision would not error — the later
     * registration simply wins — so the only defence is a name nobody else would pick.
     */
    const val PROTOCOL_ID: String = "-rogueliteshield"

    /** The prefix on a boss's nickname. Static, and see [markBosses] for why it must stay static. */
    const val NAME_PREFIX: String = "Boss "

    private val installed = AtomicBoolean(false)

    /**
     * Register the protocol parser and the send-in marker. Idempotent.
     *
     * Called from setup rather than lazily, because the parser has to be in the map *before* the
     * first shielded boss appears: an unregistered id does not fail, it dumps a red raw line into
     * the battle log, which is precisely the "the mod is broken" impression the mechanic exists to
     * avoid.
     */
    fun install() {
        if (!installed.compareAndSet(false, true)) return

        ShowdownInterpreter.registerUpdateInstructionParser(PROTOCOL_ID) { _, _, message, _ ->
            object : InterpreterInstruction {
                override fun invoke(battle: PokemonBattle) {
                    // dispatchGo, like Cobblemon's own instructions: the battle runs its messages
                    // through a dispatch queue so that text lands between the animations it
                    // describes rather than all at once when the turn resolves.
                    battle.dispatchGo {
                        render(
                            action = message.argumentAt(0),
                            name = message.argumentAt(1),
                            first = message.argumentAt(2),
                            second = message.argumentAt(3),
                        )?.let(battle::broadcastChatMessage)
                    }
                }
            }
        }

        // PRE and not POST. The marker has to be on the Pokémon before Cobblemon serialises the
        // teams for Showdown and before the client is told what it is looking at; POST fires after
        // both, and a name that appears one packet late is a name the player never sees change —
        // it just is not there.
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL) { markBosses(it.battle) }

        log.debug("roguelite: boss shield messages and send-in marker installed")
    }

    /**
     * One line of player-facing text, or null for a line we do not recognise.
     *
     * Null rather than a fallback string on purpose. A malformed line means the JS and this file
     * disagree, and the useful outcome of that is silence plus a log entry, not a half-rendered
     * sentence that a player reports as a typo.
     */
    internal fun render(action: String?, name: String?, first: String?, second: String?): Component? {
        if (name.isNullOrBlank()) return null
        return when (action) {
            // Fires on every send-in, not only the first, because Showdown runs an item's Start
            // event on each switch-in. That is the behaviour we want: a boss that comes back out
            // re-states how much of its guard is left, which is the number the player is tracking.
            "start" -> {
                val shields = first?.toIntOrNull() ?: return null
                literal("$name is shielded — ${shieldCount(shields)} to break through.", ChatFormatting.AQUA)
            }
            // THE important one. This is the line that stands between a floored hit and a bug
            // report, so it names the damage that was thrown away rather than only the outcome.
            "absorb" -> {
                val discarded = first?.toIntOrNull() ?: return null
                val left = second?.toIntOrNull() ?: return null
                literal(
                    "$name's shield held — $discarded damage was absorbed. ${shieldCount(left)} left.",
                    ChatFormatting.AQUA,
                )
            }
            // The -boost line for [second] arrives separately, through Cobblemon's own interpreter.
            // This sentence exists to say *why* it happened; without it the boost looks like an
            // ability nobody can see.
            "break" -> {
                val left = first?.toIntOrNull() ?: return null
                val stat = second?.takeIf { it.isNotBlank() }?.let(::statName)
                val tail = if (stat != null) " Its $stat rose!" else ""
                val remaining =
                    if (left <= 0) "Its last shield is gone." else "${shieldCount(left)} left."
                literal("$name's shield shattered! $remaining$tail", ChatFormatting.GOLD)
            }
            else -> {
                log.warn(
                    "roguelite: unrecognised boss shield action '{}' — the JS in " +
                        "data/cobblemon_roguelite/held_items/ is emitting a line this build does not render",
                    action,
                )
                null
            }
        }
    }

    private fun shieldCount(n: Int): String = if (n == 1) "1 shield" else "$n shields"

    /**
     * Showdown's stat keys spelled the way the game spells them.
     *
     * Mapped here rather than passed as prose from the JS so that the two sides exchange the same
     * short tokens Showdown uses everywhere else — an unknown key then falls through as itself,
     * which is a legible sentence rather than a missing one.
     */
    private fun statName(key: String): String = when (key) {
        "atk" -> "Attack"
        "def" -> "Defense"
        "spa" -> "Sp. Atk"
        "spd" -> "Sp. Def"
        "spe" -> "Speed"
        else -> key
    }

    private fun literal(text: String, colour: ChatFormatting): Component =
        Component.literal(text).withStyle(colour)

    /**
     * Rename every shielded Pokémon on a non-player side to "Boss <Species>".
     *
     * ### Why a static prefix and not a live shield count
     *
     * §2.32 rejected putting the count in the name twice over, and both reasons still hold. Whether
     * a nickname changed mid-battle even reaches the client is unverified, so a counting name would
     * be a feature that might silently do nothing; and a Pokémon whose name changes as you hit it
     * breaks a rule players know from every Pokémon game there has ever been. The name says what
     * *kind of fight* this is. The running count is what the messages are for.
     *
     * ### Why it keys off the item and not off the run
     *
     * This subscribes to every battle on the server, including ranked, gyms and wild encounters,
     * and it must not touch any of them. Rather than ask the run store — which would mean a
     * save-data lookup on a battle-start event, from a thread that is not guaranteed to be the
     * server thread — it asks the only question that is self-limiting: does this Pokémon hold one
     * of our shield items? Nothing outside this module can produce one, so the answer is no for
     * every battle the mode did not create.
     */
    internal fun markBosses(battle: PokemonBattle) {
        for (actor in battle.actors) {
            // The player's own Pokémon can never hold a shield, but checking the actor type first
            // means we do not even look — a run party is up to six Pokémon per battle and this runs
            // on every battle start on the server.
            if (actor.type == ActorType.PLAYER) continue
            for (battlePokemon in actor.pokemonList) {
                val heldId = runCatching { CobblemonHeldItemManager.showdownId(battlePokemon) }.getOrNull()
                if (!BossShields.isShieldItem(heldId)) continue
                val pokemon = battlePokemon.effectedPokemon
                val current = pokemon.nickname?.string
                // Idempotent: BATTLE_STARTED_PRE can be seen more than once for the same Pokémon if
                // a battle start is retried, and "Boss Boss Onix" is the kind of thing that ships.
                if (current != null && current.startsWith(NAME_PREFIX)) continue
                val marked = Component.literal(NAME_PREFIX).append(pokemon.species.translatedName)
                pokemon.nickname = marked
                // effectedPokemon is a battle-scoped view that is usually — but not always — the
                // same object as the real one. Setting both means the name is right whether it is
                // read from the battle or from the entity standing in the arena.
                battlePokemon.originalPokemon.takeIf { it !== pokemon }?.nickname = marked
            }
        }
    }
}
