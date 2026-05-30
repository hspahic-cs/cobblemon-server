# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=10.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Gym Defeated] ","bold":true},{"text":"Gym 10: Morty — Ghost","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§lUltra Key","bold":false},{"text":"\n§e► Next (mainline): ","bold":false},{"text":"Defeat the Elite Four §7(/warp elite4) §8(Reward: §6$5,000 + Master Ball + Ultra Key§8)","color":"white","bold":false},{"text":"\n§7Optional: rotating gyms 11–19 §7(/warp gyms) §8for extra Rare Keys\n","bold":false}]
# 0.7.25 — gym bounty paid here via /eco give
eco give @s 1500
tag @s add cq_reward_key_ultra_1
schedule function server:quests/rewards/_finalize 20t append
