package com.cobblemonroguelite.run

import net.minecraft.resources.ResourceLocation
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * §2.25's operator override, which is a lever that must not move on its own.
 *
 * Three properties are worth pinning and none of them is provable by using the feature: that the
 * override is off unless somebody turned it on (a default-on override is indistinguishable from
 * having no badge gate), that it only ever *opens* depth, and that it is per player rather than
 * server-wide. The fourth — that a run started under it stays marked — lives in [RunStateTest],
 * because it is a persistence question.
 */
class RunDepthOverrideTest {

    private fun gym(n: Int) = ResourceLocation.fromNamespaceAndPath("server", "beat_gym_%02d".format(n))

    private val tenGyms = RunDepthGate(
        tiers = (1..10).map { DepthTier(gym(it), it * 10) },
        baseMaxWave = 0,
    )

    @AfterTest
    fun clear() = RunDepthOverrides.clear()

    @Test
    fun `nobody is overridden by default`() {
        assertTrue(RunDepthOverrides.active().isEmpty())
        assertFalse(RunDepthOverrides.isActive(UUID.randomUUID()))
    }

    @Test
    fun `the override is per player and not a server switch`() {
        val overridden = UUID.randomUUID()
        val everybodyElse = UUID.randomUUID()
        RunDepthOverrides.set(overridden, "tester", on = true, by = "console")
        assertTrue(RunDepthOverrides.isActive(overridden))
        assertFalse(RunDepthOverrides.isActive(everybodyElse), "the override leaked to another player")
    }

    @Test
    fun `setting an override twice reports no change the second time`() {
        // The command counts changes so an operator can tell "granted" from "already had it", and a
        // set that always reported true would make the second call look like it did something.
        val player = UUID.randomUUID()
        assertTrue(RunDepthOverrides.set(player, "tester", on = true, by = "console"))
        assertFalse(RunDepthOverrides.set(player, "tester", on = true, by = "console"))
        assertTrue(RunDepthOverrides.set(player, "tester", on = false, by = "console"))
        assertFalse(RunDepthOverrides.set(player, "tester", on = false, by = "console"))
    }

    @Test
    fun `an override uncaps a player who has earned nothing`() {
        // The case the whole feature exists for: a dev server where nobody has a badge, and the deep
        // half of a 200-wave run is otherwise unreachable.
        assertIs<DepthGateResult.Denied>(tenGyms.evaluate(AdvancementCheck.NONE))
        assertEquals(DepthGateResult.Allowed(null), tenGyms.evaluate(AdvancementCheck.NONE, overridden = true))
    }

    @Test
    fun `an override never lowers a cap`() {
        val earnedFour = AdvancementCheck { it == gym(4) }
        assertEquals(DepthGateResult.Allowed(40), tenGyms.evaluate(earnedFour))
        assertEquals(DepthGateResult.Allowed(null), tenGyms.evaluate(earnedFour, overridden = true))
    }

    @Test
    fun `the cap reads a denial as depth zero and no cap as null`() {
        // The flattening [RunController] does per wave. Reading a denial as "no cap" is the one
        // mistake that turns the gate into its opposite, silently.
        assertEquals(0, tenGyms.evaluate(AdvancementCheck.NONE).cap)
        assertEquals(40, tenGyms.evaluate({ it == gym(4) }).cap)
        assertEquals(null, RunDepthGate.UNGATED.evaluate(AdvancementCheck.NONE).cap)
    }

    @Test
    fun `allows is what the per-wave audit asks`() {
        val earnedFour = tenGyms.evaluate({ it == gym(4) })
        assertTrue(earnedFour.allows(40))
        assertFalse(earnedFour.allows(41), "wave 41 on a cap of 40 is exactly the inflated wave")
        assertTrue(RunDepthGate.UNGATED.evaluate(AdvancementCheck.NONE).allows(200))
    }
}
