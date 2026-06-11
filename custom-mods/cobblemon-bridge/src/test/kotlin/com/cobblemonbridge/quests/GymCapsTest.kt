package com.cobblemonbridge.quests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class GymCapsTest {

    @Test
    fun `reads explicit caps and overworld progression from config`(@TempDir dir: Path) {
        val f = dir.resolve("gym_caps.json")
        f.writeText(
            """
            { "overworld": { "base": 20, "perMainlineGym": 5, "uncapAfterGym": 23 },
              "gyms": {
                "1":  { "slug": "ground",   "mainline": true,  "cap": 20 },
                "5":  { "slug": "fire",     "mainline": true,  "cap": 40 },
                "11": { "slug": "bug",      "mainline": false, "cap": null },
                "24": { "slug": "champion", "mainline": false, "cap": 70 }
              } }
            """.trimIndent()
        )
        GymCaps.load(f)

        assertEquals(20, GymCaps.battleCap(1))
        assertEquals(40, GymCaps.battleCap(5))
        assertNull(GymCaps.battleCap(11), "gym with cap:null is uncapped")
        assertEquals(70, GymCaps.battleCap(24))
        assertNull(GymCaps.battleCap(99), "gym absent from config is uncapped")

        assertEquals(listOf(1, 5), GymCaps.mainlineGymIds())
        assertEquals(20, GymCaps.overworldBase())
        assertEquals(5, GymCaps.perMainlineGym())
        assertEquals(23, GymCaps.uncapAfterGym())
    }

    @Test
    fun `falls back to built-in defaults when the file is missing`(@TempDir dir: Path) {
        GymCaps.load(dir.resolve("does-not-exist.json"))
        assertEquals(20, GymCaps.battleCap(1))
        assertEquals(65, GymCaps.battleCap(10))
        assertEquals(70, GymCaps.battleCap(20))
        assertNull(GymCaps.battleCap(13))
    }
}
