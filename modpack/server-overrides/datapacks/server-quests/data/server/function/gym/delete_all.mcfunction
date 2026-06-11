# Despawn every gym leader + Elite Four.
function server:gym/delete_ground
function server:gym/delete_grass
function server:gym/delete_fighting
function server:gym/delete_steel
function server:gym/delete_fire
function server:gym/delete_electric
function server:gym/delete_water
function server:gym/delete_psychic
function server:gym/delete_dragon
function server:gym/delete_ghost
function server:gym/delete_bug
function server:gym/delete_normal
function server:gym/delete_poison
function server:gym/delete_rock
function server:gym/delete_flying
function server:gym/delete_ice
function server:gym/delete_fairy
function server:gym/delete_dark
function server:gym/delete_oak
function server:e4/delete_alder
function server:e4/delete_cynthia
function server:e4/delete_ash
function server:e4/delete_lance
function server:e4/delete_champion
tellraw @s [{"text":"§7All gym leader + E4 entities killed.","italic":true}]
