# Side-quest reward. Distinct "[Side Quest Complete]" prefix + purple/light-purple coloring
# so it visually separates from main-line quest completions.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text":"\n§5§l[Side Quest Complete] ","bold":true},{"text":"Centurion","color":"light_purple","bold":true},{"text":"\n§dCaught 100 different Pokémon species.","bold":false},{"text":"\n§6§l✦ Reward: §e§l1 Master Ball + Ultra Key\n","bold":false}]
tag @s add cq_reward_item_pokedex_100
schedule function server:quests/rewards/_finalize 20t append
