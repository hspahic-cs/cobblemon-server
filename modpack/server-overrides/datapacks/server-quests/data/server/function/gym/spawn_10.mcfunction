# Spawn Gym 10: Grimm — Ghost at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_10_ghost.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.10,tag=!cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_10_ghost
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_10_ghost"}] add cobblemon_bridge.gym_id.10
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_10_ghost"}] add cobblemon_bridge.anchor
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_10_ghost"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§a✓ Spawned Gym 10: Grimm — Ghost","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_10"}]
