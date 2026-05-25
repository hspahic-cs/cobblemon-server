package com.cobblemonserver.npc.battle

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.party
import com.cobblemonserver.npc.CobblemonNpc
import com.cobblemonserver.npc.data.NpcTeamData
import com.cobblemonserver.npc.data.NpcTeamStore
import com.cobblemonserver.npc.data.PlayerRecord
import com.cobblemonserver.npc.data.ProfessionPoolLoader
import com.cobblemonserver.npc.economy.BattleRewards
import com.cobblemonserver.npc.economy.EconomyBridge
import com.cobblemonserver.npc.progression.SwitchOutManager
import com.cobblemonserver.npc.progression.TeamProgressionManager
import com.minecolonies.core.entity.citizen.EntityCitizen
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object NpcBattleHandler {

    fun register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            val allActors = event.winners + event.losers
            val citizenActor = allActors.filterIsInstance<CitizenBattleActor>().firstOrNull()
                ?: return@subscribe

            val citizen = citizenActor.citizen
            val data = NpcTeamStore.getOrCreate(citizen)
            val professionId = resolveProfessionId(citizen)

            data.battleCount++

            val citizenLost = event.losers.contains(citizenActor)
            if (citizenLost) {
                data.lossCount++
                TeamProgressionManager.onLoss(data, professionId, resolveMaxTier(citizen, data))
            }

            if (data.battleCount % 3 == 0) {
                SwitchOutManager.maybeSwap(data, professionId)
            }

            val playerActor = allActors.filterIsInstance<PlayerBattleActor>().firstOrNull()
            val wasFirstDefeat = if (playerActor != null && citizenLost) {
                val record = data.playerRecords.getOrPut(playerActor.uuid) { PlayerRecord() }
                val firstTime = record.firstDefeatedAt == null
                record.wins++
                record.lastBattledAt = System.currentTimeMillis()
                if (firstTime) record.firstDefeatedAt = record.lastBattledAt
                firstTime
            } else if (playerActor != null) {
                val record = data.playerRecords.getOrPut(playerActor.uuid) { PlayerRecord() }
                record.losses++
                record.lastBattledAt = System.currentTimeMillis()
                false
            } else {
                false
            }

            NpcTeamStore.set(citizen, data)

            if (playerActor != null) {
                val playerEntity = citizen.level().getPlayerByUUID(playerActor.uuid) as? ServerPlayer
                if (playerEntity != null) {
                    if (citizenLost) {
                        BattleDialogue.onNpcLose(citizen.name.string, data, playerEntity, wasFirstDefeat)
                        awardPayout(data, playerEntity)
                    } else {
                        BattleDialogue.onNpcWin(citizen.name.string, data, playerEntity)
                    }
                }
            }
        }
    }

    /**
     * Initiates a Cobblemon battle between the player and the Minecolonies citizen.
     * If the citizen has no team yet, one is generated from the unemployed pool
     * scaled to colony size.
     */
    @JvmStatic
    fun startBattle(player: ServerPlayer, citizen: EntityCitizen) {
        if (!ProfessionPoolLoader.isLoaded()) {
            player.sendSystemMessage(Component.literal("[cobblemon-npc] Profession pools not loaded yet — try again in a moment."))
            return
        }

        val data = NpcTeamStore.getOrCreate(citizen)

        // A gym leader without a theme is mid-appointment — skip battle until they pick.
        if (data.gymHirerUuid != null && data.gymLeaderTheme == null) {
            player.sendSystemMessage(Component.literal("This Gym Leader hasn't picked a theme yet."))
            return
        }

        if (data.team.isEmpty()) {
            val startingTier = resolveStartingTier(citizen)
            TeamProgressionManager.buildInitialTeam(data, startingTier, citizen.uuid)
            NpcTeamStore.set(citizen, data)
        }

        val battleTeam = buildBattleTeam(data)
        if (battleTeam.isEmpty()) {
            player.sendSystemMessage(Component.literal("${citizen.name.string} has no Pokemon to battle with!"))
            return
        }

        val citizenActor = CitizenBattleActor(citizen, battleTeam)
        val playerTeam = player.party().toBattleTeam()
        val playerActor = PlayerBattleActor(player.uuid, playerTeam)

        val result = BattleRegistry.startBattle(
            battleFormat = BattleFormat.GEN_9_SINGLES,
            side1 = BattleSide(playerActor),
            side2 = BattleSide(citizenActor)
        )

        if (result is com.cobblemon.mod.common.battles.SuccessfulBattleStart) {
            CobblemonNpc.logger.info("Battle started: ${player.name.string} vs ${citizen.name.string}")
            BattleDialogue.onBattleStart(citizen.name.string, data, player)
        } else {
            player.sendSystemMessage(Component.literal("Could not start battle — ${citizen.name.string} may already be in a battle."))
            CobblemonNpc.logger.warn("Battle start failed for citizen ${citizen.uuid}: $result")
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun buildBattleTeam(data: NpcTeamData): List<BattlePokemon> {
        return data.team.mapNotNull { npcPokemon ->
            val species = PokemonSpecies.getByName(npcPokemon.species)
            if (species == null) {
                CobblemonNpc.logger.warn("cobblemon-npc: unknown species '${npcPokemon.species}' — skipping slot")
                return@mapNotNull null
            }
            val pokemon = Pokemon().also {
                it.species = species
                it.level = npcPokemon.level
            }
            BattlePokemon.safeCopyOf(pokemon)
        }
    }

    /**
     * Returns the profession pool id for this citizen. Unemployed / missing job → "unemployed".
     * Nitwits are preserved as their own id so progression/switch-out logic can opt out.
     */
    private fun resolveProfessionId(citizen: EntityCitizen): String {
        return try {
            val job = citizen.citizenData?.job ?: return "unemployed"
            val key = job.jobRegistryEntry.key.path
            ProfessionPoolLoader.professionKeyToPoolId(key)
        } catch (e: Exception) {
            CobblemonNpc.logger.warn("cobblemon-npc: could not resolve profession for ${citizen.uuid}: ${e.message}")
            "unemployed"
        }
    }

    /**
     * Determines starting tier from colony citizen count, the Minecolonies analogue of
     * MCA's village bed count.
     *
     * Small  (< 10 citizens)  → tier 1
     * Medium (10–29 citizens) → tier 2
     * Large  (30+ citizens)   → tier 3
     */
    private fun resolveStartingTier(citizen: EntityCitizen): Int {
        return try {
            val colony = citizen.citizenColonyHandler?.colonyOrRegister ?: return 1
            val pop = colony.citizenManager.currentCitizenCount
            when {
                pop >= 30 -> 3
                pop >= 10 -> 2
                else      -> 1
            }
        } catch (e: Exception) {
            CobblemonNpc.logger.warn("cobblemon-npc: could not resolve colony size for ${citizen.uuid}: ${e.message}")
            1
        }
    }

    /**
     * Gym-leader team tier cap. For a citizen on [JobGymLeader] this is the assigned hut's
     * building level (1..5). Non-gym-leaders have no cap (returns [TeamProgressionManager.MAX_TIER]
     * equivalent via the parameter's default in the progression manager).
     */
    private fun resolveMaxTier(citizen: EntityCitizen, data: NpcTeamData): Int {
        if (data.gymLeaderTheme == null) return 6
        return try {
            citizen.citizenData?.workBuilding?.buildingLevel?.coerceIn(1, 5) ?: 1
        } catch (e: Exception) {
            CobblemonNpc.logger.warn("cobblemon-npc: could not resolve gym-leader hut level for ${citizen.uuid}: ${e.message}")
            1
        }
    }

    private fun awardPayout(data: NpcTeamData, player: ServerPlayer) {
        val payout = BattleRewards.computePayout(data)
        if (payout <= 0) return
        if (!EconomyBridge.addBalance(player.uuid, payout)) return
        player.sendSystemMessage(
            Component.literal("You earned \$$payout").withStyle(ChatFormatting.GOLD)
        )
    }
}
