# PokéRogue reskin overlays

Server-flavored text patches applied to the upstream frontend checkout by
`../apply-reskin.py` (wired into `build-and-stage.sh` before `pnpm build`).
Idempotent: each target file is restored from git first, then re-patched —
edit here and rebuild, nothing accumulates in the checkout.

**Everything in here is placeholder.** The real names/text are content the
human authors. Never put the server's real identity (IP/hostname) in overlays
that could leak it — display names and flavor text only.

## Layout

- `reskin.json` — non-JSON edits. Currently one key:
  - `"title"` — rewrites `index.html`'s `<title>` and the
    `title`/`og:title`/`twitter:title` metas (regex replace after a git
    restore of `index.html`).
- `<path>/<file>.json` — deep-merged into the file at the same path relative
  to the checkout. Keys you set win; everything else keeps the upstream value.
  Files under `locales/` live in the locales git submodule; the applier
  restores them there automatically.

## What to edit

- **Splash lines**: `locales/en/splash-texts.json`. Override *existing* keys
  only — the pool of keys is hardcoded in upstream
  `src/data/splash-messages.ts`, so a new key would never be picked. Some
  values use `{{placeholders}}`; plain overrides drop them, which is fine.
- **Trainer/rival names**: `locales/en/trainer-names.json`. Keys are upstream
  trainer ids (`brock`, `misty`, ..., `rival`, `rivalFemale`). Rename gym
  leaders and the rival to our server's gym leaders here.
- Any other locale file works the same way (e.g. `trainer-titles.json`,
  `trainer-classes.json`) — mirror its path under `reskin/`.

Locale JSONs are copied verbatim into `dist/locales/` and fetched at runtime,
so overrides are visible with a plain grep of the build output. English only
for now; other languages fall through to upstream text.
