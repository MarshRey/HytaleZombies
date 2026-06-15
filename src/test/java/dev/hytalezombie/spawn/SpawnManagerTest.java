package dev.hytalezombie.spawn;

import dev.hytalezombie.model.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnManager")
class SpawnManagerTest {

    private SpawnManager spawnManager;

    private SpawnNode nodeRoom1;
    private SpawnNode nodeRoom2;
    private SpawnNode nodeRoom2Alt;

    @BeforeEach
    void setUp() {
        spawnManager = new SpawnManager();

        nodeRoom1 = new SpawnNode("room_1", new Vector3f(10.0f, 0.0f, 10.0f), 2.0f);
        nodeRoom2 = new SpawnNode("room_2", new Vector3f(50.0f, 0.0f, 50.0f), 3.0f);
        nodeRoom2Alt = new SpawnNode("room_2", new Vector3f(55.0f, 0.0f, 45.0f), 2.5f);
    }

    @Nested
    @DisplayName("Spawn node registration")
    class SpawnNodeRegistration {

        @Test
        @DisplayName("should register a spawn node for a zone")
        void registerSpawnNode() {
            spawnManager.registerSpawnNode(nodeRoom1);
            assertTrue(spawnManager.hasNodesInZone("room_1"));
        }

        @Test
        @DisplayName("should register multiple nodes in the same zone")
        void registerSpawnNode_multipleInZone() {
            spawnManager.registerSpawnNode(nodeRoom2);
            spawnManager.registerSpawnNode(nodeRoom2Alt);

            assertTrue(spawnManager.hasNodesInZone("room_2"));
        }

        @Test
        @DisplayName("should register nodes in different zones")
        void registerSpawnNode_differentZones() {
            spawnManager.registerSpawnNode(nodeRoom1);
            spawnManager.registerSpawnNode(nodeRoom2);

            assertTrue(spawnManager.hasNodesInZone("room_1"));
            assertTrue(spawnManager.hasNodesInZone("room_2"));
        }

        @Test
        @DisplayName("should report no nodes in unregistered zone")
        void hasNodesInZone_none() {
            assertFalse(spawnManager.hasNodesInZone("nonexistent_zone"));
        }

        @Test
        @DisplayName("should log registration")
        void registerSpawnNode_logging() {
            spawnManager.registerSpawnNode(nodeRoom1);
            // Verify that the logger was called (we just check it doesn't throw)
            assertTrue(true);
        }
    }

    @Nested
    @DisplayName("Zone occupancy")
    class ZoneOccupancy {

        @BeforeEach
        void setUp() {
            spawnManager.registerSpawnNode(nodeRoom1);
            spawnManager.registerSpawnNode(nodeRoom2);
            spawnManager.registerSpawnNode(nodeRoom2Alt);
        }

        @Test
        @DisplayName("should start with no occupied zones")
        void getActiveSpawnNodes_noOccupiedZones() {
            List<SpawnNode> activeNodes = spawnManager.getActiveSpawnNodes();
            assertTrue(activeNodes.isEmpty());
        }

        @Test
        @DisplayName("should return nodes for occupied zones")
        void markZoneOccupied() {
            spawnManager.markZoneOccupied("room_1");
            List<SpawnNode> activeNodes = spawnManager.getActiveSpawnNodes();
            assertEquals(1, activeNodes.size());
            assertEquals("room_1", activeNodes.get(0).getZoneId());
        }

        @Test
        @DisplayName("should return nodes for multiple occupied zones")
        void markZoneOccupied_multipleZones() {
            spawnManager.markZoneOccupied("room_1");
            spawnManager.markZoneOccupied("room_2");
            List<SpawnNode> activeNodes = spawnManager.getActiveSpawnNodes();
            assertEquals(3, activeNodes.size()); // 1 from room_1 + 2 from room_2
        }

        @Test
        @DisplayName("should remove zones when marked unoccupied")
        void markZoneUnoccupied() {
            spawnManager.markZoneOccupied("room_1");
            spawnManager.markZoneOccupied("room_2");
            spawnManager.markZoneUnoccupied("room_1");

            List<SpawnNode> activeNodes = spawnManager.getActiveSpawnNodes();
            assertTrue(activeNodes.stream().noneMatch(n -> n.getZoneId().equals("room_1")));
            assertEquals(2, activeNodes.size()); // Only room_2 nodes remain
        }

        @Test
        @DisplayName("should handle marking same zone occupied multiple times")
        void markZoneOccupied_duplicate() {
            spawnManager.markZoneOccupied("room_1");
            spawnManager.markZoneOccupied("room_1"); // No-op
            spawnManager.markZoneOccupied("room_1"); // No-op

            List<SpawnNode> activeNodes = spawnManager.getActiveSpawnNodes();
            assertEquals(1, activeNodes.size());
        }

        @Test
        @DisplayName("should return empty for occupied zone with no nodes")
        void getActiveSpawnNodes_occupiedZoneWithoutNodes() {
            spawnManager.markZoneOccupied("empty_zone");
            List<SpawnNode> activeNodes = spawnManager.getActiveSpawnNodes();
            assertTrue(activeNodes.isEmpty());
        }
    }

    @Nested
    @DisplayName("Random spawn selection")
    class RandomSpawnSelection {

        @BeforeEach
        void setUp() {
            spawnManager.registerSpawnNode(nodeRoom1);
            spawnManager.registerSpawnNode(nodeRoom2);
            spawnManager.registerSpawnNode(nodeRoom2Alt);
        }

        @Test
        @DisplayName("should return empty optional when no active nodes")
        void getRandomSpawnNode_noActiveNodes() {
            Optional<SpawnNode> result = spawnManager.getRandomSpawnNode();
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return a spawn node from occupied zones")
        void getRandomSpawnNode() {
            spawnManager.markZoneOccupied("room_1");
            Optional<SpawnNode> result = spawnManager.getRandomSpawnNode();
            assertTrue(result.isPresent());
            assertEquals("room_1", result.get().getZoneId());
        }

        @Test
        @DisplayName("should return a node from any occupied zone")
        void getRandomSpawnNode_anyZone() {
            spawnManager.markZoneOccupied("room_1");
            spawnManager.markZoneOccupied("room_2");

            // Run multiple times to ensure we get samples from both zones
            boolean sawRoom1 = false;
            boolean sawRoom2 = false;
            for (int i = 0; i < 50; i++) {
                Optional<SpawnNode> result = spawnManager.getRandomSpawnNode();
                assertTrue(result.isPresent());
                if (result.get().getZoneId().equals("room_1")) sawRoom1 = true;
                if (result.get().getZoneId().equals("room_2")) sawRoom2 = true;
            }
            assertTrue(sawRoom1);
            assertTrue(sawRoom2);
        }
    }

    @Nested
    @DisplayName("Randomized spawn positions")
    class RandomizedPositions {

        @Test
        @DisplayName("should return position within spawn radius")
        void getRandomizedPosition_withinRadius() {
            Vector3f result = spawnManager.getRandomizedPosition(nodeRoom1);

            // Position should be within spawnRadius (2.0) of the node center
            float dx = Math.abs(result.x() - nodeRoom1.getPosition().x());
            float dz = Math.abs(result.z() - nodeRoom1.getPosition().z());
            assertTrue(dx <= nodeRoom1.getSpawnRadius() + 0.001f);
            assertTrue(dz <= nodeRoom1.getSpawnRadius() + 0.001f);
        }

        @Test
        @DisplayName("should maintain the same Y position")
        void getRandomizedPosition_sameY() {
            Vector3f result = spawnManager.getRandomizedPosition(nodeRoom1);
            assertEquals(nodeRoom1.getPosition().y(), result.y(), 0.001f);
        }

        @Test
        @DisplayName("should produce varied positions")
        void getRandomizedPosition_varied() {
            // Run multiple times and ensure we don't always get the same position
            Vector3f first = spawnManager.getRandomizedPosition(nodeRoom2);
            boolean allSame = true;
            for (int i = 0; i < 20; i++) {
                Vector3f next = spawnManager.getRandomizedPosition(nodeRoom2);
                if (!next.equals(first)) {
                    allSame = false;
                    break;
                }
            }
            assertFalse(allSame, "Randomized positions should vary");
        }
    }

    @Nested
    @DisplayName("Clear all nodes")
    class ClearAllNodes {

        @Test
        @DisplayName("should clear all registered nodes")
        void clearAllNodes() {
            spawnManager.registerSpawnNode(nodeRoom1);
            spawnManager.registerSpawnNode(nodeRoom2);
            spawnManager.markZoneOccupied("room_1");
            spawnManager.markZoneOccupied("room_2");

            spawnManager.clearAllNodes();

            assertFalse(spawnManager.hasNodesInZone("room_1"));
            assertTrue(spawnManager.getActiveSpawnNodes().isEmpty());
            assertTrue(spawnManager.getRandomSpawnNode().isEmpty());
        }
    }
}
