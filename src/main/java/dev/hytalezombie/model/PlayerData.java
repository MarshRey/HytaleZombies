package dev.hytalezombie.model;

/**
 * Holds per-player data for the HytaleZombie game mode.
 * Tracks points, kills, downs, and other player-specific state.
 */
public class PlayerData {

    private final String playerId;
    private int points;
    private int kills;
    private int downs;
    private boolean isAlive;

    public PlayerData(String playerId) {
        this.playerId = playerId;
        this.points = 0;
        this.kills = 0;
        this.downs = 0;
        this.isAlive = true;
    }

    // --- Points Management ---

    public int getPoints() {
        return points;
    }

    public void addPoints(int amount) {
        this.points = Math.max(0, this.points + amount);
    }

    public boolean deductPoints(int amount) {
        if (this.points >= amount) {
            this.points -= amount;
            return true;
        }
        return false;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    // --- Kill Tracking ---

    public int getKills() {
        return kills;
    }

    public void incrementKills() {
        this.kills++;
    }

    // --- Down Tracking ---

    public int getDowns() {
        return downs;
    }

    public void incrementDowns() {
        this.downs++;
    }

    // --- Life State ---

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }

    // --- Identity ---

    public String getPlayerId() {
        return playerId;
    }

    /**
     * Resets all player data to initial state.
     */
    public void reset() {
        this.points = 0;
        this.kills = 0;
        this.downs = 0;
        this.isAlive = true;
    }
}
