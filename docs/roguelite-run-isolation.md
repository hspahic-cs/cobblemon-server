# Roguelite run isolation: party and Minecraft inventory

Design document, revised 2026-07-31 after an adversarial review that read the deployed modpack and
the code and found the first draft unimplementable in five places. The revision record matters:
where a section below reverses the first draft, it says so and says why, in the same spirit as
§2.2's own reversal note. No code ships with this file. The authority for every §N.N reference is
`docs/pokerogue-mode-plan.md`; prior art is `docs/roguelite-prior-art.md`; arena facts are
`docs/roguelite-arena-spec.md`. Where this document and the code disagree once something is built,
whichever is *newer* is suspect — say so rather than silently trusting either.

Three decisions from the human are settled and everything below builds to them:

- **D1. Pause teleports the player out of the arena.** The current `RunController.pause` not doing
  so is the bug, not the spec. Pause is a real arena exit.
- **D2. Teleport commands are refused during an arena session** (`/home`, `/warp`, `/spawn`,
  `/tpa`, `/back`, `/enderchest`, configurable), rather than trying to catch every dimension
  change. Slipped-through teleports (other mods, op `/tp`, portals) are still handled — §7.3.
- **D3. Accessories and Sophisticated Backpacks are supported properly** — worn slots are
  snapshotted and cleared. A run never refuses to start because someone is wearing a backpack.
  Both mods are live on this server (`modpack/mods/accessories.pw.toml`,
  `sophisticated-backpacks.pw.toml`, both `side="both"`); the first draft's claim that they were
  publication-only was simply wrong.

## 0. The contract, restated as three invariants

1. **A player can never lose their real Pokémon or items.** Not to a crash, a `kill -9`, a
   disconnect mid-swap, a full disk, a mod update, or an operator deleting run state. "Lose" is
   measured on disk, not in memory: any window in which our data is the only copy of a player's
   property and that data is not yet durable is a violation, whether or not anything goes wrong.
2. **Nothing of value leaves a run** (§1.1): no item, no Pokémon, no XP, no Pokédex entry, and the
   easy ones to forget — no advancement, statistic, or third-party progression counter that gates
   anything. The payout (§2.20) and the progression channels (§2.13/§2.15/§2.17) are the
   deliberate, metered exceptions.
3. **Nothing comes in.** No personal bag (§2.11), no personal Pokémon in the run party, no potion
   effects drunk in advance, no worn backpack full of revives. This is the currently-broken
   invariant: a player today walks into a run with their full inventory, which is exactly why they
   could Dynamax — Mega Showdown gates Dynamax on holding a `dynamax_band` near a `power_spot`,
   and the arena supplies the power spot. Stripping the bag is what enforces the server's gimmick
   rules inside runs.

**Threat model.** The adversary is (in descending order of respect): the process dying at any
instruction; the player, who will try every UI the game offers, including a second player as a
mule; other mods, which insert, move, and rewrite items without asking; and time, which changes
item ids between snapshot and restore. Operators are *trusted but logged*: an op with `/give` and
NBT editors can defeat any isolation, so op actions are made loud rather than impossible.

**One rule the whole design obeys, learned from the review (F13): no guard may ever key on a
dimension id.** `ArenaLayout.FixedArenas` exists precisely so arenas can sit inside a shared world
(`multiworld` is in the modpack), and its `isArenaSpace` is deliberately a *box* test. A
dimension-keyed drops-cancel would have deleted the death drops of every bystander in that world.
Every guard below keys on one of exactly two things: `RunArenas.isInArena(player)` (which is
layout-aware), or the player's own swap tag (§4) — and where the two could disagree, the tag wins,
because the tag is what says "this player's live inventory is the run's, not their own".

## 1. What exists, and the holes this design must close

The party half is built (`run/RunPartySwap.kt`) and its four safety properties are the template:
stash into Cobblemon's own storage so our file is never the only copy; the record travels on the
Pokémon (`stashed_from_slot`); release only ever by run marker (`run_seed`); reconcile on every
login, deciding from the world. The inventory design matches those idioms where it can and states
each divergence.

The holes, from this design pass and from the review. H1–H3 and H7 are closed *by construction* in
§2's model, with sweeps kept as backstops; the rest are closed by named mechanisms; two are
partially closed and say so.

- **H1 — run Pokémon can be deposited into the PC.** `RunPartySwap.restore()` releases run
  Pokémon from the *party* and returns stashed ones from the *PC*; a run-marked Pokémon a player
  dragged into a PC box is touched by neither and survives the run — the legendary faucet §2.2
  exists to close.
- **H2 — real Pokémon can be withdrawn into the run party.** `reconcile()` installs only when the
  party holds *no* run Pokémon, so a party of five run Pokémon plus a real Garchomp dragged from
  the PC passes `partyHoldsRunPokemon` and stays mixed. The Garchomp fights waves; `RunState.kill`
  cannot delete it (not in the run's list) and `pokemonFainted` only removes run-marked Pokémon,
  so invariant 1 currently holds by two accidents. Worse (F5): `RewardGrant.grantHeldItem`
  *destroys* whatever item it displaces, so a reward aimed at a smuggled-in real Pokémon deletes a
  real held item — an invariant-1 loss with no crash involved.
- **H3 — link trades and pastures.** A run-marked Pokémon in the visible party outside the arena
  can be traded (real Pokémon on someone else's save) or pastured (a world entity outliving the
  party sweep).
- **H4 — in-run catches feed advancement criteria.** `RunCapture` reclaims the Pokémon and
  `RunDexGuard` vetoes the dex write, but Cobblemon's advancement criteria trigger off the same
  capture flow uncancelled — and §2.18's depth gate reads advancements.
- **H5 — Minecraft XP.** No arena source grants it today, but reward tables are data (§2.12) and
  one bottle-o'-enchanting entry away from a channel. Closed structurally (§5: restore *sets* XP).
- **H6 — cobblemon-unchained streaks (F7a).** Unchained tracks per-species catch/KO/hatch streaks
  granting real IV/shiny/HA bonuses (capture streak 5/10/20/30 → 1–4 perfect IVs) on *future
  overworld catches*. A 200-wave run is a streak farm, and `RunDexGuard` only vetoes
  `POKEDEX_DATA_CHANGED_PRE`. Structurally H4's twin and worth more. Partially closed — §7.6.
- **H7 — cobbreeding eggs (F7b).** A run legendary in a pasture with a real Pokémon produces an
  *egg* carrying no run marker; sweeping the parent later recovers nothing. The faucet, laundered
  through one generation. Closed by construction in §2 (run Pokémon can never reach a pasture),
  with the pasture veto demoted to defence in depth.
- **H8 — our own reward system mints unmarked items (F5).** `shop/RewardGrant.kt` `grantHeldItem`
  builds a bare `ItemStack(item, 1)` with no `custom_data`. Under §2.2-reversed the run Pokémon
  sit in the real party, the player can take the item off, and an exit rule that trusts markers
  hands it back as their property. Fixed at the mint: **every stack the run creates — rewards,
  shop purchases, run bag, held items — is marked at creation**, and §7.5's quarantine rule stops
  trusting "unmarked" as "theirs".
- **H9 — pause strands the player in the arena with their real inventory (F1, fatal in draft 1).**
  `RunController.pause` calls `RunPartySwap.restore` and teleports nobody. Draft 1 keyed its
  drops-cancel on the arena dimension, so a paused player who died would have had their *real*
  inventory voided by our own guard; and between pause and resume they hold their `dynamax_band`
  next to the power spot, reopening the exact hole invariant 3 names, because `RunBagGuard` gates
  only on `RunBattles.isFighting`. Closed by D1 (pause is a full exit) plus the §0 keying rule
  (the drops guard follows the tag, so it can never fire on someone holding their real items).

## 2. Design overview: two swaps, one key

**Both swaps are keyed on the arena session.** Entering the arena installs the run party and the
run inventory; leaving it — by pause (D1), run end, death, logout, or being found outside (§7.3) —
restores the player's own party and inventory and returns the run's property to `RunState`. While
outside the arena, a player mid-run holds *nothing* of the run's, sees their own party and their
own items, and is a completely ordinary citizen of the server.

**This revises draft 1, which keyed the party swap on the run and the inventory swap on the
arena**, and the first draft's own §2 misdescribed the code while doing it (F11: it claimed the
party "stays installed while paused"; `pause` in fact restores it, and login `reconcile` then
re-installs it, so a paused player's party oscillates between logins). One key removes the
oscillation and, more importantly, removes the entire class of outside-the-arena leak windows: H1,
H2, H3 and H7 all require a run Pokémon to exist in a real store while the player can reach a PC,
a trade partner, or a pasture — and the arena contains none of those and the player can carry none
in. What §2.2-reversed bought — Cobblemon's own party UI on the team you are actually playing —
is a property of *fighting and the between-wave gap*, both of which happen inside the arena. The
plan already agrees about the outside: §2.2's pause note calls a paused player holding run Pokémon
"a party screen that lies about what they own".

Concretely, this changes `RunPartySwap.reconcile`'s contract: install happens only on the way into
the arena (`RunController.resume` already calls it there); on login with a run and the player
*outside* the arena, reconcile must **sweep, not install** — any run-marked Pokémon in the party
or PC goes back to being `RunState`'s only (the rebind logic already tolerates this), and any
unmarked Pokémon found in the party mid-session is stashed rather than tolerated (closes H2's
"mixed party passes the test" branch; wave start additionally refuses a mixed party as the last
line). The sweeps stay even though the windows are closed by construction, because crashes strand
state: a process that dies mid-session leaves run Pokémon in the real party, and the next login's
sweep is what un-strands them.

The run's own items live in a new `RunState.runBag` (serialized stacks beside `RunState.credits`),
written at the same checkpoints, dying with the run (§2.35's rule, applied to items). Every stack
the run creates is marked in `minecraft:custom_data` with `cobblemon_roguelite:run_item = <run
seed>` — the item-side twin of `RunPartySwap.RUN_MARKER_KEY`, and like it **the only thing any
destructive path may match on**. One caveat the marker carries (F14): a `PokemonItem` stack
containing a run Pokémon is unmarked *at stack level* while its payload is marked one level down,
so every marker test on stacks must read through into a contained Pokémon's `persistentData`
before classifying the stack — §7.5.

## 3. Where the snapshot lives — the central decision

The party could be stashed in the PC because the PC is Cobblemon's storage: our file was never the
only copy. **An inventory snapshot has no such refuge — wherever it lives, it becomes the only
copy of the player's items the moment the live inventory is cleared.** Everything below follows
from taking that seriously.

**Chosen: one file per player, `<world>/data/cobblemon_roguelite/stash/<uuid>.dat`, written by us
with a temp-file + fsync + atomic-rename protocol, with a lifecycle completely independent of
`RunStore`.** New objects: `run/RunInventoryStash.kt` (the swap engine) over `run/StashFiles.kt`
(the file protocol). The review verified the reasoning and it stands unchanged from draft 1:

- **Not inside `RunStore`.** Run state is *deletable by design*: §2.23 expiry deletes it,
  `/roguelite abandon` deletes it, an operator can delete it, and `RunStore.load` deliberately
  discards entries that fail to parse while `RunStore.save` skips entries that fail to serialize
  ("it will be lost"). Every one of those must cost the player *a run*, never *their inventory*.
  The codebase's own precedent is §2.26: `PendingPayoutStore` is separate from runs because "an
  untouched run is owed nothing, whereas a held payout is already owed". A stashed inventory is
  the strongest possible form of "already owed".
- **Not a single shared `SavedData` file.** `DimensionDataStorage` writes are not atomic —
  `RunStore.flush` documents the truncated-file failure and accepts it because a lost run is
  survivable; a lost inventory is not. And a shared file couples players.
- **Not Cobblemon-style storage** (no item equivalent of the PC exists; building one is inventing
  a second inventory system to avoid trusting our own file).
- **Not an in-world container** (world-readable, finite, unindexed, and its durability is "the
  world save", which is harder to reason about than our own fsynced file).
- **Not the ender chest** (27 slots for 41+ stacks, destroys or merges existing contents, and the
  player can reach it — which converts the stash into the smuggling channel §7.4 has to fight).
- **Not "guards only, keep the inventory"** — today's state. `RunBagGuard` blocks bag-item use
  *in battle* and nothing else: not Mega Showdown's item checks, not between-wave use, not
  arbitrary modded items. Guarding use is an unwinnable allowlist; removing the inventory is not.

Per-player files buy: a write protocol exactly as paranoid as the content deserves (vanilla's own
playerdata write is temp+rename with a `.dat_old` generation — we match it and go one better with
fsync, see F9 in §5); corruption contained to one player; op inspection with `cp`. The directory
lives under `<world>/data/`, deliberately beside `<world>/playerdata/`, so any backup or rollback
captures both from the same instant.

### What the snapshot contains

- **Vanilla:** all 41 inventory slots (main + armor + offhand) as raw serialized stacks with slot
  indices (Quick Teams' "remember where it came from", applied to slots); XP level and progress;
  active mob effects; health, food, saturation; selected hotbar slot.
- **Modded worn slots (D3, F6):** the snapshot format has named per-provider sections. A provider
  interface (`integration/StashSlotProvider`: enumerate `(slotKey, stack)` pairs, clear, restore)
  is implemented by optional compat modules behind classloading guards — `compat/AccessoriesCompat`
  (covers everything registered through the Accessories API, which on this modpack includes the
  Sophisticated Backpacks back slot) and whatever else a host registers through `integration/`.
  A worn backpack's *contents* ride inside its ItemStack data, so snapshotting the stack snapshots
  the bag. **Fail closed at the seam:** if a provider is present but throws during enumeration or
  clearing, entry is refused — a run that starts with an unreadable worn slot is a run with a
  hidden bag. If the mod is absent, the section is simply empty. If a section's mod is absent at
  *restore* time, that section is residue (§5, X3) — kept, never dropped.
- **A header:** freshly minted `swapId` (UUID), run seed, timestamp, format version.
- **Not the ender chest.** It is blocked, not swapped (§7.4) — its contents stay untouched, which
  is the only handling of a container the player cannot legitimately reach mid-run that cannot
  itself lose anything.

## 4. The one flag this design permits, and why

`RunPartySwap.reconcile` decides from the world, never from a flag, and says why: a flag can be
wrong; a party holding a run-marked Pokémon cannot be. Items break that doctrine's precondition —
**an emptied inventory carries no evidence.** It is indistinguishable from a new player's, from a
death, from an op's `/clear`. So a record must exist, and the design's job is to make it
impossible for the record and the world to disagree *durably*.

The record is `cobblemon_roguelite:stash_id = <swapId>` in the player's persistent data **under
the `PlayerPersisted` subtag** — the one subtag `ServerPlayer.restoreFrom` copies across a death
clone (the hazard `RunStore`'s header documents), so death cannot shed it. The load-bearing
property: **the tag is written in the same tick as the inventory clear and reaches disk in the
same vanilla playerdata write** (temp + rename via `Util.safeReplaceFile`), so "inventory cleared"
and "tag set" are one indivisible disk event. There is no reachable disk state where the inventory
is empty and the tag absent, or full and the tag present. The tag names its file (`swapId` matches
the stash header), so a tag pointing at a missing or mismatched file is *detectable* and treated
as the loud failure it is (§6 row 3), never as permission to guess.

The tag has a second job the first draft missed: **it is the universal guard key** (§0's rule).
Drops-cancel, XP-drop-cancel, ender-chest refusal, command refusal, bag-item refusal, and the
displacement check all test the tag — meaning they follow the player wherever a teleport drops
them, and they can never touch a player whose live inventory is their own. And because it is plain
NBT in a vanilla location, **any other mod on the host can read it without a compile dependency on
this one** — which is what makes the host-side closure of H6 possible (§7.6) without violating
§2.9's standalone rule in either direction.

## 5. Entry and exit protocols, with the durability guarantee at each step

All steps on the server thread — and per the review, that is a constraint the implementation must
**enforce**, not inherit: `RunBattles` hops through `server.execute`, but `RunCapture`
deliberately does not, so "we are always on the server thread" is an assumption with a live
counterexample in this very module. `RunInventoryStash` asserts the thread at its entry points.

A "durable" step means: if the process dies at any later instruction, the step's effect survives;
if it dies earlier, the step never happened. The invariant that survived review has a name worth
keeping: **durable-write-before-clear** — no ordering exists in which the only copy of the
player's items is undurable.

### Entry (inside `RunController.resume`, before `RunArenas.enter`'s teleport)

- **E0. Refuse before touching anything** (refusing is free; a half-done swap is not): gamemode
  not survival/adventure; a `stash_id` tag already present (run the §6 reconcile instead; entry
  may proceed only once it resolves clean); a stash file present without a tag (stale — archive
  first, §6 row 2); any live stack or worn slot that fails to serialize; any present
  `StashSlotProvider` that throws.
- **E1. Void run-marked orphans** (read-through per F14) from the live inventory and worn slots —
  leftovers of a dead run are the run's property and the snapshot must not adopt them. **Before
  the snapshot is taken**, which reverses draft 1's order (F8): draft 1 snapshotted first, so the
  orphans were already in the file and the later void was dead code — X3 would have restored them
  into the real inventory permanently. Voiding pre-snapshot is safe because it is marker-keyed,
  and marker-keyed deletion of run property is the one destructive act this design permits
  anywhere.
- **E2. Build the snapshot in memory.** Mint `swapId`. Nothing observable changes.
- **E3. Write `stash/<uuid>.dat`: temp file, fsync stream and directory, atomic rename.**
  *Durability:* POSIX rename atomicity — the snapshot exists whole or not at all. On any failure
  (disk full included): delete the temp, refuse entry, tell the player, log at ERROR. Nothing
  changed; nothing lost.
- **E4. In one block: clear inventory and worn slots, zero XP, clear mob effects, write the tag,
  install the run bag** (marked stacks from `RunState.runBag`). In-memory only.
- **E5. Force the vanilla playerdata save for this player, then fsync the written
  `playerdata/<uuid>.dat` and its directory ourselves.** Vanilla's `safeReplaceFile` is
  temp+rename with **no fsync** (F9), so the rename alone orders the write against other writes
  but not against power loss; the explicit fsync is what upgrades E5 to durable. If the save
  fails: restore the live state from the in-memory snapshot, remove the tag, refuse entry. If the
  process dies before E5 lands, disk shows the old inventory and no tag; E3's file is unreferenced
  → stale (§6 row 2). Durable-write-before-clear holds.
- **E6. Teleport in. Tell the player** (§8): a swap nobody is told about is indistinguishable
  from a swap that failed — prior art's clearest lesson, proven again by our own first playtest.

### Exit — one function, five doors

The doors: **run end** (inside `RunController.endRun`, which is the single funnel — but note it
has *two effective invocation contexts*, the ordinary server-thread paths and the login path via
`penalise`, and the ordering fix below covers both); **pause** (D1 — now: exit swap, then the
same eject/teleport `RunArenas.exit` does, then party restore; `PauseAdvice.BetweenWaves` only,
mid-battle pause remains disclosure-only per §2.22); **death** (respawn hook — the clone carries
the tag, the respawn reconcile finishes the exit); **logout** (opportunistic, from
`RunLoginHooks.onLogout`, with the login reconcile as the guarantee); **displacement** (§7.3 — a
tagged player found outside the arena is exited in place).

- **X1. Capture the run bag:** serialize live marked stacks (read-through, F14) into
  `RunState.runBag`, checkpoint `RunStore`. Skipped when the run no longer exists. *Idempotent:*
  overwrite, never append.
- **X2. Partition the live inventory and worn slots.** Marked stacks (read-through): **voided** —
  run property, the marker-keyed deletion again. Unmarked stacks: **quarantined, not returned and
  not voided.** This reverses draft 1 (F5 made it necessary): draft 1 returned unmarked extras as
  "the player's property that arrived at a bad time", but our own reward path was minting
  unmarked stacks, and *any* unmarked-minting bug — ours or another mod's, including the
  place-a-marked-block-and-break-it laundering trick, since block drops are fresh stacks — turns
  "return" into the smuggling channel. Voiding instead would destroy genuinely-owed property (a
  vote-reward mod delivering mid-run). Quarantine takes neither risk: the stacks are written to
  `stash/quarantine/<uuid>-<timestamp>.dat` with the same durable protocol **before** being
  removed from the live inventory (durable-write-before-clear applies to quarantine too), the
  player is told, and an op releases or voids them after a look (§9). If the quarantine write
  fails, the stacks are returned to the player — when forced to choose, invariant 1 outranks
  invariant 2, and the WARN log carries the item list either way.
- **X3. Restore:** place each snapshot stack into its recorded slot (occupied → first free slot →
  drop at the player's feet, `RunPayoutDelivery`'s rule: recoverable off the floor beats silently
  discarded); restore worn-slot sections through their providers; **set** — never add — XP to the
  snapshot value (closes H5 structurally); restore effects, health, hunger; remove the tag. Stacks
  or sections that fail to decode (mod removed, id changed) are **residue**: restore the rest,
  keep the file, tell the player, op re-restores later — the same deferred-resolution argument
  `RunPayoutDelivery` makes for held payouts.
- **X4. Force the playerdata save, then fsync it** (same as E5). *Durability:* items-restored and
  tag-removed land as one durable disk event. **If the save fails: roll the in-memory restore
  back** — re-clear to the run-inventory state, keep the tag, keep the file, message the player,
  and schedule a retry next tick and at next login. This rollback is new (F2) and it is what makes
  the atomicity argument true: draft 1 kept the tag but *left the restored items in memory*, so
  any later autosave persisted restored-inventory-plus-tag, and the next login restored again — a
  full, repeatable inventory duplication. The rule is: **memory must always match one of the two
  legal disk states** (swapped-and-tagged, or restored-and-untagged), including on failure paths.
- **X5. Archive the stash file** (rename into `stash/stale/<uuid>-<timestamp>.dat`) — only after
  X4's fsync has returned. Draft 1 had this ordering inverted against durability (F9): an
  un-fsynced X4 followed by a durable archive meant power loss could yield cleared-and-tagged
  playerdata with the file already archived — the §6 row-3 alarm, reached from a clean exit. With
  X4 fsynced, a crash between X4 and X5 leaves file-without-tag → row 2, archived then. Archive,
  not delete: it is the last generation of a player's inventory; ops prune on their own schedule.
- **X6. Run end only, and strictly after X3:** the §2.20 payout is delivered
  (`RunPayoutDelivery`), into the *restored* real inventory, minted fresh from the table — never
  from the run bag (§2.35's argument). Draft 1 stated "payout after restore, necessarily" and
  then didn't check the login path (F10): `RunLoginHooks.onLogin` calls `reconcileOnLogin`, whose
  `penalise` → `endRun` → `deliver` ran `target.addItem` into the still-swapped live inventory
  *before* the swap reconcile — the payout landed unmarked in a tagged inventory, X2 would have
  quarantined the mode's own payout, and the smuggling WARN would have cried wolf on a designed
  path. The fix is structural, not an ordering note: **the inventory exit swap runs inside
  `endRun`, before the arena exit and before `deliver`**, exactly where the party restore already
  runs and for the same reason ("every end path funnels through here"). The login path then works
  unmodified, because by the time `deliver` runs the inventory is the real one.

## 6. Reconcile: the decision table, which is the normal path

Runs on every login (beside the party reconcile in `RunLoginHooks.onLogin`), on every respawn, on
displacement (§7.3), and as E0's repair step. Every crash, kill -9, operator intervention and
rollback presents as a row; none is an error branch, and row 1 runs a thousand times a day, which
is what keeps the rest honest.

| # | Tag | File | Run | Where | Verdict and action |
|---|-----|------|-----|-------|-------------------|
| 1 | – | – | any | outside | Nothing to do. |
| 2 | – | yes | any | any | **Stale file.** Either E3 landed but E5 never did (live inventory intact and authoritative — restoring would duplicate), or X4 landed but X5's archive didn't (already restored — restoring would duplicate). Both: archive the file, log WARN with a stack-count summary, restore nothing. Without the tag, no destructive or additive act is justified. |
| 3 | yes | – | any | any | **The alarm row.** The disk promises a stash that is not there — op deletion, or a partial rollback that restored playerdata without world data. **Refuse everything destructive:** no clear, no install, no tag removal (a restored backup must stay matchable by `swapId`). The run may not enter the arena while this holds. Tell the player plainly; ERROR names the missing `swapId`. Exits: op restores a file from backup, or `stash forfeit` acknowledges the loss (§9). Guessing here is how items die. |
| 4 | yes | yes | – | outside | Run ended/expired/deleted while swapped. Finish the exit: X2, X3, X4, X5. This row is why **operator-deletes-run-state** and **run-expiry-while-offline** cost a run, never an inventory — and it works *because* the stash store is not `RunStore`. |
| 5 | yes | yes | yes | outside | Crashed, died, or was displaced mid-session. Full exit swap (X1 first — the run continues and its bag must not die with the session). `resume` re-enters later. |
| 6 | yes | yes | any | inside arena | Login inside arena space: `reconcileOnLogin` already ejects (§2.23 lease ended at logout); after ejection this is row 4 or 5. Eject first, then swap — the swap's "outside" postcondition should be true when it finishes. |
| 7 | – | – | yes | outside | Mid-run, paused or freshly logged in: **sweep, don't install** (§2's revised `reconcile` contract) — run-marked Pokémon out of party and PC, unmarked party members stay put because the party *is* theirs right now. Install happens only through `resume`. |
| 8 | – | any | any | inside arena | **A tagless player in arena space** — a mule pulled in by `/tpahere`, or someone an op teleported. Eject immediately (they are not the leaseholder; `RunArenas` can check slot ownership), and void-on-sight any run-marked stacks they carry (read-through). Their own property is untouched — the tag rule guarantees the guards cannot mistake their inventory for a run's. |

`swapId` mismatch between tag and file (interleaved rollback generations) collapses to row 3 for
the promised file; the mismatched file itself is row 2. Neither is guessed at.

## 7. Guards — everything keyed on the tag or on `isInArena`, never on a dimension

1. **Death drops and XP orbs are cancelled for tagged players** (`LivingDropsEvent` +
   XP-drop event, victim carries `stash_id`). Safe *because* the tag proves everything held is
   run property — the real items are on disk. Corollary worth advertising: **death inside a run
   risks nothing the player owns.** With keepInventory on, the run items instead survive to the
   respawn, where the respawn reconcile (row 5) voids and restores. A tagless bystander dying
   inside a `FixedArenas` world keeps vanilla behavior — the F13 fix.
2. **Bag-item refusal widens from `RunBattles.isFighting` to "tagged":** unmarked bag items are
   refused anywhere while the swap is in place (they should not exist; this is the backstop for
   worn-slot gaps and slipped insertions), marked ones follow §2.36's rule (allowed between
   waves, `Bag Clause` + `RunBagGuard` in battle). `RunBagGuard`'s three-event interaction-layer
   mechanism is unchanged; only its gate widens.
3. **Displacement check — defence in depth behind D2.** D2 refuses the *commands* (a cancellable
   command-execution event matched against a configurable root list — configurable because host
   command sets vary, §1.2 — tested against the tag, so paused players teleport freely). For
   whatever slips past commands — op `/tp`, another mod's mechanics, a portal someone conjures —
   a cheap poll (tagged players only, every few seconds) plus the dimension-change event run the
   row-5 exit swap on any tagged player found outside `isInArena`. The swap happens *wherever
   they are standing*: they leave with their own inventory and the run's property back in
   `RunState`, which converts every escape route into a slightly odd pause. The same poll ejects
   tagless players found inside arena space (row 8).
4. **Ender chest:** refused while tagged, by testing the opened menu's backing `Container` for
   the player's `PlayerEnderChestContainer` identity — **not** the menu type (F12: NeoEssentials'
   `/enderchest` opens a `GENERIC_9x3` `ChestMenu` indistinguishable by type from every chest and
   barrel; a type-keyed guard blocks everything or nothing). D2 already refuses the command;
   this catches mods that open the container directly. Untagged players (paused included) use
   their ender chest freely — combined with the displacement check, the F4 laundering loop
   ("leave mid-run, deposit, return") dies at its first step, because leaving mid-run *is* the
   exit swap.
5. **Marker classification always reads through (F14):** a stack is run property if its
   `custom_data` carries `run_item`, **or** it is a Cobblemon `PokemonItem` whose contained
   Pokémon's `persistentData` carries `RUN_MARKER_KEY`. Every void, sweep, capture and partition
   uses the same single classifier, so the rule cannot be right in one place and wrong in
   another.
6. **H6 (unchained streaks) — partially closed, mechanism named.** A standalone mod (§2.9)
   cannot reach into cobblemon-unchained, and Cobblemon's capture/KO events are not cancellable
   at the right layer. The closure is host-side, and the tag is what makes it cheap: any host
   mixin or companion (cobblemon-bridge territory) can read `stash_id` from vanilla NBT — no
   compile dependency in either direction — and no-op unchained's streak accrual for tagged
   players. The cross-mod-mixin rules apply (`@Coerce`, `required: false`, fail-open — a failed
   apply must not crash-loop boot). For published builds this is a documented caveat: "third-party
   progression mods that count catches/KOs will count run battles unless the host intervenes; the
   tag is the hook." Same posture for H4's general advancement criteria, with one hard exception:
   **if the §2.18 depth-gate advancements can be advanced from inside a run, that specific path
   must close before ship** — a run must not farm its own gate. Open question 1.
7. **Pokémon-side guards (backstops behind §2's by-construction closure):** `restore()` sweeps
   run-marked Pokémon out of the **PC** as well as the party (H1); reconcile row 7 sweeps
   mid-run; wave start refuses a mixed party (H2); trade and pasture participation of run-marked
   Pokémon is vetoed if Cobblemon 1.7.3 exposes cancellable hooks, swept-and-logged if not (open
   question 2); `RewardGrant.grantHeldItem` refuses to target an unmarked Pokémon and marks the
   stack it grants (H8, both halves), and its displaced-item destruction becomes safe because it
   can now only ever displace run property.
8. **Pokédex:** already closed (`RunDexGuard` + `suppressingDex`). **Statistics:** accepted leak,
   recorded — they gate nothing here; published hosts inherit the caveat. **XP:** X3's set.
   **Credits:** never material (§2.35). **Payout:** the one metered channel, X6.

## 8. What the player is told, and when

Silence is indistinguishable from failure. Every message names numbers, because numbers are what
a player checks.

| Moment | Message |
|---|---|
| Entry (E6) | "Stored your N item stacks, your gear and your XP — they return when you leave the run." |
| Pause (D1) | "Run paused. Your items and party are back; your run is saved at wave W." |
| Exit, clean (X3) | "Returned your N stacks." |
| Exit, residue | "M of your items could not be restored (a mod may have been removed). They are kept safe — an operator can recover them." |
| Quarantine (X2) | "K items acquired during the run were set aside for review — an operator will return anything that is yours." |
| Row 3 | "Your stored items cannot be found. Nothing has been touched. Contact an operator — reference `<swapId>`." |
| Row 4 on login | After the expiry/interruption lines (ordering per `RunLoginHooks.onLogin` — this line is about *now*): "Your stored items have been returned." |
| X4 rollback | "Your items could not be returned just now — nothing is lost; it will retry, or relog." |
| Displacement (§7.3) | "You left the arena — your run is paused and your items are back." |
| Refusals | Always the reason: creative mode, unreconciled stash, unserializable slot, teleport-during-run. A refusal without a reason reads as the mode being broken. |

## 9. Operator surface

- `/roguelite stash inspect <player>` — header + stack list of the live stash, tag match state,
  quarantine and stale listings. The row-3 and quarantine diagnosis tool.
- `/roguelite stash restore <player> <file> confirm` — re-attempt restore from a named archive
  (residue and backup path). **Dupe-guarded (F3):** draft 1's version was an unbounded duplication
  button, because an archived file by construction has no tag pointing at it — X5 archives only
  after the tag is removed — so the command has no idempotence to inherit. Now: refuses if the
  player currently carries a tag (a live swap would collide); marks the archive **consumed**
  (rename) *durably before* delivering, so a crash between the two loses rather than duplicates —
  the §2.26 direction, "err toward losing a payout rather than duplicating one", and the loss is
  recoverable from the consumed file by an op; refuses consumed archives without an explicit
  `force` that logs at WARN naming the op.
- `/roguelite stash release <player> <quarantine-file> confirm` — hand quarantined stacks back
  (same consumed-marking protocol). Its counterpart `void` deletes them, logged.
- `/roguelite stash forfeit <player> confirm` — remove a tag whose file is acknowledged lost
  (row 3's only other exit). WARN, names the swapId, deliberately ugly to type.

## 10. Edge-case catalogue

| Case | What happens |
|---|---|
| Crash during E1–E3 | Only marker-keyed orphan voiding observable; orphan `.tmp` swept at boot. |
| Crash between E3 and E5 | Disk: old inventory, no tag; unreferenced file → row 2, archived. No loss, no dup. |
| Crash any time mid-session | Cleared+tagged+file → rows 4/5/6 restore at next login. |
| Crash between X4 and X5 | File without tag → row 2. No dup (restore requires the tag). |
| Power loss at any point | Same rows as crash: E3/E5/X4 are fsynced, so "durable" means durable against power, not just process death. The un-fsynced-vanilla-save gap (F9) is closed by our explicit fsync. |
| Disk full at E3 | Refuse entry. Nothing lost. |
| Disk full at E5 | In-memory rollback, refuse entry; E3's file becomes row-2 stale. |
| Disk full at X4 | **Roll back the in-memory restore** (F2), keep tag+file coherent, retry next tick/login. |
| Disk full at X2's quarantine write | Stacks returned to the player instead (invariant 1 outranks 2); WARN with item list. |
| Logout mid-swap | Cannot interleave: each swap runs whole on the server thread in one tick (asserted, not assumed — see §5's `RunCapture` note). |
| Disconnect during battle | §2.10 attributes the run side; inventory: tagged inside arena → row 6. |
| Death in arena, keepInventory off | Drops+XP cancelled (tag-keyed). Run bag loses deltas since X1/checkpoint — party-equivalent granularity. Respawn reconcile restores. |
| Death in arena, keepInventory on | Run items survive to respawn → respawn reconcile (row 5). |
| `/kill`, void falls | Identical to death. |
| Pause (D1) | Full exit: X1–X5, teleport out via the `RunArenas.exit` path, party restored. Mid-battle pause stays §2.22 disclosure-only. |
| `/home` etc. mid-session | Refused (D2, tag-keyed, configurable list). Paused players teleport freely — the tag is gone. |
| Op `/tp`, portal, mod teleport out of arena | Displacement check (§7.3): exit swap in place; reads as a pause. |
| `/tpahere` mule into arena | Row 8: ejected, run-marked stacks voided on sight, own property untouched. |
| Bystander in a `FixedArenas` shared world | Untouched by every guard — nothing keys on dimension (F13). |
| Op `/clear` on a mid-run player | Clears run items only; real inventory is on disk. Run bag restored from last checkpoint. |
| Op deletes run state | Row 4. Party side: sweep on login. |
| Op deletes stash file | Row 3: refuse, preserve, escalate. Never guess. |
| Run expires offline (§2.23) | Row 4. Expiry deletes runs, never stashes — different stores, on purpose. |
| World rollback (both halves) | Same-instant pair (co-located under `<world>/`) → a legal row. |
| Partial rollback | Rows 2/3; nothing destroyed. |
| Two runs / two stashes for one player | Unrepresentable (map key; one file per uuid; E0 refuses while one exists). |
| PC full / party over 6 at party stash | Existing `install()` refusals, unchanged. |
| Inventory full at restore | Cannot arise for the snapshot (restore follows the wipe into recorded slots); conflicts fall back first-free → drop at feet, never voided. |
| Accessories / Sophisticated Backpacks | Snapshotted and cleared via provider sections (D3). Provider throws → entry refused. Mod removed before restore → residue, kept. Marker survival under their stack handling: must-verify-live (§12). |
| Shulkers / bundles as vectors | Outbound: X2 voids marked (read-through) and quarantines unmarked — nesting and block-laundering both die in quarantine. Inbound: whole-inventory + worn-slot stash leaves nothing to hide in. |
| Heavy-NBT items | Raw round-trip; E0 refuses on encode failure; X3 residue on decode failure. |
| Mod update removes an item id | Residue rule; archive never deleted; op re-restores after the mod returns. |
| `PokemonItem` stacks | Classified by read-through (F14): marked payload ⇒ run property, regardless of stack-level data. |
| The run's own reward/shop/held items | Marked at mint (H8), live in `runBag`, die with the run. Payout minted separately, X6. |
| Another mod inserts items mid-run | Unmarked → unusable as bag items (tag-keyed guard), quarantined at exit, WARN. |
| Creative / `/gamemode` mid-run | Entry refused in creative; mid-run switch logged WARN, contained: spawned stacks are unmarked (quarantined), marked property cannot leave. Ops are the trust boundary. |
| Run Pokémon → PC / trade / pasture / breeding egg (H1, H3, H7) | Closed by construction: run Pokémon exist in real stores only inside the arena, which contains no PC, no trade partner, no pasture, and the player can carry none in. Sweeps + vetoes as backstops for crash-stranded state. |
| Real Pokémon into run party (H2) | Row-7 sweep + mixed-party refusal at wave start; `grantHeldItem` refuses unmarked targets. |
| Unchained streaks (H6) / advancements (H4) | Host-side closure via the tag as a cross-mod signal (§7.6); §2.18-gate exception must close before ship. Statistics: accepted. |
| XP (H5) | Set-not-add at X3; XP orbs cancelled for tagged deaths. |
| Proxy / multi-JVM | Out of scope (one JVM), recorded: stash writes are synchronous+fsynced, immune to the Quick Teams 60–90s hazard; the *party* half still rides Cobblemon's async `FileBackedPokemonStoreFactory` save. Behind Velocity, force a Cobblemon `saveAll` after every swap or the party stash inherits that loss window. |

## 11. What is refused rather than attempted

Entry in creative; entry with an unreconciled tag; entry when any slot or provider cannot be
serialized; entry when the stash write fails; wave start with a mixed party; arena entry under
row 3; teleport commands while tagged (D2); ender-chest access while tagged; reward grants aimed
at unmarked Pokémon; any restore without a matching tag-and-file pair; any op re-restore of a
consumed archive without `force`; any deletion of player property not keyed on a run marker
(quarantine exists so that "unknown" never has to mean "deleted"); any automatic read of an
archived or quarantined file; expiry or run deletion ever touching a stash.

## 12. Platform assumptions — must verify live, not settled

The review's correct complaint about draft 1's §14: these were phrased as worries when they are
preconditions. Each gets verified on the dev VM before the first line of the swap engine is
trusted:

1. `PlayerList`/`PlayerDataStorage.save` called mid-tick for one player, on this 69-mod server —
   no mixin on the save path assumes autosave/logout-only.
2. `ServerPlayer.restoreFrom` copying `PlayerPersisted` across the death clone on NeoForge
   1.21.1 specifically (three modules already rely on the claim; none has a live test).
3. Whether Mega Showdown's `dynamax_band` check reads Accessories slots (decides how load-bearing
   the worn-slot clearing is for invariant 3's headline case).
4. Whether `custom_data` markers survive Accessories' and Sophisticated Backpacks' stack handling
   (insertion, sorting, upgrades). If any path strips components, marked items launder to
   unmarked — quarantine catches the theft but the WARN volume would drown the tripwire.
5. Whether Cobblemon 1.7.3 exposes cancellable PC-move / trade / pasture hooks (decides veto vs
   sweep in §7.7).
6. fsync behavior of the deployment filesystem (the ext4 default honors fsync; verify the VM's
   mount options don't lie).

## 13. Testability

**Headless:** the file protocol against a temp dir with injected fsync/rename failures; snapshot
codec round-trips including provider sections and pathological components; the §6 table as a pure
function `(tag?, fileState, run?, inArena, liveClassification) → Action` — every crash scenario
is one input tuple, and rows 2/3/8 and the X4 rollback are the high-value cases; the X2 partition
including read-through and nested containers; residue and quarantine partitioning with a registry
stub; the consumed-archive protocol.

**Live only:** E5/X4 atomic pairing with real playerdata saves under load; death-clone tag
survival; ejection-then-swap ordering; Mega Showdown refusing Dynamax to a stripped player,
*including a band in an Accessories slot*; the ender-chest container-identity guard against
NeoEssentials `/enderchest`; D2 command refusal; the displacement poll; drops/XP cancellation.

**Smoke test** (extend `ops/gen_roguelite_smoketest.py`'s datapack; per the standing rule, never
name it `server-*`): enter wearing a Sophisticated backpack containing a `dynamax_band` → assert
stored, worn slot empty, Dynamax refused; pause mid-run → assert teleported out, inventory and
party real, `/home` works; `kill -9` between waves → relog → assert full restore and messages;
op-`/tp` a tagged player to spawn → assert displacement exit; `/tpahere` a second player into the
arena → assert row-8 ejection; hand-delete the run file → row 4; hand-delete the stash file →
row 3 and `stash forfeit`; finish a run → assert payout arrives after restore, unquarantined, and
no marked item or Pokémon survives anywhere (inventory, worn slots, PC, party); die with
keepInventory off → assert nothing real dropped and no bystander drops were eaten in a
`FixedArenas` test world.

## 14. Open questions for the human

1. **H4/H6 severity check:** can the §2.18 depth-gate advancements, or an unchained streak worth
   caring about, actually be advanced from inside a run? (What do the badge advancements trigger
   on; does unchained count KOs from trainer battles or only wild?) Decides whether the host-side
   mixin is a ship-blocker or a fast-follow.
2. **Cobblemon hooks:** cancellable PC-move / trade / pasture events in 1.7.3 — veto vs sweep for
   §7.7. (Same question survives from draft 1; nothing above depends on the answer, only the
   cleanliness of the backstops.)
3. **Quarantine review burden:** are you happy owning the op-review loop for quarantined stacks,
   or should stacks from an allowlisted source (e.g., a known vote-reward mod's items) bypass to
   auto-return? Allowlisting reopens a sliver of the smuggling surface; refusing keeps ops in the
   loop. Design defaults to no allowlist.
4. **Stale/quarantine retention:** both directories grow forever by default (deliberately — court
   of last resort). Acceptable, or set a documented op prune guideline like the VM snapshot
   archive?
5. **D2 default command list:** proposed `home, warp, spawn, tpa, tpahere, tpaccept, back, rtp,
   enderchest`. Confirm against the NeoEssentials set actually enabled on prod.

(Draft 1's question about the arena entity sweep is deleted: `arena/ArenaStamper.kt:197–204`
already discards all non-player entities including `ItemEntity`, and `RunArenas.assign` nulls
`stampedBuild` on every fresh assignment, so the first entry of each session always stamps and
sweeps. F15.)

## 15. Adversarial self-review — what still worries me

- **The tag is still a flag.** §4's argument welds it to the fact it describes through write
  atomicity, but that now rests on *our own* fsync discipline on two different files plus §12's
  assumptions 1 and 6. The kill-9-and-power-loss smoke matrix is the detector, and it must
  actually be run, not admired.
- **The quarantine changes the failure smell.** Draft 1's "return unmarked" failed open (smuggle
  risk); quarantine fails closed but converts every unmarked-minting bug — ours or a mod's — into
  player-facing friction and op workload instead of silent loss. That is the right trade, but if
  the quarantine WARN fires weekly, something is minting unmarked stacks in the arena and the
  design wants it found, not triaged forever.
- **Marker survival (§12.4) is the design's soft underbelly.** Everything destructive keys on the
  marker; a component-stripping mod turns run property into quarantined "unknowns" at best. If
  verification fails for a mod we care about, the fallback is ugly: per-stack shadow accounting
  in `runBag` (match by item+count heuristics), which is exactly the second-list-out-of-step
  design `RunPartySwap` rule 2 exists to forbid. Better to find out early.
- **The displacement poll is a loop that must never be wrong.** It runs an exit swap on a
  predicate (`tagged && !isInArena`); a bug in `isInArena` under `FixedArenas` (box math, world
  reuse) would repeatedly exit-swap a player mid-wave. Mitigation: the poll acts only when the
  predicate holds on two consecutive samples, and logs the position it decided on.
- **Two swaps, one key is simpler than draft 1 — but pause now does more** (full exit + teleport
  + party restore), and pause is the operation players will hammer. The X-protocol's idempotence
  and the row-5/7 reconciles are what make a half-crashed pause boring; the smoke test's kill-9
  during pause is the proof obligation.
