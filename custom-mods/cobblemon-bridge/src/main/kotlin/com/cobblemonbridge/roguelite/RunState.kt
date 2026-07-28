package com.cobblemonbridge.roguelite

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_bridge/roguelite/run")

/**
 * A single PokéRogue-mode run.
 *
 * The run party lives **here**, not in the player's [com.cobblemon.mod.common.api.storage.party.PlayerPartyStore].
 * Nothing caught or earned during a run persists past it (design decision 1), so we never
 * swap, mutate, or restore the player's real party — which means no crash, restart, or
 * botched restore can cost anyone their actual Pokémon. Battles are handed a synthetic
 * party store built from [party], the same way `RankedBattle.buildTempParty()` does it.
 *
 * Runs are checkpointable (design decision 2): [toNbt] writes the whole run — party
 * included — into player NBT, which the server saves on the regular tick and on logout, so
 * a run survives disconnect, clean restart, and crash. This is the same mechanism
 * `TowerGauntletHook.persist()` uses; the tower only needs a `Set<UUID>` party snapshot
 * because it battles the player's real party, whereas we must serialize the Pokémon
 * themselves.
 *
 * @property wave the wave about to be fought (1-based). Incremented on victory.
 * @property party the live run party. Permadeath removes entries; order is party order.
 * @property credits run-scoped currency, spent in the between-wave shop. Converted to
 *   server currency at run end — it is never itself a server balance.
 * @property seed fixes wave generation so a resumed run rolls the same opponents it would
 *   have rolled before the disconnect. Per-wave draws use `seed` combined with [wave].
 * @property bossesCleared count of fixed boss trainers beaten, for payout curves.
 */
data class RunState(
    var wave: Int = 1,
    val party: MutableList<Pokemon> = mutableListOf(),
    var credits: Int = 0,
    val seed: Long = 0L,
    var bossesCleared: Int = 0,
) {
    /** A run ends when every party member has fainted — permadeath, not a whiteout. */
    fun isWiped(): Boolean = party.isEmpty()

    /**
     * Drop a fainted Pokémon from the run for good. Returns true if it was present.
     *
     * Permadeath is enforced here rather than by inspecting HP at battle end, because a
     * revive used mid-battle legitimately brings a Pokémon back and must not count.
     */
    fun kill(pokemon: Pokemon): Boolean = party.removeIf { it.uuid == pokemon.uuid }

    fun toNbt(registryAccess: RegistryAccess): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("wave", wave)
        tag.putInt("credits", credits)
        tag.putLong("seed", seed)
        tag.putInt("bossesCleared", bossesCleared)
        val list = ListTag()
        party.forEach { list.add(it.saveToNBT(registryAccess)) }
        tag.put("party", list)
        return tag
    }

    companion object {
        /**
         * Rebuild a run from its checkpoint. Returns null if the snapshot is unusable, which
         * the caller must treat as "no run" rather than "empty run" — an empty party would
         * otherwise read as an instant wipe.
         *
         * Individual Pokémon that fail to load are dropped with a warning rather than voiding
         * the whole run: losing one party member to a bad tag is a far better outcome for the
         * player than losing the run, and a Cobblemon version bump is the likely cause.
         */
        fun fromNbt(registryAccess: RegistryAccess, tag: CompoundTag): RunState? {
            val wave = tag.getInt("wave")
            if (wave < 1) {
                log.warn("roguelite: checkpoint has wave={} — discarding", wave)
                return null
            }
            val list = tag.getList("party", 10 /* TAG_COMPOUND */)
            val party = mutableListOf<Pokemon>()
            for (i in 0 until list.size) {
                runCatching { Pokemon.loadFromNBT(registryAccess, list.getCompound(i)) }
                    .onSuccess { party.add(it) }
                    .onFailure { log.warn("roguelite: dropping unreadable run Pokémon at slot {}", i, it) }
            }
            if (party.isEmpty()) {
                log.warn("roguelite: checkpoint restored no party members — discarding run")
                return null
            }
            return RunState(
                wave = wave,
                party = party,
                credits = tag.getInt("credits"),
                seed = tag.getLong("seed"),
                bossesCleared = tag.getInt("bossesCleared"),
            )
        }
    }
}
