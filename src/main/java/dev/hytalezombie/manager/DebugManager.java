package dev.hytalezombie.manager;

import dev.hytalezombie.HytaleZombiePlugin;
import dev.hytalezombie.model.Barrier;
import dev.hytalezombie.model.DoorArea;
import dev.hytalezombie.model.MapZone;
import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.model.Vector3i;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;
import dev.hytalezombie.util.WorldMarkerUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages debug mode and in-world visualizations for testing.
 * When debug mode is enabled, spawn node positions, barrier positions,
 * and zone door boundaries are rendered as temporary blocks in the world.
 */
public class DebugManager {

    private static final Logger LOGGER = Logger.getLogger(DebugManager.class.getName());

    public enum DebugLayer {
        SPAWNS,
        BARRIERS,
        ZONES
    }

    private final Set<DebugLayer> activeLayers;
    private final WorldMarkerUtil markerUtil;

    public DebugManager() {
        this.activeLayers = EnumSet.noneOf(DebugLayer.class);
        this.markerUtil = new WorldMarkerUtil();
    }

    /**
     * Toggles all debug layers on or off.
     * @return true if any layer is now active
     */
    public boolean toggleAll() {
        if (activeLayers.isEmpty()) {
            activeLayers.addAll(EnumSet.allOf(DebugLayer.class));
            LOGGER.log(Level.INFO, "Debug mode ENABLED - all layers active.");
        } else {
            activeLayers.clear();
            LOGGER.log(Level.INFO, "Debug mode DISABLED - all layers cleared.");
        }
        return !activeLayers.isEmpty();
    }

    /**
     * Toggles a single debug layer on or off.
     * @param layerName one of "spawns", "barriers", "zones"
     * @return true if the layer is now active
     */
    public boolean toggleLayer(String layerName) {
        DebugLayer layer;
        try {
            layer = DebugLayer.valueOf(layerName.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Unknown debug layer: {0}", layerName);
            return false;
        }

        if (activeLayers.contains(layer)) {
            activeLayers.remove(layer);
            LOGGER.log(Level.INFO, "Debug layer {0} DISABLED.", layer);
        } else {
            activeLayers.add(layer);
            LOGGER.log(Level.INFO, "Debug layer {0} ENABLED.", layer);
        }
        return activeLayers.contains(layer);
    }

    /**
     * Sets whether a specific layer is active.
     */
    public void setLayerActive(DebugLayer layer, boolean active) {
        if (active) {
            activeLayers.add(layer);
        } else {
            activeLayers.remove(layer);
        }
    }

    /**
     * Returns whether any debug layer is currently active.
     */
    public boolean isDebugMode() {
        return !activeLayers.isEmpty();
    }

    /**
     * Returns whether a specific layer is active.
     */
    public boolean isLayerActive(DebugLayer layer) {
        return activeLayers.contains(layer);
    }

    /**
     * Refreshes in-world markers to match the current map state.
     * Called automatically after map mutations.
     */
    public void refreshMarkers(HytaleZombiePlugin plugin) {
        clearMarkers(plugin);
        if (activeLayers.isEmpty()) {
            return;
        }

        if (activeLayers.contains(DebugLayer.SPAWNS)) {
            visualizeSpawnNodes(plugin);
        }
        if (activeLayers.contains(DebugLayer.BARRIERS)) {
            visualizeBarriers(plugin);
        }
        if (activeLayers.contains(DebugLayer.ZONES)) {
            visualizeZones(plugin);
        }
    }

    /**
     * Clears all in-world debug markers.
     */
    public void clearMarkers(HytaleZombiePlugin plugin) {
        markerUtil.clearAllMarkers(plugin.getGameSession().getWorld());
    }

    /**
     * Places temporary marker blocks at every spawn node.
     * Active zones use a bright center marker; inactive zones are still shown
     * but with a different material.
     */
    public void visualizeSpawnNodes(HytaleZombiePlugin plugin) {
        SpawnManager spawnManager = plugin.getSpawnManager();
        LOGGER.log(Level.INFO, "=== Spawn Node Visualization ===");
        LOGGER.log(Level.INFO, "Total spawn nodes: {0}", spawnManager.getTotalSpawnCount());

        for (String zoneId : spawnManager.getZoneIds()) {
            boolean isActive = spawnManager.getOccupiedZones().contains(zoneId);
            String status = isActive ? "ACTIVE" : "inactive";
            LOGGER.log(Level.INFO, "  Zone '{0}' ({1}):", new Object[]{zoneId, status});

            for (SpawnNode node : spawnManager.getNodesInZone(zoneId)) {
                Vector3f pos = node.getPosition();
                Vector3i center = new Vector3i((int) pos.x(), (int) pos.y(), (int) pos.z());
                LOGGER.log(Level.INFO, "    [Spawn] pos={0} radius={1}",
                    new Object[]{node.getPosition(), node.getSpawnRadius()});

                markerUtil.placeMarker(plugin.getGameSession().getWorld(),
                    WorldMarkerUtil.MarkerType.SPAWN_CENTER, center);

                // Outline the radius on the same Y level
                float radius = node.getSpawnRadius();
                if (radius > 0) {
                    List<Vector3i> outline = new ArrayList<>();
                    int steps = Math.max(8, (int) (radius * 8));
                    for (int i = 0; i < steps; i++) {
                        double angle = 2 * Math.PI * i / steps;
                        int ox = (int) Math.round(pos.x() + Math.cos(angle) * radius);
                        int oz = (int) Math.round(pos.z() + Math.sin(angle) * radius);
                        outline.add(new Vector3i(ox, center.y(), oz));
                    }
                    markerUtil.placeMarkers(plugin.getGameSession().getWorld(),
                        WorldMarkerUtil.MarkerType.SPAWN_RADIUS, outline);
                }
            }
        }
        LOGGER.log(Level.INFO, "=== End Spawn Node Visualization ===");
    }

    /**
     * Places temporary marker blocks at every barrier position.
     */
    public void visualizeBarriers(HytaleZombiePlugin plugin) {
        BarrierManager barrierManager = plugin.getBarrierManager();
        LOGGER.log(Level.INFO, "=== Barrier Visualization ===");
        for (String zoneId : barrierManager.getAllZoneIds()) {
            for (Barrier barrier : barrierManager.getBarriersInZone(zoneId)) {
                Vector3i pos = barrier.getBlockPosition();
                LOGGER.log(Level.INFO, "  [Barrier] zone={0} pos={1} state={2} durability={3}",
                    new Object[]{zoneId, pos, barrier.getState(), barrier.getDurability()});
                markerUtil.placeMarker(plugin.getGameSession().getWorld(),
                    WorldMarkerUtil.MarkerType.BARRIER, pos);
            }
        }
        LOGGER.log(Level.INFO, "=== End Barrier Visualization ===");
    }

    /**
     * Places temporary marker blocks at zone door area corners and edges.
     */
    public void visualizeZones(HytaleZombiePlugin plugin) {
        ZoneManager zoneManager = plugin.getZoneManager();
        LOGGER.log(Level.INFO, "=== Zone/Door Visualization ===");
        for (MapZone zone : zoneManager.getAllZones()) {
            LOGGER.log(Level.INFO, "  Zone '{0}' ({1}) unlocked={2} doors={3}",
                new Object[]{zone.getZoneId(), zone.getDisplayName(), zone.isUnlocked(), zone.getDoorAreas().size()});

            for (Map.Entry<String, DoorArea> entry : zone.getDoorAreas().entrySet()) {
                DoorArea area = entry.getValue();
                LOGGER.log(Level.INFO, "    Door to '{0}': {1}",
                    new Object[]{entry.getKey(), area});

                List<Vector3i> doorMarkers = new ArrayList<>();
                for (float x = area.minX(); x <= area.maxX(); x++) {
                    for (float y = area.minY(); y <= area.maxY(); y++) {
                        for (float z = area.minZ(); z <= area.maxZ(); z++) {
                            doorMarkers.add(new Vector3i((int) x, (int) y, (int) z));
                        }
                    }
                }
                markerUtil.placeMarkers(plugin.getGameSession().getWorld(),
                    WorldMarkerUtil.MarkerType.DOOR, doorMarkers);
            }
        }
        LOGGER.log(Level.INFO, "=== End Zone/Door Visualization ===");
    }
}
