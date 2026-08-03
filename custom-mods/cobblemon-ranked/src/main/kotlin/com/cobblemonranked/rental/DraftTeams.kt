package com.cobblemonranked.rental

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.battle.countsAsLegendary
import com.cobblemonranked.battle.rankedBanReason
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.min

/**
 * Player-drafted custom rental teams (see docs/rental-drafts-plan.md).
 *
 * A draft stores the player's RAW spec (EVs up to 252) so `/ranked draft export` can hand back the
 * true target build; [build] applies the same de-tune as the prebuilt rentals (EV clamp
 * [RENTAL_EV_CAP], flat [RENTAL_IVS] IVs, level = config levelCap) so a draft is never stronger
 * than a built-in rental and always weaker than a hand-raised team.
 *
 * [validate] enforces the draft rules ([ShowdownPasteParser] already guaranteed per-set shape):
 * the ranked banlist and legendary cap are the only species restrictions — any legendary that
 * isn't banned is draftable (decided 2026-07-31; note this admits Paradox, which counts as the
 * team's one legendary, unlike the hand-authored prebuilt rentals).
 */
object DraftTeams {

    const val RENTAL_EV_CAP = 168
    const val RENTAL_IVS = 25
    const val TEAM_SIZE = 6

    /** Prefix distinguishing a player draft from a built-in rental id wherever ids are stored
     *  (tournament entries). The full id is `draft:<slug>` and is resolved per-player. */
    const val ID_PREFIX = "draft:"

    /** An edit that keeps at least this many of the 6 species is a "tune" (unrestricted);
     *  fewer means a team SWAP — cooldown-gated and priced at `draftSwapCost`. */
    const val TUNE_MIN_SHARED_SPECIES = 4

    data class Draft(
        val slug: String,                 // stable key, sanitised from the name
        val name: String,                 // display name as typed
        val members: List<RentalTeams.RentalMon>,  // RAW spec — de-tuned only in build()
        val createdAt: String,
        val updatedAt: String,
        /** Every team comes with one free (tune) edit; once spent, tunes cost `draftEditCost`. */
        val freeEditUsed: Boolean = false,
        /** When this team identity entered the slot (create or swap-edit; tunes don't touch it).
         *  Null in pre-cooldown records — treated as [createdAt]. */
        val identityChangedAt: String? = null,
    ) {
        fun identityChangedInstant(): Instant =
            runCatching { Instant.parse(identityChangedAt ?: createdAt) }.getOrElse { Instant.EPOCH }
    }

    /** [ownedSlots] is the number of slots the player has permanently purchased — it never
     *  decreases on delete. [slotLocks] are expiry instants left behind by deletes whose team's
     *  identity cooldown hadn't run out — each locks one empty slot until it passes (so
     *  delete + create can't dodge the swap cooldown). Normalised on read. */
    private data class PlayerDrafts(
        val drafts: List<Draft> = emptyList(),
        val ownedSlots: Int = 0,
        val slotLocks: List<String> = emptyList(),
        /** Unused "first team included" credits — each slot purchase grants one, and a create
         *  consumes one instead of charging `draftRefillCost`. */
        val freeFills: Int = 0,
    )

    class ValidationException(message: String) : Exception(message)

    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var draftsDir: Path

    fun load(configDir: Path) {
        draftsDir = configDir.resolve("cobblemon-ranked").resolve("runtime").resolve("drafts")
    }

    /**
     * One-time price to unlock the next slot for a player who already owns [ownedCount]: indexes
     * the `draftSlotCosts` ladder, and past its end keeps climbing by the ladder's final step so
     * the curve never flattens.
     */
    @JvmStatic
    fun slotCost(ownedCount: Int): Int {
        val costs = CobblemonRanked.config.draftSlotCosts
        if (costs.isEmpty()) return 0
        if (ownedCount < costs.size) return costs[ownedCount]
        val step = if (costs.size >= 2) costs[costs.size - 1] - costs[costs.size - 2] else costs[0]
        return costs.last() + step * (ownedCount - costs.size + 1)
    }

    // ---- store ------------------------------------------------------------------------------

    fun list(player: UUID): List<Draft> = read(player).drafts

    /** Slots this player has permanently unlocked (≥ current draft count by construction). */
    @JvmStatic
    fun ownedSlots(player: UUID): Int = read(player).ownedSlots

    /** Operator-configured ceiling on unlockable slots. */
    @JvmStatic
    fun maxSlots(): Int = CobblemonRanked.config.maxDraftSlots

    /**
     * Permanently unlocks one more slot and returns the new owned count. Selling (and charging
     * for) slots is the market's Upgrades vendor's job — it calls this through a reflection
     * bridge after taking payment; `/ranked admin grantdraftslot` wraps it for operators.
     * Callers enforce [maxSlots]; this just records ownership.
     */
    @JvmStatic
    fun grantSlot(player: UUID): Int {
        val state = read(player)
        val owned = state.ownedSlots + 1
        // A purchased slot includes its first team — grant a free-fill credit alongside.
        write(player, state.copy(ownedSlots = owned, freeFills = state.freeFills + 1))
        return owned
    }

    /** Unused "first team included with your slot" credits. */
    fun freeFills(player: UUID): Int = read(player).freeFills

    fun byName(player: UUID, name: String): Draft? {
        val slug = slugify(name)
        return read(player).drafts.find { it.slug == slug }
    }

    fun byId(player: UUID, id: String): Draft? =
        if (!id.startsWith(ID_PREFIX)) null
        else read(player).drafts.find { it.slug == id.removePrefix(ID_PREFIX) }

    /** Insert or replace (edit) a draft. Caller has already validated, cooldown-checked, and
     *  charged; [consumedFreeEdit] marks the team's one free tune spent, [identityChange] marks
     *  a create or swap-edit (restarts the slot's identity cooldown — tunes leave it alone). */
    fun save(
        player: UUID, name: String, members: List<RentalTeams.RentalMon>,
        consumedFreeEdit: Boolean = false, identityChange: Boolean = false,
        consumedFreeFill: Boolean = false,
    ): Draft {
        val slug = slugify(name)
        val now = Instant.now().toString()
        val state = read(player)
        val previous = state.drafts.find { it.slug == slug }
        val draft = Draft(
            slug, name, members,
            createdAt = previous?.createdAt ?: now, updatedAt = now,
            // A swap is a new team: fresh free edit, fresh identity clock.
            freeEditUsed = if (identityChange) false else (previous?.freeEditUsed ?: false) || consumedFreeEdit,
            identityChangedAt = if (identityChange || previous == null) now
                else previous.identityChangedAt ?: previous.createdAt,
        )
        val drafts = state.drafts.filterNot { it.slug == slug } + draft
        // Slots are sold by the market's Upgrades vendor, so drafts never outnumber owned slots;
        // the maxOf is a safety net that keeps the invariant if state was hand-edited.
        write(player, PlayerDrafts(
            drafts, maxOf(state.ownedSlots, drafts.size), state.slotLocks,
            freeFills = (state.freeFills - if (consumedFreeFill) 1 else 0).coerceAtLeast(0),
        ))
        return draft
    }

    fun delete(player: UUID, name: String): Boolean {
        val slug = slugify(name)
        val state = read(player)
        val victim = state.drafts.find { it.slug == slug } ?: return false
        // ownedSlots survives — the slot is bought, only its contents go. If the departing
        // team's identity cooldown is still running, the freed slot stays locked until it ends
        // (else delete + create would be a cooldown-free swap).
        val lockUntil = victim.identityChangedInstant().plus(cooldown())
        val locks = state.slotLocks + if (lockUntil.isAfter(Instant.now())) listOf(lockUntil.toString()) else emptyList()
        write(player, state.copy(drafts = state.drafts.filterNot { it.slug == slug }, slotLocks = locks))
        return true
    }

    // ---- identity cooldown ------------------------------------------------------------------

    private fun cooldown(): Duration =
        Duration.ofHours(CobblemonRanked.config.draftIdentityCooldownHours.toLong())

    /** Species-set overlap between two rosters (Species Clause makes them sets). */
    fun sharedSpecies(a: List<RentalTeams.RentalMon>, b: List<RentalTeams.RentalMon>): Int =
        a.map { it.species }.toSet().intersect(b.map { it.species }.toSet()).size

    /** When this draft may take a NEW team identity (swap-edit). In the past = allowed now. */
    fun swapAvailableAt(draft: Draft): Instant = draft.identityChangedInstant().plus(cooldown())

    private fun activeLocks(state: PlayerDrafts, now: Instant): List<Instant> =
        state.slotLocks.mapNotNull { runCatching { Instant.parse(it) }.getOrNull() }
            .filter { it.isAfter(now) }

    /** Owned slots minus filled ones minus delete-locked ones — what `create` may use. */
    fun availableEmptySlots(player: UUID): Int {
        val state = read(player)
        return (state.ownedSlots - state.drafts.size - activeLocks(state, Instant.now()).size)
            .coerceAtLeast(0)
    }

    /** Earliest instant a delete-locked slot frees up, or null if none are locked. */
    fun nextSlotUnlockAt(player: UUID): Instant? =
        activeLocks(read(player), Instant.now()).minOrNull()

    fun slugify(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(32)

    private fun read(player: UUID): PlayerDrafts {
        val file = draftsDir.resolve("$player.json")
        if (!file.exists()) return PlayerDrafts()
        return try {
            val state = GSON.fromJson(file.readText(), PlayerDrafts::class.java) ?: PlayerDrafts()
            state.copy(ownedSlots = maxOf(state.ownedSlots, state.drafts.size))
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to load drafts for $player", e)
            PlayerDrafts()
        }
    }

    private fun write(player: UUID, drafts: PlayerDrafts) {
        draftsDir.createDirectories()
        val file = draftsDir.resolve("$player.json")
        val now = Instant.now()
        val pruned = drafts.copy(slotLocks = activeLocks(drafts, now).map { it.toString() })
        // Keep the file while any slot is owned — purchased slots must survive an all-delete.
        if (pruned.drafts.isEmpty() && pruned.ownedSlots == 0) file.deleteIfExists()
        else file.writeText(GSON.toJson(pruned))
    }

    // ---- build ------------------------------------------------------------------------------

    /** De-tuned copy of a raw spec: the ONLY path from a draft to battle-ready Pokémon. */
    private fun detune(mon: RentalTeams.RentalMon): RentalTeams.RentalMon = mon.copy(
        evs = RentalTeams.RentalEVs(
            hp = min(mon.evs.hp, RENTAL_EV_CAP), atk = min(mon.evs.atk, RENTAL_EV_CAP),
            def = min(mon.evs.def, RENTAL_EV_CAP), spa = min(mon.evs.spa, RENTAL_EV_CAP),
            spd = min(mon.evs.spd, RENTAL_EV_CAP), spe = min(mon.evs.spe, RENTAL_EV_CAP),
        ),
        ivs = RENTAL_IVS,
        level = CobblemonRanked.config.levelCap,
    )

    /** Battle-ready mons, fresh instances, never a player's storage. Throws on a broken spec. */
    fun build(draft: Draft): List<Pokemon> = draft.members.map { mon ->
        val pokemon = PokemonProperties.parse(detune(mon).toProperties()).create()
        pokemon.heal()
        pokemon
    }

    /** Presents a draft through the prebuilt-rental shape so the shared picker/tournament flows
     *  can carry it. The id round-trips through [byId]. */
    fun asRentalTeam(draft: Draft): RentalTeams.RentalTeam = RentalTeams.RentalTeam(
        id = ID_PREFIX + draft.slug,
        name = draft.name,
        archetype = "Your custom draft — de-tuned like all rentals.",
        difficulty = "Custom",
        icon = "minecraft:writable_book",
        members = draft.members.map { detune(it) },
    )

    // ---- validation -------------------------------------------------------------------------

    /**
     * Enforces the rental conventions on a parsed team. Throws [ValidationException] with a
     * player-readable reason; returning normally means the draft is legal and compilable.
     */
    fun validate(members: List<RentalTeams.RentalMon>) {
        if (members.size != TEAM_SIZE)
            throw ValidationException("A draft needs exactly $TEAM_SIZE Pokémon (got ${members.size}).")

        val dupes = members.groupBy { it.species }.filterValues { it.size > 1 }.keys
        if (dupes.isNotEmpty())
            throw ValidationException("Duplicate species: ${dupes.joinToString()} (Species Clause).")

        members.forEach(::validateLegality)

        // Convention checks need live Pokémon for the label/held-item extensions.
        val built = members.map { mon ->
            try {
                PokemonProperties.parse(detune(mon).toProperties()).create()
            } catch (e: Exception) {
                throw ValidationException("${mon.species}: spec failed to compile (${e.message}).")
            }
        }

        // Legendary/Mythical/Paradox all count against the one cap; the banlist below is the
        // only other species restriction.
        val legendaries = built.filter { it.countsAsLegendary() }
        if (legendaries.size > CobblemonRanked.config.maxLegendaries)
            throw ValidationException(
                "Too many legendaries (${legendaries.joinToString { it.species.name }}) — max ${CobblemonRanked.config.maxLegendaries}.")
        built.firstNotNullOfOrNull { p -> p.rankedBanReason()?.let { "${p.species.name} ($it)" } }?.let {
            throw ValidationException("Banned in ranked PvP: $it.")
        }
        val megaStones = members.count { isMegaStone(it.item) }
        if (megaStones > 1)
            throw ValidationException("Only one Mega per team (found $megaStones Mega stones).")
    }

    /** Moves/abilities/forms must be legal for the species — `PokemonProperties` won't check, and
     *  an illegal set would make a draft stronger than any real team could be. */
    private fun validateLegality(mon: RentalTeams.RentalMon) {
        // Same lookup the parser used to mint mon.species, so the two can't disagree.
        val species = PokemonSpecies.species.find { it.resourceIdentifier.path == mon.species }
            ?: throw ValidationException("Unknown species \"${mon.species}\".")
        val form = mon.form?.let { f ->
            species.forms.find { it.name.equals(f, ignoreCase = true) }
                ?: throw ValidationException("${species.name} has no form \"$f\".")
        }

        val abilities = (form?.abilities ?: species.abilities).map { it.template.name.lowercase() }
        if (mon.ability.lowercase() !in abilities)
            throw ValidationException(
                "${species.name} can't have the ability \"${mon.ability}\" (legal: ${abilities.joinToString()}).")

        val learnset = form?.moves ?: species.moves
        val legal = learnset.getAllLegalMoves().map { it.name.lowercase() }.toSet()
        mon.moves.find { it.lowercase() !in legal }?.let {
            throw ValidationException("${species.name} can't learn \"$it\".")
        }
    }

    /** Mega Showdown's stones are `<species>ite[_x|_y]` in the `mega_showdown` namespace. The
     *  non-stone mega_showdown items (orbs, rusted weapons, bottles) don't match the suffix. */
    private fun isMegaStone(itemId: String): Boolean {
        val (ns, path) = itemId.split(":", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return false
        }
        return ns == "mega_showdown" && path.removeSuffix("_x").removeSuffix("_y").endsWith("ite")
    }

}
