# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=1.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Gym Defeated] ","bold":true},{"text":"Gym 1: Clay — Ground","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§lRare Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Reach $250 §7(sell items at /warp market)§f; then Gym 2 §7(/warp gyms) §8(Reward: §fPasture Block; §5Rare Key§8)\n","color":"white","bold":false}]
tag @s add cq_reward_key_rare_1
schedule function server:quests/rewards/_finalize 20t append
