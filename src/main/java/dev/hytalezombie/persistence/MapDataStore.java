package dev.hytalezombie.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.hytalezombie.manager.BarrierManager;
import dev.hytalezombie.manager.ZoneManager;
import dev.hytalezombie.model.Barrier;
import dev.hytalezombie.model.DoorArea;
import dev.hytalezombie.model.MapZone;
import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.model.Vector3i;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists the full map layout (spawn nodes, zones, doors, barriers) to a
 * single JSON file so a map can survive server restarts.
 */
public class MapDataStore {

    private static final Logger LOGGER = Logger.getLogger(MapDataStore.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public MapDataStore(@Nonnull Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Saves all map data to disk.
     */
    public void save(@Nonnull SpawnManager spawnManager,
                     @Nonnull ZoneManager zoneManager,
                     @Nonnull BarrierManager barrierManager) {
        MapData data = new MapData();

        // Spawn nodes
        data.spawnNodes = new LinkedHashMap<>();
        for (String zoneId : spawnManager.getZoneIds()) {
            List<SerializedSpawnNode> nodes = new ArrayList<>();
            for (SpawnNode node : spawnManager.getNodesInZone(zoneId)) {
                nodes.add(new SerializedSpawnNode(
                    node.getPosition().x(),
                    node.getPosition().y(),
                    node.getPosition().z(),
                    node.getSpawnRadius()
                ));
            }
            data.spawnNodes.put(zoneId, nodes);
        }
        data.occupiedZones = new LinkedHashSet<>(spawnManager.getOccupiedZones());

        // Zones and doors
        data.zones = new ArrayList<>();
        for (MapZone zone : zoneManager.getAllZones()) {
            SerializedZone sz = new SerializedZone();
            sz.id = zone.getZoneId();
            sz.name = zone.getDisplayName();
            sz.cost = zone.getDoorCost();
            sz.unlocked = zone.isUnlocked();
            sz.connections = new ArrayList<>(zone.getConnectedZoneIds());
            sz.doors = new LinkedHashMap<>();
            for (Map.Entry<String, DoorArea> entry : zone.getDoorAreas().entrySet()) {
                DoorArea area = entry.getValue();
                sz.doors.put(entry.getKey(), new SerializedDoorArea(
                    area.minX(), area.minY(), area.minZ(),
                    area.maxX(), area.maxY(), area.maxZ()
                ));
            }
            data.zones.add(sz);
        }

        // Barriers
        data.barriers = new ArrayList<>();
        for (MapZone zone : zoneManager.getAllZones()) {
            for (Barrier barrier : barrierManager.getBarriersInZone(zone.getZoneId())) {
                data.barriers.add(new SerializedBarrier(
                    barrier.getZoneId(),
                    barrier.getBlockPosition().x(),
                    barrier.getBlockPosition().y(),
                    barrier.getBlockPosition().z()
                ));
            }
        }

        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
            LOGGER.log(Level.INFO, "Saved map data to {0}", filePath);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save map data to {0}: {1}",
                new Object[]{filePath, e.getMessage()});
        }
    }

    /**
     * Loads all map data from disk. Clears and repopulates the managers.
     */
    public void load(@Nonnull SpawnManager spawnManager,
                     @Nonnull ZoneManager zoneManager,
                     @Nonnull BarrierManager barrierManager) {
        if (!Files.exists(filePath)) {
            LOGGER.log(Level.INFO, "No map data file found at {0} — starting fresh.", filePath);
            return;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Type type = new TypeToken<MapData>() {}.getType();
            MapData data = GSON.fromJson(reader, type);
            if (data == null) return;

            // Clear existing state
            spawnManager.clearAllNodes();
            zoneManager.clearAll();
            barrierManager.clearAll();

            // Load zones first so spawn/barrier validation works
            if (data.zones != null) {
                for (SerializedZone sz : data.zones) {
                    MapZone zone = new MapZone(sz.id, sz.name, sz.cost);
                    zone.setUnlocked(sz.unlocked);
                    zoneManager.registerZone(zone);
                }
                // Restore connections after all zones exist
                for (SerializedZone sz : data.zones) {
                    for (String connectedId : sz.connections) {
                        zoneManager.connectZones(sz.id, connectedId);
                    }
                }
                // Restore door areas after connections exist
                for (SerializedZone sz : data.zones) {
                    if (sz.doors != null) {
                        for (Map.Entry<String, SerializedDoorArea> entry : sz.doors.entrySet()) {
                            SerializedDoorArea sd = entry.getValue();
                            try {
                                zoneManager.setDoorArea(sz.id, entry.getKey(),
                                    new Vector3f(sd.minX, sd.minY, sd.minZ),
                                    new Vector3f(sd.maxX, sd.maxY, sd.maxZ));
                            } catch (IllegalArgumentException e) {
                                LOGGER.log(Level.WARNING, "Skipping invalid door area on load: {0}", e.getMessage());
                            }
                        }
                    }
                }
            }

            // Load spawn nodes
            if (data.spawnNodes != null) {
                for (Map.Entry<String, List<SerializedSpawnNode>> entry : data.spawnNodes.entrySet()) {
                    String zoneId = entry.getKey();
                    for (SerializedSpawnNode s : entry.getValue()) {
                        spawnManager.registerSpawnNode(new SpawnNode(
                            zoneId,
                            new Vector3f(s.x, s.y, s.z),
                            s.radius
                        ));
                    }
                }
            }
            if (data.occupiedZones != null) {
                for (String zoneId : data.occupiedZones) {
                    spawnManager.markZoneOccupied(zoneId);
                }
            }

            // Load barriers
            if (data.barriers != null) {
                for (SerializedBarrier sb : data.barriers) {
                    barrierManager.registerBarrier(new Barrier(sb.zoneId,
                        new Vector3i(sb.x, sb.y, sb.z)));
                }
            }

            LOGGER.log(Level.INFO, "Loaded map data from {0}", filePath);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load map data from {0}: {1}",
                new Object[]{filePath, e.getMessage()});
        }
    }

    // ==================== JSON DTOs ====================

    @SuppressWarnings("unused")
    private static final class MapData {
        Map<String, List<SerializedSpawnNode>> spawnNodes;
        Set<String> occupiedZones;
        List<SerializedZone> zones;
        List<SerializedBarrier> barriers;
    }

    @SuppressWarnings("unused")
    private static final class SerializedSpawnNode {
        float x, y, z, radius;
        SerializedSpawnNode(float x, float y, float z, float radius) {
            this.x = x; this.y = y; this.z = z; this.radius = radius;
        }
    }

    @SuppressWarnings("unused")
    private static final class SerializedZone {
        String id;
        String name;
        int cost;
        boolean unlocked;
        List<String> connections;
        Map<String, SerializedDoorArea> doors;
    }

    @SuppressWarnings("unused")
    private static final class SerializedDoorArea {
        float minX, minY, minZ, maxX, maxY, maxZ;
        SerializedDoorArea(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }

    @SuppressWarnings("unused")
    private static final class SerializedBarrier {
        String zoneId;
        int x, y, z;
        SerializedBarrier(String zoneId, int x, int y, int z) {
            this.zoneId = zoneId; this.x = x; this.y = y; this.z = z;
        }
    }
}
