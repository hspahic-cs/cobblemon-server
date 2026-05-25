package com.cobblemonserver.npc.progression

import com.cobblemonserver.npc.data.NpcPokemon
import com.cobblemonserver.npc.data.NpcTeamData
import com.cobblemonserver.npc.data.ProfessionPoolLoader
import kotlin.random.Random

object SwitchOutManager {

    private const val MAX_LEVEL = 75

    /**
     * Called every 3 battles (win or loss). If the NPC's team contains any Pokemon tagged
     * from a different pool than their current profession, swaps one out for a profession-pool
     * Pokemon. One swap per call — teams drift gradually, not all at once.
     *
     * No swap occurs if:
     * - All Pokemon are already profession-tagged
     * - The NPC is unemployed (unemployed pool IS their profession)
     * - The NPC is a Nitwit (can never change jobs)
     * - The profession pool is empty or fully represented on the team already
     */
    fun maybeSwap(data: NpcTeamData, professionId: String) {
        if (professionId == "unemployed" || professionId == "nitwit") return

        val mismatched = data.team.filter { it.poolTag != professionId && it.poolTag != "signature" }
        if (mismatched.isEmpty()) return

        val pool = ProfessionPoolLoader.getPool(professionId)
        val existingSpecies = data.team.map { it.species }.toSet()
        val candidates = pool.filter { it !in existingSpecies }
        if (candidates.isEmpty()) return

        val outgoing = mismatched.random()
        val incomingSpecies = candidates.random()
        val incomingLevel = (outgoing.level + Random.nextInt(-2, 3)).coerceIn(1, MAX_LEVEL)

        val index = data.team.indexOf(outgoing)
        data.team[index] = NpcPokemon(
            species = incomingSpecies,
            level = incomingLevel,
            poolTag = professionId
        )
    }
}
