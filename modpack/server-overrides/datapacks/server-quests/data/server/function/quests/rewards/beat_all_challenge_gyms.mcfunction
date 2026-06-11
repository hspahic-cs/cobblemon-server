# Granted once the player has cleared all ten challenge gym-type leaders (any order) —
# fired by the per-tick check in quests/hud/tick. Reward: 1 Ultra Key.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text":"\n§5§l[Challenge Cleared] ","bold":true},{"text":"Hard Mode Master","color":"light_purple","bold":true},{"text":"\n§dDefeated the challenge variant of all ten gym leaders.","bold":false},{"text":"\n§6§l✦ Reward: §e§lUltra Key\n","bold":false}]
tag @s add cq_reward_key_ultra_1
schedule function server:quests/rewards/_finalize 20t append
