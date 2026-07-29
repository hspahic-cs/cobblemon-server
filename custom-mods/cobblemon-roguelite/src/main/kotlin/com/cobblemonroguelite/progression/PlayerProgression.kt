package com.cobblemonroguelite.progression

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("cobblemon_roguelite/progression")

/**
 * One player's progression, species by species.
 *
 * Deliberately a plain class and not part of [ProgressionStore]: everything that decides anything
 * lives here, and nothing here needs a `MinecraftServer`, a world or a booted game. That is what
 * makes §2.15's and §2.17's rules testable at all — see the note in the task that produced this file
 * and [com.cobblemonroguelite.starter.StarterFactory] for the same split (choosing is pure, building
 * needs registries). [ProgressionStore] adds exactly two things on top: dirty marking and the file.
 *
 * ### Concurrency
 *
 * A [ConcurrentHashMap] of immutable [SpeciesProgress] values, mutated only through
 * [ConcurrentHashMap.compute]. Every update is therefore atomic for that species and non-blocking
 * for every other, which is the shape the write sources need: catches land on the server thread and
 * friendship lands from battle resolution, which Cobblemon dispatches off-thread. There is no lock
 * for a caller to take, and no read-modify-write for a caller to get wrong — [update] is the only
 * mutator and it takes the function rather than the value.
 */
class PlayerProgression {

    private val species = ConcurrentHashMap<ResourceLocation, SpeciesProgress>()

    /**
     * This player's record for [id], or [SpeciesProgress.EMPTY] if they have never touched it.
     *
     * Never null, deliberately: "no row" and "an empty row" must be indistinguishable to every reader,
     * or the IV floor a brand-new player gets depends on whether some unrelated code happened to have
     * created a row for them. [SpeciesProgress.EMPTY] already carries [IvFloor.BASE], so the base-10
     * rule in §2.17 falls out of the default rather than being applied anywhere.
     */
    fun of(id: ResourceLocation): SpeciesProgress = species[id] ?: SpeciesProgress.EMPTY

    /**
     * Apply [change] to [id]'s record atomically and return the result.
     *
     * The record is removed rather than stored when [change] produces an empty one, so a no-op update
     * cannot grow the file — otherwise reading a floor for a species (which callers do on every
     * starter offer) would eventually persist a row for every species in the game.
     */
    fun update(id: ResourceLocation, change: (SpeciesProgress) -> SpeciesProgress): SpeciesProgress {
        val updated = species.compute(id) { _, current ->
            change(current ?: SpeciesProgress.EMPTY).takeUnless { it.isEmpty() }
        }
        return updated ?: SpeciesProgress.EMPTY
    }

    /**
     * Spend candy on [id], storing the result only if the purchase succeeded.
     *
     * Here rather than at the call site because the check and the deduction have to be one atomic
     * step. Doing it as `of()` then `update()` is a textbook double-spend: two purchases racing on
     * the same species both read the same balance, both pass the affordability check, and both
     * deduct — leaving the player with one balance's worth of candy and two things bought. Rare, but
     * a shop with two open screens or a double-clicked button is exactly the case that produces it.
     */
    fun buy(
        id: ResourceLocation,
        purchase: CandyPurchase,
        starterCost: Int = SpeciesProgress.UNKNOWN_STARTER_COST,
        prices: CandyPrices = ProgressionSettings.prices,
    ): SpendResult {
        var outcome: SpendResult = SpendResult.NotPriced
        species.compute(id) { _, current ->
            val before = current ?: SpeciesProgress.EMPTY
            val result = before.buy(purchase, starterCost, prices)
            outcome = result
            // On a refusal the record goes back exactly as it was — including staying absent, which
            // is what `takeUnless` gives us for a player who cannot afford their first purchase.
            val after = if (result is SpendResult.Ok) result.progress else before
            after.takeUnless { it.isEmpty() }
        }
        return outcome
    }

    /** Every species this player has a record for. A snapshot; safe to iterate. */
    fun all(): Map<ResourceLocation, SpeciesProgress> = species.toMap()

    fun isEmpty(): Boolean = species.isEmpty()

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        species.forEach { (id, progress) ->
            if (!progress.isEmpty()) tag.put(id.toString(), progress.toNbt())
        }
        return tag
    }

    companion object {

        /**
         * Read a player's progression back. Unreadable species keys are skipped with a warning rather
         * than failing the load: one bad key is one species' candy, whereas throwing would cost the
         * player every species they have — and unlike a run (which can be restarted) there is nothing
         * they can do to earn a lost floor back except catch it all again.
         */
        fun fromNbt(tag: CompoundTag): PlayerProgression {
            val progression = PlayerProgression()
            for (key in tag.allKeys) {
                val id = ResourceLocation.tryParse(key)
                if (id == null) {
                    log.warn("roguelite: skipping progression under non-species key '{}'", key)
                    continue
                }
                val progress = runCatching { SpeciesProgress.fromNbt(tag.getCompound(key)) }
                    .onFailure { log.warn("roguelite: progression for '{}' failed to load — skipping", key, it) }
                    .getOrNull() ?: continue
                if (!progress.isEmpty()) progression.species[id] = progress
            }
            return progression
        }
    }
}
