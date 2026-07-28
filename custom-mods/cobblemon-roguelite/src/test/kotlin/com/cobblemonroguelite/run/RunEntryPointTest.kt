package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The way home, round-tripped.
 *
 * This is the field whose loss is least visible and most annoying: nothing fails, the run plays
 * normally, and at the end the player is put at world spawn instead of where they started. So the
 * round trip is worth pinning even though it is six scalars — a dropped `putDouble` reads back as
 * 0.0, which is a real coordinate and will not announce itself.
 */
class RunEntryPointTest {

    private val overworld = ResourceLocation.withDefaultNamespace("overworld")

    @Test
    fun `an entry point survives a round trip`() {
        val entry = RunEntryPoint(overworld, x = 1234.5, y = 71.0, z = -987.25, yaw = 90f, pitch = -12.5f)
        assertEquals(entry, RunEntryPoint.fromNbt(entry.toNbt()))
    }

    @Test
    fun `negative coordinates survive`() {
        // Written out because the sign is the half of a coordinate that a lazy serializer loses.
        val entry = RunEntryPoint(overworld, x = -1.0, y = -64.0, z = -30_000_000.0)
        assertEquals(entry, RunEntryPoint.fromNbt(entry.toNbt()))
    }

    @Test
    fun `a tag with no dimension reads as no entry point`() {
        // Null, not a default: the ejection path treats null as "world spawn", which is somewhere the
        // player can stand. A default dimension with the stored coordinates would put them at those
        // coordinates in the wrong world, which may be inside a mountain.
        assertNull(RunEntryPoint.fromNbt(CompoundTag()))
    }

    @Test
    fun `an unparseable dimension reads as no entry point`() {
        val tag = CompoundTag().apply {
            putString("dimension", "NOT A RESOURCE LOCATION")
            putDouble("x", 1.0)
        }
        assertNull(RunEntryPoint.fromNbt(tag))
    }
}
