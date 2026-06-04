# Spawn the [A] variant of all 3 leaders (Sabrina, Surge, Blaine) at caller's position.
# Leaders spawn 3 blocks apart along +X. Run /function server:aitest/cleanup to remove.

# Clear any stale [A] aitest trainers first.
kill @e[type=rctmod:trainer,tag=aitest,tag=aitest.variant.a]

# Sabrina [A]
execute at @s run rctmod trainer summon_persistent aitest_sabrina_a
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_sabrina_a"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_sabrina_a"}] add aitest.variant.a
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_sabrina_a"}] {Invulnerable:1b,PersistenceRequired:1b}

# Surge [A] — 3 blocks east
execute at @s positioned ^3 ^ ^ run rctmod trainer summon_persistent aitest_surge_a
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_a"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_a"}] add aitest.variant.a
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_a"}] {Invulnerable:1b,PersistenceRequired:1b}

# Blaine [A] — 6 blocks east
execute at @s positioned ^6 ^ ^ run rctmod trainer summon_persistent aitest_blaine_a
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_blaine_a"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_blaine_a"}] add aitest.variant.a
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_blaine_a"}] {Invulnerable:1b,PersistenceRequired:1b}

tellraw @s [{"text":"§a✓ Spawned AI Test [A]: Sabrina, Surge, Blaine","bold":true},{"text":"\n§7Cleanup: §f/function server:aitest/cleanup"}]
