package com.cobblemonroguelite.run

import com.cobblemonroguelite.arena.ArenaBuild
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
import kotlin.test.assertTrue

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
    fun `the entry point is written but the arena lease is not`() {
        // §2.23. A slot is leased for a session, so writing it down would restore a lease held by
        // somebody who is not connected — and, worse, one that may since have been handed to another
        // run, which teleports two players into one arena. The entry point is the opposite kind of
        // fact: where the player was standing in the real world, and the only way home.
        //
        // The read half is not exercised here (see the class docs — a restore needs a populated
        // registry) and does not need to be: the three keys are not merely ignored on load, they are
        // never named there at all.
        val entry = RunEntryPoint(ResourceLocation.withDefaultNamespace("overworld"), 10.0, 64.0, -20.0, 45f, 5f)
        val build = ArenaBuild.Palette(ResourceLocation.fromNamespaceAndPath("cobblemon_roguelite", "arena_late"))
        val painted = ResourceLocation.fromNamespaceAndPath("minecraft", "basalt_deltas")
        val tag = RunState(
            seed = 1L,
            arenaSlot = 7,
            entry = entry,
            stampedBuild = build,
            paintedBiome = painted,
        ).toNbt(RegistryAccess.EMPTY)
        assertEquals(entry, RunEntryPoint.fromNbt(tag.getCompound("entry")))
        assertFalse(tag.contains("arenaSlot"))
        assertFalse(tag.contains("stampedBuild"))
        assertFalse(tag.contains("paintedBiome"))
    }

    @Test
    fun `a run that was never given an entry point writes none`() {
        assertFalse(RunState(seed = 1L).toNbt(RegistryAccess.EMPTY).contains("entry"))
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
    fun `the activity stamp is written unconditionally`() {
        // §2.23's only input to expiry besides the wave, and there is no "never played" state to encode
        // as absence — a run is stamped at creation. The read side is [RunExpiry.restoreStamp], which is
        // a separate function precisely so that its epoch-zero defence can be run outside a server.
        val at = 1_700_000_000_000L
        val tag = RunState(seed = 1L, lastActiveAtEpochMs = at).toNbt(RegistryAccess.EMPTY)
        assertEquals(at, tag.getLong("lastActiveAt"))
        assertTrue(RunState(seed = 1L).toNbt(RegistryAccess.EMPTY).contains("lastActiveAt"))
    }

    @Test
    fun `touch moves the activity stamp forward`() {
        val run = RunState(seed = 1L, lastActiveAtEpochMs = 0L)
        run.touch()
        assertTrue(run.lastActiveAtEpochMs > 0L)
    }

    @Test
    fun `a run with no decision outstanding writes no pendingCatch`() {
        // Presence is the state, the same as the battle marker: [RunController.resume] refuses a
        // wave whenever this tag restores as non-null, so an always-written key would stop every
        // resumed run dead on a decision nobody is holding.
        assertFalse(RunState(seed = 1L).toNbt(RegistryAccess.EMPTY).contains("pendingCatch"))
    }

    @Test
    fun `the checkpoint carries where the run is, not what is painted in the arena`() {
        // Two fields for what looks like one fact, and they are not: [RunState.biome] is where the run
        // *is* and survives a slot reassignment, while paintedBiome is what is in the world and does
        // not — so only the first is worth persisting. Merging them would either re-stamp arenas that
        // are fine or stop retrying repaints that never happened.
        val visit = BiomeVisit(band = 4, biome = ResourceLocation.fromNamespaceAndPath("test", "volcano"))
        val painted = ResourceLocation.fromNamespaceAndPath("minecraft", "basalt_deltas")
        val tag = RunState(seed = 1L, biome = visit, paintedBiome = painted).toNbt(RegistryAccess.EMPTY)
        assertEquals(visit, BiomeVisit.fromNbt(tag.getCompound("biome")))
        assertFalse(tag.contains("paintedBiome"))
    }

    @Test
    fun `a run that has never entered a biome writes no biome`() {
        assertFalse(RunState(seed = 1L).toNbt(RegistryAccess.EMPTY).contains("biome"))
    }

    @Test
    fun `a biome visit in band zero survives the round trip`() {
        // Band 0 is waves 1-10, i.e. every run's first band, and it is the value a presence check
        // would get wrong: `getInt` answers 0 for an absent key just as readily as for a written one.
        val visit = BiomeVisit(band = 0, biome = ResourceLocation.fromNamespaceAndPath("test", "meadow"))
        assertEquals(visit, BiomeVisit.fromNbt(visit.toNbt()))
        assertNull(BiomeVisit.fromNbt(CompoundTag()), "an empty tag is no visit, not band 0")
    }

    @Test
    fun `the depth override flag is written on every run, not only on overridden ones`() {
        // §2.25's audit flag. Written both ways so a run file answers "was this earned" outright,
        // rather than by the absence of a key that a build without the feature would also lack.
        val honest = RunState(seed = 1L).toNbt(RegistryAccess.EMPTY)
        assertEquals(true, honest.contains("startedUnderOverride"))
        assertEquals(false, honest.getBoolean("startedUnderOverride"))

        val inflated = RunState(seed = 1L, startedUnderOverride = true).toNbt(RegistryAccess.EMPTY)
        assertEquals(true, inflated.getBoolean("startedUnderOverride"))
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
