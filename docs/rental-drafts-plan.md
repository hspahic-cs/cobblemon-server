# Draft Teams (custom rentals) — decision record

Decided 2026-07-31. This is the authority for the feature; read it before proposing changes.

## Problem

Building a real competitive team (breeding IVs, EV training, hunting items) is a big
investment, so players settle on one team and the ranked ladder goes stale. The four prebuilt
rental teams help newcomers but don't let anyone *try* a team of their own design.

## Feature

Players can **draft** a fully custom team — any species/EVs/abilities/items within server
rules — pay a fee, and battle with it in ranked and tournaments through the existing rental
flow. If they fall in love with the team, they commit: build the real thing themselves, at
full power. Drafts exist to de-risk that investment, not replace it.

## Decisions

| Decision | Choice |
|---|---|
| Power level | Same de-tune as prebuilt rentals: EVs clamped to 168/stat, IVs flat 25, level = `levelCap`. Applied **server-side at build time** — never trusted from input. Hand-raised teams keep their edge. |
| Legality | Ranked banlist (`rankedBanReason`), ≤ `maxLegendaries` (1) counting Legendary/Mythical/Paradox, ≤ 1 Mega stone, 6 unique species, moves/abilities legal for the species (checked against Cobblemon learnset/ability data — `PokemonProperties` alone would accept illegal sets, which would make drafts *stronger* than real teams). **Any legendary not on the banlist is draftable** (revised 2026-07-31 — no monument-obtainable allowlist; note this admits one Paradox as the team's legendary, unlike the hand-authored prebuilts). |
| Cost (v2, 2026-08-03) | **Slots are permanent one-time unlocks sold at the Shopkeeper's Upgrades tab** (`draftSlotCosts` 20k → 40k → 60k → 100k, flat 100k beyond; ranked owns ladder + state, market reads/grants via `DraftSlotBridge`, grant-then-charge). **Filling any unlocked empty slot is free** — no fill/refill fee exists. **Tunes** (≥4 of 6 species kept): flat `draftEditCost` (5k), never gated. **Team swaps** follow *wait = free, pay = instant*: free once the slot's `draftIdentityCooldownHours` (360 h = 15 days) elapses, else `draftSwapCost` (20k) — or one of the **free instant-swap credits granted per slot purchase** — to skip the wait. Principle: money and time are the only scarce resources; every free path costs time, every instant path costs money (this is what killed v1's fill/refill fees — a free refill path made them unpayable). No per-battle charge. No refunds. `/ranked admin grantdraftslot <player>` is the operator fallback (also grants the credit). |
| Slots | `maxDraftSlots` config (default 10). Owned-slot count and swap credits persist per player (`ownedSlots`/`swapCredits` in the drafts file); owned never decreases. |
| Churn guard | The identity cooldown is the whole guard: a slot takes a new team identity for free only once per 15 days, deletes leave the freed slot **locked for the remainder** (`slotLocks`), and skipping the wait costs real money or a bounded credit (max 10 lifetime). Simulated exploits and their answers: single-slot cycler → gated by the lock; swap-fee arbitrage vs delete+refill → both paths identical (free after cooldown), fee only buys speed; banked-credit counter-picking → bounded at 10 ever + tournament freeze. |
| Where usable | Ranked team-select **and** tournaments, via the existing rental picker ("My Drafts" rows). Tournament entries store `draft:<id>` and resolve fresh each match. **Identity freeze (v2): while a draft is a player's locked entry in an open or auto-running tournament, swaps and deletes are blocked (tunes allowed)** — revokes the v1 "editing mid-tournament is accepted" ruling, which was only safe when swaps were slow and expensive. Manual admin-run matches after close are outside the freeze (admin-supervised). |
| Authoring | **Showdown teambuilder export via pokepast.es link**: `/ranked draft create <name> <link>` — the server fetches the paste async, host-locked to pokepast.es (home-hosted box ⇒ free-form URLs would be SSRF). Book & quill paste remains a fallback (0.35.1: it demoted to fallback because Minecraft truncates clipboard pastes to one book page). No in-game teambuilder GUI (huge effort, worse UX than Showdown's own builder). |
| Commit path | `/ranked draft export <name>` prints the build sheet and a click-to-copy Showdown paste with the player's **original** (raw, up-to-252) EV spread — the draft file stores raw EVs and only the battle build de-tunes, so the export is the exact shopping list for the real team. |

## Rejected

- **In-game GUI teambuilder** — massive UI project in chest-menu form; Showdown's builder is
  strictly better and players already use it.
- **Per-battle rental fee** — taxes exactly the behaviour the feature exists to encourage.
- **Flat creation fee** (first design) — replaced by the rising slot ladder: the first draft is
  cheap enough to just try, hoarding a stable gets expensive.
- **Slot count from current drafts** (second design) — deleting dropped you back down the
  ladder, so one-slot churners never paid more than the bottom rung. Replaced by permanent
  slot ownership + cheap refills: the ladder is paid exactly once per slot, ever.
- **Full-price edits** (first design) — replaced by one free edit per draft + cheap flat fee;
  the slot ladder already carries the investment weight.
- **Monument-legendary allowlist** (first design) — dropped; the ranked banlist plus the
  1-legendary cap is the whole species rule, matching what real teams face. 

## Implementation map (custom-mods/cobblemon-ranked)

- `rental/ShowdownPasteParser.kt` — paste → `RentalMon` list (raw EVs), page-boundary tolerant.
- `rental/DraftTeams.kt` — per-player store (`runtime/drafts/<uuid>.json`), validation,
  slot-ladder pricing, de-tuned build (`draft:` id resolution).
- `config/RankedConfig.kt` — `allowDraftTeams`, `draftSlotCosts`, `draftEditCost`, `maxDraftSlots`.
- `commands/RankedCommands.kt` — `/ranked draft create|edit|delete|list|export`.
- `gui/RentalTeamMenu.kt` — "My Drafts" row for both ranked (`onConfirm`) and tournament
  (`onPickTeam`) flows.
- `tournament/TournamentManager.kt` — `resolveRoster` understands `draft:` ids.
- `custom-mods/cobblemon-market`: `economy/DraftSlotBridge.kt` (reflection to `DraftTeams`
  `@JvmStatic` API) + a "Draft Team Slot" item in `MarketMenu`'s Upgrades tab.
