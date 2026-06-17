package dev.hytalezombie.spawn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.hytalezombie.model.Vector3f;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * Removes a single spawn node from a zone by its index.
     * @param zoneId the zone containing the node
     * @param index the index of the node to remove
     * @return true if the node was removed
     */
    public boolean removeSpawnNode(@Nonnull String zoneId, int index) {
        List<SpawnNode> nodes = zoneSpawnNodes.get(zoneId);
        if (nodes != null && index >= 0 && index < nodes.size()) {
            SpawnNode removed = nodes.remove(index);
            if (nodes.isEmpty()) {
                zoneSpawnNodes.remove(zoneId);
                occupiedZones.remove(zoneId);
            }
            LOGGER.log(Level.INFO, "Removed spawn node at index {0} from zone {1}: {2}",
                    new Object[]{index, zoneId, removed});
            return true;
        }
        return false;
    }

    /**
     * Returns the total number of registered spawn nodes across all zones.
     */
    public int getTotalSpawnCount() {
        return zoneSpawnNodes.values().stream()
                .mapToInt(List::size)
                .sum();
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

    // ==================== PERSISTENCE ====================

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** JSON-serializable representation of a single spawn node. */
    @SuppressWarnings("unused")
    private static final class SerializedSpawnNode {
        float x;
        float y;
        float z;
        float radius;

        SerializedSpawnNode(float x, float y, float z, float radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }

    /** Top-level JSON structure for spawn persistence. */
    @SuppressWarnings("unused")
    private static final class SpawnData {
        Map<String, List<SerializedSpawnNode>> zoneSpawnNodes;
        Set<String> occupiedZones;
    }

    /**
     * Saves all spawn nodes and occupied zones to a JSON file.
     * Called after any mutation command (setspawn, delspawn, clearspawns, markzone, unmarkzone).
     *
     * @param filePath path to the JSON file (e.g., run/hytalezombie_data/spawn_nodes.json)
     */
    public void saveToFile(@Nonnull Path filePath) {
        SpawnData data = new SpawnData();
        data.zoneSpawnNodes = new LinkedHashMap<>();
        data.occupiedZones = new LinkedHashSet<>(occupiedZones);

        for (Map.Entry<String, List<SpawnNode>> entry : zoneSpawnNodes.entrySet()) {
            List<SerializedSpawnNode> serialized = new ArrayList<>();
            for (SpawnNode node : entry.getValue()) {
                serialized.add(new SerializedSpawnNode(
                    node.getPosition().x(),
                    node.getPosition().y(),
                    node.getPosition().z(),
                    node.getSpawnRadius()
                ));
            }
            data.zoneSpawnNodes.put(entry.getKey(), serialized);
        }

        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
            LOGGER.log(Level.INFO, "Saved {0} spawn nodes across {1} zones to {2}",
                    new Object[]{getTotalSpawnCount(), zoneSpawnNodes.size(), filePath});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save spawn nodes to {0}: {1}",
                    new Object[]{filePath, e.getMessage()});
        }
    }

    /**
     * Loads spawn nodes and occupied zones from a JSON file.
     * Called once during plugin startup. If the file doesn't exist,
     * starts with an empty state (no nodes registered).
     *
     * @param filePath path to the JSON file (e.g., run/hytalezombie_data/spawn_nodes.json)
     */
    public void loadFromFile(@Nonnull Path filePath) {
        if (!Files.exists(filePath)) {
            LOGGER.log(Level.INFO, "No spawn data file found at {0} — starting with empty spawn nodes.", filePath);
            return;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            SpawnData data = GSON.fromJson(reader, SpawnData.class);
            if (data == null) return;

            if (data.zoneSpawnNodes != null) {
                for (Map.Entry<String, List<SerializedSpawnNode>> entry : data.zoneSpawnNodes.entrySet()) {
                    String zoneId = entry.getKey();
                    for (SerializedSpawnNode s : entry.getValue()) {
                        registerSpawnNode(new SpawnNode(
                            zoneId,
                            new Vector3f(s.x, s.y, s.z),
                            s.radius
                        ));
                    }
                }
            }

            if (data.occupiedZones != null) {
                occupiedZones.addAll(data.occupiedZones);
            }

            LOGGER.log(Level.INFO, "Loaded {0} spawn nodes across {1} zones from {2}",
                    new Object[]{getTotalSpawnCount(), zoneSpawnNodes.size(), filePath});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load spawn nodes from {0}: {1}",
                    new Object[]{filePath, e.getMessage()});
        }
    }
}

