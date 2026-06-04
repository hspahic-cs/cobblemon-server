# Spawn the [B] variant of all 3 leaders (Sabrina, Surge, Blaine) at caller's position.
# Leaders spawn 3 blocks apart along the player's right. Cleanup: /function server:aitest/cleanup.

kill @e[type=rctmod:trainer,tag=aitest,tag=aitest.variant.b]

# Sabrina [B]
execute at @s run rctmod trainer summon_persistent aitest_sabrina_b
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_sabrina_b"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_sabrina_b"}] add aitest.variant.b
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_sabrina_b"}] {Invulnerable:1b,PersistenceRequired:1b}

# Surge [B]
execute at @s positioned ^3 ^ ^ run rctmod trainer summon_persistent aitest_surge_b
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_b"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_b"}] add aitest.variant.b
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_b"}] {Invulnerable:1b,PersistenceRequired:1b}

# Blaine [B]
execute at @s positioned ^6 ^ ^ run rctmod trainer summon_persistent aitest_blaine_b
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_blaine_b"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_blaine_b"}] add aitest.variant.b
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_blaine_b"}] {Invulnerable:1b,PersistenceRequired:1b}

tellraw @s [{"text":"§a✓ Spawned AI Test [B]: Sabrina, Surge, Blaine","bold":true},{"text":"\n§7Cleanup: §f/function server:aitest/cleanup"}]
