Gym leader arena blueprints go here as `gymleader1.blueprint` ... `gymleader5.blueprint` (matches `BuildingGymLeader.SCHEMATIC_NAME` + level).

Author them in-game using Structurize's `/scan` tool, then copy the resulting `.blueprint` files out of the world's `minecolonies/scans/` directory into this folder.

The level is parsed from the last digit of the file name (see `AbstractBlockHut.onBlockPlacedFromBuildTool`), so names must end in `1`..`5`.
