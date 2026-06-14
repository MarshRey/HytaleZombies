package dev.hytalezombie.events;

/**
 * Listens for player connection events to initialize player-specific data.
 * 
 * In a real Hytale environment, this would use Hytale's PlayerReadyEvent.
 * For development/testing, we define a simpler event interface.
 */
public class PlayerConnectionListener {

    private static final PlayerConnectionListener INSTANCE = new PlayerConnectionListener();

    public static PlayerConnectionListener getInstance() {
        return INSTANCE;
    }

    /**
     * Called when a player is fully loaded and ready.
     * 
     * @param playerId the UUID of the ready player
     */
    public void onPlayerReady(String playerId) {
        System.out.println("§6[HytaleZombie] §aWelcome to HytaleZombie, " + playerId + "! Type /hytalezombie for help.");
    }
}
