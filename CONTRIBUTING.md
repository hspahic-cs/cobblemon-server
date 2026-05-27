# Contributing

## Before You Start

- Check [Issues](../../issues) to see what's already being worked on
- Comment on an issue before starting work — if there isn't one, create it
- One person per issue at a time to avoid conflicts

## Branch Strategy

Branch off `main` for every change. Never push directly to `main`.

**Naming convention:**
- `feat/...` — new features
- `fix/...` — bug fixes
- `chore/...` — config, docs, infra
- `release/...` — version bump preparing a deploy

## Repo layout

| Path | What | Tracked? |
|---|---|---|
| `custom-mods/<name>/` | In-house Java/Kotlin mods we build ourselves | yes |
| `modpack/pack.toml` | packwiz pack manifest | yes |
| `modpack/mods/*.pw.toml` | Per-mod manifest (third-party, fetched at deploy time) | yes |
| `modpack/mods/*.jar` | Built jars staged in by CI | no (gitignored) |
| `modpack/server-overrides/datapacks/` | Datapacks rsynced to live `world/datapacks/` on deploy | yes |
| `modpack/overrides/` | Client-side options.txt etc. shipped in `.mrpack` | yes |
| `reference/` | Local clones of upstream mod source for reference only | no (gitignored) |
| `docs/` | Design notes, install guides | yes |
| `CHANGELOG.md` | One entry per shipped version | yes (deploy signal) |

## Build + deploy model

**dev** auto-deploys when a commit lands on `main` whose CHANGELOG.md adds a new
version heading. **prod** deploys only when you manually run the `Deploy prod`
workflow against a tag.

```
PR opens          → pr-validation.yml builds all 6 mods + validates packwiz
PR merges (no CHANGELOG bump)  → no deploy
PR merges (CHANGELOG bumped)   → deploy-dev.yml fires, dev gets the new version
git tag vX.Y.Z + push          → release.yml drafts a GitHub Release
manual run Deploy prod (tag)   → prod gets the version (refuses if dev isn't on it yet)
```

The "deploy signal is the CHANGELOG entry" rule means casual commits on main
(refactors, docs) don't deploy. Only intentional releases do.

## Adding or editing a custom mod

1. Branch off main: `git checkout -b feat/cool-feature`
2. Edit `custom-mods/<name>/src/...` — add/change Java/Kotlin code
3. (Optional) Build locally to confirm it compiles:
   ```sh
   cd custom-mods/<name> && ./gradlew build
   ```
4. Commit, push, open PR. `pr-validation.yml` will build all 6 mods.
5. Get review, merge to main. **Do NOT bump CHANGELOG yet** — bump it only
   when you're ready to ship to dev.

When you're ready to deploy:

1. Run `scripts/bump-version.sh X.Y.Z` (updates pack.toml + cobblemon-npc's
   gradle.properties).
2. Edit `CHANGELOG.md`: move items from `[Unreleased]` into a new
   `## [X.Y.Z] - YYYY-MM-DD` section.
3. Commit:
   ```sh
   git commit -am "release: X.Y.Z"
   git push origin main
   ```
4. `deploy-dev.yml` fires automatically; cobblemon-dev gets the new build
   within ~3-4 min.
5. Connect to dev with your NeoForge MC client; smoke-test.
6. Tag the same version: `git tag vX.Y.Z && git push --tags`. `release.yml`
   drafts a GitHub Release with the .mrpack and individual jars.
7. From the GitHub Actions UI, run `Deploy prod` and supply the tag.

## Adding a new mod (your own)

1. Create `custom-mods/<new-name>/` mirroring `custom-mods/cobblemon-npc/`'s
   layout (gradle wrapper, `gradle.properties`, `build.gradle.kts`,
   `src/main/...`, `src/main/resources/META-INF/neoforge.mods.toml`).
2. Build `./gradlew build` to confirm.
3. PR + bump CHANGELOG as above. CI picks up new modules under
   `custom-mods/*/` automatically — no workflow changes needed.

## Adding a third-party mod

Use packwiz against the `modpack/` dir:

```sh
cd modpack
~/go/bin/packwiz mr add <modrinth-slug>
~/go/bin/packwiz refresh
```

Commit the new `mods/<mod>.pw.toml` and the updated `index.toml`.

For Fabric mods running through Sinytra Connector (e.g. `cobblemon-economy`,
`legendary-monuments`), packwiz blocks `mr add` because the loader isn't a
match. Hand-write the `.pw.toml` instead — see `mods/cobblemon-economy.pw.toml`
for the template (filename, sha512, modrinth project + version IDs).

## Sensitive Files

`server.properties` is tracked but **do not commit RCON passwords or other
secrets**. The deployer SSH key, RCON passwords, and OAuth client secrets
live on the VM and never enter the repo.
