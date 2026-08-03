package com.cobblemonroguelite.run

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * §2.18's badge gate, with the advancement lookup replaced by a set.
 *
 * The gate is the one part of run start that can lock a player out entirely, so what is covered here
 * is the two ways of being wrong about that: denying somebody who earned a tier, and allowing
 * unlimited depth to somebody who earned none.
 */
class RunDepthGateTest {

    private fun gym(n: Int) = ResourceLocation.fromNamespaceAndPath("server", "beat_gym_%02d".format(n))

    private fun earned(vararg ids: ResourceLocation) = AdvancementCheck { it in ids.toSet() }

    private val tenGyms = RunDepthGate(
        tiers = (1..10).map { DepthTier(gym(it), it * 10) },
        baseMaxWave = 0,
    )

    @Test
    fun `the shipped default gates nothing`() {
        assertEquals(DepthGateResult.Allowed(null), RunDepthGate.UNGATED.evaluate(AdvancementCheck.NONE))
    }

    @Test
    fun `a configured gate with a zero base denies a player who has earned nothing`() {
        val result = tenGyms.evaluate(AdvancementCheck.NONE)
        assertIs<DepthGateResult.Denied>(result)
        // Every tier, not just the first: any one of them opens a run.
        assertEquals(10, result.requires.size)
    }

    @Test
    fun `depth is the deepest tier earned, not the count of them`() {
        assertEquals(DepthGateResult.Allowed(40), tenGyms.evaluate(earned(gym(4))))
        // Out of order on purpose: gyms can be cleared in more than one sequence, and a player who
        // beat gym 4 first must not sit at the depth of one who has beaten nothing.
        assertEquals(DepthGateResult.Allowed(70), tenGyms.evaluate(earned(gym(7), gym(2))))
    }

    @Test
    fun `a non-zero base is allowed without any tier`() {
        val gate = RunDepthGate(tiers = listOf(DepthTier(gym(1), 50)), baseMaxWave = 10)
        assertEquals(DepthGateResult.Allowed(10), gate.evaluate(AdvancementCheck.NONE))
        assertEquals(DepthGateResult.Allowed(50), gate.evaluate(earned(gym(1))))
    }

    @Test
    fun `tiers configured with a null base still allow the full run to an unbadged player`() {
        // Not a nicety: this is the shape an operator produces by adding tiers and forgetting the
        // base, and the answer has to be a documented one rather than a fall-through.
        val gate = RunDepthGate(tiers = listOf(DepthTier(gym(1), 50)))
        assertEquals(DepthGateResult.Allowed(null), gate.evaluate(AdvancementCheck.NONE))
    }
}
