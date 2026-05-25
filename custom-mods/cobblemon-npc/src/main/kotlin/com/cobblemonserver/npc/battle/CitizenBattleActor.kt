package com.cobblemonserver.npc.battle

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor
import com.cobblemon.mod.common.battles.ai.StrongBattleAI
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.minecolonies.core.entity.citizen.EntityCitizen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.phys.Vec3

/**
 * A Cobblemon BattleActor backed by a Minecolonies EntityCitizen.
 *
 * Extends AIBattleActor directly, bypassing BattleBuilder.pvn() which requires
 * a Cobblemon NPCEntity. The citizen's UUID is used as the actor game ID so
 * BATTLE_VICTORY events can be linked back to the entity.
 *
 * skill controls how well the NPC plays — range 0 (random) to 5 (optimal).
 * Default 3 gives a reasonable mid-tier opponent.
 */
class CitizenBattleActor(
    val citizen: EntityCitizen,
    pokemonList: List<BattlePokemon>,
    skill: Int = 3
) : AIBattleActor(
    gameId = citizen.uuid,
    pokemonList = pokemonList,
    battleAI = StrongBattleAI(skill)
), EntityBackedBattleActor<EntityCitizen> {
    override val entity: EntityCitizen = citizen
    override val initialPos: Vec3 = citizen.position()
    override val type: ActorType get() = ActorType.NPC

    override fun getName(): MutableComponent = Component.literal(citizen.name.string)

    override fun nameOwned(name: String): MutableComponent =
        Component.literal("${citizen.name.string}'s $name")

    override fun sendMessage(component: Component) {
        // Citizen NPCs don't receive battle chat messages — no-op.
    }
}
