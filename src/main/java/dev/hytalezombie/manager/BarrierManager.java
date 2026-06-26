package dev.hytalezombie.manager;

import dev.hytalezombie.model.Vector3i;
import dev.hytalezombie.model.Barrier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Manages all window barriers in the map.
 * Barriers act as blockades that zombies must destroy before reaching players.
 */
public class BarrierManager {

    private final Map<Vector3i, Barrier> barriersByPosition;
    private final Map<String, List<Barrier>> barriersByZone;

    public BarrierManager() {
        this.barriersByPosition = new HashMap<>();
        this.barriersByZone = new HashMap<>();
    }

    /**
     * Registers a barrier at its block position.
     */
    public void registerBarrier(@Nonnull Barrier barrier) {
        barriersByPosition.put(barrier.getBlockPosition(), barrier);
        barriersByZone.computeIfAbsent(barrier.getZoneId(), k -> new ArrayList<>())
                      .add(barrier);
    }

    /**
     * Gets a barrier at a specific block position.
     */
    @Nullable
    public Barrier getBarrierAt(@Nonnull Vector3i position) {
        return barriersByPosition.get(position);
    }

    /**
     * Gets all barriers in a specific zone.
     */
    @Nonnull
    public List<Barrier> getBarriersInZone(@Nonnull String zoneId) {
        return barriersByZone.getOrDefault(zoneId, Collections.emptyList());
    }

    /**
     * Checks if a position has a barrier.
     */
    public boolean hasBarrierAt(@Nonnull Vector3i position) {
        return barriersByPosition.containsKey(position);
    }

    /**
     * Removes a barrier (when broken/destroyed).
     */
    public void removeBarrier(@Nonnull Vector3i position) {
        Barrier barrier = barriersByPosition.remove(position);
        if (barrier != null) {
            List<Barrier> zoneBarriers = barriersByZone.get(barrier.getZoneId());
            if (zoneBarriers != null) {
                zoneBarriers.remove(barrier);
            }
        }
    }

    /**
     * Returns all zone IDs that currently have barriers.
     */
    public Set<String> getAllZoneIds() {
        return Set.copyOf(barriersByZone.keySet());
    }

    /**
     * Clears all barriers (useful when resetting the match).
     */
    public void clearAll() {
        barriersByPosition.clear();
        barriersByZone.clear();
    }
}

