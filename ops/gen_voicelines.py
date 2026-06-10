#!/usr/bin/env python3
"""Generate the trainer voice-lines file read by cobblemon-bridge's
TrainerVoiceHook (config/cobblemon-bridge/runtime/voicelines.json).

Source of truth is the CAST list below: one entry per character, with the
trainer id(s) that character occupies. Each id gets its own JSON entry with
the same name/color/lines — so a gym leader who appears as gym_NN_type,
bt_NN_type and bt_NN_type_challenge is written once here and fanned to all
three. Triggers: intro (battle start), taunt (down to last Pokemon),
victory (trainer wins), defeat (player wins); each is a list, picked at random.

Run from repo root: `python3 ops/gen_voicelines.py`. Output is repo-tracked and
deployed to the server's config dir; the bridge hot-reloads it on the next
battle (mtime check), no restart needed.
"""
import json
from pathlib import Path

OUT = Path("modpack/server-overrides/config/cobblemon-bridge/runtime/voicelines.json")


# Each character: ids it occupies + chat name/color + the four trigger lists.
CAST = [
    {
        "ids": ["gym_20_alder"], "name": "Alder", "color": "gold",
        "intro": [
            "Ha! Look at that fire in your eyes. C'mon, show an old man what you've got.",
            "Been a while since someone made it up here. Let's enjoy this, eh?",
        ],
        "taunt": ["Down to my last, are we? Good. This is the fun part."],
        "victory": ["Not quite yet, kid. Train up and come find me again."],
        "defeat": ["Hah! Magnificent. You and your Pokémon — that's the real thing. Be proud."],
    },
    {
        "ids": ["gym_21_cynthia"], "name": "Cynthia", "color": "gray",
        "intro": [
            "You and your Pokémon overcame every challenge to reach me — I can feel that strength from here.",
            "As one of the Four, I accept your challenge. There won't be any letup from me.",
        ],
        "taunt": [
            "I can't remember the last time I was backed into a corner like this!",
            "I won't let it end yet — this match is far too fun for that!",
        ],
        "victory": [
            "Not yet. The secret to getting stronger is simple, really — love your Pokémon with all your heart, then come back to me.",
            "A moment ago you were the strongest challenger I'd faced. Train, and be stronger still.",
        ],
        "defeat": [
            "Outstanding. You gave your Pokémon everything they needed and guided them with such certainty.",
            "Thank you — truly. It's been a while since a battle reminded me why I fell in love with this.",
        ],
    },
    {
        "ids": ["gym_22_ash"], "name": "Ash", "color": "yellow",
        "intro": [
            "Finally, a real battle! Pikachu, you ready? Let's do this!",
            "Me and my team don't back down. Bring it on!",
        ],
        "taunt": ["One left? Then we go all out — right, buddy?"],
        "victory": ["Yeah! That's what training with your friends gets you!"],
        "defeat": ["Whoa, you and your team are amazing. I've still got a ton to learn!"],
    },
    {
        "ids": ["gym_23_lance"], "name": "Lance", "color": "red",
        "intro": [
            "So you wish to test yourself against dragons. Very well — don't disappoint me.",
            "I'm Lance. Bring everything you have. I'll accept nothing less.",
        ],
        "taunt": ["My final dragon still stands. We do not bow."],
        "victory": ["Not enough. Forge your bonds stronger and face me again."],
        "defeat": ["…Incredible. You and your Pokémon battled as one. I yield, gladly."],
    },
    {
        "ids": ["gym_24_n"], "name": "N", "color": "light_purple",
        "intro": [
            "…Your Pokémon. Just now — it was speaking. Could you hear it, too?",
            "I am N. Show me your truth, and I will answer with my ideals.",
        ],
        "taunt": [
            "Only one remains… yet its voice does not waver. Nor will I.",
            "My ideals will not break so easily.",
        ],
        "victory": [
            "…As I suspected. The formula holds.",
            "Your truth could not overturn mine. …A pity.",
        ],
        "defeat": [
            "…Your ideals… your feelings… they were stronger than mine, it seems.",
            "Just now, your Pokémon were speaking — of you. There's no separating you two. …I understand that now.",
        ],
    },
]


def build() -> dict:
    out: dict = {}
    for c in CAST:
        entry = {k: c[k] for k in ("name", "color", "intro", "taunt", "victory", "defeat")}
        for tid in c["ids"]:
            out[tid] = entry
    return out


def main() -> int:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(build(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({len(build())} trainer entries)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
