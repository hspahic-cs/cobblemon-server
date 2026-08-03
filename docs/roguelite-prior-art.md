# How the Cobblemon ecosystem handles run-scoped teams and items

Research done 2026-07-31, for the question "other servers run minigames where you put your team aside
and get it back — how do they do it?" The short answer is that **almost nobody does the thing we are
doing**, and the closest matches solve a narrower problem. That is worth knowing before copying any
of it.

## The two models, and why ours is the harder one

**Model A — the whole world is the run.** [CobbleRogue](https://modrinth.com/mod/cobblerogue) is the
closest thing to what we are building: a Cobblemon roguelike explicitly inspired by PokéRogue, with
permadeath ("if one of your Pokémon faint in battle, they're permadead"), starter selection, per-
encounter roguelike drops, and a Final Stand mechanic where a Revive buys one more turn and the
Pokémon is erased if it loses anyway.

But look at how it starts a run: `/cobblerogue restartrun confirm` **resets your party and your PC**.
There is no isolation because there is nothing to isolate — the save *is* the run. That works for a
dedicated roguelike world and is useless as a minigame on a server where people have teams they care
about.

**Model B — the minigame borrows you for a while**, which is ours, and is where the ecosystem thins
out. Nothing found does a full party-and-inventory swap with a restore.

The nearest neighbour is [Cobblemon Battle
Tower](https://modrinth.com/mod/cobblemon-battle-tower), which is the obvious comparison and turns out
not to be one: players fight through floors **with their own locked-in team** and earn Battle Points.
It sidesteps the problem entirely by never taking anything away.

## The one piece of real convergent evidence

[Cobblemon Quick Teams](https://modrinth.com/mod/cobblemon-quick-teams) moves Pokémon between the
party and the PC on demand, and independently arrived at the same design as our
`RunPartySwap`:

> By default, the mod will keep track of what box/slot a Pokémon was in before it was saved into a
> team, then when loading another team, it will attempt to put it back in that same spot. If for any
> reason it cannot, it will move it to the first available box/slot **and notify you on screen**.

Remember where it came from, put it back there, fall back to the first free slot. That is our design,
arrived at separately, which is about as good a signal as this kind of thing gets.

**The part we do not do is the last clause.** Quick Teams tells the player when a Pokémon could not go
home. We log it and say nothing — and the first playtest produced exactly the confusion that predicts:
"I still have all my items", "my team is still in my party". A swap that happens silently is
indistinguishable from a swap that failed.

## The data-loss hazard they document, and why it is narrower for us

Quick Teams carries a prominent warning:

> Switching servers immediately after moving Pokémon in the Cobblemon PC or loading a team in the
> Quick Teams UI can cause players to **lose their Pokémon**. Wait at least 60–90 seconds before
> switching servers.

The mechanism is real: `FileBackedPokemonStoreFactory` saves on a tick subscription through a
background executor, so a PC write is not on disk the instant it returns. Our swap moves six Pokémon
into the PC and could be followed immediately by a disconnect.

**It is a proxy-network hazard specifically** — the loss happens when a player is handed to a
different backend server before the save flushes. We are one JVM on one VM, where a logout saves
normally, so this is not currently a live risk. It becomes one the day this server sits behind a
Velocity/Bungee proxy, and at that point the mitigation is a forced flush after the swap
(`FileBackedPokemonStoreFactory.saveAll` exists, though reaching the factory from outside is not
obviously supported).

Recorded because it is exactly the kind of constraint that is invisible until it costs somebody a
team, and because "we moved your Pokémon and then you changed servers" is unattributable after the
fact.

## The item side: nobody strips the bag

Searching for how Cobblemon servers restrict items in competitive or minigame contexts turns up
solutions one layer down from where we are looking — all of them operate on **held items**, not on the
player's Minecraft inventory:

- [Held Item Saver](https://modrinth.com/mod/held-item-saver-cobblemon) snapshots every Pokémon's held
  item at battle start and restores anything missing afterwards, so consumables are not really
  consumed.
- [Cobblemon Challenge](https://www.curseforge.com/minecraft/mc-mods/cobblemon-challenge) normalises
  levels for a challenge match and makes held items consumed during it have no lasting effect.

Both are the *opposite* of what a roguelite wants: they exist to undo attrition, and §2.11 removed the
bag precisely so attrition could not be undone.

So there is no Cobblemon-side precedent for taking the player's inventory away for the duration of a
mode. The precedent is in the wider Minecraft server world, where it is a solved and boring problem:
**per-world inventory separation**. [Inventory
Rollback](https://github.com/fs-vault/Inventory-Rollback) snapshots inventory, health, hunger, XP and
ender chest on defined events *including changing worlds*; the PerWorldInventory family of plugins
keys a whole inventory profile on which world you are in.

That maps onto us cleanly, because **the arena is already its own dimension**. "Your run inventory is
the arena dimension's inventory" is the same idea those plugins ship, and it is dimension-keyed
exactly like our arena entry and exit already are.

## What this changes about our plan

1. **Keep the party swap as built.** Quick Teams converged on the same design independently.
2. **Say something.** The single clearest gap against prior art is that our swap is silent. Both the
   install and the restore should tell the player what just moved and where, and a Pokémon that could
   not go back to its slot should say so on screen rather than only in the log.
3. **Inventory should be dimension-keyed**, following the per-world-inventory pattern rather than
   inventing a run-keyed one — the arena dimension is the natural key and it already brackets exactly
   the right window.
4. **Inventory has no PC.** This is the one place our party design does not transfer: a party could be
   stashed in Cobblemon's own storage, so our save file was never the only copy. An inventory snapshot
   has nowhere like that to live, so it lands in our save data and the write has to be durable *before*
   anything is cleared — a failed write must mean the run does not start.
5. **Note the proxy hazard** in whatever ships, so it is not rediscovered by losing a team.

## Sources

- [CobbleRogue](https://modrinth.com/mod/cobblerogue)
- [Cobblemon Battle Tower](https://modrinth.com/mod/cobblemon-battle-tower)
- [Cobblemon Quick Teams](https://modrinth.com/mod/cobblemon-quick-teams)
- [Cobblemon Home](https://modrinth.com/mod/cobblemon-home)
- [Held Item Saver](https://modrinth.com/mod/held-item-saver-cobblemon)
- [Cobblemon Challenge](https://www.curseforge.com/minecraft/mc-mods/cobblemon-challenge)
- [Inventory Rollback](https://github.com/fs-vault/Inventory-Rollback)
- [Per-world inventories discussion](https://www.minecraftforum.net/forums/support/server-support-and/2801643-how-to-make-seperate-world-inventories)
