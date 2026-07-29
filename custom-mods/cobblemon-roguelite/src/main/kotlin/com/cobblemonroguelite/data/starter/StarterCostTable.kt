package com.cobblemonroguelite.data.starter

import net.minecraft.resources.ResourceLocation

/**
 * One file's worth of starter prices: species id to points (§2.13).
 *
 * ### Why this is a datapack and not a config file, and not source
 *
 * §2.12's reason applies — tables must reload without a restart, and a server owner has to be able to
 * write their own without touching the jar — and §2.7 adds a stronger one that is specific to this
 * table. PokéRogue's per-species costs *are* their balance judgement, which makes them their data;
 * the roguelite's own prices are transcribed into a server-side datapack on our server and are never
 * vendored into this repository. A published build ships this schema, the example file, and nothing
 * else, and falls through to [com.cobblemonroguelite.starter.DerivedStarterCost] for every species.
 *
 * @property costs species to points, already validated as at least 1. Absent species are not free —
 *   see [com.cobblemonroguelite.starter.StarterCostSource].
 */
data class StarterCostTable(val id: ResourceLocation, val costs: Map<ResourceLocation, Int>)
