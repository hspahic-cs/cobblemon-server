# cobblemon-pokerogue-bridge

Server-side NeoForge 1.21.1 mod that bridges our self-hosted PokéRogue instance (frontend +
Go `rogueserver` + MariaDB `pokeroguedb`, on the same VM) into the Minecraft server.
Decision record: `docs/pokerogue-mode-plan.md` §2.44–§2.46.

What it does:

- `/pokerogue` — clickable link to the web game, linked-account line, armed-run credit count.
- `/pokerogue enter` — the §2.46 entry flow, in order:
  1. **Server-minted account** (first entry only): the bridge registers the PokéRogue account
     itself — username = MC name (every MC name is a legal rogueserver username, verified
     against `^\w{1,16}$`), password generated bridge-side (never typed; MC logs commands to
     latest.log) and stored in `accounts.json`. If an account with the MC name already exists
     (a legacy web account), the bridge refuses rather than silently binding it — staff verify
     ownership and repair with `/pokerogue link`.
  2. **§2.45 pay-to-dream** (unchanged): charges `entryFee` (NeoEssentials currency, atomic
     check-and-deduct) and writes one armed-run credit to `bridgeRunArming` — the ONE table
     the otherwise-SELECT-only DB user may write. The patched rogueserver consumes a credit
     when a NEW classic run first saves and rejects unarmed ones; against an unpatched
     rogueserver the command refuses cleanly (fee refunded, nothing armed).
  3. **Tokenized link**: the bridge asks rogueserver's secret-gated `/bridge/minttoken` for a
     one-time session token and sends `<url>/#pt=<token>` — the browser opens already logged
     in. Any mint failure degrades to the plain URL plus a manual-login hint (run stays
     armed).
- `/pokerogue password` — whispers the stored generated password (server-minted accounts
  only; legacy links predate minting and the bridge never knew their password).
- `/pokerogue unlink [player]` — self, or any player at permission level 2+.
- `/pokerogue link <player> <username>` — **staff only** (permission level 2): repairs a
  legacy web-account link after verifying ownership out of band. There is no public link verb
  anymore (§2.46 — squatting dies by construction).
- `/pokerogue claim` — pays out pending milestone rewards as server-console commands.
- Repeatable classic payout: on classic run end, the deepest `payoutBands` wave band reached
  (default 50/100/150/200 → 1/2/3/4 pokemon-crate keys; highest band only, not cumulative)
  is enqueued as a `gacha grant` pending claim for `/pokerogue claim`.
- A single background thread polls the DB read-only (`accountStats`, `sessionSaveData`
  headers, `activeClientSessions`-adjacent tables, and the `bridgeRunState` side table our
  patched rogueserver maintains) and fires run lifecycle events
  (`com.cobblemonpokerogue.bridge.api.BridgeEvents`) on the server main thread. Against an
  unpatched rogueserver it degrades to start/end detection only (no waves/species).

Config lives in `config/cobblemon-pokerogue-bridge/`:

- `config.json` — frontend `url`, `apiBase` (rogueserver's HTTP API, default
  `http://127.0.0.1:8001`), `tokenSecret` (shared secret for `/bridge/minttoken`, minted by
  setup-vm.sh; empty disables the token flow → plain links), `db` credentials, `pollSeconds`,
  optional `shrine`, `entryFee` (default 5000; 0 = free entry, still arms), `payoutBands`
  (wave → key count). Defaults are placeholders (`CHANGE-ME`); real hostnames/credentials are
  never committed.
- `accounts.json`, `state.json` — bridge-owned stores; don't hand-edit while running.
  `accounts.json` entries are `{"username": ..., "password": ...}`; legacy bare-string
  entries (pre-minting links, no stored password) stay valid.
- `milestones.json` — the milestone table. **This is content: the human authors it.**
  `milestones.example.json` (rewritten each boot) shows the schema with obviously-placeholder
  `say PLACEHOLDER` rewards only. Schema:
  `[{id, stat, threshold, tier(1|2|3), display, rewards:["console command with %player%", ...]}]`
  where `stat` is any numeric `accountStats` column (e.g. `sessionsWon`, `pokemonCaught`,
  `highestEndlessWave`) or the bridge-side virtual stat `maxClassicWave` (deepest classic
  wave ever observed for the account).

Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build`
