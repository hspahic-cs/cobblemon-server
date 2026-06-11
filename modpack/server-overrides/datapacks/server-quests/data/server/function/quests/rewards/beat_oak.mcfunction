# Flavor for beating Professor Oak (shared by normal + hard). The Rare Key and the cash bounty are
# granted Kotlin-side by cobblemon-bridge AdvancementHook (datapack gacha/eco calls fail to load).
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§5§l[Secret] ","bold":true},{"text":"You bested Professor Oak — the Kanto Master.","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§lRare Key","bold":false}]
