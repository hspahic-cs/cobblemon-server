package com.cobblemonroguelite.run

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
