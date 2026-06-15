package dev.hytalezombie.manager;

import dev.hytalezombie.model.Barrier;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages debug mode and in-world visualizations for testing.
 * When debug mode is enabled, spawn node positions, barrier health,
 * and zone boundaries can be rendered as particles or holograms.
 * <p>
 * In the current logical implementation, this primarily logs state
 * information. When integrated with Hytale's entity/particle system,
 * it would spawn visual indicators in the world.
 */
public class DebugManager {

    private static final Logger LOGGER = Logger.getLogger(DebugManager.class.getName());

    private boolean debugMode;

    public DebugManager() {
        this.debugMode = false;
    }

    /**
     * Toggles debug mode on/off.
     * @return the new state
     */
    public boolean toggle() {
        debugMode = !debugMode;
        if (debugMode) {
            LOGGER.log(Level.INFO, "Debug mode ENABLED - Spawn nodes and barriers will be visualized.");
        } else {
            LOGGER.log(Level.INFO, "Debug mode DISABLED - Visualizations cleared.");
        }
        return debugMode;
    }

    /**
     * Sets debug mode to a specific state.
     */
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        if (!enabled) {
            LOGGER.log(Level.INFO, "Debug mode cleared.");
        }
    }

    /**
     * Returns whether debug mode is currently active.
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Logs all spawn node positions and their zone assignments.
     * In a real Hytale implementation, this would spawn particles
     * at each position (green for active zones, gray for inactive).
     */
    public void visualizeSpawnNodes(SpawnManager spawnManager) {
        if (!debugMode) {
            LOGGER.log(Level.FINE, "Debug mode is off - skipping spawn node visualization.");
            return;
        }

        LOGGER.log(Level.INFO, "=== Spawn Node Visualization ===");
        LOGGER.log(Level.INFO, "Total spawn nodes: {0}", spawnManager.getTotalSpawnCount());
        LOGGER.log(Level.INFO, "Active (occupied) zones: {0}", spawnManager.getOccupiedZones());

        for (String zoneId : spawnManager.getZoneIds()) {
            boolean isActive = spawnManager.getOccupiedZones().contains(zoneId);
            String status = isActive ? "ACTIVE" : "inactive";

            LOGGER.log(Level.INFO, "  Zone '{0}' ({1}):", new Object[]{zoneId, status});
            for (SpawnNode node : spawnManager.getNodesInZone(zoneId)) {
                LOGGER.log(Level.INFO, "    [Spawn] pos={0} radius={1}",
                        new Object[]{node.getPosition(), node.getSpawnRadius()});
            }
        }
        LOGGER.log(Level.INFO, "=== End Spawn Node Visualization ===");
    }

    /**
     * Logs all barrier states.
     * In a real Hytale implementation, this would show holograms
     * above each barrier with current durability/state.
     */
    public void visualizeBarriers(BarrierManager barrierManager) {
        if (!debugMode) return;

        LOGGER.log(Level.INFO, "=== Barrier Visualization ===");
        // BarrierManager doesn't expose a list-all method, but we'd iterate
        // all registered barriers if available
        LOGGER.log(Level.INFO, "  (Barrier visualization would show holograms in-game)");
        LOGGER.log(Level.INFO, "=== End Barrier Visualization ===");
    }
}
