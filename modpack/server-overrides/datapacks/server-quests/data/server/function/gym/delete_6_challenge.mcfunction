# Despawn the CHALLENGE variant of Gym 6: Volkner.
# Filters by both gym_id and gym_challenge tags so it only kills the challenge entity.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.6,tag=cobblemon_bridge.gym_challenge]
tellraw @s [{"text":"§7Killed any Challenge Gym 6 (Volkner) entities.","italic":true}]
