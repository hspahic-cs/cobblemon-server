# 0.7.26 — mainline payout for beating all 4 Elite Four trainers. Cash via /eco give
# (NeoEssentials) so it lands in the same balance the gym bounties go to. Master Ball +
# Ultra Key delivered via tag → _finalize handler 20 ticks later (same pattern as
# reach_pokedex_100, which has the same item bundle).
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text":"\n§6§l[Mainline Quest Complete] ","bold":true},{"text":"Defeat the Elite Four","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§l$5,000 + Master Ball + Ultra Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Pokémon Champion §8(Reward: see beat_gym_24)\n","color":"white","bold":false}]
eco give @s 5000
tag @s add cq_reward_item_elite_four
schedule function server:quests/rewards/_finalize 20t append
