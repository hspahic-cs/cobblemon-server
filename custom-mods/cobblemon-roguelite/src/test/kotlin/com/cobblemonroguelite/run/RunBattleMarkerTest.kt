package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The marker's half of the checkpoint format.
 *
 * Unlike the run around it this needs no [net.minecraft.core.RegistryAccess] at all — it is UUIDs and
 * an int — so the full round trip is testable here, which matters because the identities in it are
 * what [RunState.kill] matches on. A UUID that does not survive the write is a penalty that silently
 * kills nothing.
 */
class RunBattleMarkerTest {

    private val boot = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()

    @Test
    fun `round-trips through NBT`() {
        val marker = RunBattleMarker(wave = 42, boot = boot, onField = listOf(first, second))
        assertEquals(marker, RunBattleMarker.fromNbt(marker.toNbt()))
    }

    @Test
    fun `round-trips an empty field`() {
        val marker = RunBattleMarker(wave = 1, boot = boot, onField = emptyList())
        assertEquals(marker, RunBattleMarker.fromNbt(marker.toNbt()))
    }

    @Test
    fun `an absent marker tag reads as no battle`() {
        assertNull(RunBattleMarker.fromNbt(CompoundTag()))
    }

    @Test
    fun `an unreadable boot identity reads as no battle`() {
        // The safe direction: no penalty. Killing on the strength of a tag we could not parse would
        // spend a player's Pokémon on a guess.
        val tag = CompoundTag().apply {
            putInt("wave", 5)
            putString("boot", "not-a-uuid")
            put("onField", ListTag().apply { add(StringTag.valueOf(first.toString())) })
        }
        assertNull(RunBattleMarker.fromNbt(tag))
    }

    @Test
    fun `a nonsense wave reads as no battle`() {
        val tag = RunBattleMarker(1, boot, listOf(first)).toNbt().apply { putInt("wave", 0) }
        assertNull(RunBattleMarker.fromNbt(tag))
    }

    @Test
    fun `an unreadable field entry is dropped, not the whole marker`() {
        // One bad id costs one Pokémon out of the penalty; discarding the marker would cost the
        // penalty entirely, which is the exploit rather than the safe side.
        val tag = RunBattleMarker(3, boot, listOf(first)).toNbt().apply {
            put(
                "onField",
                ListTag().apply {
                    add(StringTag.valueOf("garbage"))
                    add(StringTag.valueOf(second.toString()))
                },
            )
        }
        assertEquals(listOf(second), RunBattleMarker.fromNbt(tag)?.onField)
    }
}
