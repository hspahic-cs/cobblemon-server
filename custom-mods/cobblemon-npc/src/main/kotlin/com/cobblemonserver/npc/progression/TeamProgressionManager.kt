package com.cobblemonserver.npc.progression

import com.cobblemonserver.npc.data.NpcPokemon
import com.cobblemonserver.npc.data.NpcTeamData
import com.cobblemonserver.npc.data.ProfessionPoolLoader
import com.cobblemonserver.npc.gym.GymLeaderPoolLoader
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

object TeamProgressionManager {

    data class TierConfig(val levelThreshold: Int, val teamSize: Int, val itemTier: ItemTier, val baseLevel: Int)

    enum class ItemTier { NONE, BASIC, FULL }

    val tiers: Map<Int, TierConfig> = mapOf(
        1 to TierConfig(levelThreshold = 0,  teamSize = 1, itemTier = ItemTier.NONE,  baseLevel = 8),
        2 to TierConfig(levelThreshold = 15, teamSize = 2, itemTier = ItemTier.NONE,  baseLevel = 15),
        3 to TierConfig(levelThreshold = 30, teamSize = 3, itemTier = ItemTier.BASIC, baseLevel = 30),
        4 to TierConfig(levelThreshold = 45, teamSize = 4, itemTier = ItemTier.FULL,  baseLevel = 45),
        5 to TierConfig(levelThreshold = 55, teamSize = 5, itemTier = ItemTier.FULL,  baseLevel = 55),
        6 to TierConfig(levelThreshold = 65, teamSize = 6, itemTier = ItemTier.FULL,  baseLevel = 65),
    )

    private const val MAX_LEVEL = 75
    private const val GYM_MAX_LEVEL = 100
    private const val MAX_TIER = 6

    /**
     * Called when the NPC loses a battle. Levels up each Pokemon by 4–6 (random, weighted
     * toward the team average so stragglers catch up faster). Then checks tier advancement.
     *
     * [professionId] is the pool id for the citizen's current job (as produced by
     * [ProfessionPoolLoader.professionKeyToPoolId]). Gym leaders use a separate slot model
     * and the value is ignored.
     *
     * [maxTier] caps how far the team can promote. For gym leaders this is the hut's
     * building level (1..5); for other citizens it is [MAX_TIER] (no cap).
     */
    fun onLoss(data: NpcTeamData, professionId: String, maxTier: Int = MAX_TIER) {
        if (data.team.isEmpty()) return

        val teamAvg = data.team.map { it.level }.average()
        val cap = if (data.gymLeaderTheme != null) GYM_MAX_LEVEL else MAX_LEVEL

        data.team.forEach { pokemon ->
            val gain = weightedLevelGain(pokemon.level, teamAvg)
            pokemon.level = (pokemon.level + gain).coerceAtMost(cap)
        }

        checkTierAdvancement(data, professionId, maxTier)
    }

    private fun weightedLevelGain(currentLevel: Int, teamAvg: Double): Int {
        val delta = currentLevel - teamAvg
        return when {
            delta < -3 -> 6
            delta > 3  -> 4
            else -> Random.nextInt(4, 7)
        }
    }

    private fun checkTierAdvancement(data: NpcTeamData, professionId: String, maxTier: Int) {
        if (data.gymLeaderTheme != null) {
            advanceGymLeaderTier(data, maxTier)
            return
        }

        val effectiveCap = maxTier.coerceIn(1, MAX_TIER)
        if (data.currentTier >= effectiveCap) return

        val nextTier = data.currentTier + 1
        val nextConfig = tiers[nextTier] ?: return

        val allMeetThreshold = data.team.all { it.level >= nextConfig.levelThreshold }
        if (!allMeetThreshold) return

        data.currentTier = nextTier

        val pool = ProfessionPoolLoader.getPool(professionId)
        val existing = data.team.map { it.species }.toSet()
        val candidates = pool.filter { it !in existing }

        if (candidates.isEmpty()) return

        val species = candidates.random()
        val teamAvg = data.team.map { it.level }.average().roundToInt()
        val newLevel = (teamAvg + Random.nextInt(-3, 4)).coerceIn(1, MAX_LEVEL)

        data.team.add(NpcPokemon(
            species = species,
            level = newLevel,
            poolTag = professionId
        ))

        checkTierAdvancement(data, professionId, maxTier)
    }

    /**
     * Gym-leader tier progression. [maxTier] is the hut's building level (1..5):
     *   1 → team of 3
     *   2 → team of 4
     *   3 → team of 5
     *   4 → team of 6
     *   5 → team of 6 + legendary slot at avg ≥ 81
     * Team size earned by teamAvg is clamped to this cap.
     */
    private fun advanceGymLeaderTier(data: NpcTeamData, maxTier: Int) {
        val themeId = data.gymLeaderTheme ?: return
        val theme = GymLeaderPoolLoader.getTheme(themeId) ?: return

        val teamAvg = data.team.map { it.level }.average()
        val earnedSize = when {
            teamAvg >= 51 -> 6
            teamAvg >= 31 -> 5
            teamAvg >= 16 -> 4
            else          -> 3
        }
        val sizeCap = (maxTier + 2).coerceAtMost(6)
        val targetSize = earnedSize.coerceAtMost(sizeCap)

        if (data.team.size < targetSize) {
            val nextSlotNumber = data.team.size + 1
            val slot = theme.maxTeam.firstOrNull { it.slot == nextSlotNumber }
            if (slot != null && data.team.none { it.species == slot.species }) {
                val newLevel = teamAvg.roundToInt().coerceIn(1, GYM_MAX_LEVEL)
                data.team.add(NpcPokemon(
                    species = slot.species,
                    level = newLevel,
                    poolTag = "gym:$themeId"
                ))
            }
        }

        val legendaryUnlocked = maxTier >= 5
        if (legendaryUnlocked && teamAvg >= 81 && theme.legendarySpecies != null && theme.legendaryReplacesSlot != null) {
            val slotNumber = theme.legendaryReplacesSlot
            val index = slotNumber - 1
            if (index in data.team.indices && data.team[index].species != theme.legendarySpecies) {
                val existing = data.team[index]
                data.team[index] = NpcPokemon(
                    species = theme.legendarySpecies,
                    level = existing.level,
                    poolTag = "gym:$themeId:legendary"
                )
            }
        }
    }

    /**
     * Generates an initial team for a fresh citizen. Slot 0 is the signature Pokemon —
     * rolled deterministically from the signature pool keyed by [seedUuid] the first time
     * this runs, and preserved across later rebuilds. Remaining slots draw from the
     * unemployed pool; drift toward the current job happens later via [SwitchOutManager].
     */
    fun buildInitialTeam(data: NpcTeamData, startingTier: Int, seedUuid: UUID) {
        val config = tiers[startingTier] ?: tiers[1]!!
        data.currentTier = startingTier

        val unemployedPool = ProfessionPoolLoader.getPool("unemployed")
        if (unemployedPool.isEmpty()) return

        val signatureSpecies = resolveSignature(data, seedUuid)
        if (signatureSpecies != null) {
            val level = (config.baseLevel + Random.nextInt(-3, 4)).coerceIn(1, MAX_LEVEL)
            data.team.add(NpcPokemon(
                species = signatureSpecies,
                level = level,
                poolTag = "signature"
            ))
        }

        val remaining = config.teamSize - data.team.size
        if (remaining <= 0) return

        val filler = unemployedPool.filter { it != signatureSpecies }.shuffled()
        if (filler.isEmpty()) return

        repeat(remaining) { slot ->
            val species = filler[slot % filler.size]
            val level = (config.baseLevel + Random.nextInt(-3, 4)).coerceIn(1, MAX_LEVEL)
            data.team.add(NpcPokemon(
                species = species,
                level = level,
                poolTag = "unemployed"
            ))
        }
    }

    private fun resolveSignature(data: NpcTeamData, seedUuid: UUID): String? {
        data.signatureSpecies?.let { return it }

        val signaturePool = ProfessionPoolLoader.getPool("signature")
        if (signaturePool.isEmpty()) return null

        val seed = seedUuid.mostSignificantBits xor seedUuid.leastSignificantBits
        val pick = signaturePool[Random(seed).nextInt(signaturePool.size)]
        data.signatureSpecies = pick
        return pick
    }
}
