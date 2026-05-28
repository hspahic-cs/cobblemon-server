# Gym-TP Villager + Arena Coordinate Commands

Branch: `feat/gym-tp-villager` (off 0.5.4 / commit 97183f9)
Modifies: `custom-mods/cobblemon-bridge`, `custom-mods/cobblemon-ranked`

## Goal

Give players a right-clickable NPC villager whose menu warps them to the gyms
they've unlocked. Give ops in-game commands to set the warp coordinates and the
two `/ranked` arena positions (replacing manual JSON editing).

## Non-goals

- Migrating existing datapack-based gym/market spawn-delete functions to Kotlin.
  0.5.4 already committed them into `server-quests/`, which solves the original
  "lives only on prod" concern.
- Changing how gym progression itself is tracked. The `server:beat_gym_<N>`
  advancement chain stays the source of truth for "has the player beaten gym N".

## Architecture

Two mods, no new mods.

### Part A — gym-TP villager (cobblemon-bridge)

A vanilla `minecraft:villager`, summoned and tagged by a new op command, is
intercepted on right-click by a new event handler. The handler reads the
player's advancement state and a small JSON config, then opens a 6-row vanilla
`ChestMenu` listing each entry the player has unlocked. Clicking a slot
teleports the player.

```
Op runs /gymtp spawn      → villager exists, tagged cobblemon_bridge.gym_tp_npc
Op runs /gymtp set 1      → gym 1 coords written to runtime/gym_tps.json
Player right-clicks       → GymTpNpcHook cancels interact, opens GymTpMenu
GymTpMenu                 → reads advancements + GymTpStore, builds chest
Player clicks slot        → ServerPlayer.teleportTo(level, x, y, z, yaw, pitch)
```

#### Components

| Component | File | Role |
|---|---|---|
| `GymTpStore` | `cobblemon-bridge/.../gymtp/GymTpStore.kt` | Gson-backed load/save of `runtime/gym_tps.json`. Map<String, GymEntry>. Atomic write (tmp + rename). |
| `GymEntry` | same file | `{ position: ArenaPos, unlockAdvancement: String?, label: String? }`. `ArenaPos` shape: `{x, y, z, world, yaw, pitch}` — reuse exact shape from `cobblemon-ranked/.../config/RankedConfig.kt:19-26` (keep the two types independent — copy not import — since the two mods don't depend on each other). |
| `GymTpNpcHook` | `cobblemon-bridge/.../gymtp/GymTpNpcHook.kt` | Subscribes to `PlayerInteractEvent.EntityInteract` HIGHEST. Mirrors `cobblemon-market/.../gui/MarketNpcHook.kt`. Cancels default villager interact when tag matches. |
| `GymTpMenu` | `cobblemon-bridge/.../gymtp/GymTpMenu.kt` | Builds `ChestMenu` GENERIC_9x6. One row per visible entry. Slot click → teleport + close. Mirrors layout style of `cobblemon-market/.../gui/MarketMenu.kt`. |
| `GymTpVisibility` | `cobblemon-bridge/.../gymtp/GymTpVisibility.kt` | Pure function: `(player, store) → List<VisibleEntry>`. Easier to unit test in isolation than the menu builder. |
| `GymTpCommands` | `cobblemon-bridge/.../commands/GymTpCommands.kt` | Brigadier registration for `/gymtp ...`. Registered from `CobblemonBridge.kt` alongside existing commands. |
| `BridgeTags` addition | `cobblemon-bridge/.../tags/BridgeTags.kt` | New `const val GYM_TP_NPC = "$NAMESPACE.gym_tp_npc"`. |

#### Persistence

`<configDir>/cobblemon-bridge/runtime/gym_tps.json`:

```json
{
  "entries": {
    "1":  { "position": {"x":100, "y":64, "z":50, "world":"minecraft:overworld", "yaw":0, "pitch":0},
            "unlockAdvancement": null, "label": null },
    "2":  { "position": {...}, "unlockAdvancement": null, "label": null },
    "rotating": { "position": {...}, "unlockAdvancement": "server:beat_gym_10", "label": null }
  }
}
```

First load: file missing → empty store, no error. Malformed JSON → empty store
+ warning log. Concurrent writes prevented by single-threaded server-tick
discipline; atomic file rename guards against power-loss corruption.

#### Visibility rule

For each `(id, entry)` in store, the entry is visible to a player iff:

1. If `entry.unlockAdvancement` is non-null: visible iff player holds it. (Explicit override always wins for both numeric and string keys.)
2. Else if `id` parses as integer `N`:
   - Always visible if `N == 1`
   - Visible if player has `server:beat_gym_<N>` (revisit a beaten gym)
   - Visible if player has `server:beat_gym_<N-1>` (next-up)
3. Else (string id with no explicit unlock): hidden, warn at load time so the op knows to set one.

Sort order: numeric-keyed entries first (ascending), then string-keyed in
insertion order. Display label: `"Gym <N>"` for numeric, otherwise
title-cased id + " Gyms" (so `rotating` → `"Rotating Gyms"`), overridden by
`entry.label` if set.

#### Visual layout (54-slot chest)

```
Row 0:  [ ][ ][ ][ ][ "Gym Warps" sign ][ ][ ][ ][close ]
Row 1:  [Gym 1][Gym 2][Gym 3][Gym 4][Gym 5][Gym 6][Gym 7][Gym 8][Gym 9]
Row 2:  [Gym 10][Rotating Gyms][...empty...]
Row 3-5: empty (clicks blocked)
```

Each gym slot is a `WHITE_BANNER` (or similar) named the gym label with lore
`"✓ Beaten — click to revisit"` or `"→ Available — click to challenge"`.
Click handling intercepts every slot in `clicked()`; the only side effect is
teleport-then-close on a configured-and-visible slot, otherwise no-op.

#### Commands (op level 2)

| Command | Effect |
|---|---|
| `/gymtp set <id>` | Set `<id>` to sender's position+facing+dimension. |
| `/gymtp set <id> <x> <y> <z> [yaw] [pitch] [dimension]` | Explicit form. |
| `/gymtp set <id> unlock <advancement>` | Set/replace the unlock advancement. |
| `/gymtp set <id> label <text>` | Set/replace the display label. |
| `/gymtp clear <id>` | Remove the entry. |
| `/gymtp list` | Print all entries to sender. |
| `/gymtp spawn` | Kill any existing tagged villager, summon a new one at sender's position with `Invulnerable / NoAI / PersistenceRequired / Silent` and the `cobblemon_bridge.gym_tp_npc` tag. Profession: librarian; CustomName: `"Gym Guide"`. |
| `/gymtp delete` | Kill the tagged villager. |

`<id>` argument autocompletes from the current store keys.

### Part B — arena coordinate commands (cobblemon-ranked)

Slot into the existing `/ranked admin` subtree in `cobblemon-ranked/.../commands/RankedCommands.kt`. No new files needed unless command count justifies splitting.

| Command | Effect |
|---|---|
| `/ranked admin setarena 1` | Write sender's pos+facing+dimension to `RankedConfig.arenaPos1`, save. |
| `/ranked admin setarena 2` | Same for `arenaPos2`. |
| `/ranked admin setarena 1 <x> <y> <z> [yaw] [pitch] [dimension]` | Explicit form. |
| `/ranked admin setarena 2 <x> <y> <z> [yaw] [pitch] [dimension]` | Explicit form. |
| `/ranked admin clearpos 1` / `clearpos 2` | Set to null. |
| `/ranked admin showarena` | Print both positions. |

Implementation: read sender position via `CommandSourceStack.position` and `getRotation`, dimension via `getLevel().dimension().location()`, write through `RankedConfig.save()` (already exists). No reload needed — `RankedConfig` is loaded once at start, then in-memory. The existing battle-start path reads `arenaPos1`/`arenaPos2` from the in-memory config.

## Error handling

- **Missing dimension on teleport:** Use `server.getLevel(ResourceKey.create(...))`; if null, send the player a red error message, don't crash.
- **Cross-dimension teleport:** `ServerPlayer.teleportTo(ServerLevel, x, y, z, yaw, pitch)` handles dimension swap.
- **Store missing/corrupt:** load returns empty store + WARN log. Set commands write fresh file.
- **Empty store + right-click:** menu opens with a single info row "No gyms configured. Ask an op to run /gymtp set."
- **String-keyed entry without unlock advancement:** hidden, WARN logged once at load time.
- **Two players opening the same villager:** ChestMenu is per-player; each gets their own instance (vanilla behavior).
- **Atomic save:** write to `gym_tps.json.tmp`, fsync, rename — survives power loss. Same pattern can be backported to `RankedConfig`/`MarketStore` separately (out of scope).

## Testing

### Unit tests (cobblemon-bridge)

- `GymTpStoreTest`: round-trip empty / single entry / multi entry; missing file → empty; malformed JSON → empty + no exception.
- `GymTpVisibilityTest`: per the visibility rule table — gym 1 always visible; gym N visible with prereq or beaten; string key requires explicit unlock; user-supplied `unlockAdvancement` overrides numeric default. Stub advancement lookup via a small `AdvancementChecker` interface seam (real impl walks `ServerPlayer.advancements`; test impl is a set of strings).

### Unit tests (cobblemon-ranked)

- One test per new `setarena`/`clearpos`/`showarena` path verifying it mutates `RankedConfig` and calls `save()`.

### Manual verification (game)

1. Launch dev server on this branch.
2. `/gymtp set 1` at spawn point.
3. `/gymtp set 2` at a chosen spot.
4. `/gymtp spawn` → confirm villager appears, is invulnerable (try `/kill @e[type=villager,distance=..3]` from a non-op — should be no-op).
5. Right-click as a non-op player → only Gym 1 row visible (no advancements yet).
6. `/advancement grant @s server:beat_gym_1` → re-open menu → Gym 1 (beaten) + Gym 2 (next-up) visible.
7. Click Gym 2 → land at the right coords + facing.
8. `/gymtp set rotating unlock=server:beat_gym_10` → confirm hidden until that advancement is granted, then visible.
9. `/ranked admin setarena 1` at corner A, `setarena 2` at corner B → `/ranked challenge <other player>` → confirm both players land at the right arenas on accept.

## Out of scope

- Migration of `server:gym/*` and `server:market/*` datapack functions to Kotlin (0.5.4 fixed the version-control concern by committing them).
- Atomic-write retrofit for existing stores (`RankedConfig`, `MarketStore`, `EloStore`).
- A `/gymtp respawn` helper or in-game re-tag command.
- Per-player gym TP positions (positions are global; per-player would be a different feature).
