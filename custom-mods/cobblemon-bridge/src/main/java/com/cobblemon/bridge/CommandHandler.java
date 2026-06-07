// ...

public class CommandHandler {
    // ...

    @Command(name = "wild", permission = "cobblemon.wild")
    public void handleWildCommand(Player player) {
        // Get the spawn world
        World world = player.getServer().getWorld("spawn");

        // Teleport the player to a random location within the spawn world
        Teleportation teleportation = new Teleportation();
        teleportation.teleportToWild(world, player);
    }

    // ...
}