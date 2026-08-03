package com.cobblemonroguelite.data.starter

import net.minecraft.resources.ResourceLocation

/**
 * One file's worth of hand-assigned unlock abilities: species id to ability name (§2.27).
 *
 * ### What this overrides, and why it has to exist
 *
 * Candy buys a species' **hidden ability** on a starter. Hidden abilities are official and balanced,
 * which is what makes them the right power level (§2.27) — and they are also wildly uneven, because
 * they were never balanced against each other. Speed Boost is transformative and Truant is a
 * punishment. PokéRogue sidesteps that by hand-assigning every passive; this table is how a server
 * does the same thing without needing a second ability slot.
 *
 * An entry here replaces the hidden ability *entirely* for that species. It may name an ability the
 * species does not otherwise have — that is the point — and it may equally restate the species' own
 * hidden ability, which is the natural way to write "this one is fine as it is" down.
 *
 * @property abilities species to ability name, already validated as non-blank. Absent species use
 *   their hidden ability, which is the default and will be the answer for nearly every one.
 */
data class HiddenAbilityTable(val id: ResourceLocation, val abilities: Map<ResourceLocation, String>)
