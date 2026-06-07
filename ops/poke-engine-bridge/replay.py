#!/usr/bin/env python3
"""Replay a logged battle turn through the bridge code, offline.

Battle logs are JSONL files written by app.py when BRIDGE_BATTLE_LOG_DIR is
set — one complete request per line. This re-runs any turn locally with full
logging, so "the gym did something weird on turn N" becomes reproducible.

    cd ops/poke-engine-bridge
    PYTHONPATH=../../reference/foul-play \
        ../../reference/foul-play/.venv/bin/python replay.py <battle.jsonl> \
        [--turn N] [--search-ms 1000] [--list]

--turn is 1-based; defaults to the last logged turn. --list shows a summary
of all turns (pick, elapsed, error) without searching.
"""

import argparse
import json
import logging
import sys

from config import FoulPlayConfig

from bridge import PickRequest, pick_move


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logfile")
    parser.add_argument("--turn", type=int, default=0, help="1-based; 0 = last")
    parser.add_argument("--search-ms", type=int, default=1000)
    parser.add_argument(
        "--temperature",
        type=float,
        default=None,
        help="override opponent-fallibility temperature (0=perfect opponent). "
        "Default: use the value logged in the turn record.",
    )
    parser.add_argument("--list", action="store_true", help="summarize turns and exit")
    args = parser.parse_args()

    turns = []
    with open(args.logfile) as f:
        for line in f:
            if line.strip():
                turns.append(json.loads(line))
    if not turns:
        sys.exit("no turns in log file")

    if args.list:
        for i, t in enumerate(turns, 1):
            outcome = t.get("pick") or t.get("error", "?")
            print(
                f"turn {i:3d}  force_switch={str(t.get('force_switch', False)):5}  "
                f"log_lines={len(t.get('log_lines', [])):3d}  -> {outcome}"
            )
        return

    index = args.turn - 1 if args.turn else len(turns) - 1
    t = turns[index]
    print(f"--- replaying turn {index + 1}/{len(turns)} of {t['battle_id']}")
    print(f"--- original outcome: {t.get('pick') or t.get('error')}")

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")
    FoulPlayConfig.parallelism = 1
    FoulPlayConfig.search_time_ms = args.search_ms

    req = PickRequest(
        request_json=t["request_json"],
        log_lines=t["log_lines"],
        gym_side=t["gym_side"],
        pokemon_format=t["pokemon_format"],
        generation=t["generation"],
        smogon_stats_format=t["smogon_stats_format"],
        search_time_ms=args.search_ms,
        opponent_team_packed=t.get("opponent_team_packed"),
        force_switch=t.get("force_switch", False),
        temperature=args.temperature
        if args.temperature is not None
        else t.get("temperature", 0.0),
    )
    print(f"--- temperature: {req.temperature}")
    choice = pick_move(t["battle_id"], req)
    print(f"--- replay pick: {choice}")


if __name__ == "__main__":
    main()
