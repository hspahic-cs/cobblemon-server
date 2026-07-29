# Roguelite trainer skins — sourcing list

**Status: 27 characters left of the original 87.**

Coverage of the roguelite's named cast:

| Source | Count | Where it comes from |
| --- | --- | --- |
| RCT's own library | 43 ids | `ROGUELITE_SKINS` in `gen_trainer_texture_pack.py` |
| Hand-sourced | 57 | `ops/skin-staging/` → `install_hand_sourced_skins.py` |
| Our own server-gym casts | 3 | `ROGUELITE_FROM_SERVER_GYMS` (Alder, Cheren, Grant) |
| **Still skinless** | **27** | the table below |

### Correction to the earlier version of this file

The previous list said 88 characters had no skin. That was wrong twice over:

- The real count was **87**, not 88.
- It was built purely from what RCT's jar ships and was **never cross-checked against the
  282 skins this repo already installs**. Eleven of the characters on it — Alder, Brycen,
  Cheren, Clay, Drayden, Grant, Korrina, Marnie, Skyla, Valerie, Viola — already had a face
  on this server under a `gym_*` id, because our own gyms cast those same characters.

Alder was the one that surfaced this. Eight of the eleven now have a purpose-made
hand-sourced skin instead; Alder, Cheren and Grant use our server's existing cast, via
`ROGUELITE_FROM_SERVER_GYMS`. That is rule 3(a) of the matching rules in
`gen_trainer_texture_pack.py` — prefer the face our own server already uses for a
character, so a player sees the same person in both modes. It is *not* the lookalike
substitution rule 4 forbids: rule 4 is about casting an unrelated NPC as a character.

## How to add one

Drop a **64×64 Minecraft skin PNG** into `ops/skin-staging/` named after the character
(`Brawly.png` — the character's name, any capitalisation), then run:

    python3 ops/install_hand_sourced_skins.py

It validates and installs each file under the right `rgl_*` trainer id. The staging folder
is gitignored; only the installed result is committed. Re-runnable, so files can arrive in
batches and in any order — anything missing falls back to RCT's default skin.

The installer refuses a file rather than installing it silently when it is not really a
PNG, is not 64×64, or has a filename that names no character in the roster. Legacy 64×32
skins are converted automatically (Roxanne was one).

To review what is installed:

    python3 ops/gen_skin_review_page.py && open /tmp/roguelite-skins.html

That renders every skin as a front-facing figure, grouped by source. It writes outside the
repo on purpose — see Licensing below.

### The mistake to avoid

26 of the 27 below were already downloaded once, but came out as **HTML pages saved with a
`.png` name** — 110 KB each, which every tool accepts and renders as a missing texture (one,
Raihan, had no extension at all). Use the site's **Download** button; not File → Save Page
As. The installer catches both and reports them as "re-download".

These pages cannot be fetched from the command line: planetminecraft returns 403 to `curl`
and to automated fetches, browser user-agent or not. They have to come from a real browser.

## Licensing

Fan-made skins of Nintendo characters are **server-side content only**. They must not be
committed to anything intended for publication (plan §2.7, §1.2). Keep provenance notes if
the source asks for attribution — the source page for each is recorded below.

## Still needed — 26 re-downloads

### The fast way: do all 26 at once

Open any planetminecraft page, open the browser console (⌘⌥J), and paste
**`ops/fetch-missing-skins.js`**. It downloads all 26 with the right filenames, skips
anything that comes back as a page rather than an image, and prints what succeeded. Chrome
asks once to allow multiple downloads. Then move them into `ops/skin-staging/` and run the
installer.

Regenerate the snippet after editing the table below:

    python3 ops/gen_skin_fetch_snippet.py

It has to run *on* the planetminecraft origin — the fetch needs the site's own cookies,
which is what separates it from the automated request the WAF rejects.

### Or one at a time

Direct download links, extracted from the pages that were saved last time. Filename on the
left is what to save it as in `ops/skin-staging/`.

| Save as | Skin | Download |
| --- | --- | --- |
| `Acerola.png` | acerola | https://www.planetminecraft.com/skin/acerola-5729551/download/file/16137530/ |
| `Allister.png` | Allister | https://www.planetminecraft.com/skin/allister/download/file/12268069/ |
| `Bede.png` | Bede – Pokémon Sw/Sh | https://www.planetminecraft.com/skin/bede-pokemon-sw-sh/download/file/12533145/ |
| `Blue.png` | Champion Blue (Sun/Moon) | https://www.planetminecraft.com/skin/pokemon-champion-blue-pokemon-sun-moon/download/file/11197001/ |
| `Geeta.png` | Geeta | https://www.planetminecraft.com/skin/pokemon-geeta/download/file/17088253/ |
| `Gordie.png` | Gordie (Sword/Shield) | https://www.planetminecraft.com/skin/t1-5653864/download/file/15909190/ |
| `Grimsley.png` | Grimsley (BW) | https://www.planetminecraft.com/skin/grimsley-pokemon-bw/download/file/14325163/ |
| `Grusha.png` | Grusha | https://www.planetminecraft.com/skin/grusha-pokemon/download/file/16402973/ |
| `Ilima.png` | Ilima (Sun/Moon) | https://www.planetminecraft.com/skin/ilima-pokemon-sun-and-moon-a-lot-better-in-3d-oof/download/file/11620324/ |
| `Iris.png` | Champion Iris | https://www.planetminecraft.com/skin/champion-iris-pokemon-6236933/download/file/17671729/ |
| `Karen.png` | karen | https://www.planetminecraft.com/skin/karen-5092265/download/file/14325173/ |
| `Katy.png` | Gym Leader Katy (SV) | https://www.planetminecraft.com/skin/pokemon-scarlet-amp-violet-gym-leader-katy/download/file/19832473/ |
| `Kiawe.png` | kiawe | https://www.planetminecraft.com/skin/kiawe/download/file/12475957/ |
| `Kukui.png` | Professor Kukui | https://www.planetminecraft.com/skin/professor-kukui-pokemon-sun-and-moon/download/file/11790939/ |
| `Lana.png` | Lana | https://www.planetminecraft.com/skin/lana-pok-mon/download/file/16334895/ |
| `Malva.png` | Elite Four Malva | https://www.planetminecraft.com/skin/elite-four-malva/download/file/7430294/ |
| `Mina.png` | Mina (Sun/Moon) | https://www.planetminecraft.com/skin/mina---pokemon-sunmoon/download/file/10763377/ |
| `Olivia.png` | olivia | https://www.planetminecraft.com/skin/olivia-6190586/download/file/17528550/ |
| `Olympia.png` | Gym Leader Olympia | https://www.planetminecraft.com/skin/gym-leader-olympia/download/file/7421938/ |
| `Phoebe.png` | Phoebe | https://www.planetminecraft.com/skin/phoebe-from-pokemon/download/file/10371242/ |
| `Piers.png` | piers | https://www.planetminecraft.com/skin/piers/download/file/12553553/ |
| `Raihan.png` | Raihan! // Pokemon | https://www.planetminecraft.com/skin/raihan-pokemon/download/file/13959617/ |
| `Ramos.png` | Gym Leader Ramos | https://www.planetminecraft.com/skin/gym-leader-ramos/download/file/7420705/ |
| `Steven.png` | Steven Stone | https://www.planetminecraft.com/skin/steven-stone-pokemon-4278008/download/file/12089666/ |
| `Tulip.png` | tulip | https://www.planetminecraft.com/skin/tulip-6655537/download/file/18929376/ |
| `Wulfric.png` | Gym Leader Wulfric | https://www.planetminecraft.com/skin/gym-leader-wulfric/download/file/7422486/ |

## Still needed — 1 with no source yet

No page was saved for this one, so it still needs finding:

| Save as | Character |
| --- | --- |
| `Nanu.png` | Nanu — Alola Ula'ula kahuna |

## Optional upgrades

Covered, but by our server's existing cast rather than a purpose-made skin. A
hand-sourced file dropped in staging will take over automatically — delete the name from
`ROGUELITE_FROM_SERVER_GYMS` at the same time so there is only one source per id.

| Save as | Currently using | Download |
| --- | --- | --- |
| `Cheren.png` | `gym_12_cheren` | https://www.planetminecraft.com/skin/pokemon-black-white-cheren/download/file/15794298/ |
| `Grant.png` | `gym_14_grant` | https://www.planetminecraft.com/skin/gym-leader-grant/download/file/7418586/ |
| `Alder.png` | `gym_20_alder` | — none found |
