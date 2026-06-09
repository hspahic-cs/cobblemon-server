# Spawn Challenge Gym 1: Clay — Ground at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_01_ground_challenge.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.
# level_cap.50 forces a flat L50 player cap (challenge teams are L50), overriding the gym_id formula.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.1,tag=cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_01_ground_challenge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_01_ground_challenge"}] add cobblemon_bridge.gym_id.1
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_01_ground_challenge"}] add cobblemon_bridge.gym_challenge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_01_ground_challenge"}] add cobblemon_bridge.level_cap.50
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_01_ground_challenge"}] add cobblemon_bridge.anchor
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_01_ground_challenge"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§d✓ Spawned Challenge Gym 1: Clay — Ground","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_1_challenge"}]
