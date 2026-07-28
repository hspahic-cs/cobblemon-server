package com.cobblemonroguelite.run

import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull

/**
 * Covers the parts of the checkpoint format that do not need a live game.
 *
 * A run with Pokémon in it cannot be round-tripped here: `Pokemon.saveToNBT`/`loadFromNBT` resolve
 * species, moves, and items out of a populated [RegistryAccess], which only exists on a booted
 * server. So these tests exercise the decisions made *before* any Pokémon is touched — schema
 * version, wave validation, the empty-party discard — which is also where every "silently resumed a
 * wrong run" bug would live. [RegistryAccess.EMPTY] is enough for those paths precisely because
 * they never reach a registry.
 */
class RunStateTest {

    private fun checkpointTag(
        version: Int = RunState.SCHEMA_VERSION,
        wave: Int = 3,
        partySize: Int = 1,
    ): CompoundTag = CompoundTag().apply {
        putInt("schemaVersion", version)
        putInt("wave", wave)
        putInt("credits", 250)
        putLong("seed", 99L)
        putInt("bossesCleared", 1)
        // Entries are empty compounds: any test that gets far enough to read one is a test that
        // was supposed to have bailed out earlier, and it will fail loudly rather than pass.
        put("party", ListTag().apply { repeat(partySize) { add(CompoundTag()) } })
    }

    @Test
    fun `toNbt stamps the current schema version`() {
        val tag = RunState(seed = 7L).toNbt(RegistryAccess.EMPTY)
        assertEquals(RunState.SCHEMA_VERSION, tag.getInt("schemaVersion"))
    }

    @Test
    fun `toNbt writes the scalar run fields`() {
        val tag = RunState(wave = 12, credits = 400, seed = -5L, bossesCleared = 2)
            .toNbt(RegistryAccess.EMPTY)
        assertEquals(12, tag.getInt("wave"))
        assertEquals(400, tag.getInt("credits"))
        assertEquals(-5L, tag.getLong("seed"))
        assertEquals(2, tag.getInt("bossesCleared"))
    }

    @Test
    fun `fromNbt refuses a newer schema than it understands`() {
        val tag = checkpointTag(version = RunState.SCHEMA_VERSION + 1)
        assertNull(RunState.fromNbt(RegistryAccess.EMPTY, tag))
    }

    @Test
    fun `fromNbt refuses an older schema it has no migration for`() {
        val tag = checkpointTag(version = RunState.SCHEMA_VERSION - 1)
        assertNull(RunState.fromNbt(RegistryAccess.EMPTY, tag))
    }

    @Test
    fun `fromNbt refuses a pre-versioning tag`() {
        // Absent key reads as 0. Written as a removal rather than putInt(0) because that is the
        // shape a real pre-versioning checkpoint has on disk.
        val tag = checkpointTag().apply { remove("schemaVersion") }
        assertNull(RunState.fromNbt(RegistryAccess.EMPTY, tag))
    }

    @Test
    fun `fromNbt refuses a non-positive wave`() {
        assertNull(RunState.fromNbt(RegistryAccess.EMPTY, checkpointTag(wave = 0)))
        assertNull(RunState.fromNbt(RegistryAccess.EMPTY, checkpointTag(wave = -1)))
    }

    @Test
    fun `fromNbt discards a run whose party restored empty`() {
        // The distinction that matters: null means "no run", which the player can restart from.
        // A RunState with an empty party would read as an instant wipe and end the run for them.
        assertNull(RunState.fromNbt(RegistryAccess.EMPTY, checkpointTag(partySize = 0)))
    }

    @Test
    fun `toNbt writes the arena fields`() {
        val entry = RunEntryPoint(ResourceLocation.withDefaultNamespace("overworld"), 10.0, 64.0, -20.0, 45f, 5f)
        val template = ResourceLocation.fromNamespaceAndPath("cobblemon_roguelite", "arena_late")
        val tag = RunState(seed = 1L, arenaSlot = 7, entry = entry, stampedTemplate = template)
            .toNbt(RegistryAccess.EMPTY)
        assertEquals(7, tag.getInt("arenaSlot"))
        assertEquals(entry, RunEntryPoint.fromNbt(tag.getCompound("entry")))
        assertEquals(template.toString(), tag.getString("stampedTemplate"))
    }

    @Test
    fun `an unassigned arena slot is absent from the tag, not written as zero`() {
        // Zero is a valid slot, and [RunState.fromNbt] distinguishes the two by presence. A run
        // written as "slot 0" when it never had one would be restored pointing at somebody else's
        // arena, and the allocator — which derives occupancy from exactly this field — would believe
        // it. The read side of that pair cannot be exercised here; see the class docs.
        val tag = RunState(seed = 1L).toNbt(RegistryAccess.EMPTY)
        assertFalse(tag.contains("arenaSlot"))
        assertFalse(tag.contains("entry"))
        assertFalse(tag.contains("stampedTemplate"))
    }

    @Test
    fun `toNbt carries the battle marker into the checkpoint`() {
        val marker = RunBattleMarker(wave = 9, boot = UUID.randomUUID(), onField = listOf(UUID.randomUUID()))
        val tag = RunState(seed = 1L, battle = marker).toNbt(RegistryAccess.EMPTY)
        assertEquals(marker, RunBattleMarker.fromNbt(tag.getCompound("battle")))
    }

    @Test
    fun `a run between waves writes no battle marker`() {
        // Presence *is* the state — see [RunBattleMarker]. An always-written marker would make every
        // login look like a disconnected battle and start killing Pokémon for it.
        assertFalse(RunState(seed = 1L).toNbt(RegistryAccess.EMPTY).contains("battle"))
    }

    @Test
    fun `toNbt pins the roster id and carries the opponent history`() {
        // Both are inputs to what the *next* wave is, not just records of what happened, so a
        // checkpoint that dropped either would resume a run that composes different waves from the
        // one it saved — with nothing to say so.
        val roster = ResourceLocation.fromNamespaceAndPath("test", "roster")
        val memory = RunTrainerMemory().apply { record(5, ResourceLocation.fromNamespaceAndPath("test", "rival")) }
        val tag = RunState(seed = 1L, trainerRoster = roster, trainerMemory = memory).toNbt(RegistryAccess.EMPTY)
        assertEquals(roster.toString(), tag.getString("trainerRoster"))
        assertEquals(memory, RunTrainerMemory.fromNbt(tag.getList("trainerMemory", 10)))
    }

    @Test
    fun `an empty opponent history is left out of the tag`() {
        // Unlike the arena slot and the battle marker, absent and empty mean the same thing here —
        // this is bytes in a file written every wave, not a state distinction.
        assertFalse(RunState(seed = 1L).toNbt(RegistryAccess.EMPTY).contains("trainerMemory"))
    }

    @Test
    fun `a slot of zero is written`() {
        assertEquals(true, RunState(seed = 1L, arenaSlot = 0).toNbt(RegistryAccess.EMPTY).contains("arenaSlot"))
    }

    @Test
    fun `partySnapshot hands back a copy, not the live list`() {
        val run = RunState(seed = 1L)
        assertNotSame(run.party, run.partySnapshot())
    }

    @Test
    fun `a fresh run starts at wave one and is wiped`() {
        val run = RunState(seed = 1L)
        assertEquals(1, run.wave)
        assertEquals(true, run.isWiped())
    }
}
