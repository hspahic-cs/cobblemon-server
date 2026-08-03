# Roguelite arena build spec

For anyone detailing a roguelite arena. Every number here is what the code actually does, not a
convention we hope holds — the file and line is given where it matters, so it can be re-checked.

## The short version

Build a **41-block circular island** on a floor plane, inside a **64 × 32 × 64** box. Leave the
centre and one block east of it clear. Export the whole box as a structure. That is the whole job.

## The volume you own

| | |
|---|---|
| Box | 64 wide (X) × 32 tall (Y) × 64 deep (Z) |
| Floor plane | the bottom layer of the box |
| Standard island | 41 × 41 circular, centred |
| Margin outside the island | 11 blocks on every side, still inside the box |

The box is the volume the mode **sweeps, clears and re-paints** between waves. Anything you build
inside it is looked after. Anything outside it is not — it will never be tidied up, never be
recoloured by the biome repaint, and will still be sitting there during somebody else's run. So the
11-block margin is yours for a backdrop, cliffs, walls or water, but the box edge is a hard wall.

Arenas are 1024 blocks apart, so there is no chance of two of them meeting.

## Coordinates, in local structure space

Local `(0, 0, 0)` is the box's minimum corner — the north-west corner of the floor plane. The floor
layer is at local `y = 0`, and a player stands at `y = 1`.

| What | Local position | Notes |
|---|---|---|
| Island centre block | `(31, 0, 31)` | true centre — build symmetry around this |
| Player lands | `(32, 1, 32)` | facing **+Z** (south) |
| **Power spot** | `(32, 1, 31)` | see below — this one is not optional |
| Opponent appears | `(32, 1, 38)` | 6 blocks along +Z from the player |
| Island extent | local `11 … 51` on X and Z | the disc inscribed in that square |

Two things follow from the table that are easy to miss:

- **The player does not land on the centre block.** The island's true centre is `(31, ·, 31)` and the
  entry is `(32, 1, 32)`, one block south-east of it. That is a consequence of centring an odd island
  in an even box, it is one block, and nobody can feel it — but if you are aligning something to where
  the player stands, `(32, 1, 32)` is the number.
- **Keep the battle corridor clear.** The player at `(32, 1, 32)` and the opponent at `(32, 1, 38)`
  need the space between and above them free of anything solid. Pokémon models are large; give the
  middle of the island a good clear span and put the detail around the edges.

## The power spot is not decoration

`mega_showdown:power_spot` at local `(32, 1, 31)`.

Mega Evolution, Terastallisation and Dynamax are confined to arenas by exactly one rule: *that block
exists inside an arena and nowhere else.* A generated island places it for you. **A hand-built
structure does not** — `ArenaStamper.stampTemplate` places your `.nbt` and nothing else
(`arena/ArenaStamper.kt:103`). If you leave it out, the gimmicks silently stop working in that arena
and nothing in the log says why.

So: place it yourself, at that position, and do not bury it.

## Round, not square

The standard island is circular (`"shape": "circle"` in the palette). A square platform floating in a
void dimension reads as a chunk of a world that was cut out; a disc reads as somewhere that was always
an island.

The generator inscribes the disc in the `width × depth` footprint, measuring from cell centres, so a
41-wide island is symmetric about its true centre block. Rims follow the edge of the disc rather than
the bounding box, and the four optional pillars stand on the diagonals — where the corners would have
been if the island had any.

## How to build one

1. **Start from the generated island.** Get the palette stamped in a creative world — that is your
   canvas: floor, rim, pillars, power spot, all in the right places.
2. **Detail it.** Stay inside the 64 × 32 × 64 box, keep the battle corridor clear, and leave the
   power spot alone.
3. **Export with a structure block**, covering the whole box, anchored at the box's minimum corner so
   local `(0, 0, 0)` lines up with the floor plane's north-west corner.
4. **Ship it** as `data/<namespace>/structure/<path>.nbt`.
5. **Point the biome at it** — in the biome's JSON, replace `arena_palette` with `arena_template`
   naming your structure. Exactly one of the two; writing both is an error rather than one silently
   winning.

## Checklist before handing one over

- [ ] Everything is inside 64 × 32 × 64, anchored at the minimum corner
- [ ] Floor layer is at local `y = 0`
- [ ] `mega_showdown:power_spot` is at local `(32, 1, 31)`
- [ ] `(32, 1, 32)` and `(32, 1, 38)` are clear, and so is the space between and above them
- [ ] Nothing hangs below the floor plane — it is outside the box, so nothing will ever clean it up
- [ ] The island reads as an island from the middle, since that is where the player spends the fight

## Where these numbers live

| Number | Source |
|---|---|
| 64 × 32 × 64 box | `ArenaBox` default, `arena/ArenaConfig.kt` |
| Entry at box centre, +1Y | `ArenaConfig.entryOffset` |
| Facing +Z | `ArenaConfig.entryYaw` = 0 |
| Opponent 6 blocks along the facing | `RunWildBattle.OPPONENT_DISTANCE` |
| Power spot one block +X of centre | `ArenaPlan.POWER_SPOT_OFFSET_X` |
| Standard 41 × 41 circle | `ARENA_FOOTPRINT`, `ops/gen_roguelite_smoketest.py` |

If a number here disagrees with the code, the code is right and this file is stale — say so.
