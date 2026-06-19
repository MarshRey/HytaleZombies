package dev.hytalezombie.manager;

import dev.hytalezombie.model.DoorArea;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.model.MapZone;
import dev.hytalezombie.model.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages all map zones, their connectivity, door states, and door areas.
 * Zones represent areas of the map that can be locked/unlocked.
 *
 * <p>Doors between zones use {@link DoorArea} bounding boxes for player
 * crossing detection. When a player's position enters a door area,
 * they transition into the connected zone. Zone occupancy is tracked
 * automatically based on which zones players are in.</p>
 */
public class ZoneManager {

    private static final Logger LOGGER = Logger.getLogger(ZoneManager.class.getName());

    private final Map<String, MapZone> zones;
    private final String startingZoneId;

    public ZoneManager(@Nonnull String startingZoneId) {
        this.zones = new ConcurrentHashMap<>();
        this.startingZoneId = startingZoneId;

        MapZone spawnZone = new MapZone(startingZoneId, "Spawn Room", 0);
        spawnZone.setUnlocked(true);
        zones.put(startingZoneId, spawnZone);
    }

    public void registerZone(@Nonnull MapZone zone) {
        zones.put(zone.getZoneId(), zone);
        LOGGER.log(Level.INFO, "Registered zone: {0}", zone);
    }

    @Nullable
    public MapZone getZone(@Nonnull String zoneId) {
        return zones.get(zoneId);
    }

    public void connectZones(@Nonnull String zoneIdA, @Nonnull String zoneIdB) {
        MapZone zoneA = zones.get(zoneIdA);
        MapZone zoneB = zones.get(zoneIdB);
        if (zoneA != null && zoneB != null) {
            zoneA.addConnectedZone(zoneIdB);
            zoneB.addConnectedZone(zoneIdA);
            LOGGER.log(Level.FINE, "Connected zones: {0} <-> {1}", new Object[]{zoneIdA, zoneIdB});
        }
    }

    public boolean unlockZone(@Nonnull String playerId, @Nonnull String zoneId,
                              @Nonnull PlayerDataManager playerDataManager) {
        MapZone zone = zones.get(zoneId);
        if (zone == null) return false;
        if (zone.isUnlocked()) return true;

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
        if (!playerDataManager.hasEnoughPoints(playerId, zone.getDoorCost())) {
            return false;
        }
        playerDataManager.getOrCreatePlayerData(playerId).deductPoints(zone.getDoorCost());
        zone.setUnlocked(true);
        LOGGER.log(Level.INFO, "Zone unlocked: {0} by player {1}",
                new Object[]{zone.getDisplayName(), playerId});
        return true;
    }

    public boolean isZoneUnlocked(@Nonnull String zoneId) {
        MapZone zone = zones.get(zoneId);
        return zone != null && zone.isUnlocked();
    }

    @Nonnull
    public List<MapZone> getUnlockedZones() {
        List<MapZone> unlocked = new ArrayList<>();
        for (MapZone zone : zones.values()) {
            if (zone.isUnlocked()) unlocked.add(zone);
        }
        return unlocked;
    }

    @Nonnull
    public Collection<MapZone> getAllZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    @Nonnull
    public String getStartingZoneId() {
        return startingZoneId;
    }

    // ==================== DOOR AREA MANAGEMENT ====================

    /**
     * Sets a door area between two connected zones using two corner points.
     * The AABB is computed from min/max of the two points — order doesn't matter.
     * "Stand at one side of the doorway, record position; stand at the
     * other side, record position." The resulting box can be any width/height/depth.
     *
     * @param zoneIdA first zone
     * @param zoneIdB second zone
     * @param corner1 one corner of the door area
     * @param corner2 opposite corner of the door area
     * @throws IllegalArgumentException if zones are not found or not connected
     */
    public void setDoorArea(@Nonnull String zoneIdA, @Nonnull String zoneIdB,
                            @Nonnull Vector3f corner1, @Nonnull Vector3f corner2) {
        MapZone zoneA = zones.get(zoneIdA);
        MapZone zoneB = zones.get(zoneIdB);
        if (zoneA == null) throw new IllegalArgumentException("Zone not found: " + zoneIdA);
        if (zoneB == null) throw new IllegalArgumentException("Zone not found: " + zoneIdB);
        if (!zoneA.getConnectedZoneIds().contains(zoneIdB)) {
            throw new IllegalArgumentException(
                "Zones '" + zoneIdA + "' and '" + zoneIdB + "' are not connected. "
                + "Call connectZones() first.");
        }

        DoorArea area = DoorArea.fromCorners(corner1, corner2);
        zoneA.setDoorArea(zoneIdB, area);
        zoneB.setDoorArea(zoneIdA, area);
        LOGGER.log(Level.INFO, "Door area set: {0} <-> {1} — {2}",
                new Object[]{zoneIdA, zoneIdB, area});
    }

    /**
     * Gets the door area between two connected zones.
     */
    @Nullable
    public DoorArea getDoorArea(@Nonnull String zoneIdA, @Nonnull String zoneIdB) {
        MapZone zone = zones.get(zoneIdA);
        return zone != null ? zone.getDoorArea(zoneIdB) : null;
    }

    /**
     * Checks if a player's position is inside a door area in their current zone,
     * returning the zone they would transition into.
     *
     * @param currentZoneId  zone the player is currently in
     * @param playerPosition player's world-space position
     * @return target zone ID if crossing a door, or null
     */
    @Nullable
    public String checkDoorCrossing(@Nonnull String currentZoneId, @Nonnull Vector3f playerPosition) {
        MapZone currentZone = zones.get(currentZoneId);
        if (currentZone == null) return null;
        String targetZoneId = currentZone.checkDoorCrossing(playerPosition);
        if (targetZoneId != null) {
            MapZone targetZone = zones.get(targetZoneId);
            if (targetZone != null && targetZone.isUnlocked()) {
                return targetZoneId;
            }
        }
        return null;
    }

    /**
     * Finds which zone a player is in based on their position relative to door areas.
     */
    @Nonnull
    public String findPlayerZone(@Nonnull Vector3f position) {
        for (MapZone zone : zones.values()) {
            String crossedZone = zone.checkDoorCrossing(position);
            if (crossedZone != null) {
                MapZone target = zones.get(crossedZone);
                if (target != null && target.isUnlocked()) {
                    return crossedZone;
                }
                return zone.getZoneId();
            }
        }
        return startingZoneId;
    }

    public boolean hasUnlockedPathTo(@Nonnull String targetZoneId) {
        if (targetZoneId.equals(startingZoneId)) return true;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        for (MapZone zone : zones.values()) {
            if (zone.isUnlocked()) {
                queue.add(zone.getZoneId());
                visited.add(zone.getZoneId());
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(targetZoneId)) return true;
            MapZone currentZone = zones.get(current);
            if (currentZone != null) {
                for (String neighbor : currentZone.getConnectedZoneIds()) {
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

    public void resetAllZones() {
        for (MapZone zone : zones.values()) {
            zone.setUnlocked(zone.getZoneId().equals(startingZoneId));
        }
        LOGGER.log(Level.INFO, "All zones reset (only starting zone remains unlocked)");
    }

    public void clearAll() {
        zones.clear();
        MapZone spawnZone = new MapZone(startingZoneId, "Spawn Room", 0);
        spawnZone.setUnlocked(true);
        zones.put(startingZoneId, spawnZone);
    }

    // ==================== ZONE REMOVAL ====================

    /**
     * Removes a zone and cleans up all references to it.
     * <ul>
     *   <li>Removes door areas from connected zones that reference this zone</li>
     *   <li>Removes this zone's ID from connected zones' connection lists</li>
     *   <li>Removes spawn nodes in this zone from SpawnManager</li>
     *   <li>Marks the zone as unoccupied in SpawnManager</li>
     * </ul>
     *
     * <p>The starting zone cannot be removed.</p>
     *
     * @param zoneId      the zone to remove
     * @param spawnManager for cleaning up spawn nodes
     * @throws IllegalArgumentException if trying to remove the starting zone
     */
    public void removeZone(@Nonnull String zoneId, @Nonnull SpawnManager spawnManager) {
        if (zoneId.equals(startingZoneId)) {
            throw new IllegalArgumentException(
                "Cannot remove the starting zone '" + startingZoneId + "'.");
        }

        MapZone zone = zones.remove(zoneId);
        if (zone == null) {
            LOGGER.log(Level.WARNING, "Zone not found for removal: {0}", zoneId);
            return;
        }

        // Clean up references in all connected zones
        for (String connectedId : zone.getConnectedZoneIds()) {
            MapZone connected = zones.get(connectedId);
            if (connected != null) {
                connected.removeConnectedZone(zoneId);
                LOGGER.log(Level.FINE, "Cleaned connection and door area from {0} → {1}",
                        new Object[]{connectedId, zoneId});
            }
        }

        // Remove spawn nodes and occupancy for this zone
        spawnManager.removeNodesInZone(zoneId);
        spawnManager.markZoneUnoccupied(zoneId);

        LOGGER.log(Level.INFO, "Zone removed: {0} ({1}). Cleaned up {2} door references.",
                new Object[]{zoneId, zone.getDisplayName(), zone.getConnectedZoneIds().size()});
    }
}