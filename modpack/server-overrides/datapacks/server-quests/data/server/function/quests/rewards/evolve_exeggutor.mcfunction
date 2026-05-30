# 0.7.25 — player just evolved Exeggcute → Exeggutor. Hand them their first Pasture
# Block so they can park the Exeggutor on a carrot patch. Pasture reward was moved here
# from reach_income_250 to keep the entire Cobbleworkers introduction in one chain.
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text":"\n§a§l[Quest Complete] ","bold":true},{"text":"Sunny Side Up","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§lPasture Block","bold":false},{"text":"\n§7Place it next to a §6carrot patch§7. Park your Exeggutor inside and it'll auto-harvest in a §a5x5§7 area.","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Place the Pasture Block §8(Reward: §616 Bone Meal§8)\n","color":"white","bold":false}]
tag @s add cq_reward_item_pasture_block
schedule function server:quests/rewards/_finalize 20t append
