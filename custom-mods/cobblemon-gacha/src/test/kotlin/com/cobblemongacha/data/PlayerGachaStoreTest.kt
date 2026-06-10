package com.cobblemongacha.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class PlayerGachaStoreTest {

    @Test
    fun `round-trip preserves all fields`(@TempDir tmp: Path) {
        val u = UUID.randomUUID()
        val store = PlayerGachaStore(tmp)
        val data = store.getOrCreate(u, "SixthSense")
        data.lastLoginGrantDate = "2026-05-11"
        data.lastRankedGrantDate = "2026-05-10"
        store.save()

        val rehydrated = PlayerGachaStore(tmp)
        rehydrated.load()
        val loaded = rehydrated.get(u)!!
        assertEquals("SixthSense", loaded.name)
        assertEquals("2026-05-11", loaded.lastLoginGrantDate)
        assertEquals("2026-05-10", loaded.lastRankedGrantDate)
    }

    @Test
    fun `missing file is silent on load`(@TempDir tmp: Path) {
        val store = PlayerGachaStore(tmp)
        store.load()
        assertNull(store.get(UUID.randomUUID()))
    }

    @Test
    fun `welcome-keys flag defaults false, persists, and survives reload`(@TempDir tmp: Path) {
        val u = UUID.randomUUID()
        val store = PlayerGachaStore(tmp)
        // Defaults false → an existing/new player is eligible exactly once.
        assertFalse(store.getOrCreate(u, "Newcomer").grantedWelcomeKeys)
        // The grant path sets it true and saves.
        store.getOrCreate(u, "Newcomer").grantedWelcomeKeys = true
        store.save()
        // Reload from disk: still true → the one-time check short-circuits forever.
        val reloaded = PlayerGachaStore(tmp).apply { load() }
        assertTrue(reloaded.get(u)!!.grantedWelcomeKeys)
    }

    @Test
    fun `older records without the welcome flag deserialize to false`(@TempDir tmp: Path) {
        // Simulate a pre-existing players.json that predates the field: write a record with only
        // the old fields, then load — the player must still be eligible (flag false), so every
        // current player gets the one-time grant on next login.
        val u = UUID.randomUUID()
        val legacy = PlayerGachaStore(tmp)
        legacy.getOrCreate(u, "Veteran").lastLoginGrantDate = "2026-05-01"
        legacy.save()
        val loaded = PlayerGachaStore(tmp).apply { load() }.get(u)!!
        assertFalse(loaded.grantedWelcomeKeys)
    }

    @Test
    fun `getOrCreate refreshes cached name`(@TempDir tmp: Path) {
        val u = UUID.randomUUID()
        val store = PlayerGachaStore(tmp)
        store.getOrCreate(u, "OldName")
        val refreshed = store.getOrCreate(u, "NewName")
        assertEquals("NewName", refreshed.name)
    }
}
