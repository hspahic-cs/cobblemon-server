# Player Auction House (`cobblemon-auction`)

A persistent, player-to-player market. Players list items (item + quantity) for a
price; the item leaves their inventory and goes live. Other players browse and buy;
purchased items land in a per-player **mailbox** to retrieve. Sale proceeds pay
straight into the seller's balance.

> Not to be confused with the existing **`cobblemon-market`** mod, which is a
> dynamic-pricing *server* NPC shop. This is a separate mod (`cobblemon_auction`)
> so the modids don't collide. It reuses that mod's infrastructure patterns.

## Decisions (locked)

| Question | Choice |
|---|---|
| Price entry | **Anvil text input** — sell flow pops a vanilla Anvil rename box to type the price. No chat commands. |
| Buy granularity | **Whole bundle only** — a listing is item+qty at one total price; buyer takes all of it. |
| Economy extras | **Expiry only** — listings expire after N days (config) and return to the seller's mailbox. No fees/tax in v1. |
| NPC provisioning | **Mirror the market vendor** — cobblemon-bridge entity tag + datapack summon-function pattern. |

## Stack / constraints

- NeoForge-only (Kotlin For Forge), MC `1.21.1`, NeoForge `21.1.227`, Java 21, Kotlin `2.2.20`, Cobblemon `1.7.3`.
- Server-only: `@Mod(MOD_ID, dist = [Dist.DEDICATED_SERVER])`, `displayTest = IGNORE_ALL_VERSION`.
- **No custom networking** — server-driven vanilla `ChestMenu`s; menu sync is automatic.
- Currency: **NeoEssentials Economy** via reflection — copy `EconomyBridge.kt` from `cobblemon-market`.

## Templates to copy

| Piece | Source |
|---|---|
| Mod scaffold / entrypoint / build | `custom-mods/cobblemon-market/` |
| Economy reflection bridge | `cobblemon-market/.../economy/EconomyBridge.kt` |
| Browser + buy GUI | `cobblemon-market/.../gui/MarketMenu.kt` |
| Withdraw-enabled mailbox GUI | `cobblemon-gacha/.../gui/GachaChestMenu.kt` (relax extraction guards) |
| NPC interact hook | `cobblemon-market/.../gui/MarketNpcHook.kt` |
| Config/runtime path split | `cobblemon-market/.../internal/ConfigPaths.kt` + `docs/design/mod-state-vs-config.md` |
| JSON store (save-on-mutate) | `cobblemon-market/.../data/MarketStore.kt` |

## Data model

Persisted as Gson JSON under `config/cobblemon_auction/runtime/` (runtime = never
touched by deploys; see `docs/design/mod-state-vs-config.md`).

```
Listing {
  id: UUID
  sellerUuid: UUID, sellerName: String
  item: <serialized ItemStack>, count: Int
  price: Long              // total, whole-bundle
  createdAt: epochMillis
  expiresAt: epochMillis   // createdAt + config.listingTtlDays
}
```

- `runtime/listings.json` — global list of active listings.
- `runtime/mailbox/<uuid>.json` — per-player pending item stacks (purchases +
  returned/expired/cancelled listings). Proceeds are **not** stored here; they pay
  into the seller's NeoEssentials balance at sale time.

### ItemStack serialization (the one net-new problem)

No existing store persists a full stack — they store item ids only. Listings must
preserve components (enchants, custom names, etc.), so serialize via
`ItemStack.CODEC` with `RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())`.
Round-trip fidelity is unit-tested.

## Flows (all entered from the Auctioneer NPC)

1. **Sell** — hold the item in your main hand, click *Sell Held Item* → the whole stack is
   escrowed at once (removed from hand) → Anvil box to type the total price → confirm writes a
   `Listing`. Held stack = the bundle/quantity. Blocklisted items rejected up front; closing the
   anvil without confirming (or logging out mid-entry) returns the escrowed stack to inventory, or
   to the mailbox if there's no room / you left. *(Held-hand rather than a deposit slot: no
   orphaned-item cleanup, and the anvil screen can't tamper with an already-escrowed item.)*
2. **Browse / Buy** — paginated 54-slot GUI of active listings (lore shows price +
   seller). Click → confirm → `EconomyBridge.subtractBalance(buyer, price)` must
   return true → `addBalance(seller, price)` → stack to **buyer's mailbox** → listing
   removed. Listing is re-validated on the confirm click to prevent double-buy.
3. **Mailbox** — withdraw-enabled chest GUI; click / "take all" moves stacks to the
   player inventory and persists the removal.
4. **My listings** — list the caller's active listings; cancel returns the stack to
   their mailbox.
5. **Expiry** — a periodic sweep (server tick, throttled) moves expired listings to
   the seller's mailbox.

## Safety

- **Blocklist** (config): disallow listing living Pokémon (filled Poké Balls) and
  other exploitable/bound items — Pokémon have their own trade path with level-cap
  rules. Default-deny anything on the list at both list-time and (defensively) buy-time.
- All state mutations run on the server thread → no locking; but every settle step
  re-reads current state before committing (no stale-listing double-spends).
- Economy degradation: if the reflection bridge can't reach NeoEssentials, **block
  purchases** (fail closed) rather than giving items away.

## Config (`authored/config.json`)

- `listingTtlDays` (default 7)
- `maxListingsPerPlayer` (default 10)
- `blocklist` (item ids / tags)
- `minPrice`, `maxPrice`

## Testing

JUnit 5 (`src/test/kotlin`) — the parts that run without booting Minecraft:
- `AuctionStore` / `MailboxStore`: save↔load round-trip, add/remove-once, `expired` cutoff,
  `bySeller`/`countBySeller` filtering.
- `AuctionConfig`: writes defaults on first boot then reads them back; blocklist matching; TTL floor.
- `Gui.timeLeft` bucket formatting.

Needs a live server, so verified at runtime rather than in JUnit: ItemStack CODEC round-trip,
the anvil price flow, and buy/settlement (Minecraft classes + NeoEssentials aren't on the test
classpath). Gson is pulled in as a `testImplementation` since MC provides it only at real runtime.

## Build / rollout

- `cd custom-mods/cobblemon-auction && ./gradlew build --no-daemon` → jar into `modpack/mods/`.
- CI auto-discovers `custom-mods/*/`. NeoEssentials is already in the modpack.
- NPC: `/auctionadmin spawn` (op) summons a persistent villager tagged `cobblemon_auction.auctioneer`
  (interaction) + `cobblemon_bridge.anchor.auction` (pins it via cobblemon-bridge's generic anchor,
  AI-on for idle head movement). `/auctionadmin delete` removes nearby auctioneers. This mirrors the
  market vendor's villager+tag approach rather than a datapack summon function.
- Deploy: a **new** `## [X.Y.Z]` heading in `CHANGELOG.md` triggers the dev auto-deploy; entries left
  under `## [Unreleased]` never deploy. Prod is manual. (This feature currently sits under
  `[Unreleased]` — no deploy until it's versioned.)

## Open / deferred (not in v1)

- Fees / sales tax (money sink) — deferred; hooks left in config shape.
- Partial-quantity buys — out of scope (whole-bundle only).
- Search / filtering in the browser — start with pagination only.
