package dev.hytalezombie.manager;

import dev.hytalezombie.model.MapZone;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages all map zones, their connectivity, and door states.
 * Zones represent areas of the map that can be locked/unlocked.
 */
public class ZoneManager {

    private static final Logger LOGGER = Logger.getLogger(ZoneManager.class.getName());

    private final Map<String, MapZone> zones;
    private final String startingZoneId;

    public ZoneManager(@Nonnull String startingZoneId) {
        this.zones = new ConcurrentHashMap<>();
        this.startingZoneId = startingZoneId;

        // The starting zone is always unlocked
        MapZone spawnZone = new MapZone(startingZoneId, "Spawn Room", 0);
        spawnZone.setUnlocked(true);
        zones.put(startingZoneId, spawnZone);
    }

    /**
     * Registers a new zone in the map.
     */
    public void registerZone(@Nonnull MapZone zone) {
        zones.put(zone.getZoneId(), zone);
        LOGGER.log(Level.INFO, "Registered zone: {0}", zone);
    }

    /**
     * Gets a zone by its ID.
     */
    @Nullable
    public MapZone getZone(@Nonnull String zoneId) {
        return zones.get(zoneId);
    }

    /**
     * Connects two zones (bidirectional).
     */
    public void connectZones(@Nonnull String zoneIdA, @Nonnull String zoneIdB) {
        MapZone zoneA = zones.get(zoneIdA);
        MapZone zoneB = zones.get(zoneIdB);

        if (zoneA != null && zoneB != null) {
            zoneA.addConnectedZone(zoneIdB);
            zoneB.addConnectedZone(zoneIdA);
            LOGGER.log(Level.FINE, "Connected zones: {0} <-> {1}", new Object[]{zoneIdA, zoneIdB});
        }
    }

    /**
     * Attempts to unlock a zone by paying its door cost.
     * @param playerId the player paying
     * @param zoneId the zone to unlock
     * @param playerDataManager for point deduction
     * @return true if the zone was unlocked
     */
    public boolean unlockZone(@Nonnull String playerId, @Nonnull String zoneId,
                              @Nonnull PlayerDataManager playerDataManager) {
        MapZone zone = zones.get(zoneId);
        if (zone == null) return false;
        if (zone.isUnlocked()) return true;

        // Check if any connected zone is already unlocked (must have access path)
        boolean hasAccess = false;
        for (String connectedId : zone.getConnectedZoneIds()) {
            MapZone connected = zones.get(connectedId);
            if (connected != null && connected.isUnlocked()) {
                hasAccess = true;
                break;
            }
        }

        if (!hasAccess && !zoneId.equals(startingZoneId)) {
            LOGGER.log(Level.WARNING, "Cannot unlock {0}: no access from unlocked zones", zoneId);
            return false;
        }

        // Attempt payment
        if (!playerDataManager.hasEnoughPoints(playerId, zone.getDoorCost())) {
            return false;
        }

        playerDataManager.getOrCreatePlayerData(playerId).deductPoints(zone.getDoorCost());
        zone.setUnlocked(true);

        LOGGER.log(Level.INFO, "Zone unlocked: {0} by player {1}",
                new Object[]{zone.getDisplayName(), playerId});
        return true;
    }

    /**
     * Checks if a zone is unlocked.
     */
    public boolean isZoneUnlocked(@Nonnull String zoneId) {
        MapZone zone = zones.get(zoneId);
        return zone != null && zone.isUnlocked();
    }

    /**
     * Gets all unlocked zones.
     */
    @Nonnull
    public List<MapZone> getUnlockedZones() {
        List<MapZone> unlocked = new ArrayList<>();
        for (MapZone zone : zones.values()) {
            if (zone.isUnlocked()) unlocked.add(zone);
        }
        return unlocked;
    }

    /**
     * Gets all registered zones.
     */
    @Nonnull
    public Collection<MapZone> getAllZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    /**
     * Gets the starting zone ID.
     */
    @Nonnull
    public String getStartingZoneId() {
        return startingZoneId;
    }

    /**
     * Checks if a path exists from unlocked zones to a target zone
     * traversing only through unlocked zones.
     */
    public boolean hasUnlockedPathTo(@Nonnull String targetZoneId) {
        if (targetZoneId.equals(startingZoneId)) return true;

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        // BFS from all unlocked zones, traversing only through unlocked zones
        for (MapZone zone : zones.values()) {
            if (zone.isUnlocked()) {
                queue.add(zone.getZoneId());
                visited.add(zone.getZoneId());
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(targetZoneId)) return true;

            // Check if target is directly the current zone
            MapZone currentZone = zones.get(current);
            if (currentZone != null) {
                for (String neighbor : currentZone.getConnectedZoneIds()) {
                    // Only traverse through unlocked zones
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        MapZone neighborZone = zones.get(neighbor);
                        if (neighborZone != null && neighborZone.isUnlocked()) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Resets all zones to locked state (except starting zone).
     */
    public void resetAllZones() {
        for (MapZone zone : zones.values()) {
            zone.setUnlocked(zone.getZoneId().equals(startingZoneId));
        }
        LOGGER.log(Level.INFO, "All zones reset (only starting zone remains unlocked)");
    }

    /**
     * Removes all zones.
     */
    public void clearAll() {
        zones.clear();
        MapZone spawnZone = new MapZone(startingZoneId, "Spawn Room", 0);
        spawnZone.setUnlocked(true);
        zones.put(startingZoneId, spawnZone);
    }
}
