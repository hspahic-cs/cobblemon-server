# BP (Battle Points) Currency System Design

**Date**: 2026-07-12  
**Status**: Approved for implementation

## Overview

A new currency system (BP) for tournament rewards. Admins grant BP to players after tournaments, which can be spent at a configurable NPC shop in the market for items and vouchers. Some vouchers integrate with existing vendor systems (TR vendors, held item vendors).

## Components

### 1. BP Storage & Persistence

**Storage**: Flat JSON file per player
- Location: `bp/<player-uuid>.json`
- Format: `{ "balance": 100 }`
- Auto-creates on first grant
- Persists across server reloads

**Why**: Simple, independent from Essentials, easy to reset/manage per tournament

### 2. Admin Commands

**Command namespace**: `/ranked bp`

Commands:
- `/ranked bp add <player> <amount>` — Adds BP to player's balance
- `/ranked bp set <player> <amount>` — Sets balance to exact amount

Example:
```
/ranked bp add Almutwakel 50    # Almutwakel now has +50 BP
/ranked bp set Almutwakel 100   # Almutwakel now has exactly 100 BP
```

### 3. BP Shop NPC & Configuration

**Configuration file**: `bp-items.json`

Structure:
```json
{
  "items": {
    "masterball": { "cost": 10 },
    "ultra_key": { "cost": 10 },
    "pokemon_crate": { "cost": 1 },
    "rare_key": { "cost": 2 },
    "tr_voucher": { "cost": 2, "isVoucher": true, "voucherType": "tr" },
    "held_item_voucher": { "cost": 2, "isVoucher": true, "voucherType": "held_item" },
    "ability_patch": { "cost": 5 },
    "shiny_voucher": { "cost": 30, "isVoucher": true, "voucherType": "shiny" },
    "rare_candy": { "cost": 1 }
  }
}
```

**Shop NPC**: 
- Tagged `cobblemon_bridge.market_vendor.bp_shop`
- Spawned in market with `/market admin spawn bp_shop`
- Right-click opens menu showing only BP-shop items
- Uses existing market menu UI

**Purchase flow**:
1. Player clicks item in menu
2. System checks: does player have X BP?
3. If yes: deduct BP, give item (or voucher with NBT tag)
4. If no: show "insufficient BP" message
5. Menu updates in real-time

### 4. Voucher Items

**Voucher types**: `tr_voucher`, `held_item_voucher`, `shiny_voucher`

**NBT tag structure** (prevents forging):
```
{
  "bp_voucher": true,
  "voucher_type": "tr"  // or "held_item", "shiny"
}
```

**Behavior**:
- Stack in player inventory like normal items
- Cannot be crafted/duped (NBT tag + custom item validation)
- Consumed by vendors when used (removed from inventory)
- Display name in inventory: e.g., "TR Voucher (2 BP value)"

### 5. Vendor Integration

#### TR Vendors
- Check for `tr_voucher` items in player inventory before charging currency
- If voucher present: consume 1 voucher, dispense TR
- If no voucher: charge normal currency cost
- GUI shows both options: "Use voucher" or "Pay currency"

#### Held Item Vendors
- Check for `held_item_voucher` items in player inventory before charging currency
- If voucher present: consume 1 voucher, dispense held item
- If no voucher: charge normal currency cost
- GUI shows both options: "Use voucher" or "Pay currency"

#### Shiny Voucher
- Non-self-service: admin uses the voucher manually when turning a Pokémon shiny
- Held in inventory until admin validates and consumes it
- Validation: check NBT tag, remove from inventory

## Data Flow

```
Admin grants BP
    ↓
/ranked bp add <player> <amount>
    ↓
BP file updated: bp/<uuid>.json balance += amount
    ↓
Player visits BP shop NPC (tagged bp_shop)
    ↓
Menu opens (market menu, scoped to bp_shop vendor)
    ↓
Player clicks item
    ↓
Check: balance >= cost?
    ├─ YES: deduct BP, give item/voucher (with NBT tag)
    └─ NO: show insufficient BP message
    ↓
For vouchers: vendor checks inventory for matching NBT tag
    ├─ Found: consume voucher, dispense item/service
    └─ Not found: charge currency instead
```

## File Structure

```
custom-mods/cobblemon-ranked/
├── src/main/kotlin/com/cobblemonranked/bp/
│   ├── BpManager.kt                 # Core BP storage/access
│   ├── BpCommands.kt                # /ranked bp add/set commands
│   └── BpShopProvider.kt            # Menu provider for BP shop
│
custom-mods/cobblemon-market/
├── src/main/kotlin/com/cobblemonmarket/bp/
│   ├── BpVoucherValidator.kt        # NBT tag validation + consumption
│   └── VendorIntegration.kt         # TR/held-item vendor patches
│
config/
├── bp-items.json                    # Shop item prices & configuration
│
bp/
└── <player-uuid>.json               # Per-player BP balance (auto-created)
```

## Testing Scenarios

1. Admin grants BP: `/ranked bp add testplayer 100`
2. Player visits shop, sees 100 BP balance
3. Player buys TR Voucher (2 BP): balance → 98 BP, voucher in inventory
4. Player visits TR vendor with voucher
5. TR vendor consumes voucher, gives TR (no currency deducted)
6. Voucher removed from inventory
7. Admin grants Shiny Voucher (30 BP)
8. Admin uses voucher to turn Pokémon shiny (manual process)
9. Shiny Voucher consumed

## Success Criteria

- [ ] `/ranked bp add` and `/ranked bp set` work
- [ ] BP persists across reloads
- [ ] BP shop NPC shows correct items and prices
- [ ] Players can buy items/vouchers with BP
- [ ] Vouchers have NBT tags preventing duping
- [ ] TR vendors accept and consume TR vouchers
- [ ] Held item vendors accept and consume held item vouchers
- [ ] Shiny vouchers can be manually consumed by admins
- [ ] All fallback to currency when voucher not present
