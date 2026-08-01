# cobblemon-pokerogue-bridge

Server-side NeoForge 1.21.1 mod that bridges our self-hosted PokéRogue instance (frontend +
Go `rogueserver` + MariaDB `pokeroguedb`, on the same VM) into the Minecraft server.
Decision record: `docs/pokerogue-mode-plan.md` §2.44.

What it does:

- `/pokerogue` — clickable link to the web game + linked-account status.
- `/pokerogue link <username>` — first-come-first-served MC↔PokéRogue account linking
  (validated against the DB `accounts` table; collisions are refused and logged for staff).
- `/pokerogue unlink [player]` — self, or any player at permission level 2+.
- `/pokerogue claim` — pays out pending milestone rewards as server-console commands.
- A single background thread polls the DB read-only (`accountStats`, `sessionSaveData`
  headers, `activeClientSessions`-adjacent tables, and the `bridgeRunState` side table our
  patched rogueserver maintains) and fires run lifecycle events
  (`com.cobblemonpokerogue.bridge.api.BridgeEvents`) on the server main thread. Against an
  unpatched rogueserver it degrades to start/end detection only (no waves/species).

Config lives in `config/cobblemon-pokerogue-bridge/`:

- `config.json` — frontend `url`, `db` credentials, `pollSeconds`, optional `shrine`.
  Defaults are placeholders (`CHANGE-ME`); real hostnames/credentials are never committed.
- `accounts.json`, `state.json` — bridge-owned stores; don't hand-edit while running.
- `milestones.json` — the milestone table. **This is content: the human authors it.**
  `milestones.example.json` (rewritten each boot) shows the schema with obviously-placeholder
  `say PLACEHOLDER` rewards only. Schema:
  `[{id, stat, threshold, tier(1|2|3), display, rewards:["console command with %player%", ...]}]`
  where `stat` is any numeric `accountStats` column (e.g. `sessionsWon`, `pokemonCaught`,
  `highestEndlessWave`).

Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build`
