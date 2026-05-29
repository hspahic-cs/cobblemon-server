playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true},{"text": "Into the Wild", "color": "white", "bold": true},{"text": "\n§6§l✦ Reward: §e§l3 Raw Copper + 5 Bone Meal", "bold": false},{"text": "\n§e► Next: ", "bold": false},{"text": "Set a Home (/sethome)\n", "color": "white", "bold": false}]
tag @s add cq_reward_item_raw_copper
schedule function server:quests/rewards/_finalize 20t append
