# Skin staging

Drop downloaded trainer skin PNGs here. Nothing in this folder is committed —
it is a workspace, not a destination.

## The one thing that helps

**Put the character's name somewhere in the filename.** Anything containing it works:

    flannery.png
    flannery-gym-leader.png
    Flannery (1).png
    download (3) flannery.png

That is enough for the import to match the file to `rgl_flannery.png` on its own.
A file whose name matches no character is reported rather than guessed at, so a
wrong skin never lands silently.

## What happens next

Point me at this folder and I will:

- check each file is a real Minecraft skin (64×64; legacy 64×32 is flagged, not silently upscaled)
- rename to the correct `rgl_*.png` trainer id
- move it into `custom-mods/cobblemon-npc/src/main/resources/assets/rctmod/textures/trainers/single/`
- build an HTML page showing every skin beside the character it is cast as, so you can
  approve or reject the set visually before anything is committed
- update `ops/roguelite-missing-skins.md` to show what is still outstanding

Partial batches are fine. Any skin present is used; anything missing falls back to the
default, so there is no need to finish the list — or to work through it in order.

## Names still needed

See `ops/roguelite-missing-skins.md` (88 characters).

## Licensing

Fan-made skins of Nintendo characters are **server-side content only** and must not reach
anything intended for publication (plan §2.7, §1.2). If a source asks for attribution, note
it here and it will be carried through.
