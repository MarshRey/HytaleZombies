package dev.hytalezombie.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MapZone")
class MapZoneTest {

    private MapZone spawnRoom;
    private MapZone room2;

    @BeforeEach
    void setUp() {
        spawnRoom = new MapZone("spawn_room", "Spawn Room", 0);
        room2 = new MapZone("room_2", "Room 2", 500);
    }

    @Nested
    @DisplayName("Initial state")
    class InitialState {

        @Test
        @DisplayName("should have correct zone ID")
        void getZoneId() {
            assertEquals("spawn_room", spawnRoom.getZoneId());
            assertEquals("room_2", room2.getZoneId());
        }

        @Test
        @DisplayName("should have correct display name")
        void getDisplayName() {
            assertEquals("Spawn Room", spawnRoom.getDisplayName());
            assertEquals("Room 2", room2.getDisplayName());
        }

        @Test
        @DisplayName("should have correct door cost")
        void getDoorCost() {
            assertEquals(0, spawnRoom.getDoorCost());
            assertEquals(500, room2.getDoorCost());
        }

        @Test
        @DisplayName("should start locked")
        void isUnlocked_default() {
            assertFalse(spawnRoom.isUnlocked());
            assertFalse(room2.isUnlocked());
        }

        @Test
        @DisplayName("should have no connected zones")
        void getConnectedZoneIds_default() {
            assertTrue(spawnRoom.getConnectedZoneIds().isEmpty());
        }
    }

    @Nested
    @DisplayName("Unlocking")
    class Unlocking {

        @Test
        @DisplayName("should set unlocked state")
        void setUnlocked() {
            room2.setUnlocked(true);
            assertTrue(room2.isUnlocked());
        }

        @Test
        @DisplayName("should toggle unlocked state")
        void setUnlocked_toggle() {
            room2.setUnlocked(true);
            assertTrue(room2.isUnlocked());
            room2.setUnlocked(false);
            assertFalse(room2.isUnlocked());
        }
    }

    @Nested
    @DisplayName("Zone connectivity")
    class ZoneConnectivity {

        @Test
        @DisplayName("should add connected zone")
        void addConnectedZone() {
            spawnRoom.addConnectedZone("room_2");
            assertTrue(spawnRoom.getConnectedZoneIds().contains("room_2"));
        }

        @Test
        @DisplayName("should add multiple connected zones")
        void addConnectedZone_multiple() {
            spawnRoom.addConnectedZone("room_2");
            spawnRoom.addConnectedZone("room_3");
            assertEquals(2, spawnRoom.getConnectedZoneIds().size());
        }

        @Test
        @DisplayName("should return unmodifiable set of connected zones")
        void getConnectedZoneIds_unmodifiable() {
            spawnRoom.addConnectedZone("room_2");
            assertThrows(UnsupportedOperationException.class, () -> {
                spawnRoom.getConnectedZoneIds().add("room_3");
            });
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal based on zone ID")
        void equals_sameId() {
            MapZone sameZone = new MapZone("room_2", "Different Name", 999);
            assertEquals(room2, sameZone);
            assertEquals(room2.hashCode(), sameZone.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different ID")
        void equals_differentId() {
            assertNotEquals(spawnRoom, room2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void equals_null() {
            assertNotEquals(null, room2);
        }
    }

    @Nested
    @DisplayName("String representation")
    class ToString {

        @Test
        @DisplayName("should include zone details")
        void toString_containsInfo() {
            String str = room2.toString();
            assertTrue(str.contains("room_2"));
            assertTrue(str.contains("Room 2"));
            assertTrue(str.contains("500"));
        }
    }
}
