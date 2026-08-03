package com.cobblemonroguelite.progression

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("cobblemon_roguelite/progression")

/**
 * What every player has earned across all of their runs: candy and IV floors, per species (§2.15,
 * §2.17).
 *
 * ### Why world [SavedData], for the same reason [com.cobblemonroguelite.run.RunStore] is
 *
 * Raw `player.persistentData` does not survive a death-respawn — `ServerPlayer.restoreFrom` copies
 * only the `PlayerPersisted` subtag across the clone and drops the rest — so a player killed by a
 * creeper would come back with their whole collection's worth of candy and floors gone. That
 * argument is *stronger* here than it is for the run store: a lost run is one run, a lost
 * progression record is every run the player has ever finished, and there is no way to earn it back
 * except by playing all of them again.
 *
 * ### Why a separate file from the run store, and not another key in it
 *
 * The two have opposite lifetimes. A run row is created at run start and **deleted** at run end;
 * a progression row is created on a player's first in-run catch and never deleted. Sharing a file
 * would mean one corrupt or hand-edited entry could cost the other its contents, and it would put
 * the permanent record inside the file that ops are most likely to be poking at when a run goes
 * wrong. Separate files also make the boundary §1.1 restates legible on disk: what leaves a run is
 * exactly what is in *this* file, and an operator can read it and check.
 *
 * ### Where progression may **not** come from
 *
 * Not the Pokédex. [com.cobblemonroguelite.battle.RunDexGuard] vetoes dex writes made inside a run,
 * so nothing an in-run catch does is visible there — and the veto is load-bearing for §2.15, not
 * incidental. Not the player's party or PC either: [com.cobblemonroguelite.battle.RunCapture]
 * reclaims a run catch straight back out of both, and reading them would be reading the *server's*
 * collection, which §2.17 explicitly stopped sourcing the floor from. This file is the only witness.
 *
 * ### Dirty marking and flushing
 *
 * [PlayerProgression] is handed out by reference, so a caller that mutates it directly cannot mark
 * this store dirty — same contract as [com.cobblemonroguelite.run.RunStore], and the reason the
 * earning and spending entry points here are methods on the *store* rather than on the record.
 * They mark dirty and flush, so candy is on disk at the moment it is earned.
 */
class ProgressionStore private constructor() : SavedData() {

    private val players = ConcurrentHashMap<UUID, PlayerProgression>()

    /**
     * [player]'s record. Created on demand — reading a floor for a player who has never played is a
     * normal thing to do (the starter offer does it for every species on screen).
     *
     * The empty record it creates costs nothing on disk: [save] skips players with no species, so a
     * read cannot grow the file the way a naive `computeIfAbsent` plus unconditional write would.
     */
    fun of(player: UUID): PlayerProgression = players.computeIfAbsent(player) { PlayerProgression() }

    /** Read-only convenience: what [player] has earned for [species], never null. */
    fun progressFor(player: UUID, species: ResourceLocation): SpeciesProgress = of(player).of(species)

    /**
     * §2.17's floor for [player] and [species] — [IvFloor.BASE] until a run catch raises it. This is
     * the read the starter side makes; see [RunProgression] for the seam it plugs into.
     */
    fun ivFloor(player: UUID, species: ResourceLocation): IvFloor = progressFor(player, species).floor

    /**
     * Credit a catch made **inside a run**: candy for it, and its IVs into the floor.
     *
     * Flushed rather than left to the next autosave, and for a sharper reason than a run checkpoint
     * has. A crash rolls the run back to its last checkpoint, which the player re-fights — but candy
     * is not re-earnable, because the wild Pokémon that produced it was consumed by the catch and the
     * re-fight rolls a different wave. Losing the write means losing the catch's entire meta value
     * with nothing to show for it. `DimensionDataStorage.save()` writes every dirty entry, so when
     * this coincides with a run checkpoint (it usually does — the catch checkpoints the run too) the
     * two go out in the same write and stay in step.
     */
    fun creditCatch(
        server: MinecraftServer,
        player: UUID,
        species: ResourceLocation,
        caughtIvs: IvFloor,
        shinyVariant: Int,
        rules: CandyRules = ProgressionSettings.candy,
    ): SpeciesProgress {
        val updated = of(player).update(species) { it.creditCatch(caughtIvs, shinyVariant, rules) }
        setDirty()
        flush(server, player)
        return updated
    }

    /**
     * Credit friendship earned in battle, converting whole thresholds into candy.
     *
     * **Not flushed unless it actually paid.** This is called for every party member on every cleared
     * wave, which is two hundred waves times six Pokémon in a full run; flushing each one would put a
     * file write on the between-wave path for a counter that ticks by one. A crash costs at most a
     * wave of friendship, which is the same thing the run itself loses. The moment it crosses into
     * candy it is worth the write, and that is rare enough to be free.
     */
    fun creditFriendship(
        server: MinecraftServer,
        player: UUID,
        species: ResourceLocation,
        gained: Int,
        starterCost: Int = SpeciesProgress.UNKNOWN_STARTER_COST,
        rules: CandyRules = ProgressionSettings.candy,
    ): SpeciesProgress {
        val record = of(player)
        val before = record.of(species).candy
        val updated = record.update(species) { it.creditFriendship(gained, rules, starterCost) }
        setDirty()
        if (updated.candy != before) flush(server, player)
        return updated
    }

    /**
     * Spend candy. Always flushed: a spend that is lost hands the player their candy back *and* the
     * thing they bought if the purchase had any effect outside this file (an egg, most obviously),
     * which is the one direction of loss that duplicates value rather than costing it.
     */
    fun buy(
        server: MinecraftServer,
        player: UUID,
        species: ResourceLocation,
        purchase: CandyPurchase,
        starterCost: Int = SpeciesProgress.UNKNOWN_STARTER_COST,
        prices: CandyPrices = ProgressionSettings.prices,
    ): SpendResult {
        val result = of(player).buy(species, purchase, starterCost, prices)
        if (result is SpendResult.Ok) {
            setDirty()
            flush(server, player)
        }
        return result
    }

    /** Snapshot of everyone's progression, for op tooling. */
    fun allPlayers(): Map<UUID, PlayerProgression> = players.toMap()

    /**
     * Identical to [com.cobblemonroguelite.run.RunStore.flush] and for the identical reason: an
     * inline `save()` from an off-thread caller can race the world autosave already walking the same
     * `DimensionDataStorage`, and the loser is a truncated file that reads back as "you have earned
     * nothing". `execute` runs inline when we are already on the server thread, so the common path
     * pays nothing.
     */
    private fun flush(server: MinecraftServer, player: UUID) {
        server.execute {
            runCatching { server.overworld().dataStorage.save() }
                .onFailure { log.warn("roguelite: progression flush failed for {}", player, it) }
        }
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val all = CompoundTag()
        players.forEach { (uuid, progression) ->
            // Players with nothing earned are skipped, not written empty — see [of] on why a bare
            // read must not be able to grow this file.
            if (progression.isEmpty()) return@forEach
            runCatching { all.put(uuid.toString(), progression.toNbt()) }
                .onFailure { log.error("roguelite: failed to serialize progression for {}", uuid, it) }
        }
        tag.put(PLAYERS_KEY, all)
        tag.putInt(SCHEMA_KEY, SCHEMA_VERSION)
        return tag
    }

    companion object {

        /** File name under `<world>/data/`. Separate from the run file — see the class docs. */
        const val DATA_NAME = "cobblemon_roguelite_progression"

        /**
         * Bump when the *meaning* of anything [save] writes changes. Nothing reads it yet and that is
         * fine: it costs one int now and is the difference between a future migration being possible
         * and being guesswork, on a file that is never deleted and will outlive every other one here.
         *
         * **2 (§2.27):** the per-species `passive` flag became `hiddenAbility`. Deliberately **no
         * migration** — the flag is not read under its old key, so a version-1 file loads with every
         * unlock reset to unbought. That is only acceptable because nothing is live: the mode has
         * never run on a server, so there is no player whose candy this costs. If that ever stops
         * being true, this is the version number a migration keys off, which is the whole reason it
         * was here before anything read it.
         */
        const val SCHEMA_VERSION = 2

        private const val PLAYERS_KEY = "players"
        private const val SCHEMA_KEY = "schema"

        /**
         * The store for this server, created on first use. Cached by
         * `DimensionDataStorage.computeIfAbsent`, so callers may treat this as a cheap accessor.
         */
        fun of(server: MinecraftServer): ProgressionStore {
            val factory = SavedData.Factory({ ProgressionStore() }, { tag, _ -> load(tag) })
            return server.overworld().dataStorage.computeIfAbsent(factory, DATA_NAME)
        }

        private fun load(tag: CompoundTag): ProgressionStore {
            val store = ProgressionStore()
            val all = tag.getCompound(PLAYERS_KEY)
            for (key in all.allKeys) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull()
                if (uuid == null) {
                    log.warn("roguelite: skipping progression under non-UUID key '{}'", key)
                    continue
                }
                store.players[uuid] = PlayerProgression.fromNbt(all.getCompound(key))
            }
            log.info("roguelite: loaded progression for {} player(s)", store.players.size)
            return store
        }
    }
}
