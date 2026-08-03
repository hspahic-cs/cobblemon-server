# PokéRogue session-save schema & run-lifecycle detection

Reference for the reward-bridge mod that polls the self-hosted PokéRogue DB
(MariaDB) read-only. Everything below is verified against the exact code our
instance runs — not guessed from upstream docs.

**Verified sources** (2026-07-31):

- Frontend: `pokerogue` beta branch @ `399345b` — `src/@types/save-data.ts`
  (`SessionSaveData`), `src/system/game-data.ts` (serialization, clear flow),
  `src/phases/encounter-phase.ts` (autosave cadence),
  `src/phases/game-over-phase.ts` + `post-game-over-phase.ts` (run end).
- Server: `rogueserver` master @ `0cf149a` — `db/savedata.go`, `db/db_setup.go`,
  `api/endpoints.go`, `api/savedata/{clear,update,delete,common}.go`.
- The **live binary on the VM was built from exactly `0cf149a`**
  (`vcs.revision` embedded in `/opt/pokerogue/bin/rogueserver`), so the code
  above is authoritative for our deployment. Game version served: `1.12.0.10`.

---

## 1. CRITICAL: the DB blob is zstd + Go **gob**, NOT zstd + JSON

`sessionSaveData.data` and `systemSaveData.data` are written by
`db/savedata.go` as:

```go
gob.NewEncoder(zstdWriter).Encode(data)   // defs.SessionSaveData struct
```

So the blob is a **zstd frame (magic `28 B5 2F FD`) wrapping a Go
`encoding/gob` stream** of `defs.SessionSaveData` — not JSON. There is no
practical gob decoder for the JVM or Python.

Consequences for the mod:

- **Row-level DB state (presence, `timestamp`, accountStats columns) is
  readable directly and is enough for the whole run lifecycle** (section 4).
- To read *inside* the save (waveIndex, party, gameMode) the mod needs one of:
  1. **A tiny Go sidecar** that imports `rogueserver/defs`, does
     zstd→gob→JSON, and exposes it (recommended; ~40 lines, reuses
     `ReadSessionSaveData` verbatim).
  2. The HTTP API (`GET /savedata/session/get`) which returns **decompressed
     JSON** (verified live) — but it only serves the *authenticated account's*
     own save, and see the session-clobber warning in section 6.
- The JSON shape below is what the client sends/receives over the API and what
  the gob stream decodes back into; field names are identical either way
  (the Go struct's json tags match the TS interface).

## 2. Session JSON — the fields we need

Full interface: `SessionSaveData` (frontend `src/@types/save-data.ts`, Go
`defs/savedata.go`). Fields relevant to the bridge:

| key | type | meaning |
|---|---|---|
| `seed` | string | per-run RNG seed; **stable for the whole run** — use as run identity across polls (slot reuse!) |
| `gameMode` | int | see enum below |
| `waveIndex` | int | current wave (1-based; classic ends at 200, daily at 50) |
| `party` | array | player party, slot order; `party[0]` = lead |
| `party[i].species` | int | `SpeciesId` enum value (see section 3) |
| `party[i].formIndex` | int | form within species (0 = base) |
| `party[i].level` | int | |
| `party[i].nickname` | string | often base64-ish/empty; display name fallback = species |
| `party[i].shiny` / `.variant` | bool / int | |
| `party[i].fusionSpecies` | int | non-zero when spliced (endless fusion) |
| `money`, `score` | int | |
| `playTime` | int | **seconds** in this run (ticks 1/s) |
| `battleType` | int | 0 WILD, 1 TRAINER, 2 CLEAR, 3 MYSTERY_ENCOUNTER — `2` only appears in the final victory save |
| `timestamp` | int | client `Date.now()` — **epoch milliseconds** at serialization |
| `gameVersion` | string | e.g. `"1.12.0.10"` |
| `victoryCount`, `faintCount`, `reviveCount`, `playerFaints` | int | run counters |
| `challenges` | array of `{id,value,severity}` | non-empty in CHALLENGE mode |
| `name` | string | player-chosen run name (may be absent) |

`gameMode` values (`src/enums/game-modes.ts`, default TS enum numbering):

```
0 = CLASSIC        (victory = clear wave 200)
1 = ENDLESS        (no victory; ends only in defeat/abandon)
2 = SPLICED_ENDLESS
3 = DAILY          (victory = clear wave 50)
4 = CHALLENGE      (classic rules, wave 200)
```

Trimmed source-derived example (shape produced by
`GameData.getSessionSaveData()`; the bridgetest account had no session rows,
so no live capture — see section 6):

```json
{
  "seed": "MTc4NTU1NDc5NTIwNA==",
  "playTime": 842,
  "gameMode": 0,
  "party": [
    {
      "id": 1234567,
      "player": true,
      "species": 6,
      "nickname": "",
      "formIndex": 0,
      "level": 36,
      "shiny": false,
      "variant": 0,
      "hp": 121,
      "ivs": [31, 20, 15, 31, 10, 25],
      "fusionSpecies": 0
    }
  ],
  "money": 3250,
  "score": 615,
  "waveIndex": 42,
  "battleType": 0,
  "trainer": null,
  "gameVersion": "1.12.0.10",
  "name": "",
  "timestamp": 1785554795204,
  "challenges": [],
  "mysteryEncounterType": -1,
  "playerFaints": 0
}
```

## 3. Reading `party[0].species` as a lowercase species id

`species` is the numeric `SpeciesId` enum value
(`src/enums/species-id.ts`):

- **1–1025**: national dex number of the base species
  (`6` = charizard, `658` = greninja).
- **Regional forms are separate ids with a thousands prefix**:
  `2xxx` = Alolan (`2019` = ALOLA_RATTATA), `4xxx` = Galarian
  (`4052` = GALAR_MEOWTH), `6xxx` = Hisuian (`6570` = HISUI_ZORUA),
  `8xxx` = Paldean (`8128` = PALDEA_TAUROS). Also `2670` =
  ETERNAL_FLOETTE (a one-off, not Alolan).
- Rule: `dex = id < 2000 ? id : id % 1000`; region = `id / 2000`
  (1 alola, 2 galar, 3 hisui, 4 paldea). Lowercase species id =
  the enum constant name lowercased with the region prefix stripped
  (map dex → name with any national-dex table, e.g. Cobblemon's own
  species registry: `dex 6 → "charizard"`).
- Megas / Gmax / cosmetic forms are **not** separate ids — they live in
  `formIndex` on the base species.
- Endless splices: if `fusionSpecies != 0` the mon is a fusion; the head
  species is still `species`.

## 4. Run lifecycle — DB-observable signals

### Tables involved (schemas in `db/db_setup.go`)

- `sessionSaveData(uuid BINARY(16), slot TINYINT, data LONGBLOB,
  timestamp TIMESTAMP)` — PK `(uuid, slot)`, **5 slots (0–4)** per account.
  `timestamp` is set by the server (`UTC_TIMESTAMP()`) on every REPLACE.
- `systemSaveData(uuid PK, data, timestamp)` — one per account, REPLACEd on
  every sync.
- `accountStats(uuid PK, playTime, battles, classicSessionsPlayed,
  sessionsWon, highestEndlessWave, highestLevel, pokemonSeen,
  pokemonDefeated, pokemonCaught, pokemonHatched, eggsPulled, *Vouchers)` —
  **absolute values copied from the client's `gameStats` on every system
  save**, not server-side increments (`db/account.go UpdateAccountStats`).
- `dailyRunCompletions(uuid, seed, mode, score, timestamp)` — PK
  `(uuid, seed)`; one row inserted per **completed** (= won) run seed.
  Despite the name it records **classic victories too** (mode 0), and since
  classic seeds are unique per run, effectively every classic win = new row.
- `activeClientSessions(uuid PK, clientSessionId VARCHAR(32))` — which
  browser tab currently owns the account. Overwritten on session/get,
  system/get (when stale), and checked by updateall. **Presence tells you
  nothing about a run**; it's a concurrency token, not a heartbeat.
- `accounts.lastActivity` — touched on every savedata update/clear/delete.
- `accountDailyRuns(uuid, date, score, wave)` — daily-mode leaderboard row,
  written by `/savedata/session/clear` for gameMode 3 regardless of win/loss.

### Save write path (what moves `timestamp`)

The client saves locally **every wave**, but syncs to the server
(`POST /savedata/updateall`, handler `handleUpdateAll`) only when
`waveIndex % 5 == 1` (waves 1, 6, 11, … "X1 and X6") **or** ≥300 s of play
since the last save (`encounter-phase.ts:300`). One `updateall` REPLACEs
**both** the session row (chosen slot) and the system row, and rewrites all
`accountStats` columns.

Expected cadence while someone plays: `sessionSaveData.timestamp` for
`(uuid, slot)` advances every ~5 waves or ~5 minutes, whichever first.
Poll at 30–60 s; anything faster buys nothing.

### Signals

**Run started**
- A row appears at `(uuid, slot)` that didn't exist before — first sync fires
  at wave 1 (1 % 5 == 1), so this is prompt.
- OR an existing row's decoded `seed` changes (slot reuse after an old
  orphaned save — see pitfalls; presence alone is not identity, `seed` is).
- Corroboration: `accountStats.classicSessionsPlayed` /
  `endlessSessionsPlayed` bumps by 1 (client increments at starter select,
  `select-starter-phase.ts`; lands with the wave-1 sync).
  `dailyRunSessionsPlayed` is client-side only — **not** an accountStats
  column, don't look for it.

**Run in progress**
- `timestamp` on the `(uuid, slot)` row advances; decoded `waveIndex`
  increases (server rejects wave regressions for the same seed:
  "session out of date: existing wave index is greater").
- `accountStats.battles` grows with each sync (absolute value).

**Run ended — victory** (classic wave 200 / daily wave 50; endless has none)
1. `PostGameOverPhase` does a final `updateall` — session row updated one
   last time with `battleType = 2 (CLEAR)` and final `waveIndex`
   (`validateSessionCompleted`: gameMode 0 + wave 200, or gameMode 3 + wave
   50), and `accountStats.sessionsWon` bumps (+1, pushed from client
   `gameStats.sessionsWon++` in `game-over-phase.ts`).
2. Immediately after, `POST /savedata/session/clear` → server inserts a
   `dailyRunCompletions(uuid, seed, mode)` row (win only) and **DELETEs the
   session row**.

   Detection rule: row deleted AND (`sessionsWon` increased OR a new
   `dailyRunCompletions` row for the last-seen `seed`). The
   `dailyRunCompletions` check is the strongest signal — it is written
   server-side and carries the seed you were tracking.

**Run ended — defeat**
- Same clear flow but `validateSessionCompleted` is false: session row
  DELETEd, **no** `dailyRunCompletions` insert, **no** `sessionsWon` change.
- Detection rule: row for a tracked seed disappears with no seed-completion
  row and no `sessionsWon` bump ⇒ defeat (or manual abandon — see pitfalls).

**Daily mode extra**: on `/clear` of a gameMode 3 run the server also
upserts `accountDailyRuns` (win or lose) with `score` and the completed wave
— a defeat-visible artifact unique to daily.

### Pitfalls

- **5 slots**: a player can have several concurrent saves. Track runs by
  `(uuid, slot, seed)`, not by uuid.
- **Session rows survive unconfirmed defeats.** The clear only happens in
  `PostGameOverPhase`, i.e. after the player sits through/confirms the
  game-over sequence. If they close the tab at the death screen, the row
  stays at the last autosave (a wave *before* the killing one — reloading it
  is the de-facto retry mechanism). Treat "row exists but timestamp stale
  for hours/days" as *dormant*, not *in progress*, and do not infer defeat
  from staleness alone.
- **Retries**: with `enableRetries` on (non-victory), choosing Retry reloads
  the last save — no DB delete, run continues under the same seed. The wave
  can appear to repeat; that's fine since regressions never reach the DB.
- **Manual slot delete** (`/savedata/session/delete`, menu "delete save") has
  the same row-level signature as a confirmed defeat (delete, no stat
  change). Heuristic: a real defeat is preceded seconds earlier by a final
  row REPLACE (the `saveAll` in PostGameOverPhase); a menu delete usually is
  not. Do not award defeat-consolation rewards on delete-without-recent-write.
- **Endless never "wins"**: gameMode 1/2 runs only end in defeat/abandon.
  Progress metric is `waveIndex` / `accountStats.highestEndlessWave`.
- **accountStats are absolute copies**, refreshed on *every* system sync from
  whatever client state uploads. Compute deltas between polls; never assume
  a column moves exactly at run end (e.g. `classicSessionsPlayed` moves at
  run *start*).
- **In-JSON `timestamp` is client clock (ms)**; the SQL `timestamp` column is
  server UTC. Use the SQL column for cadence, the JSON one only for display.
- **`sessionSaveData.timestamp` also REPLACEs on `/savedata/session/update`**
  (legacy single-save path) — same semantics, don't assume updateall is the
  only writer.

## 5. HTTP API quick reference (LAN, placeholder host)

```
POST http://pokerogue.example.lan/api/account/login
     form: username=...&password=...          -> {"token": "<base64>"}
GET  http://pokerogue.example.lan/api/savedata/session/get?slot=0&clientSessionId=<32 alnum>
     header: Authorization: <token>           -> SessionSaveData JSON (200)
                                              -> "save does not exist" (404)
GET  http://pokerogue.example.lan/api/savedata/system/get?clientSessionId=<32 alnum>
     header: Authorization: <token>           -> SystemSaveData JSON
```

The API returns **decompressed JSON** (verified against the live instance);
zstd/gob exists only at the DB layer.

## 6. WARNING: API reads clobber the player's live session

`GET /savedata/session/get` **unconditionally overwrites**
`activeClientSessions` with the supplied `clientSessionId`
(`endpoints.go handleSession`); `system/get` does the same when the id is
stale. If you fetch a mid-run player's save over the API with your own
clientSessionId, their next `updateall` fails with
"session out of date: not active" and their client force-reloads.

Therefore: the bridge must poll the **DB**, not the API, for other players.
The API path (and `ops/pokerogue/dump-session.py`) is for test accounts and
debugging only.

Live capture status (2026-07-31): login + `system/get` verified against the
test account (`gameVersion 1.12.0.10`, JSON confirmed); all 5 session slots
returned 404 (account has never started a run), so the session example above
is source-derived, not captured.
