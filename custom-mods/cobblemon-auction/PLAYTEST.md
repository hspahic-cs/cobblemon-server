# 🪙 Auction House — Playtest Guide (dev)

A new **Auction House** is live on the **dev** server for testing. Players can sell items to
each other for in-game money. We need you to try it and report anything weird — especially
around setting a price and collecting your stuff.

**Currency is your normal money balance** (check it with `/balance`). Sales pay the seller
directly; items you buy wait for you in a **Mailbox**.

---

## Getting to it

Right-click the **Auctioneer** (the villager with the cyan name at the test spot). That opens
the Auction House window. Everything happens in that menu — no commands needed.

> Admins: spawn/remove the NPC with `/auctionadmin spawn` and `/auctionadmin delete`.

---

## Please run through these and report what happens

**1. Sell an item**
- Hold any item in your main hand (try a **stack**, and separately something **enchanted or renamed**).
- Click **Sell Held Item** (emerald).
- An anvil opens — type a **whole-number price** in the text box, then click the paper on the right to confirm.
- ✅ The item should leave your hand and show up in the listings for everyone.
- ✅ A **listing fee** (5% of your price, min $1) is deducted when you confirm — the confirm paper shows the amount.

**2. Back out of a sale (item should come back)**
- Start a sell, but **close the anvil** (Esc) instead of confirming.
- ✅ Your item should be returned to you (in your inventory, or your Mailbox if your inventory is full).

**3. Buy something (needs a second player)**
- Have someone else list an item. Open the Auctioneer, **left-click** the listing, then **left-click again** to confirm.
- ✅ Your money drops by the price, the seller gets paid, and the item lands in your **Mailbox** (cyan ender-chest button, top row).
- ✅ **Fee refund:** when it sells, the seller gets their listing fee back (they receive price + fee). If a listing **expires or is cancelled**, the fee is *not* refunded.

**4. Collect from your Mailbox**
- Open the **Mailbox** and left-click an item to collect it.
- ✅ It goes into your inventory (overflow drops at your feet — nothing should ever be lost).

**5. Cancel your own listing**
- Open **Your Listings** (book button), left-click a listing to cancel.
- ✅ The item comes back to your **Mailbox** (not destroyed).

**6. Try to break it** (these should be blocked gracefully, not crash):
- Buy something you **can't afford** → should say you don't have enough money.
- Try to **list an item when you can't afford the fee** (get your balance near $0) → listing refused, item returned.
- Try to **buy your own** listing → should tell you to cancel it instead.
- Try to sell a **Poké Ball** → should be refused (Pokémon/Poké Balls can't be listed for now).
- Type a **silly price** (0, letters, huge number) → the confirm button shouldn't appear / should refuse.
- **Log out** right after clicking Sell (before confirming a price), then log back in → your item should be in your Mailbox.

---

## What to report

Tell us (with a screenshot if you can):
- Did **typing the price** and **confirming** work smoothly? Any items that **disappeared**?
- Did **enchanted / renamed** items keep their name/enchants after buying?
- Was the **money** always correct (buyer charged, seller paid, exactly once)?
- Anything confusing, any error message, or any crash.

Thanks! This is brand-new and hasn't had a real in-game shakedown yet — your testing is the verification. 🙏

---

## Quick Checklist (TL;DR)

- [ ] **List** — hold item, *Sell*, type price, confirm → item leaves hand, shows in listings, fee deducted
- [ ] **Cancel a sell** — close the anvil without confirming → item returned
- [ ] **Buy** (2nd player) — left-click a listing, left-click again → money moves, item in **Mailbox**
- [ ] **Collect** — Mailbox → click item → into inventory
- [ ] **Cancel listing** — *Your Listings* → cancel → item back in Mailbox (fee **not** refunded)
- [ ] **Fee refund** — a listing that **sells** refunds the seller's fee (price + fee); expired/cancelled keeps it
- [ ] **Can't afford** — buy too pricey → blocked; list with ~$0 balance → fee refused, item returned
- [ ] **Own listing** — buying your own → refused
- [ ] **Blocked item** — sell a Poké Ball → refused
- [ ] **Bad price** — 0 / letters / huge number → confirm won't go through
- [ ] **Enchanted/renamed** — list one, buy it → name + enchants survive
- [ ] **Logout mid-sell** — *Sell* then log out before pricing → item in Mailbox on return

**Report:** any item that disappears, wrong money, lost enchant/name, or a crash.
