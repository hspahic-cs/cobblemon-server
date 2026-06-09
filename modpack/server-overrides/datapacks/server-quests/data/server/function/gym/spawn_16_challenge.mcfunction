# Spawn Challenge Gym 16: Brycen — Ice at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_16_ice_challenge.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.
# level_cap.50 forces a flat L50 player cap (challenge teams are L50), overriding the gym_id formula.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.16,tag=cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_16_ice_challenge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_16_ice_challenge"}] add cobblemon_bridge.gym_id.16
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_16_ice_challenge"}] add cobblemon_bridge.gym_challenge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_16_ice_challenge"}] add cobblemon_bridge.level_cap.50
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_16_ice_challenge"}] add cobblemon_bridge.anchor
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_16_ice_challenge"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§d✓ Spawned Challenge Gym 16: Brycen — Ice","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_16_challenge"}]
