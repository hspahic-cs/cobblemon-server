# reference/

Local clones of upstream mod source trees, kept here for code lookup during
development. **Nothing in this directory is built or shipped.** The actual
mod jars come from packwiz (`modpack/mods/*.pw.toml`) for third-party mods
and from `custom-mods/` for in-house ones.

This whole folder is gitignored — clone the upstream repos yourself if you
want them locally:

```sh
git clone https://gitlab.com/cable-mc/cobblemon.git              reference/cobblemon
git clone https://github.com/ldtteam/minecolonies.git            reference/minecolonies
git clone https://github.com/Luke100000/minecraft-comes-alive.git reference/minecraft-comes-alive
```
