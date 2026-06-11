package com.cobblemonserver.pokeai.ai

import java.util.Collections
import java.util.UUID

/**
 * Records battle ids that ran the foul-play (poke-engine, `pe`) AI, so other systems can tell a
 * foul-play NPC apart from a normal one. Used by cobblemon-bridge's defeat-bounty hook to give
 * foul-play NPCs a money multiplier on the fight reward.
 *
 * Bounded LRU set — a battle id is read at most once, right after the battle ends, so only the most
 * recent ids need to be retained; the eldest are evicted past [MAX]. Reachable cross-mod by fully
 * qualified name (`com.cobblemonserver.pokeai.ai.FoulPlayBattles`) via reflection — see
 * cobblemon-bridge's PokeAiBridge.
 */
object FoulPlayBattles {

    private const val MAX = 1024

    private val ids: MutableSet<UUID> = Collections.synchronizedSet(
        Collections.newSetFromMap(object : LinkedHashMap<UUID, Boolean>(64, 0.75f, false) {
            override fun removeEldestEntry(eldest: Map.Entry<UUID, Boolean>?): Boolean = size > MAX
        }),
    )

    /** Mark [battleId] as a foul-play battle — called whenever the pe AI makes a choice. */
    @JvmStatic
    fun mark(battleId: UUID) {
        ids.add(battleId)
    }

    /** True if [battleId] ran the foul-play AI. */
    @JvmStatic
    fun wasFoulPlay(battleId: UUID): Boolean = ids.contains(battleId)
}
