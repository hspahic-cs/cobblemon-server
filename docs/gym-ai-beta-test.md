# Gym AI Beta Test — Tester Guide

You're testing the new gym-leader AI. Every gym leader now plays with a real
search engine instead of scripted moves. Your job: **fight all 24 leaders and
write down anything that looks wrong.** Every battle is automatically logged
on the server, so your notes + the gym name are enough for us to replay the
exact moment something went weird.

## Setup (once)

1. Download `Cobblemon.Server-0.8.1.mrpack` (or newer) from the
   **dev-latest** release: <https://github.com/hspahic-cs/cobblemon-server/releases/tag/dev-latest>
2. PrismLauncher → **Add Instance → Import** → pick the `.mrpack`
3. Connect to the dev server: `<DEV SERVER ADDRESS>`
4. Make sure you have op (ask Harris) — the spawn commands need it
5. Bring whatever team you like — the AI adapts to any team

## How to test

Stand somewhere open, then spawn the leaders **3 at a time**:

```
/function server:aitest/spawn_1
```

Fight all three (right-click a leader to battle), then the chat message tells
you the next command (`spawn_2`, `spawn_3`, … up to `spawn_8`). When you're
completely done:

```
/function server:aitest/cleanup
```

The chunks:

| Command | Leaders |
|---|---|
| `spawn_1` | Clay, Gardenia, Korrina |
| `spawn_2` | Byron, Blaine, Volkner |
| `spawn_3` | Crasher Wake, Sabrina, Drayden |
| `spawn_4` | Morty, Viola, Cheren |
| `spawn_5` | Koga, Grant, Skyla |
| `spawn_6` | Brycen, Valerie, Marnie |
| `spawn_7` | Oak, Lorelei, Cynthia |
| `spawn_8` | Agatha, Lance, Champion |

You can heal between fights; leaders don't need to be beaten in order. If a
battle breaks completely, `/function server:aitest/cleanup` + re-spawn that
chunk gives you a fresh copy of the leader.

## What's NORMAL (don't report these)

- **Turns take ~2–3 seconds** — the AI is thinking, that's expected
- Smart switching (pivoting to a counter after seeing your move)
- The AI knowing your team — it plays "open team sheet" by design
- Losing. They're supposed to be hard now.

## What to REPORT

- A Pokémon **stands there doing nothing** (turn skipped)
- The AI **only switches, never attacks** for many turns in a row
- Same move spammed into something it can't hurt (e.g. Normal move into a Ghost)
- The battle **freezes/softlocks** — your move buttons never come back
- Turns suddenly taking 10+ seconds
- Anything that just feels dumb or broken — when in doubt, write it down

---

# Test Results

> Copy everything below into your own doc, fill it in as you go, send it back.

**Tester:** _________  **Date:** _________  **My team:** _________

## Quick checklist

| # | Leader | Fought? | W/L | Verdict (✅ fine / 🤔 weird / ❌ broken) |
|---|---|---|---|---|
| 1 | Clay | ☐ | | |
| 2 | Gardenia | ☐ | | |
| 3 | Korrina | ☐ | | |
| 4 | Byron | ☐ | | |
| 5 | Blaine | ☐ | | |
| 6 | Volkner | ☐ | | |
| 7 | Crasher Wake | ☐ | | |
| 8 | Sabrina | ☐ | | |
| 9 | Drayden | ☐ | | |
| 10 | Morty | ☐ | | |
| 11 | Viola | ☐ | | |
| 12 | Cheren | ☐ | | |
| 13 | Koga | ☐ | | |
| 14 | Grant | ☐ | | |
| 15 | Skyla | ☐ | | |
| 16 | Brycen | ☐ | | |
| 17 | Valerie | ☐ | | |
| 18 | Marnie | ☐ | | |
| 19 | Professor Oak | ☐ | | |
| 20 | Lorelei | ☐ | | |
| 21 | Cynthia | ☐ | | |
| 22 | Agatha | ☐ | | |
| 23 | Lance | ☐ | | |
| 24 | Champion | ☐ | | |

## Issues

> One block per issue. Approximate time helps but the gym name is the
> important part — we can find the battle from the logs.

### Issue 1
- **Gym:**
- **Roughly when (your local time):**
- **What happened:**
- **What I expected:**

### Issue 2
- **Gym:**
- **Roughly when:**
- **What happened:**
- **What I expected:**

(copy more blocks as needed)

## Overall impressions

> Difficulty? Fun? Too slow? Anything about how the AI *feels* to play against.
