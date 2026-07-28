package com.cobblemonroguelite.run

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * §2.10's two directions, which is the whole reason the decision is a pure function.
 *
 * Both failure modes are invisible in play — a penalty that never fires makes quitting a losing
 * battle free, and one that fires on our own restarts takes Pokémon off players who did nothing —
 * and neither is reproducible without a dropped connection. Here they are assertions.
 */
class DisconnectAttributionTest {

    private val boot = UUID.randomUUID()
    private val otherBoot = UUID.randomUUID()
    private val lead = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val third = UUID.randomUUID()

    private fun marker(onField: List<UUID> = listOf(lead), boot: UUID = this.boot) =
        RunBattleMarker(wave = 12, boot = boot, onField = onField)

    @Test
    fun `no marker is no battle`() {
        assertEquals(
            DisconnectVerdict.NoBattle,
            DisconnectAttribution.verdict(null, boot, listOf(lead, second)),
        )
    }

    @Test
    fun `a marker from another boot is the server's fault`() {
        val verdict = DisconnectAttribution.verdict(marker(boot = otherBoot), boot, listOf(lead, second))
        assertEquals(DisconnectVerdict.ServerRestarted(12), verdict)
    }

    @Test
    fun `a marker from this boot is the player's drop`() {
        val verdict = DisconnectAttribution.verdict(marker(), boot, listOf(lead, second))
        assertEquals(DisconnectVerdict.PlayerDropped(12, listOf(lead)), verdict)
    }

    @Test
    fun `casualties are the on-field Pokemon and nobody else`() {
        val verdict = DisconnectAttribution.verdict(marker(listOf(second)), boot, listOf(lead, second, third))
        assertEquals(listOf(second), assertIs<DisconnectVerdict.PlayerDropped>(verdict).casualties)
    }

    @Test
    fun `an on-field Pokemon that already died is not counted again`() {
        // It fainted before the drop, so permadeath has already taken it. Reporting it here would tell
        // the player they lost the same Pokémon twice.
        val verdict = DisconnectAttribution.verdict(marker(listOf(lead, second)), boot, listOf(second))
        assertEquals(listOf(second), assertIs<DisconnectVerdict.PlayerDropped>(verdict).casualties)
    }

    @Test
    fun `casualties come back in party order, not field order`() {
        // The controller kills by walking the party, and the message names them in the order it took
        // them. Field order is whatever the battle layer last reported and means nothing to a player.
        val verdict = DisconnectAttribution.verdict(marker(listOf(third, lead)), boot, listOf(lead, second, third))
        assertEquals(listOf(lead, third), assertIs<DisconnectVerdict.PlayerDropped>(verdict).casualties)
    }

    @Test
    fun `a whole-party field is a whole-party casualty list`() {
        // The wipe path: everything out, everything gone. The controller has to end the run through
        // the payout flow rather than leave a zero-party run loaded.
        val party = listOf(lead, second)
        val verdict = DisconnectAttribution.verdict(marker(party), boot, party)
        assertEquals(party, assertIs<DisconnectVerdict.PlayerDropped>(verdict).casualties)
    }

    @Test
    fun `an empty field costs nothing`() {
        val verdict = DisconnectAttribution.verdict(marker(emptyList()), boot, listOf(lead))
        assertEquals(emptyList(), assertIs<DisconnectVerdict.PlayerDropped>(verdict).casualties)
    }

    @Test
    fun `a fresh boot identity never matches the one before it`() {
        val first = ServerBootId.current
        ServerBootId.remint()
        // If these ever collided, every restart would read as a player drop.
        assertEquals(false, first == ServerBootId.current)
    }
}
