package dev.hytalezombie.events;

/**
 * Player connection event handling.
 * 
 * PlayerReadyEvent handling is now done directly in HytaleZombiePlugin.preLoad()
 * via Hytale's EventRegistry. This class is kept as a utility for any
 * player-related logic that needs to be shared.
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
        System.out.println("[HytaleZombie] Welcome to HytaleZombie, " + playerId + "! Type /hytalezombie for help.");
    }
}
