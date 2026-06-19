package dev.hytalezombie.manager;

import dev.hytalezombie.model.MapZone;
import dev.hytalezombie.model.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages all map zones, their connectivity, door states, and door positions.
 * Zones represent areas of the map that can be locked/unlocked.
 *
 * <p>Doors between zones have world-space positions for player crossing detection.
 * When a player walks near a door position, they transition into the connected zone.
 * Zone occupancy is tracked automatically based on which zones players are in.</p>
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

    // ==================== DOOR POSITION MANAGEMENT ====================

    /**
     * Sets the world-space position of a door connecting two zones.
     * The door position is stored on both zones for bidirectional crossing detection.
     *
     * @param zoneIdA  the first zone
     * @param zoneIdB  the second zone
     * @param position the world-space position of the door between them
     * @throws IllegalArgumentException if the zones are not connected
     */
    public void setDoorPosition(@Nonnull String zoneIdA, @Nonnull String zoneIdB,
                                @Nonnull Vector3f position) {
        MapZone zoneA = zones.get(zoneIdA);
        MapZone zoneB = zones.get(zoneIdB);

        if (zoneA == null) {
            throw new IllegalArgumentException("Zone not found: " + zoneIdA);
        }
        if (zoneB == null) {
            throw new IllegalArgumentException("Zone not found: " + zoneIdB);
        }
        if (!zoneA.getConnectedZoneIds().contains(zoneIdB)) {
            throw new IllegalArgumentException(
                "Zones '" + zoneIdA + "' and '" + zoneIdB + "' are not connected. "
                + "Call connectZones() first.");
        }

        zoneA.setDoorPosition(zoneIdB, position);
        zoneB.setDoorPosition(zoneIdA, position);
        LOGGER.log(Level.INFO, "Door position set: {0} <-> {1} at {2}",
                new Object[]{zoneIdA, zoneIdB, position});
    }

    /**
     * Gets the door position between two connected zones.
     *
     * @param zoneIdA the zone the player is currently in
     * @param zoneIdB the connected zone
     * @return the door position, or null if not set
     */
    @Nullable
    public Vector3f getDoorPosition(@Nonnull String zoneIdA, @Nonnull String zoneIdB) {
        MapZone zone = zones.get(zoneIdA);
        return zone != null ? zone.getDoorPosition(zoneIdB) : null;
    }

    /**
     * Checks if a player's position is near a door in their current zone,
     * and returns the zone they would transition into.
     *
     * @param currentZoneId the zone the player is currently in
     * @param playerPosition the player's world-space position
     * @return the ID of the zone the player is crossing into, or null if no door crossed
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
     * Finds which zone a player is in based on their position relative to door positions.
     * This is used for initial zone assignment when a player joins.
     *
     * <p>Strategy: check all door positions across all zones. If the player is near
     * a door, they're in the zone on the "inside" side. If they're not near any door,
     * they default to the starting zone.</p>
     *
     * @param position the player's position
     * @return the zone ID the player is most likely in
     */
    @Nonnull
    public String findPlayerZone(@Nonnull Vector3f position) {
        // Check all zones for door proximity — if the player is near a door,
        // they just entered through it and are in that zone
        for (MapZone zone : zones.values()) {
            String crossedZone = zone.checkDoorCrossing(position);
            if (crossedZone != null) {
                // Player is near a door from this zone to crossedZone —
                // they're in crossedZone if it's unlocked, otherwise in this zone
                MapZone target = zones.get(crossedZone);
                if (target != null && target.isUnlocked()) {
                    return crossedZone;
                }
                return zone.getZoneId();
            }
        }
        // Default to starting zone
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
