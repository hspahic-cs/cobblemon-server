package com.cobblemonroguelite.boss

/**
 * PokéRogue's segmented boss HP bar, expressed as a Cobblemon held item (§2.32).
 *
 * A shielded boss's HP is divided into `shields + 1` chunks. Incoming damage may never carry it
 * past a chunk boundary in one hit: the hit is floored at the boundary, one shield shatters, and a
 * random stat rises by one stage. When the last shield is gone the final chunk is ordinary HP and
 * the boss can be knocked out.
 *
 * ### Why this file exists at all, when the mechanic runs in JavaScript
 *
 * The behaviour lives in `data/cobblemon_roguelite/held_items/boss_shield_*.js`, inside Showdown's
 * GraalJS context, where nothing here can reach it and no unit test can observe it. What *is*
 * ours, and what breaks the mechanic silently if it drifts, is the contract between the two sides:
 *
 * - the **id** Showdown will see for a given shield count, and
 * - the **boundary arithmetic** the JS implements.
 *
 * Both are written here, both are tested here, and the JS quotes this file by name. That does not
 * make the JS tested — it makes a disagreement between the two a thing a reader can find, rather
 * than a boss that absorbs damage at the wrong percentages and looks merely "tuned oddly".
 *
 * ### How the shield count reaches the JavaScript
 *
 * Through **which item the boss holds**, and nothing else. Showdown has no per-Pokémon slot for
 * mod data — a set carries species, level, ability, moves, stats and one item id — so the count has
 * to be part of the item's identity. §2.31 reached the same conclusion from the other direction
 * when it looked at stacking: *"pre-generate tiers as separate datapack items"*. There is one JS
 * file per count, `bossshield1` … `bossshield5`, and the generator picks the one it wants.
 *
 * The stack itself is built by [heldItemProperty], which does **not** name a registered item of
 * ours. Registering items would make this mod client-required — NeoForge syncs the item registry
 * and a client without the entries is refused at login — and §2.32 spent real argument on *not*
 * paying that price yet. Instead the boss holds a vanilla `minecraft:shield` carrying Cobblemon's
 * own `held_item_effect` data component, which `CobblemonHeldItemManager.showdownId` reads
 * *before* it looks at the item registry at all. The item you can see is irrelevant; the component
 * is the item Showdown gets told about.
 *
 * That also gives an honest failure: if the component is ever dropped, a bare `minecraft:shield`
 * maps to no Showdown item, so the boss simply holds nothing. It does not become some other item.
 */
object BossShields {

    /**
     * How many shields the mechanic can express, i.e. how many `boss_shield_N.js` files ship.
     *
     * Raising this number is **two** edits, and doing only one of them is the failure this constant
     * exists to make loud: a count with no file produces a held item id Showdown has never heard
     * of, the item resolves to nothing, and the boss quietly fights with no shields at all. Nothing
     * logs, because from Cobblemon's point of view a Pokémon holding an unknown item is normal.
     */
    const val MAX_SHIELDS: Int = 5

    /** The Showdown item id for a boss carrying [shields] shields. Matches `name:` in the JS file. */
    fun showdownId(shields: Int): String {
        require(shields in 1..MAX_SHIELDS) { "shields must be 1..$MAX_SHIELDS, was $shields" }
        return "bossshield$shields"
    }

    /**
     * The `held_item=` fragment that puts [shields] shields on a generated Pokémon.
     *
     * ### Why it is a component string and not an item id
     *
     * `PokemonProperties` parses this value with vanilla's `ItemParser` and applies the component
     * patch it returns, so the full item-stack syntax is available here. Two properties of
     * Cobblemon's parser make it safe to write inline, and both are worth knowing before anyone
     * edits the string:
     *
     * - a properties string is split on **spaces first**, so this fragment must contain none;
     * - each token is then split at its **first** `=` only, so the `=` inside `[component=value]`
     *   stays in the value rather than truncating it.
     *
     * Break either rule and the failure is silent: the Pokémon is built, it just holds nothing.
     *
     * `consumed:false` is written out rather than left to a default because the component's codec
     * is not ours and an omitted required field fails the parse — again silently, again as a boss
     * with no shields.
     */
    fun heldItemProperty(shields: Int): String =
        "held_item=minecraft:shield[cobblemon:held_item_effect=" +
            "{showdownId:\"${showdownId(shields)}\",consumed:false}]"

    /**
     * True when [showdownId] is one of ours, i.e. the holder is a shielded boss.
     *
     * Used by [BossShieldBattle] to decide who gets the name marker, and deliberately matched on
     * the **prefix plus a valid count** rather than on the prefix alone: a future `bossshieldxyz`
     * belonging to somebody else should not be mistaken for this mechanic.
     */
    fun isShieldItem(showdownId: String?): Boolean =
        showdownId != null && (1..MAX_SHIELDS).any { showdownId == showdownId(it) }

    /**
     * The HP a boss with [shields] shields, [broken] of them already gone, cannot be taken below by
     * a single hit.
     *
     * **This is the specification the JavaScript implements**, written identically on both sides:
     *
     * ```
     * Math.ceil(maxHp * (shields - broken) / (shields + 1))
     * ```
     *
     * The `+ 1` is the point of the whole shape. `shields` shields means `shields + 1` chunks of
     * HP: with three shields a boss stops at 75%, 50% and 25%, and only the last quarter is
     * ordinary HP that a hit may take to zero. Dividing by `shields` instead would give a
     * three-shield boss two real stops and one free one, so every boss would be a shield weaker
     * than its own message claims.
     *
     * Rounding **up** so the floor is never below the true fraction. A boss whose maxHP does not
     * divide evenly loses the rounding remainder from its last chunk rather than from a shielded
     * one, which is the version a player cannot notice.
     *
     * Returns 0 once every shield is broken — no floor, the boss is killable — which is why
     * [broken] is allowed to equal [shields] rather than being rejected.
     */
    fun floorHp(maxHp: Int, shields: Int, broken: Int): Int {
        require(maxHp >= 1) { "maxHp must be at least 1, was $maxHp" }
        require(shields in 1..MAX_SHIELDS) { "shields must be 1..$MAX_SHIELDS, was $shields" }
        require(broken in 0..shields) { "broken must be 0..$shields, was $broken" }
        val remaining = shields - broken
        if (remaining <= 0) return 0
        return Math.ceilDiv(maxHp * remaining, shields + 1)
    }

    /**
     * How much damage the current chunk can still take before the next shield goes.
     *
     * Zero or negative means the holder is already at or past the boundary — which the JS treats as
     * "advance the broken count and try again" rather than as an error. It happens for real: HP can
     * move without passing through the damage handler (Pain Split, a direct `-sethp`), and a floor
     * sitting above current HP would clamp every subsequent hit to nothing and make the boss
     * immortal. That is the single worst failure this mechanic can have, so both sides guard it.
     */
    fun absorbableDamage(currentHp: Int, maxHp: Int, shields: Int, broken: Int): Int =
        currentHp - floorHp(maxHp, shields, broken)
}
