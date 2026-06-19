package dev.hytalezombie.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a zone/region in the map.
 * Each zone has its own set of spawn nodes, doors leading to other zones,
 * and a point cost to enter.
 *
 * <p>Doors between zones are tracked via {@link #doorPositions}, which maps
 * a connected zone ID to the world-space position of the door leading there.
 * When a player walks within {@link #DOOR_CROSSING_RADIUS} blocks of a door,
 * they transition into the connected zone.</p>
 */
public class MapZone {

    /** How close a player must be to a door position to trigger a zone transition. */
    public static final float DOOR_CROSSING_RADIUS = 2.5f;

    private final String zoneId;
    private final String displayName;
    private final int doorCost;
    private final Set<String> connectedZoneIds;
    private final Map<String, Vector3f> doorPositions;
    private boolean isUnlocked;

    public MapZone(@Nonnull String zoneId, @Nonnull String displayName, int doorCost) {
        this.zoneId = zoneId;
        this.displayName = displayName;
        this.doorCost = doorCost;
        this.connectedZoneIds = new HashSet<>();
        this.doorPositions = new HashMap<>();
        this.isUnlocked = false;
    }

    @Nonnull
    public String getZoneId() {
        return zoneId;
    }

    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    public int getDoorCost() {
        return doorCost;
    }

    public boolean isUnlocked() {
        return isUnlocked;
    }

    public void setUnlocked(boolean unlocked) {
        isUnlocked = unlocked;
    }

    public void addConnectedZone(@Nonnull String zoneId) {
        connectedZoneIds.add(zoneId);
    }

    @Nonnull
    public Set<String> getConnectedZoneIds() {
        return Set.copyOf(connectedZoneIds);
    }

    // ==================== DOOR POSITIONS ====================

    /**
     * Sets the world-space position of the door leading to a connected zone.
     * Both this zone and the target zone must be connected for the door to be valid.
     *
     * @param connectedZoneId the zone this door leads to
     * @param position        the world-space position of the door
     */
    public void setDoorPosition(@Nonnull String connectedZoneId, @Nonnull Vector3f position) {
        if (!connectedZoneIds.contains(connectedZoneId)) {
            throw new IllegalArgumentException(
                "Zone '" + connectedZoneId + "' is not connected to zone '" + zoneId + "'. "
                + "Call connectZones() first.");
        }
        doorPositions.put(connectedZoneId, position);
    }

    /**
     * Gets the world-space position of the door leading to a connected zone.
     *
     * @param connectedZoneId the target zone
     * @return the door position, or null if not set
     */
    @Nullable
    public Vector3f getDoorPosition(@Nonnull String connectedZoneId) {
        return doorPositions.get(connectedZoneId);
    }

    /**
     * Returns all door positions for this zone.
     *
     * @return unmodifiable map of connectedZoneId → door position
     */
    @Nonnull
    public Map<String, Vector3f> getDoorPositions() {
        return Map.copyOf(doorPositions);
    }

    /**
     * Checks if a world-space position is within door-crossing range
     * of any door in this zone.
     *
     * @param position the position to check
     * @return the connected zone ID if a door was crossed, or null
     */
    @Nullable
    public String checkDoorCrossing(@Nonnull Vector3f position) {
        for (Map.Entry<String, Vector3f> entry : doorPositions.entrySet()) {
            float dx = position.x() - entry.getValue().x();
            float dy = position.y() - entry.getValue().y();
            float dz = position.z() - entry.getValue().z();
            float distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= DOOR_CROSSING_RADIUS * DOOR_CROSSING_RADIUS) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Removes the door position for a connected zone.
     */
    public void removeDoorPosition(@Nonnull String connectedZoneId) {
        doorPositions.remove(connectedZoneId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapZone mapZone = (MapZone) o;
        return zoneId.equals(mapZone.zoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zoneId);
    }

    @Override
    public String toString() {
        return "MapZone{id='" + zoneId + "', name='" + displayName
            + "', cost=" + doorCost + ", doors=" + doorPositions.size() + "}";
    }
}
