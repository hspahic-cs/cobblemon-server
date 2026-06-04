# Spawn the [C] variant of all 3 leaders (Sabrina, Surge, Blaine) at caller's position.
# Leaders spawn 3 blocks apart along the player's right. Cleanup: /function server:aitest/cleanup.

kill @e[type=rctmod:trainer,tag=aitest,tag=aitest.variant.c]

# Sabrina [C]
execute at @s run rctmod trainer summon_persistent aitest_sabrina_c
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_sabrina_c"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_sabrina_c"}] add aitest.variant.c
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_sabrina_c"}] {Invulnerable:1b,PersistenceRequired:1b}

# Surge [C]
execute at @s positioned ^3 ^ ^ run rctmod trainer summon_persistent aitest_surge_c
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_c"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_c"}] add aitest.variant.c
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_c"}] {Invulnerable:1b,PersistenceRequired:1b}

# Blaine [C]
execute at @s positioned ^6 ^ ^ run rctmod trainer summon_persistent aitest_blaine_c
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_blaine_c"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_blaine_c"}] add aitest.variant.c
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_blaine_c"}] {Invulnerable:1b,PersistenceRequired:1b}

tellraw @s [{"text":"§a✓ Spawned AI Test [C]: Sabrina, Surge, Blaine","bold":true},{"text":"\n§7Cleanup: §f/function server:aitest/cleanup"}]
