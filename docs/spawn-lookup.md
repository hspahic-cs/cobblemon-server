# Pokémon spawn biomes

Type a partial name to filter. Bucket rarity (within ultra-rare bucket too) determines how heavily a species shows up in its biomes; see the [legendaries page](legendaries.md) for the bucket roll rates.

<input type="text" id="spawn-filter" placeholder="Type a name (e.g. 'char' for Charmander, Charmeleon, Charizard…)" style="width:100%;padding:8px;margin-bottom:12px;font-size:14px;box-sizing:border-box;">

<div style="overflow-x:auto;">
<table id="spawn-table" style="width:100%;border-collapse:collapse;font-size:13px;">
<thead><tr><th style="text-align:left;padding:6px;border-bottom:2px solid currentColor;">Species</th><th style="text-align:left;padding:6px;border-bottom:2px solid currentColor;">Bucket</th><th style="text-align:left;padding:6px;border-bottom:2px solid currentColor;">Biomes</th></tr></thead>
<tbody>
<tr><td style="padding:4px 6px;">Abra</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Abra</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">magical</td></tr>
<tr><td style="padding:4px 6px;">Abra</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">hills, temperate</td></tr>
<tr><td style="padding:4px 6px;">Absol</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">mountain, snowy_forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Accelgor</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Aegislash</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Aerodactyl</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Aggron</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Aipom</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Alakazam</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Alakazam</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">magical</td></tr>
<tr><td style="padding:4px 6px;">Alakazam</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">hills, temperate</td></tr>
<tr><td style="padding:4px 6px;">Alcremie</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Alcremie</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Alomomola</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Alomomola</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_ocean, ocean</td></tr>
<tr><td style="padding:4px 6px;">Altaria</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow</td></tr>
<tr><td style="padding:4px 6px;">Altaria</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld, peak, sky</td></tr>
<tr><td style="padding:4px 6px;">Amaura</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Ambipom</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Amoonguss</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mushroom</td></tr>
<tr><td style="padding:4px 6px;">Ampharos</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">highlands, plains</td></tr>
<tr><td style="padding:4px 6px;">Annihilape</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, jungle</td></tr>
<tr><td style="padding:4px 6px;">Anorith</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Araquanid</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, freshwater, grassland, jungle</td></tr>
<tr><td style="padding:4px 6px;">Araquanid</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, freshwater, grassland, jungle</td></tr>
<tr><td style="padding:4px 6px;">Arbok</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, nether-desert</td></tr>
<tr><td style="padding:4px 6px;">Arboliva</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Arcanine</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Arceus</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Archen</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Archeops</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Ariados</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, jungle, overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Armaldo</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Armarouge</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Aromatisse</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Aromatisse</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, floral</td></tr>
<tr><td style="padding:4px 6px;">Aron</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Arrokuda</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, coast, ocean, swamp</td></tr>
<tr><td style="padding:4px 6px;">Articuno</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">freezing, overworld</td></tr>
<tr><td style="padding:4px 6px;">Aurorus</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Avalugg</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">frozen_ocean, nether-frozen</td></tr>
<tr><td style="padding:4px 6px;">Axew</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Azelf</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">coast, island, river, sky</td></tr>
<tr><td style="padding:4px 6px;">Azumarill</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">floral, hills, magical, river, snowy_forest, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Azumarill</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">river</td></tr>
<tr><td style="padding:4px 6px;">Azurill</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">floral, hills, magical, river, snowy_forest, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Azurill</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">river</td></tr>
<tr><td style="padding:4px 6px;">Bagon</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, dripstone</td></tr>
<tr><td style="padding:4px 6px;">Bagon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">dripstone, peak</td></tr>
<tr><td style="padding:4px 6px;">Baltoy</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Baltoy</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, desert</td></tr>
<tr><td style="padding:4px 6px;">Banette</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Banette</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Barbaracle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Barbaracle</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Barboach</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freshwater, hills, jungle, mushroom, overworld, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Barboach</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Barraskewda</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, coast, ocean, swamp</td></tr>
<tr><td style="padding:4px 6px;">Basculegion</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, forest, freshwater, grassland, jungle, mountain, snowy_forest, taiga, tundra</td></tr>
<tr><td style="padding:4px 6px;">Basculin</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, forest, freshwater, grassland, jungle, mountain, snowy_forest, taiga, tundra</td></tr>
<tr><td style="padding:4px 6px;">Bastiodon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Bayleef</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">#aether:is_aether, floral, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Beartic</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Beedrill</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#the_bumblezone:the_bumblezone, aether:skyroot_forest, aether:skyroot_woodland, forest, jungle</td></tr>
<tr><td style="padding:4px 6px;">Beheeyem</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Beheeyem</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Beldum</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether</td></tr>
<tr><td style="padding:4px 6px;">Beldum</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, end, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Beldum</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">dripstone, peak</td></tr>
<tr><td style="padding:4px 6px;">Bellibolt</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freshwater</td></tr>
<tr><td style="padding:4px 6px;">Bellossom</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">floral, jungle, savanna, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Bellsprout</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Bergmite</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">frozen_ocean, nether-frozen</td></tr>
<tr><td style="padding:4px 6px;">Bewear</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">bamboo</td></tr>
<tr><td style="padding:4px 6px;">Bibarel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, freshwater, snowy_forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Bibarel</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, freshwater, taiga</td></tr>
<tr><td style="padding:4px 6px;">Bidoof</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, freshwater, snowy_forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Bidoof</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, freshwater, taiga</td></tr>
<tr><td style="padding:4px 6px;">Binacle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Binacle</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Blacephalon</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Blacephalon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">nether-desert</td></tr>
<tr><td style="padding:4px 6px;">Blastoise</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">freshwater, hills, jungle, temperate, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Blaziken</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, plains</td></tr>
<tr><td style="padding:4px 6px;">Blissey</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Blitzle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Boldore</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Boldore</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Boltund</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Bonsly</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Bouffalant</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Bounsweet</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Braixen</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Brambleghast</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert, nether-desert</td></tr>
<tr><td style="padding:4px 6px;">Bramblin</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert, nether-desert</td></tr>
<tr><td style="padding:4px 6px;">Braviary</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">sky</td></tr>
<tr><td style="padding:4px 6px;">Braviary</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, hills, mountain</td></tr>
<tr><td style="padding:4px 6px;">Breloom</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, mushroom</td></tr>
<tr><td style="padding:4px 6px;">Brionne</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">coast, ocean, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Bronzong</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Bronzong</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">jungle, peak</td></tr>
<tr><td style="padding:4px 6px;">Bronzor</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Bronzor</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">jungle, peak</td></tr>
<tr><td style="padding:4px 6px;">Brutebonnet</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Bruxish</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Bruxish</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Budew</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Buizel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, freshwater, grassland, hills, jungle, taiga</td></tr>
<tr><td style="padding:4px 6px;">Buizel</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freshwater</td></tr>
<tr><td style="padding:4px 6px;">Bulbasaur</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Buneary</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, hills, snowy_forest, snowy_taiga, taiga</td></tr>
<tr><td style="padding:4px 6px;">Bunnelby</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, temperate</td></tr>
<tr><td style="padding:4px 6px;">Butterfree</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, jungle, temperate, the_bumblezone:floral_meadow, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Buzzwole</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Buzzwole</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Cacnea</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert</td></tr>
<tr><td style="padding:4px 6px;">Cacturne</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert</td></tr>
<tr><td style="padding:4px 6px;">Calyrex</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">snowy_taiga</td></tr>
<tr><td style="padding:4px 6px;">Camerupt</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, nether-overgrowth, nether-wasteland, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Capsakid</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, nether-overgrowth</td></tr>
<tr><td style="padding:4px 6px;">Carbink</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, the_bumblezone:crystal_canyon</td></tr>
<tr><td style="padding:4px 6px;">Carbink</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">nether-quartz</td></tr>
<tr><td style="padding:4px 6px;">Carnivine</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, lush, overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Carracosta</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Carvanha</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, ocean, overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Caterpie</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, jungle, plains, the_bumblezone:floral_meadow, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Celebi</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Centiskorch</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, nether-basalt, nether-wasteland, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Ceruledge</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Cetitan</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">frozen_ocean, glacial, tundra</td></tr>
<tr><td style="padding:4px 6px;">Cetoddle</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">frozen_ocean, glacial, tundra</td></tr>
<tr><td style="padding:4px 6px;">Chandelure</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-soul_fire, overworld</td></tr>
<tr><td style="padding:4px 6px;">Chansey</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Charcadet</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Charizard</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">hills, nether-basalt, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Charmander</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">hills, nether-basalt, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Charmeleon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">hills, nether-basalt, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Chatot</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, sky, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Chatot</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, jungle</td></tr>
<tr><td style="padding:4px 6px;">Chesnaught</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Chespin</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Chewtle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freshwater, hills, jungle, temperate</td></tr>
<tr><td style="padding:4px 6px;">Chienpao</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Chikorita</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">#aether:is_aether, floral, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Chimchar</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain, nether-forest</td></tr>
<tr><td style="padding:4px 6px;">Chimecho</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, magical, mountain, overworld, spooky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Chinchou</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">deep_ocean, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Chingling</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, magical, mountain, overworld, spooky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Chiyu</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">nether-basalt, nether-wasteland</td></tr>
<tr><td style="padding:4px 6px;">Cinccino</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">magical, overworld</td></tr>
<tr><td style="padding:4px 6px;">Cinderace</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, grassland, tundra</td></tr>
<tr><td style="padding:4px 6px;">Clamperl</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Clamperl</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">ocean, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Clauncher</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Clawitzer</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Claydol</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Claydol</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, desert</td></tr>
<tr><td style="padding:4px 6px;">Clefable</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, dripstone, hills, magical</td></tr>
<tr><td style="padding:4px 6px;">Clefairy</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, dripstone, hills, magical</td></tr>
<tr><td style="padding:4px 6px;">Cleffa</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, dripstone, hills, magical</td></tr>
<tr><td style="padding:4px 6px;">Clobbopus</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Clobbopus</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, ocean, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Clodsire</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, has_block-mud</td></tr>
<tr><td style="padding:4px 6px;">Clodsire</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">arid, savanna</td></tr>
<tr><td style="padding:4px 6px;">Cloyster</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, cold_ocean, frozen_ocean, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Cobalion</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Cofagrigus</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Cofagrigus</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Combee</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, #the_bumblezone:the_bumblezone, overworld, temperate</td></tr>
<tr><td style="padding:4px 6px;">Combusken</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, plains</td></tr>
<tr><td style="padding:4px 6px;">Comfey</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">floral, the_bumblezone:floral_meadow, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Comfey</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Conkeldurr</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Copperajah</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, hills, savanna</td></tr>
<tr><td style="padding:4px 6px;">Corphish</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freshwater, hills, jungle, temperate</td></tr>
<tr><td style="padding:4px 6px;">Corsola</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach, byg:warped_desert, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Corsola</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">beach, nether-quartz, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Corviknight</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, sky, spooky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Corvisquire</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, sky, spooky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Cosmog</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Cottonee</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, plains</td></tr>
<tr><td style="padding:4px 6px;">Crabominable</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">peak</td></tr>
<tr><td style="padding:4px 6px;">Crabrawler</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast</td></tr>
<tr><td style="padding:4px 6px;">Cradily</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Cramorant</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Cramorant</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, coast, island, ocean, sky</td></tr>
<tr><td style="padding:4px 6px;">Cranidos</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Crawdaunt</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freshwater, hills, jungle, temperate</td></tr>
<tr><td style="padding:4px 6px;">Cresselia</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">floral</td></tr>
<tr><td style="padding:4px 6px;">Croagunk</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Crobat</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, forest, overworld, spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Crobat</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark, overworld</td></tr>
<tr><td style="padding:4px 6px;">Crocalor</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, nether-wasteland</td></tr>
<tr><td style="padding:4px 6px;">Croconaw</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">arid, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Crustle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands</td></tr>
<tr><td style="padding:4px 6px;">Cryogonal</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-frozen</td></tr>
<tr><td style="padding:4px 6px;">Cryogonal</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Cubchoo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Cubone</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert, nether-desert, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Cufant</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, hills, savanna</td></tr>
<tr><td style="padding:4px 6px;">Cursola</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach, byg:warped_desert, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Cursola</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">beach, nether-quartz, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Cutiefly</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, overworld, the_bumblezone:floral_meadow, the_bumblezone:pollinated_fields</td></tr>
<tr><td style="padding:4px 6px;">Cyclizar</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">badlands, grassland</td></tr>
<tr><td style="padding:4px 6px;">Cyndaquil</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">nether-crimson, nether-forest, nether-overgrowth, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Dachsbun</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Darkrai</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Darmanitan</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert, jungle, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Dartrix</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, snowy_forest, snowy_taiga, taiga</td></tr>
<tr><td style="padding:4px 6px;">Darumaka</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert, jungle, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Decidueye</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, snowy_forest, snowy_taiga, taiga</td></tr>
<tr><td style="padding:4px 6px;">Dedenne</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna, shrubland</td></tr>
<tr><td style="padding:4px 6px;">Deerling</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, forest, hills, plains, snowy_forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Deerling</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Deino</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Deino</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Delibird</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether</td></tr>
<tr><td style="padding:4px 6px;">Delibird</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">peak</td></tr>
<tr><td style="padding:4px 6px;">Delphox</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Deoxys</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Dewgong</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Dewott</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">beach, coast, cold_ocean, ocean, snowy_beach, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Dewpider</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, freshwater, grassland, jungle</td></tr>
<tr><td style="padding:4px 6px;">Dewpider</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, freshwater, grassland, jungle</td></tr>
<tr><td style="padding:4px 6px;">Dhelmise</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Dhelmise</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Dialga</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jagged_peaks, peak</td></tr>
<tr><td style="padding:4px 6px;">Diancie</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Diggersby</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, temperate</td></tr>
<tr><td style="padding:4px 6px;">Diglett</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Ditto</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Ditto</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Ditto</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Dodrio</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, savanna</td></tr>
<tr><td style="padding:4px 6px;">Doduo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, savanna</td></tr>
<tr><td style="padding:4px 6px;">Dolliv</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Dondozo</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">bamboo, forest, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Donphan</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, savanna</td></tr>
<tr><td style="padding:4px 6px;">Doublade</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Dragalge</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Dragapult</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Dragonair</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Dragonair</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Dragonair</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">#aether:is_aether, magical, ocean</td></tr>
<tr><td style="padding:4px 6px;">Dragonite</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Dragonite</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Dragonite</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">#aether:is_aether, magical, ocean, sky</td></tr>
<tr><td style="padding:4px 6px;">Drakloak</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Drampa</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">peak, sky</td></tr>
<tr><td style="padding:4px 6px;">Drapion</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert</td></tr>
<tr><td style="padding:4px 6px;">Dratini</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Dratini</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Dratini</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">#aether:is_aether, magical, ocean</td></tr>
<tr><td style="padding:4px 6px;">Drednaw</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freshwater, hills, jungle, temperate</td></tr>
<tr><td style="padding:4px 6px;">Dreepy</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Drifblim</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">sky</td></tr>
<tr><td style="padding:4px 6px;">Drifblim</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Drifloon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">sky</td></tr>
<tr><td style="padding:4px 6px;">Drifloon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Drilbur</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Drizzile</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Drowzee</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Druddigon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Druddigon</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Dubwool</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, mountain, plains</td></tr>
<tr><td style="padding:4px 6px;">Ducklett</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, freshwater, sky</td></tr>
<tr><td style="padding:4px 6px;">Ducklett</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, freshwater, sky</td></tr>
<tr><td style="padding:4px 6px;">Dudunsparce</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Dugtrio</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Dunsparce</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Duosion</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">end, overworld</td></tr>
<tr><td style="padding:4px 6px;">Durant</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, hills, nether-mountain</td></tr>
<tr><td style="padding:4px 6px;">Dusclops</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-soul_sand, spooky</td></tr>
<tr><td style="padding:4px 6px;">Dusknoir</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-soul_sand, spooky</td></tr>
<tr><td style="padding:4px 6px;">Duskull</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-soul_sand, spooky</td></tr>
<tr><td style="padding:4px 6px;">Dwebble</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands</td></tr>
<tr><td style="padding:4px 6px;">Eelektrik</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Eelektrik</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Eelektross</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Eelektross</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Eevee</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Eevee</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, temperate</td></tr>
<tr><td style="padding:4px 6px;">Eevee</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Eiscue</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">frozen_ocean, glacial, tundra</td></tr>
<tr><td style="padding:4px 6px;">Ekans</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, nether-desert</td></tr>
<tr><td style="padding:4px 6px;">Eldegoss</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Electabuzz</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">hills, plains</td></tr>
<tr><td style="padding:4px 6px;">Electivire</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">hills, plains</td></tr>
<tr><td style="padding:4px 6px;">Electrike</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">grassland, hills</td></tr>
<tr><td style="padding:4px 6px;">Electrode</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Electrode</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Elekid</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">hills, plains</td></tr>
<tr><td style="padding:4px 6px;">Elgyem</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Elgyem</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Emboar</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">nether-crimson, nether-overgrowth, plains</td></tr>
<tr><td style="padding:4px 6px;">Emolga</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">taiga</td></tr>
<tr><td style="padding:4px 6px;">Empoleon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean, frozen_river, glacial, tundra</td></tr>
<tr><td style="padding:4px 6px;">Enamorus</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Entei</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Escavalier</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Espathra</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">arid</td></tr>
<tr><td style="padding:4px 6px;">Espeon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Espeon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">hills</td></tr>
<tr><td style="padding:4px 6px;">Espurr</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freezing, overworld</td></tr>
<tr><td style="padding:4px 6px;">Eternatus</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Excadrill</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Exeggcute</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, savanna</td></tr>
<tr><td style="padding:4px 6px;">Exeggcute</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">beach, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Exeggutor</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, savanna</td></tr>
<tr><td style="padding:4px 6px;">Exeggutor</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">beach, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Exploud</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Exploud</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Falinks</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Farfetchd</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Farfetchd</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Farigiraf</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Fearow</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, savanna, sky</td></tr>
<tr><td style="padding:4px 6px;">Feebas</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Fennekin</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Feraligatr</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">arid, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Ferroseed</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Ferrothorn</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Fezandipiti</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Fidough</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Finizen</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Finizen</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Finneon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">deep_ocean, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Flaaffy</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">highlands, plains</td></tr>
<tr><td style="padding:4px 6px;">Flabebe</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Flamigo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, savanna, swamp, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Flamigo</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, jungle, savanna, swamp, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Flareon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Flareon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, desert</td></tr>
<tr><td style="padding:4px 6px;">Fletchinder</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, nether-forest, nether-fungus, sky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Fletchling</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, nether-forest, nether-fungus, sky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Flittle</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">arid</td></tr>
<tr><td style="padding:4px 6px;">Floatzel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, freshwater, grassland, hills, jungle, taiga</td></tr>
<tr><td style="padding:4px 6px;">Floatzel</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freshwater</td></tr>
<tr><td style="padding:4px 6px;">Floette</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Floragato</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">floral, magical</td></tr>
<tr><td style="padding:4px 6px;">Florges</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Fluttermane</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Flygon</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Fomantis</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">floral, jungle, the_bumblezone:floral_meadow, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Foongus</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mushroom</td></tr>
<tr><td style="padding:4px 6px;">Forretress</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">taiga</td></tr>
<tr><td style="padding:4px 6px;">Fraxure</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Frillish</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Froakie</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">bamboo, freshwater, jungle</td></tr>
<tr><td style="padding:4px 6px;">Frogadier</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">bamboo, freshwater, jungle</td></tr>
<tr><td style="padding:4px 6px;">Froslass</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Froslass</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">glacial, nether-frozen, snowy, tundra</td></tr>
<tr><td style="padding:4px 6px;">Fuecoco</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, nether-wasteland</td></tr>
<tr><td style="padding:4px 6px;">Furfrou</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Furret</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, taiga, temperate, tundra</td></tr>
<tr><td style="padding:4px 6px;">Gabite</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, badlands, mountain, overworld, thermal</td></tr>
<tr><td style="padding:4px 6px;">Gabite</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Gallade</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Gallade</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, magical, overworld, snowy_forest</td></tr>
<tr><td style="padding:4px 6px;">Galvantula</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, jungle, overworld, savanna</td></tr>
<tr><td style="padding:4px 6px;">Garbodor</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Garchomp</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, badlands, mountain, overworld, thermal</td></tr>
<tr><td style="padding:4px 6px;">Garchomp</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Gardevoir</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Gardevoir</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, magical, overworld, snowy_forest</td></tr>
<tr><td style="padding:4px 6px;">Garganacl</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, nether-quartz</td></tr>
<tr><td style="padding:4px 6px;">Gastly</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld, spooky, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Gastly</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Gastrodon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean, lukewarm_ocean, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Gastrodon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">beach</td></tr>
<tr><td style="padding:4px 6px;">Gengar</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld, spooky, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Gengar</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Geodude</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Gible</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, badlands, mountain, overworld, thermal</td></tr>
<tr><td style="padding:4px 6px;">Gible</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Gigalith</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Gigalith</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Gimmighoul</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Gimmighoul</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether</td></tr>
<tr><td style="padding:4px 6px;">Gimmighoul</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Girafarig</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Giratina</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Glaceon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Glaceon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Glalie</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">glacial, nether-frozen, snowy, tundra</td></tr>
<tr><td style="padding:4px 6px;">Glameow</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freezing, overworld</td></tr>
<tr><td style="padding:4px 6px;">Glastrier</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Gligar</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-mountain</td></tr>
<tr><td style="padding:4px 6px;">Gligar</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands</td></tr>
<tr><td style="padding:4px 6px;">Glimmet</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">biomesoplenty:crystalline_chasm, nether-quartz, overworld, the_bumblezone:crystal_canyon</td></tr>
<tr><td style="padding:4px 6px;">Glimmora</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">biomesoplenty:crystalline_chasm, nether-quartz, overworld, the_bumblezone:crystal_canyon</td></tr>
<tr><td style="padding:4px 6px;">Gliscor</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-mountain</td></tr>
<tr><td style="padding:4px 6px;">Gliscor</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands</td></tr>
<tr><td style="padding:4px 6px;">Gloom</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, savanna, temperate, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Gogoat</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Golbat</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, forest, overworld, spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Golbat</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark, overworld</td></tr>
<tr><td style="padding:4px 6px;">Goldeen</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, bamboo, floral, freezing, freshwater, jungle, magical, mountain, river, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Golduck</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, freshwater, grassland, magical, swamp, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Golem</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Golett</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert, jungle, overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Golett</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Golett</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Golisopod</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Golisopod</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Golurk</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert, jungle, overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Golurk</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Golurk</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Goodra</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">dripstone, lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Goodra</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">dripstone, lush, overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Goodra</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Goomy</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">dripstone, lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Goomy</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">dripstone, lush, overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Goomy</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Gorebyss</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Gorebyss</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">ocean, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Gossifleur</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Gothita</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Gothita</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Gothitelle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Gothitelle</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Gothorita</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Gothorita</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Gougingfire</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Gourgeist</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Gourgeist</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">nether-overgrowth</td></tr>
<tr><td style="padding:4px 6px;">Grafaiai</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Granbull</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Grapploct</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Grapploct</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, ocean, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Graveler</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Greattusk</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">arid</td></tr>
<tr><td style="padding:4px 6px;">Greedent</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Greninja</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">bamboo, freshwater, jungle</td></tr>
<tr><td style="padding:4px 6px;">Grimer</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-toxic, overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Grimer</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Grimmsnarl</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">magical, nether-forest, nether-warped</td></tr>
<tr><td style="padding:4px 6px;">Grookey</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Grotle</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Groudon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, nether-wasteland, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Grovyle</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, jungle</td></tr>
<tr><td style="padding:4px 6px;">Growlithe</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Grumpig</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, nether-fungus</td></tr>
<tr><td style="padding:4px 6px;">Gumshoos</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Gurdurr</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Guzzlord</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Gyarados</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, freshwater, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Gyarados</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freshwater, ocean</td></tr>
<tr><td style="padding:4px 6px;">Hakamoo</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Hakamoo</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Happiny</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Hariyama</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Hatenna</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Hatenna</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, magical</td></tr>
<tr><td style="padding:4px 6px;">Hatterene</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Hatterene</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, magical</td></tr>
<tr><td style="padding:4px 6px;">Hattrem</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Hattrem</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, magical</td></tr>
<tr><td style="padding:4px 6px;">Haunter</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld, spooky, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Haunter</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Hawlucha</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Hawlucha</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Haxorus</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Heatmor</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-mountain</td></tr>
<tr><td style="padding:4px 6px;">Heatmor</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, hills</td></tr>
<tr><td style="padding:4px 6px;">Heatran</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, nether-wasteland, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Heracross</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, aether:skyroot_forest, aether:skyroot_woodland, jungle, overworld, the_bumblezone:crystal_canyon, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Heracross</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Herdier</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Hippopotas</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Hippowdon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Hitmonchan</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">hills</td></tr>
<tr><td style="padding:4px 6px;">Hitmonlee</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">hills</td></tr>
<tr><td style="padding:4px 6px;">Hitmontop</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">hills</td></tr>
<tr><td style="padding:4px 6px;">Honchkrow</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">sky, spooky, swamp, taiga</td></tr>
<tr><td style="padding:4px 6px;">Honedge</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Hooh</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">cobblemon:is_floral, plains</td></tr>
<tr><td style="padding:4px 6px;">Hoopa</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Hoothoot</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, sky, spooky</td></tr>
<tr><td style="padding:4px 6px;">Hoppip</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, plains, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Horsea</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">lukewarm_ocean, ocean, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Houndoom</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Houndoom</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, overworld</td></tr>
<tr><td style="padding:4px 6px;">Houndour</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Houndour</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, overworld</td></tr>
<tr><td style="padding:4px 6px;">Huntail</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Huntail</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">ocean, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Hydreigon</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Hydreigon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Hypno</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Igglybuff</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, plains</td></tr>
<tr><td style="padding:4px 6px;">Illumise</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freshwater, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Illumise</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#the_bumblezone:the_bumblezone, freshwater</td></tr>
<tr><td style="padding:4px 6px;">Impidimp</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">magical, nether-forest, nether-warped</td></tr>
<tr><td style="padding:4px 6px;">Incineroar</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Infernape</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain, nether-forest</td></tr>
<tr><td style="padding:4px 6px;">Inkay</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Inkay</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, end, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Inteleon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Ironbundle</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Ironcrown</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Ironleaves</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Ironmoth</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Ironthorns</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Irontreads</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">arid, mountain</td></tr>
<tr><td style="padding:4px 6px;">Ironvaliant</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">dripstone, plains</td></tr>
<tr><td style="padding:4px 6px;">Ivysaur</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Jangmoo</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Jangmoo</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Jellicent</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Jigglypuff</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, plains</td></tr>
<tr><td style="padding:4px 6px;">Jirachi</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">end, mountain</td></tr>
<tr><td style="padding:4px 6px;">Jolteon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Jolteon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">grassland</td></tr>
<tr><td style="padding:4px 6px;">Joltik</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, jungle, overworld, savanna</td></tr>
<tr><td style="padding:4px 6px;">Jumpluff</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, plains, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Jynx</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Kabuto</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Kabutops</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Kadabra</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Kadabra</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">magical</td></tr>
<tr><td style="padding:4px 6px;">Kadabra</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">hills, temperate</td></tr>
<tr><td style="padding:4px 6px;">Kakuna</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#the_bumblezone:the_bumblezone, aether:skyroot_forest, aether:skyroot_woodland, forest, jungle</td></tr>
<tr><td style="padding:4px 6px;">Kangaskhan</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Karrablast</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Kartana</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Kartana</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">bamboo</td></tr>
<tr><td style="padding:4px 6px;">Kecleon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Keldeo</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Kilowattrel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, island, ocean, sky</td></tr>
<tr><td style="padding:4px 6px;">Kilowattrel</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, island, ocean, sky</td></tr>
<tr><td style="padding:4px 6px;">Kingdra</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">lukewarm_ocean, ocean, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Kingler</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Kirlia</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Kirlia</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, magical, overworld, snowy_forest</td></tr>
<tr><td style="padding:4px 6px;">Klang</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Klawf</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands</td></tr>
<tr><td style="padding:4px 6px;">Kleavor</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, hills</td></tr>
<tr><td style="padding:4px 6px;">Klefki</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Klefki</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Klink</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Klinklang</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Koffing</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">magical, nether-toxic, overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Komala</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, savanna</td></tr>
<tr><td style="padding:4px 6px;">Kommoo</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Kommoo</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Koraidon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">end, overworld</td></tr>
<tr><td style="padding:4px 6px;">Krabby</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Kricketot</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, swamp</td></tr>
<tr><td style="padding:4px 6px;">Kricketune</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, swamp</td></tr>
<tr><td style="padding:4px 6px;">Krokorok</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Krookodile</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Kubfu</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">bamboo</td></tr>
<tr><td style="padding:4px 6px;">Kyogre</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Kyurem</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">glacial</td></tr>
<tr><td style="padding:4px 6px;">Lairon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Lampent</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-soul_fire, overworld</td></tr>
<tr><td style="padding:4px 6px;">Landorus</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Lanturn</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">deep_ocean, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Lapras</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Lapras</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Lapras</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Larvesta</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, desert, jungle, nether-crimson, nether-forest</td></tr>
<tr><td style="padding:4px 6px;">Larvitar</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Larvitar</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Latias</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Latios</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Leafeon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Leafeon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, jungle</td></tr>
<tr><td style="padding:4px 6px;">Leavanny</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Lechonk</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mushroom, nether-crimson, nether-fungus, nether-overgrowth, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Ledian</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, temperate, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Ledyba</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, temperate, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Lickilicky</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">grassland</td></tr>
<tr><td style="padding:4px 6px;">Lickitung</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">grassland</td></tr>
<tr><td style="padding:4px 6px;">Liepard</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, overworld, savanna, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Lileep</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Lilligant</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, forest, hills, the_bumblezone:floral_meadow, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Lillipup</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Linoone</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Litleo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-overgrowth, nether-wasteland, savanna</td></tr>
<tr><td style="padding:4px 6px;">Litten</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Litwick</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-soul_fire, overworld</td></tr>
<tr><td style="padding:4px 6px;">Lombre</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Lopunny</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, hills, snowy_forest, snowy_taiga, taiga</td></tr>
<tr><td style="padding:4px 6px;">Lotad</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Loudred</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Loudred</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Lucario</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, mountain</td></tr>
<tr><td style="padding:4px 6px;">Ludicolo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Lugia</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Lumineon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">deep_ocean, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Lunatone</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">dripstone, overworld</td></tr>
<tr><td style="padding:4px 6px;">Lunatone</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">dripstone, mountain</td></tr>
<tr><td style="padding:4px 6px;">Lurantis</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">floral, jungle, the_bumblezone:floral_meadow, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Luvdisc</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Luxio</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">desert, savanna</td></tr>
<tr><td style="padding:4px 6px;">Luxray</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">desert, savanna</td></tr>
<tr><td style="padding:4px 6px;">Mabosstiff</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Machamp</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills</td></tr>
<tr><td style="padding:4px 6px;">Machoke</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills</td></tr>
<tr><td style="padding:4px 6px;">Machop</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills</td></tr>
<tr><td style="padding:4px 6px;">Magby</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, nether-basalt, nether-wasteland, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Magcargo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-basalt, nether-wasteland, overworld, vanilla-nether, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Magearna</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">floral</td></tr>
<tr><td style="padding:4px 6px;">Magikarp</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, freshwater, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Magikarp</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, freshwater, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Magmar</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, nether-basalt, nether-wasteland, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Magmortar</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, nether-basalt, nether-wasteland, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Magnemite</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Magnemite</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Magneton</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Magneton</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Magnezone</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Magnezone</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Makuhita</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Malamar</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Malamar</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, end, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Mamoswine</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">tundra</td></tr>
<tr><td style="padding:4px 6px;">Manaphy</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Manectric</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">grassland, hills</td></tr>
<tr><td style="padding:4px 6px;">Mankey</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, jungle</td></tr>
<tr><td style="padding:4px 6px;">Mantine</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Mantine</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">lukewarm_ocean, ocean, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Mantyke</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Mantyke</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">lukewarm_ocean, ocean, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Maractus</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert</td></tr>
<tr><td style="padding:4px 6px;">Mareanie</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Mareanie</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Mareep</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">highlands, plains</td></tr>
<tr><td style="padding:4px 6px;">Marill</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">floral, hills, magical, river, snowy_forest, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Marill</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">river</td></tr>
<tr><td style="padding:4px 6px;">Marowak</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert, nether-desert, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Marshadow</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Marshtomp</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, forest, jungle, lush, mushroom, overworld, savanna, swamp</td></tr>
<tr><td style="padding:4px 6px;">Maschiff</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Masquerain</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#the_bumblezone:the_bumblezone, freshwater</td></tr>
<tr><td style="padding:4px 6px;">Maushold</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld, temperate</td></tr>
<tr><td style="padding:4px 6px;">Mawile</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Mawile</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Medicham</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, peak</td></tr>
<tr><td style="padding:4px 6px;">Meditite</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, peak</td></tr>
<tr><td style="padding:4px 6px;">Meganium</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">#aether:is_aether, floral, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Meltan</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Meowscarada</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">floral, magical</td></tr>
<tr><td style="padding:4px 6px;">Meowstic</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freezing, overworld</td></tr>
<tr><td style="padding:4px 6px;">Meowth</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freezing, overworld, taiga</td></tr>
<tr><td style="padding:4px 6px;">Meowth</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Mesprit</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">coast, island, river, sky</td></tr>
<tr><td style="padding:4px 6px;">Metagross</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether</td></tr>
<tr><td style="padding:4px 6px;">Metagross</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, end, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Metagross</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">dripstone, peak</td></tr>
<tr><td style="padding:4px 6px;">Metang</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether</td></tr>
<tr><td style="padding:4px 6px;">Metang</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, end, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Metang</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">dripstone, peak</td></tr>
<tr><td style="padding:4px 6px;">Metapod</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, jungle, plains, the_bumblezone:floral_meadow, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Mew</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">floral, jungle</td></tr>
<tr><td style="padding:4px 6px;">Mienfoo</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Mienshao</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Mightyena</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, overworld, savanna</td></tr>
<tr><td style="padding:4px 6px;">Milcery</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Milcery</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Milotic</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Miltank</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">grassland, mushroom_fields</td></tr>
<tr><td style="padding:4px 6px;">Mimejr</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Mimejr</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freezing, overworld</td></tr>
<tr><td style="padding:4px 6px;">Mimikyu</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Mimikyu</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">spooky</td></tr>
<tr><td style="padding:4px 6px;">Minccino</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">magical, overworld</td></tr>
<tr><td style="padding:4px 6px;">Minun</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Miraidon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">end, overworld</td></tr>
<tr><td style="padding:4px 6px;">Misdreavus</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-warped, overworld, spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Mismagius</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-warped, overworld, spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Moltres</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">beach, overworld</td></tr>
<tr><td style="padding:4px 6px;">Monferno</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain, nether-forest</td></tr>
<tr><td style="padding:4px 6px;">Morelull</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, jungle, lush, magical, mushroom, nether-fungus, overworld</td></tr>
<tr><td style="padding:4px 6px;">Morgrem</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">magical, nether-forest, nether-warped</td></tr>
<tr><td style="padding:4px 6px;">Morpeko</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Mrmime</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Mrmime</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freezing, overworld</td></tr>
<tr><td style="padding:4px 6px;">Mrrime</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Mudbray</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, grassland</td></tr>
<tr><td style="padding:4px 6px;">Mudkip</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, forest, jungle, lush, mushroom, overworld, savanna, swamp</td></tr>
<tr><td style="padding:4px 6px;">Mudsdale</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, grassland</td></tr>
<tr><td style="padding:4px 6px;">Muk</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-toxic, overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Muk</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Munchlax</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Munchlax</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, hills, snowy_forest</td></tr>
<tr><td style="padding:4px 6px;">Munkidori</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">beach, river</td></tr>
<tr><td style="padding:4px 6px;">Munna</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Murkrow</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">sky, spooky, swamp, taiga</td></tr>
<tr><td style="padding:4px 6px;">Musharna</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Nacli</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, nether-quartz</td></tr>
<tr><td style="padding:4px 6px;">Naclstack</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, nether-quartz</td></tr>
<tr><td style="padding:4px 6px;">Natu</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, overworld, plateau</td></tr>
<tr><td style="padding:4px 6px;">Necrozma</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Nickit</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Nickit</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Nidoking</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Nidoqueen</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Nidoranf</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Nidoranm</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Nidorina</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Nidorino</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Nihilego</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Nihilego</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Nincada</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, jungle</td></tr>
<tr><td style="padding:4px 6px;">Ninetales</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-forest, nether-frozen</td></tr>
<tr><td style="padding:4px 6px;">Ninetales</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, snowy_forest, snowy_taiga, taiga</td></tr>
<tr><td style="padding:4px 6px;">Ninjask</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, jungle</td></tr>
<tr><td style="padding:4px 6px;">Noctowl</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, sky, spooky</td></tr>
<tr><td style="padding:4px 6px;">Noibat</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Noibat</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Noivern</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Noivern</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Nosepass</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Numel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, nether-overgrowth, nether-wasteland, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Nuzleaf</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest</td></tr>
<tr><td style="padding:4px 6px;">Obstagoon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">taiga</td></tr>
<tr><td style="padding:4px 6px;">Octillery</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Oddish</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, savanna, temperate, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Ogerpon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Oinkologne</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mushroom, nether-crimson, nether-fungus, nether-overgrowth, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Okidogi</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Omanyte</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Omastar</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Onix</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">nether-basalt, overworld</td></tr>
<tr><td style="padding:4px 6px;">Orthworm</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert, overworld</td></tr>
<tr><td style="padding:4px 6px;">Orthworm</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, desert, nether-desert, overworld</td></tr>
<tr><td style="padding:4px 6px;">Oshawott</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">beach, coast, cold_ocean, ocean, snowy_beach, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Overqwil</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">cold, cold_ocean, frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Overqwil</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">cold, cold_ocean</td></tr>
<tr><td style="padding:4px 6px;">Pachirisu</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest</td></tr>
<tr><td style="padding:4px 6px;">Palafin</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Palafin</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Palkia</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jagged_peaks, peak</td></tr>
<tr><td style="padding:4px 6px;">Palossand</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach</td></tr>
<tr><td style="padding:4px 6px;">Panpour</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">jungle, river, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Pansage</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Pansear</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">jungle, tropical_island, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Paras</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mushroom, nether-fungus</td></tr>
<tr><td style="padding:4px 6px;">Paras</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Parasect</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mushroom, nether-fungus</td></tr>
<tr><td style="padding:4px 6px;">Parasect</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Patrat</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">grassland</td></tr>
<tr><td style="padding:4px 6px;">Pecharunt</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Pelipper</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, sky, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Perrserker</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freezing, taiga</td></tr>
<tr><td style="padding:4px 6px;">Persian</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freezing, overworld</td></tr>
<tr><td style="padding:4px 6px;">Persian</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Petilil</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, forest, hills, the_bumblezone:floral_meadow, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Phanpy</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, savanna</td></tr>
<tr><td style="padding:4px 6px;">Phantump</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, spooky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Pheromosa</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Pheromosa</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Phione</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Pichu</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, beach, forest, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Pidgeot</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, sky, temperate</td></tr>
<tr><td style="padding:4px 6px;">Pidgeotto</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, sky, temperate</td></tr>
<tr><td style="padding:4px 6px;">Pidgey</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, sky, temperate</td></tr>
<tr><td style="padding:4px 6px;">Pidove</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, overworld, sky</td></tr>
<tr><td style="padding:4px 6px;">Pignite</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">nether-crimson, nether-overgrowth, plains</td></tr>
<tr><td style="padding:4px 6px;">Pikachu</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, beach, forest, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Pikipek</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, sky, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Piloswine</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">tundra</td></tr>
<tr><td style="padding:4px 6px;">Pincurchin</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Pincurchin</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Pineco</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">taiga</td></tr>
<tr><td style="padding:4px 6px;">Pinsir</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Pinsir</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Piplup</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean, frozen_river, glacial, tundra</td></tr>
<tr><td style="padding:4px 6px;">Plusle</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Poipole</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Poipole</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Politoed</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freshwater, hills, jungle, lush, magical, mushroom, overworld, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Poliwag</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freshwater, hills, jungle, lush, magical, mushroom, overworld, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Poliwhirl</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freshwater, hills, jungle, lush, magical, mushroom, overworld, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Poliwrath</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freshwater, hills, jungle, lush, magical, mushroom, overworld, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Poltchageist</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">arid, bamboo, cherry_blossom, forest, island, jungle, ocean, swamp, thermal, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Polteageist</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Polteageist</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">floral, magical, mountain, plains, snowy, spooky, taiga, tundra</td></tr>
<tr><td style="padding:4px 6px;">Ponyta</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, nether-overgrowth, nether-wasteland</td></tr>
<tr><td style="padding:4px 6px;">Ponyta</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">floral, grassland, magical</td></tr>
<tr><td style="padding:4px 6px;">Poochyena</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, overworld, savanna</td></tr>
<tr><td style="padding:4px 6px;">Popplio</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">coast, ocean, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Porygon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Porygon2</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Porygonz</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Primarina</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">coast, ocean, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Primeape</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, jungle</td></tr>
<tr><td style="padding:4px 6px;">Prinplup</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean, frozen_river, glacial, tundra</td></tr>
<tr><td style="padding:4px 6px;">Probopass</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Psyduck</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, freshwater, grassland, magical, swamp, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Pumpkaboo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Pumpkaboo</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">nether-overgrowth</td></tr>
<tr><td style="padding:4px 6px;">Pupitar</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Pupitar</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Purrloin</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, overworld, savanna, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Purugly</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freezing, overworld</td></tr>
<tr><td style="padding:4px 6px;">Pyroar</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-overgrowth, nether-wasteland, savanna</td></tr>
<tr><td style="padding:4px 6px;">Pyukumuku</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach, ocean, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Pyukumuku</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">beach</td></tr>
<tr><td style="padding:4px 6px;">Quagsire</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, lush, mushroom, overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Quagsire</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Quaquaval</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest, freshwater, grassland, jungle, river</td></tr>
<tr><td style="padding:4px 6px;">Quaxly</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest, freshwater, grassland, jungle, river</td></tr>
<tr><td style="padding:4px 6px;">Quaxwell</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest, freshwater, grassland, jungle, river</td></tr>
<tr><td style="padding:4px 6px;">Quilava</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">nether-crimson, nether-forest, nether-overgrowth, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Quilladin</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Qwilfish</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">cold, cold_ocean, frozen_ocean, lukewarm_ocean, ocean, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Qwilfish</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">cold, cold_ocean, lukewarm_ocean, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Raboot</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, grassland, tundra</td></tr>
<tr><td style="padding:4px 6px;">Rabsca</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid</td></tr>
<tr><td style="padding:4px 6px;">Ragingbolt</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">coast</td></tr>
<tr><td style="padding:4px 6px;">Raichu</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, beach, forest, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Raikou</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Ralts</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Ralts</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, magical, overworld, snowy_forest</td></tr>
<tr><td style="padding:4px 6px;">Rampardos</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Rapidash</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, nether-overgrowth, nether-wasteland</td></tr>
<tr><td style="padding:4px 6px;">Rapidash</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">floral, grassland, magical</td></tr>
<tr><td style="padding:4px 6px;">Raticate</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">grassland, overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Raticate</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Rattata</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">grassland, overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Rattata</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Rayquaza</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Regice</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Regidrago</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Regieleki</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Regigigas</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">sandy</td></tr>
<tr><td style="padding:4px 6px;">Regirock</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Registeel</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Relicanth</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">deep_ocean, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Relicanth</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Relicanth</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Rellor</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid</td></tr>
<tr><td style="padding:4px 6px;">Remoraid</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Reshiram</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">nether-basalt</td></tr>
<tr><td style="padding:4px 6px;">Reuniclus</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">end, overworld</td></tr>
<tr><td style="padding:4px 6px;">Revavroom</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, overworld</td></tr>
<tr><td style="padding:4px 6px;">Rhydon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, savanna</td></tr>
<tr><td style="padding:4px 6px;">Rhyhorn</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, savanna</td></tr>
<tr><td style="padding:4px 6px;">Rhyperior</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, savanna</td></tr>
<tr><td style="padding:4px 6px;">Ribombee</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, overworld, the_bumblezone:floral_meadow, the_bumblezone:pollinated_fields</td></tr>
<tr><td style="padding:4px 6px;">Rillaboom</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Riolu</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, mountain</td></tr>
<tr><td style="padding:4px 6px;">Roaringmoon</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">dripstone, mountain</td></tr>
<tr><td style="padding:4px 6px;">Roggenrola</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Roggenrola</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Rookidee</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, sky, spooky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Roselia</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Roserade</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Rowlet</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, snowy_forest, snowy_taiga, taiga</td></tr>
<tr><td style="padding:4px 6px;">Rufflet</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">sky</td></tr>
<tr><td style="padding:4px 6px;">Rufflet</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, hills, mountain</td></tr>
<tr><td style="padding:4px 6px;">Sableye</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, biomesoplenty:crystalline_chasm, overworld, the_bumblezone:crystal_canyon</td></tr>
<tr><td style="padding:4px 6px;">Sableye</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Salamence</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, dripstone</td></tr>
<tr><td style="padding:4px 6px;">Salamence</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">dripstone, peak, sky</td></tr>
<tr><td style="padding:4px 6px;">Salandit</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, mountain, nether-basalt, nether-desert, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Salazzle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, mountain, nether-basalt, nether-desert, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Samurott</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">beach, coast, cold_ocean, ocean, snowy_beach, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Sandaconda</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Sandile</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Sandshrew</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid</td></tr>
<tr><td style="padding:4px 6px;">Sandslash</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid</td></tr>
<tr><td style="padding:4px 6px;">Sandygast</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach</td></tr>
<tr><td style="padding:4px 6px;">Sandyshocks</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Sawk</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Sawsbuck</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, forest, hills, plains, snowy_forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Sawsbuck</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Scatterbug</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, plains, savanna, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Sceptile</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, jungle</td></tr>
<tr><td style="padding:4px 6px;">Scizor</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Scolipede</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, jungle, lush, nether-forest, nether-fungus, overworld</td></tr>
<tr><td style="padding:4px 6px;">Scorbunny</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, grassland, tundra</td></tr>
<tr><td style="padding:4px 6px;">Scovillain</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, nether-overgrowth</td></tr>
<tr><td style="padding:4px 6px;">Scrafty</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, badlands</td></tr>
<tr><td style="padding:4px 6px;">Scraggy</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, badlands</td></tr>
<tr><td style="padding:4px 6px;">Screamtail</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">floral</td></tr>
<tr><td style="padding:4px 6px;">Scyther</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Scyther</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, hills</td></tr>
<tr><td style="padding:4px 6px;">Seadra</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">lukewarm_ocean, ocean, overworld, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Seaking</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, bamboo, floral, freezing, freshwater, jungle, magical, mountain, river, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Sealeo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Seedot</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest</td></tr>
<tr><td style="padding:4px 6px;">Seel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Sentret</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, taiga, temperate, tundra</td></tr>
<tr><td style="padding:4px 6px;">Serperior</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Servine</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Sewaddle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Sharpedo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Shaymin</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">floral</td></tr>
<tr><td style="padding:4px 6px;">Shedinja</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland</td></tr>
<tr><td style="padding:4px 6px;">Shedinja</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, jungle</td></tr>
<tr><td style="padding:4px 6px;">Shelgon</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, dripstone</td></tr>
<tr><td style="padding:4px 6px;">Shelgon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">dripstone</td></tr>
<tr><td style="padding:4px 6px;">Shellder</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, cold_ocean, frozen_ocean, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Shellos</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean, lukewarm_ocean, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Shellos</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">beach</td></tr>
<tr><td style="padding:4px 6px;">Shelmet</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">swamp</td></tr>
<tr><td style="padding:4px 6px;">Shieldon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Shiftry</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest</td></tr>
<tr><td style="padding:4px 6px;">Shiinotic</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, jungle, lush, magical, mushroom, nether-fungus, overworld</td></tr>
<tr><td style="padding:4px 6px;">Shinx</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">desert, savanna</td></tr>
<tr><td style="padding:4px 6px;">Shroodle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Shroomish</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, mushroom</td></tr>
<tr><td style="padding:4px 6px;">Shuckle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Shuckle</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Shuppet</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Shuppet</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Sigilyph</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Sigilyph</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, badlands, desert, end</td></tr>
<tr><td style="padding:4px 6px;">Silicobra</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Simipour</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">jungle, river, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Simisage</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Simisear</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">jungle, tropical_island, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Sinistcha</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">arid, bamboo, cherry_blossom, forest, island, jungle, ocean, swamp, thermal, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Sinistea</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Sinistea</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">floral, magical, mountain, plains, snowy, spooky, taiga, tundra</td></tr>
<tr><td style="padding:4px 6px;">Sirfetchd</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Sizzlipede</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, nether-basalt, nether-wasteland, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Skarmory</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">badlands, desert, sky</td></tr>
<tr><td style="padding:4px 6px;">Skeledirge</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, nether-wasteland</td></tr>
<tr><td style="padding:4px 6px;">Skiddo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Skiploom</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, plains, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Skorupi</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, desert</td></tr>
<tr><td style="padding:4px 6px;">Skrelp</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Skwovet</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Slaking</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Slakoth</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Sliggoo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">dripstone, lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Sliggoo</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">dripstone, lush, overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Sliggoo</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Slitherwing</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Slowbro</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach, mushroom_fields, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Slowking</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach, mushroom_fields, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Slowpoke</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach, freshwater, mushroom_fields, river, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Slugma</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-basalt, nether-wasteland, overworld, vanilla-nether, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Slurpuff</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Slurpuff</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Smeargle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Smeargle</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Smoliv</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Smoochum</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Sneasel</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">mountain, taiga</td></tr>
<tr><td style="padding:4px 6px;">Sneasler</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Snivy</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Snorlax</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Snorlax</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, hills, snowy_forest</td></tr>
<tr><td style="padding:4px 6px;">Snorunt</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Snorunt</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">glacial, nether-frozen, snowy, tundra</td></tr>
<tr><td style="padding:4px 6px;">Snubbull</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Sobble</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Solosis</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">end, overworld</td></tr>
<tr><td style="padding:4px 6px;">Solrock</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">dripstone, overworld</td></tr>
<tr><td style="padding:4px 6px;">Solrock</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, dripstone, mountain</td></tr>
<tr><td style="padding:4px 6px;">Spearow</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, savanna, sky</td></tr>
<tr><td style="padding:4px 6px;">Spectrier</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">spooky</td></tr>
<tr><td style="padding:4px 6px;">Spewpa</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, floral, plains, savanna, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Spheal</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Spidops</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Spinarak</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, jungle, overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Spinda</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">bamboo, hills</td></tr>
<tr><td style="padding:4px 6px;">Spiritomb</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark, overworld</td></tr>
<tr><td style="padding:4px 6px;">Spiritomb</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">desert, jungle, overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Spoink</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, nether-fungus</td></tr>
<tr><td style="padding:4px 6px;">Sprigatito</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">floral, magical</td></tr>
<tr><td style="padding:4px 6px;">Spritzee</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Spritzee</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, floral</td></tr>
<tr><td style="padding:4px 6px;">Squawkabilly</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, overworld, savanna, sky, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Squirtle</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">freshwater, hills, jungle, temperate, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Stakataka</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Stakataka</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Stantler</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, snowy_forest, snowy_taiga, tundra</td></tr>
<tr><td style="padding:4px 6px;">Staraptor</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, sky, snowy_forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Staravia</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, sky, snowy_forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Starly</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">hills, sky, snowy_forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Starmie</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Starmie</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, ocean, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Staryu</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Staryu</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, ocean, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Steelix</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">nether-basalt, overworld</td></tr>
<tr><td style="padding:4px 6px;">Steenee</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Stonjourner</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Stonjourner</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Stonjourner</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Stoutland</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Stufful</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">bamboo</td></tr>
<tr><td style="padding:4px 6px;">Stunfisk</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Sudowoodo</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Suicune</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Sunflora</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, overworld, sunflower_plains, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Sunkern</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, overworld, sunflower_plains, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Surskit</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#the_bumblezone:the_bumblezone, freshwater</td></tr>
<tr><td style="padding:4px 6px;">Swablu</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow</td></tr>
<tr><td style="padding:4px 6px;">Swablu</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld, peak, sky</td></tr>
<tr><td style="padding:4px 6px;">Swadloon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Swampert</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">badlands, forest, jungle, lush, mushroom, overworld, savanna, swamp</td></tr>
<tr><td style="padding:4px 6px;">Swanna</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, freshwater, sky</td></tr>
<tr><td style="padding:4px 6px;">Swanna</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, freshwater, sky</td></tr>
<tr><td style="padding:4px 6px;">Swellow</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, sky, temperate</td></tr>
<tr><td style="padding:4px 6px;">Swinub</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">tundra</td></tr>
<tr><td style="padding:4px 6px;">Swirlix</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Swirlix</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, overworld</td></tr>
<tr><td style="padding:4px 6px;">Swoobat</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, savanna</td></tr>
<tr><td style="padding:4px 6px;">Sylveon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Sylveon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, magical</td></tr>
<tr><td style="padding:4px 6px;">Tadbulb</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freshwater</td></tr>
<tr><td style="padding:4px 6px;">Taillow</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, sky, temperate</td></tr>
<tr><td style="padding:4px 6px;">Talonflame</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, forest, nether-forest, nether-fungus, sky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Tandemaus</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, overworld, temperate</td></tr>
<tr><td style="padding:4px 6px;">Tangela</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Tangrowth</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Tapubulu</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">beach, forest, island</td></tr>
<tr><td style="padding:4px 6px;">Tapufini</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">beach, island, ocean</td></tr>
<tr><td style="padding:4px 6px;">Tapukoko</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">beach, island, jungle</td></tr>
<tr><td style="padding:4px 6px;">Tapulele</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">beach, floral, island</td></tr>
<tr><td style="padding:4px 6px;">Tarountula</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Tatsugiri</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">bamboo, forest, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Tauros</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">grassland, highlands</td></tr>
<tr><td style="padding:4px 6px;">Teddiursa</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, aether:skyroot_forest, aether:skyroot_woodland, forest, mountain, overworld, taiga, the_bumblezone:crystal_canyon, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Tentacool</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Tentacool</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Tentacruel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Tentacruel</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Tepig</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">nether-crimson, nether-overgrowth, plains</td></tr>
<tr><td style="padding:4px 6px;">Terapagos</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Terrakion</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Thievul</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, taiga</td></tr>
<tr><td style="padding:4px 6px;">Thievul</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Throh</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Thundurus</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Thwackey</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Timburr</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Tinkatink</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Tinkatink</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether</td></tr>
<tr><td style="padding:4px 6px;">Tinkaton</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Tinkaton</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether</td></tr>
<tr><td style="padding:4px 6px;">Tinkatuff</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Tinkatuff</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether</td></tr>
<tr><td style="padding:4px 6px;">Tirtouga</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Toedscool</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mushroom, nether-fungus</td></tr>
<tr><td style="padding:4px 6px;">Toedscruel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mushroom, nether-fungus</td></tr>
<tr><td style="padding:4px 6px;">Togedemaru</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">hills</td></tr>
<tr><td style="padding:4px 6px;">Togekiss</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, magical, sky, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Togepi</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, magical, sky, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Togetic</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, magical, sky, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Torchic</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, plains</td></tr>
<tr><td style="padding:4px 6px;">Torkoal</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, nether-basalt, thermal</td></tr>
<tr><td style="padding:4px 6px;">Torkoal</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Tornadus</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Torracat</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Torterra</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Totodile</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">arid, jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Toucannon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, sky, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Toxapex</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Toxapex</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Toxel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Toxel</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Toxicroak</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, swamp</td></tr>
<tr><td style="padding:4px 6px;">Toxtricity</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Toxtricity</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Tranquill</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, overworld, sky</td></tr>
<tr><td style="padding:4px 6px;">Trapinch</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Trapinch</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Treecko</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, jungle</td></tr>
<tr><td style="padding:4px 6px;">Trevenant</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, spooky, taiga</td></tr>
<tr><td style="padding:4px 6px;">Tropius</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, jungle</td></tr>
<tr><td style="padding:4px 6px;">Trubbish</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Trumbeak</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, sky, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Tsareena</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Turtonator</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">nether-basalt</td></tr>
<tr><td style="padding:4px 6px;">Turtwig</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest</td></tr>
<tr><td style="padding:4px 6px;">Tynamo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Tynamo</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Typhlosion</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">nether-crimson, nether-forest, nether-overgrowth, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Tyranitar</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Tyranitar</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Tyrantrum</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Tyrogue</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">hills</td></tr>
<tr><td style="padding:4px 6px;">Tyrunt</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">lush</td></tr>
<tr><td style="padding:4px 6px;">Umbreon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Umbreon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">spooky</td></tr>
<tr><td style="padding:4px 6px;">Unfezant</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">mountain, overworld, sky</td></tr>
<tr><td style="padding:4px 6px;">Unown</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">end, overworld, the_bumblezone:howling_constructs</td></tr>
<tr><td style="padding:4px 6px;">Ursaluna</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, aether:skyroot_forest, aether:skyroot_woodland, forest, mountain, overworld, taiga, the_bumblezone:crystal_canyon, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Ursaring</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, aether:skyroot_forest, aether:skyroot_woodland, forest, mountain, overworld, taiga, the_bumblezone:crystal_canyon, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Uxie</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">coast, island, river, sky</td></tr>
<tr><td style="padding:4px 6px;">Vanillish</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freezing, nether-frozen</td></tr>
<tr><td style="padding:4px 6px;">Vanillish</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Vanillite</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freezing, nether-frozen</td></tr>
<tr><td style="padding:4px 6px;">Vanillite</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Vanilluxe</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freezing, nether-frozen</td></tr>
<tr><td style="padding:4px 6px;">Vanilluxe</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">freezing</td></tr>
<tr><td style="padding:4px 6px;">Vaporeon</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Vaporeon</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">freshwater, jungle, magical, temperate, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Varoom</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">badlands, overworld</td></tr>
<tr><td style="padding:4px 6px;">Veluza</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Veluza</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">ocean, overworld</td></tr>
<tr><td style="padding:4px 6px;">Venipede</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, jungle, lush, nether-forest, nether-fungus, overworld</td></tr>
<tr><td style="padding:4px 6px;">Venomoth</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Venonat</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Venusaur</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Vespiquen</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, #the_bumblezone:the_bumblezone, overworld, temperate</td></tr>
<tr><td style="padding:4px 6px;">Vibrava</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Victini</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">island, savanna, volcanic</td></tr>
<tr><td style="padding:4px 6px;">Victreebel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Vigoroth</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Vileplume</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, savanna, temperate, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Vivillon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, badlands, cherry_blossom, coast, desert, floral, forest, freshwater, frozen_ocean, glacial, jungle, magical, mountain, mushroom, ocean, overworld, plains, savanna, sky, snowy_forest, spooky, sunflower_plains, taiga, the_bumblezone:floral_meadow, tropical_island, tundra, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Volbeat</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">freshwater, the_bumblezone:floral_meadow</td></tr>
<tr><td style="padding:4px 6px;">Volbeat</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#the_bumblezone:the_bumblezone, freshwater</td></tr>
<tr><td style="padding:4px 6px;">Volcanion</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">arid, overworld</td></tr>
<tr><td style="padding:4px 6px;">Volcarona</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, desert, jungle, nether-crimson, nether-forest</td></tr>
<tr><td style="padding:4px 6px;">Voltorb</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Voltorb</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Vulpix</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">nether-forest, nether-frozen</td></tr>
<tr><td style="padding:4px 6px;">Vulpix</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, snowy_forest, snowy_taiga, taiga</td></tr>
<tr><td style="padding:4px 6px;">Wailmer</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Wailmer</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_ocean, ocean</td></tr>
<tr><td style="padding:4px 6px;">Wailord</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">ocean</td></tr>
<tr><td style="padding:4px 6px;">Wailord</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_ocean, ocean</td></tr>
<tr><td style="padding:4px 6px;">Walkingwake</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Walrein</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">cold_ocean, frozen_ocean</td></tr>
<tr><td style="padding:4px 6px;">Wartortle</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">freshwater, hills, jungle, temperate, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Watchog</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">grassland</td></tr>
<tr><td style="padding:4px 6px;">Wattrel</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, island, ocean, sky</td></tr>
<tr><td style="padding:4px 6px;">Wattrel</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, island, ocean, sky</td></tr>
<tr><td style="padding:4px 6px;">Weavile</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">taiga</td></tr>
<tr><td style="padding:4px 6px;">Weedle</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#the_bumblezone:the_bumblezone, aether:skyroot_forest, aether:skyroot_woodland, forest, jungle</td></tr>
<tr><td style="padding:4px 6px;">Weepinbell</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Weezing</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">magical, nether-toxic, overworld, spooky</td></tr>
<tr><td style="padding:4px 6px;">Whimsicott</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, plains</td></tr>
<tr><td style="padding:4px 6px;">Whirlipede</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">forest, jungle, lush, nether-forest, nether-fungus, overworld</td></tr>
<tr><td style="padding:4px 6px;">Whiscash</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">arid, freshwater, hills, jungle, mushroom, overworld, swamp, temperate</td></tr>
<tr><td style="padding:4px 6px;">Whiscash</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Whiscash</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">arid, freshwater, hills, jungle, mushroom, overworld, temperate</td></tr>
<tr><td style="padding:4px 6px;">Whismur</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Whismur</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Wigglytuff</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, floral, plains</td></tr>
<tr><td style="padding:4px 6px;">Wiglett</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach, lukewarm_ocean, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Wimpod</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Wimpod</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">coast, overworld, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Wingull</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean, sky, tropical_island</td></tr>
<tr><td style="padding:4px 6px;">Wishiwashi</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">coast, ocean</td></tr>
<tr><td style="padding:4px 6px;">Wobbuffet</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Wochien</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Woobat</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">jungle, savanna</td></tr>
<tr><td style="padding:4px 6px;">Wooloo</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_grove, aether:skyroot_meadow, mountain, plains</td></tr>
<tr><td style="padding:4px 6px;">Wooper</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, arid, has_block-mud, lush, mushroom, overworld, swamp</td></tr>
<tr><td style="padding:4px 6px;">Wooper</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, arid, mushroom, overworld, savanna, swamp</td></tr>
<tr><td style="padding:4px 6px;">Wugtrio</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">beach, lukewarm_ocean, warm_ocean</td></tr>
<tr><td style="padding:4px 6px;">Wynaut</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Wyrdeer</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">snowy_forest, snowy_taiga, tundra</td></tr>
<tr><td style="padding:4px 6px;">Xatu</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">badlands, overworld, plateau</td></tr>
<tr><td style="padding:4px 6px;">Xerneas</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">floral</td></tr>
<tr><td style="padding:4px 6px;">Xurkitree</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">end</td></tr>
<tr><td style="padding:4px 6px;">Yamask</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Yamask</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Yamper</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Yanma</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, #the_bumblezone:the_bumblezone, freshwater, lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Yanmega</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">#aether:is_aether, #the_bumblezone:the_bumblezone, freshwater, lush, overworld</td></tr>
<tr><td style="padding:4px 6px;">Yungoos</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">plains</td></tr>
<tr><td style="padding:4px 6px;">Yveltal</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">forest, nether-soul_sand, sandy</td></tr>
<tr><td style="padding:4px 6px;">Zacian</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">spooky</td></tr>
<tr><td style="padding:4px 6px;">Zamazenta</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">spooky</td></tr>
<tr><td style="padding:4px 6px;">Zapdos</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain, overworld</td></tr>
<tr><td style="padding:4px 6px;">Zarude</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">jungle</td></tr>
<tr><td style="padding:4px 6px;">Zebstrika</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">savanna</td></tr>
<tr><td style="padding:4px 6px;">Zekrom</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">mountain</td></tr>
<tr><td style="padding:4px 6px;">Zeraora</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">desert</td></tr>
<tr><td style="padding:4px 6px;">Zigzagoon</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">aether:skyroot_forest, aether:skyroot_woodland, taiga, temperate</td></tr>
<tr><td style="padding:4px 6px;">Zoroark</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, snowy_forest, snowy_taiga, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Zorua</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">forest, snowy_forest, snowy_taiga, vanilla-nether</td></tr>
<tr><td style="padding:4px 6px;">Zubat</td><td style="padding:4px 6px;color:#888;font-weight:600;">common</td><td style="padding:4px 6px;">#aether:is_aether, forest, overworld, spooky, swamp</td></tr>
<tr><td style="padding:4px 6px;">Zubat</td><td style="padding:4px 6px;color:#4a8;font-weight:600;">uncommon</td><td style="padding:4px 6px;">deep_dark, overworld</td></tr>
<tr><td style="padding:4px 6px;">Zweilous</td><td style="padding:4px 6px;color:#48a;font-weight:600;">rare</td><td style="padding:4px 6px;">#aether:is_aether, deep_dark</td></tr>
<tr><td style="padding:4px 6px;">Zweilous</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
<tr><td style="padding:4px 6px;">Zygarde</td><td style="padding:4px 6px;color:#a48;font-weight:600;">ultra-rare</td><td style="padding:4px 6px;">overworld</td></tr>
</tbody>
</table>
</div>

<script>
(function() {
  var input = document.getElementById('spawn-filter');
  var rows = document.querySelectorAll('#spawn-table tbody tr');
  input.addEventListener('input', function() {
    var q = input.value.toLowerCase().trim();
    rows.forEach(function(r) {
      var name = r.cells[0].textContent.toLowerCase();
      r.style.display = (!q || name.indexOf(q) !== -1) ? '' : 'none';
    });
  });
})();
</script>

## Notes

Each label is a **tag** that covers many concrete biomes. For example `forest` includes Oak Forest, Birch Forest, Dark Oak Forest, Old Growth Spruce Taiga, and several modded biomes that share the same tag. If a Pokémon's row says `forest`, it spawns in any biome tagged that way.

