# 0.7.29 — Pocket Change reward is now the Leaf Stone for the Exeggcute onboarding chain.
# Replaces the previous Pasture Block (moved to evolve_exeggutor in 0.7.26) and the
# interim 5 Great Balls + 3 EXP Candy S kit (also 0.7.26). The Leaf Stone naturally slots
# here because by the time the player has ¢250 they've already beaten gym 1 and hatched
# their Exeggcute from beat_wild_trainer — they're ready to evolve. The separate
# `receive_leaf_stone` quest (with the AND-gate of gym1 + income) was deleted in 0.7.29;
# this single quest replaces it.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§6§l[Mainline Quest Complete] ","bold": true},{"text": "Pocket Change","color": "white","bold": true},{"text": "\n§6§l✦ Reward: §e§lLeaf Stone","bold": false},{"text": "\n§7Use it on your hatched §aExeggcute§7 to evolve into §aExeggutor§7.","bold": false},{"text": "\n§e► Next: ","bold": false},{"text": "Evolve Exeggutor §8(Reward: §fPasture Block§8)\n","color": "white","bold": false}]
tag @s add cq_reward_item_leaf_stone
schedule function server:quests/rewards/_finalize 20t append
