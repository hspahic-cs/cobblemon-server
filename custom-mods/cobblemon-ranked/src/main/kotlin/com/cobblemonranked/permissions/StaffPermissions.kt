package com.cobblemonranked.permissions

import net.minecraft.commands.CommandSourceStack
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to NeoEssentials' permission system, so staff commands can be opened to the
 * `moderator` group without handing out vanilla op. Copied deliberately into each mod that needs
 * it (same convention as [com.cobblemonranked.economy.EconomyBridge]) to keep the mods
 * dependency-free of each other.
 *
 * ## Why this exists
 *
 * Every custom-mod admin command gates on vanilla `CommandSourceStack.hasPermission(n)`, which
 * only consults `ops.json`. NeoEssentials' *own* commands (`/kick`, `/ban`, `/mute`, …) gate on
 * its permission nodes instead and need no op at all — so a moderator can be a non-op player.
 * Without this bridge, that same moderator would be locked out of every command we ship.
 *
 * Granting them op instead is not an option: NeoEssentials' `opsBypassPermissions` defaults to
 * **true** and its op test is `hasPermissions(2)`, so any op-level-2 player silently receives
 * *every* NeoEssentials permission — including `/permissions`, which is how you make more admins.
 * Op level 1 doesn't bypass, but is too weak to be useful for our commands either.
 *
 * ## Semantics
 *
 * [check] is `vanilla op level >= opLevel` **OR** `player holds the NeoEssentials node`. The op
 * arm is kept first so behaviour for existing admins and for the server console is bit-identical
 * to before this bridge existed, and so everything still works if NeoEssentials is ever removed.
 *
 * If NeoEssentials is absent the node arm degrades to `false` (warn-once) rather than throwing:
 * these are `.requires()` predicates, and brigadier evaluates them while building the command
 * tree it sends to every joining client. A throw there would break command sync for that player.
 *
 * ## Gotcha: group changes need a relog
 *
 * Brigadier caches the per-player command tree at login. After
 * `/permissions user <name> setgroup moderator`, the newly-permitted commands won't appear in
 * tab-completion (and will be rejected as unknown) until that player reconnects. NeoEssentials'
 * own commands behave the same way. This is a client-side command-tree limitation, not a
 * permission-store staleness issue.
 */
object StaffPermissions {

    private val log = LoggerFactory.getLogger("cobblemon-ranked/staff-perms")

    private const val PERMISSION_API_CLASS = "com.zerog.neoessentials.api.permissions.PermissionAPI"
    private const val M_HAS_PERMISSION = "hasPermission"

    @Volatile private var hasPermissionMethod: Method? = null
    private val resolved = AtomicBoolean(false)
    private val warnedOnce = AtomicBoolean(false)

    private fun method(): Method? {
        if (resolved.get()) return hasPermissionMethod
        synchronized(this) {
            if (resolved.get()) return hasPermissionMethod
            hasPermissionMethod = try {
                Class.forName(PERMISSION_API_CLASS)
                    .getMethod(M_HAS_PERMISSION, UUID::class.java, String::class.java)
            } catch (e: ClassNotFoundException) {
                warnOnce("NeoEssentials PermissionAPI not loaded — staff commands are op-only")
                null
            } catch (e: Throwable) {
                warnOnce("NeoEssentials PermissionAPI reflection failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
            resolved.set(true)
        }
        return hasPermissionMethod
    }

    /**
     * True if [source] may run a command gated on [node], either by holding vanilla op level
     * [opLevel] or by holding the NeoEssentials permission [node]. Never throws.
     */
    fun check(source: CommandSourceStack, node: String, opLevel: Int): Boolean {
        if (source.hasPermission(opLevel)) return true
        val player = source.player ?: return false
        val m = method() ?: return false
        return try {
            m.invoke(null, player.uuid, node) as? Boolean ?: false
        } catch (e: Throwable) {
            log.error("StaffPermissions.check('$node') failed", e)
            false
        }
    }

    private fun warnOnce(message: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(message)
    }
}
