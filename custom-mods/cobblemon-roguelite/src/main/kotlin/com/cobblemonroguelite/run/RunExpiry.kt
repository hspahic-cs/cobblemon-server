package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * One retention band: a run at [minWave] or deeper, and not yet covered by a deeper band, is kept for
 * [days] after the last wave it played.
 *
 * Bands rather than a formula because the shape §2.23 asks for is not smooth — "a wave-3 run is worth
 * nothing, a wave-150 run is many hours of play" is a statement about a handful of thresholds, and a
 * curve fitted to it would only add a second thing to get wrong when an operator wants wave 100 to
 * mean six months exactly.
 *
 * @property minWave inclusive. Read bottom-up: the deepest band whose [minWave] the run has reached
 *   wins, which is the opposite convention to [com.cobblemonroguelite.arena.ArenaBand] and is why
 *   [RunExpiryPolicy] enforces the ordering rather than trusting the author to write them in order.
 * @property days how long a run in this band survives without being played.
 */
data class RunExpiryBand(val minWave: Int, val days: Int) {
    init {
        require(minWave >= 1) { "minWave must be at least 1, was $minWave" }
        require(days >= 1) { "a retention band of $days day(s) would expire runs mid-session" }
    }
}

/**
 * §2.23's expiry curve: how long an untouched run is kept, by how deep it got.
 *
 * ### What this is for, and what it is not for
 *
 * Storage hygiene. It was **not** capacity management even before the arena lease became a per-session
 * thing, and it is emphatically not now: a run is a handful of Pokémon and some counters, a player
 * holds one at a time, and arenas are handed back at logout
 * ([com.cobblemonroguelite.arena.RunArenas.release]). Nothing is starved by a run sitting on disk, so
 * the periods here can be — and are — generous. Anyone tempted to shorten them to free something up
 * should check what they think is being freed.
 *
 * ### Why it pays nothing
 *
 * An expired run is deleted, not ended-and-paid. Six months of silence is not owed a payout, and a
 * payout would land on a player who is by definition not there — which is
 * [com.cobblemonroguelite.payout.PendingPayoutStore]'s problem, and paying into it here would create a
 * debt out of an absence.
 *
 * This is deliberately **not** the same answer [com.cobblemonroguelite.payout.PendingPayoutLedger]
 * gives, and the two must not be unified. A held payout is already owed: earned, resolved, and waiting
 * only because the server chose the moment of delivery. An untouched run is owed nothing at all. Same
 * word, opposite obligations.
 *
 * @property bands checked deepest-first. Must start at wave 1 so that every run matches something, and
 *   must not let a deeper run expire sooner than a shallower one — an inversion there would delete a
 *   wave-150 run while keeping the wave-3 run beside it, which is the failure the whole curve exists to
 *   prevent and which nothing downstream would notice.
 */
data class RunExpiryPolicy(val bands: List<RunExpiryBand> = DEFAULT_BANDS) {

    init {
        require(bands.isNotEmpty()) { "an expiry policy needs at least one band" }
        require(bands.first().minWave == 1) {
            "the first band must start at wave 1 or a shallow run matches nothing; it starts at " +
                "${bands.first().minWave}"
        }
        bands.zipWithNext { shallower, deeper ->
            require(deeper.minWave > shallower.minWave) {
                "expiry bands must be in ascending wave order; $deeper follows $shallower"
            }
            require(deeper.days >= shallower.days) {
                "band $deeper keeps a deeper run for less time than $shallower does, so reaching wave " +
                    "${deeper.minWave} would make a run expire sooner"
            }
        }
    }

    /**
     * The retention period for a run at [wave], in whole days.
     *
     * A wave below 1 falls to the shallowest band rather than throwing. Nothing should produce one —
     * [RunState.fromNbt] discards a checkpoint with `wave < 1` — but this is called from a sweep that
     * walks every run on the server, and one hand-edited file must not take the sweep down and leave
     * every other stale run in place.
     */
    fun daysFor(wave: Int): Int = (bands.lastOrNull { wave >= it.minWave } ?: bands.first()).days

    fun periodMillisFor(wave: Int): Long = TimeUnit.DAYS.toMillis(daysFor(wave).toLong())

    /**
     * How close to the end the login warning starts: a quarter of the period, capped at a fortnight.
     *
     * A fraction rather than a fixed window because the periods span 7 days to 180, and a fixed
     * fortnight would warn a shallow run's owner before the run was even at risk — which trains people
     * to ignore the line. The cap is there for the other end: "your run expires in 45 days" is not a
     * warning, it is trivia, and the one that matters is the last one they will see.
     */
    fun warnWithinMillisFor(wave: Int): Long =
        minOf(periodMillisFor(wave) / 4, TimeUnit.DAYS.toMillis(MAX_WARNING_DAYS))

    companion object {

        /**
         * The shipped curve. Unlike most numbers in this module these are decisions rather than
         * placeholders — §2.23 fixes both ends of it — though an operator is free to retune them.
         *
         * - **Waves 1–9, a week.** The first biome band. A run abandoned here is a starter team and
         *   nothing else; the player has lost minutes and can buy another.
         * - **Waves 10–24, a month.** Past the first transition, so the run has caught something and
         *   survived a boss. A month covers an ordinary lapse in playing.
         * - **Waves 25–49, two months** and **50–99, three**. The middle of a 200-wave ladder is where
         *   a run stops being replaceable — the party is the product of a specific seed and a specific
         *   set of catches, and none of it can be reconstructed.
         * - **Wave 100 and deeper, six months**, which is §2.23's own number. Half a ladder is many
         *   hours, and the run should outlive any absence a person can explain.
         */
        val DEFAULT_BANDS: List<RunExpiryBand> = listOf(
            RunExpiryBand(minWave = 1, days = 7),
            RunExpiryBand(minWave = 10, days = 30),
            RunExpiryBand(minWave = 25, days = 60),
            RunExpiryBand(minWave = 50, days = 90),
            RunExpiryBand(minWave = 100, days = 180),
        )

        /** The fortnight [warnWithinMillisFor] caps at. */
        const val MAX_WARNING_DAYS = 14L
    }
}

/**
 * Where a run stands against its retention period.
 *
 * Carries the numbers rather than a boolean because both consumers need them: the warning has to say
 * how long is left, and the log line for a deletion has to say how long the run had been untouched and
 * what the period was — otherwise "expired" is unanswerable when somebody disputes it.
 *
 * @property idleMillis how long since the run was last played.
 * @property periodMillis the retention period its depth earned it.
 */
data class RunExpiryStatus(val idleMillis: Long, val periodMillis: Long, val warnWithinMillis: Long) {

    val expired: Boolean get() = idleMillis >= periodMillis

    /** Never negative: a run past its period reports zero rather than a negative countdown. */
    val remainingMillis: Long get() = (periodMillis - idleMillis).coerceAtLeast(0L)

    /** Worth telling the player about on login. False once [expired] — there is nothing left to warn. */
    val nearExpiry: Boolean get() = !expired && remainingMillis <= warnWithinMillis

    /**
     * Days left, rounded **up**, so a run with six hours to live reports "1 day" rather than "0 days".
     * Rounding down would print a warning that says the run is already gone while it is still there.
     */
    val remainingDays: Long get() = ceilDays(remainingMillis)

    val idleDays: Long get() = TimeUnit.MILLISECONDS.toDays(idleMillis)

    val periodDays: Long get() = TimeUnit.MILLISECONDS.toDays(periodMillis)
}

private fun ceilDays(millis: Long): Long {
    val day = TimeUnit.DAYS.toMillis(1)
    return (millis + day - 1) / day
}

/**
 * §2.23's expiry decision, with no clock and no store in it.
 *
 * Every function here takes `now` rather than reading [System.currentTimeMillis], which is the only
 * reason any of this is testable: the alternative is a rule that decides whether to delete somebody's
 * run and has never been executed outside a booted server.
 */
object RunExpiry {

    /**
     * Where [wave]'s run stands, given when it was last played.
     *
     * A [lastActiveAtEpochMs] in the future — a clock that went backwards, a save moved between
     * machines — yields a negative idle time, which reports as not expired. That is the right direction:
     * a wrong clock must not be able to delete runs, and the condition heals as soon as the clock does.
     */
    fun evaluate(
        wave: Int,
        lastActiveAtEpochMs: Long,
        nowEpochMs: Long,
        policy: RunExpiryPolicy,
    ): RunExpiryStatus = RunExpiryStatus(
        idleMillis = nowEpochMs - lastActiveAtEpochMs,
        periodMillis = policy.periodMillisFor(wave),
        warnWithinMillis = policy.warnWithinMillisFor(wave),
    )

    fun evaluate(run: RunState, nowEpochMs: Long, policy: RunExpiryPolicy): RunExpiryStatus =
        evaluate(run.wave, run.lastActiveAtEpochMs, nowEpochMs, policy)

    /**
     * The activity stamp a checkpoint restores with, given the raw value in the tag.
     *
     * A function rather than one `takeIf` inside [RunState.fromNbt] because of what it defends against
     * and where that path can be run: `getLong` answers **0** for an absent or damaged key, 0 is January
     * 1970, and 1970 is expired under every band in [RunExpiryPolicy] — so a truncated file would have
     * the sweep silently delete a wave-150 run at the next boot. The restore path itself needs a booted
     * server to reach (it loads Pokémon out of a populated registry), so left inline this rule would
     * ship never having been executed.
     *
     * Reading a missing stamp as "now" costs at most one retention period of storage and cannot destroy
     * anything, which is the only acceptable direction for a default here.
     */
    fun restoreStamp(rawEpochMs: Long, nowEpochMs: Long): Long =
        if (rawEpochMs > 0L) rawEpochMs else nowEpochMs

    /**
     * Which of [runs] are past their period, and by how much. Empty on essentially every call.
     *
     * The sweep's whole decision, separated from the sweep so that "which runs get deleted" can be
     * exercised without a world on disk. The caller does the deleting, because deleting is the part
     * that needs a server.
     */
    fun stale(
        runs: Map<UUID, RunState>,
        nowEpochMs: Long,
        policy: RunExpiryPolicy,
    ): Map<UUID, RunExpiryStatus> = runs
        .mapValues { (_, run) -> evaluate(run, nowEpochMs, policy) }
        .filterValues { it.expired }
}

/**
 * The record left behind when a run was deleted while its owner was away, so that they can be told.
 *
 * ### Why this is persisted at all
 *
 * Because the sweep runs at server start, and the person it affects is by definition not there. Without
 * a record, a player returns to `/roguelite status` saying "you have no run" — which is
 * indistinguishable from never having had one, and reads as the server having eaten it. §2.23 asks for
 * expiry to be *said plainly*, and a message needs something to survive the gap.
 *
 * It is not a debt and must not grow into one. It holds three numbers, it is dropped the moment it is
 * delivered, and one that is never delivered is discarded by [RunStore] once it is older than any run
 * would have been — the notice about an expiry outliving the run it describes would be the same
 * accumulation the expiry was written to stop.
 *
 * @property wave how deep the run got. The one number a returning player actually wants.
 * @property idleDays how long it had gone untouched, so the message can be checked rather than
 *   believed.
 * @property expiredAtEpochMs when it was deleted, used only to age the notice out.
 */
data class RunExpiryNotice(val wave: Int, val idleDays: Long, val expiredAtEpochMs: Long) {

    fun toNbt(): CompoundTag = CompoundTag().apply {
        putInt("wave", wave)
        putLong("idleDays", idleDays)
        putLong("expiredAt", expiredAtEpochMs)
    }

    companion object {
        fun of(wave: Int, status: RunExpiryStatus, nowEpochMs: Long): RunExpiryNotice =
            RunExpiryNotice(wave, status.idleDays, nowEpochMs)

        /** Null on a damaged tag: an undeliverable notice is a missing sentence, not a broken store. */
        fun fromNbt(tag: CompoundTag): RunExpiryNotice? {
            val wave = tag.getInt("wave")
            if (wave < 1) return null
            return RunExpiryNotice(wave, tag.getLong("idleDays"), tag.getLong("expiredAt"))
        }
    }
}
