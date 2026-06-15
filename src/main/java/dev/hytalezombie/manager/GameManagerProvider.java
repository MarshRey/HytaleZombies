package dev.hytalezombie.manager;

import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.spawn.SpawnManager;

/**
 * Interface for providing access to game managers.
 * This allows tests to work without the full Hytale plugin dependency.
 */
public interface GameManagerProvider {
    HytaleZombieConfig getPluginConfig();
    RoundManager getRoundManager();
    PlayerDataManager getPlayerDataManager();
    BarrierManager getBarrierManager();
    SpawnManager getSpawnManager();
}
