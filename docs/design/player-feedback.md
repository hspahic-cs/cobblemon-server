# Player feedback (bug reports + suggestions)

## Structure

```
                                ┌────────────────────────────────────┐
   /feedback bug <text>         │ cobblemon-feedback (server-side)   │
   ──────────────────────▶      │                                     │
                                │  1. Capture metadata (party, coords,│
                                │     TPS, recent chat, log tail)     │
                                │  2. POST to GitHub Issues API       │
                                │  3. Reply in chat with issue URL    │
                                └────────────┬───────────────────────┘
                                             │
                                             ▼
                            github.com/hspahic-cs/cobblemon-server
                                  · labeled `bug` or `suggestion`
                                  · markdown body, threaded comments
```

## Summary

Players hit a bug or have an idea. They run `/feedback bug <text>` or
`/feedback suggest <text>` from in-game chat. The server captures everything
useful about the moment (where they were, what their party was, what the
server was doing) and opens a labeled GitHub Issue automatically. Devs see
the issue in the repo's **Issues** tab and triage from there.

Phase 1 (current) is server-side capture only — no screenshots. Phase 2
will add a companion client mod that captures a screenshot and uploads it
to Cloudflare R2, with the URL embedded in the issue body.

## Player flow

1. `/feedback bug The trainer at gym 3 walks through the wall`
2. Chat: "Submitting your bug to the dev team…"
3. ~2 seconds later: "✓ Submitted! https://github.com/hspahic-cs/cobblemon-server/issues/N"
4. Done. Per-player cooldown of 60s prevents spam.

## What's in each issue

- **Type:** bug / suggestion
- **Player:** username + UUID
- **Submitted:** UTC timestamp
- **Server version:** read from `.deployed_version` on the VM
- **Mods loaded:** count
- **Dimension / biome / coords**
- **TPS:** average over last ~100 ticks
- **Description:** the player's text
- **Pokémon party:** species + level for each slot
- **Recent chat:** last 20 lines from the server (configurable)
- **Server log tail:** last 30 lines from `logs/latest.log`

## Reference

### Configuration

Per-instance (token is sensitive, so this is **runtime** config — see
[mod-state-vs-config](../dev/conventions/mod-state-vs-config.md)):

```
/opt/cobblemon-{dev,prod}/config/cobblemon-feedback/runtime/config.json
```

```json
{
  "githubRepo": "hspahic-cs/cobblemon-server",
  "githubToken": "github_pat_…",
  "bugLabel": "bug",
  "suggestionLabel": "suggestion",
  "cooldownSeconds": 60,
  "chatBufferSize": 20,
  "logTailLines": 30
}
```

The token is a fine-grained GitHub PAT scoped to **Issues: Read and write**
on the cobblemon-server repo. Rotate annually.

### Token setup

1. https://github.com/settings/personal-access-tokens → Generate new token (fine-grained)
2. Resource owner: hspahic-cs
3. Repository access: only `cobblemon-server`
4. Permissions: Issues = Read and write
5. Expiration: 1 year
6. SCP into `/opt/cobblemon-{dev,prod}/config/cobblemon-feedback/runtime/config.json`
7. `chmod 600` the file

### Triage workflow

Issues land in https://github.com/hspahic-cs/cobblemon-server/issues with
`bug` or `suggestion` labels. Devs:

1. Read the metadata to understand context
2. Add area labels (e.g. `area-market`, `area-gym`) for sorting
3. Comment on the issue or assign yourself
4. Close on PR merge using `Fixes #N` in commit messages

### Rate limiting

Per-player 60-second cooldown on `/feedback`. Configurable via
`cooldownSeconds` in config. Set to 0 to disable.

### What's NOT in the report (Phase 1)

- No client-side screenshot
- No client log
- No GPU/driver info
- No player's keybind / control state

Adding these requires a companion client mod and is deferred to Phase 2.

### Privacy

This repo is **public**. Player username + UUID are NOT included in
issue bodies — they're replaced by an HMAC-derived `anon-XXXXXXXX`
reporter ID. Maintainers reverse the lookup with op-only `/feedback whois`
(in-memory, since last server start) or by grepping the runtime audit
log at `config/cobblemon-feedback/runtime/audit.log`. See
[player-feedback-phase2.md](player-feedback-phase2.md) for the full
anonymization design.
