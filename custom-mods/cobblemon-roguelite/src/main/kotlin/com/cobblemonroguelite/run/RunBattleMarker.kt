package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import java.util.UUID

/**
 * A wave battle that started and has not been reported as finished — §2.10's battle-in-progress
 * marker.
 *
 * Present means "a battle is happening right now, or was happening when we last heard anything".
 * Absent means the run is between waves. That is the entire state model, and both halves matter:
 * a marker that is never cleared makes every login look like a rage-quit, and one that is never set
 * makes quitting a losing battle free.
 *
 * @property wave the wave that was interrupted, kept so the player can be told what they dropped out
 *   of. [RunState.wave] normally agrees with it and is still not a substitute: this one is the wave
 *   the battle was *for*, fixed when the battle began, and anything that moves the run afterwards —
 *   a future between-wave step, a repair, an operator — makes them disagree without making either
 *   wrong. Reading the run's wave for the message would then describe the fight the player is about
 *   to have rather than the one they dropped out of.
 * @property boot the [ServerBootId] of the process that started the battle. The comparison this
 *   exists for is in [DisconnectAttribution].
 * @property onField the Pokémon that were out when we last heard. **UUIDs, not indices or names**:
 *   [RunState.kill] matches on UUID and the party shifts under permadeath, so an index would kill
 *   whoever moved into that slot. Empty is legal — a battle can be stamped before anything is sent
 *   out — and means the interruption costs nothing.
 */
data class RunBattleMarker(
    val wave: Int,
    val boot: UUID,
    val onField: List<UUID>,
) {
    fun toNbt(): CompoundTag = CompoundTag().apply {
        putInt("wave", wave)
        // Strings rather than `putUUID`'s int array, for the same reason [RunStore] keeps runs under
        // string keys: an operator hand-repairing a run file has to be able to read this and match it
        // against a Pokémon UUID in the party beside it.
        putString("boot", boot.toString())
        put("onField", ListTag().apply { onField.forEach { add(StringTag.valueOf(it.toString())) } })
    }

    companion object {

        /**
         * Null when the marker cannot be read.
         *
         * The failure direction is deliberate: an unreadable marker reads as **no battle**, so it
         * costs the player nothing. The alternative is killing Pokémon on the strength of a tag we
         * just admitted we could not parse, and permadeath is not a thing to guess at. The cost of
         * this direction is one skipped penalty; the cost of the other is a player's party.
         */
        fun fromNbt(tag: CompoundTag): RunBattleMarker? {
            val wave = tag.getInt("wave").takeIf { it >= 1 } ?: return null
            val boot = runCatching { UUID.fromString(tag.getString("boot")) }.getOrNull() ?: return null
            val list = tag.getList("onField", 8 /* TAG_STRING */)
            val onField = (0 until list.size).mapNotNull {
                runCatching { UUID.fromString(list.getString(it)) }.getOrNull()
            }
            return RunBattleMarker(wave, boot, onField)
        }
    }
}
