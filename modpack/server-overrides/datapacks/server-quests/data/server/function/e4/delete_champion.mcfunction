# Despawn Gym 24: N. Only kills entities carrying our gym_id tag, so it
# won't touch other RCT trainers that happen to be nearby.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.24]
tellraw @s [{"text":"§7Killed any Gym 24 (N) entities.","italic":true}]
