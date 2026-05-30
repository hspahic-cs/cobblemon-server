# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=1.
# 0.7.25: also fires the `gym1_done` criterion on receive_leaf_stone so that quest
# unlocks once the player has ALSO completed reach_income_250.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Gym Defeated] ","bold":true},{"text":"Gym 1: Clay — Ground","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§lRare Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Reach $250 §7(sell items at /warp market) §7to unlock the §eLeaf Stone§7 quest, then Gym 2 §8(via the §eExeggutor farm§8 chain)\n","color":"white","bold":false}]
# 0.7.25 — gym bounty paid here via /eco give
eco give @s 150
tag @s add cq_reward_key_rare_1
advancement grant @s only server:receive_leaf_stone gym1_done
schedule function server:quests/rewards/_finalize 20t append
