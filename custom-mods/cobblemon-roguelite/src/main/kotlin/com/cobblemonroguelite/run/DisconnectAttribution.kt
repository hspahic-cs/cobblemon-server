package com.cobblemonroguelite.run

import java.util.UUID

/** What an interrupted battle turns out to have been. */
sealed interface DisconnectVerdict {

    /** Nothing was in progress. The overwhelmingly common login. */
    data object NoBattle : DisconnectVerdict

    /** The marker outlived the process that wrote it, so the interruption was ours. No penalty. */
    data class ServerRestarted(val wave: Int) : DisconnectVerdict

    /**
     * Same process, so the connection that went away was the player's.
     *
     * @property casualties the on-field Pokémon that are **still in the party**, in party order.
     *   Filtered here rather than at the kill so that what we report is what we actually took: an
     *   on-field Pokémon that already fainted before the drop is gone by other means, and counting it
     *   would tell the player they lost something twice.
     */
    data class PlayerDropped(val wave: Int, val casualties: List<UUID>) : DisconnectVerdict
}

/**
 * §2.10's decision, with nothing of the game in it: whose fault was the interruption, and what does
 * it cost.
 *
 * ### Why this is a pure function and not three lines inside the controller
 *
 * It is the whole of the decision that can be got wrong invisibly. Both mistakes — penalising a
 * player for our restart, and never firing so that quitting a losing battle is free — look identical
 * from inside the controller, need a booted server and a dropped connection to reproduce, and would
 * be discovered by a player rather than by us. Here they are two test cases.
 *
 * The controller keeps everything that needs a server: killing, checkpointing, ending the run.
 */
object DisconnectAttribution {

    /**
     * @param marker the run's battle-in-progress marker, or null if it was between waves.
     * @param boot [ServerBootId.current]. Passed in rather than read here so the comparison can be
     *   tested both ways; nothing else may supply it.
     * @param party the live run party's UUIDs, in party order.
     */
    fun verdict(marker: RunBattleMarker?, boot: UUID, party: List<UUID>): DisconnectVerdict {
        if (marker == null) return DisconnectVerdict.NoBattle
        // Inequality is the "not the player's fault" side, and it is the side that has to be the
        // default for anything unexpected: a marker from a boot we cannot recognise is a marker we
        // did not write, and the only honest reading of it is that this process was not there.
        if (marker.boot != boot) return DisconnectVerdict.ServerRestarted(marker.wave)
        val onField = marker.onField.toSet()
        return DisconnectVerdict.PlayerDropped(marker.wave, party.filter { it in onField })
    }
}
