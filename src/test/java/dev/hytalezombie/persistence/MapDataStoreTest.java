package dev.hytalezombie.persistence;

import dev.hytalezombie.manager.BarrierManager;
import dev.hytalezombie.manager.ZoneManager;
import dev.hytalezombie.model.Barrier;
import dev.hytalezombie.model.MapZone;
import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.model.Vector3i;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MapDataStore")
class MapDataStoreTest {

    private Path dataFile;
    private MapDataStore store;
    private SpawnManager spawnManager;
    private ZoneManager zoneManager;
    private BarrierManager barrierManager;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        dataFile = tempDir.resolve("map_data.json");
        store = new MapDataStore(dataFile);
        spawnManager = new SpawnManager();
        zoneManager = new ZoneManager("spawn_room");
        barrierManager = new BarrierManager();
    }

    @Test
    @DisplayName("should save and load spawn nodes")
    void saveAndLoad_spawnNodes() {
        spawnManager.registerSpawnNode(new SpawnNode("spawn_room", new Vector3f(1, 2, 3), 4.0f));
        spawnManager.markZoneOccupied("spawn_room");

        store.save(spawnManager, zoneManager, barrierManager);

        SpawnManager loadedSpawn = new SpawnManager();
        ZoneManager loadedZone = new ZoneManager("spawn_room");
        BarrierManager loadedBarrier = new BarrierManager();
        store.load(loadedSpawn, loadedZone, loadedBarrier);

        assertEquals(1, loadedSpawn.getTotalSpawnCount());
        assertTrue(loadedSpawn.getOccupiedZones().contains("spawn_room"));
        SpawnNode node = loadedSpawn.getNodesInZone("spawn_room").get(0);
        assertEquals(1.0f, node.getPosition().x(), 0.001f);
        assertEquals(4.0f, node.getSpawnRadius(), 0.001f);
    }

    @Test
    @DisplayName("should save and load zones, connections, and doors")
    void saveAndLoad_zonesAndDoors() {
        MapZone room2 = new MapZone("room_2", "Room 2", 1000);
        zoneManager.registerZone(room2);
        zoneManager.connectZones("spawn_room", "room_2");
        zoneManager.setDoorArea("spawn_room", "room_2",
            new Vector3f(10, 64, -5), new Vector3f(12, 67, -5));

        store.save(spawnManager, zoneManager, barrierManager);

        SpawnManager loadedSpawn = new SpawnManager();
        ZoneManager loadedZone = new ZoneManager("spawn_room");
        BarrierManager loadedBarrier = new BarrierManager();
        store.load(loadedSpawn, loadedZone, loadedBarrier);

        MapZone loadedRoom2 = loadedZone.getZone("room_2");
        assertNotNull(loadedRoom2);
        assertEquals("Room 2", loadedRoom2.getDisplayName());
        assertEquals(1000, loadedRoom2.getDoorCost());
        assertTrue(loadedRoom2.getConnectedZoneIds().contains("spawn_room"));
        assertNotNull(loadedZone.getDoorArea("spawn_room", "room_2"));
    }

    @Test
    @DisplayName("should save and load barriers")
    void saveAndLoad_barriers() {
        barrierManager.registerBarrier(new Barrier("spawn_room", new Vector3i(5, 64, 7)));

        store.save(spawnManager, zoneManager, barrierManager);

        SpawnManager loadedSpawn = new SpawnManager();
        ZoneManager loadedZone = new ZoneManager("spawn_room");
        BarrierManager loadedBarrier = new BarrierManager();
        store.load(loadedSpawn, loadedZone, loadedBarrier);

        assertEquals(1, loadedBarrier.getBarriersInZone("spawn_room").size());
        Barrier barrier = loadedBarrier.getBarriersInZone("spawn_room").get(0);
        assertEquals(new Vector3i(5, 64, 7), barrier.getBlockPosition());
    }

    @Test
    @DisplayName("should do nothing when file does not exist")
    void load_missingFile() {
        assertFalse(dataFile.toFile().exists());
        store.load(spawnManager, zoneManager, barrierManager);
        assertEquals(0, spawnManager.getTotalSpawnCount());
    }

    @Test
    @DisplayName("should clear existing state before loading")
    void load_clearsExistingState() {
        spawnManager.registerSpawnNode(new SpawnNode("spawn_room", new Vector3f(0, 0, 0), 1.0f));
        store.save(spawnManager, zoneManager, barrierManager);

        spawnManager.registerSpawnNode(new SpawnNode("extra", new Vector3f(1, 1, 1), 1.0f));
        store.load(spawnManager, zoneManager, barrierManager);

        assertEquals(1, spawnManager.getTotalSpawnCount());
        assertFalse(spawnManager.getZoneIds().contains("extra"));
    }
}
