package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation

/**
 * The trainers this run has met lately, so wave 50 does not draw wave 45's.
 *
 * ### Why this is run state and could not have lived in the roster
 *
 * [com.cobblemonroguelite.data.trainer.TrainerRoster] draws from `(seed, wave)` and deliberately
 * nothing else — that is what makes a resumed run meet the same opponent it would have met before the
 * disconnect (§2.3), and its own docs name this omission. "Not the one I just fought" is not a
 * function of a seed and a wave; it is a function of what *this run* has already done, so the only
 * place it can live is beside the run and inside the checkpoint. A roster-side version would have to
 * reconstruct the history on every draw, and would then be reconstructing it from a different
 * starting point after a restore — which is the one thing the seed exists to rule out.
 *
 * ### Why the window is [WINDOW] and not the whole run
 *
 * §2.19 puts twenty trainer waves and twenty boss waves in a run. Bands are kind-scoped, so a boss
 * entry can never match a trainer pool and costs nothing to carry alongside one; eight entries is
 * therefore roughly the last four trainer waves *and* the last four boss waves under the shipping
 * 5/10 schedule — about forty waves of play, which for a mode that spans days (§2.19) is more than a
 * sitting. A repeat further apart than that is not a repeat anyone notices, and remembering all forty
 * non-wild waves would grow the checkpoint to buy it.
 *
 * The upper bound is the one that actually constrains the number: **the window has to stay under a
 * realistic pool size.** Exclude more trainers than a band holds and every candidate is excluded, at
 * which point the memory can only stop working. [RunTrainerSelection] shortens the window until
 * something survives rather than giving up, so a three-trainer band still gets "never twice running"
 * — but eight is picked to sit below the pool sizes §2.19 asks authors for, so that shortening stays
 * a safety net instead of the normal path.
 *
 * ### Recording is keyed by wave, which is what keeps a run replayable
 *
 * [record] replaces the entry for a wave instead of appending a second one, and [before] hands out
 * only entries from *earlier* waves. Together those mean re-planning a wave cannot change what that
 * wave draws — and waves get re-planned constantly: every `/roguelite status`, and every time §2.10
 * hands an interrupted wave back to be fought again, which is explicitly promised to be the same
 * fight. Appending per look would make the answer depend on how many times the player asked.
 *
 * Mutated only on the server thread, by [RunController] at wave boundaries. Unlike [RunState.party]
 * nothing in a battle touches it, so it needs no lock.
 */
class RunTrainerMemory(initial: List<Entry> = emptyList()) {

    /**
     * One wave and who it fought. The wave number is carried rather than implied by position because
     * it is what makes [record] idempotent and [before] exact; a bare list of ids could do neither.
     */
    data class Entry(val wave: Int, val trainerId: ResourceLocation)

    /**
     * Held one longer than [WINDOW] on purpose. [record] for the current wave evicts the oldest
     * entry, and if capacity were exactly [WINDOW] that eviction would shrink the set [before] sees
     * for that same wave — so asking twice, once either side of the record, could answer differently.
     * The spare slot means the entries preceding the newest one are still a full window afterwards.
     */
    private val recorded = ArrayList<Entry>(WINDOW + 1)

    init {
        initial.forEach { record(it.wave, it.trainerId) }
    }

    /** Oldest first — the order a checkpoint file and anyone hand-reading one both want. */
    fun entries(): List<Entry> = recorded.toList()

    fun isEmpty(): Boolean = recorded.isEmpty()

    /**
     * Who this run met before [wave], most recent first, at most [WINDOW] of them.
     *
     * Filtered by wave rather than simply handed back whole because the entry for [wave] itself is
     * already present whenever a wave is looked at a second time, and letting a wave avoid *itself*
     * would make the second look disagree with the first.
     */
    fun before(wave: Int): List<ResourceLocation> =
        recorded.asReversed().asSequence().filter { it.wave < wave }.take(WINDOW).map { it.trainerId }.toList()

    fun record(wave: Int, trainerId: ResourceLocation) {
        recorded.removeIf { it.wave == wave }
        recorded.add(Entry(wave, trainerId))
        while (recorded.size > WINDOW + 1) recorded.removeAt(0)
    }

    fun toNbt(): ListTag = ListTag().apply {
        recorded.forEach { entry ->
            add(
                CompoundTag().apply {
                    putInt("wave", entry.wave)
                    putString("trainer", entry.trainerId.toString())
                },
            )
        }
    }

    /** Compared by contents, so a checkpoint round-trip is assertable in one line. */
    override fun equals(other: Any?): Boolean = other is RunTrainerMemory && other.recorded == recorded

    override fun hashCode(): Int = recorded.hashCode()

    override fun toString(): String = recorded.toString()

    companion object {

        /** See the class docs — this number is bounded from both ends and neither bound is arbitrary. */
        const val WINDOW = 8

        /**
         * Never null, and unreadable entries are dropped rather than failing the load.
         *
         * The failure direction matters and it is the opposite of [RunBattleMarker]'s. Losing an entry
         * costs one avoided repeat, which is a cosmetic loss the player would have to be counting to
         * notice; discarding the run over an unparseable id would cost them the whole run, and this is
         * the least important field in the file to lose it over.
         */
        fun fromNbt(list: ListTag): RunTrainerMemory {
            val entries = (0 until list.size).mapNotNull { index ->
                val tag = list.getCompound(index)
                val wave = tag.getInt("wave").takeIf { it >= 1 } ?: return@mapNotNull null
                val trainer = ResourceLocation.tryParse(tag.getString("trainer")) ?: return@mapNotNull null
                Entry(wave, trainer)
            }
            return RunTrainerMemory(entries)
        }
    }
}
