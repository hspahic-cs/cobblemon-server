# Spawn rb + pe variants of Surge side-by-side at caller's position.
# rb at caller, pe 3 blocks east. Run /function server:aitest/cleanup to remove.

# Clear any stale Surge aitest trainers first.
kill @e[type=rctmod:trainer,tag=aitest,tag=aitest.leader.surge]

# Surge [rb]
execute at @s run rctmod trainer summon_persistent aitest_surge_rb
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_rb"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_rb"}] add aitest.leader.surge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_rb"}] add aitest.variant.rb
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_rb"}] {Invulnerable:1b,PersistenceRequired:1b}

# Surge [pe] — 3 blocks east
execute at @s positioned ^3 ^ ^ run rctmod trainer summon_persistent aitest_surge_pe
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_pe"}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_pe"}] add aitest.leader.surge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_pe"}] add aitest.variant.pe
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"aitest_surge_pe"}] {Invulnerable:1b,PersistenceRequired:1b}

tellraw @s [{"text":"§a✓ Spawned Surge: [rb] vs [pe]","bold":true},{"text":"\n§7Cleanup: §f/function server:aitest/cleanup"}]
