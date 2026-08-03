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
| Cost | **Slots are permanent one-time unlocks sold at the Shopkeeper's Upgrades tab** (alongside `/sethome` slots), on a rising ladder (`draftSlotCosts`, default 25k → 50k → 100k → 200k → 300k, then +100k per extra slot). Ranked owns the ladder + slot state; the market reads/grants through a reflection bridge (`DraftSlotBridge`, grant-then-charge like home slots). `/ranked draft create` never sells slots — it places a team into an owned empty slot for `draftRefillCost` (default 10k; same for first fill and refills). Deleting empties the slot but you keep it. **Edits: flat `draftEditCost` (default 10k), with one free edit per team** (a new team in the slot gets a fresh free edit). No per-battle charge (never a reason not to queue). No refunds. `/ranked admin grantdraftslot <player>` is the operator fallback. |
| Slots | `maxDraftSlots` config (default 10; ladder extrapolates slots 6-10 to 400k…800k). Owned-slot count persists per player (`ownedSlots` in the drafts file) and never decreases. |
| Churn guard | Without one, a player could re-team one cheap slot daily and never climb the ladder. Decided 2026-07-31: an edit keeping **≥4 of 6 species is a "tune"** — unrestricted (first free, then `draftEditCost`). Keeping fewer is a **team swap**: costs `draftSwapCost` (default 50k) AND is allowed once per slot per `draftIdentityCooldownHours` (default 168 h = 7 days). Deleting a draft leaves the freed slot **locked until the departed team's cooldown ends** (`slotLocks`), so delete + create can't dodge the timer. A swap/create restarts the slot's clock and grants a fresh free tune. |
| Where usable | Ranked team-select **and** tournaments, via the existing rental picker ("My Drafts" row). Tournament entries store `draft:<id>` and resolve the draft fresh each match — editing a draft mid-tournament changes the roster (accepted; locking was not worth the machinery). |
| Authoring | **Showdown teambuilder paste** written into a book & quill, imported with `/ranked draft create <name>` while holding the book. No in-game teambuilder GUI (huge effort, worse UX than Showdown's own builder). |
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
