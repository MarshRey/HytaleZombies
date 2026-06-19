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
 * <p>Doors between zones are tracked via {@link #doorAreas}, which maps
 * a connected zone ID to a {@link DoorArea} defining the rectangular
 * region players cross through. Door areas can be any width/height.</p>
 */
public class MapZone {

    private final String zoneId;
    private final String displayName;
    private final int doorCost;
    private final Set<String> connectedZoneIds;
    private final Map<String, DoorArea> doorAreas;
    private boolean isUnlocked;

    public MapZone(@Nonnull String zoneId, @Nonnull String displayName, int doorCost) {
        this.zoneId = zoneId;
        this.displayName = displayName;
        this.doorCost = doorCost;
        this.connectedZoneIds = new HashSet<>();
        this.doorAreas = new HashMap<>();
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

    /**
     * Removes a connection to another zone.
     */
    public void removeConnectedZone(@Nonnull String zoneId) {
        connectedZoneIds.remove(zoneId);
        doorAreas.remove(zoneId);
    }

    @Nonnull
    public Set<String> getConnectedZoneIds() {
        return Set.copyOf(connectedZoneIds);
    }

    // ==================== DOOR AREAS ====================

    /**
     * Sets the door area leading to a connected zone.
     * Both zones must be connected for the door to be valid.
     *
     * @param connectedZoneId the zone this door leads to
     * @param area            the bounding box of the door
     * @throws IllegalArgumentException if the zones are not connected
     */
    public void setDoorArea(@Nonnull String connectedZoneId, @Nonnull DoorArea area) {
        if (!connectedZoneIds.contains(connectedZoneId)) {
            throw new IllegalArgumentException(
                "Zone '" + connectedZoneId + "' is not connected to zone '" + zoneId + "'. "
                + "Call connectZones() first.");
        }
        doorAreas.put(connectedZoneId, area);
    }

    /**
     * Gets the door area leading to a connected zone.
     *
     * @param connectedZoneId the target zone
     * @return the door area, or null if not set
     */
    @Nullable
    public DoorArea getDoorArea(@Nonnull String connectedZoneId) {
        return doorAreas.get(connectedZoneId);
    }

    /**
     * Returns all door areas for this zone.
     *
     * @return unmodifiable map of connectedZoneId → door area
     */
    @Nonnull
    public Map<String, DoorArea> getDoorAreas() {
        return Map.copyOf(doorAreas);
    }

    /**
     * Checks if a world-space position is inside any door area in this zone.
     *
     * @param position the position to check
     * @return the connected zone ID if a door was crossed, or null
     */
    @Nullable
    public String checkDoorCrossing(@Nonnull Vector3f position) {
        for (Map.Entry<String, DoorArea> entry : doorAreas.entrySet()) {
            if (entry.getValue().contains(position)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Removes the door area for a connected zone.
     */
    public void removeDoorArea(@Nonnull String connectedZoneId) {
        doorAreas.remove(connectedZoneId);
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
            + "', cost=" + doorCost + ", doors=" + doorAreas.size() + "}";
    }
}
