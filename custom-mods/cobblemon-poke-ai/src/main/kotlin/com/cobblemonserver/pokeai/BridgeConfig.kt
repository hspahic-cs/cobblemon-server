package com.cobblemonserver.pokeai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import net.neoforged.fml.loading.FMLPaths
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private val log = LoggerFactory.getLogger("cobblemon_poke_ai/config")

private const val CONFIG_FILE_NAME = "cobblemon-poke-ai.json"

data class BridgeConfigModel(
    val url: String = "http://127.0.0.1:8642",
    val timeoutMs: Long = 8000L,
    val searchTimeMs: Int = 3000,
    val pokemonFormat: String = "gen9customgame",
    val generation: String = "gen9",
    val smogonStatsFormat: String = "gen9nationaldex",
)

object BridgeConfig {
    @Volatile var url: String = BridgeConfigModel().url ; private set
    @Volatile var timeoutMs: Long = BridgeConfigModel().timeoutMs ; private set
    @Volatile var searchTimeMs: Int = BridgeConfigModel().searchTimeMs ; private set
    @Volatile var pokemonFormat: String = BridgeConfigModel().pokemonFormat ; private set
    @Volatile var generation: String = BridgeConfigModel().generation ; private set
    @Volatile var smogonStatsFormat: String = BridgeConfigModel().smogonStatsFormat ; private set

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        val path = configPath()
        val model = if (Files.exists(path)) {
            try {
                Files.newBufferedReader(path).use { gson.fromJson(it, BridgeConfigModel::class.java) }
                    ?: BridgeConfigModel()
            } catch (e: JsonSyntaxException) {
                log.error("Invalid {} — using defaults", path, e)
                BridgeConfigModel()
            }
        } else {
            log.info("No {} found — writing defaults", path)
            val defaults = BridgeConfigModel()
            writeDefaults(path, defaults)
            defaults
        }
        url = model.url
        timeoutMs = model.timeoutMs
        searchTimeMs = model.searchTimeMs
        pokemonFormat = model.pokemonFormat
        generation = model.generation
        smogonStatsFormat = model.smogonStatsFormat
    }

    private fun configPath(): Path = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME)

    private fun writeDefaults(path: Path, defaults: BridgeConfigModel) {
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use { gson.toJson(defaults, it) }
    }
}
