# Roguelite trainer skins — sourcing list

43 skins are already mapped from RCT's own library (see `gen_trainer_texture_pack.py`).
These 88 named characters have **no** RCT texture and currently render as the default skin.

## How to add one

Drop a **64×64 Minecraft skin PNG** into:

    custom-mods/cobblemon-npc/src/main/resources/assets/rctmod/textures/trainers/single/

named exactly `<trainer id>.png` from the table below. RCT resolves skins by trainer id;
the `textureResource` field in trainer JSON is ignored by the renderer.

Partial is fine — any file present is used, anything missing falls back to the default.
Skins do not need to arrive together or in order.

## Licensing

Fan-made skins of Nintendo characters are **server-side content only**. They must not be
committed to anything intended for publication (plan §2.7, §1.2). Keep provenance notes
if the source asks for attribution.

## The list

| Region | Character | Filename |
| --- | --- | --- |
| Kanto | Janine | `rgl_janine.png` |
| Hoenn | Roxanne | `rgl_roxanne.png` |
| Hoenn | Brawly | `rgl_brawly.png` |
| Hoenn | Wattson | `rgl_wattson.png` |
| Hoenn | Flannery | `rgl_flannery.png` |
| Hoenn | Norman | `rgl_norman.png` |
| Hoenn | Winona | `rgl_winona.png` |
| Hoenn | Tate | `rgl_tate.png` |
| Hoenn | Liza | `rgl_liza.png` |
| Hoenn | Juan | `rgl_juan.png` |
| Hoenn | Wallace | `rgl_wallace.png` |
| Unova | Cilan | `rgl_cilan.png` |
| Unova | Chili | `rgl_chili.png` |
| Unova | Cress | `rgl_cress.png` |
| Unova | Lenora | `rgl_lenora.png` |
| Unova | Burgh | `rgl_burgh.png` |
| Unova | Elesa | `rgl_elesa.png` |
| Unova | Clay | `rgl_clay.png` |
| Unova | Skyla | `rgl_skyla.png` |
| Unova | Brycen | `rgl_brycen.png` |
| Unova | Drayden | `rgl_drayden.png` |
| Unova | Roxie | `rgl_roxie.png` |
| Unova | Marlon | `rgl_marlon.png` |
| Unova | Cheren | `rgl_cheren.png` |
| Unova | Iris | `rgl_iris.png` |
| Kalos | Viola | `rgl_viola.png` |
| Kalos | Grant | `rgl_grant.png` |
| Kalos | Korrina | `rgl_korrina.png` |
| Kalos | Ramos | `rgl_ramos.png` |
| Kalos | Clemont | `rgl_clemont.png` |
| Kalos | Valerie | `rgl_valerie.png` |
| Kalos | Olympia | `rgl_olympia.png` |
| Kalos | Wulfric | `rgl_wulfric.png` |
| Alola | Ilima | `rgl_ilima.png` |
| Alola | Lana | `rgl_lana.png` |
| Alola | Kiawe | `rgl_kiawe.png` |
| Alola | Mallow | `rgl_mallow.png` |
| Alola | Sophocles | `rgl_sophocles.png` |
| Alola | Acerola | `rgl_acerola.png` |
| Alola | Mina | `rgl_mina.png` |
| Alola | Hala | `rgl_hala.png` |
| Alola | Olivia | `rgl_olivia.png` |
| Alola | Nanu | `rgl_nanu.png` |
| Alola | Hapu | `rgl_hapu.png` |
| Galar | Milo | `rgl_milo.png` |
| Galar | Nessa | `rgl_nessa.png` |
| Galar | Kabu | `rgl_kabu.png` |
| Galar | Bea | `rgl_bea.png` |
| Galar | Allister | `rgl_allister.png` |
| Galar | Opal | `rgl_opal.png` |
| Galar | Gordie | `rgl_gordie.png` |
| Galar | Melony | `rgl_melony.png` |
| Galar | Piers | `rgl_piers.png` |
| Galar | Marnie | `rgl_marnie.png` |
| Galar | Raihan | `rgl_raihan.png` |
| Galar | Bede | `rgl_bede.png` |
| Paldea | Katy | `rgl_katy.png` |
| Paldea | Brassius | `rgl_brassius.png` |
| Paldea | Iono | `rgl_iono.png` |
| Paldea | Kofu | `rgl_kofu.png` |
| Paldea | Larry | `rgl_larry.png` |
| Paldea | Ryme | `rgl_ryme.png` |
| Paldea | Tulip | `rgl_tulip.png` |
| Paldea | Grusha | `rgl_grusha.png` |
| EliteFour | Will | `rgl_will.png` |
| EliteFour | Karen | `rgl_karen.png` |
| EliteFour | Sidney | `rgl_sidney.png` |
| EliteFour | Phoebe | `rgl_phoebe.png` |
| EliteFour | Glacia | `rgl_glacia.png` |
| EliteFour | Drake | `rgl_drake.png` |
| EliteFour | Shauntal | `rgl_shauntal.png` |
| EliteFour | Marshal | `rgl_marshal.png` |
| EliteFour | Grimsley | `rgl_grimsley.png` |
| EliteFour | Caitlin | `rgl_caitlin.png` |
| EliteFour | Malva | `rgl_malva.png` |
| EliteFour | Siebold | `rgl_siebold.png` |
| EliteFour | Wikstrom | `rgl_wikstrom.png` |
| EliteFour | Drasna | `rgl_drasna.png` |
| EliteFour | Molayne | `rgl_molayne.png` |
| EliteFour | Kahili | `rgl_kahili.png` |
| Champion | Blue | `rgl_blue.png` |
| Champion | Steven | `rgl_steven.png` |
| Champion | Wallace | `rgl_wallace.png` |
| Champion | Alder | `rgl_alder.png` |
| Champion | Diantha | `rgl_diantha.png` |
| Champion | Kukui | `rgl_kukui.png` |
| Champion | Leon | `rgl_leon.png` |
| Champion | Geeta | `rgl_geeta.png` |
