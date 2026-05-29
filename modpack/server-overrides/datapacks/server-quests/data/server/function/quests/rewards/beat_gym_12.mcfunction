# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=12.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Gym Defeated] ","bold":true},{"text":"Gym 12: Cheren — Normal","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§lRare Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Another rotating gym §7(/warp gyms)§f or the Elite Four §7(/warp elite4)\n","color":"white","bold":false}]
tag @s add cq_reward_key_rare_1
schedule function server:quests/rewards/_finalize 20t append
