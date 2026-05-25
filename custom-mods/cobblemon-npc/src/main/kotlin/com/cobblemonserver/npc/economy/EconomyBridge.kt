package com.cobblemonserver.npc.economy

import com.cobblemonserver.npc.CobblemonNpc
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.neoforged.fml.ModList
import java.util.UUID

/**
 * Bridge to CobbleDollars (NeoForge 1.21.1).
 *
 * CobbleDollars ships no documented Java API, so we dispatch the `cobbledollars` command
 * server-side. Presence is detected via NeoForge's [ModList]; payouts are no-ops when the
 * mod is absent so battles still run without error.
 *
 * The server instance is captured on first use via [attach] — typically from a server-start
 * hook. If [addBalance] is called before [attach], it returns false and logs a warning.
 */
object EconomyBridge {

    private const val MOD_ID = "cobbledollars"

    @Volatile
    private var server: MinecraftServer? = null

    fun attach(server: MinecraftServer) {
        this.server = server
    }

    fun isAvailable(): Boolean = ModList.get()?.isLoaded(MOD_ID) == true

    fun addBalance(playerUuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return false
        if (!isAvailable()) return false
        val s = server ?: run {
            CobblemonNpc.logger.warn("cobblemon-npc: EconomyBridge not attached — dropping payout of $amount to $playerUuid")
            return false
        }
        return try {
            val source: CommandSourceStack = s.createCommandSourceStack().withPermission(4)
            val command = "cobbledollars give $playerUuid $amount"
            s.commands.performPrefixedCommand(source, command)
            true
        } catch (e: Exception) {
            CobblemonNpc.logger.warn("cobblemon-npc: addBalance failed for $playerUuid: ${e.message}")
            false
        }
    }
}
