# Temperature A/B test: Blaine + Byron at T=0,1,2
# Generated for tuning — /function server:aitest/cleanup to remove
#
# `rctmod trainer summon_persistent` ignores the command's `positioned` context
# and spawns at the player's feet, so each trainer is identified by its unique
# TrainerId at @s (no distance clamp) and then tp'd out to its row slot. Tagging
# off a ^offset point silently dropped every leader past ^3 (no level cap).

kill @e[type=rctmod:trainer,tag=aitest.temptest]

# Blaine T=0.0  (+0x)
execute at @s run rctmod trainer summon_persistent aitest_gym_05_blaine_t0_pe
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t0_pe"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t0_pe"}] add aitest.temptest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t0_pe"}] add cobblemon_bridge.level_cap.50
execute at @s run data merge entity @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t0_pe"}] {Invulnerable:1b,PersistenceRequired:1b}
execute at @s positioned ^0 ^ ^ run tp @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t0_pe"}] ~ ~ ~

# Blaine T=1.0  (+3x)
execute at @s run rctmod trainer summon_persistent aitest_gym_05_blaine_t1_pe
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t1_pe"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t1_pe"}] add aitest.temptest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t1_pe"}] add cobblemon_bridge.level_cap.50
execute at @s run data merge entity @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t1_pe"}] {Invulnerable:1b,PersistenceRequired:1b}
execute at @s positioned ^3 ^ ^ run tp @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t1_pe"}] ~ ~ ~

# Blaine T=2.0  (+6x)
execute at @s run rctmod trainer summon_persistent aitest_gym_05_blaine_t2_pe
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t2_pe"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t2_pe"}] add aitest.temptest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t2_pe"}] add cobblemon_bridge.level_cap.50
execute at @s run data merge entity @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t2_pe"}] {Invulnerable:1b,PersistenceRequired:1b}
execute at @s positioned ^6 ^ ^ run tp @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t2_pe"}] ~ ~ ~

# Byron T=0.0  (+9x)
execute at @s run rctmod trainer summon_persistent aitest_gym_04_byron_t0_pe
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t0_pe"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t0_pe"}] add aitest.temptest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t0_pe"}] add cobblemon_bridge.level_cap.50
execute at @s run data merge entity @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t0_pe"}] {Invulnerable:1b,PersistenceRequired:1b}
execute at @s positioned ^9 ^ ^ run tp @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t0_pe"}] ~ ~ ~

# Byron T=1.0  (+12x)
execute at @s run rctmod trainer summon_persistent aitest_gym_04_byron_t1_pe
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t1_pe"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t1_pe"}] add aitest.temptest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t1_pe"}] add cobblemon_bridge.level_cap.50
execute at @s run data merge entity @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t1_pe"}] {Invulnerable:1b,PersistenceRequired:1b}
execute at @s positioned ^12 ^ ^ run tp @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t1_pe"}] ~ ~ ~

# Byron T=2.0  (+15x)
execute at @s run rctmod trainer summon_persistent aitest_gym_04_byron_t2_pe
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t2_pe"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t2_pe"}] add aitest.temptest
execute at @s run tag @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t2_pe"}] add cobblemon_bridge.level_cap.50
execute at @s run data merge entity @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t2_pe"}] {Invulnerable:1b,PersistenceRequired:1b}
execute at @s positioned ^15 ^ ^ run tp @e[type=rctmod:trainer,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t2_pe"}] ~ ~ ~

tellraw @s [{"text":"§a✓ Temp test: Blaine T0/T1/T2, Byron T0/T1/T2","bold":true},{"text":"\n§7Cleanup: §f/function server:aitest/cleanup"}]
