# Gym progression

> **Status:** stub — fleshed-out walkthrough pending.

The server has 24 gym leaders organized into a tower. Each gym has four
difficulty tiers (1–4) plus a competitive **Challenge Mode** where teams use
optimized movesets, EVs/IVs, and held items.

## Beat order

Gyms 1–18 are themed by Pokémon type and have wiki-sourced teams. Gyms
19–23 are the Elite Four (custom-designed, harder). Gym 24 is the Champion.

You can challenge gyms in any order, but the level cap scales with the gym
number — early gyms are easier on your team.

## Rewards

Each tier pays out coins (more for higher tiers), and Challenge Mode pays
the most. Defeating a gym leader for the first time also awards an
advancement (`server:beat_gym_<N>`) which gates other content.

## Read more

- **[Gym system design](../design/gym-system.md)** — full design doc
- **[Economy](economy.md)** — what gym wins pay out

Full gym leader rosters live in the `server-gyms` datapack under
`modpack/server-overrides/datapacks/server-gyms/data/server/trainers/`.
