# Elite Four — exploration music

This folder is the **exploration** layer: ambient music that plays while walking
around the Elite Four world (`multiworld:elite4`) *between* fights. During an
actual battle it's automatically paused and the battle theme takes over (see
`../elite4-battle/`), then it resumes.

Good fits are the calmer "Pokémon League" / Indigo Plateau / Victory Road area
themes rather than battle music. Optional — if you leave this empty, the world is
just quiet between fights. Drop audio here and run
`python3 ops/soundtracks/build-soundtracks.py`; tracks rotate with a short gap.

The iconic Elite Four/Champion *battle* tracks go in `../elite4-battle/`, not here.
