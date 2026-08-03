package com.cobblemonroguelite.run

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * §2.23's expiry curve, which decides whether somebody's run is deleted.
 *
 * Worth testing at this length because of what it does when it is wrong. Every other decision in this
 * module fails visibly — a wave will not start, a payout is empty, an arena is missing a floor — and
 * this one fails by removing a party the player cannot get back, silently, while they are not
 * connected. There is nothing to notice and nothing to restore from.
 *
 * All of it runs without a server because [RunExpiry] takes `now` as a parameter rather than reading
 * the clock, which is the reason the rule can be executed at all outside a booted game.
 */
class RunExpiryTest {

    private val policy = RunExpiryPolicy()

    private fun days(n: Long): Long = TimeUnit.DAYS.toMillis(n)

    private fun run(wave: Int, idleDays: Long, now: Long = NOW): RunState =
        RunState(wave = wave, seed = 1L, lastActiveAtEpochMs = now - days(idleDays))

    @Test
    fun `the shipped curve keeps a wave 100 run for six months`() {
        // §2.23 names this number directly. It is the one value in the band table that is quoted from
        // the decision rather than interpolated between its two ends.
        assertEquals(180, policy.daysFor(100))
        assertEquals(180, policy.daysFor(200))
    }

    @Test
    fun `the curve is a step per depth tier`() {
        assertEquals(7, policy.daysFor(1))
        assertEquals(7, policy.daysFor(9))
        assertEquals(30, policy.daysFor(10))
        assertEquals(30, policy.daysFor(24))
        assertEquals(60, policy.daysFor(25))
        assertEquals(90, policy.daysFor(50))
        assertEquals(90, policy.daysFor(99))
    }

    @Test
    fun `going deeper never shortens the period`() {
        // The property the whole curve exists for, checked across every wave of a 200-wave run rather
        // than at the boundaries. An inversion would delete a wave-150 run while keeping the wave-3 run
        // beside it, and nothing downstream would report it as anything but an expiry.
        var previous = 0
        for (wave in 1..200) {
            val period = policy.daysFor(wave)
            assertTrue(period >= previous, "wave $wave keeps a run for $period days, less than $previous")
            previous = period
        }
    }

    @Test
    fun `a band list that shortens with depth is refused at construction`() {
        assertFailsWith<IllegalArgumentException> {
            RunExpiryPolicy(listOf(RunExpiryBand(1, days = 90), RunExpiryBand(50, days = 30)))
        }
    }

    @Test
    fun `a band list that does not start at wave one is refused`() {
        // Every run has to match a band. A list starting at wave 10 would leave waves 1-9 falling back
        // to whatever the lookup happened to do, which is exactly the sort of hole that is discovered
        // by a deletion.
        assertFailsWith<IllegalArgumentException> { RunExpiryPolicy(listOf(RunExpiryBand(10, days = 30))) }
    }

    @Test
    fun `a shallow run abandoned for a fortnight is gone and a deep one is not`() {
        // The comparison §2.23 is actually about: same absence, different worth. Two weeks is well past
        // a wave-3 run's week and nowhere near a wave-150 run's six months.
        assertTrue(RunExpiry.evaluate(run(wave = 3, idleDays = 14), NOW, policy).expired)
        assertFalse(RunExpiry.evaluate(run(wave = 150, idleDays = 14), NOW, policy).expired)
    }

    @Test
    fun `a run expires the moment its period is reached, not a day later`() {
        assertFalse(RunExpiry.evaluate(run(wave = 1, idleDays = 6), NOW, policy).expired)
        assertTrue(RunExpiry.evaluate(run(wave = 1, idleDays = 7), NOW, policy).expired)
    }

    @Test
    fun `logging in is not activity, so only the stamp moves the deadline`() {
        // The rule stated as an assertion. `lastActiveAtEpochMs` is the only input, and nothing in the
        // login path writes it — a player who connects daily and never fights still loses the run.
        val idle = run(wave = 3, idleDays = 30)
        assertTrue(RunExpiry.evaluate(idle, NOW, policy).expired)
        idle.lastActiveAtEpochMs = NOW - days(1)
        assertFalse(RunExpiry.evaluate(idle, NOW, policy).expired)
    }

    @Test
    fun `a clock that went backwards cannot delete anything`() {
        // A save moved between machines, or an NTP correction. Negative idle time reports as live,
        // which is the only safe direction: a wrong clock must not be able to destroy runs, and the
        // condition heals as soon as the clock does.
        val future = RunState(wave = 150, seed = 1L, lastActiveAtEpochMs = NOW + days(30))
        assertFalse(RunExpiry.evaluate(future, NOW, policy).expired)
        assertEquals(0L, RunExpiry.evaluate(future, NOW, policy).remainingMillis.coerceAtMost(0L))
    }

    @Test
    fun `the warning window is a quarter of the period, capped at a fortnight`() {
        // Fixed windows fail at both ends of a 7-to-180-day range: a fortnight would warn a shallow
        // run's owner before it was at risk, and a quarter of six months is trivia rather than a
        // warning.
        assertEquals(days(7) / 4, policy.warnWithinMillisFor(1))
        assertEquals(days(14), policy.warnWithinMillisFor(100))
    }

    @Test
    fun `a deep run warns in its last fortnight and not before`() {
        assertFalse(RunExpiry.evaluate(run(wave = 100, idleDays = 165), NOW, policy).nearExpiry)
        assertTrue(RunExpiry.evaluate(run(wave = 100, idleDays = 170), NOW, policy).nearExpiry)
    }

    @Test
    fun `an already expired run does not also report as near expiry`() {
        // The two are consumed by different sentences, and a login that said both — "it will be
        // discarded in 0 days" and "it was discarded" — would read as a bug in whichever came second.
        val status = RunExpiry.evaluate(run(wave = 100, idleDays = 400), NOW, policy)
        assertTrue(status.expired)
        assertFalse(status.nearExpiry)
    }

    @Test
    fun `the countdown rounds up so a run with hours left is not reported as gone`() {
        // Rounding down prints "discarded in 0 days" about a run that is still there, which is worse
        // than a day's imprecision in the other direction.
        val status = RunExpiry.evaluate(
            RunState(wave = 1, seed = 1L, lastActiveAtEpochMs = NOW - days(7) + TimeUnit.HOURS.toMillis(6)),
            NOW,
            policy,
        )
        assertFalse(status.expired)
        assertEquals(1L, status.remainingDays)
    }

    @Test
    fun `the sweep picks out only the stale runs`() {
        // The whole of the sweep's decision, exercised without a world: the caller does the deleting.
        val fresh = UUID.randomUUID()
        val shallow = UUID.randomUUID()
        val deep = UUID.randomUUID()
        val stale = RunExpiry.stale(
            mapOf(
                fresh to run(wave = 3, idleDays = 1),
                shallow to run(wave = 3, idleDays = 60),
                deep to run(wave = 150, idleDays = 60),
            ),
            NOW,
            policy,
        )
        assertEquals(setOf(shallow), stale.keys)
        assertEquals(60L, stale.getValue(shallow).idleDays)
        assertEquals(7L, stale.getValue(shallow).periodDays)
    }

    @Test
    fun `an empty server sweeps to nothing`() {
        assertTrue(RunExpiry.stale(emptyMap(), NOW, policy).isEmpty())
    }

    @Test
    fun `a nonsensical wave falls to the shallowest band instead of throwing`() {
        // Reachable only from a hand-edited checkpoint, and it must not take the sweep down: one bad
        // file would otherwise leave every other stale run in place forever.
        assertEquals(7, policy.daysFor(0))
        assertEquals(7, policy.daysFor(-1))
    }

    @Test
    fun `a checkpoint with no activity stamp restores as active now, not as epoch zero`() {
        // The one direction this field must never fail in. `getLong` answers 0 for an absent key, 0 is
        // January 1970, and 1970 is expired under every band — so a truncated or hand-edited checkpoint
        // would have the next sweep silently delete a deep run. The restore path needs a booted server,
        // which is why this rule is a function here rather than a `takeIf` inside RunState.fromNbt.
        assertEquals(NOW, RunExpiry.restoreStamp(rawEpochMs = 0L, nowEpochMs = NOW))
        assertEquals(NOW, RunExpiry.restoreStamp(rawEpochMs = -5L, nowEpochMs = NOW))
        assertEquals(1L, RunExpiry.restoreStamp(rawEpochMs = 1L, nowEpochMs = NOW))
        assertFalse(
            RunExpiry.evaluate(
                RunState(wave = 150, seed = 1L, lastActiveAtEpochMs = RunExpiry.restoreStamp(0L, NOW)),
                NOW,
                policy,
            ).expired,
        )
    }

    @Test
    fun `a notice carries what the player has to be told`() {
        val status = RunExpiry.evaluate(run(wave = 42, idleDays = 100), NOW, policy)
        val notice = RunExpiryNotice.of(wave = 42, status = status, nowEpochMs = NOW)
        assertEquals(notice, RunExpiryNotice.fromNbt(notice.toNbt()))
        assertEquals(100L, notice.idleDays)
    }

    private companion object {
        /** A fixed instant, so nothing here depends on when it is run. */
        const val NOW = 1_800_000_000_000L
    }
}
