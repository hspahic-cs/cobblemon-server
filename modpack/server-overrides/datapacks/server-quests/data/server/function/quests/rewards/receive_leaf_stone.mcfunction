# 0.7.25 — second quest in the Exeggcute onboarding. Player just defeated gym 1 AND
# reached ¢250 income; reward them with the Leaf Stone they need to evolve the
# Exeggcute they should already have hatched from beat_wild_trainer.
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text":"\n§a§l[Quest Complete] ","bold":true},{"text":"Botany Lesson","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§lLeaf Stone","bold":false},{"text":"\n§7Use it on your hatched §aExeggcute§7 to evolve it into §aExeggutor§7.","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Evolve Exeggutor §8(Reward: §fPasture Block§8)\n","color":"white","bold":false}]
tag @s add cq_reward_item_leaf_stone
schedule function server:quests/rewards/_finalize 20t append
