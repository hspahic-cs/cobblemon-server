# server-rct-ai-fix

Auto-generated datapack — **do not hand-edit**.

Overrides every vanilla rctmod trainer JSON that's missing an `ai` field, injecting
`"ai": {"type": "rb", "data": {}}` so the trainer routes through `rbrctai`. Without
this, the default Cobblemon trainer AI runs the battle but `BATTLE_VICTORY` doesn't
fire reliably on this server (Cobblemon 1.7.3 + Sinytra Connector), which silently
skips the NPC bounty payout in `GymDefeatHook`.

Regenerate from a fresh rctmod jar with:

```
python3 ops/gen_rct_ai_fix_datapack.py /path/to/rctmod-neoforge-*.jar
```

Trainers that already have an `ai` field set in the rctmod jar are NOT touched —
this datapack only overrides the ai-missing subset.
