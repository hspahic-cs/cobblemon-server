# server-overrides/config

Authored configs shipped to `<install>/config/` on every deploy.

Layout per the [mod-state-vs-config](../../../docs/design/mod-state-vs-config.md)
convention:

```
config/
├── cobblemon-<modname>/
│   └── authored/
│       └── *.json     ← the only files we ship
└── <third-party-mod>/
    └── *.json         ← opt-in per file
```

Anything outside these paths is untouched on deploy.
Player runtime state (`runtime/`, world data) lives only on the VM.
