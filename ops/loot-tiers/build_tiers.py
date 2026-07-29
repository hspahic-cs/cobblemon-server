#!/usr/bin/env python3
"""Build the server-wide loot tier list.

Reads every place the server already grants or prices an item, assigns each item
a tier T0-T5, and writes:

    ops/loot-tiers/tiers.json   machine-readable source of truth
    docs/loot-tiers.md          human-readable reference

Tiers come from three things, in priority order:

    1. ops/loot-tiers/overrides.json   explicit pins (judgment calls)
    2. CATEGORY_RULES below            pattern rules over item ids
    3. namespace default               last-resort fallback

Evidence (which crate/chest/trainer table actually grants an item, and at what
rate) is collected and attached to every row so a tier can be argued with rather
than taken on faith. Evidence does NOT auto-assign tiers -- drop rate measures
how often we *currently* give something out, which is the thing we're trying to
sanity-check, so using it as the tier would be circular.

Usage:  python3 ops/loot-tiers/build_tiers.py [--check]

    --check  exit 1 if the generated files differ from what's on disk
             (for CI / pre-commit; does not write)
"""
from __future__ import annotations

import argparse
import collections
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
OVERRIDES = ROOT / "ops/loot-tiers/overrides.json"
REGISTRY = ROOT / "ops/loot-tiers/item-registry.json"
MOD_LOOT = ROOT / "ops/loot-tiers/mod-loot.json"
OUT_JSON = ROOT / "ops/loot-tiers/tiers.json"
OUT_MD = ROOT / "docs/loot-tiers.md"

OVERRIDES_DIR = ROOT / "modpack/server-overrides"
MARKET = OVERRIDES_DIR / "config/cobblemon-market/authored/items.json"
GACHA_DIR = OVERRIDES_DIR / "config/cobblemon-gacha/authored/tables"
DATAPACKS = OVERRIDES_DIR / "datapacks"

# TX (-1) is a STATUS, not a rarity: items that are intentionally not obtainable.
# Kept in the same field so one sort/filter covers everything, and rendered "TX".
DISABLED = -1

TIER_NAMES = {
    DISABLED: "Disabled",
    5: "Mythic",
    4: "Legendary",
    3: "Epic",
    2: "Rare",
    1: "Uncommon",
    0: "Common",
}
TIER_BLURB = {
    DISABLED: ("Intentionally not obtainable — recipe banned and/or stripped from loot. "
               "Never use as a reward. If one of these is still dropping, that's a bug to fix, "
               "not a tier to change."),
    5: "Gates a box legendary or mythical, or guarantees a catch. Never a routine reward.",
    4: "Summons or permanently unlocks a legendary/forme. One-per-player scale.",
    3: "Permanent competitive power or a hard-gated component. A real chase reward.",
    2: "Strong but repeatable. Fine as the headline reward for a genuine challenge.",
    1: "Routine reward scale. Safe for regular play loops.",
    0: "Filler. Safe to hand out in bulk.",
}

# Namespaces we tier individually. simpletms is collapsed (see TM_* below).
NAMESPACES = ("cobblemon", "mega_showdown", "legendarymonuments", "minecraft")

# ---------------------------------------------------------------- category rules
# (regex over the item id, tier, rationale). First match wins, so order matters:
# put specific patterns above general ones.
CATEGORY_RULES: list[tuple[str, int, str]] = [
    # --- consumable healing / status: bulk filler
    (r":(potion|super_potion|hyper_potion|max_potion|full_restore)$", 0, "Healing consumable"),
    (r":(revive|max_revive)$", 0, "Revive consumable"),
    (r":(ether|max_ether|elixir|max_elixir)$", 0, "PP consumable"),
    (r":(antidote|awakening|burn_heal|ice_heal|paralyze_heal|full_heal)$", 0, "Status heal"),
    (r":(remedy|fine_remedy|superb_remedy|energy_root|medicinal_leek)$", 0, "Herbal heal"),
    # --- currency & basic balls
    (r":relic_coin(_pouch|_sack)?$", 0, "Currency"),
    (r":(poke_ball|great_ball|ultra_ball)$", 0, "Basic ball"),
    (r":ancient_\w*ball$", 0, "Hisuian basic ball"),
    # --- exp candy: size-graded
    (r":exp_candy_(xs|s|m)$", 0, "Small exp candy"),
    # --- vanilla bulk materials
    (r"^minecraft:(cobblestone|dirt|sand|gravel|stick|string|coal|iron_nugget|gold_nugget|"
     r"copper_ingot|iron_ingot|gold_ingot|quartz|lapis_lazuli|emerald|redstone|flint|clay_ball|"
     r"bone|leather|feather|wheat|bread|apple|glass|torch|paper|charcoal)$", 0, "Vanilla bulk material"),
    (r"^minecraft:\w*(planks|log|stone|bricks|slab|stairs|wall|fence|door|sign)$", 0, "Vanilla building block"),
    # --- specialty balls: routine
    (r":(azure|dive|dusk|fast|heavy|nest|net|quick|timer|luxury|friend|moon|love|lure|level|"
     r"safari|sport|premier|repeat|cherish|park)_ball$", 1, "Specialty ball"),
    # --- routine competitive filler
    (r":\w+_mint(_leaf)?$", 1, "Nature mint"),
    (r":\w*mint_seeds$", 1, "Mint seed"),
    (r":\w+_apricorn(_seed)?$", 1, "Apricorn"),
    (r":(hp_up|protein|iron|calcium|zinc|carbos)$", 1, "Vitamin"),
    (r":\w+_feather$", 1, "EV feather"),
    (r":(health|quick|mighty|smart|tough|courage)_candy$", 3, "IV candy — raises a stat's effective IV by 1; heavy player demand"),
    (r":\w*fossil\w*$", 1, "Fossil"),
    (r":\w+_mulch$", 1, "Mulch"),
    (r":power_(anklet|band|belt|bracer|lens|weight)$", 1, "EV training item"),
    (r":exp_candy_l$", 1, "Mid exp candy"),
    (r":\w+_tumblestone(_cluster)?$", 1, "Crafting material"),
    (r":\w+_berry$", 1, "Berry"),
    # --- type-flavoured power: rare band
    (r":\w+_gem$", 3, "Type gem — one-shot damage boost"),
    (r":\w+_tera_shard$", DISABLED, "Tera shard — Tera is banned on this server"),
    (r":\w+ium_z$", DISABLED, "Z-crystal — disabled on this server"),
    (r":blank_z$", DISABLED, "Blank Z-crystal — disabled on this server"),
    (r":z_?(ring|power_ring)(_\w+)?$", DISABLED, "Z-Ring — enabler for the disabled Z-crystals"),
    (r":\w+_z_(ring|power_ring)$", DISABLED, "Z-Ring — enabler for the disabled Z-crystals"),
    (r":\w*z_ring\w*$", DISABLED, "Z-Ring — enabler for the disabled Z-crystals"),
    (r":tera_(orb|pouch\w*)$", DISABLED, "Tera enabler — Tera is banned on this server"),
    (r":dynamax_(band|candy)$", DISABLED, "Dynamax is disabled on this server"),
    (r":max_(soup|mushroom|honey)$", DISABLED, "Dynamax is disabled on this server"),
    (r":sweet_max_soup$", DISABLED, "Dynamax is disabled on this server"),
    (r":\w+_plate$", 2, "Arceus plate / type booster"),
    (r":\w+_memory$", 2, "Silvally memory"),
    # --- evolution items
    (r":(fire|water|thunder|leaf|moon|sun|shiny|dusk|dawn|ice)_stone(_block)?$", 0, "Evolution stone"),
    (r":(link_cable|dubious_disc|dragon_scale|deep_sea_tooth|deep_sea_scale|electirizer|"
     r"magmarizer|protector|reaper_cloth|prism_scale|whipped_dream|sachet|oval_stone|"
     r"chipped_pot|cracked_pot|masterpiece_teacup|black_augurite|auspicious_armor|"
     r"malicious_armor|metal_alloy|galarica_cuff|galarica_wreath)$", 2, "Evolution item"),
    # --- premium competitive held items
    (r":(choice_band|choice_scarf|choice_specs|life_orb|leftovers|focus_sash|assault_vest|"
     r"eviolite|heavy_duty_boots|covert_cloak|booster_energy|clear_amulet|loaded_dice|"
     r"expert_belt|rocky_helmet|air_balloon|weakness_policy|throat_spray|blunder_policy|"
     r"punching_glove|ability_shield|mirror_herb)$", 2, "Premium competitive held item"),
    # --- LegendaryMonuments families, classified from the mod's own tooltips
    #     (e.g. entei_treat: "can be used to summon Entei at the Burned Tower")
    (r"^legendarymonuments:\w+_treat$", 4, "Summons a legendary at its shrine"),
    (r"^legendarymonuments:(arctic|molten|zap|magma)_stone$", 4, "Summons a legendary (bird / Heatran)"),
    (r"^legendarymonuments:(space|time|antimatter)_globe$", 4, "Azure Flute component — the Arceus path"),
    (r"^legendarymonuments:(gs_ball|tuft_of_mew_hair)$", 4, "Summons a mythical"),
    (r"^legendarymonuments:\w+_seal$", 3, "Locates a shrine"),
    (r"^legendarymonuments:\w+_tablet$", 3, "Regi chamber gate"),
    (r"^legendarymonuments:galarian_urn_of_\w+$", 3, "Legendary-adjacent gate component"),
    (r"^legendarymonuments:(uxie_claw|azelf_fang|mesprit_plume|fragmented_red_chain)$", 3,
     "Red Chain component — crafts into a T4"),
    (r"^legendarymonuments:\w+_key$", 3, "Chamber / monument gate"),
    (r"^legendarymonuments:\w+_golem_ingot$", 2, "Golem crafting material"),
    (r"^legendarymonuments:\w+_pauldron$", 2, "Regi armour component"),
    (r"^legendarymonuments:special_(leafy_greens|meat_chunks|spices)$", 1,
     "Curry ingredient — a Swords of Justice favourite"),
    (r"^legendarymonuments:(poketreat_box|dream_string|clear_bell|cosmic_bag|galar_particle)$", 1,
     "Utility / crafting material"),
    # --- everything else held/util
    (r"^cobblemon:", 1, "Standard held / utility item"),
    (r"^mega_showdown:", 2, "Mega Showdown item (type/forme adjacent)"),
    (r"^legendarymonuments:", 3, "Monument item — treat as gated until pinned otherwise"),
    (r"^minecraft:", 0, "Vanilla item"),
]

# SimpleTMs: 631 near-identical ids, collapsed to two rules rather than 631 rows.
# The premium list is CURATED (competitive relevance), not derived -- there is no
# move-power data in the TM item ids to derive it from.
TM_PREMIUM = {
    "earthquake", "closecombat", "swordsdance", "nastyplot", "calmmind", "dragondance",
    "stealthrock", "spikes", "toxicspikes", "willowisp", "thunderwave", "knockoff",
    "uturn", "voltswitch", "flipturn", "recover", "roost", "substitute", "protect",
    "icebeam", "flamethrower", "thunderbolt", "shadowball", "focusblast", "surf",
    "dracometeor", "hurricane", "leechseed", "defog", "rapidspin", "trickroom",
    "psyshock", "moonblast", "playrough", "ironhead", "scald", "hydropump",
}
TM_PREMIUM_TIER = 2
TM_DEFAULT_TIER = 1


def tlabel(t: int) -> str:
    """T5..T0, or TX for the disabled bucket."""
    return "TX" if t == DISABLED else f"T{t}"


def load_json(p: pathlib.Path):
    try:
        return json.loads(p.read_text())
    except Exception:
        return None


# ---------------------------------------------------------------- evidence
def per_chest(weight: int, total: int, rolls) -> float | None:
    if not total or not isinstance(rolls, int):
        return None
    p = weight / total
    return (1 - (1 - p) ** rolls) * 100


def collect_evidence() -> dict[str, list[dict]]:
    ev: dict[str, list[dict]] = collections.defaultdict(list)
    ev_tables: set[str] = set()

    # --- gacha crates: explicit tier + weight
    for f in sorted(GACHA_DIR.glob("*.json")):
        d = load_json(f)
        if not d:
            continue
        crate = d.get("tier", f.stem.upper())
        for e in d.get("entries", []):
            for it in e.get("items", []):
                # A single item ("id"), or a random-pick bundle ("ids": [...]).
                # Missing the plural form silently drops whole categories -- the
                # evolution stones, nature mints and most Z-crystals all live in
                # random_item bundles.
                ids = [it["id"]] if isinstance(it.get("id"), str) else []
                ids += [i for i in (it.get("ids") or []) if isinstance(i, str)]
                bundle = len(ids) > 1
                for iid in ids:
                    detail = f"{e.get('lootTier','?')} band, {e.get('weightPct')}%"
                    if bundle:
                        detail += f" (1-of-{len(ids)} random)"
                    ev[iid].append({
                        "source": f"crate:{crate.lower()}",
                        "detail": detail,
                        # a 1-of-N bundle grants any single member at rate/N
                        "rate": round(e["weightPct"] / len(ids), 3)
                        if isinstance(e.get("weightPct"), (int, float)) else None,
                    })

    # --- loot tables in datapacks (chest + trainer)
    for f in sorted(DATAPACKS.rglob("*.json")):
        if "loot_table" not in f.as_posix():
            continue
        d = load_json(f)
        if not isinstance(d, dict) or "pools" not in d:
            continue
        mns = re.search(r"/datapacks/[^/]+/data/([a-z0-9_]+)/loot_table/(.+)\.json$", f.as_posix())
        label = f"{mns.group(1)}/{mns.group(2)}" if mns else f.parent.name + "/" + f.stem
        ev_tables.add(label)
        for pool in d.get("pools") or []:
            entries = pool.get("entries") or []
            total = sum(e.get("weight", 1) for e in entries)
            rolls = pool.get("rolls")
            for e in entries:
                iid = e.get("name")
                if not isinstance(iid, str) or ":" not in iid:
                    continue
                pc = per_chest(e.get("weight", 1), total, rolls)
                ev[iid].append({
                    "source": f"loot:{label}",
                    "detail": f"w={e.get('weight',1)}/{total}, rolls={rolls}",
                    "rate": round(pc, 2) if pc is not None else None,
                })

    # --- mod-side loot tables (ruins, archaeology, trainer loot, un-overridden
    #     monument chests). Without these, evidence only covers tables we
    #     override, and an item sourced purely from ruins reads as "not granted
    #     anywhere" -- which is how 9 type gems were wrongly reported as having
    #     no source. Skip any table we override; ours is the live version.
    ours = {lbl for lbl in ev_tables}
    for iid, srcs in ((load_json(MOD_LOOT) or {}).get("sources") or {}).items():
        for sc in srcs:
            if sc["t"] in ours:
                continue
            ev[iid].append({
                "source": f"mod:{sc['t']}",
                "detail": "mod loot table",
                "rate": sc.get("r"),
            })

    # --- market: purchasable at all is itself a rarity ceiling
    mk = load_json(MARKET) or {}
    for iid, meta in mk.items():
        ev[iid].append({
            "source": "market",
            "detail": f"${meta.get('baseBuyPrice',0):,} ({meta.get('vendorTag') or 'untagged'})",
            "rate": None,
        })
    return ev


# ---------------------------------------------------------------- universe
# Only pull ids out of keys that actually hold an ITEM. A blanket regex over the
# file text also scoops up structure ids, biome tags, block ids and sound events
# -- e.g. legendarymonuments:spear_pillar is a structure, not something you can
# put in a chest.
ITEM_KEYS = {"item", "signatureItem", "heldItem"}
# Paths that never contain item ids, only worldgen/registry references.
SKIP_PATH = re.compile(r"/(worldgen|structure|tags|advancement|dimension|biome)/")


def walk_items(node, out: set[str], in_items_list: bool = False) -> None:
    """Recursively pull item ids out of item-bearing keys only."""
    if isinstance(node, dict):
        # loot-table entry: {"type": "minecraft:item", "name": "<id>"}
        if node.get("type") in ("minecraft:item", "item") and isinstance(node.get("name"), str):
            out.add(node["name"])
        # recipe result: {"result": {"id": "<id>"}}
        res = node.get("result")
        if isinstance(res, dict) and isinstance(res.get("id"), str):
            out.add(res["id"])
        # gacha random_item bundle: {"ids": ["<id>", ...]}
        if isinstance(node.get("ids"), list):
            out.update(i for i in node["ids"] if isinstance(i, str) and ":" in i)
        for k, v in node.items():
            # heldItem may be a single id OR a list of candidates to pick from.
            # Note: list entries are sometimes bare ("dark_gem" with no namespace);
            # those are skipped rather than guessed at, since the resolving
            # namespace is ambiguous.
            if k in ITEM_KEYS:
                if isinstance(v, str) and ":" in v:
                    out.add(v)
                elif isinstance(v, list):
                    out.update(i for i in v if isinstance(i, str) and ":" in i)
            # gacha: {"items": [{"id": "<id>"}]}
            elif k == "items" and isinstance(v, list):
                for e in v:
                    if isinstance(e, dict) and isinstance(e.get("id"), str):
                        out.add(e["id"])
                    walk_items(e, out, True)
            else:
                walk_items(v, out, in_items_list)
    elif isinstance(node, list):
        for v in node:
            walk_items(v, out, in_items_list)


def collect_universe() -> set[str]:
    # Seed from the mods' actual item registry, not just what our configs happen
    # to reference. Reference-only seeding silently omitted 381 real items --
    # including 10 Tera shards, every Z-Ring, and the Tera Orb -- which meant
    # "disabled" categories looked complete while whole variants sat untiered.
    out: set[str] = set((load_json(REGISTRY) or {}).get("items", {}))
    for f in OVERRIDES_DIR.rglob("*.json"):
        if SKIP_PATH.search(f.as_posix()):
            continue
        d = load_json(f)
        if d is None:
            continue
        if f == MARKET and isinstance(d, dict):
            out |= set(d)
            continue
        walk_items(d, out)
    return {i for i in out if ":" in i and i.split(":")[0] in NAMESPACES}


def classify(iid: str, overrides: dict, market: dict) -> tuple[int, str, str]:
    """Assign a tier. Purchasability is deliberately NOT used to cap the tier.

    An earlier version capped any purchasable item at T1, on the theory that if
    you can buy it, it isn't a chase item. That's wrong, and consistently so:
    heavy consumption creates scarcity even when an item is on the shelf. Exp
    candies are the clearest case -- they're purchasable AND among the few
    sellable items AND feedstock for the EV candies, so demand keeps them
    genuinely valuable. Plates are stocked but still Rare. Shelf price is
    recorded as evidence and left for a human to weigh.
    """
    if iid in overrides:
        tier, why = overrides[iid]
        return tier, why, "override"
    for pat, tier, why in CATEGORY_RULES:
        if re.search(pat, iid):
            return tier, why, "rule"
    return 0, "Unmatched — defaulted", "default"


def build() -> tuple[dict, str]:
    raw = load_json(OVERRIDES) or {}
    overrides = {k: v for k, v in raw.items() if not k.startswith("_")}

    market = load_json(MARKET) or {}
    ev = collect_evidence()

    universe = collect_universe() | {k for k in ev if k.split(":")[0] in NAMESPACES}
    universe |= set(overrides)
    universe = {u for u in universe if u.split(":")[0] in NAMESPACES}

    rows = []
    for iid in sorted(universe):
        tier, why, how = classify(iid, overrides, market)
        rows.append({
            "id": iid,
            "tier": tier,
            "tierLabel": tlabel(tier),
            "tierName": TIER_NAMES[tier],
            "rationale": why,
            "assignedBy": how,
            "sources": ev.get(iid, []),
        })

    tms = {
        "note": ("631 SimpleTMs are collapsed into two rules rather than enumerated. "
                 "The premium split is curated by competitive relevance -- TM item ids "
                 "carry no move-power data to derive it from."),
        "defaultTier": TM_DEFAULT_TIER,
        "premiumTier": TM_PREMIUM_TIER,
        "premiumMoves": sorted(TM_PREMIUM),
    }

    doc = {
        "_generated": "ops/loot-tiers/build_tiers.py -- do not hand-edit; edit overrides.json or the rules",
        "tiers": {tlabel(k): {"name": v, "meaning": TIER_BLURB[k]} for k, v in sorted(TIER_NAMES.items(), reverse=True)},
        "simpletms": tms,
        "items": rows,
    }
    return doc, render_md(doc)


# ---------------------------------------------------------------- markdown
def render_md(doc: dict) -> str:
    rows = doc["items"]
    by_tier = collections.defaultdict(list)
    for r in rows:
        by_tier[r["tier"]].append(r)

    L: list[str] = []
    L.append("# Loot tiers\n")
    L.append("Canonical rarity ladder for every item the server hands out. Consult this\n"
             "when designing a new game, quest, crate, or reward so payouts stay consistent\n"
             "with what already exists.\n")
    L.append("!!! warning \"Generated file\"\n")
    L.append("    Built by `ops/loot-tiers/build_tiers.py`. Don't hand-edit — change\n"
             "    `ops/loot-tiers/overrides.json` (for a specific item) or the category\n"
             "    rules in the script (for a whole class), then re-run it.\n")

    L.append("## The ladder\n")
    L.append("| Tier | Name | Use it for |")
    L.append("|---|---|---|")
    for t in sorted(TIER_NAMES, reverse=True):
        L.append(f"| **{tlabel(t)}** | {TIER_NAMES[t]} | {TIER_BLURB[t]} |")
    L.append("")
    counts = ", ".join(f"{tlabel(t)}: {len(by_tier[t])}" for t in sorted(by_tier, reverse=True))
    L.append(f"{len(rows)} items tiered — {counts}.\n")

    L.append("## Picking a reward\n")
    L.append("Rough guidance, not a rule:\n")
    L.append("- **Daily / repeatable loop** → T0–T1. Bulk is fine.\n"
             "- **Weekly objective, gym rematch, mid-tier quest** → T1–T2, occasionally T2 as the headline.\n"
             "- **Genuine one-off challenge (tournament placing, hard boss, long questline)** → T2–T3.\n"
             "- **T4 and T5 gate legendaries.** Handing these out casually devalues the crate\n"
             "  economy and the monument hunt at the same time. Prefer a crate key instead —\n"
             "  it preserves the roll.\n")
    L.append("The Ultra crate is the rarity benchmark the tiers are calibrated against:\n"
             "jackpot band 0.8–1.6%, high band 4.9%.\n")

    for t in sorted(by_tier, reverse=True):
        L.append(f"## {tlabel(t)} — {TIER_NAMES[t]}\n")
        L.append(f"*{TIER_BLURB[t]}*\n")
        L.append("| Item | Why | Where it comes from |")
        L.append("|---|---|---|")
        for r in sorted(by_tier[t], key=lambda x: x["id"]):
            srcs = r["sources"]
            if srcs:
                shown = "; ".join(
                    f"`{s['source']}`" + (f" {s['rate']}%" if s.get("rate") is not None else "")
                    for s in srcs[:3])
                if len(srcs) > 3:
                    shown += f" +{len(srcs)-3} more"
            else:
                shown = "*not currently granted anywhere*"
            L.append(f"| `{r['id']}` | {r['rationale']} | {shown} |")
        L.append("")

    tms = doc["simpletms"]
    L.append("## SimpleTMs (collapsed)\n")
    L.append(f"{tms['note']}\n")
    L.append(f"- **Default: T{tms['defaultTier']}** — every TM not listed below.\n")
    L.append(f"- **Premium: T{tms['premiumTier']}** — {len(tms['premiumMoves'])} competitively "
             "load-bearing moves (hazards, setup, pivots, recovery, top coverage):\n")
    L.append("  " + ", ".join(f"`{m}`" for m in tms["premiumMoves"]) + "\n")

    L.append("## How tiers are assigned\n")
    L.append("In priority order:\n")
    L.append("1. **`overrides.json`** — explicit pins. Judgment calls live here.\n"
             "2. **Category rules** — patterns over item ids in `build_tiers.py`.\n"
             "3. **Namespace default** — last resort.\n")
    L.append("Drop rates are recorded as *evidence* but deliberately do **not** drive the tier.\n"
             "How often we currently hand something out is the thing we're trying to\n"
             "sanity-check against, so deriving the tier from it would be circular — a\n"
             "mispriced item would justify its own mispricing.\n")
    return "\n".join(L) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="fail if generated output is stale")
    args = ap.parse_args()

    doc, md = build()
    new_json = json.dumps(doc, indent=2) + "\n"

    if args.check:
        stale = []
        if not OUT_JSON.exists() or OUT_JSON.read_text() != new_json:
            stale.append(str(OUT_JSON.relative_to(ROOT)))
        if not OUT_MD.exists() or OUT_MD.read_text() != md:
            stale.append(str(OUT_MD.relative_to(ROOT)))
        if stale:
            print("STALE (re-run ops/loot-tiers/build_tiers.py): " + ", ".join(stale), file=sys.stderr)
            return 1
        print("loot tiers up to date")
        return 0

    OUT_JSON.write_text(new_json)
    OUT_MD.write_text(md)
    n = len(doc["items"])
    by = collections.Counter(r["tier"] for r in doc["items"])
    print(f"wrote {OUT_JSON.relative_to(ROOT)} and {OUT_MD.relative_to(ROOT)}")
    print(f"  {n} items — " + ", ".join(f"{tlabel(t)}: {by[t]}" for t in sorted(by, reverse=True)))
    unmatched = [r["id"] for r in doc["items"] if r["assignedBy"] == "default"]
    if unmatched:
        print(f"  {len(unmatched)} unmatched (defaulted to T0): {', '.join(unmatched[:15])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
