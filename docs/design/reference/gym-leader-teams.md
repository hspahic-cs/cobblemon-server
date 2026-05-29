# Gym Leader Team Compositions

## Summary

Define the full team roster for all 10 gym leaders across all tiers and challenge mode. These feed directly into the Cobblemon datapack NPC definitions.

## Requirements

Each gym leader needs **4 tier teams + 1 challenge mode team:**

| Tier | Level Cap | Notes |
|------|-----------|-------|
| 1 | 15 | Entry level, type-themed basics |
| 2 | 25 | |
| 3 | 35 | |
| 4 | 45 | |
| Challenge | 100 | Competitive — optimized movesets, EVs, IVs, held items |

Each Pokemon entry needs:
- Species
- Level
- Moves (4)
- Ability
- Held item (optional for lower tiers, required for Challenge)
- IVs/EVs (required for Challenge)

## Gym Leaders

Fill in type and team for each floor:

| Floor | Type | Leader Name | Status |
|-------|------|-------------|--------|
| 1 | | | ⬜ Not started |
| 2 | | | ⬜ Not started |
| 3 | | | ⬜ Not started |
| 4 | | | ⬜ Not started |
| 5 | | | ⬜ Not started |
| 6 | | | ⬜ Not started |
| 7 | | | ⬜ Not started |
| 8 | | | ⬜ Not started |
| 9 | | | ⬜ Not started |
| 10 | | | ⬜ Not started |

## Reference

- YouTube series referenced in design doc for team inspiration
- All 18 types available — avoid duplicate types across leaders
- Tier 1 teams should be beatable with starter-level Pokemon

## Deliverable

A filled table per gym leader in the following format, ready to plug into the datapack:

```
Leader: <name>
Type: <type>

Tier 1 (Lv15):
  - <species> | Lv15 | Moves: ... | Ability: ...

Tier 2 (Lv25):
  ...

Tier 3 (Lv35):
  ...

Tier 4 (Lv45):
  ...

Challenge (Lv100):
  - <species> | Lv100 | Moves: ... | Ability: ... | Item: ... | EVs: ... | IVs: ...
```

## Acceptance Criteria

- [ ] All 10 gym leaders have a assigned type
- [ ] All 10 leaders have teams for tiers 1–4
- [ ] All 10 leaders have a Challenge Mode team
- [ ] No duplicate types across leaders
- [ ] Challenge Mode teams have full competitive sets
