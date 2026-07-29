/*
 * Boss Shield 5 — 5 shields. See boss_shield_1.js for the whole mechanic; this file is a tier
 * declaration and nothing else.
 *
 * WHY THIS IS A STUB. The shield count cannot ride on the Pokémon — Showdown gives a set no free
 * field — so it rides on WHICH ITEM the boss holds, one item per count. Copying the implementation
 * into all five would mean fixing every bug five times, so it lives in boss_shield_1.js and
 * installs itself on `globalThis`.
 *
 * That is order-independent even though Cobblemon evaluates every held-item file in one `eval`
 * with no defined order between them: the global is dereferenced inside the handlers, and handlers
 * run during a battle, long after every file has been evaluated.
 *
 * The guard is the "boss_shield_1.js was deleted" case. It degrades to an inert but still
 * unremovable item rather than throwing, because an exception thrown inside a Showdown handler
 * takes the whole battle down — a silent loss of the mechanic is bad, a dead battle is worse.
 */

(function () {
	function impl() {
		return globalThis.cobblemonRogueliteBossShield;
	}

	return {
		name: "Boss Shield 5",
		spritenum: 0,
		num: -9005,
		gen: 9,
		rogueliteShields: 5,
		onStart: function (pokemon) {
			var shared = impl();
			if (shared) shared.start(this, pokemon);
		},
		onDamagePriority: -100,
		onDamage: function (damage, target, source, effect) {
			var shared = impl();
			return shared ? shared.damage(this, damage, target, source, effect) : undefined;
		},
		onUpdate: function (pokemon) {
			var shared = impl();
			if (shared) shared.update(this, pokemon);
		},
		onTakeItem: function () {
			return false;
		},
	};
})()
