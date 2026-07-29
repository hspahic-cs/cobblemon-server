/*
 * Boss Shield — PokéRogue's segmented boss HP bar, as a Cobblemon held item (plan §2.32).
 *
 * =============================================================================================
 * READ THIS BEFORE EDITING. This is the least familiar code in the repository and the rules it
 * runs under are not the rules of any other file here.
 * =============================================================================================
 *
 * WHERE THIS RUNS
 *
 * Cobblemon scans `data/<namespace>/held_items/*.js` out of every datapack and pushes them into
 * the Showdown battle simulator as REAL item definitions — handler functions and all, with access
 * to the battle and the Pokémon. This is stock Cobblemon (`api/item/HeldItems.kt`): no mixin, no
 * patching of Showdown's bundle, nothing that a published mod could not rely on. §2.31 established
 * the channel and also established why the alternatives are closed: Mega Showdown already
 * overwrites Showdown's own `sim/*.js` on our server, so patching the bundle would be a load-order
 * fight with a mod we depend on.
 *
 * HOW IT IS LOADED, AND THE TWO CONSEQUENCES THAT MATTER
 *
 * Cobblemon does NOT evaluate these files one by one. It joins every held-item file on the server
 * into a single JavaScript object literal — `{ boss_shield_1: <this file>, eggantberry: <that
 * file>, ... }` — and `eval`s the whole thing in one go. So:
 *
 *   1. A SYNTAX ERROR HERE BREAKS EVERY HELD ITEM ON THE SERVER, not just this one. There is no
 *      per-file isolation and no per-file error. Leftovers stops working because of a stray comma
 *      in this file.
 *   2. A file must be a single JavaScript EXPRESSION, because it is being used as an object
 *      value. Hence the `(function () { ... })()` wrapper — a statement would not parse.
 *
 * Note also that the file name is only the object key; the Showdown item id comes from `name`
 * below, lowercased and stripped of spaces (`"Boss Shield 1"` -> `bossshield1`). Renaming `name`
 * renames the item that Kotlin's `BossShields.showdownId` has to produce, and nothing will tell
 * you they have diverged.
 *
 * WHY THE SHARED IMPLEMENTATION LIVES IN *THIS* FILE
 *
 * The shield count cannot travel as data — Showdown has no per-Pokémon slot for mod fields — so it
 * travels as WHICH ITEM the boss holds, one file per count (§2.31's own answer to the same problem
 * for stacking). Five files, five nearly identical items. Copying the logic five times would mean
 * fixing every bug five times, so it is written once here and installed on `globalThis`;
 * `boss_shield_2.js` … `boss_shield_5.js` are stubs that call into it.
 *
 * That is safe despite the files having no defined evaluation order between them, because the
 * stubs only dereference the global INSIDE their handlers, and handlers run during a battle — long
 * after every held-item file in the same `eval` has been evaluated. The stubs still guard against
 * the global being missing, which is the "somebody deleted this file" case: they degrade to an
 * inert unremovable item rather than throwing inside the simulator, because an exception in a
 * battle handler takes the whole battle down.
 *
 * WHAT THE MECHANIC IS
 *
 * `shields` shields divide the holder's HP into `shields + 1` chunks. A hit may never carry the
 * holder past a chunk boundary: it is floored at the boundary, one shield shatters, and a random
 * stat rises one stage. Once every shield is broken the last chunk is ordinary HP and the boss can
 * be knocked out. The arithmetic is stated in `boss/BossShields.kt#floorHp` and is written here in
 * the same form on purpose — that file is where it is unit tested, since nothing in here can be.
 *
 * The item refuses removal (`onTakeItem` returns false, the same property Mega Stones use).
 * Without that, Knock Off or Trick deletes a boss's defining mechanic mid-fight, which reads as a
 * bug rather than as counterplay.
 *
 * KNOWN GAP: Magic Room and Embargo suppress item effects wholesale in Showdown
 * (`Pokemon#ignoringItem`), and no item can opt out of that the way `ignoreKlutz` opts out of
 * Klutz. Under either, the shields go dormant and the boss can be burst past a boundary. The
 * broken count survives in `itemState`, so they come back when the effect ends — it is a
 * suspension, not the deletion that `onTakeItem` exists to prevent. Closing it would mean the
 * ability slot, which §2.32 rejected so that a boss keeps its own ability.
 *
 * SIGNALLING IS PART OF THE MECHANIC. An unexplained damage floor is worse than no damage floor:
 * a player who sees a lethal hit land for 80% with no explanation concludes the mod is broken. The
 * three `battle.add` calls below are not logging — see `boss/BossShieldBattle.kt` for what they
 * render as and why they use a custom protocol id.
 */

(function () {
	var impl = globalThis.cobblemonRogueliteBossShield;
	if (!impl) {
		impl = globalThis.cobblemonRogueliteBossShield = {
			/*
			 * Must match BossShieldBattle.PROTOCOL_ID. An id Cobblemon's interpreter does not know
			 * is not an error — it broadcasts the raw line in red chat, pipes and all — so a typo
			 * here shows up as what looks like a crash report in the middle of the battle log.
			 */
			protocol: "-rogueliteshield",

			/* Boostable stats, in Showdown's own keys. Deliberately not accuracy/evasion: those
			 * are the two boosts that make a fight unreadable rather than harder. */
			stats: ["atk", "def", "spa", "spd", "spe"],

			/* How many shields this holder's item grants, or 0 if it is not one of ours. */
			shieldsOf: function (pokemon) {
				var item = pokemon.getItem();
				return (item && item.rogueliteShields) || 0;
			},

			/*
			 * `boss/BossShields.kt#floorHp`, in JavaScript. Keep the two in the same shape.
			 *
			 * The `+ 1` is the whole point: `shields` shields means `shields + 1` chunks, so three
			 * shields stop the boss at 75%, 50% and 25% and leave the last quarter killable.
			 * Dividing by `shields` would give every boss one fewer real stop than its own message
			 * claims.
			 */
			floorHp: function (pokemon, shields, broken) {
				var remaining = shields - broken;
				if (remaining <= 0) return 0;
				return Math.ceil((pokemon.maxhp * remaining) / (shields + 1));
			},

			/*
			 * The per-Pokémon shield state, on Showdown's `itemState`.
			 *
			 * `itemState` is the right home rather than a volatile: Showdown clears volatiles on
			 * switch-out and does NOT clear `itemState`, so a boss that retreats and returns comes
			 * back with the same shields broken. A volatile would hand the player a full guard
			 * every time the boss switched.
			 *
			 * The loop is not paranoia. HP can move without ever passing through `onDamage` — Pain
			 * Split, a direct `-sethp`, Cobblemon writing an absolute HP value back into the sim —
			 * and a floor sitting ABOVE current HP would clamp every later hit to zero damage and
			 * make the boss literally unkillable. So the broken count is re-derived from HP on
			 * every read, upwards only, which also means healing can never restore a broken shield.
			 */
			sync: function (pokemon, shields) {
				var state = pokemon.itemState;
				var broken = state.rogueliteBroken || 0;
				while (broken < shields && pokemon.hp <= impl.floorHp(pokemon, shields, broken)) {
					broken++;
				}
				state.rogueliteBroken = broken;
				return state;
			},

			/*
			 * One stat that is not already maxed, chosen with the battle's own seeded RNG so a
			 * replayed battle boosts the same stat. Returns null when every stat is at +6, in which
			 * case the break is announced without a stat rather than claiming one that cannot rise.
			 */
			pickStat: function (battle, pokemon) {
				var options = [];
				for (var i = 0; i < impl.stats.length; i++) {
					if ((pokemon.boosts[impl.stats[i]] || 0) < 6) options.push(impl.stats[i]);
				}
				if (!options.length) return null;
				return options[battle.random(options.length)];
			},

			/*
			 * Fires on every send-in, not only the first, because Showdown runs an item's Start
			 * event on each switch-in. That is wanted: a boss returning to the field re-states how
			 * much of its guard is left, which is the number the player is tracking.
			 */
			start: function (battle, pokemon) {
				var shields = impl.shieldsOf(pokemon);
				if (!shields || pokemon.ignoringItem()) return;
				var state = impl.sync(pokemon, shields);
				battle.add(impl.protocol, "start", pokemon.name, String(shields - state.rogueliteBroken));
			},

			/*
			 * The floor itself. Returning a number from `onDamage` replaces the damage — the same
			 * mechanism Focus Sash and Sturdy use, which is why this is `onDamage` and not one of
			 * the after-hit hooks: only here can the damage still be changed.
			 *
			 * `onDamagePriority` is set very low on each item below so this runs LAST, after Sturdy,
			 * Focus Sash and Endure have had their say. Ours is the outermost floor; anything that
			 * runs after it could push the holder back through a boundary we just refused.
			 *
			 * ONE SHIELD PER HIT, always. A hit big enough to cross three boundaries crosses one,
			 * and the rest of its damage is discarded. That is the mechanic, not a rounding
			 * convenience: it is what stops a single crit from deleting a whole boss bar.
			 */
			damage: function (battle, damage, target) {
				var shields = impl.shieldsOf(target);
				if (!shields) return;
				var state = impl.sync(target, shields);
				var broken = state.rogueliteBroken;
				/* Last chunk: no floor left, the boss dies like anything else. */
				if (broken >= shields) return;

				var room = target.hp - impl.floorHp(target, shields, broken);
				/* The hit fits inside the current chunk. Nothing was absorbed, so nothing is said —
				 * a line on every hit would bury the two lines that matter. */
				if (damage < room) return;

				state.rogueliteBroken = broken + 1;
				var left = shields - state.rogueliteBroken;

				/*
				 * THE line. This is what stands between a floored hit and a bug report, so it names
				 * the damage that was thrown away rather than only the outcome. Skipped when the
				 * hit landed exactly on the boundary, because then nothing actually was absorbed.
				 */
				if (damage > room) {
					battle.add(impl.protocol, "absorb", target.name, String(damage - room), String(left));
				}

				/*
				 * The boost is DEFERRED to onUpdate rather than applied here. Stock Showdown has no
				 * ability or item that boosts from inside `onDamage` — Anger Shell and Berserk both
				 * detect in `onDamage` and act later — and running a nested boost event in the
				 * middle of damage calculation is exactly the sort of thing that works until it
				 * does not. `onUpdate` runs after every action and every residual, so the gap is
				 * imperceptible and the event nesting is one Showdown does constantly (every berry
				 * eats from `onUpdate`).
				 */
				state.rogueliteBoost = impl.pickStat(battle, target);
				battle.add(
					impl.protocol,
					"break",
					target.name,
					String(left),
					state.rogueliteBoost || "",
				);
				return room;
			},

			/* Applies the boost a break queued. See `damage` for why it is not applied inline. */
			update: function (battle, pokemon) {
				var state = pokemon.itemState;
				var stat = state.rogueliteBoost;
				if (!stat) return;
				state.rogueliteBoost = null;
				if (!pokemon.hp) return;
				var boost = {};
				boost[stat] = 1;
				battle.boost(boost, pokemon, pokemon);
			},
		};
	}

	return {
		name: "Boss Shield 1",
		spritenum: 0,
		num: -9001,
		gen: 9,
		/* Read back by `shieldsOf`. Showdown's BasicEffect copies unknown data fields onto the
		 * item verbatim, which is what makes a custom field on an item possible at all. */
		rogueliteShields: 1,
		onStart: function (pokemon) {
			impl.start(this, pokemon);
		},
		/* Lower than Focus Sash's -40 and Sturdy's -30, so our floor is applied last. */
		onDamagePriority: -100,
		onDamage: function (damage, target, source, effect) {
			return impl.damage(this, damage, target, source, effect);
		},
		onUpdate: function (pokemon) {
			impl.update(this, pokemon);
		},
		/* The shields ARE the boss. Knock Off, Trick or Magic Room deleting them mid-fight reads
		 * as a bug rather than as counterplay, so the item refuses removal outright — the same
		 * property Mega Stones use, so this is supported behaviour and not a trick. */
		onTakeItem: function () {
			return false;
		},
	};
})()
