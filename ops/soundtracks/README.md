# World soundtracks

Custom music for the Multiworld dimensions. There are **two layers**:

- **Exploration music** — plays while walking around a world. Driven per-dimension
  by the client-only `cobblemon-soundtracks` mod
  (`custom-mods/cobblemon-soundtracks/`).
- **Battle themes** — play *during* a fight. Set on the player by
  `cobblemon-bridge`'s `BattleThemeHook` when an Elite Four / arena battle starts;
  Cobblemon loops the theme and auto-pauses the exploration music for the
  battle's duration, then resumes it.

| What                  | Dimension(s)                     | Source folder            | Sound event     |
|-----------------------|----------------------------------|--------------------------|-----------------|
| Spawn exploration     | `multiworld:spawn`               | `source/spawn/`          | `music.spawn`   |
| Elite Four exploration| `multiworld:elite4`              | `source/elite4/`         | `music.elite4`  |
| Arena exploration     | `multiworld:arena1`, `arena2`, … | `source/arena/`          | `music.arena`   |
| **Gym battle (shared pool)** | regular gym fights, gym id 1–19 | `source/gym-battle/`     | `battle.gym`    |
| **E4 battle (per member)** | each E4 fight, by gym id 20–24 | `source/elite4-battle/<member>/` | `battle.e4_*` |
| **Arena battle (PvP)**| arena fights                     | `source/arena-battle/`   | `battle.arena`  |
| Wilderness            | `minecraft:overworld`            | *(none — stays vanilla)* | —               |

Elite Four battle themes are **per member** — `source/elite4-battle/` has a
subfolder per trainer (`lorelei/`, `cynthia/`, `agatha/`, `lance/`, `champion/`);
see its `TRACKS.md`. All **regular** gym leaders (gym 1–19) share one rotating
pool (`source/gym-battle/`, ~10–12 tracks). Arenas are PvP (no trainer), so they
share one battle pool too. Leave any folder empty to fall back to Cobblemon's
default battle music there.

Exploration folders rotate through their tracks (short gap between songs). A
battle folder picks one track at random per battle and loops it. Every dimension
not listed (including the wilderness) is left on vanilla music.

## Adding / changing music

1. Put audio files in the matching `source/<…>/` folder. Any format ffmpeg reads
   (mp3, wav, flac, m4a, ogg…). Name them descriptively. See the `TRACKS.md` in
   `source/elite4-battle/` for a per-generation suggestion list.
2. Run (needs `ffmpeg`):

   ```
   python3 ops/soundtracks/build-soundtracks.py
   ```

   This re-encodes everything to Ogg Vorbis into the mod's resources and
   regenerates `sounds.json`. Removed sources are pruned. Re-run any time.
3. Commit the generated `.ogg` files + `sounds.json`. CI builds the mod jar and
   the `.mrpack` ships it to clients automatically (`cobblemon-soundtracks` is
   `dist=[Dist.CLIENT]`; the bridge that sets the theme is server-side).

## Notes

- **Source audio is gitignored** (it can be large and is often copyrighted) —
  only the converted `.ogg` that ship in the jar are committed. Keep your
  originals somewhere safe; they're the input, not the artifact.
- Battle themes need no code change — Cobblemon builds the sound event from the
  id, so this script's `sounds.json` entry is all that's required.
- Tuning the gap between exploration tracks lives in `SoundtrackManager.kt`
  (`MIN_DELAY` / `MAX_DELAY`, in ticks).
- New event = add a row to `EVENTS` in `build-soundtracks.py` + a `source/<…>/`
  folder. New *exploration* dimension also needs a `register(...)` in
  `ModSounds.kt` and a branch in `SoundtrackManager.onSelectMusic`. New *battle*
  theme also needs a branch in `cobblemon-bridge` `BattleThemeHook`.
