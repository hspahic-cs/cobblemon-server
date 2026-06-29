# server-flight-speed-cap

Caps the top speed of rideable flying Pokémon to **0.75×** the Cobblemon
defaults. Players were flying in straight lines on fast flying mounts to scan
for Legendary Monuments, generating chunks faster than the server could keep up
(main thread stalling in `FlowingFluid` post-processing during chunk gen →
"Can't keep up!" tick lag).

## Changes

Overrides `data/cobblemon/ride_settings/*.json`, lowering only the **max**
speed parameter of `speedExpr` (the per-Pokémon min-speed floor and stamina
are unchanged):

| Controller | Default max | New max | Notes |
|------------|-------------|---------|-------|
| `bird`     | 20.0        | 15.0    | most flying types |
| `jet`      | 24.0        | 18.0    | high-speed flyers |
| `hover`    | 20.0        | 15.0    | hovering mounts |
| `rocket`   | 0.5 (+boost) | unchanged | boost-based, not a sustained cruiser — left as-is pending a separate decision |

## Notes

- A full data file is required because datapack JSON overrides replace the
  source file wholesale (no field merge).
- This is a *rate* mitigation: it buys chunk-gen headroom but a determined
  flyer can still outrun a slow server. Pair with chunk-gen capacity work
  (pre-generation / more cores) for a complete fix.
- Cobblemon ride settings also expose a stamina mechanic
  (`staminaExpr`, `infiniteStamina`, `stamDrain*`) — an alternative/finer knob
  if a flat speed cap proves too blunt.
