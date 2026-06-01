# Cobbleworkers

Turn your idle Pokémon into farm hands. Park them on a pasture block,
hook a chest up to it, and they'll auto-harvest, generate, or gather
the things in their job description.

## Setup (3 steps)

1. **Place a Pasture Block.** You get your first from the
   `evolve_exeggutor` quest reward. More are craftable / buyable.
2. **Connect a chest to the Pasture Block.** Place a chest adjacent
   to the pasture — the chest is where harvested items / generated
   resources land. Without a connected chest, the worker has nowhere
   to deposit.
3. **Send a Pokémon to the pasture.** Right-click with the species on
   the pasture's UI. The job that activates depends on the species —
   one of [the entries in the table below](#job-reference).

## Work range

Each worker operates within a **2-block horizontal radius** from
the pasture block (5×5 area) and **5 blocks vertically**. That's
roughly `areaScanRadius: 2` × `areaScanHeight: 5` in
`config/cobbleworkers/cobbleworkers.json`. Plan your crop / target-
block layout around that footprint.

## Job reference

24 jobs registered. 4 are disabled on this server because they trivially
AFK-farm; the rest are active.

### Active jobs

| Job | What it does | Eligible species |
|---|---|---|
| **crops_harvester** | Breaks + auto-replants mature vanilla crops (wheat, carrots, potatoes, beetroot) | Leafeon, Bellossom, Sunflora, Lilligant, Vileplume, Victreebel, Exeggutor, Roserade, Simisage, Whimsicott |
| **berry_harvester** | Picks mature Cobblemon berries | Leafeon, Bellossom, Sunflora, Lilligant, Vileplume, Victreebel, Exeggutor, Roserade, Simisage, Whimsicott |
| **apricorn_harvester** | Picks mature apricorns | Vikavolt, Scizor |
| **mint_harvester** | Picks mature mints | Clefable, Wigglytuff, Togekiss, Florges, Whimsicott, Slurpuff, Aromatisse, Ninetales |
| **netherwart_harvester** | Harvests mature nether wart | Mismagius, Chandelure, Aegislash, Froslass, Dusknoir, Polteageist |
| **amethyst_harvester** | Mines ripe amethyst clusters | Rhyperior |
| **tumblestone_harvester** | Mines tumblestone clusters | Aegislash, Sandslash, Scizor, Steelix |
| **black_tumblestone_harvester** | Mines black tumblestone | Aegislash, Sandslash, Scizor, Steelix |
| **sky_tumblestone_harvester** | Mines sky tumblestone | Aegislash, Sandslash, Scizor, Steelix |
| **fletcher** | Crafts arrows from sticks + feathers in the chest | Vileplume, Victreebel, Roserade, Nidoking, Nidoqueen |
| **irrigator** | Bonemeals adjacent farmland | Vaporeon, Starmie, Cloyster, Poliwrath, Ludicolo, Simipour, Politoed, Slowking, Kingdra |
| **extinguisher** | Puts out fires in range | Vaporeon, Starmie, Cloyster, Poliwrath, Ludicolo, Simipour, Politoed, Slowking, Kingdra |
| **water_generator** | Fills adjacent water cauldrons (cooldown 90s) | Vaporeon, Starmie, Cloyster, Poliwrath, Ludicolo, Simipour, Politoed, Slowking, Kingdra |
| **lava_generator** | Fills adjacent lava cauldrons (cooldown 90s) | Flareon, Arcanine, Ninetales, Simisear, Magmortar |
| **fuel_generator** | Generates furnace fuel into adjacent furnaces (cooldown 80s) | Flareon, Arcanine, Ninetales, Simisear, Magmortar |
| **brewing_stand_fuel_generator** | Adds blaze powder to adjacent brewing stands (cooldown 80s) | Kingdra |
| **powder_snow_generator** | Fills adjacent powder-snow cauldrons (cooldown 90s) | Glaceon, Cetitan, Froslass, Cloyster, Ninetales, Sandslash, Darmanitan |
| **item_gatherer** | Sweeps dropped items in range into the chest | Starmie, Exeggutor, Gallade, Musharna, Slowking |

### Disabled jobs

| Job | Reason disabled |
|---|---|
| **archaeologist** | Trivially AFK-farms suspicious-block loot |
| **dive_looter** | Trivially AFK-farms underwater treasure |
| **fishing_looter** | Trivially AFK-fishes |
| **pickup_looter** | Unbounded item sweep — `item_gatherer` (range-limited) covers the use case instead |

## Tips

- **Exeggutor is the workhorse pseudo-starter.** It's in both
  `crops_harvester` (vanilla crops) and `berry_harvester` (Cobblemon
  berries), plus `item_gatherer`. The mainline quest chain
  (`evolve_exeggutor → ranch_carrot_farm`) is built around this — get
  your Exeggutor on a pasture next to a carrot patch ASAP.
- **One Pokémon = one job.** A worker performs the job matched by its
  species; you can't make a Vaporeon do crops or an Exeggutor do
  irrigation.
- **Cooldown 0 ≠ unlimited speed.** Pasture and target-block scans
  have their own server-wide cooldowns (`areaScanCooldown: 45t`,
  `navigationTimeout: 30t`). Cobbleworkers throttle automatically.
- **Multiple workers, one pasture.** A pasture block can hold
  multiple Pokémon (default 16 per the Cobblemon
  `defaultPasturedPokemonLimit`). Each one runs its own job in the
  same range.
- **Form matters.** The species filter is by base name, so Alolan
  forms (e.g. Alolan Ninetales = Ice/Fairy) work alongside the
  Kantonian form (Fire) in the same job list. The job logic doesn't
  enforce type compatibility — Kantonian Ninetales as a
  `powder_snow_generator` will still fill cauldrons even though it's
  thematically a fire-type.

## Why some species are in unexpected jobs

The lists target Pokémon whose theme or canonical lore matches the
job — sometimes loosely. Ninetales shows up in both `mint_harvester`
(Alolan = Fairy, fits the mints flavor) and `powder_snow_generator`
(Alolan = Ice). Aegislash in `netherwart_harvester` is the ghost-type
nudge toward the nether. Steelix as a `tumblestone_harvester` because
it lives in stone biomes. Etc. If a species you'd expect is missing,
file a `/feedback bug` and we'll consider adding it.
