package com.cobblemonbridge.profile

import com.cobblemonbridge.economy.EconomyBridge
import com.cobblemonbridge.quests.LevelCap
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_bridge/profile/data")

/**
 * Snapshot of everything the /profile chest GUI needs about a player. Built fresh on each
 * /profile call — small enough that a cache isn't worth the staleness risk.
 */
data class ProfileSnapshot(
    val displayName: String,
    /** Persisted UUID of the player being looked up — needed by the GUI to build a
     *  `player_head` ItemStack with the player's real skin via `DataComponents.PROFILE`. */
    val playerUuid: UUID,
    val gymBadgeCount: Int,
    val gymBadgeTotal: Int,
    val levelCap: Int,
    val elo: Int?,         // null when ranked module isn't loaded or player has no record
    val wins: Int?,
    val losses: Int?,
    val income: Int,
    val colony: String?,   // null when no minecolony is registered to this player
    val favorite: FavoriteEntry?,
    val lastTeam: List<String>,  // species names of their last ranked PvP team, or empty
)

object ProfileBuilder {

    /**
     * Builds a snapshot for [targetUuid]. [online] is the [ServerPlayer] when the target is
     * currently online (used for level-cap lookup which reads advancements off the player
     * object); pass null if the lookup is for an offline player.
     */
    fun build(server: MinecraftServer, targetUuid: UUID, displayName: String, online: ServerPlayer?): ProfileSnapshot {
        val (have, total) = countGymBadges(server, online)
        val cap = if (online != null) LevelCap.forPlayer(online) else 0
        val (elo, wins, losses) = readRankedStats(targetUuid)
        val income = EconomyBridge.getBalance(targetUuid)
        val colony = readColonyName(server, targetUuid)
        val favorite = FavoriteTracker.get().favorite(targetUuid)
        val team = readLastTeam(targetUuid)
        return ProfileSnapshot(
            displayName = displayName,
            playerUuid = targetUuid,
            gymBadgeCount = have,
            gymBadgeTotal = total,
            levelCap = cap,
            elo = elo, wins = wins, losses = losses,
            income = income,
            colony = colony,
            favorite = favorite,
            lastTeam = team,
        )
    }

    /** Counts how many `server:beat_gym_<N>` advancements the player has completed. */
    private fun countGymBadges(server: MinecraftServer, player: ServerPlayer?): Pair<Int, Int> {
        if (player == null) return 0 to 24
        var have = 0
        var total = 0
        for (n in 1..24) {
            val rl = ResourceLocation.fromNamespaceAndPath("server", "beat_gym_$n")
            val holder: AdvancementHolder = server.advancements.get(rl) ?: continue
            total++
            if (player.advancements.getOrStartProgress(holder).isDone) have++
        }
        if (total == 0) total = 24
        return have to total
    }

    // ─── cobblemon-ranked reflection ────────────────────────────────────────

    private val rankedWarned = AtomicBoolean(false)
    @Volatile private var eloStoreMethod: Method? = null
    @Volatile private var teamStoreMethod: Method? = null

    private fun readRankedStats(uuid: UUID): Triple<Int?, Int?, Int?> {
        return try {
            val cls = Class.forName("com.cobblemonranked.CobblemonRanked")
            val companion = cls.getField("Companion").get(null)
            if (eloStoreMethod == null)
                eloStoreMethod = companion.javaClass.getMethod("getEloStore")
            val eloStore = eloStoreMethod!!.invoke(companion)
            // EloStore.getAll(): Map<String, PlayerEloData>
            val allMap = eloStore.javaClass.getMethod("getAll").invoke(eloStore) as Map<*, *>
            val rec = allMap[uuid.toString()] ?: return Triple(null, null, null)
            val recCls = rec.javaClass
            val elo = recCls.getMethod("getElo").invoke(rec) as Int
            val wins = recCls.getMethod("getWins").invoke(rec) as Int
            val losses = recCls.getMethod("getLosses").invoke(rec) as Int
            Triple(elo, wins, losses)
        } catch (e: ClassNotFoundException) {
            warnRankedOnce("cobblemon-ranked not loaded — profile won't show ELO")
            Triple(null, null, null)
        } catch (e: Throwable) {
            warnRankedOnce("ranked reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            Triple(null, null, null)
        }
    }

    private fun readLastTeam(uuid: UUID): List<String> {
        return try {
            val cls = Class.forName("com.cobblemonranked.CobblemonRanked")
            val companion = cls.getField("Companion").get(null)
            if (teamStoreMethod == null)
                teamStoreMethod = companion.javaClass.getMethod("getTeamStore")
            val teamStore = teamStoreMethod!!.invoke(companion)
            // TeamStore.getTeam(uuid: UUID): List<SavedTeam>? — convention from cobblemon-ranked
            val getTeam = try {
                teamStore.javaClass.getMethod("getTeam", UUID::class.java)
            } catch (_: NoSuchMethodException) {
                null
            }
            val raw = getTeam?.invoke(teamStore, uuid) as? List<*> ?: return emptyList()
            raw.mapNotNull { it?.javaClass?.getMethod("getSpecies")?.invoke(it) as? String }
        } catch (e: Throwable) {
            warnRankedOnce("readLastTeam failed: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    private fun warnRankedOnce(msg: String) {
        if (rankedWarned.compareAndSet(false, true)) log.warn(msg)
    }

    // ─── MineColonies reflection ─────────────────────────────────────────────

    private val coloniesWarned = AtomicBoolean(false)

    private fun readColonyName(server: MinecraftServer, uuid: UUID): String? {
        return try {
            // IColonyManager.getInstance().getColonyByOwner(level, uuid)?.getName()
            val mgrCls = Class.forName("com.minecolonies.api.colony.IColonyManager")
            val getInstance = mgrCls.getMethod("getInstance")
            val mgr = getInstance.invoke(null)
            // The API takes a Level + UUID. Use the overworld since colonies are spawn-bound.
            val level = server.overworld()
            val getByOwner = try {
                mgr.javaClass.methods.first { it.name == "getIColonyByOwner" && it.parameterCount == 2 }
            } catch (_: NoSuchElementException) {
                return null
            }
            val colony = getByOwner.invoke(mgr, level, uuid) ?: return null
            colony.javaClass.getMethod("getName").invoke(colony) as? String
        } catch (e: ClassNotFoundException) {
            warnColoniesOnce("MineColonies not loaded — profile won't show colony name")
            null
        } catch (e: Throwable) {
            warnColoniesOnce("colony reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun warnColoniesOnce(msg: String) {
        if (coloniesWarned.compareAndSet(false, true)) log.warn(msg)
    }
}
