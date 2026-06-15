package dev.hytalezombie.spawn;

import dev.hytalezombie.model.Vector3f;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages zombie spawn nodes and handles spawn logic.
 * Enemies spawn only from nodes within zones currently occupied by players.
 */
public class SpawnManager {

    private static final Logger LOGGER = Logger.getLogger(SpawnManager.class.getName());

    private final Map<String, List<SpawnNode>> zoneSpawnNodes;

    /**
     * Tracks which zones players are currently in.
     * Keyed by zone ID (e.g., "spawn_room", "room_2").
     */
    private final Set<String> occupiedZones;

    public SpawnManager() {
        this.zoneSpawnNodes = new HashMap<>();
        this.occupiedZones = new HashSet<>();
    }

    /**
     * Registers a spawn node for a specific zone.
     */
    public void registerSpawnNode(@Nonnull SpawnNode node) {
        zoneSpawnNodes.computeIfAbsent(node.getZoneId(), k -> new ArrayList<>())
                      .add(node);
        LOGGER.log(Level.INFO, "Registered spawn node: {0}", node);
    }

    /**
     * Marks a zone as occupied by players.
     */
    public void markZoneOccupied(@Nonnull String zoneId) {
        occupiedZones.add(zoneId);
    }

    /**
     * Marks a zone as no longer occupied by players.
     */
    public void markZoneUnoccupied(@Nonnull String zoneId) {
        occupiedZones.remove(zoneId);
    }

    /**
     * Returns all spawn nodes for zones that players currently occupy.
     * This optimizes pathfinding by only spawning near players.
     */
    @Nonnull
    public List<SpawnNode> getActiveSpawnNodes() {
        List<SpawnNode> activeNodes = new ArrayList<>();
        for (String zoneId : occupiedZones) {
            List<SpawnNode> zoneNodes = zoneSpawnNodes.get(zoneId);
            if (zoneNodes != null) {
                activeNodes.addAll(zoneNodes);
            }
        }
        return activeNodes;
    }

    /**
     * Returns a random spawn node from the player-occupied zones.
     * Returns null if no spawn nodes are available.
     */
    public Optional<SpawnNode> getRandomSpawnNode() {
        List<SpawnNode> activeNodes = getActiveSpawnNodes();
        if (activeNodes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(activeNodes.get(new Random().nextInt(activeNodes.size())));
    }

    /**
     * Calculates a randomized spawn position within a node's radius.
     */
    @Nonnull
    public Vector3f getRandomizedPosition(@Nonnull SpawnNode node) {
        Random random = new Random();
        float offsetX = (random.nextFloat() - 0.5f) * 2 * node.getSpawnRadius();
        float offsetZ = (random.nextFloat() - 0.5f) * 2 * node.getSpawnRadius();

        return new Vector3f(
            node.getPosition().x() + offsetX,
            node.getPosition().y(),
            node.getPosition().z() + offsetZ
        );
    }

    /**
     * Clears all spawn node registrations.
     */
    public void clearAllNodes() {
        zoneSpawnNodes.clear();
        occupiedZones.clear();
    }

    /**
     * Removes all spawn nodes for a specific zone.
     * @param zoneId the zone whose spawns should be removed
     */
    public void removeNodesInZone(@Nonnull String zoneId) {
        zoneSpawnNodes.remove(zoneId);
        occupiedZones.remove(zoneId);
        LOGGER.log(Level.INFO, "Removed all spawn nodes in zone: {0}", zoneId);
    }

    /**
     * Returns all zone IDs that have registered spawn nodes.
     */
    @Nonnull
    public Set<String> getZoneIds() {
        return Collections.unmodifiableSet(zoneSpawnNodes.keySet());
    }

    /**
     * Returns all spawn nodes registered for a specific zone.
     * Returns an empty list if the zone has no nodes.
     */
    @Nonnull
    public List<SpawnNode> getNodesInZone(@Nonnull String zoneId) {
        return zoneSpawnNodes.getOrDefault(zoneId, Collections.emptyList());
    }

    /**
     * Returns all registered occupied zones (for debugging).
     */
    @Nonnull
    public Set<String> getOccupiedZones() {
        return Collections.unmodifiableSet(occupiedZones);
    }

    /**
     * Checks if a zone has any registered spawn nodes.
     */
    public boolean hasNodesInZone(@Nonnull String zoneId) {
        List<SpawnNode> nodes = zoneSpawnNodes.get(zoneId);
        return nodes != null && !nodes.isEmpty();
    }
}

