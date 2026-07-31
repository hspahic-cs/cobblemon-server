/*
 * Reviver Seed — PokéRogue's one-shot auto-revive, as a player held item (plan §2.33/§2.34).
 *
 * READ boss_shield_1.js FIRST for the rules of this directory: one joined eval (a syntax error
 * here kills every held item on the server), single-EXPRESSION files, and the Showdown id coming
 * from `name:` — "Reviver Seed" -> `reviverseed`, which Kotlin's
 * `ModifierItems.showdownId(REVIVER_SEED, 1)` must match. Single tier, so no stub/global split:
 * this file is self-contained.
 *
 * WHAT THE MECHANIC IS: the first time the holder would faint — from any damage source, not only
 * moves, because PokéRogue's seed revives from hazards and residuals too — it survives and ends up
 * at half its max HP, and the seed is consumed. Consumption is real twice over: `useItem()`
 * removes the Showdown item and emits `-enditem`, and the ItemStack this definition rides on was
 * minted with `held_item_effect.consumed = true`, which is the flag
 * `CobblemonHeldItemManager.shouldConsumeItem` reads to delete the REAL stack off the run Pokémon.
 * One revive per seed, ever — the §2.34 ruling, and the opposite flag from the shields'
 * `consumed:false`.
 *
 * HOW IT IS SPLIT ACROSS TWO HOOKS, and why not one:
 *
 *   - `onDamage` is the only place damage can still be replaced (Focus Sash's own mechanism):
 *     a would-be-lethal hit is floored to leave 1 HP, and a flag is queued on `itemState`.
 *   - The heal and the consumption happen in `onUpdate`, NOT inline — the same deferral
 *     boss_shield_1.js documents for its stat boost: stock Showdown never runs a nested
 *     heal/boost event from inside `onDamage`, and `onUpdate` runs after every action and every
 *     residual, so the gap is imperceptible. Consuming in `onUpdate` also keeps the ordering
 *     honest: the item must still be held for its own `onUpdate` to fire, and once `useItem()`
 *     lands the handlers are gone with it.
 *
 * A consequence, accepted deliberately: every lethal blow BETWEEN the trigger and the next
 * `onUpdate` is floored too, so a multi-hit move cannot burn the seed on hit one and kill on hit
 * two. That matches PokéRogue, where the revive happens after the whole faint and the holder
 * stands back up regardless of what felled it.
 *
 * `onDamagePriority: -50` puts the floor AFTER Focus Sash (-40) and Sturdy (-30) have had their
 * say — an ability like Sturdy endures first and the seed keeps its charge — and before the
 * shields' -100, which never shares a holder with this anyway.
 *
 * KNOWN ROUGH EDGE: Cobblemon renders `-enditem` for an item id it has never registered by
 * falling back to the raw id, so the chat line names `reviverseed` rather than "Reviver Seed".
 * Cosmetic, and fixable later with a custom protocol line like the shields'; not worth an
 * interpreter today.
 *
 * `onTakeItem` refuses removal for the same two reasons multi_lens_1.js gives: the stack is
 * run-marked property, and PokéRogue modifiers cannot be stolen.
 */

(function () {
	return {
		name: "Reviver Seed",
		spritenum: 0,
		num: -9111,
		gen: 9,
		onDamagePriority: -50,
		onDamage: function (damage, target, source, effect) {
			if (typeof damage !== "number" || damage < target.hp) return;
			var state = target.itemState;
			state.rogueliteRevive = true;
			/* Floor at 1, Focus-Sash style. The climb to half HP is onUpdate's job. */
			return target.hp - 1;
		},
		onUpdate: function (pokemon) {
			var state = pokemon.itemState;
			if (!state.rogueliteRevive) return;
			state.rogueliteRevive = false;
			if (!pokemon.hp) return;
			/* useItem() emits -enditem, which is what tells Cobblemon to delete the real,
			 * consumed:true ItemStack. If it refuses (Embargo landing in the gap, item already
			 * gone), no heal either — the seed must never heal without being spent. */
			if (pokemon.useItem()) {
				var half = Math.ceil(pokemon.maxhp / 2);
				if (pokemon.hp < half) this.heal(half - pokemon.hp, pokemon);
			}
		},
		onTakeItem: function () {
			return false;
		},
	};
})()
