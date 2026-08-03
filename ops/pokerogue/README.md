# Self-hosted PokéRogue (hosted pivot)

The real PokéRogue, self-hosted on the cobblemon VM, with a reward bridge into the
Minecraft server. Decision record: `docs/pokerogue-mode-plan.md` §2.44.

## Layout on the VM

| Piece | Where | Port |
|---|---|---|
| Frontend (Vite build of pagefaultgames/pokerogue) | nginx static, `/opt/pokerogue/frontend` | 8000 |
| API (pagefaultgames/rogueserver, Go) | systemd `rogueserver.service`, `/opt/pokerogue/bin` | 8001, reverse-proxied at `:8000/api/` |
| MariaDB (`pokeroguedb`) | localhost only | 3306 |

The API is only ever addressed same-origin through the `/api/` proxy — the frontend's
`PKR-Client-Version` header fails rogueserver's CORS preflight cross-origin, and
same-origin makes 8000 the single public port.

Credentials live only in `/etc/pokerogue/rogueserver.env` (600, generated on first
setup — deploys must never touch it, same rule as the feedback-mod runtime secrets).

## Install / update

```
ops/pokerogue/build-and-stage.sh all 'http://<host>:8000/api'
ssh cobblemon sudo bash -s -- 'http://<host>:8000' < ops/pokerogue/setup-vm.sh
```

`<host>` is whatever players' browsers reach (LAN IP for testing, public address for
release — Discord only, never committed). Both scripts are idempotent; re-run the pair
to ship a new build. The frontend bakes the API URL in at build time, so a host change
means a rebuild, not just a re-setup.

## Notes

- The frontend build applies the reskin overlays in `reskin/` (server title,
  splash lines, trainer display names) via `apply-reskin.py` — see
  `reskin/README.md`. Shipped values are placeholders; real text is
  human-authored content.
- rogueserver is built `-tags=devsetup`: it creates/migrates its own schema
  (`CREATE TABLE IF NOT EXISTS ...`) on boot. Accounts are local username/password
  (registration open by default); Discord/Google OAuth is unconfigured and stays off.
- Remote players need a router forward for 8000 only (same as the MC port — user
  action, not scripted). 8001 never needs to be reachable from outside the VM.
- The reward bridge (not yet built) polls `accountStats` in `pokeroguedb` — monotonic
  per-account counters (`sessionsWon`, `highestEndlessWave`, `pokemonCaught`, ...) —
  and grants milestone rewards in-game. Milestones only, never web-side quantities:
  the web save is client-trusting.
