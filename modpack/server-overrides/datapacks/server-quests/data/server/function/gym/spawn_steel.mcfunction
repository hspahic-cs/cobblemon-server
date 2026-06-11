# Spawn Gym 4: Jarvis — Steel at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_04_steel.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.4,tag=!cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_04_steel
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_04_steel"}] add cobblemon_bridge.gym_id.4
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_04_steel"}] add cobblemon_bridge.anchor
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_04_steel"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§a✓ Spawned Gym 4: Jarvis — Steel","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_4"}]
