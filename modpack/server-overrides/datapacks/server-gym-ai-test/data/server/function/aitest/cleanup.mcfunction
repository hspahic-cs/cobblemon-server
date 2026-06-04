# Remove all AI test trainers (every variant).
kill @e[type=rctmod:trainer,tag=aitest]
tellraw @s [{"text":"§a✓ Cleared all AI test trainers","bold":true}]
