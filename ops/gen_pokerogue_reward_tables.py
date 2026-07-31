#!/usr/bin/env python3
"""Transcribe PokéRogue's post-wave item system into roguelite reward + shop tables.

    python3 ops/gen_pokerogue_reward_tables.py [--out DIR]

§2.39's standing rule: where PokéRogue has a table, we extract it. The extraction of record is
ops/pokerogue-modifier-pools.json (their `init-modifier-pools.ts` et al., read 2026-07-31 at commit
0d94c5bb); this script maps it through the §2.34 modifier-audit dispositions into:

  - reward_tables/default.json  — the free post-wave pick (their PLAYER pool). One table for every
    wave kind, which is faithful: their post-battle pick ALWAYS draws from the PLAYER pool — the
    WILD/TRAINER pools generate held items on enemy Pokémon, not the pick.
  - shop_tables/default.json    — the paid row (their getPlayerShopModifierTypeOptionsForWave):
    seven fixed row groups unlocking at waves 1/21/51/81/111/141/171, priced as multiples of the
    wave-money base — cost_multiplier is exactly that multiple, verbatim.

WHAT IS VERBATIM AND WHAT IS NOT — read before retuning:

  - Tier odds are their 1024-roll windows as flat tier curves: 768/195/48/12/1
    (75% / 19.0% / 4.7% / 1.2% / 0.1%). Their looping LUCK upgrade is NOT mirrored — we have no
    luck stat to hang it on; recorded divergence, revisit if runs feel flat.
  - Their per-item healing weight FUNCTIONS became `scaled_by` conditions (injured/fainted) with
    the function's base multiplier as the weight. The piecewise shapes (min(3,...) caps, HP-ratio
    thresholds) are collapsed to linear scaling — the property preserved is the one that matters:
    a full-health party is never offered potions, a standing party never sees revives.
  - DROPPED entries shift their tier-share onto the survivors. That is inherent to dropping and
    it means our COMMON is candy/ball-heavier than theirs. The DROPPED table below says why each
    is out; anything new in a re-extraction that is neither mapped nor dropped fails this script.

GAME-CONTENT KNOBS (operator's, per the authorship rule): TM_MOVES below is a curated starter set,
not an extraction — their TM tiers derive from per-species learnsets and did not survive the trip.
Edit freely; everything else should be retuned by editing the extraction or the dispositions, not
the emitted JSON.

INSTALL: the output pack defines the same `cobblemon_roguelite:default` table ids the smoke pack
does. BOTH INSTALLED = LAST-ENABLED WINS (the AllTheMons/spawn-nerfs lesson). Either delete the
smoke pack's reward_tables/ and shop_tables/ or `/datapack enable` this pack last. Directory name
must NOT start with `server-` (deploy prune gotcha, see gen_roguelite_smoketest.py).
"""

import argparse
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "cobblemon_roguelite"
DEFAULT_OUT = os.path.join(ROOT, "ops/roguelite-tables-pack")
POOLS = os.path.join(ROOT, "ops/pokerogue-modifier-pools.json")
INSTALL_DIR_NAME = "roguelite-tables"

# Their tier windows out of randSeedInt(1024): >255 COMMON, 61..255 GREAT, 13..60 ULTRA,
# 1..12 ROGUE, 0 MASTER. Flat curves because their odds do not move with wave — depth enters
# through min_wave gates and the luck loop we do not have.
TIERS = [("common", 768), ("great", 195), ("ultra", 48), ("rogue", 12), ("master", 1)]

# Ball rewards are 5 balls per pick in PokéRogue (1 for Master). Cobblemon id spellings verified
# against the repo's item list 2026-07-31 — note x_defence / x_special_attack / charcoal_stick.
def bag(item, count=1):
    return {"type": "item", "item": f"cobblemon:{item}", "count": count}

def held(item):
    return {"type": "held_item", "item": f"cobblemon:{item}"}

def tm(move):
    return {"type": "move", "move": move}

# CURATED, NOT EXTRACTED — their TM tiers are per-species learnset partitions with no portable
# list. A starter spread per tier; the operator owns this.
TM_MOVES = {
    "common": ["facade", "rocktomb", "aerialace", "swift", "protect"],
    "great": ["dig", "icywind", "thunderwave", "bulkup", "calmmind"],
    "ultra": ["earthquake", "icebeam", "thunderbolt", "flamethrower", "shadowball"],
}

# The 18 attack type boosters, all stock Cobblemon items at Showdown's own 1.2x.
TYPE_BOOSTERS = [
    "silk_scarf", "charcoal_stick", "mystic_water", "magnet", "miracle_seed", "never_melt_ice",
    "black_belt", "poison_barb", "soft_sand", "sharp_beak", "twisted_spoon", "silver_powder",
    "hard_stone", "spell_tag", "dragon_fang", "black_glasses", "metal_coat", "fairy_feather",
]

BERRIES = ["sitrus_berry", "lum_berry", "leppa_berry"]
MINTS = ["adamant_mint", "modest_mint", "jolly_mint", "timid_mint", "careful_mint", "bold_mint"]
STONES = [
    "fire_stone", "water_stone", "thunder_stone", "leaf_stone", "moon_stone",
    "sun_stone", "dusk_stone", "dawn_stone", "ice_stone", "shiny_stone",
]


def split(prefix, tier, total_weight, items, reward_of, min_wave=1, note=None):
    """One PokéRogue generator entry (e.g. BERRY, MINT) fanned into per-item entries sharing its
    weight. Their generator rolls the sub-item after the tier draw; our schema has one layer, so
    the fan-out IS the sub-roll, at equal sub-odds (their sub-odds are in the unresolved list)."""
    share = total_weight / len(items)
    return [
        {"id": f"{prefix}_{item.split(':')[-1]}" if prefix else item, "tier": tier,
         "weight": round(share, 4), "min_wave": min_wave, "reward": reward_of(item),
         **({"_note": note} if note else {})}
        for item in items
    ]


def entry(eid, tier, weight, reward, scaled_by=None, min_wave=1, max_wave=None, note=None):
    out = {"id": eid, "tier": tier, "weight": weight, "min_wave": min_wave, "reward": reward}
    if max_wave is not None:
        out["max_wave"] = max_wave
    if scaled_by:
        out["scaled_by"] = scaled_by
    if note:
        out["_note"] = note
    return out


def credits(multiplier):
    """Their MoneyRewardModifierType: money as a multiple of the wave-money formula. Our `credits`
    reward resolves through the same shared curve (WaveMoneyCurve) at the wave it is granted."""
    return {"type": "credits", "multiplier": multiplier}


# ── The free pick: their PLAYER pool through the §2.34 dispositions ─────────────────
# Weights are their constants; `scaled_by` carries their weight-function multiplier.

REWARD_ENTRIES = (
    [
        # COMMON — their windowed 75%.
        entry("poke_ball", "common", 6, bag("poke_ball", 5), note="theirs: 5x per pick, capped stock ignored"),
        entry("rare_candy", "common", 2, {"type": "level", "amount": 1}),
        entry("potion", "common", 3, bag("potion"), scaled_by="injured",
              note="theirs: 3 x min(3, members >=10HP lost) — collapsed to linear injured-scaling"),
        entry("super_potion", "common", 1, bag("super_potion"), scaled_by="injured"),
        entry("ether", "common", 3, bag("ether"),
              note="theirs scales by PP depletion; no PP condition exists, flat is closer than injured"),
        entry("max_ether", "common", 1, bag("max_ether")),
    ]
    # TEMP_STAT_STAGE_BOOSTER: ruled 2026-07-31 — EV upgrades instead of X items. X items are
    # temporary stage boosts PokéRogue re-rolls every fight; in a run our party persists, so the
    # durable equivalent (small EV grants, §2.34's accepted vitamin reshape) reads better and the
    # X-item pick stops competing with the run bag. Their weight 4 split across the six EV stats.
    + [
        entry(f"ev_{stat}", "common", round(4 / 6, 4), {"type": "ev", "stat": stat, "amount": 4})
        for stat in ["hp", "attack", "defence", "special_attack", "special_defence", "speed"]
    ]
    + split(None, "common", 2, BERRIES, held, note="BERRY generator; sub-odds unresolved, split evenly")
    + split("tm_common", "common", 2, TM_MOVES["common"], tm, note="CURATED — see TM_MOVES")
    + [
        # GREAT — 19.0%.
        entry("great_ball", "great", 6, bag("great_ball", 5)),
        entry("pp_up", "great", 2, bag("pp_up")),
        entry("full_heal", "great", 2, bag("full_heal"),
              note="theirs: 6 x statused count; no status condition — flat, tuned low"),
        entry("revive", "great", 9, bag("revive"), scaled_by="fainted",
              note="theirs: 9 x min(3, fainted)"),
        entry("max_revive", "great", 3, bag("max_revive"), scaled_by="fainted"),
        entry("hyper_potion", "great", 3, bag("hyper_potion"), scaled_by="injured"),
        entry("max_potion", "great", 1, bag("max_potion"), scaled_by="injured"),
        entry("full_restore", "great", 1, bag("full_restore"), scaled_by="injured",
              note="theirs: floor((hp+status)/2) — injured is the expressible half"),
        entry("elixir", "great", 3, bag("elixir")),
        entry("max_elixir", "great", 1, bag("max_elixir")),
        entry("dire_hit", "great", 4, bag("dire_hit")),
        entry("soothe_bell", "great", 2, held("soothe_bell"),
              note="friendship feeds §2.15 candy via creditWaveFriendship"),
        entry("nugget", "great", 5, credits(1.0), max_wave=198,
              note="MoneyRewardModifierType 1x the wave-money formula; their classic wave-199 stop"),
    ]
    + split("tm_great", "great", 3, TM_MOVES["great"], tm, note="CURATED — see TM_MOVES")
    + [
        entry("base_stat_protein", "great", 1, {"type": "ev", "stat": "attack", "amount": 10}),
        entry("base_stat_iron", "great", 1, {"type": "ev", "stat": "defence", "amount": 10}),
        entry("base_stat_calcium", "great", 0.5, {"type": "ev", "stat": "special_attack", "amount": 10}),
        entry("base_stat_zinc", "great", 0.5, {"type": "ev", "stat": "special_defence", "amount": 10}),
        # BASE_STAT_BOOSTER w3 fanned across the vitamins (their sub-roll is per-stat); §2.34's
        # accepted magnitude gap: EVs, not base-stat multipliers.
    ]
    + split(None, "great", 4, STONES, bag, min_wave=15,
            note="EVOLUTION_ITEM: theirs ramps min(ceil(wave/15),8); flattened at 4 behind a wave-15 gate")
    + [
        # ULTRA — 4.7%.
        entry("ultra_ball", "ultra", 15, bag("ultra_ball", 5)),
        entry("pp_max", "ultra", 3, bag("pp_max")),
    ]
    + split(None, "ultra", 4, MINTS, bag,
            note="MINT generator fanned into the six competitive mints as usable items")
    + [
        entry("eviolite", "ultra", 10, held("eviolite"),
              note="theirs gates on unlock+NFE-in-party; ours is always live — evolution staging (§2.30) keeps NFEs common"),
        entry("rarer_candy", "ultra", 4, {"type": "level", "amount": 2},
              note="RESHAPE: their exact rarer-candy effect is in the unresolved list; two levels stands in"),
        entry("quick_claw", "ultra", 3, held("quick_claw")),
        entry("wide_lens", "ultra", 7, held("wide_lens")),
        entry("big_nugget", "ultra", 12, credits(2.5), max_wave=198,
              note="MoneyRewardModifierType 2.5x the wave-money formula; their classic wave-199 stop"),
    ]
    + split("boost", "ultra", 9, TYPE_BOOSTERS, held,
            note="ATTACK_TYPE_BOOSTER w9 fanned across all 18 types")
    + split("tm_ultra", "ultra", 11, TM_MOVES["ultra"], tm, note="CURATED — see TM_MOVES")
    + [
        # ROGUE — 1.2%.
        entry("premium_ball", "rogue", 16, bag("quick_ball", 5),
              note="RESHAPE: no Rogue Ball exists; Quick Ball is the strongest stock general-purpose ball"),
        entry("leftovers", "rogue", 3, held("leftovers")),
        entry("shell_bell", "rogue", 3, held("shell_bell")),
        entry("scope_lens", "rogue", 4, held("scope_lens")),
        entry("focus_band", "rogue", 5, held("focus_band")),
        entry("kings_rock", "rogue", 3, held("kings_rock")),
        entry("relic_gold", "rogue", 2, credits(10.0), max_wave=198,
              note="MoneyRewardModifierType 10x the wave-money formula; their classic wave-199 stop"),
        entry("ability_patch", "rogue", 6, {"type": "ability"}, max_wave=189,
              note="RESHAPE of ABILITY_CHARM: direct HA grant instead of odds boost; their weight and their wave-189 stop (skipInClassicAfterWave)",
              ),
        # MASTER — 0.1%. Their master tier is mostly meta (charms, vouchers); the ball is what maps.
        entry("master_ball", "master", 24, bag("master_ball", 1)),
    ]
)

# Their-id → why it is not in the table. Audited against the extraction at generation time so a
# re-extract that grows the pool fails loudly instead of silently under-mirroring.
DROPPED = {
    "LURE": "no wild-lure system", "SUPER_LURE": "no wild-lure system", "MAX_LURE": "no wild-lure system",
    "SACRED_ASH": "no such Cobblemon item; nearest is max_revive which already maps",
    # NUGGET/BIG_NUGGET/RELIC_GOLD restored 2026-07-31 as `credits` rewards (multiplier of the
    # shared wave-money curve, exactly their MoneyRewardModifierType shape). The remaining money
    # modifiers stay out: they are passive %-boosts, not one-shot grants.
    "NUGGET": None, "BIG_NUGGET": None, "RELIC_GOLD": None,
    "AMULET_COIN": "passive money %-boost, no channel", "GOLDEN_PUNCH": "passive money-on-hit, no channel",
    "COIN_CASE": "passive money interest, no channel",
    "LOCK_CAPSULE": "0-weight in classic anyway; reroll-lock UI does not exist",
    "MAP": "no biome-choice mechanic", "IV_SCANNER": "IVs visible via Cobblemon UI",
    "VOUCHER": "egg-gacha meta currency", "VOUCHER_PLUS": "egg-gacha meta", "VOUCHER_PREMIUM": "egg-gacha meta",
    "CATCHING_CHARM": "0-weight in classic", "SHINY_CHARM": "meta odds — Unchained streaks own shiny odds",
    "HEALING_CHARM": "passive %-boost, no channel",
    # Ruled 2026-07-31 (playtest): their EXP items are TEAM-WIDE PERMANENT run buffs — Share gives
    # every party member a cut of participants' EXP, Charm raises total EXP % — not held items.
    # The first cut shipped them as cobblemon:exp_share/lucky_egg held items, which misrepresents
    # both. Out until the run-passive mechanism exists (run-scoped stacks + an EXP-event multiplier).
    "EXP_CHARM": "team-wide permanent buff — needs the run-passive mechanism",
    "SUPER_EXP_CHARM": "team-wide permanent buff — needs the run-passive mechanism",
    "EXP_SHARE": "team-wide permanent buff — needs the run-passive mechanism",
    "MEMORY_MUSHROOM": "no move-relearn channel; TM entries cover move acquisition",
    "PP_UP": None, "PP_MAX": None,  # mapped
    "SPECIES_STAT_BOOSTER": "species-conditional (Light Ball etc.); needs a species-in-party condition first",
    "RARE_SPECIES_STAT_BOOSTER": "species-conditional", "LEEK": "species-conditional",
    "SOUL_DEW": "Latias/Latios-only in Showdown, species-conditional",
    "TOXIC_ORB": "ability-conditional in theirs; junk pick without the condition", "FLAME_ORB": "ability-conditional",
    "MYSTICAL_ROCK": "ability-conditional weather item",
    "REVIVER_SEED": "§2.33 tiered-item harness not built yet", "MULTI_LENS": "§2.33 harness not built",
    "BATON": "their custom switch-item, no Showdown equivalent", "GRIP_CLAW": "Cobblemon blocks item theft (§2.34)",
    "MINI_BLACK_HOLE": "Cobblemon blocks item theft", "BERRY_POUCH": "redundant — consumed:false is free (§2.34)",
    "TERA_SHARD": "§2.5 in-run gimmick wiring undecided", "TERA_ORB": "§2.5 undecided",
    "MEGA_BRACELET": "§2.5 undecided", "DYNAMAX_BAND": "§2.5 undecided",
    "DNA_SPLICERS": "fusion out of scope", "EVOLUTION_ITEM": None, "RARE_EVOLUTION_ITEM": "folded into the stone set",
    "FORM_CHANGE_ITEM": "form items are monument/craft-ban territory", "RARE_FORM_CHANGE_ITEM": "same",
    "EVIOLITE": None, "MINT": None, "BERRY": None, "TM_COMMON": None, "TM_GREAT": None, "TM_ULTRA": None,
    "POKEBALL": None, "GREAT_BALL": None, "ULTRA_BALL": None, "ROGUE_BALL": None, "MASTER_BALL": None,
    "RARE_CANDY": None, "RARER_CANDY": None, "POTION": None, "SUPER_POTION": None, "HYPER_POTION": None,
    "MAX_POTION": None, "FULL_RESTORE": None, "FULL_HEAL": None, "REVIVE": None, "MAX_REVIVE": None,
    "ETHER": None, "MAX_ETHER": None, "ELIXIR": None, "MAX_ELIXIR": None, "DIRE_HIT": None,
    "TEMP_STAT_STAGE_BOOSTER": None, "SOOTHE_BELL": None, "BASE_STAT_BOOSTER": None,
    "ABILITY_CHARM": None, "LEFTOVERS": None, "SHELL_BELL": None, "SCOPE_LENS": None,
    "FOCUS_BAND": None, "KINGS_ROCK": None, "QUICK_CLAW": None, "WIDE_LENS": None,
    "ATTACK_TYPE_BOOSTER": None, "CANDY_JAR": "candy meta lives in §2.15's own economy",
}

# ── The paid row: their shop, verbatim ──────────────────────────────────────────────
# (item, cost multiple, row-unlock wave). Rows unlock at ceil((wave+10)/30) so row N opens at
# 30N-29 for N>=2 — i.e. 21/51/81/111/141/171 — matching their cumulative catalog.
SHOP_ROWS = [
    ("potion", 0.2, 1), ("ether", 0.4, 1), ("revive", 2.0, 1),
    ("super_potion", 0.45, 21), ("full_heal", 1.0, 21),
    ("elixir", 1.0, 51), ("max_ether", 1.0, 51),
    ("hyper_potion", 0.8, 81), ("max_revive", 2.75, 81),
    # MEMORY_MUSHROOM (4x, row 4) dropped: no relearn channel.
    ("max_potion", 1.5, 111), ("max_elixir", 2.5, 111),
    ("full_restore", 2.25, 141),
    # SACRED_ASH (10x, row 7) dropped: no such item.
]


def write(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")
    print(f"  wrote {os.path.relpath(path, ROOT)}")


def audit_against_extraction():
    """Every PLAYER-pool id must be mapped or deliberately dropped. A re-extract that adds a
    modifier neither list knows about should stop the build, not quietly under-mirror."""
    with open(POOLS, encoding="utf-8") as handle:
        pools = json.load(handle)
    their_ids = {e["id"] for tier in pools["player_pool"].values() for e in tier}
    unaccounted = sorted(their_ids - set(DROPPED))
    if unaccounted:
        raise SystemExit(f"unmapped PLAYER-pool ids (map or add to DROPPED): {unaccounted}")
    return pools.get("source_commit_or_date", "unknown")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", default=DEFAULT_OUT)
    args = parser.parse_args()

    source = audit_against_extraction()
    ids = [e["id"] for e in REWARD_ENTRIES]
    assert len(ids) == len(set(ids)), f"duplicate entry ids: {sorted(i for i in ids if ids.count(i) > 1)}"

    data = os.path.join(args.out, "data", NS, "roguelite")
    note = [
        f"Transcribed from PokéRogue ({source}) via ops/pokerogue-modifier-pools.json —",
        "regenerate with ops/gen_pokerogue_reward_tables.py; do not hand-edit (edit the script).",
        "Tier odds are their 1024-roll windows; luck upgrades not mirrored (no luck stat).",
        "_note fields document per-entry reshapes; the loader ignores underscore-prefixed keys.",
    ]

    print("assembling the PokéRogue-mirrored tables pack")
    write(os.path.join(args.out, "pack.mcmeta"), {
        "pack": {"pack_format": 48, "description": "roguelite reward/shop tables - PokeRogue transcription"},
    })

    write(os.path.join(data, "reward_tables", "default.json"), {
        "_comment": note,
        "tiers": [{"id": tid, "curve": [{"wave": 1, "weight": w}]} for tid, w in TIERS],
        "entries": REWARD_ENTRIES,
    })

    write(os.path.join(data, "shop_tables", "default.json"), {
        "_comment": note + [
            "cost_multiplier x wave-money base, their multiples verbatim; min_wave = their row",
            "unlock (21/51/81/111/141/171). Boss waves close the shop in code (ShopStock).",
        ],
        "entries": [
            {"id": item, "cost_multiplier": mult, "min_wave": wave,
             "reward": bag(item)}
            for item, mult, wave in SHOP_ROWS
        ],
    })

    dropped_real = {k: v for k, v in DROPPED.items() if v}
    print(f"\n  {len(REWARD_ENTRIES)} reward entries, {len(SHOP_ROWS)} shop rows, "
          f"{len(dropped_real)} of their modifiers deliberately dropped")
    print("\nINSTALL (replaces the smoke pack's reward/shop tables — see module docstring):")
    print(f"  scp -r {os.path.relpath(args.out, ROOT)} "
          f"cobblemon:/opt/cobblemon-dev/world/datapacks/{INSTALL_DIR_NAME}")
    print("  # then delete roguelite-smoketest/data/*/roguelite/{reward_tables,shop_tables}")
    print("  # or /datapack enable this pack LAST — same-id tables resolve last-enabled-wins.")
    print("  ssh cobblemon 'sudo systemctl restart cobblemon-dev'")


if __name__ == "__main__":
    main()
