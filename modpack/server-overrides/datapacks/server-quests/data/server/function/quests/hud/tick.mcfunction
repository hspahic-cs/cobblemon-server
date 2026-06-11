# Runs every server tick (registered via `minecraft:tick` function tag).
#
# Two responsibilities:
#   1. Drain any `/trigger cq_hud_toggle` activations: if a player has score 1, flip their tag
#      and reset the trigger so they can run it again later.
#   2. Throttle: bump every online player's cq_hud_tick score; only when the score % 30 == 0
#      do we run the per-player HUD refresh (action bar). That's ~1.5s cadence.

# --- Handle opt-out toggles --------------------------------------------------
execute as @a[scores={cq_hud_toggle=1..}] run function server:quests/hud/handle_toggle

# --- Throttle the HUD refresh to every 30 ticks (1.5s) ----------------------
scoreboard players add @a cq_hud_tick 1

execute as @a[scores={cq_hud_tick=30..},tag=!cq_hud_off] run function server:quests/hud/tick_player

# Reset the tick counter for anyone who hit 30 this tick (so we count fresh next round).
scoreboard players set @a[scores={cq_hud_tick=30..}] cq_hud_tick 0

# Make sure new joiners get the trigger usable. `scoreboard players enable` is idempotent on
# already-enabled players, so running it cheaply each tick covers anyone who joined since load.
scoreboard players enable @a cq_hud_toggle

# --- Milestone: Ultra Key for clearing all ten challenge gym-type leaders (any order) -------
# Grants the meta-advancement (which awards an Ultra Key) to anyone who holds all ten
# beat_gym_N_challenge advancements but not yet the meta. The `=false` clause makes this fire
# exactly once per player and also covers players who finished the challenges before this existed.
advancement grant @a[advancements={server:beat_gym_1_challenge=true,server:beat_gym_2_challenge=true,server:beat_gym_3_challenge=true,server:beat_gym_4_challenge=true,server:beat_gym_5_challenge=true,server:beat_gym_6_challenge=true,server:beat_gym_7_challenge=true,server:beat_gym_8_challenge=true,server:beat_gym_9_challenge=true,server:beat_gym_10_challenge=true,server:beat_all_challenge_gyms=false}] only server:beat_all_challenge_gyms
