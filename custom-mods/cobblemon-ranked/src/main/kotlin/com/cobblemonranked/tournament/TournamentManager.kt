package com.cobblemonranked.tournament

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.CobblemonRanked
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory tournament registration state.
 *
 * Lifecycle: an admin runs `/ranked tournament open` to open registration; players `/join` to pick
 * a [ROSTER_SIZE]-Pokémon roster (max [MAX_SPECIALS_ROSTER] Legendary/Paradox/Ultra-Beast). A player
 * is only *entered* once they lock a roster in, and may re-`/join` to change it until an admin runs
 * `/ranked tournament close`, which prints the ELO seeding to admins. Matches are then run with
 * `/ranked tournament play <p1> <p2>`, where each player picks a subset of 6 (max 1 special) from
 * their locked roster.
 *
 * Rosters are stored as Pokémon UUIDs that reference the player's live party/PC and are resolved on
 * demand ([resolveRoster]). State is intentionally NOT persisted across a server restart — a
 * tournament is a live event; re-open it if the server bounces.
 */
object TournamentManager {

    const val ROSTER_SIZE = 9
    const val MAX_SPECIALS_ROSTER = 4

    /** A locked entrant: the roster is a list of Pokémon UUIDs into the player's party/PC. */
    data class Entry(val uuid: UUID, val name: String, val pokemonUuids: List<UUID>)

    @Volatile private var open = false
    private val entries = ConcurrentHashMap<UUID, Entry>()

    fun isOpen(): Boolean = open

    /** Opens a fresh registration window (clears any prior entrants) and announces it. */
    fun openRegistration(server: MinecraftServer) {
        open = true
        entries.clear()
        val header = Component.literal("§6§l[Tournament] §r§eRegistration is OPEN! ")
            .append(
                Component.literal("§a§n[Click here or type /join]").withStyle {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/join"))
                }
            )
        for (p in server.playerList.players) {
            p.sendSystemMessage(header)
            p.sendSystemMessage(Component.literal(
                "§7[Tournament] Pick §f$ROSTER_SIZE Pokémon§7 for your roster (max §c$MAX_SPECIALS_ROSTER§7 Legendary/Paradox/Ultra-Beast). You can change it until registration closes."))
        }
    }

    /** Closes registration and prints the ELO seeding to every online admin (op level 4). */
    fun closeRegistration(server: MinecraftServer) {
        open = false
        val seeded = seeding()
        for (p in server.playerList.players) {
            p.sendSystemMessage(Component.literal("§6[Tournament] §eRegistration is now CLOSED."))
        }
        val admins = server.playerList.players.filter { it.hasPermissions(4) }
        for (admin in admins) {
            admin.sendSystemMessage(Component.literal(
                "§c§l(admin only) §r§6§l- Tournament Seeding §7(${seeded.size} entrant${if (seeded.size == 1) "" else "s"})"))
            if (seeded.isEmpty()) {
                admin.sendSystemMessage(Component.literal("§7   (no entrants)"))
            } else {
                seeded.forEachIndexed { i, (entry, elo) ->
                    admin.sendSystemMessage(Component.literal(
                        "§7   ${(i + 1).toString().padStart(2)}. §f${entry.name} §7— ELO §e$elo"))
                }
            }
        }
    }

    /** Lock (or replace) a player's roster, officially entering them. */
    fun lockIn(player: ServerPlayer, roster: List<Pokemon>) {
        entries[player.uuid] = Entry(player.uuid, player.name.string, roster.map { it.uuid })
        player.sendSystemMessage(Component.literal(
            "§a§l[Tournament] §r§aYou're entered with a ${roster.size}-Pokémon roster! §7You can /join again to change it until registration closes."))
    }

    fun isEntered(uuid: UUID): Boolean = entries.containsKey(uuid)
    fun getEntry(uuid: UUID): Entry? = entries[uuid]

    /** Entrants paired with their current ELO, sorted by ELO descending (seeding order). */
    fun seeding(): List<Pair<Entry, Int>> {
        val store = CobblemonRanked.eloStore
        val list = ArrayList<Pair<Entry, Int>>(entries.size)
        for (e in entries.values) {
            val elo = store.get(e.uuid)?.elo ?: store.getOrCreate(e.uuid, e.name).elo
            list.add(e to elo)
        }
        // Explicit Comparator (not sortedByDescending) — see EloStore.getLeaderboard for why.
        list.sortWith(Comparator { a, b -> b.second.compareTo(a.second) })
        return list
    }

    /**
     * Resolve a player's locked roster to live Pokémon from their current party + PC. Returns null
     * if the player has no entry; silently skips any roster member that can no longer be found
     * (released / traded). Order follows the locked roster.
     */
    fun resolveRoster(player: ServerPlayer): List<Pokemon>? {
        val entry = entries[player.uuid] ?: return null
        val byId = collectOwnedPokemon(player)
        return entry.pokemonUuids.mapNotNull { byId[it] }
    }

    /** Map of every Pokémon UUID the player currently owns (party + all PC boxes) → the Pokémon. */
    fun collectOwnedPokemon(player: ServerPlayer): Map<UUID, Pokemon> {
        val byId = HashMap<UUID, Pokemon>()
        try {
            for (pk in Cobblemon.storage.getParty(player)) byId[pk.uuid] = pk
        } catch (_: Exception) {}
        try {
            val pc = Cobblemon.storage.getPC(player)
            for (box in pc.boxes) {
                // Cobblemon PC boxes hold 30 slots; scan defensively with try/catch.
                for (i in 0 until 30) {
                    val pk = try { box.get(i) } catch (_: Exception) { null }
                    if (pk != null) byId[pk.uuid] = pk
                }
            }
        } catch (_: Exception) {}
        return byId
    }
}
