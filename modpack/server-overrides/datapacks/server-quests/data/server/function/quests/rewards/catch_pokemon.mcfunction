# Auto-generated.
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true},{"text": "Gotta Catch One", "color": "white", "bold": true},{"text": "\n§6§l✦ Reward: §e§l3 Carrots", "bold": false},{"text": "\n§e► Next: ", "bold": false},{"text": "Stockpile 32 Carrots §8(Reward: §f3 Blue Apricorn Sprouts§8)\n", "color": "white", "bold": false}]
tag @s add cq_reward_item_carrots_craft
schedule function server:quests/rewards/_finalize 20t append
