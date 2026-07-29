package com.cobblemonroguelite.run

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonroguelite.starter.StarterOffer
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Everything the mode says to a player, in one place.
 *
 * Here rather than inline at the call sites because two unrelated paths need the same words: the
 * command layer and the login hook both have to describe a pending start, and the wording of "you
 * have paid for a run and not picked a starter" is the sort of thing that goes subtly out of sync
 * between two copies and then reads as two different situations.
 *
 * Plain [Component.literal] English, deliberately. Translation keys would need a language file this
 * module does not ship, and a half-translated mod is worse than an untranslated one. The exception
 * is anything the charge provider says: those [Component]s arrive already built and are passed
 * through untouched, because only the provider knows what the price is denominated in.
 */
object RunMessages {

    fun noRun(): Component = literal("You have no run in progress. /roguelite start begins one.")

    fun alreadyRunning(): Component =
        literal("You already have a run in progress. Finish it, or /roguelite abandon to walk away.")

    fun depthLocked(refusal: RunStartRefusal.DepthLocked): Component = literal(
        "You have not earned a run yet. Beat a gym first — any of: " +
            refusal.requires.joinToString(", ") { it.toString() },
    )

    /**
     * The one refusal that is about the server and not the player, so it is the one that has to say
     * "come back" rather than "you cannot". Nothing has been charged when this is shown.
     */
    fun noArenaAvailable(): Component =
        literal("Every run arena is in use right now. Nothing has been taken — try again shortly.")

    /**
     * Said when the arena could not be built. Deliberately does not name the structure file: the
     * player cannot fix it and the operator gets the path in the log, with the whole namespace.
     */
    fun arenaUnavailable(): Component = literal(
        "Your run arena could not be prepared, so you have not been moved. Your run is safe — tell an operator.",
    )

    fun noStarters(): Component = literal(
        "The starter pool is empty, so there is nothing to offer you. Your run is paid for and " +
            "waiting — tell an operator, then /roguelite resume.",
    )

    /** The confirm prompt. It names the price, which is the entire reason the quote step exists. */
    fun confirmStart(price: Component?): Component {
        val line = Component.literal("Start a run? ").withStyle(ChatFormatting.YELLOW)
        if (price != null) line.append(price).append(Component.literal(" "))
        return line.append(
            Component.literal("This is charged up front and is not refunded if you abandon. Type /roguelite start confirm.")
                .withStyle(ChatFormatting.GRAY),
        )
    }

    fun offer(offer: StarterOffer): Component = literal(
        "Choose your starter with /roguelite starter <species>: " +
            offer.species.joinToString(", ") { it.toString() },
    )

    fun started(species: String): Component =
        literal("Your run begins with $species at level 1. /roguelite resume to fight wave 1.")

    fun notOffered(): Component = literal("That species was not in your offer. /roguelite status shows it again.")

    fun speciesUnavailable(): Component =
        literal("That species could not be created on this server. Pick another — your run is still paid for.")

    fun atWave(wave: Int, party: Int, depthCap: Int?): Component {
        val cap = depthCap?.let { " (your badges allow $it)" } ?: ""
        return literal("Wave $wave$cap, $party Pokémon alive.")
    }

    /**
     * Said when the wave handler refuses. Names the state of the world rather than an error, because
     * on a build with no handler registered this is not a fault — it is the mode being unfinished,
     * and the player's run is intact.
     */
    fun waveUnavailable(wave: Int): Component =
        literal("Wave $wave cannot be started — run battles are not implemented on this server yet. Your run is safe.")

    /**
     * Said when the run's trainer roster is not loaded. Does not name the id, for
     * [arenaUnavailable]'s reason — the player cannot fix a datapack and the operator has it in the
     * log — and says *safe* rather than *error*, because the run genuinely is: it resumes untouched
     * the moment the roster is back.
     */
    fun rosterUnavailable(): Component = literal(
        "Your run's opponent roster is not loaded on this server, so the next wave cannot be built. " +
            "Your run is safe — tell an operator, then /roguelite resume.",
    )

    /**
     * §2.10 on reconnect, and the one message in here that is not optional.
     *
     * A player who logs back in to a party one Pokémon short and no explanation has been handed a bug
     * — permadeath they did not watch happen, from a rule nobody told them. So both branches say the
     * *other* branch exists: the penalised player is told the server did not restart, and the player
     * we interrupted is told they were not charged for it. That contrast is the rule, and it is what
     * stops the penalty reading as a server that eats Pokémon at random.
     */
    fun interrupted(outcome: DisconnectOutcome): Component = when (outcome) {
        is DisconnectOutcome.CleanResume -> Component.literal(
            "The server restarted during your wave ${outcome.wave} battle. Nothing was lost — " +
                "/roguelite resume to fight it again.",
        ).withStyle(ChatFormatting.YELLOW)

        is DisconnectOutcome.Penalised -> {
            // The empty case is a real reconnect: everything that was out had already fainted, so the
            // penalty found nothing. Saying "you lost nothing" beats saying nothing at all, which
            // would leave them wondering what a mid-battle disconnect had cost them.
            val head = if (outcome.killed.isEmpty()) {
                "Your connection dropped during wave ${outcome.wave}. Nothing was left on the field to lose."
            } else {
                "Your connection dropped during wave ${outcome.wave}, so what you had out was lost for good: " +
                    outcome.killed.joinToString(", ") + "."
            }
            // "Still on" and not "continues at": the wave is not skipped, and a player who is not told
            // that will assume the Pokémon bought them the fight. It did not — that is the entire
            // reason the penalty stopped advancing the run.
            val tail = when {
                outcome.ended != null -> " That was your last Pokémon."
                outcome.resumesAt != outcome.wave -> " Your run stands at wave ${outcome.resumesAt}."
                else -> " Your run is still on wave ${outcome.wave} — that fight is still ahead of you."
            }
            Component.literal(
                head + tail + " Disconnecting mid-battle counts as yours; a server restart would not have.",
            ).withStyle(ChatFormatting.RED)
        }
    }

    /**
     * §2.22's whole point: the disconnect penalty stated *before* it is paid rather than discovered
     * by paying it.
     *
     * ### Why nothing here names a Pokémon
     *
     * It would be the wrong one. Until the wave handler reports switches through
     * [RunController.battleFieldChanged], the marker still holds the party lead that
     * [RunController] stamped at the start of the battle — the right *size* of loss aimed at the
     * wrong Pokémon (see [RunWaveHandler]). A warning that names it would be confidently wrong for
     * every player who has switched, and a wrong name in a warning about permadeath is worse than no
     * name: it is the one detail they would check it against. "What you have out" is true either way,
     * and stays true when the handler lands.
     *
     * The alternative — naming it only when the marker is known-current — needs the marker to carry
     * whether it has ever been reported, i.e. persisted state added for a cosmetic gain, and it would
     * still be silent for exactly the builds where it is wrong.
     */
    fun pause(advice: PauseAdvice): Component = when (advice) {
        // The one case that says "start one": a player who asks whether it is safe to leave and has
        // no run is usually asking the wrong command, not planning to log off.
        PauseAdvice.NoRun -> noRun()

        PauseAdvice.StarterPending -> literal(
            "No battle in progress. Your paid start is saved and your starter offer will be waiting — " +
                "log off whenever you like.",
        )

        is PauseAdvice.BetweenWaves -> literal(
            "Safe to leave. No battle is in progress and your run is saved at wave ${advice.wave} — " +
                "nothing is lost by logging off now.",
        )

        // Red, and it says "does not" twice on purpose. A command called `pause` invites the reading
        // that using it makes leaving safe, and that reading costs a Pokémon.
        is PauseAdvice.MidBattle -> Component.literal(
            "You are mid-battle on wave ${advice.wave}. Leaving now — logging out, or your connection " +
                "dropping — kills what you have out on the field, for good. This command does not stop " +
                "that and does not pause the battle. Type /roguelite pause confirm if you understand.",
        ).withStyle(ChatFormatting.RED)

        is PauseAdvice.MidBattleAcknowledged -> Component.literal(
            "Understood. Nothing has been taken and your wave ${advice.wave} battle is still live — the " +
                "price is charged when you actually leave, not for saying so. Finish the battle and it " +
                "costs nothing.",
        ).withStyle(ChatFormatting.YELLOW)
    }

    /**
     * §2.13, at the moment the ball lands.
     *
     * The full-party branch is red and the ordinary one is not, which is the only signal the player
     * gets that this catch is different from the last one: it has not joined anything yet, and the
     * next thing they type decides which Pokémon stops existing.
     */
    fun caught(routing: CatchRouting, pokemon: Pokemon): Component = when (routing) {
        is CatchRouting.Joined -> Component.literal(
            // The level is named because §2.21 puts a mid-run catch in at its own encounter level
            // rather than the party's, so a player who expected it to arrive at parity with their
            // lead can see immediately that it did not.
            "Caught ${describe(pokemon)}. It joins your run party in slot ${routing.slot}.",
        ).withStyle(ChatFormatting.GREEN)

        is CatchRouting.HeldForDecision -> Component.literal(
            "Caught ${describe(pokemon)}, but your run party is full. It is not yours yet — " +
                "/roguelite catch to decide what to give up.",
        ).withStyle(ChatFormatting.RED)

        is CatchRouting.AlreadyDeciding -> Component.literal(
            "Caught ${describe(pokemon)} while you were still deciding about " +
                "${describe(routing.held)}. ${pokemon.species.name} is gone — settle the first " +
                "decision with /roguelite catch.",
        ).withStyle(ChatFormatting.RED)
    }

    /**
     * §2.14 refusing a catch on a trainer or boss wave.
     *
     * Says the Pokémon is gone rather than implying it was never caught, because the player watched
     * the ball land. Only reachable through a bug — those opponents are spawned uncatchable — so it
     * also has to be a line an operator can search the log for, which is why it names the rule.
     */
    fun uncatchableWave(): Component = Component.literal(
        "That was not a wild wave, so it could not be caught into your run — and it is gone. " +
            "Tell an operator: a run wave was catchable that should not have been.",
    ).withStyle(ChatFormatting.RED)

    /**
     * The decision itself. The longest message in here, and every line of it is load-bearing.
     *
     * ### Why it says "destroyed" twice and never says "box" or "storage"
     *
     * Mainline Pokémon trains players that a full party means a trip to the PC, i.e. that this screen
     * costs nothing. §2.13 gives a run no PC on purpose: whichever side of this the player picks, that
     * Pokémon is gone the way a faint is gone. A prompt that read as inventory management would be
     * describing a different game, and the player would find out which one only after picking.
     *
     * ### Why the party is numbered here rather than left to /roguelite status
     *
     * The swap takes a slot number, so the numbers have to come from the same message that asks for
     * one. Sending them to another command to look it up is how somebody destroys slot 3 having read
     * a list that was ordered differently.
     */
    fun catchPrompt(pokemon: Pokemon, party: List<Pokemon>): Component {
        val roster = party.mapIndexed { index, member -> "  ${index + 1}. ${describe(member)}" }
            .joinToString("\n")
        return Component.literal(
            "You are holding ${describe(pokemon)} and your run party is full.\n" +
                "One of these seven does not continue the run, and whichever you give up is gone for " +
                "good — there is no box to take it back out of.\n" +
                "$roster\n" +
                "/roguelite catch swap <1-${party.size}> — that party member is destroyed and " +
                "${pokemon.species.name} takes its place.\n" +
                "/roguelite catch release — ${pokemon.species.name} is destroyed and your party is unchanged.",
        ).withStyle(ChatFormatting.RED)
    }

    /** The claim branch: a slot opened between the catch and the question, so there is nothing to decide. */
    fun catchJoined(pokemon: Pokemon, slot: Int): Component = Component.literal(
        "A slot had opened in your run party, so ${describe(pokemon)} took it — slot $slot. " +
            "Nothing was given up.",
    ).withStyle(ChatFormatting.GREEN)

    fun nothingHeld(): Component =
        literal("You are not holding a caught Pokémon. /roguelite status shows where your run stands.")

    /** Said when a wave is asked for while a decision is outstanding. Names the block, not an error. */
    fun catchPending(pokemon: Pokemon): Component = Component.literal(
        "Your run is waiting on you: ${describe(pokemon)} is caught and your party is full. " +
            "/roguelite catch to swap or release it, then resume.",
    ).withStyle(ChatFormatting.YELLOW)

    /** Both confirmations name the Pokémon that dies, because that is the only fact being confirmed. */
    fun confirmSwap(slot: Int, discarded: Pokemon, incoming: Pokemon): Component = Component.literal(
        "Swap ${describe(incoming)} in for ${describe(discarded)} in slot $slot? " +
            "${discarded.species.name} is destroyed for good — it does not go anywhere. " +
            "Type /roguelite catch swap $slot confirm.",
    ).withStyle(ChatFormatting.RED)

    fun confirmRelease(pokemon: Pokemon): Component = Component.literal(
        "Release ${describe(pokemon)}? It is destroyed for good and your party is unchanged. " +
            "Type /roguelite catch release confirm.",
    ).withStyle(ChatFormatting.RED)

    fun catchResolved(resolution: CatchResolution): Component = when (resolution) {
        is CatchResolution.Swapped -> Component.literal(
            "${resolution.discarded.species.name} is gone. ${describe(resolution.kept)} takes slot " +
                "${resolution.slot}. /roguelite resume to fight on.",
        ).withStyle(ChatFormatting.YELLOW)

        is CatchResolution.Released -> Component.literal(
            "${resolution.released.species.name} is gone and your party is unchanged. " +
                "/roguelite resume to fight on.",
        ).withStyle(ChatFormatting.YELLOW)

        // Nothing was destroyed, so this is the one branch that is not red: it is a typo, and the
        // decision is still there to make.
        is CatchResolution.NoSuchSlot -> literal(
            "Your run party has ${resolution.partySize} Pokémon, so that slot is not one of them. " +
                "/roguelite catch lists them.",
        )
    }

    /** Species and level in one place, so the number is present everywhere a Pokémon is named. */
    private fun describe(pokemon: Pokemon): String = "${pokemon.species.name} (Lv ${pokemon.level})"

    fun confirmAbandon(wave: Int, party: Int): Component = Component.literal(
        "Abandon your run at wave $wave? Your $party run Pokémon are destroyed and your entry fee is " +
            "not refunded. Type /roguelite abandon confirm.",
    ).withStyle(ChatFormatting.RED)

    fun ended(report: RunEndReport): Component {
        val head = when (report.cause) {
            RunEndCause.CLEARED_FINAL_WAVE -> "You cleared the run at wave ${report.wave}."
            RunEndCause.REACHED_DEPTH_CAP -> "Your run ended at wave ${report.wave} — as deep as your badges allow."
            RunEndCause.RUN_LENGTH_SHORTENED -> "Your run ended at wave ${report.wave}; the run length changed under it."
            RunEndCause.PARTY_WIPED -> "Your run ended at wave ${report.wave}. Your party was wiped out."
            RunEndCause.PLAYER_ABANDONED -> "You abandoned your run at wave ${report.wave}."
        }
        val payout = when {
            report.delivery.delivered.isEmpty() && report.delivery.undelivered.isEmpty() -> " No payout."
            // Named specifically: the player has to know something is owed, or a delivery failure
            // reads to them as a payout that was never meant to exist.
            !report.delivery.complete ->
                " Paid ${report.delivery.delivered.size}; ${report.delivery.undelivered.size} could not be handed over — tell an operator."

            else -> " Paid ${report.delivery.delivered.size} reward(s)."
        }
        return literal(head + payout)
    }

    private fun literal(text: String): Component = Component.literal(text)
}
