package com.cobblemonbridge.commands

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

private val log = LoggerFactory.getLogger("cobblemon_bridge/test_teams")

/**
 * Dev-only preset teams for [TestTeamCommand], loaded from
 * `config/cobblemon-bridge/runtime/test_teams.json`. Tiers: `uber`, `ou`, `normal`.
 *
 * Fields are nullable on purpose: Gson constructs Kotlin data classes via reflection and does NOT
 * apply constructor defaults for keys missing from the JSON (a missing list becomes `null`, not
 * `emptyList()`), so the command coalesces with `?:` everywhere.
 */
object TestTeams {

    data class MonSpec(
        val species: String? = null,
        val form: String? = null,
        val aspects: Set<String>? = null,
        val nature: String? = null,
        val ability: String? = null,
        val item: String? = null,
        val shiny: Boolean = false,
        val evs: Map<String, Int>? = null,
        val moves: List<String>? = null,
    )

    data class Team(
        val name: String? = null,
        /** Team-level value used to fill all six IVs (e.g. 31 for competitive, 22 for "normal"). */
        val ivs: Int = 31,
        val mons: List<MonSpec>? = null,
    )

    private data class TeamsFile(
        val uber: List<Team>? = null,
        val ou: List<Team>? = null,
        val normal: List<Team>? = null,
    )

    private val gson: Gson = GsonBuilder().create()
    @Volatile private var data: TeamsFile = TeamsFile()

    fun init() = load(
        FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve("test_teams.json")
    )

    fun load(file: Path) {
        data = try {
            if (file.exists()) gson.fromJson(file.readText(), TeamsFile::class.java) ?: TeamsFile()
            else { log.warn("test_teams.json missing at {} — /testteam will have no teams", file); TeamsFile() }
        } catch (e: Exception) {
            log.warn("test_teams.json load failed — /testteam will have no teams", e); TeamsFile()
        }
        log.info("test teams loaded: uber={}, ou={}, normal={}", teams("uber").size, teams("ou").size, teams("normal").size)
    }

    val TIERS: List<String> = listOf("uber", "ou", "normal")

    fun teams(tier: String): List<Team> = when (tier.lowercase()) {
        "uber" -> data.uber
        "ou" -> data.ou
        "normal" -> data.normal
        else -> null
    } ?: emptyList()
}
