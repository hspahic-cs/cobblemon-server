# server-lunar-frequency

Lowers how often **Enhanced Celestials** lunar events fire. The default rate (each of
blood / harvest / blue moon rolling `chance` 0.10 every night with only 4 nights minimum
between) lands a special moon roughly every 5–7 nights — too frequent for this server.

## Changes

Overrides `data/enhancedcelestials/enhancedcelestials/lunar/event/{blood_moon,harvest_moon,blue_moon}.json`,
changing only the `dimension_chances."minecraft:overworld"` fields:

| Field | Default | New |
|-------|---------|-----|
| `chance` | 0.10 | **0.033** |
| `min_number_of_nights_between` | 4 | **12** |

≈ one special moon every ~16–20 nights (about a third as often). The `super_*` moons
(already `chance` 0.05, 20 nights between) are intentionally left untouched.

## Notes

- Full event JSONs are copied from the EC jar (datapack overrides replace the whole file;
  no field merge), so all other event settings — colors, drops, mob settings — are unchanged.
- To re-tune: edit `chance` (per-night probability) and/or `min_number_of_nights_between`
  in the three event files. Regenerate from the jar if the mod updates these defaults.
