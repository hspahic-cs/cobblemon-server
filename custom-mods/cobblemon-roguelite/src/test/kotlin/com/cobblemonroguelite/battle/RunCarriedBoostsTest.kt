package com.cobblemonroguelite.battle

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The protocol replay, pinned against REAL line shapes.
 *
 * The ident and line formats below are copied from an actual saved production battle log
 * (`/opt/cobblemon-dev/battle_logs/98126a41….txt`, 2026-07-31) — `p1a: <uuid>` idents, per-line
 * entries, bare `update`/`|t:|` noise between them. Three capture wirings failed before this one
 * because each was built on an assumption about Cobblemon's internals that nothing checked; this
 * test is the check, and any future format drift fails HERE with the offending line named rather
 * than as a silent never-carries in play.
 */
class RunCarriedBoostsTest {

    private val garchomp = "ed0ab605-ebdf-435a-ad0f-afdc117c5a83"
    private val dondozo = "11111111-2222-3333-4444-555555555555"

    private fun switchLine(uuid: String, hp: String = "39/39") =
        "|switch|p1a: $uuid|Garchomp, $uuid, L9, M|$hp"

    @Test
    fun `boosts accumulate for the slot occupant and key to its uuid`() {
        val (owner, stages) = RunCarriedBoosts.replay(
            listOf(
                "update",
                "|t:|1785531337",
                switchLine(garchomp),
                "|-boost|p1a: $garchomp|atk|2",
                "|-boost|p1a: $garchomp|spe|1",
                "|-unboost|p1a: $garchomp|def|1",
                "|-boost|p2a: enemy-uuid|atk|6",
            ),
        )
        assertEquals(UUID.fromString(garchomp), owner)
        assertEquals(mapOf("atk" to 2, "spe" to 1, "def" to -1), stages)
    }

    @Test
    fun `a switch resets the slate and re-keys the owner`() {
        val (owner, stages) = RunCarriedBoosts.replay(
            listOf(
                switchLine(garchomp),
                "|-boost|p1a: $garchomp|atk|6",
                switchLine(dondozo),
                "|-boost|p1a: $dondozo|spd|1",
            ),
        )
        assertEquals(UUID.fromString(dondozo), owner)
        assertEquals(mapOf("spd" to 1), stages)
    }

    @Test
    fun `a faint carries nothing`() {
        val (owner, stages) = RunCarriedBoosts.replay(
            listOf(switchLine(garchomp), "|-boost|p1a: $garchomp|atk|2", "|faint|p1a: $garchomp"),
        )
        assertNull(owner)
        assertTrue(stages.isEmpty())
    }

    @Test
    fun `clearboost and stage clamping behave`() {
        val (_, cleared) = RunCarriedBoosts.replay(
            listOf(switchLine(garchomp), "|-boost|p1a: $garchomp|atk|2", "|-clearboost|p1a: $garchomp"),
        )
        assertTrue(cleared.isEmpty())

        val (_, clamped) = RunCarriedBoosts.replay(
            listOf(
                switchLine(garchomp),
                "|-boost|p1a: $garchomp|atk|6",
                "|-boost|p1a: $garchomp|atk|6",
            ),
        )
        assertEquals(mapOf("atk" to 6), clamped)
    }
}
