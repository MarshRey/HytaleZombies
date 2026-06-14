package dev.hytalezombie.spawn;

import dev.hytalezombie.model.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnNode")
class SpawnNodeTest {

    private static final String ZONE_ID = "room_1";
    private static final Vector3f POSITION = new Vector3f(10.0f, 0.0f, 10.0f);
    private static final float RADIUS = 2.0f;

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should store zone ID")
        void getZoneId() {
            SpawnNode node = new SpawnNode(ZONE_ID, POSITION, RADIUS);
            assertEquals(ZONE_ID, node.getZoneId());
        }

        @Test
        @DisplayName("should store position")
        void getPosition() {
            SpawnNode node = new SpawnNode(ZONE_ID, POSITION, RADIUS);
            assertEquals(POSITION, node.getPosition());
        }

        @Test
        @DisplayName("should store spawn radius")
        void getSpawnRadius() {
            SpawnNode node = new SpawnNode(ZONE_ID, POSITION, RADIUS);
            assertEquals(RADIUS, node.getSpawnRadius(), 0.001f);
        }

        @Test
        @DisplayName("should allow zero radius")
        void zeroRadius() {
            SpawnNode node = new SpawnNode(ZONE_ID, POSITION, 0.0f);
            assertEquals(0.0f, node.getSpawnRadius(), 0.001f);
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when same zone, position, radius")
        void equals_same() {
            SpawnNode node1 = new SpawnNode(ZONE_ID, POSITION, RADIUS);
            SpawnNode node2 = new SpawnNode(ZONE_ID, POSITION, RADIUS);
            assertEquals(node1, node2);
            assertEquals(node1.hashCode(), node2.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different zone")
        void equals_differentZone() {
            SpawnNode node1 = new SpawnNode("room_1", POSITION, RADIUS);
            SpawnNode node2 = new SpawnNode("room_2", POSITION, RADIUS);
            assertNotEquals(node1, node2);
        }

        @Test
        @DisplayName("should not be equal with different position")
        void equals_differentPosition() {
            SpawnNode node1 = new SpawnNode(ZONE_ID, new Vector3f(10.0f, 0.0f, 10.0f), RADIUS);
            SpawnNode node2 = new SpawnNode(ZONE_ID, new Vector3f(20.0f, 0.0f, 10.0f), RADIUS);
            assertNotEquals(node1, node2);
        }

        @Test
        @DisplayName("should not be equal with different radius")
        void equals_differentRadius() {
            SpawnNode node1 = new SpawnNode(ZONE_ID, POSITION, 2.0f);
            SpawnNode node2 = new SpawnNode(ZONE_ID, POSITION, 5.0f);
            assertNotEquals(node1, node2);
        }
    }

    @Nested
    @DisplayName("String representation")
    class ToString {

        @Test
        @DisplayName("should include zone and position")
        void toString_containsInfo() {
            SpawnNode node = new SpawnNode(ZONE_ID, POSITION, RADIUS);
            String str = node.toString();
            assertTrue(str.contains("room_1"));
            assertTrue(str.contains("(10.0, 0.0, 10.0)"));
        }
    }
}
