package com.cobblemonroguelite.run

import java.util.UUID

/**
 * Which server process this is. The unforgeable half of §2.10's disconnect attribution.
 *
 * ### What it is for
 *
 * A run stamped with a battle in progress ([RunBattleMarker]) has to be told apart from a run whose
 * battle was interrupted *by us*. The whole decision rests on that: a player whose connection dropped
 * mid-battle pays for it, and a player whose battle died because an operator restarted the server
 * pays nothing. The only thing that distinguishes those two on reconnect is whether the process that
 * wrote the marker is the process reading it.
 *
 * ### Why it is minted, and not read from anything
 *
 * Nothing here comes from the client, from the world save, or from a field the player can influence —
 * it is a fresh random value per process. That is the property §2.10 needs: a player who could make
 * the stored id differ from the live one would have a free escape from every losing battle, and one
 * who could make it match would be able to inflict the penalty on someone else. A random UUID cannot
 * be predicted or replayed, and it never leaves the server: it is written into the run file and
 * compared against this value, and is not sent to any client.
 *
 * A wall-clock start time would have worked as well, but it collides across a restart-in-the-same-
 * second and is the kind of thing someone later "fixes" by rounding.
 *
 * ### Minted twice, on purpose
 *
 * The field initializer runs at class load — once per JVM, before any run can be read — so this is
 * never absent. An absent boot identity would have to fail one way or the other, and both are wrong:
 * penalise everybody for our restarts, or nobody for their own drops. [remint] then runs at server
 * start, which matters only where a JVM hosts more than one server lifetime (an integrated server
 * reopening a world). There, a reopened world genuinely *is* a new boot, and carrying the old id over
 * would attribute the shutdown to the player.
 */
object ServerBootId {

    @Volatile
    private var id: UUID = UUID.randomUUID()

    val current: UUID get() = id

    /** Called from [RunLoginHooks] on server start. Never call this while runs are loaded. */
    fun remint() {
        id = UUID.randomUUID()
    }
}
