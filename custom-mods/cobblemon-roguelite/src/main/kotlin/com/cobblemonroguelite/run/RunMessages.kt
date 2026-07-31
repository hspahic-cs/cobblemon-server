package com.cobblemonroguelite.run

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonroguelite.starter.StarterCatalogue
import com.cobblemonroguelite.starter.StarterSelection
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.integration.RunTrainerBattles
import com.cobblemonroguelite.starter.StarterSelectionResult
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

    /**
     * §2.2-reversed, said out loud.
     *
     * The swap moves the player's whole team into their PC and puts the run's in its place, and until
     * now it did that in silence — which the first playtest read as the mode having done nothing, or
     * having lost something. Prior art (docs/roguelite-prior-art.md) notifies on screen for exactly
     * this move; this is that.
     *
     * Names the PC specifically, because the recovery for every possible failure below is "your
     * Pokémon are in your PC", and a player who has been told that once will look there.
     */
    fun partyStashed(count: Int): Component = literal(
        "Your $count Pokémon are safe in your PC for the run. You will get them back when it ends.",
    )

    // ------------------------------------------------------------------ §8 of the isolation design.
    // Silence is indistinguishable from failure; every message names numbers, because numbers are
    // what a player checks.

    fun stashStored(stacks: Int): Component = literal(
        "Stored your $stacks item stack(s), your gear and your XP — they return when you leave the run.",
    )

    fun stashReturned(stacks: Int): Component = literal("Returned your $stacks item stack(s).")

    fun stashResidue(count: Int): Component = literal(
        "$count of your items could not be restored (a mod may have been removed). They are kept " +
            "safe — an operator can recover them.",
    )

    fun stashQuarantined(count: Int): Component = literal(
        "$count item(s) acquired during the run were set aside for review — an operator will return " +
            "anything that is yours.",
    )

    /** Row 3. Names the swapId because it is the one string an operator can act on. */
    fun stashAlarm(swapId: String): Component = literal(
        "Your stored items cannot be found. Nothing has been touched. Contact an operator — " +
            "reference $swapId.",
    )

    fun stashRolledBack(): Component = literal(
        "Your items could not be returned just now — nothing is lost; it will retry, or relog.",
    )

    fun stashRefused(reason: String): Component =
        literal("The run cannot start: $reason.")

    fun pausedFully(wave: Int): Component = literal(
        "Run paused. Your items and party are back; your run is saved at wave $wave.",
    )

    fun displacedExit(): Component = literal(
        "You left the arena — your run is paused and your items are back.",
    )

    fun commandRefusedDuringRun(): Component = literal(
        "That command is disabled during a run. /roguelite pause first — your run keeps its progress.",
    )

    fun enderChestRefusedDuringRun(): Component = literal(
        "Your ender chest is out of reach during a run.",
    )

    fun partyReturned(count: Int): Component =
        literal("Your run is over and your $count Pokémon are back in your party.")

    /**
     * Some of their team could not be put back and is still in the PC.
     *
     * Told rather than logged, because the player counts their party, finds it short, and concludes
     * the mode ate one. It is recoverable and automatic — the next login retries — but only somebody
     * who knows that will wait rather than panic.
     */
    fun partyStranded(count: Int): Component = literal(
        "$count of your Pokémon could not be moved back into your party and are still in your PC. " +
            "Nothing is lost — log out and back in and it will try again.",
    )

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

    /**
     * No longer says "your run is paid for": under §2.13's budget the catalogue is checked *before*
     * the fee ([RunStart]), so a player who sees this has been charged nothing.
     */
    fun noStarters(): Component = literal(
        "This server has no startable Pokémon configured, so a run cannot begin. Nothing has been " +
            "taken — tell an operator.",
    )

    /** The catalogue is fine and the budget is set below everything in it. Names both numbers. */
    fun noAffordableStarters(refusal: RunStartRefusal.NoAffordableStarters): Component = literal(
        "The starting budget is ${refusal.budget} point(s) and the cheapest Pokémon on this server " +
            "costs ${refusal.cheapest}, so no team can be bought. Nothing has been taken — tell an operator.",
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

    /**
     * The catalogue, priced (§2.13).
     *
     * Says "up to" rather than naming a team size, because there is no fixed one — at these prices a
     * budget buys two or three Pokémon and a party reaches six by catching, and a message that
     * implied otherwise would read as a bug the first time somebody could only afford two.
     *
     * Unaffordable options are listed and marked rather than hidden. A player who cannot yet afford
     * something should be able to see what they are saving discounts for; hiding it would make the
     * candy reductions in §2.15 invisible until the moment they had already paid off.
     */
    fun catalogue(catalogue: StarterCatalogue): Component {
        val budget = catalogue.budget
        val listed = catalogue.options.joinToString(", ") { option ->
            val price = "${option.species} (${option.cost})"
            if (option.cost <= budget) price else "$price too dear"
        }
        return literal(
            "You have $budget point(s) to spend on up to ${StarterSelection.MAX_STARTERS} Pokémon — " +
                "buy them with /roguelite starter <species> [more...]. Spending less is fine, and " +
                "catching is how the rest of your party arrives. Available: $listed",
        )
    }

    fun started(team: List<String>, spent: Int, remaining: Int): Component {
        val unspent = if (remaining > 0) " $remaining point(s) unspent." else ""
        return literal(
            // Read from the config rather than written out. It said "level 1" and the starters now
            // begin at 5, which is exactly how a message becomes a lie nobody notices.
            "Your run begins with ${team.joinToString(", ")} at level ${RunSettings.current.starterLevel} " +
                "for $spent point(s).$unspent " +
                "/roguelite resume to fight wave 1.",
        )
    }

    /**
     * Why a proposed team was refused. One function over the whole result so that adding a refusal
     * without wording it fails to compile — a refusal the player is shown as silence is a refusal
     * they cannot act on, and this is the command layer's only chance to say anything.
     */
    fun starterRejected(reason: StarterSelectionResult): Component = when (reason) {
        is StarterSelectionResult.Accepted ->
            // Not reachable through the command path, and worded rather than thrown: a crash here
            // would cost the player their session over a message they were never meant to see.
            literal("That team is fine. /roguelite status shows where you stand.")

        StarterSelectionResult.Empty ->
            literal("Name at least one Pokémon. /roguelite status shows what you can afford.")

        is StarterSelectionResult.NotEligible -> literal(
            "Not available to you: ${reason.species.joinToString(", ")}. Catching a species on the " +
                "server unlocks it for future runs; legendaries never unlock. /roguelite status lists yours.",
        )

        is StarterSelectionResult.Unpriced -> literal(
            "This server has no price set for ${reason.species.joinToString(", ")}, so they cannot be " +
                "bought. Tell an operator — your run is still paid for.",
        )

        is StarterSelectionResult.Duplicate ->
            literal("You can only take one ${reason.species}. Pick a different second Pokémon.")

        is StarterSelectionResult.TooMany ->
            literal("A party holds ${reason.max}. Pick fewer — the rest of it comes from catching.")

        is StarterSelectionResult.OverBudget -> literal(
            "That team costs ${reason.spent} and you have ${reason.budget}. Drop one, or trade down " +
                "— unspent points are allowed.",
        )
    }

    fun speciesUnavailable(species: Any): Component = literal(
        "$species could not be created on this server, so none of that team was bought. Pick again " +
            "— your run is still paid for.",
    )

    /**
     * §2.24, at the moment the arena becomes somewhere else.
     *
     * Names the place and nothing else — not the wave, not the band, not how many waves it lasts.
     * The message exists because the world visibly changed and an unexplained change reads as a
     * glitch; a player who wants the numbers has `/roguelite status`. Green because it is the one
     * unambiguously good thing this mode says between waves.
     */
    fun enteredBiome(name: String): Component =
        Component.literal("You have arrived in $name.").withStyle(ChatFormatting.GREEN)

    /**
     * §2.25, said to the player whose run it is.
     *
     * Told rather than hidden, and that is the decision: the override is an operator's act on a
     * player's run, and a player who is quietly playing an uncapped run has no way to know their
     * result is not comparable with anyone else's. The wording puts it on the run rather than on
     * them — they did not cheat, the gate was lifted.
     */
    fun depthOverridden(): Component = Component.literal(
        "This run was started with the badge gate lifted by an operator, so its depth is not earned " +
            "and it is recorded that way.",
    ).withStyle(ChatFormatting.YELLOW)

    fun atWave(wave: Int, party: Int, depthCap: Int?): Component {
        val cap = depthCap?.let { " (your badges allow $it)" } ?: ""
        return literal("Wave $wave$cap, $party Pokémon alive.")
    }

    /**
     * Said when the wave handler refuses.
     *
     * ### It used to claim battles were not implemented, and that cost an evening
     *
     * The old wording was "run battles are not implemented on this server yet", said for *every*
     * refusal. On the first server that got far enough to fight, battles were implemented and
     * installed — the actual cause was an unconfigured wild pool — and the message sent both the
     * player and the next reader looking at the wrong layer entirely. A message that names a cause it
     * has not checked is worse than one that names none.
     *
     * So it now distinguishes the one thing this layer can actually know ([RunWaves.isImplemented])
     * from everything else, and in the everything-else case says only what is true: the wave did not
     * start, the run is intact, and the reason is in the log. Nothing here can be more specific
     * without the handler reporting *why* it refused, which is a wider change than this deserves —
     * [com.cobblemonroguelite.run.RunWaveHandler] returns a boolean by design.
     */
    fun waveUnavailable(wave: Int, kind: RunOpponent? = null): Component =
        // A trainer or boss wave on a server with no trainer provider is not a fault and not a mystery
        // — it is §2.6's licence question, unresolved. Saying so beats sending the player to an
        // operator who will find "the trainer battle provider is not implemented" in the log and have
        // nothing to do about it. This is the third rewording of this message: the first two both
        // named a cause they had not checked.
        if (kind != null && kind != RunOpponent.WILD && !RunTrainerBattles.isImplemented()) {
            literal(
                "Wave $wave is a trainer battle, and trainer waves are not available on this server yet " +
                    "— only wild waves are. Your run is safe and stays on this wave.",
            )
        } else if (!RunWaves.isImplemented()) {
            literal("Wave $wave cannot be started — run battles are not implemented on this server yet. Your run is safe.")
        } else {
            literal(
                "Wave $wave could not be started. Your run is safe and nothing was lost — tell an " +
                    "operator, who will find the reason in the server log.",
            )
        }

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
            "No battle in progress. Your paid start is saved and your starting budget will be waiting — " +
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
