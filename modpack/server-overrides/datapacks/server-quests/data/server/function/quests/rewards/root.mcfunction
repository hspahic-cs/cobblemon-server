# First-join: teleport to spawn world + give the Server Wiki book.
# NeoEssentials spawnOnJoin is unreliable, so we handle it here via the root advancement (triggers once on first tick).
#
# The book is a single page with one clickable link to the MkDocs site
# (https://hspahic-cs.github.io/cobblemon-server/). The wiki content is
# published from the docs/ folder in this repo and stays in lockstep with
# releases — easier to update than a multi-page book that gets baked into
# inventories at first-join. Existing players who already have the old
# wiki book aren't affected.
execute in multiworld:spawn run tp @s -247.5 84.0 184.5 179.25 -4.2
give @s minecraft:written_book[minecraft:written_book_content={title:"Server Wiki",author:"Server",pages:['{"text":"§l§9Server Wiki§r\\n\\nEverything you need to know about the server lives on our docs site:\\n\\n","extra":[{"text":"§9§n[Open the Wiki]§r","clickEvent":{"action":"open_url","value":"https://hspahic-cs.github.io/cobblemon-server/"}},{"text":"\\n\\n§7Or copy this link:\\nhspahic-cs.github.io/\\ncobblemon-server"}]}']}] 1
