# Player feedback — Phase 2 (screenshot capture + public-repo PII)

Phase 1 (server-side `/feedback` → GitHub issue) ships in 0.5.0+. This
doc specs the next-up changes.

## Goals

1. **Player-controlled screenshot attachment.** Press a key to capture
   the current frame; submit feedback later (with the screenshot still
   held in memory) when the inventory/dialog/menu is closed and chat is
   accessible.
2. **PII-safe issues now that the repo is public.** Username + UUID
   currently land in issue bodies. Strip both from the public-facing
   text; keep them recoverable via private metadata only the maintainer
   can read.

These two are independent and can ship in either order. Screenshot is
the user-visible feature; PII is a privacy hardening.

## 1. Screenshot capture

### Player flow

```
─[ in-game, on any screen ]─────────────────────────────────────────
                                               (player presses F2)
  → [vanilla MC chat] "Saved screenshot as 2026-05-28_15.31.42.png"
  → [our chat]        "📸 Captured for /feedback. Submit within 2 min."
─[ closes menu, opens chat ]────────────────────────────────────────
  /feedback bug Trainer at gym 3 walks through the wall
  → "Submitting your bug to the dev team…"
  → "📤 Uploading screenshot…"
  → "✓ Submitted! https://github.com/.../issues/N"

If no F2 was pressed in the last 2 minutes, behavior is identical to
today: text-only issue, no upload step. The screenshot is purely
additive — vanilla F2 still saves to disk regardless.
```

### What our client mod adds to F2

Minecraft's F2 keybind already calls `Screenshot.takeScreenshot(...)`
which renders the framebuffer to a `NativeImage` and writes a `.png`
to the screenshots folder. We don't replace that — we hook the same
event:

- Subscribe to `ScreenshotEvent` (NeoForge fires this when F2 is
  pressed and the image has been captured but before the file write
  finishes).
- Take a copy of the `NativeImage` and hold it in client-side memory
  under a per-player slot. Time-bounded: expires after 120 seconds.
- Print a small chat acknowledgment so the player knows the capture
  is queued for /feedback (separate from MC's own "Saved screenshot
  as ..." message).
- Don't touch the disk write — let MC's normal flow continue.

The 120s window is long enough that the player can navigate menus,
type a thoughtful description, and run `/feedback`, but short enough
that a forgotten capture from earlier in the session doesn't
accidentally attach.

### Why F2 not a new keybind

Players already know F2 = screenshot. No new muscle memory. Vanilla
MC's screenshot folder still gets the file (useful for the player's
own records). Our /feedback attachment is a free add-on to the same
keypress. If a player presses F2 to share something *not* related to
a bug, no problem — they just don't follow with /feedback and the
captured frame expires harmlessly after 2 minutes.

The keybind itself is rebindable in MC settings as usual; whatever the
player binds vanilla screenshot to, our hook follows.

### Architecture

This requires a **client mod**, since the server can't access the
client framebuffer. Two options:

#### A. New `cobblemon-feedback-client` mod (recommended)

Separate Fabric/NeoForge mod that:
- Registers the F7 keybind (client-only, via `KeyMappingRegistry`).
- Holds the captured `NativeImage` in a singleton object.
- Uses NeoForge's networking API to register a custom payload type
  `cobblemonfeedback:screenshot`. When the server's `/feedback` command
  runs, the server *requests* the screenshot from the client; if the
  client has one, it ships the bytes back.

**Why request/response from the server**: keeps all upload logic on
the server side. The client mod doesn't need any cloud credentials
embedded in the jar.

#### B. Extend `cobblemon-feedback` to also be client-loaded

Same code, runs on both sides. NeoForge supports `side = "BOTH"`
mods; we'd flag client-only steps with `DistExecutor.runForDist`.
Smaller deployment surface (one jar, one manifest entry) but
intermingled client/server code is harder to reason about.

**Pick A.** Cleaner separation, and we already have a precedent: the
five mods al wrote are split into independent modules.

### Network protocol

```
1. Client → Server: feedback packet
   - When: F7 pressed
   - Payload: { player_uuid, captured_at_epoch_sec }
   - Purpose: Tell server "I have a screenshot ready."
   - Server records this in a per-player ConcurrentHashMap with the
     captured-at timestamp.

2. Server → Client: request_screenshot packet
   - When: Player runs /feedback AND server saw step 1 within 120s
   - Payload: { request_id }
   - Purpose: Ask client to upload its held screenshot.

3. Client → Server: screenshot_data packet
   - Payload: { request_id, png_bytes }
   - Note: NeoForge custom payloads have a default 32 KB limit. PNG
     screenshots are typically 200 KB-2 MB. We split the upload into
     chunks: { request_id, chunk_index, total_chunks, bytes }, server
     reassembles. Upper bound: 8 MB total.

4. Server uploads PNG to backend (see "Storage backend" below) and
   appends the resulting URL to the GitHub issue.
```

### Storage backend

Three options, in order of preference:

#### Option 1: Cloudflare R2 (recommended)

- Server has an R2 access key in its `runtime/config.json` (already a
  per-instance secret, never shipped via deploy — same convention as
  the GitHub PAT).
- Server uploads PNG to a dedicated bucket like `cobblemon-feedback-screenshots`.
- Bucket has public-read enabled with a custom domain like
  `screenshots.cobblemon.example`. URL pattern:
  `https://screenshots.cobblemon.example/<issue-id>-<random>.png`.
- Server includes the URL in the issue body.
- Lifecycle rule: auto-delete objects after 365 days.

**Pros**: zero egress fees, fast, scales to thousands of screenshots
for ~$5/year. The credential lives only on the server (never shipped
in the client jar). No exposure to player decompilation.

**Cons**: requires an R2 account + bucket setup. Single bucket means
all images are publicly readable forever (or until lifecycle expiry).

#### Option 2: Imgur API

- Server uses the Imgur anonymous-upload API (no auth needed for the
  free tier, rate-limited to 1250 uploads/day per IP).
- URL is returned in the API response.
- Server includes the URL in the issue body.

**Pros**: no infra. Works tomorrow.
**Cons**: Imgur has occasionally killed third-party API access. Brittle.

#### Option 3: GitHub-attached image

- Upload PNG directly into the issue body via the
  `repos/<owner>/<repo>/contents/<path>` endpoint, committed to a
  branch like `feedback-screenshots`.
- Reference via raw URL.

**Pros**: lives in the repo, no third-party dependency.
**Cons**: clutters git history. Each screenshot is a permanent commit.
Public repo = images are public anyway, but the diff noise is real.

**Pick Option 1 (R2).** Cleanest separation, lowest cost, most flexible.
Imgur is the fallback if R2 setup is too much friction.

### Failure modes

| Failure | Behavior |
|---|---|
| F7 pressed but no screenshot held within 120s when /feedback runs | Submit text-only issue; chat says "(no screenshot attached)" |
| Client mod not installed (server-only player) | F7 does nothing client-side; server's /feedback works text-only |
| R2 upload fails (network / quota / 5xx) | Submit text-only issue; chat says "Screenshot upload failed; submitted without it" |
| Screenshot >8 MB after PNG encode | Reject in client; chat says "Screenshot too large to attach" |
| Player runs /feedback while a chunked upload from a prior /feedback is mid-flight | Cancel the prior upload, start fresh |

### Out of scope

- Multi-screenshot per feedback (one image per issue is fine for v1)
- Editing/cropping the screenshot before upload
- Image annotation tools

## 2. Public-repo PII fix

### What's currently exposed

Every issue body contains:
```
**Player:** Harris (`a8f3...`)
...
*Submitted via in-game /feedback bug by Harris*
```
And the title:
```
[bug] Trainer walks through wall — by Harris
```

`Harris` is the in-game username (not real name in this case, but
*could be* if a player chose their real name). The UUID is the
Minecraft account UUID — used for skin/cape lookups via the Mojang API,
publicly resolvable to the username.

### Risk model

**Low**: someone reading public issues sees Minecraft usernames and
UUIDs. Not credentials, not addresses, not anything financially useful.
But: tying a real player's identity to a public bug report ("Harris
has terrible WiFi, look at the TPS log") is mildly antisocial.

**Mitigation**: keep username/UUID out of the public issue body; embed
them in a **private fragment** the maintainer can recover when needed.

### Approach: HMAC-encoded reporter ID

Replace `**Player:** Harris (a8f3...)` with `**Reporter:** anon-7f3e2c1b`
in the public body. The hash is `HMAC-SHA256(secret_key, uuid)[:16]`.

Maintainer flow when triaging:
1. See `Reporter: anon-7f3e2c1b` in the issue.
2. Need to follow up with the player ("can you reproduce X?").
3. SSH to the server, run `/feedback whois anon-7f3e2c1b` (op-only
   command, decodes the hash by checking against currently-online
   players' hashes — server holds the secret).
4. Or check the server-side log of `/feedback` invocations
   (`logs/feedback-audit.log`) which keeps the full mapping.

The secret is per-instance — same `runtime/config.json` as the GitHub
token. New install → new secret → new anon-IDs (intentional; players
can't be tracked across server resets).

### Other PII to scrub

- **Recent chat**: currently dumps the last 20 lines verbatim in the
  issue body. Other players' usernames appear in chat. Strip to
  `<player1>`, `<player2>` aliases — same anonymization scheme.
- **Coords/dimension**: keep. Useful for repro, not personally
  identifiable.
- **Party (Pokémon species + level)**: keep. Not personally identifying.

### Already-exposed history

The current issues filed during the private-repo era have real
usernames. Two options:
1. Leave them; they're already shipped to the world.
2. Programmatically rewrite all open issues to use anon-IDs (one-shot
   migration script). Closed issues stay as-is (they're archive).

**Recommend option 1.** Migration is more disruptive than the harm
warrants, and we're not seeing organic traffic yet.

## Phasing

| Phase | Scope | When |
|---|---|---|
| 2a | PII anonymization (HMAC reporter ID, chat scrub) | Next sprint. Trivial. |
| 2b | New `cobblemon-feedback-client` mod, F7 keybind, screenshot held in memory, basic chunked-payload protocol | Following sprint. ~200 lines of Kotlin + manifest. |
| 2c | R2 bucket setup, server-side upload step, URL in issue body | Bundled with 2b once R2 is provisioned. |

## Decisions captured (2026-05-28)

- **Keybind**: hook MC's vanilla F2 screenshot. No new keybind.
- **Frame contents**: capture exactly what F2 captures — the rendered
  frame as-is, including HUD, open menus, F3 overlay, chat. Same
  contract as vanilla F2: "what I see is what gets saved." Lets the
  player consciously frame the bug (open the menu they want shown,
  toggle F3 if they want coords visible). No surprise privacy
  surface — players already understand F2 semantics.
- **Cloud storage**: new Cloudflare R2 setup. R2 bucket + access key
  to be provisioned during 2c implementation.
