# Roguelite trainer skins — coverage and how to change one

**Status: complete. All 87 characters RCT does not ship now have a skin.**

130 `rgl_*` trainer ids are installed in
`custom-mods/cobblemon-npc/src/main/resources/assets/rctmod/textures/trainers/single/`:

| Source | Count | Driven by |
| --- | --- | --- |
| RCT's own library | 43 ids | `ROGUELITE_SKINS` in `gen_trainer_texture_pack.py` |
| Hand-sourced | 84 | `ops/skin-staging/` → `install_hand_sourced_skins.py` |
| Our own server-gym casts | 3 | `ROGUELITE_FROM_SERVER_GYMS` (Alder, Cheren, Grant) |

RCT resolves skins client-side by trainer id — `single/<trainerId>.png`, then
`groups/<group>.png`, then `default.png`. The `textureResource` field in trainer JSON is
ignored by the renderer; it is only used as a mapping table by the generator.

## Adding or replacing a skin

Drop a **64×64 PNG** into `ops/skin-staging/` named after the character (`Brawly.png`, any
capitalisation), then:

    python3 ops/install_hand_sourced_skins.py

The installer refuses a file rather than installing it silently when it is not really a
PNG, is the wrong size, or names no character in the roster. Legacy 64×32 skins are
converted automatically by mirroring the single limb pair face by face — five of the
installed skins came in that way (Malva, Olympia, Ramos, Roxanne, Wulfric).

Replacing one of the three server-gym casts also means deleting its entry from
`ROGUELITE_FROM_SERVER_GYMS`, or the next run of `gen_trainer_texture_pack.py` puts the old
face back. The installer warns when this applies.

To review what is installed:

    python3 ops/gen_skin_review_page.py && open /tmp/roguelite-skins.html

Renders every skin as a front-facing figure, grouped by source. It writes outside the repo
on purpose — see Licensing.

## Worth a second look

Sourced and installed, but the art looks weak rather than wrong — bare skin-coloured limbs
where the character should be clothed, which reads as a low-effort fan skin in game:

| Id | Problem |
| --- | --- |
| `rgl_phoebe` | Renders close to unclothed; least usable of the set |
| `rgl_gordie` | Washed-out pinks and greys, does not read as Gordie's climbing gear |
| `rgl_hapu` | Bare arms, gold torso pattern |
| `rgl_marnie` | Bare arms over a pink dress |

`rgl_roxanne` is worth a glance for a different reason: it is one of the legacy 64×32
conversions, so its second limb pair is mirrored rather than authored.

## Downloading in bulk

`ops/fetch-missing-skins.js` downloads a list of skins from planetminecraft in one paste:
open any planetminecraft page, open the console (⌘⌥J), paste the file. It names each file
what the installer expects and rejects anything that comes back over 20 KB.

Regenerate it after editing a download table here:

    python3 ops/gen_skin_fetch_snippet.py

It reads only a "Still needed" section, so with the list complete it currently has nothing
to emit. Add such a section with `| \`Name.png\` | label | url |` rows to use it again.

**It must run in a browser on the planetminecraft origin.** The site sits behind a
Cloudflare WAF that returns 403 to every request from a shell — the site and
`static.planetminecraft.com` alike, with or without a browser user-agent. A same-origin
`fetch` carries the site's cookies and is served normally; nothing scripted from outside
the browser is.

### The failure this avoids

The first sourcing pass produced 28 files that were **HTML skin pages saved with a `.png`
name**, 110 KB each. Every tool downstream accepts them and renders a missing texture, so
it fails silently. The tell is size: a real 64×64 skin is 0.8–6 KB. Use the site's
**Download** button, not File → Save Page As. Both the installer and the snippet now catch
this.

## Licensing

Fan-made skins of Nintendo characters are **server-side content only**. They must not be
committed to anything intended for publication (plan §2.7, §1.2) — that includes the
standalone `cobblemon-roguelite` mod. They ship in `cobblemon-npc`, which is modpack-side.
Keep provenance notes if a source asks for attribution.
