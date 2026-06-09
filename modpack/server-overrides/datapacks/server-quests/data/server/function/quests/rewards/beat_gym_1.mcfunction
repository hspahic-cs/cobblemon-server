# Awarded by the rctmod:defeat_count trigger on the advancement when the player defeats
# Clay (trainer_id=gym_01_ground). 0.7.29: stripped the `eco give` line (it was added in
# 0.7.26 but /eco isn't registered at datapack-load time, so the whole function silently
# failed to load and players got NO reward) — gym bounty is now paid by Kotlin
# AdvancementHook listening to AdvancementEarnEvent. Also stripped the
# `advancement grant ... receive_leaf_stone gym1_done` line — that quest was deleted in
# 0.7.29; Leaf Stone reward moved to reach_income_250 directly.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Gym Defeated] ","bold":true},{"text":"Gym 1: Clay — Ground","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §6$150 §7+§e§l Rare Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Reach $250 to receive a §eLeaf Stone§f, then evolve, place a Pasture Block, win PvP, then Gym 2\n","color":"white","bold":false}]
tag @s add cq_reward_key_rare_1
schedule function server:quests/rewards/_finalize 20t append
