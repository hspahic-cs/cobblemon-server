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
 * @property trainerRoster which roster this run's trainer and boss waves are drawn from, pinned at
 *   run start for [payoutTable]'s reason and resolved per wave for its caveat — see [RunRosters].
 *   Null means no id was pinned, which after the v5 schema bump can only be a damaged or hand-edited
 *   checkpoint; it is kept as a state rather than made a load failure because it resolves as
 *   [RunRoster.Missing] and stops the run loudly, where discarding the checkpoint would cost the
 *   player their party over one absent string.
 * @property trainerMemory the recent-opponent window §2.19 asks for, so twenty trainer waves against
 *   a small pool do not read as five. Persisted rather than recomputed because it is an input to
 *   selection: a run that resumed with an empty memory would draw *different* trainers from the run
 *   it was a moment ago, which is the resume guarantee inverted. See [RunTrainerMemory].
 * @property arenaSlot the run's arena, as a grid index. **An index and not coordinates**, because the
 *   coordinates are derived from it ([com.cobblemonroguelite.arena.ArenaLayout]) and a second copy of
 *   a derived fact is a second thing that can be wrong. It is also what makes this store the
 *   allocator's whole source of truth: a slot is occupied exactly when some active run says so, so a
 *   crash cannot leave a slot leased to nobody. Null means no arena has been assigned yet — which is
 *   a real state, since a run exists from the moment a starter is chosen and the assignment can fail.
 * @property entry where the player came from, restored on run end and on an ejection. See
 *   [RunEntryPoint].
 * @property stampedTemplate which arena build is currently standing in [arenaSlot]. Persisted so a
 *   restart mid-run does not re-stamp an arena that is already correct, and so §2.19's band
 *   transition can be detected by comparison rather than by remembering which band we were in — the
 *   template id is the thing that actually has to change, and reading the band boundary twice is how
 *   a re-tuned band list silently stops re-stamping.
 * @property biome §2.24: which biome this run is in, and the wave band it entered it for. See
 *   [BiomeVisit] for why the band travels with it and [BiomeRotation] for why this is stored at all
 *   rather than derived from [wave] — in short, because §2.24 leaves the door open to the player
 *   *choosing* the next biome, and a derived one closes it behind a schema change.
 * @property paintedBiome the Minecraft biome currently painted into [arenaSlot], or null when the
 *   slot has not been repainted for this run. [stampedTemplate]'s field one row over, for exactly its
 *   reason: it is a fact about what is in the world, not about what should be, and the two come apart
 *   the moment a slot is handed to a different run. Kept separate from [biome] because a repaint can
 *   fail while a stamp succeeds — writing one field would then either re-stamp an arena that is fine
 *   or stop retrying a repaint that never happened.
 * @property startedUnderOverride §2.25: true when an operator's badge-gate override was in force for
 *   this player at the moment the run was created. Persisted for the one reason the override exists to
 *   be honest about — a run that reached wave 180 without the badges for it has to be tellable from
 *   one that earned them, and the only moment that fact is knowable is run start. Never written
 *   anywhere else, so it cannot be granted retroactively by turning the override on mid-run.
 * @property battle §2.10's battle-in-progress marker, or null between waves. Set when a wave battle
 *   begins and cleared when it resolves, by [RunController] — see [RunBattleMarker].
 *
 *   Persisted, and note that the attribution never actually reads the persisted copy: a marker that
 *   survived to disk and back has by definition crossed a restart, which is the branch that costs the
 *   player nothing. It is written because a checkpoint that omitted it would describe a run as being
 *   between waves when it is not, and every later reader of the file — an operator, a repair, a
 *   future policy — would believe that. The live comparison works off the in-memory run, which
 *   [RunStore] holds for the whole server lifetime and therefore across a player's disconnect.
 * @property pendingCatch §2.13's seventh catch, waiting on a swap-or-release decision. It is a run
 *   Pokémon that is **not** in [party] and is not in any Cobblemon store either, which makes this
 *   field the only reference to it in the world — hence persisted, and hence the run refuses to
 *   advance while it is set ([RunController.resume]). A run that fought on with this dangling would
 *   eventually end, and the payout path does not look here, so the Pokémon would be destroyed by a
 *   decision the player was never asked to make.
 *
 *   Every *mutation* takes the same monitor as [party], because each one is a compound operation —
 *   "is the party full, and if so hold this", "is a decision outstanding, and if so which member goes"
 *   — and two of those interleaving is how a catch gets silently overwritten by the next one. Plain
 *   reads of it do not take the monitor: they ask only whether a decision is outstanding, they all
 *   happen on the server thread, and the same is true of the [battle] marker beside it.
 */
data class RunState(
    var wave: Int = 1,
    val party: MutableList<Pokemon> = mutableListOf(),
    var credits: Int = 0,
    val seed: Long,
    var bossesCleared: Int = 0,
    val payoutTable: ResourceLocation? = null,
    val trainerRoster: ResourceLocation? = null,
    val trainerMemory: RunTrainerMemory = RunTrainerMemory(),
    var arenaSlot: Int? = null,
    var entry: RunEntryPoint? = null,
    var stampedTemplate: ResourceLocation? = null,
    var biome: BiomeVisit? = null,
    var paintedBiome: ResourceLocation? = null,
    var battle: RunBattleMarker? = null,
    var pendingCatch: Pokemon? = null,
    val startedUnderOverride: Boolean = false,
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

    /**
     * Offer a freshly caught Pokémon to the run (§2.13). See [CatchRouting] for the three answers.
     *
     * **Nothing here touches [Pokemon.level].** §2.21 puts a mid-run catch in at its own encounter
     * level, which is the curve level for the wave it was caught on and therefore already at parity
     * with the run — levelling it to match the party would hand back exactly the catch-up cost that
     * decision exists to charge. It is not healed either, for [RunBattleParty][com.cobblemonroguelite.battle.RunBattleParty]'s
     * reason: the ball landed on a Pokémon that had been fought down, and topping it up is the same
     * silent undoing of attrition a `heal()` between waves would be.
     *
     * One monitor for the whole decision. Split into "is the party full?" then "add", two captures
     * arriving together would both see room and one of them would be dropped on the floor — and a
     * dropped run catch is unrecoverable, not a retry.
     */
    fun offer(pokemon: Pokemon): CatchRouting = synchronized(party) {
        when (RunCatchRules.route(party.size, pendingCatch != null)) {
            RunCatchRules.Route.REFUSE -> CatchRouting.AlreadyDeciding(pendingCatch!!)

            RunCatchRules.Route.JOIN -> {
                party.add(pokemon)
                CatchRouting.Joined(party.size)
            }

            RunCatchRules.Route.HOLD -> {
                pendingCatch = pokemon
                CatchRouting.HeldForDecision(pokemon, party.toList())
            }
        }
    }

    /**
     * Take the held catch in without asking, when the party has room again.
     *
     * The prompt exists *because* the party was full, so a party that is no longer full has answered
     * the question by itself — asking a player to choose between six and a spare slot is a decision
     * with one sensible answer and an irrecoverable wrong one. Reachable in practice only through
     * §2.10: a disconnect in the few ticks between the capture and the wave resolving kills what was
     * on the field, and the run comes back holding a catch and five party members.
     *
     * Returns the slot it landed in, or null when there was nothing held or no room for it.
     */
    fun claimPendingCatch(): Int? = synchronized(party) {
        val held = pendingCatch
        if (!RunCatchRules.claims(party.size, held != null)) return null
        party.add(held!!)
        pendingCatch = null
        party.size
    }

    /**
     * §2.13's decision, applied. Null when nothing was held — which is the whole of the double-fire
     * guard: a second `confirm` finds the field already cleared and destroys nothing.
     */
    fun resolveCatch(decision: CatchDecision): CatchResolution? = synchronized(party) {
        val held = pendingCatch ?: return null
        return when (decision) {
            is CatchDecision.Release -> {
                pendingCatch = null
                CatchResolution.Released(held)
            }

            is CatchDecision.Swap -> {
                // 1-based, because the prompt numbers the party for the player and the command takes
                // the number they read there. Out of range is answered rather than thrown: it is a
                // typo in a command whose other branch destroys a Pokémon, so it has to fail by
                // saying no.
                val index = RunCatchRules.swapIndex(decision.slot, party.size)
                    ?: return CatchResolution.NoSuchSlot(party.size)
                val discarded = party.set(index, held)
                pendingCatch = null
                // Placed at the discarded one's index rather than appended: slot 1 is the lead the
                // next wave opens with ([RunBattleParty][com.cobblemonroguelite.battle.RunBattleParty]
                // leads on `party.first()`), so appending would quietly move the lead whenever
                // somebody swapped it out.
                CatchResolution.Swapped(decision.slot, discarded, held)
            }
        }
    }

    fun toNbt(registryAccess: RegistryAccess): CompoundTag {
        val tag = CompoundTag()
        tag.putInt(SCHEMA_KEY, SCHEMA_VERSION)
        tag.putInt("wave", wave)
        tag.putInt("credits", credits)
        tag.putLong("seed", seed)
        tag.putInt("bossesCleared", bossesCleared)
        payoutTable?.let { tag.putString("payoutTable", it.toString()) }
        trainerRoster?.let { tag.putString("trainerRoster", it.toString()) }
        // Skipped when empty, and note this is *not* the presence-is-state trick the battle marker
        // and the arena slot use: absent and empty mean the same thing here, so the only thing being
        // saved is bytes in a file that is written every wave.
        if (!trainerMemory.isEmpty()) tag.put("trainerMemory", trainerMemory.toNbt())
        // Absent rather than a sentinel for all three. `-1` would read back as a slot index in every
        // arithmetic that touches it, and there is no coordinate that means "nowhere".
        arenaSlot?.let { tag.putInt("arenaSlot", it) }
        entry?.let { tag.put("entry", it.toNbt()) }
        stampedTemplate?.let { tag.putString("stampedTemplate", it.toString()) }
        biome?.let { tag.put("biome", it.toNbt()) }
        paintedBiome?.let { tag.putString("paintedBiome", it.toString()) }
        // Written on every run and not only on the ones it is true for, unlike everything above it.
        // This is the audit flag §2.25 asks for, and a file where "honest" and "written by a build
        // that did not have the flag yet" look identical is a file that cannot answer the question it
        // exists for — even though the schema version already answers it, one indirection away.
        tag.putBoolean("startedUnderOverride", startedUnderOverride)
        battle?.let { tag.put("battle", it.toNbt()) }
        val list = ListTag()
        partySnapshot().forEach { list.add(it.saveToNBT(registryAccess)) }
        tag.put("party", list)
        // Written even though the run cannot advance while it is set, and *because* of that: this
        // tag is the only reference to a Pokémon that is in no store at all, so a checkpoint that
        // skipped it would destroy a catch on the next restart — and the run would come back
        // resumable, with nothing anywhere to say a decision had been pending.
        synchronized(party) { pendingCatch }?.let { held ->
            runCatching { tag.put(PENDING_CATCH_KEY, held.saveToNBT(registryAccess)) }
                // The one failure that would otherwise be invisible in both directions: the catch is
                // gone and the run comes back resumable, so nothing downstream ever notices a
                // decision was owed. Caught rather than thrown for [RunStore.save]'s reason — one
                // unserializable Pokémon must not take the whole checkpoint with it.
                .onFailure { log.error("roguelite: could not serialize a held catch — it will be lost", it) }
        }
        return tag
    }

    companion object {
        /**
         * Bump whenever the shape or the *meaning* of anything [toNbt] writes changes — a renamed
         * key, a changed unit, a field that starts counting from something else. Without this a
         * format change reads old saves as if they were new ones and silently resumes runs with
         * wrong values, which is the one failure mode a checkpoint must never have.
         */
        const val SCHEMA_VERSION = 7

        private const val SCHEMA_KEY = "schemaVersion"

        private const val PENDING_CATCH_KEY = "pendingCatch"

        /**
         * §2.13: the run party holds six and there is no run PC.
         *
         * Not configurable, and it is not a tuning knob dressed up as a constant. Cobblemon's
         * `PartyStore` is six slots, and [RunBattleParty][com.cobblemonroguelite.battle.RunBattleParty]
         * builds the wave's battle team by pouring the run party into one — so a seventh member would
         * not be a bigger party, it would be a wave refused for a party that "would not fit a battle
         * store". The number is inherited, not chosen.
         */
        const val MAX_PARTY = 6

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
                // Same restore-as-null rule as the payout table, and a stronger case for it: an
                // unreadable roster id resolves as "not loaded", which stops the run with the party
                // intact and is repairable by an operator. Failing the load instead would delete the
                // party outright, over a field the player has never seen.
                trainerRoster = tag.getString("trainerRoster").takeIf { it.isNotEmpty() }
                    ?.let { ResourceLocation.tryParse(it) },
                trainerMemory = RunTrainerMemory.fromNbt(tag.getList("trainerMemory", 10 /* TAG_COMPOUND */)),
                // A run restored without its slot is not broken, it is unassigned: the next entry
                // allocates one and stamps it. Restoring a *wrong* slot would be the bad outcome, so
                // "absent" has to stay distinguishable from "zero", which is a valid index.
                arenaSlot = if (tag.contains("arenaSlot")) tag.getInt("arenaSlot") else null,
                entry = if (tag.contains("entry")) RunEntryPoint.fromNbt(tag.getCompound("entry")) else null,
                // Deliberately dropped if unparseable rather than defaulted. A wrong value here says
                // "the arena already has the right build in it" and would skip the stamp that puts a
                // floor under the player.
                stampedTemplate = tag.getString("stampedTemplate").takeIf { it.isNotEmpty() }
                    ?.let { ResourceLocation.tryParse(it) },
                // Both restore as null on damage, which the arena layer reads as "nothing is standing
                // and nothing is painted" and repairs on the next prepare. That is the safe direction
                // here in a way it is not for [stampedTemplate] above: a wrong value there skips the
                // stamp that puts a floor under the player, whereas a missing value here costs one
                // extra repaint of an arena nobody is looking at yet.
                biome = if (tag.contains("biome")) BiomeVisit.fromNbt(tag.getCompound("biome")) else null,
                paintedBiome = tag.getString("paintedBiome").takeIf { it.isNotEmpty() }
                    ?.let { ResourceLocation.tryParse(it) },
                startedUnderOverride = tag.getBoolean("startedUnderOverride"),
                // Absent or unreadable both restore as "no battle", which costs the player nothing.
                // See [RunBattleMarker.fromNbt] for why that is the only safe failure direction.
                battle = if (tag.contains("battle")) RunBattleMarker.fromNbt(tag.getCompound("battle")) else null,
                // Dropped rather than fatal, on the same rule the party members follow: a catch that
                // will not load costs the player one Pokémon they had not yet decided to keep, where
                // failing the load would cost them the six they had. Logged at WARN because unlike a
                // party member this one leaves no gap behind — the run simply becomes resumable, and
                // nothing else would ever mention that a decision had been outstanding.
                pendingCatch = if (tag.contains(PENDING_CATCH_KEY)) {
                    runCatching { Pokemon.loadFromNBT(registryAccess, tag.getCompound(PENDING_CATCH_KEY)) }
                        .onFailure { log.warn("roguelite: dropping an unreadable held catch — the run resumes without it", it) }
                        .getOrNull()
                } else {
                    null
                },
            )
        }
    }
}
