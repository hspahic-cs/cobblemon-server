# 0.7.29 — stripped the `eco give @s 5000` line. NeoEssentials' /eco command isn't
# registered at datapack function-load time, so brigadier rejected the whole function
# parse in 0.7.26–0.7.28 — players who beat all 4 E4 got nothing. Bounty payment moved
# to the Kotlin AdvancementHook (subscribes to NeoForge AdvancementEarnEvent for
# server:defeat_elite_four), which fires from a context where the eco bridge is live.
# Master Ball + Ultra Key still delivered via the cq_reward_item_elite_four tag handler
# in _finalize.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text":"\n§6§l[Mainline Quest Complete] ","bold":true},{"text":"Defeat the Elite Four","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §6$5,000 §7+ §e§lMaster Ball §7+ §e§lUltra Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Pokémon Champion §8(Reward: see beat_gym_24)\n","color":"white","bold":false}]
tag @s add cq_reward_item_elite_four
schedule function server:quests/rewards/_finalize 20t append
