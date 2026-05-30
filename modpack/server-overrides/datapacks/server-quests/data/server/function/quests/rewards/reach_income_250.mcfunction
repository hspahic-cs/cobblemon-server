# 0.7.25 — Pasture Block reward moved out of this quest; it now lives on
# evolve_exeggutor where it fits the Cobbleworkers introduction narrative. This quest
# now gives a small grinding kit (5 Great Balls + 3 EXP Candy S). It also fires the
# `income_done` criterion on `receive_leaf_stone` so that quest unlocks once the player
# has also completed beat_gym_1.
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true},{"text": "Pocket Change", "color": "white", "bold": true},{"text": "\n§eYour first ¢250 banked. Keep grinding.", "bold": false},{"text": "\n§6§l✦ Reward: §e§l5 Great Balls + 3 EXP Candy S", "bold": false},{"text": "\n§e► Next: ", "bold": false},{"text": "Beat Gym 1 to unlock the §eLeaf Stone§7 quest §8(starts the Exeggutor farm chain)\n", "color": "white", "bold": false}]
tag @s add cq_reward_item_income_kit
advancement grant @s only server:receive_leaf_stone income_done
schedule function server:quests/rewards/_finalize 20t append
