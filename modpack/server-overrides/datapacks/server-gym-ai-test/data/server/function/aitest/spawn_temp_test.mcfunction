# Temperature A/B test: Blaine + Byron at T=0,1,2
# Generated for tuning — /function server:aitest/cleanup to remove

kill @e[type=rctmod:trainer,tag=aitest.temptest]

# Blaine T=0.0  (+0x)
execute at @s positioned ^0 ^ ^ run rctmod trainer summon_persistent aitest_gym_05_blaine_t0_pe
execute at @s positioned ^0 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t0_pe"}] add aitest
execute at @s positioned ^0 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t0_pe"}] add aitest.temptest
execute at @s positioned ^0 ^ ^ run data merge entity @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t0_pe"}] {Invulnerable:1b,PersistenceRequired:1b}

# Blaine T=1.0  (+3x)
execute at @s positioned ^3 ^ ^ run rctmod trainer summon_persistent aitest_gym_05_blaine_t1_pe
execute at @s positioned ^3 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t1_pe"}] add aitest
execute at @s positioned ^3 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t1_pe"}] add aitest.temptest
execute at @s positioned ^3 ^ ^ run data merge entity @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t1_pe"}] {Invulnerable:1b,PersistenceRequired:1b}

# Blaine T=2.0  (+6x)
execute at @s positioned ^6 ^ ^ run rctmod trainer summon_persistent aitest_gym_05_blaine_t2_pe
execute at @s positioned ^6 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t2_pe"}] add aitest
execute at @s positioned ^6 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t2_pe"}] add aitest.temptest
execute at @s positioned ^6 ^ ^ run data merge entity @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_05_blaine_t2_pe"}] {Invulnerable:1b,PersistenceRequired:1b}

# Byron T=0.0  (+9x)
execute at @s positioned ^9 ^ ^ run rctmod trainer summon_persistent aitest_gym_04_byron_t0_pe
execute at @s positioned ^9 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t0_pe"}] add aitest
execute at @s positioned ^9 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t0_pe"}] add aitest.temptest
execute at @s positioned ^9 ^ ^ run data merge entity @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t0_pe"}] {Invulnerable:1b,PersistenceRequired:1b}

# Byron T=1.0  (+12x)
execute at @s positioned ^12 ^ ^ run rctmod trainer summon_persistent aitest_gym_04_byron_t1_pe
execute at @s positioned ^12 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t1_pe"}] add aitest
execute at @s positioned ^12 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t1_pe"}] add aitest.temptest
execute at @s positioned ^12 ^ ^ run data merge entity @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t1_pe"}] {Invulnerable:1b,PersistenceRequired:1b}

# Byron T=2.0  (+15x)
execute at @s positioned ^15 ^ ^ run rctmod trainer summon_persistent aitest_gym_04_byron_t2_pe
execute at @s positioned ^15 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t2_pe"}] add aitest
execute at @s positioned ^15 ^ ^ run tag @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t2_pe"}] add aitest.temptest
execute at @s positioned ^15 ^ ^ run data merge entity @e[type=rctmod:trainer,distance=..5,limit=1,sort=nearest,nbt={TrainerId:"aitest_gym_04_byron_t2_pe"}] {Invulnerable:1b,PersistenceRequired:1b}

tellraw @s [{"text":"§a✓ Temp test: Blaine T0/T1/T2, Byron T0/T1/T2","bold":true},{"text":"\n§7Cleanup: §f/function server:aitest/cleanup"}]
