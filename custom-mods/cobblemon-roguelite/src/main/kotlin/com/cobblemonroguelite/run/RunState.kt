package com.cobblemonroguelite.run

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonroguelite.arena.ArenaBuild
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
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
 * @property credits run-scoped currency, earned and spent inside the run and **discarded when it
 *   ends** (plan §2.35). It is deliberately *not* converted to anything: §2.20 makes the payout
 *   non-currency so the entry fee stays a real sink, and converting a leftover balance would
 *   quietly reopen that. An earlier version of this comment said "converted to
 *   server currency at run end" — that predates §2.20 and was wrong. Not to
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
 *   allocator's whole source of truth: a slot is occupied exactly when some active run says so. Null
 *   means the run holds no arena, which since §2.23 is the ordinary resting state — a run that has
 *   just been created, and *every* run whose player is offline.
 *
 *   **Deliberately not persisted**, and this is the load-bearing half of §2.23's lease. A slot is held
 *   for a session and given back at logout ([com.cobblemonroguelite.arena.RunArenas.release]); a crash
 *   skips that call, so if the field reached disk it would come back leased to a player who is not
 *   connected, and would stay leased until they returned — which for an abandoned run is never. Not
 *   writing it makes "no lease outlives the process" structural rather than something a shutdown path
 *   has to remember to do. It also removes the worse failure the persisted version had: a restored
 *   slot that somebody else has since been given, which teleports two players into one arena.
 * @property entry where the player came from, restored on run end and on an ejection. Persisted,
 *   unlike the three arena fields around it, because it is a fact about the *world outside* the run and
 *   is the only way home. See [RunEntryPoint].
 * @property stampedBuild which arena is currently standing in [arenaSlot] — §2.29's generated palette
 *   or a hand-built template, tagged so the two can never be confused for each other
 *   ([com.cobblemonroguelite.arena.ArenaBuild]). Not persisted, for
 *   [arenaSlot]'s reason and with an extra edge: it is the value §2.19's band transition is detected by
 *   comparison against, so a copy that survived into a session holding a *different* slot would report
 *   "already correct" about a box the run has never been in and skip the stamp that puts a floor under
 *   the player. Within a session it still does its job — a band transition re-stamps and a wave that
 *   stays in its band does not, which is what keeps a 130k-block write off every battle.
 * @property biome §2.24: which biome this run is in, and the wave band it entered it for. See
 *   [BiomeVisit] for why the band travels with it and [BiomeRotation] for why this is stored at all
 *   rather than derived from [wave] — in short, because §2.24 leaves the door open to the player
 *   *choosing* the next biome, and a derived one closes it behind a schema change.
 * @property paintedBiome the Minecraft biome currently painted into [arenaSlot], or null when the
 *   slot has not been repainted for this run. [stampedBuild]'s field one row over, for exactly its
 *   reason and with the same answer on persistence: it is a fact about what is in the world, not about
 *   what should be, and the two come apart the moment a slot is handed to a different run. Kept
 *   separate from [biome] because a repaint can fail while a stamp succeeds — writing one field would
 *   then either re-stamp an arena that is fine or stop retrying a repaint that never happened.
 * @property lastActiveAtEpochMs when this run was last *played* — §2.23's activity clock, and the only
 *   input to expiry besides [wave]. Stamped by [touch] when a wave begins and when one is cleared, and
 *   nowhere else: §2.23 is explicit that logging in is not activity, so a player who connects daily and
 *   never touches their run is not keeping it alive. Set to the creation time for a run that has not
 *   fought yet, which makes an abandoned starter offer's run age from the moment it existed rather than
 *   from epoch zero.
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

    /**
     * §2.11's run bag: every ItemStack the run has granted and the player is not currently holding
     * live — captured at each arena exit (isolation design X1) and reinstalled at the next entry.
     * Marked stacks only ([com.cobblemonroguelite.run.RunItems]); dies with the run like [credits]
     * (§2.35). Server thread only, like everything else the wave step touches.
     */
    val runBag: MutableList<ItemStack> = mutableListOf(),

    /**
     * Stat stages carried between wild waves (PokéRogue's rule, user decision 2026-07-31), keyed by
     * Pokémon UUID, values showdownId→stage. Written by
     * [com.cobblemonroguelite.battle.RunCarriedBoosts] at wild-wave victory, cleared by any
     * non-wild wave, currently READ by nothing that changes a battle — injection is a researched
     * follow-up and the capture half persists so a mid-implementation restart loses nothing.
     */
    val carriedBoosts: MutableMap<java.util.UUID, Map<String, Int>> = mutableMapOf(),

    /**
     * §2.43 (end): the run's team-wide permanent buffs — [RunPassive.id] → stack count. Written by
     * the reward path ([com.cobblemonroguelite.shop.RewardGrant]) under each kind's [RunPassive.maxStacks]
     * cap, read by [com.cobblemonroguelite.battle.RunExpPassives] on every battle EXP gain. Dies with
     * the run like [credits] (§2.35). Server thread only, like the wave step that grants into it.
     *
     * Keyed by the wire id rather than the enum so an id this build does not know survives a
     * round-trip instead of being silently deleted — it is read by nothing, which is also why keeping
     * it is safe.
     */
    val passiveStacks: MutableMap<String, Int> = mutableMapOf(),
    /**
     * How many times the free reward offer has been rerolled **on the current wave**, and whether the
     * one free option has already been taken.
     *
     * Both are per-wave and both are reset by [advanceTo], which is the only place a wave changes.
     * Persisted, because §2.16 promises a paused run resumes to the state it left: without the reroll
     * count the offer would recompute to the *first* three items and a relog would silently undo a
     * paid reroll, and without the taken flag a relog would hand out the free reward again.
     *
     * They are two fields rather than one nullable "state of this wave's step" because they answer
     * independent questions and a caller almost always wants exactly one of them.
     */
    var rerollsThisWave: Int = 0,
    var rewardTakenThisWave: Boolean = false,
    val seed: Long,
    var bossesCleared: Int = 0,
    val payoutTable: ResourceLocation? = null,
    val trainerRoster: ResourceLocation? = null,
    val trainerMemory: RunTrainerMemory = RunTrainerMemory(),
    var arenaSlot: Int? = null,
    var entry: RunEntryPoint? = null,
    var stampedBuild: ArenaBuild? = null,
    var biome: BiomeVisit? = null,
    var paintedBiome: ResourceLocation? = null,
    var battle: RunBattleMarker? = null,
    var pendingCatch: Pokemon? = null,
    val startedUnderOverride: Boolean = false,
    var lastActiveAtEpochMs: Long = System.currentTimeMillis(),
) {
    /**
     * The Pokémon that was on the field when the last wave ended, so the next wave sends it back
     * out — PokéRogue's behaviour, asked for in the first playtest: every wave opening on party
     * slot 1 makes the slot-1 Pokémon the only one that ever fights unless the player re-sorts
     * their party between waves.
     *
     * A body property, not a constructor field, and therefore **deliberately not persisted**: after
     * a relog the lead falls back to party order, which is visible, harmless, and cheaper than a
     * save-format change. Written by [RunController.waveCleared] from the §2.10 field marker just
     * before that marker is cleared; read by the battle layer when it builds the next team.
     */
    var lastLead: java.util.UUID? = null

    /**
     * A run ends when every party member is down.
     *
     * **Reversed 2026-07-31 (first playtest): a faint is a faint, not a death.** §2.13 originally
     * removed fainted Pokémon from the run outright, and the playtest surfaced what that broke: the
     * shop and reward tables sell Revives (ruled in), and a Revive with no fainted Pokémon to
     * target is a purchase that can never do anything — the two rules contradicted and the revive
     * economy is the one PokéRogue actually has. So fainted members now *stay*, at 0 HP, revivable
     * between waves, exactly as in PokéRogue; the wipe is everyone down at once. The empty-party
     * check remains for the one path that still removes Pokémon: §2.10's disconnect penalty.
     */
    fun isWiped(): Boolean = synchronized(party) {
        party.isEmpty() || party.all { pokemon -> runCatching { pokemon.isFainted() }.getOrDefault(false) }
    }

    /**
     * §2.23: mark the run as played, right now.
     *
     * Called where a wave actually moves — begun or cleared — and nowhere near the login path. The
     * distinction is the whole of the activity rule: expiry exists to reclaim runs nobody is playing,
     * and a clock that any connection resets would keep every run a regular player ever abandoned alive
     * forever while doing nothing for the storage it was written for.
     *
     * Takes the wall clock rather than a caller-supplied instant because there is nothing to be
     * consistent with — the value is only ever compared against a later reading of the same clock, and
     * the decision that reads it ([RunExpiry]) takes `now` as a parameter so that it stays testable.
     */
    fun touch() {
        lastActiveAtEpochMs = System.currentTimeMillis()
    }

    /**
     * Move the run to [wave], resetting everything that is scoped to a single wave.
     *
     * **The only way a wave should change.** Assigning `run.wave` directly is what this replaces, and
     * the reason it is a method is that the assignment alone is a bug: [rerollsThisWave] and
     * [rewardTakenThisWave] describe the between-wave step of the wave being *left*, so a run that
     * advanced without clearing them would arrive at the next wave with its free reward already spent
     * and its rerolls already priced up. Both failures are silent — the player simply finds the step
     * missing — which is exactly the kind that should be impossible to write rather than remembered.
     */
    fun advanceTo(wave: Int) {
        this.wave = wave
        rerollsThisWave = 0
        rewardTakenThisWave = false
    }

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
        // Written unconditionally rather than skipped when zero. Absence would be indistinguishable
        // from "not rerolled", which is the same value — but a future non-zero default would then read
        // back wrong from every existing save, and this file is written every wave anyway.
        tag.putInt("rerollsThisWave", rerollsThisWave)
        tag.putBoolean("rewardTakenThisWave", rewardTakenThisWave)
        tag.putLong("seed", seed)
        // Absent and empty mean the same thing (the trainerMemory argument), so no schema bump: an
        // old save reads back as an empty bag, which is also what it was.
        if (runBag.isNotEmpty()) {
            val bag = ListTag()
            runBag.forEach { stack -> bag.add(stack.save(registryAccess)) }
            tag.put("runBag", bag)
        }
        if (carriedBoosts.isNotEmpty()) {
            val boosts = CompoundTag()
            carriedBoosts.forEach { (uuid, stages) ->
                val entry = CompoundTag()
                stages.forEach { (stat, stage) -> entry.putInt(stat, stage) }
                boosts.put(uuid.toString(), entry)
            }
            tag.put("carriedBoosts", boosts)
        }
        // Absent and empty mean the same thing (no passives), so no schema bump — the same rule the
        // run bag and carried boosts follow, and the reason a run saved before passives existed
        // still loads.
        if (passiveStacks.isNotEmpty()) {
            val passives = CompoundTag()
            passiveStacks.forEach { (id, count) -> passives.putInt(id, count) }
            tag.put(PASSIVES_KEY, passives)
        }
        tag.putInt("bossesCleared", bossesCleared)
        payoutTable?.let { tag.putString("payoutTable", it.toString()) }
        trainerRoster?.let { tag.putString("trainerRoster", it.toString()) }
        // Skipped when empty, and note this is *not* the presence-is-state trick the battle marker
        // and the arena slot use: absent and empty mean the same thing here, so the only thing being
        // saved is bytes in a file that is written every wave.
        if (!trainerMemory.isEmpty()) tag.put("trainerMemory", trainerMemory.toNbt())
        // §2.23: arenaSlot, stampedBuild and paintedBiome are **not** written, and their absence is
        // the mechanism rather than an omission. All three describe a lease that lasts one session, so
        // a copy on disk would be a claim about an arena this run does not hold the moment the process
        // that made it goes away — see the property docs. `entry` is written because it is the opposite
        // kind of fact: where the player was standing in the real world before any of this started.
        entry?.let { tag.put("entry", it.toNbt()) }
        biome?.let { tag.put("biome", it.toNbt()) }
        // Written unconditionally. There is no "never played" state to encode as absence — a run is
        // stamped at creation — and an absent key would read back as epoch zero, i.e. a run that
        // expired decades ago, which is the one direction this field must never fail in.
        tag.putLong(LAST_ACTIVE_KEY, lastActiveAtEpochMs)
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
        const val SCHEMA_VERSION = 8

        private const val SCHEMA_KEY = "schemaVersion"

        private const val PENDING_CATCH_KEY = "pendingCatch"

        private const val PASSIVES_KEY = "passives"

        private const val LAST_ACTIVE_KEY = "lastActiveAt"

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
                // Both default to the "step not started" values on an older save, which is the safe
                // direction: a player who had rerolled gets one reroll's worth of value back, whereas
                // defaulting `rewardTaken` to true would silently eat a reward they had not taken.
                rerollsThisWave = tag.getInt("rerollsThisWave"),
                rewardTakenThisWave = tag.getBoolean("rewardTakenThisWave"),
                seed = tag.getLong("seed"),
                carriedBoosts = tag.getCompound("carriedBoosts").let { boosts ->
                    boosts.allKeys.mapNotNull { key ->
                        val uuid = runCatching { java.util.UUID.fromString(key) }.getOrNull() ?: return@mapNotNull null
                        val entry = boosts.getCompound(key)
                        uuid to entry.allKeys.associateWith { entry.getInt(it) }
                    }.toMap().toMutableMap()
                },
                runBag = tag.getList("runBag", 10 /* TAG_COMPOUND */).mapNotNull { element ->
                    // parse() over raw errors: an unreadable bag stack (mod removed mid-run) is
                    // dropped WITH a log line — run property, so dropping is legal, and quieter than
                    // discarding the whole run over a consumable.
                    ItemStack.parse(registryAccess, element).orElse(null).also {
                        if (it == null) log.warn("roguelite: dropping unreadable run-bag stack from checkpoint")
                    }
                }.toMutableList(),
                // Absent reads as an empty compound, i.e. no passives — which is what a run saved by
                // a build without passives had. Known kinds are clamped to their cap so a hand-edited
                // count cannot exceed what the reward path could grant; unknown ids are kept as-is
                // (see the property doc), and non-positive counts are dropped as damage.
                passiveStacks = tag.getCompound(PASSIVES_KEY).let { passives ->
                    passives.allKeys.mapNotNull { id ->
                        val count = passives.getInt(id)
                        if (count < 1) return@mapNotNull null
                        id to (RunPassive.byId(id)?.let { count.coerceAtMost(it.maxStacks) } ?: count)
                    }.toMap().toMutableMap()
                },
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
                // arenaSlot, stampedBuild and paintedBiome are not read because they are not
                // written (§2.23). A restored run holds no arena and nothing is claimed to be standing
                // in one, so the first entry of the new session allocates, stamps and repaints — which
                // is the correct thing to do about a grid whose contents nobody can vouch for.
                entry = if (tag.contains("entry")) RunEntryPoint.fromNbt(tag.getCompound("entry")) else null,
                // Restores as null on damage, which the arena layer reads as "the run is nowhere in
                // particular" and settles on the next prepare. Unlike the fields above it this one is
                // *where the run is*, not what is in a box, so it is the one biome fact worth keeping
                // across a session.
                biome = if (tag.contains("biome")) BiomeVisit.fromNbt(tag.getCompound("biome")) else null,
                startedUnderOverride = tag.getBoolean("startedUnderOverride"),
                // Through [RunExpiry.restoreStamp] rather than read straight, because a missing key
                // reads as 0 — January 1970, which is expired under every band — and this whole path
                // needs a booted server to reach. See there for the rule and why it is not inline.
                lastActiveAtEpochMs = RunExpiry.restoreStamp(
                    tag.getLong(LAST_ACTIVE_KEY),
                    System.currentTimeMillis(),
                ),
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
