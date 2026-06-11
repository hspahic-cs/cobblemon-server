# Side-quest reward (follow-up to Centurion). Same "[Side Quest Complete]" prefix + purple
# coloring as reach_pokedex_100. The Master Ball is given here directly — a vanilla `give` is
# reliable for a Cobblemon item (unlike the cobblenav PokéNav, which the bridge grants in Kotlin).
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text":"\n§5§l[Side Quest Complete] ","bold":true},{"text":"Master Collector","color":"light_purple","bold":true},{"text":"\n§dCaught 300 different Pokémon species.","bold":false},{"text":"\n§6§l✦ Reward: §e§l1 Master Ball\n","bold":false}]
give @s cobblemon:master_ball 1
