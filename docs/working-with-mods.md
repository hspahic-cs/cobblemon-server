# Working with mods — end-to-end guide

The complete workflow for editing custom mods, shipping them to dev, and
promoting to prod. Read this first if you've never deployed before.

## How the system is wired

```
   ┌─────────────────┐
   │  custom-mods/   │  6 in-house Java/Kotlin mods (you can edit any)
   └────────┬────────┘
            │ ./gradlew build  (CI does this)
            ▼
   ┌─────────────────┐
   │  modpack/       │  packwiz manifest of every mod (yours + 3rd-party)
   │   pack.toml     │
   │   mods/*.toml   │  references to Modrinth jars
   │   mods/*.jar    │  staged here by CI before rsync
   └────────┬────────┘
            │ rsync over SSH  (CI does this)
            ▼
   ┌──────────────────────────────────────────────┐
   │  192.168.1.20  (the cobblemon VM)             │
   │  /opt/cobblemon-dev   :25566   (test server)  │
   │  /opt/cobblemon-prod  :25565   (live server)  │
   └──────────────────────────────────────────────┘
```

Two servers run side-by-side on the same VM. dev is for testing changes
before they go to prod.

**External access for friends:**
- prod: `108.21.168.120:25565`
- dev:  `108.21.168.120:25566`

## How deploys are triggered

| Action on the repo | What deploys |
|---|---|
| Open a PR touching `custom-mods/` or `modpack/` | CI builds all 6 mods to verify |
| **Merge a PR that bumps CHANGELOG.md** | dev auto-deploys + `dev-latest` mrpack updated ← *this is the deploy signal* |
| Push a `vX.Y.Z` git tag | Tagged GitHub Release drafted with .mrpack (no deploy) |
| Manually run "Deploy prod" workflow against a tag | prod deploys |

## Where players get the modpack

| Audience | URL | Updated when |
|---|---|---|
| **Dev testers** | https://github.com/hspahic-cs/cobblemon-server/releases/tag/dev-latest | Every dev deploy (overwritten in place) |
| **Prod players** | https://github.com/hspahic-cs/cobblemon-server/releases/latest | Only on tagged `vX.Y.Z` releases |

Players bookmark either URL once and re-download when they need the new pack.
PrismLauncher: Add Instance → Import → paste URL.

**Code merges without a CHANGELOG bump don't deploy anywhere.** The
CHANGELOG entry is the explicit signal that says "this is ready to ship."

## Step-by-step: editing an existing mod

This is the most common workflow.

### 1. Branch off main

```sh
cd /Users/scorpio/Repos/personal/cobblemon-server
git checkout main && git pull
git checkout -b feat/your-change
```

### 2. Edit the mod

Pick a mod under `custom-mods/`. Six are available:

| Module | Owner | What it does |
|---|---|---|
| `cobblemon-npc` | yours | Minecolonies citizens as Pokémon trainers |
| `cobblemon-bridge` | almutwakel | Cobblemon-event hooks: level cap, gyms, E4, command aliases |
| `cobblemon-carrots` | almutwakel | Carrot-based healing |
| `cobblemon-gacha` | almutwakel | Lootbox crates |
| `cobblemon-market` | almutwakel | Dynamic-pricing shop with `/market` |
| `cobblemon-ranked` | almutwakel | ELO PvP ladder |

Make your code change in `custom-mods/<mod>/src/`. The Java/Kotlin source is
under `src/main/`, with `META-INF/neoforge.mods.toml` declaring the modid
and dependencies.

### 3. Build locally to catch obvious breakage (optional but recommended)

```sh
cd custom-mods/<mod>
./gradlew build --no-daemon
```

A successful build produces `build/libs/<mod>-1.0.0.jar`. If the build
fails locally, CI will too — fix before pushing.

If you don't have JDK 21 locally, just push and let CI build. PR validation
will compile all 6 mods on every PR.

### 4. Open a PR

```sh
git add custom-mods/<mod>
git commit -m "feat: short description"
git push -u origin feat/your-change
gh pr create --base main --head feat/your-change \
  --title "feat: short description" \
  --body "What changed and why"
```

Wait for `PR validation` to go green. If it fails, click into the run on
GitHub, fix the build error, push again. Don't merge a red PR.

### 5. Bump CHANGELOG (right before you're ready to ship)

Once your code is reviewed and ready to actually deploy:

```sh
scripts/bump-version.sh 0.4.2     # or whatever the next version is
```

This updates:
- `modpack/pack.toml` version
- `custom-mods/cobblemon-npc/gradle.properties` mod_version

Then edit `CHANGELOG.md` — move items from the `[Unreleased]` section into a
new `## [0.4.2] - YYYY-MM-DD` heading describing what's new. Format:

```markdown
## [Unreleased]

## [0.4.2] - 2026-05-27

### Added
- Brief description of new feature

### Fixed
- Brief description of fix

## [0.4.1] - ...
```

Commit it on the same branch:

```sh
git add CHANGELOG.md modpack/pack.toml custom-mods/cobblemon-npc/gradle.properties
git commit -m "release: 0.4.2"
git push
```

### 6. Merge to main → dev deploys automatically

After merge, GitHub Actions auto-fires `Deploy dev` because CHANGELOG.md
changed. Watch it at https://github.com/hspahic-cs/cobblemon-server/actions
or:

```sh
gh run list -L 3
```

Takes ~3-4 min. The workflow does:
1. Build all 6 mods at version 0.4.2
2. Resolve modpack dependencies via packwiz
3. rsync the mods to `/opt/cobblemon-dev/staging/mods.v0.4.2/`
4. rsync datapacks (overlay onto `/opt/cobblemon-dev/world/datapacks/`)
5. Atomic-swap the `mods` symlink → `mods.v0.4.2`
6. `systemctl restart cobblemon-dev`
7. Verify the service comes back active

When green, dev is on 0.4.2.

### 7. Connect to dev and test

`Deploy dev` also overwrites the `dev-latest` GitHub pre-release with a
freshly-built `.mrpack`. Players testing on dev can re-import from
https://github.com/hspahic-cs/cobblemon-server/releases/tag/dev-latest
in PrismLauncher each time they need to pick up new mods.

Then add server `108.21.168.120:25566` in your MC client and join. Verify
your change works.

You can tail server logs from your Mac to confirm mods loaded correctly:

```sh
ssh sysadmin@192.168.1.20 'tail -f /opt/cobblemon-dev/logs/latest.log'
```

### 8. Tag the release (optional, before promoting to prod)

If dev looks good and you want a downloadable .mrpack on GitHub:

```sh
git tag v0.4.2
git push origin v0.4.2
```

This fires `release.yml`, which builds a fresh .mrpack and drafts a GitHub
Release with all six jars + the .mrpack attached. Publish it from the UI or:

```sh
gh release edit v0.4.2 --draft=false
```

Friends now have a downloadable URL:
`https://github.com/hspahic-cs/cobblemon-server/releases/tag/v0.4.2`

### 9. Promote to prod

Once you're confident the version on dev is good:

```sh
gh workflow run deploy-prod.yml --ref main -f tag=v0.4.2
```

Or use the UI: Actions → Deploy prod → Run workflow → enter `v0.4.2`.

This refuses to run if dev isn't already on the same version (safety check).
Override with the `skip_dev_check` input only if you know what you're doing.

Prod restarts; players are briefly disconnected (~30s); they reconnect on
the new version.

## Step-by-step: adding a new custom mod

The CI iterates `custom-mods/*/` so dropping a new module is mostly a
copy-paste. Easiest is to clone an existing module's structure.

```sh
cd custom-mods
cp -r cobblemon-npc cobblemon-mynewmod
cd cobblemon-mynewmod
rm -rf build .gradle  # clear any local build state
```

Now edit:
- `gradle.properties` — change `mod_version` and `mod_id` if needed
- `settings.gradle` — change `rootProject.name`
- `src/main/resources/META-INF/neoforge.mods.toml` — change `modId`,
  `displayName`, `description`
- `src/main/kotlin/...` — your new mod's package + code

Verify it builds:

```sh
./gradlew build
```

Then push a PR as in step 4 above. CI will pick up the new module
automatically — no workflow edits needed.

## Step-by-step: adding a third-party mod

Use packwiz from your Mac:

```sh
brew install go               # if you don't have it
go install github.com/packwiz/packwiz@dfd8b68a4796c763e25bad50265ea1f1233e24f1
cd modpack
~/go/bin/packwiz mr add <modrinth-slug>     # e.g. "spark"
~/go/bin/packwiz refresh
```

This creates `modpack/mods/<mod>.pw.toml`. Commit it.

For Fabric mods running through Sinytra Connector (rare — needed for things
like `cobblemon-economy`), packwiz refuses because the loader doesn't match.
Hand-write the `.pw.toml` instead. Use `modpack/mods/cobblemon-economy.pw.toml`
as a template — fields: `name`, `filename`, `side`, `[download]`/`url`,
`hash`, `[update.modrinth]/mod-id`, `version`. Fetch `version` and `hash`
via the Modrinth API.

## Day-to-day operational tasks

### Reset dev to a recent prod-like state

Every Wednesday at 5 AM UTC, prod is snapshotted. To wipe dev's state and
load the latest snapshot:

```sh
ssh sysadmin@192.168.1.20 'sudo /opt/snapshots/dev-reset.sh'
```

This stops dev, backs up its current world to `world.before-reset-<ts>`,
restores from the latest snapshot, and starts dev. Useful when dev's state
has drifted, you want to test against real player data, or things broke
mid-test.

To force an immediate snapshot (before resetting), trigger it manually:

```sh
ssh sysadmin@192.168.1.20 'sudo systemctl start prod-snapshot.service'
```

See [snapshots.md](snapshots.md) for the full snapshot infrastructure.

### Roll back a bad deploy

The `mods.vX.Y.Z` directories are kept on the VM (5 most recent) so you can
flip the symlink:

```sh
ssh sysadmin@192.168.1.20
cd /opt/cobblemon-dev    # or /opt/cobblemon-prod
ls mods.v*               # see available versions
ln -sfn mods.v0.4.0 mods # roll back to 0.4.0
sudo systemctl restart cobblemon-dev
echo 0.4.0 > .deployed_version  # so future re-deploys aren't skipped as no-op
```

### Inspect a server live

Use RCON. There's a small RCON helper in `/tmp/cobblemon-load/rcon.py` if
you set up earlier; otherwise inline:

```sh
# prod
ssh sysadmin@192.168.1.20 'RCONPW=$(grep ^rcon.password /opt/cobblemon-prod/server.properties | cut -d= -f2); python3 -c "
import socket, struct
s=socket.socket(); s.connect((\"127.0.0.1\",25575))
def pkt(rid,t,b): body=struct.pack(\"<ii\",rid,t)+b.encode()+b\"\x00\x00\"; return struct.pack(\"<i\",len(body))+body
s.send(pkt(1,3,\"\$RCONPW\")); s.recv(4096)
s.send(pkt(2,2,\"list\")); print(s.recv(4096)[12:-2].decode())
"'
```

For dev, swap port 25575 → 25576 and `cobblemon-prod` → `cobblemon-dev`.

## Troubleshooting

### CI's PR validation fails

Click the failing run, expand "Build all custom mods", look at which module
errored. Most common: a Kotlin compile error from your edit. Fix it, push.

### deploy-dev fails at rsync step

The deployer SSH key may have expired or the VM may be unreachable. SSH
from your Mac to confirm:

```sh
ssh deployer@192.168.1.20 echo ok
```

If that fails, check `/etc/deploy-keys/` on the GitHub Actions runner pod
and the `authorized_keys` file on the VM.

### deploy-dev fails at "Wait for service"

The mod jars deployed but the server didn't start back up cleanly. Tail the
log to see what crashed:

```sh
ssh sysadmin@192.168.1.20 'tail -50 /opt/cobblemon-dev/logs/latest.log'
```

Common causes: missing third-party dep (add to packwiz), modid collision
(rename one), Kotlin signature mismatch (rebuild against current
`gradle.properties` versions). To recover, roll back via the symlink swap
above and fix forward.

### Deploy claims success but the change isn't visible in-game

Confirm the version on the VM matches what you bumped:

```sh
ssh sysadmin@192.168.1.20 'cat /opt/cobblemon-dev/.deployed_version'
ssh sysadmin@192.168.1.20 'ls -la /opt/cobblemon-dev/mods'
```

If `.deployed_version` is older than the CHANGELOG, the deploy was likely
skipped as a no-op. Force redeploy:

```sh
gh workflow run deploy-dev.yml --ref main -f version=0.4.2
```

### Friend's mod doesn't load after deploy

Friend's mods may declare runtime deps (rctmod, cobreeding, cobblemon-economy,
cloth-config) that need to be in `modpack/mods/*.pw.toml`. Check the dev log
for `Mod X requires Y`. Add the missing manifest via packwiz or hand-write
it for Fabric-via-Connector mods. Bump CHANGELOG and re-deploy.

## File reference

| File | Why you'd touch it |
|---|---|
| `custom-mods/<mod>/src/` | Editing mod code |
| `modpack/mods/<mod>.pw.toml` | Adding/updating a third-party mod |
| `modpack/server-overrides/datapacks/` | Editing server-side datapacks |
| `modpack/overrides/` | Editing client-side files (options.txt) bundled in mrpack |
| `CHANGELOG.md` | THE deploy signal — bump this to ship |
| `scripts/bump-version.sh` | Sync version across pack.toml + cobblemon-npc/gradle.properties |
| `.github/workflows/deploy-dev.yml` | Edit dev deploy flow itself |
| `.github/workflows/deploy-prod.yml` | Edit prod deploy flow itself |
| `.github/workflows/release.yml` | Edit how the .mrpack and GitHub Release are built |
| `.github/workflows/pr-validation.yml` | Edit PR build checks |
| `ops/snapshots/` | Edit snapshot/reset scripts (then redeploy to VM) |
