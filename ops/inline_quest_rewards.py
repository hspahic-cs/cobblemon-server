#!/usr/bin/env python3
"""For each quest-reward mcfunction, append '§8(Reward: <X>§8)' to its
trailing 'Next:' line. The next quest(s) come from a static chain map
(authoritative copy of the chain shape in cobblemon-bridge's
QuestCommand.kt); reward labels mirror QuestCommand.REWARDS.

This is intentionally per-source-file rather than NLP — the Next: text is
freeform, but the source file's role in the chain is deterministic.
"""
import re
import sys
from pathlib import Path

REWARDS = {
    "select_pokemon": "§f10 Poké Balls",
    "use_wild": "§f3 Raw Copper + 5 Bone Meal",
    "set_home": "§f3 Red Apricorn Sprouts",
    "craft_pokeball": "§fIron Pickaxe",
    "catch_pokemon": "§f3 Carrots",
    "heal_pokemon": "§fRevive",
    "farm_carrots": "§f3 Blue Apricorn Sprouts",
    "beat_wild_trainer": "§fSophisticated Backpack",
    "reach_party_level_20": "§aCommon Egg",
    "reach_income_250": "§fPasture Block",
    "first_pvp_win": "§aCommon Egg",
    "reach_income_1000": "§fMinecolonies Supply Camp",
    "reach_income_10000": "§f1 Master Ball",
    "reach_income_100000": "§6Ultra Key",
    "reach_elo_1100": "§fGreat Ball + Super Potion",
    "reach_elo_1200": "§fUltra Ball + Hyper Potion",
    "reach_elo_1300": "§f1 Rare Candy",
    "reach_elo_1500": "§f1 Master Ball",
    "reach_elo_2000": "§6Ultra Key",
    "join_colony": "§fPoké Healer",
}
for i in range(1, 25):
    REWARDS[f"beat_gym_{i}"] = "§6Ultra Key" if i in (10, 19, 23, 24) else "§5Rare Key"

# Source quest → [next quest(s)]. The list captures all next-pointers shown in
# the source's "Next:" text. A challenge variant inherits the base's nexts.
# Empty list = end-of-chain (no reward to advertise).
NEXT: dict[str, list[str]] = {
    "root": ["select_pokemon"],
    "select_pokemon": ["use_wild"],
    "use_wild": ["set_home"],
    "set_home": ["craft_pokeball"],
    "craft_pokeball": ["catch_pokemon"],
    "catch_pokemon": ["farm_carrots"],
    "heal_pokemon": ["beat_wild_trainer"],
    "farm_carrots": ["heal_pokemon"],
    "beat_wild_trainer": ["reach_party_level_20"],
    "reach_party_level_20": ["beat_gym_1"],
    "first_pvp_win": ["reach_elo_1100"],
    "reach_elo_1100": ["reach_elo_1200"],
    "reach_elo_1200": ["reach_elo_1300"],
    "reach_elo_1300": ["reach_elo_1500"],
    "reach_elo_1500": ["reach_elo_2000"],
    "reach_elo_2000": [],
    "reach_income_250": ["reach_income_1000", "first_pvp_win"],
    "reach_income_1000": ["join_colony", "reach_income_10000"],
    "reach_income_10000": ["reach_income_100000"],
    "reach_income_100000": [],
    "join_colony": [],
    # Mainline gym ladder: each gym hints at the next. Gym 1 also branches into
    # reach_income_100. Gym 10 transitions into the rotating tier (we show
    # beat_gym_11 as the next, plus beat_gym_20 to signal the Elite Four route).
    "beat_gym_1": ["reach_income_250", "beat_gym_2"],
    "beat_gym_10": ["beat_gym_11", "beat_gym_20"],
    "beat_gym_19": ["beat_gym_20"],
    "beat_gym_20": ["beat_gym_21"],
    "beat_gym_21": ["beat_gym_22"],
    "beat_gym_22": ["beat_gym_23"],
    "beat_gym_23": ["beat_gym_24"],
    "beat_gym_24": [],
}
# Default mainline gyms 2..9 → next gym
for i in range(2, 10):
    NEXT.setdefault(f"beat_gym_{i}", [f"beat_gym_{i + 1}"])
# Rotating gyms 11..18 each suggest the next rotating gym + E4 route.
for i in range(11, 19):
    NEXT.setdefault(f"beat_gym_{i}", [f"beat_gym_{i + 1}", "beat_gym_20"])
# Challenge (Hard Mode) variants inherit their base gym's nexts.
for i in range(1, 25):
    base = f"beat_gym_{i}"
    NEXT[f"{base}_challenge"] = NEXT.get(base, [])


NEXT_PATTERN = re.compile(
    r'(\{"text":\s*"\\n§e[►▶] Next:\s*",\s*"bold":\s*false\},\s*\{"text":\s*")'
    r'([^"]*)'
    r'(")'
)


def reward_labels_for(source: str) -> list[str]:
    """Reward labels for each next quest, deduplicated in original order.
    Multiple nexts pointing at the same reward (e.g. two Rare Key gyms) collapse
    to one label so the hint reads `(Reward: Rare Key)` not `(Reward: Rare Key; Rare Key)`."""
    nexts = NEXT.get(source, [])
    seen: set[str] = set()
    out: list[str] = []
    for q in nexts:
        label = REWARDS.get(q)
        if label and label not in seen:
            seen.add(label)
            out.append(label)
    return out


def inject(payload: str, rewards: list[str]) -> str:
    if not rewards:
        return payload
    suffix = " §8(Reward: " + "; ".join(rewards) + "§8)"
    if payload.endswith("\\n"):
        return payload[:-2] + suffix + "\\n"
    return payload + suffix


def process(path: Path) -> bool:
    src = path.read_text(encoding="utf-8")
    source = path.stem  # e.g. "beat_gym_1"
    rewards = reward_labels_for(source)
    if not rewards:
        return False

    changed = False

    def repl(m: re.Match[str]) -> str:
        nonlocal changed
        prefix, payload, suffix = m.group(1), m.group(2), m.group(3)
        if "(Reward:" in payload:
            return m.group(0)
        new_payload = inject(payload, rewards)
        if new_payload == payload:
            return m.group(0)
        changed = True
        return prefix + new_payload + suffix

    new_src = NEXT_PATTERN.sub(repl, src)
    if changed:
        path.write_text(new_src, encoding="utf-8")
    return changed


def main() -> int:
    root = Path("modpack/server-overrides/datapacks/server-quests/data/server/function/quests/rewards")
    if not root.exists():
        print(f"missing dir: {root}", file=sys.stderr)
        return 1
    total = 0
    changed = 0
    skipped: list[str] = []
    for fp in sorted(root.glob("*.mcfunction")):
        if fp.name == "_finalize.mcfunction":
            continue
        total += 1
        had_next = "Next:" in fp.read_text()
        if process(fp):
            changed += 1
        elif had_next:
            skipped.append(fp.name)
    print(f"updated {changed}/{total} reward mcfunctions")
    if skipped:
        print("skipped (had Next: but mapping has none or already injected):")
        for n in skipped:
            print(f"  - {n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
