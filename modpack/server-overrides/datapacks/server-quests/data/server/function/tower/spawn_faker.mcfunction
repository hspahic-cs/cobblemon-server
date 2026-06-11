# Test-spawn Faker (Battle Tower bt_19, challenge / pe AI) at the caller's position.
# Summons the challenge variant with the tower's flat L50 cap so the fight matches the
# real hard-track tower battle. Re-running kills the previous copy first (no duplicates).
kill @e[type=rctmod:trainer,nbt={TrainerId:"bt_19_faker_challenge"}]
execute at @s run rctmod trainer summon_persistent bt_19_faker_challenge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"bt_19_faker_challenge"}] add cobblemon_bridge.level_cap.50
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"bt_19_faker_challenge"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§a✓ Spawned Faker — Unkillable Demon King (BT challenge, L50 cap)","bold":true},{"text":"\n§7Remove: §f/kill @e[type=rctmod:trainer,nbt={TrainerId:\"bt_19_faker_challenge\"}]"}]
