# Despawn every gym leader + Elite Four.
function server:gym/delete_gym_ground gym_grass gym_fighting gym_steel gym_fire gym_electric gym_water gym_psychic gym_dragon gym_ghost gym_bug gym_normal gym_poison gym_rock gym_flying gym_ice gym_fairy gym_dark gym_oak e4_alder e4_cynthia e4_ash e4_lance e4_champion
tellraw @s [{"text":"§7All gym leader + E4 entities killed.","italic":true}]
