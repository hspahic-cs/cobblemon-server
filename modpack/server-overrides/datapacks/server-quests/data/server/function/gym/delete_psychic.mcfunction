# Despawn Gym 8: Juniper — Psychic. Only kills entities carrying our gym_id tag, so it
# won't touch other RCT trainers that happen to be nearby.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.8]
tellraw @s [{"text":"§7Killed any Gym 8 (Juniper — Psychic) entities.","italic":true}]
