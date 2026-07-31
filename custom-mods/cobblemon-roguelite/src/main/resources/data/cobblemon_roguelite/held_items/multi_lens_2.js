/*
 * Multi Lens II — tier 2: 3 hits at 50% + 25% + 25%. See multi_lens_1.js for the whole mechanic;
 * this file is a tier declaration and nothing else, the same stub shape as boss_shield_2.js.
 *
 * WHY A STUB: the tier cannot ride on the Pokémon — Showdown gives a set no free field — so it
 * rides on WHICH ITEM is held, one file per tier, and §2.34 makes a reward pick REPLACE
 * `multilens1` with this rather than add a second lens. The shared implementation lives in
 * multi_lens_1.js on `globalThis`; the guard degrades to an inert unremovable item if that file
 * is deleted, because an exception inside a Showdown handler takes the whole battle down.
 */

(function () {
	function impl() {
		return globalThis.cobblemonRogueliteMultiLens;
	}

	return {
		name: "Multi Lens 2",
		spritenum: 0,
		num: -9102,
		gen: 9,
		rogueliteMultiLensTier: 2,
		onModifyMove: function (move, pokemon, target) {
			var shared = impl();
			if (shared) shared.modifyMove(this, move, pokemon);
		},
		onModifyDamage: function (damage, source, target, move) {
			var shared = impl();
			return shared ? shared.modifyDamage(this, damage, source, target, move) : undefined;
		},
		onTakeItem: function () {
			return false;
		},
	};
})()
