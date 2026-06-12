# Elite Four — battle themes (per member)

These play **during** the Elite Four / Champion fights (set on the player by
cobblemon-bridge's BattleThemeHook). **Each member has its own subfolder** — drop
that trainer's theme into the matching folder, then run
`python3 ops/soundtracks/build-soundtracks.py`. Put more than one file in a
subfolder and a random one is picked per fight. File names are cosmetic.

Our roster (gym ids 20–24, in gauntlet order):

| Subfolder   | Trainer            | Gym id | Signature track ideas                                   |
|-------------|--------------------|--------|---------------------------------------------------------|
| `alder/`    | Elite Four #1      | 20     | Battle! (Champion Alder) — Gen 5 BW                     |
| `cynthia/`  | Elite Four #2      | 21     | Battle! (Champion Cynthia) — Gen 4 DPPt (iconic)        |
| `ash/`      | Elite Four #3      | 22     | anime "Battle Frontier" / a hype trainer theme of choice |
| `lance/`    | Elite Four #4      | 23     | Battle! (Champion Lance) — GSC / HGSS                   |
| `n/`        | Champion           | 24     | Battle! (N) or Decisive Battle! N — Gen 5 BW            |

Leave a subfolder empty and that fight just uses Cobblemon's default battle
music. These are copyrighted Nintendo/Game Freak tracks; source them yourself.
They are gitignored here — only the converted .ogg that ship in the mod jar are
committed.
