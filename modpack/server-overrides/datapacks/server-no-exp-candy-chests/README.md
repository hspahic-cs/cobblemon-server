# server-no-exp-candy-chests

Despite the name, this pack is now the single home for **all our Legendary
Monuments chest-loot overrides**. It started as an exp-candy strip and grew.
Renaming it would change the datapack id, so the name stays for now — see
"Naming" at the bottom.

## What it does

1. **Exp candy strip.** Overrides `cobblemon:sets/any_exp_candy` with an empty
   pool (covers every chest that *references* the set) and surgically removes
   inline `exp_candy_*` entries from the chests that hardcode them.
2. **Artifact rarity.** Keeps legendary-summon and forme-change items in line
   with the Ultra crate, which is the server's rarity benchmark.

## LM chest overrides

The mod ships 11 chest tables. We override 10; `heatran_cave_chest` is left at
upstream defaults (reviewed — nothing above mid-tier in it).

| Item | Chest | Upstream | Ours | Why |
|---|---|---|---|---|
| `minecraft:totem_of_undying` | bell_tower | 8.7% | **removed** | Gates Zacian; crate-only (0.23.31) |
| `mega_showdown:red_orb` | bell_tower | 1.1% | **0.57%** | Primal Groudon |
| `mega_showdown:blue_orb` | lugia_temple | 2.6% | **0.53%** | Primal Kyogre — was 2.4× easier than its Groudon counterpart |
| `griseous_orb` / `adamant_orb` / `lustrous_orb` | turnback_cave_vault | 5.0% ea | **0.53% ea** | Held items |
| `griseous_core` / `adamant_crystal` / `lustrous_globe` | turnback_cave_vault | 1.0% ea | **0.11% ea** | Origin-forme unlocks (Ubers-tier) |

All percentages are **per chest opened**, not per roll — these pools roll 3–14
times, so per-roll weights badly understate real supply. Compare against the
Ultra crate: jackpot band 0.8–1.6%, high band 4.9%.

## How the nerfs are expressed

Two different mechanics, because loot-table weights are integers and several of
these items already sat at the minimum `weight: 1`:

- **turnback_cave_vault** — target weights lowered directly (orbs `50→5`, cores
  `10→1`). The pool total drops, so every *other* entry's share rises
  proportionally. No entries added or removed; count stays at 45.
- **lugia_temple / bell_tower** — `blue_orb` and `red_orb` were already at
  `weight: 1` and can't go lower, so instead **every other entry is scaled up**
  (×5 and ×2 respectively). Relative odds among all other items are unchanged;
  only the orb's share falls.

If you retune these, regenerate rather than hand-editing: the scale factors are
what make the orb math work.

## Totem of Undying (the Zacian gate)

The Ultra crate's 1.6% roll is the **only intended** source. Two others were
removed:

| Source | Status |
|---|---|
| `bell_tower_chest` | Removed 0.23.31 (8.7%/chest) |
| `champion_jax_05b7` `signatureItem` | Removed — was guaranteed on defeat |
| Ultra crate, 1.6% | **Intended — leave this one** |

Vanilla evokers (raids, woodland mansions) still drop them; that's vanilla and
out of scope.

If a player reports getting one from Ho-Oh, it is either pre-June-2026, an
evoker, or a Bell Tower chest that was generated *and populated* before the
0.23.31 fix and hasn't been looted yet.

## Known gaps (deliberately not changed)

These are **intentional** — the call was not to punish players for finding this
content themselves. Listed so the rates are discoverable, not as a to-do list.

- `lugia_temple_chest` still contains **inline exp candies** (`exp_candy_xs`
  90.2%/chest, `exp_candy_s` 56.3%, `exp_candy_m` 19.4%). It was never covered
  by the exp-candy strip because it hardcodes the items rather than referencing
  `cobblemon:sets/any_exp_candy`. Adding this file to the pack did not change
  that either way — flagging it as a decision, not an oversight.
- `old_sea_map` (**Mew**, 12.3%/chest, liberty_island) and `griseous_key`
  (**Giratina**, 10.9%/chest, turnback_cave) are both ~2× more likely than any
  4.9% Ultra crate summon item. Untouched pending a call.
- `cobblemon:ability_patch` still drops at 5.9%/chest in registeel, even though
  it was deliberately removed from the Ultra crate.
- Light/Dark Stone shards remain craftable 9→1 into the full stone, which
  bypasses the crate's 4.9% Light Stone / Dark Stone.

## Naming

The pack id `server-no-exp-candy-chests` no longer describes its scope. A rename
to something like `server-monument-loot` would be clearer, and the deploy's
"Prune retired server-* datapacks" step would clean up the old id automatically
— but the new id would need confirming as *enabled* on both servers afterward
(`server-lootballs` is currently present-but-disabled on prod, so this is a real
failure mode).
