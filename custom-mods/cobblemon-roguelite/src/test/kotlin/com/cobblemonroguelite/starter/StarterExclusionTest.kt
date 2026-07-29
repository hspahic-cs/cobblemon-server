package com.cobblemonroguelite.starter

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * §2.13's legendary ban.
 *
 * Worth its own tests because the obvious implementation is wrong in a way nobody notices: Cobblemon's
 * `isLegendary()` reads one label, and the species that most need banning — Arceus and every plate
 * form of it, Mew, Darkrai — do not carry it. A ban that stopped Zapdos and admitted Arceus would look
 * like it worked.
 */
class StarterExclusionTest {

    private fun id(name: String) = ResourceLocation.fromNamespaceAndPath("cobblemon", name)

    private val labels = mapOf(
        id("pidgey") to setOf("gen1"),
        id("zapdos") to setOf("legendary", "gen1"),
        id("arceus") to setOf("mythical", "gen4"),
        id("greattusk") to setOf("paradox", "gen9"),
        id("nihilego") to setOf("ultra_beast", "gen7"),
        id("buzzwole") to setOf("ultrabeast", "gen7"),
        id("shouty") to setOf("LEGENDARY"),
    )

    private val exclusion = LabelStarterExclusion({ labels[it] })

    @Test
    fun `an ordinary species is startable`() {
        assertFalse(exclusion.isExcluded(id("pidgey")))
    }

    @Test
    fun `legendaries, mythicals, paradox and ultra beasts are all excluded`() {
        // Mythical is the case Cobblemon's own helper misses, and it is the one that matters most:
        // Arceus, with a form for every plate.
        listOf("zapdos", "arceus", "greattusk", "nihilego", "buzzwole").forEach {
            assertTrue(exclusion.isExcluded(id(it)), "$it was startable")
        }
    }

    @Test
    fun `label matching ignores case`() {
        // Labels are datapack text. A species pack that capitalised one would otherwise walk straight
        // past a hard ban.
        assertTrue(exclusion.isExcluded(id("shouty")))
    }

    @Test
    fun `an unknown species is excluded rather than admitted`() {
        // Fail closed: a species whose labels cannot be read cannot be proven safe, and this is the
        // only direction that can leak a legendary through. It costs nothing — an unresolvable
        // species could not have been created for the run anyway.
        assertTrue(exclusion.isExcluded(id("nonesuch")))
    }

    @Test
    fun `the ban list is a set, and narrowing it is one edit`() {
        // Pins that the labels are configuration of the rule rather than scattered through it: a
        // server that wanted Ultra Beasts startable should be able to say so here and nowhere else.
        val legendaryOnly = LabelStarterExclusion({ labels[it] }, excluded = setOf("legendary"))
        assertTrue(legendaryOnly.isExcluded(id("zapdos")))
        assertFalse(legendaryOnly.isExcluded(id("nihilego")))
    }
}
