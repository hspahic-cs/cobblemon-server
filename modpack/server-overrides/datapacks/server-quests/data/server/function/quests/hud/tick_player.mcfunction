# Runs `as <player>` every ~30 ticks (~1.5s) for HUD-on players. Picks the LOWEST
# uncompleted mainline quest by walking top-down and using a per-tick `cq_hud_done`
# tag to ensure only one quest's actionbar fires.

tag @s remove cq_hud_done

# --- select_pokemon ---
execute if entity @s[tag=!cq_hud_done,advancements={server:select_pokemon=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Pick a Starter Pokémon","color":"white","bold":false},{"text":" §7— press §fP§7 to select your starter"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:select_pokemon=false}] run tag @s add cq_hud_done

# --- use_wild ---
execute if entity @s[tag=!cq_hud_done,advancements={server:use_wild=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Enter the Wilderness","color":"white","bold":false},{"text":" §7— type §f/wild§7 to teleport"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:use_wild=false}] run tag @s add cq_hud_done

# --- set_home ---
execute if entity @s[tag=!cq_hud_done,advancements={server:set_home=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Set a Home","color":"white","bold":false},{"text":" §7— §f/sethome§7 wherever you want to live"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:set_home=false}] run tag @s add cq_hud_done

# --- craft_pokeball ---
execute if entity @s[tag=!cq_hud_done,advancements={server:craft_pokeball=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Craft a Poké Ball","color":"white","bold":false},{"text":" §7— 4 red apricorns + 1 copper ingot"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:craft_pokeball=false}] run tag @s add cq_hud_done

# --- catch_pokemon ---
execute if entity @s[tag=!cq_hud_done,advancements={server:catch_pokemon=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Catch a Pokémon","color":"white","bold":false},{"text":" §7— right-click a wild mon holding a Poké Ball"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:catch_pokemon=false}] run tag @s add cq_hud_done

# --- farm_carrots ---
execute if entity @s[tag=!cq_hud_done,advancements={server:farm_carrots=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Stockpile 32 Carrots","color":"white","bold":false},{"text":" §7— plant carrots and harvest 32"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:farm_carrots=false}] run tag @s add cq_hud_done

# --- heal_pokemon ---
execute if entity @s[tag=!cq_hud_done,advancements={server:heal_pokemon=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Heal a Pokémon","color":"white","bold":false},{"text":" §7— feed a carrot or use a Poké Healer"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:heal_pokemon=false}] run tag @s add cq_hud_done

# --- beat_wild_trainer ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_wild_trainer=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Beat a Wild Trainer","color":"white","bold":false},{"text":" §7— wander; trainers spawn around you"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_wild_trainer=false}] run tag @s add cq_hud_done

# --- reach_party_level_20 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:reach_party_level_20=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Level 20 Trainer","color":"white","bold":false},{"text":" §7— train any party Pokémon to level 20 (your cap)"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:reach_party_level_20=false}] run tag @s add cq_hud_done

# --- beat_gym_1 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_1=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 1: Dusty","color":"white","bold":false},{"text":" §7— find him at §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_1=false}] run tag @s add cq_hud_done

# --- reach_income_250 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:reach_income_250=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Reach $250","color":"white","bold":false},{"text":" §7— sell items at §f/warp market §7(tip: §f/market prices§7)"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:reach_income_250=false}] run tag @s add cq_hud_done

# --- first_pvp_win ---
execute if entity @s[tag=!cq_hud_done,advancements={server:first_pvp_win=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Win a Ranked Battle","color":"white","bold":false},{"text":" §7— §f/challenge <player>"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:first_pvp_win=false}] run tag @s add cq_hud_done

# --- beat_gym_2 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_2=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 2: Gardenia","color":"white","bold":false},{"text":" §7— §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_2=false}] run tag @s add cq_hud_done

# --- beat_gym_3 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_3=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 3: Lee Sin","color":"white","bold":false},{"text":" §7— §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_3=false}] run tag @s add cq_hud_done

# --- beat_gym_4 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_4=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 4: Jarvis","color":"white","bold":false},{"text":" §7— §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_4=false}] run tag @s add cq_hud_done

# --- beat_gym_5 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_5=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 5: Zuko","color":"white","bold":false},{"text":" §7— §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_5=false}] run tag @s add cq_hud_done

# --- beat_gym_6 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_6=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 6: Stan","color":"white","bold":false},{"text":" §7— §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_6=false}] run tag @s add cq_hud_done

# --- beat_gym_7 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_7=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 7: Kai","color":"white","bold":false},{"text":" §7— §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_7=false}] run tag @s add cq_hud_done

# --- beat_gym_8 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_8=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 8: Juniper","color":"white","bold":false},{"text":" §7— §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_8=false}] run tag @s add cq_hud_done

# --- beat_gym_9 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_9=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 9: Quinn","color":"white","bold":false},{"text":" §7— §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_9=false}] run tag @s add cq_hud_done

# --- beat_gym_10 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_10=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 10: Grimm","color":"white","bold":false},{"text":" §7— §f/warp gyms"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_10=false}] run tag @s add cq_hud_done

# Gyms 11-19 (rotating, optional) intentionally NOT on the HUD — they're branch
# content for extra keys, not mainline progression. They still appear in
# /quests list under "Rotating Gyms".

# --- beat_gym_20 (E4 1) ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_20=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Elite Four 1: Alder","color":"white","bold":false},{"text":" §7— §f/warp elite4"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_20=false}] run tag @s add cq_hud_done

# --- beat_gym_21 (E4 2 — consecutive after E4 1) ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_21=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Beat E4 2: Cynthia","color":"white","bold":false},{"text":" §7— consecutively after E4 1 §f(/warp elite4)"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_21=false}] run tag @s add cq_hud_done

# --- beat_gym_22 (E4 3 — consecutive after E4 1-2) ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_22=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Beat E4 3: Ash","color":"white","bold":false},{"text":" §7— fighting E4 1-3 consecutively §f(/warp elite4)"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_22=false}] run tag @s add cq_hud_done

# --- beat_gym_23 (E4 4 — consecutive after E4 1-3) ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_23=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Beat E4 4: Lance","color":"white","bold":false},{"text":" §7— fighting all four trainers consecutively §f(/warp elite4)"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_23=false}] run tag @s add cq_hud_done

# --- beat_gym_24 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_24=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Champion","color":"white","bold":false},{"text":" §7— §f/warp elite4"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_24=false}] run tag @s add cq_hud_done

# --- All mainline complete ---
execute if entity @s[tag=!cq_hud_done] run title @s actionbar [{"text":"§a✓ All mainline quests complete!  ","bold":true},{"text":"§7Try /quests list for branch tracks (ELO, income, colony).","color":"gray","bold":false}]
