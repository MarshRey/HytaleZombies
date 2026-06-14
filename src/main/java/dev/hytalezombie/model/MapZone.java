package dev.hytalezombie.model;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a zone/region in the map.
 * Each zone has its own set of spawn nodes, doors leading to other zones,
 * and a point cost to enter.
 */
public class MapZone {

    private final String zoneId;
    private final String displayName;
    private final int doorCost;
    private final Set<String> connectedZoneIds;
    private boolean isUnlocked;

    public MapZone(@Nonnull String zoneId, @Nonnull String displayName, int doorCost) {
        this.zoneId = zoneId;
        this.displayName = displayName;
        this.doorCost = doorCost;
        this.connectedZoneIds = new HashSet<>();
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
        return "MapZone{id='" + zoneId + "', name='" + displayName + "', cost=" + doorCost + "}";
    }
}
