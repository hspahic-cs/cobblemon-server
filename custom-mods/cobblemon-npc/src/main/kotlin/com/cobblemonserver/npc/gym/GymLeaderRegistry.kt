package com.cobblemonserver.npc.gym

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Per-server set of gym leader themes currently claimed. Persists on the overworld's
 * data storage so the pool survives server restarts.
 *
 * Also holds the transient picker offers (a villager the hirer has just appointed but
 * has not yet picked a theme for). These survive restart too so a player can disconnect,
 * reconnect, and still complete the choice.
 */
class GymLeaderRegistry : SavedData {

    val usedThemes: MutableSet<String> = mutableSetOf()

    /** villagerUuid → current pending offer (hirer + 4 candidate theme ids) */
    val pendingPickers: MutableMap<UUID, PendingPicker> = mutableMapOf()

    data class PendingPicker(
        val hirerUuid: UUID,
        val themeIds: List<String>
    )

    constructor() : super()

    constructor(nbt: CompoundTag) : super() {
        val themes = nbt.getList("usedThemes", Tag.TAG_STRING.toInt())
        for (i in 0 until themes.size) {
            usedThemes.add(themes.getString(i))
        }

        val pickers = nbt.getList("pendingPickers", Tag.TAG_COMPOUND.toInt())
        for (i in 0 until pickers.size) {
            val entry = pickers.getCompound(i)
            val villagerId = UUID.fromString(entry.getString("villagerUuid"))
            val hirerId = UUID.fromString(entry.getString("hirerUuid"))
            val idsTag = entry.getList("themeIds", Tag.TAG_STRING.toInt())
            val ids = (0 until idsTag.size).map { idsTag.getString(it) }
            pendingPickers[villagerId] = PendingPicker(hirerId, ids)
        }
    }

    override fun save(nbt: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        val themes = ListTag()
        usedThemes.forEach { themes.add(net.minecraft.nbt.StringTag.valueOf(it)) }
        nbt.put("usedThemes", themes)

        val pickers = ListTag()
        pendingPickers.forEach { (villagerId, picker) ->
            val entry = CompoundTag()
            entry.putString("villagerUuid", villagerId.toString())
            entry.putString("hirerUuid", picker.hirerUuid.toString())
            val ids = ListTag()
            picker.themeIds.forEach { ids.add(net.minecraft.nbt.StringTag.valueOf(it)) }
            entry.put("themeIds", ids)
            pickers.add(entry)
        }
        nbt.put("pendingPickers", pickers)
        return nbt
    }

    fun markThemeUsed(themeId: String) {
        usedThemes.add(themeId)
        setDirty()
    }

    fun releaseTheme(themeId: String) {
        usedThemes.remove(themeId)
        setDirty()
    }

    fun recordPicker(villagerUuid: UUID, hirerUuid: UUID, themeIds: List<String>) {
        pendingPickers[villagerUuid] = PendingPicker(hirerUuid, themeIds)
        setDirty()
    }

    fun consumePicker(villagerUuid: UUID): PendingPicker? {
        val result = pendingPickers.remove(villagerUuid)
        if (result != null) setDirty()
        return result
    }

    fun getPicker(villagerUuid: UUID): PendingPicker? = pendingPickers[villagerUuid]

    companion object {
        private const val DATA_ID = "cobblemon_npc_gym_leaders"

        fun get(level: ServerLevel): GymLeaderRegistry {
            return level.dataStorage.computeIfAbsent(
                SavedData.Factory(
                    ::GymLeaderRegistry,
                    { nbt, _ -> GymLeaderRegistry(nbt) },
                    null as net.minecraft.util.datafix.DataFixTypes?
                ),
                DATA_ID
            )
        }
    }
}
