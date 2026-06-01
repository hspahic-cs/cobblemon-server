# Pokémon Spawns

What spawns where, and what's different on our server from base
Cobblemon.

## The full spawn table

The complete species-by-species spawn list lives in this spreadsheet,
maintained alongside the server:

[**Cobblemon Spawns (1.7.3) — Google Sheets →**](https://docs.google.com/spreadsheets/d/1DJT7Hd0ldgVUjJbN0kYQFAyNBP6JGG_Clkipax98x-g/edit?gid=0#gid=0)

The sheet is the source of truth. It tracks:

- Species number + form (including regional variants like Alolan)
- Rarity **bucket** (`ultra-rare`, `rare`, `uncommon`, `common`)
- **Weight** within each bucket
- **Level range** (min/max)
- **Biome** allowlist + excluded biomes
- **Time of day**, **weather**, sky light requirements
- **Multipliers** for weather/time (Storm ×5, Twilight ×2.5, Night ×1.5)
- **Context** (`Natural`, `Water`, `Derelict`, etc.)

## What's different about our server

### No hostile vanilla mob spawns

Zombies, skeletons, creepers, endermen, etc. **do not spawn naturally**
on this server — anywhere except in [restricted dimensions](#restricted-dimensions).
Pokémon are the only ambient threat. This is a deliberate design choice
(`WorldRulesHook` cancels every `MobCategory.MONSTER` natural spawn).

**Exception:** the **Warden**. It still emerges from sculk shriekers
when triggered. That's intentional — the deep dark is a hazard players
opt into.

### Restricted dimensions

These dimensions block **all Pokémon natural spawns** and **all RCT
trainer natural spawns**. Command-summoned entities still work (this
is how gym leaders and market vendors exist there).

- `multiworld:spawn` — the hub world
- `multiworld:arena1`, `arena2` — PvP arenas
- `multiworld:elite4` — Elite Four chambers

Block edits are also disabled here for non-ops. You can't farm a
showcase world.

### Spawn modifiers are active

The spreadsheet's multiplier column actually applies:

- **Storm** → ×5 spawn rate for storm-flagged species
- **Twilight** → ×2.5
- **Night** → ×1.5

So farming a specific rare species often means timing it to a storm at
night in the right biome. Check the spreadsheet's `Weather` + `Time`
columns.

### Hidden Ability flags

A subset of species can spawn with their **Hidden Ability**. The egg
pool CSV (`config/cobblemon-gacha/egg_pools.json`) marks which species
qualify; the same flag is consulted by the gacha when an egg pull
specifies HA. Wild spawn HA chance follows base Cobblemon rules.

## Finding specific Pokémon

1. Open [the sheet](https://docs.google.com/spreadsheets/d/1DJT7Hd0ldgVUjJbN0kYQFAyNBP6JGG_Clkipax98x-g/edit?gid=0#gid=0)
2. Filter by species (column B)
3. Read biome + time + weather conditions
4. `/wild` until you land in a matching biome, or check [Xaero's Worldmap](https://github.com/cobblemon/cobblemon/wiki) for biome distribution
5. If the entry says `Night ×1.5`, come back at night for better odds

For species that appear only in `Derelict` context (abandoned
structures), you'll need to seek out ruins / villages / strongholds —
biome alone isn't enough.

## Bucket philosophy

Cobblemon's bucket system means rarity is **chunk-relative**, not
absolute: within a chunk, the bucket roll picks `common` 70% of the
time, `uncommon` 20%, `rare` 8%, `ultra-rare` 2%. Then within the
chosen bucket, weights decide which species spawns.

Practically: a `common`-bucket entry with weight 50 spawns a lot more
than a `rare`-bucket entry with weight 100 in the same biome.

## Reporting bad / missing spawns

Use `/feedback bug Spawn issue: <species> not appearing in <biome>` —
captures your coords, biome, time, and weather so the spawn condition
can be checked against the sheet.
