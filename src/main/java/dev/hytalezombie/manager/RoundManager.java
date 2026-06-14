package dev.hytalezombie.manager;

import dev.hytalezombie.HytaleZombiePlugin;
import dev.hytalezombie.config.HytaleZombieConfig;

/**
 * Manages the round/wave system for the survival game mode.
 * Tracks the current round number, triggers round advancement,
 * and provides scaling data for zombie difficulty.
 */
public class RoundManager {

    private final HytaleZombiePlugin plugin;
    private HytaleZombieConfig config;
    private int currentRound;
    private int activeZombieCount;
    private boolean matchActive;

    /**
     * Creates a RoundManager linked to the plugin.
     * The config will be loaded from the plugin's static config.
     */
    public RoundManager(HytaleZombiePlugin plugin) {
        this.plugin = plugin;
        this.currentRound = 0;
        this.activeZombieCount = 0;
        this.matchActive = false;
    }

    /**
     * Creates a RoundManager with a specific config (for testing).
     */
    public RoundManager(HytaleZombiePlugin plugin, HytaleZombieConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.currentRound = 0;
        this.activeZombieCount = 0;
        this.matchActive = false;
    }

    /**
     * Returns the config, either the injected one or from the plugin.
     */
    private HytaleZombieConfig getConfig() {
        if (config != null) {
            return config;
        }
        return HytaleZombiePlugin.getPluginConfig();
    }

    /**
     * Starts a new match from round 1.
     */
    public void startMatch() {
        this.currentRound = 1;
        this.matchActive = true;
        this.activeZombieCount = 0;
        plugin.getLogger().info("HytaleZombie match started! Round 1");
    }

    /**
     * Ends the current match and resets state.
     */
    public void endMatch() {
        this.currentRound = 0;
        this.matchActive = false;
        this.activeZombieCount = 0;
        plugin.getLogger().info("HytaleZombie match ended.");
    }

    /**
     * Advances to the next round.
     */
    public void advanceRound() {
        if (!matchActive) return;
        this.currentRound++;
        plugin.getLogger().info("HytaleZombie advancing to round " + currentRound);
    }

    /**
     * Calculates the health for a zombie based on the current round.
     * Health scales by the configured multiplier each round.
     */
    public float getScaledZombieHealth() {
        HytaleZombieConfig cfg = getConfig();
        float baseHealth = cfg.getZombieBaseHealth();
        float scaling = cfg.getHealthScalingPerRound();
        return (float) (baseHealth * Math.pow(scaling, currentRound - 1));
    }

    /**
     * Calculates the speed multiplier for a zombie based on the current round.
     */
    public float getScaledZombieSpeed() {
        HytaleZombieConfig cfg = getConfig();
        float baseSpeed = cfg.getZombieBaseSpeed();
        float scaling = cfg.getSpeedScalingPerRound();
        return (float) (baseSpeed * Math.pow(scaling, currentRound - 1));
    }

    /**
     * Calculates the number of zombies to spawn for this round.
     */
    public int getSpawnCount(int playerCount) {
        HytaleZombieConfig cfg = getConfig();
        int baseCount = cfg.getZombieSpawnBaseCount();
        int perPlayer = cfg.getZombiesPerPlayer();
        return baseCount + (perPlayer * playerCount) + (currentRound * 2);
    }

    // --- Getters ---

    public int getCurrentRound() {
        return currentRound;
    }

    public int getActiveZombieCount() {
        return activeZombieCount;
    }

    public boolean isMatchActive() {
        return matchActive;
    }

    public void incrementActiveZombies() {
        this.activeZombieCount++;
    }

    public void decrementActiveZombies() {
        this.activeZombieCount = Math.max(0, this.activeZombieCount - 1);
        // Auto-advance when all zombies are eliminated
        if (matchActive && this.activeZombieCount <= 0) {
            advanceRound();
        }
    }
}
