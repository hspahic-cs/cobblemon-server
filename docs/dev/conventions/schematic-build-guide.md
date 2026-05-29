# Building a Schematic for Me — Beginner Guide

Hey! Thanks for helping out. This walks you through everything from zero. If you've only played vanilla Minecraft, that's fine — I'll explain each step.

**What you're doing:** installing a modded Minecraft launcher, joining my server, building a structure I've described, then saving it as a file I can use. The file ends up on my server automatically, so you don't have to email it or anything.

---

## Step 1: Install Prism Launcher

You can't use the normal Minecraft launcher for this — my server runs mods, and you need the same mods to connect. **Prism Launcher** is the easiest way to handle this. It's free and open-source.

1. Go to **https://prismlauncher.org/download/**
2. Download the version for your OS (Windows / Mac / Linux).
3. Install and open it.
4. The first time it opens, it'll ask you to log into your Microsoft / Minecraft account. Do that — it uses your existing Minecraft purchase, you don't buy anything again.

---

## Step 2: Create an instance with NeoForge

An "instance" is just a self-contained Minecraft install. We need one running **NeoForge** on **Minecraft 1.21.1**.

1. In Prism, click **Add Instance** (top-left).
2. Pick **Vanilla**.
3. **Name:** anything (e.g. "Harris Server").
4. **Minecraft version:** select **1.21.1**. (Uncheck "Release" filter if you don't see it.)
5. On the left panel, click **NeoForge**.
6. Pick the latest NeoForge version for 1.21.1 and click **OK**.
7. Click **OK** again to create the instance.

Prism will download everything. Takes a minute.

---

## Step 3: Add the mods

I'll send you a ZIP file or a folder with all the mods — call it `mods.zip`. These are the exact mods my server runs. You **must** use these, or the server will reject your connection.

1. In Prism, right-click your new instance → **Edit**.
2. On the left, click **Mods**.
3. Click **Add** (or drag and drop the `.jar` files from my ZIP into this window).
4. Select all the mod `.jar` files I sent you.
5. Close the window.

Double-check every mod is enabled (checkbox on the left of each row).

---

## Step 4: Launch and join the server

1. Double-click the instance to launch. First launch is slow — mods load. Wait for the Minecraft main menu.
2. Click **Multiplayer** → **Add Server**.
3. **Server Name:** anything.
4. **Server Address:** paste the IP I send you.
5. Click **Done**, then double-click the server to join.

**If it says "outdated client" or "outdated server":** your NeoForge version doesn't match mine. Tell me what version yours is — I'll match it for you.

**If it says "missing mods" or lists specific mods:** you skipped Step 3 or missed a file. Add the missing ones.

---

## Step 5: Get to the build area

Once you're in:

1. I'll give you permission to fly and use creative-mode stuff. If you can't fly, tell me.
2. I'll teleport you to a flat build area, or you can run `/tp <coordinates>` with the numbers I give you.
3. Press **`E`** to open your inventory. You should see the creative tabs (lots of colored squares at the top). If you only see a 2×2 crafting grid, you're not in creative — tell me.

---

## Step 6: Build the thing

I'll send you a reference image or description. Build it however you want — WorldEdit (next step) doesn't care about the order you place blocks.

Tips:
- **Middle-click** a block to copy it into your hotbar.
- **Double-tap space** to fly, space to go up, shift to go down.
- If you mess up, break blocks with left-click. No penalty.
- Build **aligned to a corner you'll remember** — e.g. "the front-left corner is at this fencepost." Makes the next step easier.

---

## Step 7: Select the build with WorldEdit

WorldEdit is a mod already installed on the server. You use it with chat commands (start with `/`).

1. Type in chat: `//wand` — it gives you a wooden axe automatically.
2. **Left-click** the block at one corner of your build (e.g. bottom-front-left corner of the ground).
3. **Right-click** the block at the opposite corner (top-back-right — include the roof and any overhang).
4. You should see `First position set` and `Second position set` messages.

To check you got everything, type `//size`. It prints the dimensions. Make sure those numbers cover the whole build, including the roof.

If you missed something, just re-click the corners — it overwrites the old selection.

---

## Step 8: Save the schematic

Two commands, in order:

```
//copy
//schem save <name>
```

Replace `<name>` with something descriptive, no spaces. Examples: `townhall_lvl1`, `harris_bakery`, `stone_tower`.

You should see `<name> saved.` or similar. That's it — the file is on my server now.

**Send me the exact name you used** so I know which one to grab.

---

## Troubleshooting

- **Prism won't launch / crashes on start** → make sure you installed Java if prompted. Prism usually handles this automatically but some Mac installs need it manually.
- **"Missing mods" on join** → you skipped a mod in Step 3, or added the wrong version. Send me a screenshot of the mod list.
- **"You don't have permission"** on `//wand` or `//copy` → tell me, I need to grant WorldEdit perms.
- **Can't left-click to set position 1** → you need the wooden axe in hand. Run `//wand` again.
- **`//size` shows wrong dimensions** → re-select. Remember WorldEdit is inclusive — both corner blocks are part of the selection.
- **Build is off-center when I use it** → origin point issue. Tell me which corner you set as position 1 and I can adjust.

---

## Quick cheat sheet

| Command | What it does |
|---|---|
| `//wand` | Gives you the selection axe |
| `//pos1` / `//pos2` | Sets a corner at your feet (alt to clicking) |
| `//size` | Shows current selection dimensions |
| `//copy` | Copies selection to clipboard |
| `//schem save <name>` | Saves clipboard to a file on the server |
| `//schem list` | Lists saved schematics (to double-check yours is there) |

Ping me if anything's unclear. Thanks again!
