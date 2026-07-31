#!/usr/bin/env python3
"""Assemble a THROWAWAY datapack that makes the roguelite exercisable on the dev server.

    python3 ops/gen_roguelite_smoketest.py [--out DIR]

This is TEST SCAFFOLDING, not content. Every number in it was chosen to make a mechanic fire
quickly, not to be fun or balanced:

  - waves are short and bosses are frequent, so a shield and a rival turn up in minutes
  - the reward table holds ONE OF EACH RunReward type, including the bag item that is known to
    fail, because the failure path is a thing to verify rather than avoid
  - prices are small so a few waves of credits reach the shop
  - three biomes on disjoint 10-wave windows, each with its own palette, so the §2.24 rotation and
    the §2.19 re-stamp fire on a schedule you can watch rather than on a weighted roll

The real tables are a balance decision and belong to the server operator (plan §2.7). Nothing here
should ever be promoted to prod: it writes to `cobblemon_roguelite:default`, which is the id the mod
actually reads, so a real table must REPLACE these files rather than sit alongside them.

WHY IT EXISTS AT ALL: every table resolves through a named `default` id and the mod ships only
`example.json`, so on a fresh server a run refuses at its first trainer wave and both halves of the
between-wave step report "no table loaded". Without this you would be testing the refusal paths.

The trainer bands are read from ops/roguelite-generic-trainer-bands.json — the 72 generic trainers
already picked by hand — so the smoke test uses the same cast the real roster will.
"""

import argparse
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "cobblemon_roguelite"
DEFAULT_OUT = os.path.join(ROOT, "ops/roguelite-smoketest-pack")

# The directory name this must be installed under on a server, and it MUST NOT start with `server-`.
#
# Learned by breaking the dev deploy on 2026-07-30. deploy-dev.yml has a "Prune retired server-*
# datapacks" step that deletes any `server-*` pack the build does not ship, and it runs as `deployer`
# while a hand-scp'd pack is owned by `sysadmin` — so the rm fails with Permission denied, the step
# exits 1, and the whole deploy dies. It took down an unrelated 0.33.0 release, and it would have
# failed every subsequent deploy until the directory was renamed.
#
# Two problems, one fix. Outside the `server-*` namespace the prune does not touch it, so it neither
# breaks the deploy nor gets deleted on every deploy — which also means the smoke test survives
# deploys instead of needing to be re-installed each time.
INSTALL_DIR_NAME = "roguelite-smoketest"

# Real RCT trainers, so the ids resolve to actual NPCs with authored teams and skins. NOT the rgl_*
# ids: those name trainers nothing defines yet, so a boss band built from them would spawn nothing.
BOSS_BANDS = [
    ("smoke_boss_early", 1, 20, ["rctmod:leader_brock_0038", "rctmod:leader_misty_0020"]),
    ("smoke_boss_mid", 21, 40, ["rctmod:gym_leader_roark_058a", "rctmod:leader_erika_0041"]),
    ("smoke_boss_late", 41, None, ["rctmod:champion_cynthia_05a7", "rctmod:elite_four_aaron_05a3"]),
]

# One of each RunReward type. `item` (a bag item) is deliberately included and is EXPECTED to report
# a failure — §2.11's run bag does not exist, and seeing that reported is the point.
REWARDS = [
    ("smoke_ev", "common", {"type": "ev", "stat": "attack", "amount": 20}),
    ("smoke_level", "common", {"type": "level", "amount": 2}),
    ("smoke_nature", "common", {"type": "nature", "nature": "adamant"}),
    ("smoke_ability", "rare", {"type": "ability"}),
    ("smoke_held_item", "rare", {"type": "held_item", "item": "cobblemon:leftovers"}),
    ("smoke_tm", "rare", {"type": "move", "move": "earthquake"}),
    ("smoke_bag_item_EXPECT_FAILURE", "rare", {"type": "item", "item": "cobblemon:revive", "count": 1}),
]

# PokéRogue's own price multiples (docs/roguelite-economy-reference.md), against the shared wave-money
# curve rather than flat numbers — so the shop stays in reach at wave 150 without a second table.
#
# The REWARDS behind them are still ours and still placeholders: §2.11 removed the run bag, so we have
# no potions or revives to sell. The names below say what each slot is standing in FOR, so that when
# consumables exist the multiplier is already the right one.
SHOP = [
    # (id, cost multiple, stand-in reward)
    ("stands_in_for_potion", 0.2, {"type": "ev", "stat": "hp", "amount": 10}),
    ("stands_in_for_ether", 0.4, {"type": "level", "amount": 1}),
    ("stands_in_for_revive", 2.0, {"type": "held_item", "item": "cobblemon:focus_sash"}),
    ("stands_in_for_full_heal", 1.0, {"type": "nature", "nature": "adamant"}),
    ("stands_in_for_max_potion", 1.5, {"type": "ev", "stat": "speed", "amount": 20}),
]

# THE REASON A SMOKE RUN COULD NOT BE PLAYED AT ALL.
#
# RunArenas resolves the build as `biome?.arenaBuild ?: config.builds.buildFor(wave)`, and with no
# biome loaded that fallback is ArenaBuilds.default — `cobblemon_roguelite:arena`, a template the mod
# deliberately does not ship (§2.29: content is not the module's to invent, so it fails loudly). So
# every wave failed to stamp, nobody was ever teleported, and no wave could be fought.
#
# A biome carrying a palette is the mechanism as designed, which is why this is a datapack fix and
# not a code change: nothing about §2.29 has to be reversed for the smoke test to have a floor.
#
# THE STANDARD ARENA FOOTPRINT. See docs/roguelite-arena-spec.md, which is the builder-facing
# version of this number and the thing to hand somebody detailing an arena.
#
# 41x41, the same for every biome, because the arenas are being hand-detailed one at a time: a
# footprint that varied per biome would mean every build started by re-measuring, and a detail pass
# that fit one arena would not transfer to the next. Odd, so there is a true centre block to build
# symmetry around, at local (31, 0, 31).
#
# 51 rather than the full 64 box leaves 6 blocks of margin on every side, inside the box and so still
# swept and repainted — that margin is where a backdrop, walls or terrain go without any of it falling
# outside the volume the mode tidies up.
#
# Was 41 until it was stood in: five blocks more radius, because a 41 island is a tad small once two
# Pokémon models and their animations are on it.
#
# These varied 41 -> 31 -> 21 before standardising, to exercise the box sweep clearing what a larger
# earlier band dirtied. That coverage is now only in ArenaBoxScanTest, which is where it belongs
# anyway — it was never worth paying for in the shape of the arenas players stand in.
ARENA_FOOTPRINT = 51

PALETTES = [
    ("smoke_meadow", {
        "floor": "minecraft:grass_block",
        "rim": {"block": "minecraft:mossy_cobblestone", "height": 2},
        "pillars": {"block": "minecraft:oak_log", "height": 6, "inset": 3},
    }),
    ("smoke_volcano", {
        "floor": "minecraft:basalt",
        "rim": {"block": "minecraft:polished_basalt", "height": 3},
        "pillars": {"block": "minecraft:magma_block", "height": 8, "inset": 4},
    }),
    ("smoke_tundra", {
        "floor": "minecraft:snow_block",
        "rim": {"block": "minecraft:packed_ice", "height": 2},
    }),
]

# Disjoint windows, not weights. A smoke test wants the transition to happen at a wave you can write
# down: at the default band length of 10 these hand over at exactly waves 11 and 21, so the re-stamp
# and the repaint are two things to walk into rather than two things to hope for.
#
# `power_spot` is TRUE on every palette above — see the comment where it is written for why removing it
# was the wrong fix for the VMax that showed up in the first playtest.
BIOMES = [
    ("smoke_meadow", "Smoke Meadow", "minecraft:meadow", 1, 10),
    ("smoke_volcano", "Smoke Caldera", "minecraft:basalt_deltas", 11, 20),
    ("smoke_tundra", "Smoke Tundra", "minecraft:snowy_slopes", 21, None),
]

# THE SECOND REASON A RUN COULD NOT BE PLAYED.
#
# With the arena fixed, a run started, drafted and teleported correctly — and then refused wave 1,
# telling the player "run battles are not implemented on this server yet". They were implemented and
# installed. What was missing was this: WildPools had no pool, so the generator had nothing to draw
# and every wild wave refused. Wild waves are most of a run.
#
# Wave windows overlap on purpose, so most waves have several candidates and the draw is visibly a
# draw. Fully-evolved species appear late rather than being scaled up from Caterpie, since the level
# curve makes anything survivable and a wave-40 Metapod reads as the pool being broken.
WILD_POOL = [
    # (species, weight, min_wave, max_wave)
    ("cobblemon:caterpie", 3, 1, 8),
    ("cobblemon:pidgey", 3, 1, 12),
    ("cobblemon:rattata", 3, 1, 12),
    ("cobblemon:zubat", 2, 1, 15),
    ("cobblemon:geodude", 2, 4, 20),
    ("cobblemon:machop", 2, 4, 20),
    ("cobblemon:growlithe", 2, 6, 24),
    ("cobblemon:abra", 1, 6, 24),
    ("cobblemon:haunter", 2, 12, 34),
    ("cobblemon:kadabra", 2, 12, 34),
    ("cobblemon:machoke", 2, 14, 36),
    ("cobblemon:golbat", 2, 14, 36),
    ("cobblemon:arcanine", 2, 26, None),
    ("cobblemon:alakazam", 1, 26, None),
    ("cobblemon:snorlax", 1, 30, None),
    ("cobblemon:gyarados", 1, 30, None),
    ("cobblemon:dragonite", 1, 40, None),
]


def write(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")
    print(f"  wrote {os.path.relpath(path, ROOT)}")


def generic_bands():
    """The 72 hand-picked generic trainers, reused so the smoke test shows the real cast."""
    path = os.path.join(ROOT, "ops/roguelite-generic-trainer-bands.json")
    if not os.path.isfile(path):
        raise SystemExit(f"missing {path} — run ops/gen_roguelite_generic_pool.py first")
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)["bands"]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", default=DEFAULT_OUT)
    args = parser.parse_args()

    data = os.path.join(args.out, "data", NS, "roguelite")
    note = [
        "THROWAWAY SMOKE-TEST DATA - generated by ops/gen_roguelite_smoketest.py.",
        "Not content and not balanced: every number here exists to make a mechanic fire quickly.",
        "Do NOT promote to prod. Writes the `default` id the mod reads, so real tables must REPLACE it.",
    ]

    print("assembling the roguelite smoke-test pack")

    write(os.path.join(args.out, "pack.mcmeta"), {
        "pack": {"pack_format": 48, "description": "roguelite smoke test - THROWAWAY, do not promote"},
    })

    # --- roster: real generic trainers, frequent bosses, shields early so #34 is testable ---
    write(os.path.join(data, "trainer_rosters", "default.json"), {
        "_comment": note + [
            "authored_for is SHORT on purpose: a 60-wave run at trainer-every-3/boss-every-6 reaches a",
            "shielded boss in minutes, where the shipping 200/5/10 schedule would take an evening.",
        ],
        "authored_for": {"run_length": 60, "trainer_interval": 3, "boss_interval": 6},
        "bands": [
            # Re-banded to the short schedule: the generated bands are cut for 200 waves.
            {**band, "min_wave": 1, "max_wave": 20} if i == 0
            else {**band, "min_wave": 21, "max_wave": 40} if i == 1
            else {k: v for k, v in {**band, "min_wave": 41}.items() if k != "max_wave"}
            for i, band in enumerate(generic_bands())
        ] + [
            {"id": bid, "kind": "boss", "min_wave": lo,
             **({"max_wave": hi} if hi else {}), "trainers": ids}
            for bid, lo, hi, ids in BOSS_BANDS
        ],
        "generation": {
            "_note": "Shields from wave 6 so the very first boss is a wall - that is what #34 verifies.",
            "boss_shields": [{"min_wave": 6, "shields": 2}, {"min_wave": 24, "shields": 3, "members": 2}],
        },
    })

    # --- the free half ---
    write(os.path.join(data, "reward_tables", "default.json"), {
        "_comment": note + [
            "One entry per RunReward type. smoke_bag_item_EXPECT_FAILURE is meant to fail: §2.11's run",
            "bag does not exist, and RewardGrant reports that rather than silently doing nothing.",
        ],
        "tiers": [
            {"id": "common", "curve": [{"wave": 1, "weight": 100}]},
            {"id": "rare", "curve": [{"wave": 1, "weight": 60}]},
        ],
        "entries": [
            {"id": rid, "tier": tier, "weight": 1, "min_wave": 1, "reward": reward}
            for rid, tier, reward in REWARDS
        ],
    })

    # --- the paid half ---
    write(os.path.join(data, "shop_tables", "default.json"), {
        "_comment": note + ["Priced as multiples of the wave curve, PokéRogue-style — see docs/roguelite-economy-reference.md."],
        "entries": [
            {"id": sid, "cost_multiplier": multiple, "min_wave": 1, "reward": reward}
            for sid, multiple, reward in SHOP
        ],
    })

    # --- payout, so a finished run does not log an empty resolve ---
    write(os.path.join(data, "payout_tables", "default.json"), {
        "_comment": note + ["A single token payout so the run-end path has something to resolve."],
        "entries": [
            {"id": "smoke_payout",
             # `outcomes` is a LIST and has no default - the loader says so explicitly. Naming all
             # three means a smoke run pays whether it is completed, wiped or abandoned, which is what
             # you want when the thing under test is the payout path rather than the balance.
             "outcomes": ["completed", "wiped", "abandoned"],
             "min_wave": 1,
             "grant": {"type": "item", "item": "cobblemon:poke_ball", "count": 1}},
        ],
    })

    # --- somewhere to stand, which is the difference between a testable run and no run ---
    for pid, palette in PALETTES:
        write(os.path.join(data, "arena_palettes", f"{pid}.json"), {
            "shape": "circle",
            # THE POWER SPOT STAYS. It was removed on the theory that it was what let a player VMax
            # in a run; that was wrong twice over.
            #
            # Dynamax needs the `dynamax_band` ITEM *and* a power spot within 20 blocks — both, not
            # either. And Tera needs a `tera_orb` plus 50 shards and no power spot at all, so removing
            # the block never banned Tera the way the removal claimed.
            #
            # The player could VMax because they walk into a run carrying their own inventory, band
            # included. Stripping the bag (§2.11, still unenforced) is the fix; removing the block only
            # broke §2.5's confinement, which is what makes the gimmicks arena-only in the first place.
            "power_spot": True,
            "width": ARENA_FOOTPRINT,
            "depth": ARENA_FOOTPRINT,
            "_comment": note + [
                "Blocks, not taste. Without a palette the arena build falls back to ArenaBuilds.default",
                "— `cobblemon_roguelite:arena`, an .nbt nothing ships — so no arena is stamped, no player",
                "is teleported, and no wave can be fought. That is what this file is for.",
            ],
            **palette,
        })

    for bid, name, mc_biome, lo, hi in BIOMES:
        write(os.path.join(data, "biomes", f"{bid}.json"), {
            "_comment": note + [
                "Enabled (weight 1) unlike the shipped example, and windowed so the handover waves are",
                "predictable: 1-10, 11-20, 21+. Each names its own palette, so crossing a window",
                "re-stamps the arena AND repaints the box.",
            ],
            "display_name": name,
            "arena_palette": f"{NS}:{bid}",
            "minecraft_biome": mc_biome,
            "min_wave": lo,
            **({"max_wave": hi} if hi else {}),
            "weight": 1,
        })

    # --- something to fight, which is the difference between a run and a walk ---
    write(os.path.join(data, "wild_pools", "smoke.json"), {
        "_comment": note + [
            "Without at least one enabled entry here, WildPools has no pool, the generator draws",
            "nothing and every wild wave refuses to start — which the player is told as a wave that",
            "'could not be started'. Wild waves are most of a run.",
        ],
        "entries": [
            {"species": species, "weight": weight, "min_wave": lo, **({"max_wave": hi} if hi else {})}
            for species, weight, lo, hi in WILD_POOL
        ],
    })

    print("\nNEXT, and read the caveats above first:")
    print(f"  scp -r {os.path.relpath(args.out, ROOT)} "
          f"cobblemon:/opt/cobblemon-dev/world/datapacks/{INSTALL_DIR_NAME}")
    print(f"  # the name matters: anything starting with `server-` is pruned by deploy-dev.yml,")
    print(f"  # which runs as `deployer` and cannot delete a sysadmin-owned dir — that failure")
    print(f"  # exits the whole deploy. See INSTALL_DIR_NAME.")
    print("  ssh cobblemon 'sudo systemctl restart cobblemon-dev'")


if __name__ == "__main__":
    main()
