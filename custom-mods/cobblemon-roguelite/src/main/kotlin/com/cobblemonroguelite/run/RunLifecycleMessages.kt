package com.cobblemonroguelite.run

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * What the mode says about a run *between* sessions — §2.23's two subjects.
 *
 * Kept apart from [RunMessages], which is everything said to a player who is doing something. These
 * lines are all answers to "what happened while I was away", and they share a job that the rest of the
 * vocabulary does not have: each one explains a state change the player did not witness and cannot
 * otherwise account for. A run that has moved, or a run that is gone, with no sentence attached, reads
 * as the server having lost something.
 *
 * Plain [Component.literal] English, for [RunMessages]'s reason: there is no language file to key
 * against and a half-translated mod is worse than an untranslated one.
 */
object RunLifecycleMessages {

    /**
     * Said to a player who logged out inside an arena and has been put back outside it.
     *
     * It exists because the alternative is silent teleportation. Under §2.23 the arena was handed to
     * whoever needed it next, so the player has to be moved on login — and being moved without
     * explanation is exactly how somebody concludes the mode is broken and abandons a run that is
     * perfectly intact. So the line says all three things: they were moved, the run is fine, and how to
     * get back in.
     */
    fun returnedFromArena(): Component = Component.literal(
        "Run arenas are handed back when you log out, so you have been returned to where you started. " +
            "Your run is untouched — /roguelite resume goes back in.",
    ).withStyle(ChatFormatting.GRAY)

    /**
     * §2.23's warning, on login, for a run that is close to being discarded.
     *
     * Names the wave, the days left and what to do about it. The wave is there so the player can weigh
     * it — "wave 4" and "wave 140" deserve different reactions to the same sentence — and the remedy is
     * there because the rule is not guessable: playing a wave resets the clock and logging in does not,
     * which is the one thing about expiry a player has to know.
     */
    fun expiringSoon(wave: Int, status: RunExpiryStatus): Component = Component.literal(
        "Your run at wave $wave has not been played for ${status.idleDays} day(s) and will be " +
            "discarded in ${status.remainingDays}. Playing a wave keeps it — logging in does not.",
    ).withStyle(ChatFormatting.YELLOW)

    /**
     * Said once, when a player returns to find the run gone.
     *
     * Says that nothing was paid, which is the part that would otherwise be assumed. A run ending
     * normally pays a table (§2.20), so a player who is only told "your run expired" has every reason
     * to go looking for a payout that was never owed — and to report its absence as a bug. Saying it
     * plainly is cheaper than answering it later.
     */
    fun expired(notice: RunExpiryNotice): Component = Component.literal(
        "Your run at wave ${notice.wave} was discarded — it had not been played for " +
            "${notice.idleDays} day(s). Untouched runs are not kept forever and expiry pays nothing. " +
            "/roguelite start begins a new one.",
    ).withStyle(ChatFormatting.RED)
}
