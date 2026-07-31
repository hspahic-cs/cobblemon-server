/*
 * Multi Lens — PokéRogue's extra-strike modifier, as a tiered player held item (plan §2.33/§2.34).
 *
 * READ boss_shield_1.js FIRST. Everything its header says about this directory applies here
 * unchanged and is not repeated in full: every held-item file on the server is joined into ONE
 * object literal and eval'd in one go (a syntax error here breaks every held item on the server,
 * including Cobblemon's own), each file must be a single EXPRESSION, and the Showdown item id
 * comes from `name:` below — "Multi Lens 1" -> `multilens1`, which is what Kotlin's
 * `ModifierItems.showdownId(MULTI_LENS, 1)` must produce. Nothing warns when they diverge; the
 * holder just quietly holds an item Showdown has never heard of.
 *
 * HOW THE PLAYER SIDE DIFFERS FROM THE SHIELDS. A boss shield never exists as an ItemStack — the
 * team generator writes a properties string. This item is a REAL stack on a run Pokémon, minted by
 * `modifier/ModifierItems.kt`: a vanilla spyglass whose `cobblemon:held_item_effect` component
 * names this Showdown id. Cobblemon's held-item manager reads that component before it looks at
 * the item registry, so the spyglass is theatre and this definition is the item.
 *
 * WHAT THE MECHANIC IS (their spec: each stack converts 25% of attack damage into an additional
 * strike):
 *
 *   tier 1 -> 2 hits: 75% + 25%          tier 2 -> 3 hits: 50% + 25% + 25%
 *
 * Two hooks, because Showdown splits the job in two (§2.34): `onModifyMove` sets `move.multihit`,
 * which `hitStepMoveHitLoop` reads — the exact assignment Parental Bond makes — and the per-hit
 * damage split is OURS to apply in `onModifyDamage`, because the engine only auto-reduces hits
 * where `multihitType === "parentalbond"`, and that path is hard-coded to ×0.25 on the second hit
 * of an unweakened first — not our split. So `multihitType` is deliberately NOT set.
 *
 * The move-eligibility guard below is Parental Bond's own list, copied: no status moves, nothing
 * already multi-hit (Loaded Dice territory, and double-dipping Bullet Seed would be absurd), no
 * charge/future moves, no spread hits. `move.rogueliteMultiLens` rides on the ACTIVE move copy —
 * Showdown clones the move object per use, so tagging it does not contaminate the dex entry — and
 * is what `onModifyDamage` trusts, so a move that was boosted keeps its split even if the item is
 * somehow gone by the time damage is computed.
 *
 * The stub-and-global structure is boss_shield's: the shared implementation installs itself on
 * `globalThis` here, multi_lens_2.js only declares its tier. Order-independent because the global
 * is only dereferenced inside handlers, which run mid-battle, long after every file has been
 * evaluated; the guard degrades a missing global to an inert item rather than throwing, because an
 * exception inside a Showdown handler takes the whole battle down.
 *
 * `onTakeItem` refuses removal, the same property Mega Stones use. The stack underneath is
 * run-marked property that the run's isolation machinery accounts for; letting Knock Off or Trick
 * detach it mid-battle would hand Cobblemon's item-sync a stack our bookkeeping thinks is on a
 * Pokémon. PokéRogue modifiers cannot be stolen either, so this is also the faithful reading.
 */

(function () {
	var impl = globalThis.cobblemonRogueliteMultiLens;
	if (!impl) {
		impl = globalThis.cobblemonRogueliteMultiLens = {
			/* How many extra strikes this holder's item grants, or 0 if it is not one of ours.
			 * `rogueliteMultiLensTier` is a custom data field: Showdown's BasicEffect copies unknown
			 * fields onto the item verbatim, same trick as the shields' `rogueliteShields`. */
			tierOf: function (pokemon) {
				var item = pokemon.getItem();
				return (item && item.rogueliteMultiLensTier) || 0;
			},

			/* Parental Bond's own exclusions, copied — see the header for why each class of move
			 * is out. `flags` is always an object on a real move, but the guards fail-open. */
			applies: function (move) {
				if (!move || move.category === "Status") return false;
				if (move.multihit) return false;
				var flags = move.flags || {};
				if (flags["noparentalbond"] || flags["charge"] || flags["futuremove"]) return false;
				if (move.spreadHit || move.isZ || move.isMax) return false;
				return true;
			},

			modifyMove: function (battle, move, pokemon) {
				var tier = impl.tierOf(pokemon);
				if (!tier || pokemon.ignoringItem() || !impl.applies(move)) return;
				/* The assignment §2.34 confirmed: hitStepMoveHitLoop reads this. One extra hit
				 * per tier. */
				move.multihit = tier + 1;
				move.rogueliteMultiLens = tier;
			},

			/*
			 * The per-hit split. Runs once per hit; `move.hit` is the 1-based hit counter the hit
			 * loop maintains, which is exactly how the engine's own parentalbond quarter-damage
			 * check identifies the extra hit.
			 *
			 * chainModify in 1/4096ths, Showdown's own fixed-point idiom: first hit keeps
			 * (1 - 0.25×tier) of its damage — 3072/4096 = 75% at tier 1, 2048/4096 = 50% at tier 2
			 * — and every extra hit is 1024/4096 = 25%. Total stays 100%: the lens converts
			 * damage, it does not add any. The reward is secondary-effect rolls, contact procs and
			 * sash/shield pressure per hit — which is also why this item into a boss's §2.32
			 * shields is deliberately strong: more hits, more boundaries crossed.
			 */
			modifyDamage: function (battle, damage, source, target, move) {
				var tier = move && move.rogueliteMultiLens;
				if (!tier) return;
				if (move.hit > 1) return battle.chainModify([1024, 4096]);
				return battle.chainModify([4096 - tier * 1024, 4096]);
			},
		};
	}

	return {
		name: "Multi Lens 1",
		spritenum: 0,
		num: -9101,
		gen: 9,
		/* Read back by `tierOf`. */
		rogueliteMultiLensTier: 1,
		onModifyMove: function (move, pokemon, target) {
			impl.modifyMove(this, move, pokemon);
		},
		onModifyDamage: function (damage, source, target, move) {
			return impl.modifyDamage(this, damage, source, target, move);
		},
		onTakeItem: function () {
			return false;
		},
	};
})()
