// ...

public class Teleportation {
    // ...

    public void teleportToWild(World world, Player player) {
        // Ensure the player is teleported within the spawn world
        int x = (int) (Math.random() * 1000) - 500;
        int z = (int) (Math.random() * 1000) - 500;
        int y = world.getHighestBlockYAt(x, z);

        // Check if the coordinates are within the spawn world
        if (Math.abs(x) > 1000 || Math.abs(z) > 1000) {
            // If not, adjust the coordinates to be within the spawn world
            x = Math.max(-1000, Math.min(x, 1000));
            z = Math.max(-1000, Math.min(z, 1000));
        }

        player.teleport(new Location(world, x, y, z));
    }

    // ...
}