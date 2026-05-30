# 0.7.25: switched from random Common Egg to a guaranteed Exeggcute egg. This kicks off the
# Cobbleworkers onboarding chain (receive_leaf_stone → evolve_exeggutor → ranch_carrot_farm)
# without requiring the player to roll into an Exeggcute from the common pool. Egg level/IVs
# match the gacha eggs (min_perfect_ivs=2).
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true},{"text": "First Trainer Down", "color": "white", "bold": true},{"text": "\n§6§l✦ Reward: §e§lExeggcute Egg §7(2 perfect IVs)", "bold": false},{"text": "\n§7Hatch it — you'll need it for the next quest line.", "bold": false},{"text": "\n§e► Next: ", "bold": false},{"text": "Level 20 Trainer §8(Reward: §aRanked starter kit§8)\n", "color": "white", "bold": false}]
tag @s add cq_reward_egg_exeggcute
schedule function server:quests/rewards/_finalize 20t append
