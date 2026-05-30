# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=22.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Elite Four #3] ","bold":true},{"text":"Elite Four #3: Agatha","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§lRare Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Continue the Elite Four gauntlet §7(/warp elite4) §8(Reward: §6Ultra Key§8)\n","color":"white","bold":false}]
# 0.7.25 — gym bounty paid here via /eco give
eco give @s 3300
tag @s add cq_reward_key_rare_1
schedule function server:quests/rewards/_finalize 20t append
