package com.cobblemonroguelite.run

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/run")

/**
 * A single PokéRogue-mode run.
 *
 * The run party lives **here**, not in the player's [com.cobblemon.mod.common.api.storage.party.PlayerPartyStore].
 * Nothing caught or earned during a run persists past it (design decision 1), so we never
 * swap, mutate, or restore the player's real party — which means no crash, restart, or
 * botched restore can cost anyone their actual Pokémon. Battles are handed a synthetic
 * party store built from [party], the same way `RankedBattle.buildTempParty()` does it.
 *
 * Runs are checkpointable (design decision 2): [toNbt] writes the whole run — party included —
 * and [RunStore] persists it as world save data, so a run survives disconnect, clean restart,
 * and crash. Note this is *not* the player-NBT mechanism the battle tower uses; see [RunStore]
 * for why that one would delete a run party on any in-world death.
 *
 * @property wave the wave about to be fought (1-based). Incremented on victory.
 * @property party the live run party. Permadeath removes entries; order is party order.
 *   **Mutated from battle threads.** The monitor is the list object itself: everything in this
 *   class touches it under `synchronized(party)`, and anything outside that has to reach the
 *   raw list must take the same lock. Readers should prefer [partySnapshot].
 * @property credits run-scoped currency, spent in the between-wave shop. Converted to
 *   server currency at run end — it is never itself a server balance.
 * @property seed fixes wave generation so a resumed run rolls the same opponents it would
 *   have rolled before the disconnect. Per-wave draws use `seed` combined with [wave].
 *   Deliberately has **no default**: a caller that forgot to supply one would hand every player
 *   on the server the identical run, and nothing about that failure is visible in play until two
 *   players compare notes. Making it a required argument turns it into a compile error instead.
 * @property bossesCleared count of fixed boss trainers beaten, for payout curves.
 * @property payoutTable which payout table this run pays from, pinned at run start rather than read
 *   from live config at run end. A run is a multi-session commitment (§2.19 puts it at days), so an
 *   operator retuning between somebody's wave 3 and their wave 200 would otherwise change what an
 *   in-flight run pays — the same class of "a run in progress changed under the player" the seed
 *   exists to prevent (§2.16), except this one is invisible until the payout lands. Null means the
 *   run was started before a table was configured and falls back to [com.cobblemonroguelite.data.payout.PayoutTables.DEFAULT_TABLE]
 *   at end.
 *
 *   Note what this pins and what it cannot: the table *id*, not the table *contents*. A datapack
 *   reload still changes what the entries pay. Pinning contents would mean copying the resolved
 *   table into every checkpoint and versioning it, which buys a guarantee nobody asked for against a
 *   change only an operator can make.
 */
data class RunState(
    var wave: Int = 1,
    val party: MutableList<Pokemon> = mutableListOf(),
    var credits: Int = 0,
    val seed: Long,
    var bossesCleared: Int = 0,
    val payoutTable: ResourceLocation? = null,
) {
    /** A run ends when every party member has fainted — permadeath, not a whiteout. */
    fun isWiped(): Boolean = synchronized(party) { party.isEmpty() }

    /**
     * The party as it stood at one instant, safe to iterate off the thread that owns the battle.
     *
     * A defensive copy taken under the lock, rather than a synchronized list wrapper: a wrapper
     * makes each individual call atomic but leaves *iteration* — which is what [toNbt] and the
     * world autosave do — needing an explicit lock anyway, and holding the monitor across six
     * `saveToNBT` calls would stall the battle thread that is trying to report a faint. Copying
     * six references costs nothing and the lock is held for exactly that long.
     *
     * This makes the list safe to walk; it does not make the Pokémon in it immutable, so a
     * snapshot taken mid-battle can still serialize a Pokémon whose HP is being written. That is
     * why checkpoints are taken at wave boundaries and not per-turn.
     */
    fun partySnapshot(): List<Pokemon> = synchronized(party) { party.toList() }

    /**
     * Drop a fainted Pokémon from the run for good. Returns true if it was present.
     *
     * Permadeath is enforced here rather than by inspecting HP at battle end, because a
     * revive used mid-battle legitimately brings a Pokémon back and must not count.
     *
     * **Identity contract.** Matching is by UUID, so whatever the battle is handed must preserve
     * it. `Pokemon.clone()` defaults to `newUUID = true`, which would make every call here a
     * silent no-op and permadeath would simply never fire — pass `clone(newUUID = false)`, or
     * hand the run Pokémon over uncloned.
     */
    fun kill(pokemon: Pokemon): Boolean = synchronized(party) { party.removeIf { it.uuid == pokemon.uuid } }

    fun toNbt(registryAccess: RegistryAccess): CompoundTag {
        val tag = CompoundTag()
        tag.putInt(SCHEMA_KEY, SCHEMA_VERSION)
        tag.putInt("wave", wave)
        tag.putInt("credits", credits)
        tag.putLong("seed", seed)
        tag.putInt("bossesCleared", bossesCleared)
        payoutTable?.let { tag.putString("payoutTable", it.toString()) }
        val list = ListTag()
        partySnapshot().forEach { list.add(it.saveToNBT(registryAccess)) }
        tag.put("party", list)
        return tag
    }

    companion object {
        /**
         * Bump whenever the shape or the *meaning* of anything [toNbt] writes changes — a renamed
         * key, a changed unit, a field that starts counting from something else. Without this a
         * format change reads old saves as if they were new ones and silently resumes runs with
         * wrong values, which is the one failure mode a checkpoint must never have.
         */
        const val SCHEMA_VERSION = 2

        private const val SCHEMA_KEY = "schemaVersion"

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
            val version = tag.getInt(SCHEMA_KEY)
            if (version != SCHEMA_VERSION) {
                // Refusal is the safe half of a migration path. A tag stamped with a version we do
                // not know is a tag whose fields we cannot claim to understand, and a half-parsed
                // run — right party, wrong wave — is worse than no run, because it does not
                // announce itself: the player just keeps playing a run that is quietly wrong.
                // An absent key reads as 0, i.e. a pre-versioning checkpoint, and is refused for
                // the same reason. When the format does change, migrate below-version tags here
                // and keep the refusal for above-version ones — those are a downgraded server
                // reading a save it has no way to represent.
                log.warn(
                    "roguelite: checkpoint schema v{} is not v{} — discarding run (no migration path)",
                    version, SCHEMA_VERSION,
                )
                return null
            }
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
                // An unparseable id restores as null rather than failing the run: null falls back to
                // the default table at payout, which is a table the player might still be paid from,
                // where discarding the checkpoint would cost them the whole run over a string.
                payoutTable = tag.getString("payoutTable").takeIf { it.isNotEmpty() }
                    ?.let { ResourceLocation.tryParse(it) },
            )
        }
    }
}
