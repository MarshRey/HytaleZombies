package dev.hytalezombie;

import dev.hytalezombie.commands.HytaleZombieCommand;
import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.events.PlayerConnectionListener;
import dev.hytalezombie.manager.*;
import dev.hytalezombie.spawn.SpawnManager;

import java.util.logging.Logger;

/**
 * Plugin entry point for HytaleZombie.
 * 
 * In a real Hytale environment, this extends com.hypixel.hytale.server.core.plugin.JavaPlugin
 * and uses the Hytale API for config, commands, and events.
 * For development/testing, we use a simplified base class.
 */
public class HytaleZombiePlugin {

    private static HytaleZombieConfig config;
    private final Logger logger;

    // Core managers
    private RoundManager roundManager;
    private PlayerDataManager playerDataManager;
    private BarrierManager barrierManager;
    private SpawnManager spawnManager;

    public HytaleZombiePlugin() {
        this.logger = Logger.getLogger(getClass().getName());
        this.config = new HytaleZombieConfig();
    }

    /**
     * Called during plugin initialization.
     * Equivalent to Hytale's setup() method.
     */
    public void initialize() {
        this.logger.info("HytaleZombie is initializing...");

        // Initialize managers
        this.roundManager = new RoundManager(this);
        this.playerDataManager = new PlayerDataManager();
        this.barrierManager = new BarrierManager();

        // Register commands
        this.registerCommand(new HytaleZombieCommand(this));

        // Register event listeners
        this.registerEventListener(PlayerConnectionListener.getInstance());

        this.logger.info("HytaleZombie initialized successfully!");
    }

    /**
     * Stub: would register a command with Hytale's command system.
     */
    public void registerCommand(HytaleZombieCommand command) {
        this.logger.info("Command registered: " + command.getClass().getSimpleName());
    }

    /**
     * Stub: would register an event listener with Hytale's event system.
     */
    public void registerEventListener(PlayerConnectionListener listener) {
        this.logger.info("Event listener registered");
    }

    public Logger getLogger() {
        return logger;
    }

    public static HytaleZombieConfig getPluginConfig() {
        return config;
    }

    public RoundManager getRoundManager() {
        return roundManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public BarrierManager getBarrierManager() {
        return barrierManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public void setSpawnManager(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    public void setRoundManager(RoundManager roundManager) {
        this.roundManager = roundManager;
    }

    public void setPlayerDataManager(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    public void setBarrierManager(BarrierManager barrierManager) {
        this.barrierManager = barrierManager;
    }
}
