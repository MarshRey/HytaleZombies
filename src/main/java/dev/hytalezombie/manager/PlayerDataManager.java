package dev.hytalezombie.manager;

import dev.hytalezombie.model.PlayerData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player data across the game session.
 * Tracks all connected players and their individual stats.
 */
public class PlayerDataManager {

    private final Map<String, PlayerData> playerDataMap;

    public PlayerDataManager() {
        this.playerDataMap = new ConcurrentHashMap<>();
    }

    /**
     * Retrieves or creates player data for the given player ID.
     */
    @Nonnull
    public PlayerData getOrCreatePlayerData(@Nonnull String playerId) {
        return playerDataMap.computeIfAbsent(playerId, PlayerData::new);
    }

    /**
     * Retrieves existing player data, or null if the player has no data yet.
     */
    @Nullable
    public PlayerData getPlayerData(@Nonnull String playerId) {
        return playerDataMap.get(playerId);
    }

    /**
     * Removes a player's data when they disconnect.
     */
    public void removePlayerData(@Nonnull String playerId) {
        playerDataMap.remove(playerId);
    }

    /**
     * Checks if a player has enough points for a purchase.
     */
    public boolean hasEnoughPoints(@Nonnull String playerId, int cost) {
        PlayerData data = playerDataMap.get(playerId);
        return data != null && data.getPoints() >= cost;
    }

    /**
     * Resets all player data (for new match).
     */
    public void resetAll() {
        for (PlayerData data : playerDataMap.values()) {
            data.reset();
        }
    }

    /**
     * Returns the number of tracked players.
     */
    public int getPlayerCount() {
        return playerDataMap.size();
    }
}
