package com.cobblemonserver.npc.data

import com.cobblemonserver.npc.CobblemonNpc
import com.minecolonies.core.entity.citizen.EntityCitizen
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

/**
 * Per-entity attachment holding Pokemon team + gym-leader state for Minecolonies citizens.
 * Persists with the entity's NBT; survives world unload.
 *
 * Note: Minecolonies citizens can die and respawn as fresh entities. When that happens the
 * attachment is lost (it lives on the entity, not on `ICitizenData`). If this becomes a
 * problem in playtest, migrate the store onto the colony's saved data keyed by citizen id.
 */
object NpcTeamStore {

    private val ATTACHMENT_TYPES: DeferredRegister<AttachmentType<*>> =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CobblemonNpc.MOD_ID)

    private val TEAM_DATA_HOLDER = ATTACHMENT_TYPES.register(
        "npc_team_data",
        Supplier {
            AttachmentType.builder(Supplier { NpcTeamData() })
                .serialize(NpcTeamData.CODEC)
                .build()
        }
    )

    fun register(modBus: IEventBus) {
        ATTACHMENT_TYPES.register(modBus)
    }

    private fun type(): AttachmentType<NpcTeamData> = TEAM_DATA_HOLDER.get()

    fun get(citizen: EntityCitizen): NpcTeamData? =
        if (citizen.hasData(type())) citizen.getData(type()) else null

    fun getOrCreate(citizen: EntityCitizen): NpcTeamData = citizen.getData(type())

    fun set(citizen: EntityCitizen, data: NpcTeamData) {
        citizen.setData(type(), data)
    }

    fun has(citizen: EntityCitizen): Boolean = citizen.hasData(type())
}
