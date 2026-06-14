package dev.hytalezombie.spawn;

import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.HytaleZombiePlugin;
import dev.hytalezombie.config.HytaleZombieConfig;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Manages zombie spawn nodes and handles spawn logic.
 * Enemies spawn only from nodes within zones currently occupied by players.
 */
public class SpawnManager {

    private final HytaleZombiePlugin plugin;
    private final Map<String, List<SpawnNode>> zoneSpawnNodes;

    /**
     * Tracks which zones players are currently in.
     * Keyed by zone ID (e.g., "spawn_room", "room_2").
     */
    private final Set<String> occupiedZones;

    public SpawnManager(@Nonnull HytaleZombiePlugin plugin) {
        this.plugin = plugin;
        this.zoneSpawnNodes = new HashMap<>();
        this.occupiedZones = new HashSet<>();
    }

    /**
     * Registers a spawn node for a specific zone.
     */
    public void registerSpawnNode(@Nonnull SpawnNode node) {
        zoneSpawnNodes.computeIfAbsent(node.getZoneId(), k -> new ArrayList<>())
                      .add(node);
        plugin.getLogger().info("Registered spawn node: " + node);
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
     * Checks if a zone has any registered spawn nodes.
     */
    public boolean hasNodesInZone(@Nonnull String zoneId) {
        List<SpawnNode> nodes = zoneSpawnNodes.get(zoneId);
        return nodes != null && !nodes.isEmpty();
    }
}
