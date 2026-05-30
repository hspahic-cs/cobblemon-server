# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=19.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Gym Defeated] ","bold":true},{"text":"Professor Oak — Kanto Mastery","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§lUltra Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Take on the Elite Four §7(/warp elite4) §8(Reward: §5Rare Key§8)\n","color":"white","bold":false}]
# 0.7.25 — gym bounty paid here via /eco give
eco give @s 2850
tag @s add cq_reward_key_ultra_1
schedule function server:quests/rewards/_finalize 20t append
