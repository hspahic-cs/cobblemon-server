package com.cobblemonroguelite.integration

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The trainer seam's registration mechanics, and one property that is worth a test on its own.
 *
 * **The shipped default has to be the refusing one.** [RunTrainerBattles] is the second seam in this
 * module that refuses rather than no-ops, and the reason is the same as
 * [com.cobblemonroguelite.run.RunWaves]': the only available no-op is "count the wave as won", which
 * walks a run to wave 200 and pays out for it. A refactor that swapped the default for something
 * permissive would be invisible in play — trainer waves would simply start passing — so the identity
 * of the default is asserted rather than assumed.
 *
 * What is deliberately *not* here: any call to [RunTrainerBattles.begin]. It takes a `MinecraftServer`
 * and a `ServerPlayer`, neither of which can be constructed outside a booted server, so the refusal
 * itself and the fail-closed behaviour around a throwing provider are dev-VM checks and are recorded
 * as such rather than faked with a mock that would only test the mock.
 */
class RunTrainerBattlesTest {

    @AfterTest
    fun restore() = RunTrainerBattles.reset()

    @Test
    fun `nothing registered means the refusing default is in force`() {
        assertFalse(RunTrainerBattles.isImplemented())
        assertSame(RunTrainerBattles.UNIMPLEMENTED, RunTrainerBattles.current)
    }

    @Test
    fun `registering replaces the default`() {
        val provider = RunTrainerBattleProvider { _, _, _ -> true }
        RunTrainerBattles.register(provider)
        assertTrue(RunTrainerBattles.isImplemented())
        assertSame(provider, RunTrainerBattles.current)
    }

    @Test
    fun `the last registration wins, so an operator swap cannot brick the mode`() {
        val first = RunTrainerBattleProvider { _, _, _ -> true }
        val second = RunTrainerBattleProvider { _, _, _ -> false }
        RunTrainerBattles.register(first)
        RunTrainerBattles.register(second)
        assertSame(second, RunTrainerBattles.current)
    }

    @Test
    fun `reset restores the refusal`() {
        RunTrainerBattles.register(RunTrainerBattleProvider { _, _, _ -> true })
        RunTrainerBattles.reset()
        assertFalse(RunTrainerBattles.isImplemented())
        assertSame(RunTrainerBattles.UNIMPLEMENTED, RunTrainerBattles.current)
    }
}
