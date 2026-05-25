package com.cobblemonserver.npc.gym

import com.cobblemonserver.npc.data.NpcPokemon
import com.cobblemonserver.npc.data.NpcTeamData
import com.cobblemonserver.npc.data.NpcTeamStore
import com.minecolonies.core.entity.citizen.EntityCitizen
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

object GymLeaderManager {

    private const val TITLE_PREFIX = "[Gym Leader] "
    private const val PICKER_OPTIONS = 4

    enum class HireFailure {
        NO_COLONY,
        ALREADY_GYM_LEADER,
        POOL_NOT_LOADED
    }

    enum class FireFailure {
        NOT_GYM_LEADER
    }

    sealed class HireResult {
        object Success : HireResult()
        data class Failed(val reason: HireFailure) : HireResult()
    }

    sealed class FireResult {
        object Success : FireResult()
        data class Failed(val reason: FireFailure) : FireResult()
    }

    /**
     * Called by [com.cobblemonserver.npc.gym.GymAssignmentListener] when a citizen is assigned
     * to [com.cobblemonserver.npc.job.JobGymLeader]. The "hirer" is the colony owner — the
     * player who placed the hut. Returns Success, or Failed with a reason that is logged
     * (not shown to any player, since this is an event-driven flow).
     */
    fun onJobAssigned(citizen: EntityCitizen): HireResult {
        if (!GymLeaderPoolLoader.isLoaded()) return HireResult.Failed(HireFailure.POOL_NOT_LOADED)

        val colony = citizen.citizenColonyHandler?.colonyOrRegister
            ?: return HireResult.Failed(HireFailure.NO_COLONY)

        val ownerUuid = colony.permissions.owner
            ?: return HireResult.Failed(HireFailure.NO_COLONY)

        val existingData = NpcTeamStore.get(citizen)
        if (existingData?.gymHirerUuid != null) {
            return HireResult.Failed(HireFailure.ALREADY_GYM_LEADER)
        }

        val level = citizen.level() as? ServerLevel
            ?: return HireResult.Failed(HireFailure.NO_COLONY)
        val registry = GymLeaderRegistry.get(level)
        val candidates = GymLeaderPoolLoader.availableThemes(registry.usedThemes)
            .shuffled()
            .take(PICKER_OPTIONS)
            .map { it.id }

        if (candidates.isEmpty()) {
            return HireResult.Failed(HireFailure.POOL_NOT_LOADED)
        }

        val data = existingData ?: NpcTeamData()
        data.gymHirerUuid = ownerUuid
        data.originalName = citizen.customName?.string
        NpcTeamStore.set(citizen, data)

        applyTitleToName(citizen, data.originalName)

        registry.recordPicker(citizen.uuid, ownerUuid, candidates)

        // If the owner is online, send them the picker right away; otherwise they'll get it
        // next time they right-click the citizen (resendPickerIfOwed).
        val ownerPlayer = level.server.playerList.getPlayer(ownerUuid)
        if (ownerPlayer != null) {
            sendPickerMessage(ownerPlayer, citizen, candidates)
        }

        return HireResult.Success
    }

    /**
     * Called by [com.cobblemonserver.npc.gym.GymAssignmentListener] when a citizen is removed
     * from [com.cobblemonserver.npc.job.JobGymLeader] (reassigned or hut destroyed). Clears
     * all gym-leader state and restores the citizen's original name.
     */
    fun onJobRemoved(citizen: EntityCitizen): FireResult {
        val data = NpcTeamStore.get(citizen) ?: return FireResult.Failed(FireFailure.NOT_GYM_LEADER)
        if (data.gymHirerUuid == null) return FireResult.Failed(FireFailure.NOT_GYM_LEADER)

        val level = citizen.level() as? ServerLevel ?: return FireResult.Failed(FireFailure.NOT_GYM_LEADER)
        val registry = GymLeaderRegistry.get(level)

        data.gymLeaderTheme?.let { registry.releaseTheme(it) }
        registry.consumePicker(citizen.uuid)

        restoreOriginalName(citizen, data.originalName)

        data.gymLeaderTheme = null
        data.gymHirerUuid = null
        data.originalName = null
        data.team.clear()
        data.battleCount = 0
        data.lossCount = 0
        data.currentTier = 1
        NpcTeamStore.set(citizen, data)

        return FireResult.Success
    }

    /**
     * Commits a theme choice made from the hirer's chat picker.
     * Returns true on success; false if the picker no longer applies (theme taken, hirer mismatch, etc.).
     */
    fun chooseTheme(player: ServerPlayer, citizen: EntityCitizen, themeId: String): Boolean {
        val level = citizen.level() as? ServerLevel ?: return false
        val registry = GymLeaderRegistry.get(level)
        val picker = registry.getPicker(citizen.uuid) ?: return false
        if (picker.hirerUuid != player.uuid) return false
        if (themeId !in picker.themeIds) return false
        if (themeId in registry.usedThemes) return false

        val theme = GymLeaderPoolLoader.getTheme(themeId) ?: return false

        val data = NpcTeamStore.getOrCreate(citizen)
        data.gymLeaderTheme = themeId
        data.team.clear()
        theme.startingThree.forEach { species ->
            data.team.add(NpcPokemon(species = species, level = 1, poolTag = "gym:$themeId"))
        }
        data.currentTier = 1
        data.battleCount = 0
        data.lossCount = 0
        NpcTeamStore.set(citizen, data)

        registry.markThemeUsed(themeId)
        registry.consumePicker(citizen.uuid)

        player.sendSystemMessage(
            Component.literal("Gym Leader theme set: ").append(
                Component.literal(theme.name).withStyle(ChatFormatting.AQUA)
            )
        )
        return true
    }

    fun resendPickerIfOwed(player: ServerPlayer, citizen: EntityCitizen): Boolean {
        val level = citizen.level() as? ServerLevel ?: return false
        val registry = GymLeaderRegistry.get(level)
        val picker = registry.getPicker(citizen.uuid) ?: return false
        if (picker.hirerUuid != player.uuid) return false
        sendPickerMessage(player, citizen, picker.themeIds)
        return true
    }

    // -------------------------------------------------------------------------
    // Name helpers
    // -------------------------------------------------------------------------

    private fun applyTitleToName(citizen: EntityCitizen, originalName: String?) {
        val base = originalName?.takeIf { it.isNotBlank() } ?: citizen.name.string
        citizen.customName = Component.literal(TITLE_PREFIX + base)
        citizen.isCustomNameVisible = true
    }

    private fun restoreOriginalName(citizen: EntityCitizen, originalName: String?) {
        if (originalName != null) {
            citizen.customName = Component.literal(originalName)
        } else {
            val current = citizen.customName?.string
            if (current != null && current.startsWith(TITLE_PREFIX)) {
                citizen.customName = Component.literal(current.removePrefix(TITLE_PREFIX))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Picker UI
    // -------------------------------------------------------------------------

    private fun sendPickerMessage(player: ServerPlayer, citizen: EntityCitizen, themeIds: List<String>) {
        player.sendSystemMessage(Component.literal("─── Choose a Gym Leader Theme ───").withStyle(ChatFormatting.GOLD))
        themeIds.forEach { id ->
            val theme = GymLeaderPoolLoader.getTheme(id) ?: return@forEach
            val command = "/cobblemon-npc gym choose-theme ${citizen.uuid} $id"
            val starters = theme.startingThree.joinToString(", ")
            val hover = Component.literal("${theme.description}\n\nStarting three: $starters")
            val line = Component.literal("  ▶ ").withStyle(ChatFormatting.GRAY)
                .append(
                    Component.literal(theme.name)
                        .withStyle(
                            Style.EMPTY
                                .withColor(ChatFormatting.AQUA)
                                .withBold(true)
                                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                        )
                )
                .append(Component.literal("  ($starters)").withStyle(ChatFormatting.DARK_GRAY))
            player.sendSystemMessage(line)
        }
        player.sendSystemMessage(Component.literal("Click a theme name to lock it in. Pick carefully — themes only release when you fire this Gym Leader.").withStyle(ChatFormatting.GRAY))
    }
}
