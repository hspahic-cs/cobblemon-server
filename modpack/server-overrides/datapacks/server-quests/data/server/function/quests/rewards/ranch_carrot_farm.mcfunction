# 0.7.25 — capstone of the Exeggcute onboarding. Player just placed their Pasture Block;
# reward with bonemeal so they can fast-grow a starter carrot patch for the Exeggutor.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text":"\n§6§l[Mainline Quest Complete] ","bold":true},{"text":"Farm Hands","color":"white","bold":true},{"text":"\n§6§l✦ Reward: §e§l16 Bone Meal","bold":false},{"text":"\n§7Fast-grow some carrots and watch your Exeggutor harvest. Range is §a5x5 horizontal§7 from the Pasture Block.","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"First PvP Win §8(Reward: §aCommon Egg§8)\n","color":"white","bold":false}]
tag @s add cq_reward_item_bonemeal_16
schedule function server:quests/rewards/_finalize 20t append
