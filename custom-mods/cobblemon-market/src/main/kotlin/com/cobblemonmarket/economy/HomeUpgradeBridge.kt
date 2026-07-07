package com.cobblemonmarket.economy

import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to NeoEssentials' home system, used by the Upgrades vendor to sell extra
 * `/sethome` slots.
 *
 * NeoEssentials computes a player's home limit as
 *   `max(config default maxHomes, highest N for which the player has permission "neoessentials.home.<N>")`
 * — see `HomeManager.getMaxHomesForPlayer`. There is no LuckPerms on this server, so those
 * permission nodes live in NeoEssentials' own internal permission store. We grant a slot by
 * running its `/permissions user <name> add neoessentials.home.<N>` command from a server-level
 * (op) source; that command clears the permission cache and persists to disk, so the new limit
 * takes effect immediately.
 *
 * Everything degrades to "unavailable" (null / false) if NeoEssentials isn't loaded or its
 * internals move, so the rest of the mod keeps running.
 */
object HomeUpgradeBridge {

    private val log = LoggerFactory.getLogger("cobblemon-market/home-upgrade")
    private const val HOME_MANAGER_CLASS = "com.zerog.neoessentials.teleportation.HomeManager"
    /** Permission node template — NeoEssentials checks `neoessentials.home.<N>`. */
    const val HOME_PERMISSION_PREFIX = "neoessentials.home."

    @Volatile private var manager: Any? = null
    @Volatile private var getMaxForPlayer: Method? = null
    @Volatile private var getBaseMax: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun resolve(): Boolean {
        if (manager != null) return true
        return try {
            val cls = Class.forName(HOME_MANAGER_CLASS)
            val mgr = cls.getMethod("getInstance").invoke(null)
            getMaxForPlayer = cls.getMethod("getMaxHomesForPlayer", ServerPlayer::class.java)
            getBaseMax = cls.getMethod("getMaxHomesPerPlayer")
            manager = mgr
            true
        } catch (e: Throwable) {
            warnOnce("NeoEssentials HomeManager reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    fun isAvailable(): Boolean = resolve()

    /** The player's current effective home limit, or null if NeoEssentials is unavailable. */
    fun currentMaxHomes(player: ServerPlayer): Int? {
        if (!resolve()) return null
        return try {
            getMaxForPlayer!!.invoke(manager, player) as Int
        } catch (e: Throwable) {
            log.error("currentMaxHomes failed", e); null
        }
    }

    /** The server-wide baseline home limit (config `maxHomes`), or null if unavailable. */
    fun baseMaxHomes(): Int? {
        if (!resolve()) return null
        return try {
            getBaseMax!!.invoke(manager) as Int
        } catch (e: Throwable) {
            log.error("baseMaxHomes failed", e); null
        }
    }

    /**
     * Grants the player permission `neoessentials.home.<slot>` via NeoEssentials' internal
     * permissions command, run from a server-level source. Returns true if the grant took
     * effect — verified by re-reading the effective limit rather than trusting the command's
     * (suppressed) return code.
     */
    fun grantHomeSlot(player: ServerPlayer, slot: Int): Boolean {
        val before = currentMaxHomes(player) ?: return false
        val node = "$HOME_PERMISSION_PREFIX$slot"
        val cmd = "permissions user ${player.gameProfile.name} add $node"
        try {
            val src = player.server.createCommandSourceStack().withSuppressedOutput()
            player.server.commands.performPrefixedCommand(src, cmd)
        } catch (e: Throwable) {
            log.error("grantHomeSlot command dispatch failed for {}", player.gameProfile.name, e)
            return false
        }
        val after = currentMaxHomes(player) ?: return false
        if (after <= before) {
            log.warn(
                "grantHomeSlot: '{}' did not raise home limit for {} (still {})",
                cmd, player.gameProfile.name, after,
            )
            return false
        }
        return true
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
