package dev.hytalezombie.manager;

import dev.hytalezombie.model.MapZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ZoneManager")
class ZoneManagerTest {

    private static final String STARTING_ZONE = "spawn_room";

    @Mock
    private PlayerDataManager playerDataManager;

    private ZoneManager zoneManager;

    private MapZone room1;
    private MapZone room2;
    private MapZone room3;

    @BeforeEach
    void setUp() {
        zoneManager = new ZoneManager(STARTING_ZONE);

        room1 = new MapZone("room_1", "First Room", 500);
        room2 = new MapZone("room_2", "Second Room", 1000);
        room3 = new MapZone("room_3", "Third Room", 1500);

        zoneManager.registerZone(room1);
        zoneManager.registerZone(room2);
        zoneManager.registerZone(room3);

        // Connect zones: spawn <-> room1 <-> room2 <-> room3
        zoneManager.connectZones(STARTING_ZONE, "room_1");
        zoneManager.connectZones("room_1", "room_2");
        zoneManager.connectZones("room_2", "room_3");
    }

    @Nested
    @DisplayName("Initial state")
    class InitialState {

        @Test
        @DisplayName("should start with starting zone unlocked")
        void startingZoneUnlocked() {
            assertTrue(zoneManager.isZoneUnlocked(STARTING_ZONE));
        }

        @Test
        @DisplayName("should start with other zones locked")
        void otherZonesLocked() {
            assertFalse(zoneManager.isZoneUnlocked("room_1"));
            assertFalse(zoneManager.isZoneUnlocked("room_2"));
            assertFalse(zoneManager.isZoneUnlocked("room_3"));
        }

        @Test
        @DisplayName("should have the starting zone created")
        void startingZoneExists() {
            assertNotNull(zoneManager.getZone(STARTING_ZONE));
            assertEquals(STARTING_ZONE, zoneManager.getStartingZoneId());
        }

        @Test
        @DisplayName("should return null for unknown zone")
        void unknownZone() {
            assertNull(zoneManager.getZone("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Zone registration")
    class ZoneRegistration {

        @Test
        @DisplayName("should register new zones")
        void registerZone() {
            MapZone newZone = new MapZone("basement", "Basement", 2000);
            zoneManager.registerZone(newZone);

            assertNotNull(zoneManager.getZone("basement"));
            assertFalse(zoneManager.isZoneUnlocked("basement"));
        }

        @Test
        @DisplayName("should get all registered zones")
        void getAllZones() {
            Collection<MapZone> allZones = zoneManager.getAllZones();
            // Starting zone + 3 registered zones
            assertEquals(4, allZones.size());
        }

        @Test
        @DisplayName("should return unlocked zones")
        void getUnlockedZones() {
            List<MapZone> unlocked = zoneManager.getUnlockedZones();
            assertEquals(1, unlocked.size());
            assertEquals(STARTING_ZONE, unlocked.get(0).getZoneId());
        }
    }

    @Nested
    @DisplayName("Zone unlocking")
    class ZoneUnlocking {

        @Test
        @DisplayName("should unlock a zone when player has enough points")
        void unlockZone() {
            when(playerDataManager.hasEnoughPoints("player-1", 500)).thenReturn(true);
            when(playerDataManager.getOrCreatePlayerData("player-1"))
                    .thenReturn(new dev.hytalezombie.model.PlayerData("player-1"));

            // Give the mock player data some points
            dev.hytalezombie.model.PlayerData data = new dev.hytalezombie.model.PlayerData("player-1");
            data.addPoints(1000);
            when(playerDataManager.getOrCreatePlayerData("player-1")).thenReturn(data);

            boolean unlocked = zoneManager.unlockZone("player-1", "room_1", playerDataManager);

            assertTrue(unlocked);
            assertTrue(zoneManager.isZoneUnlocked("room_1"));
        }

        @Test
        @DisplayName("should not unlock zone if player lacks points")
        void unlockZoneInsufficientFunds() {
            when(playerDataManager.hasEnoughPoints("player-1", 500)).thenReturn(false);

            boolean unlocked = zoneManager.unlockZone("player-1", "room_1", playerDataManager);

            assertFalse(unlocked);
            assertFalse(zoneManager.isZoneUnlocked("room_1"));
        }

        @Test
        @DisplayName("should not unlock a zone with no access path")
        void unlockZoneNoAccess() {
            // Create an isolated zone not connected to any unlocked zone
            MapZone isolated = new MapZone("isolated", "Isolated Room", 500);
            zoneManager.registerZone(isolated);
            // Don't connect it

            when(playerDataManager.hasEnoughPoints("player-1", 500)).thenReturn(true);
            when(playerDataManager.getOrCreatePlayerData("player-1"))
                    .thenReturn(new dev.hytalezombie.model.PlayerData("player-1"));

            boolean unlocked = zoneManager.unlockZone("player-1", "isolated", playerDataManager);

            assertFalse(unlocked);
        }

        @Test
        @DisplayName("should not charge for already unlocked zone")
        void unlockZoneAlreadyUnlocked() {
            when(playerDataManager.hasEnoughPoints("player-1", 500)).thenReturn(true);
            when(playerDataManager.getOrCreatePlayerData("player-1"))
                    .thenReturn(new dev.hytalezombie.model.PlayerData("player-1"));

            // First unlock works
            assertTrue(zoneManager.unlockZone("player-1", "room_1", playerDataManager));

            // Second attempt should be free
            assertTrue(zoneManager.unlockZone("player-1", "room_1", playerDataManager));
            // Points should only be deducted once
        }

        @Test
        @DisplayName("should return false for unknown zone")
        void unlockUnknownZone() {
            boolean unlocked = zoneManager.unlockZone("player-1", "nonexistent", playerDataManager);
            assertFalse(unlocked);
        }
    }

    @Nested
    @DisplayName("Path finding")
    class PathFinding {

        @Test
        @DisplayName("should find path to a directly connected unlocked zone")
        void hasPathToDirectUnlockedZone() {
            room1.setUnlocked(true);

            // room_1 is unlocked and connected to spawn, so path exists
            assertTrue(zoneManager.hasUnlockedPathTo("room_1"));
        }

        @Test
        @DisplayName("should not find path through locked zones")
        void noPathThroughLockedZones() {
            // All zones except spawn are locked, so no path to room_2
            boolean hasPath = zoneManager.hasUnlockedPathTo("room_2");
            assertFalse(hasPath);
        }

        @Test
        @DisplayName("should find path to starting zone")
        void pathToStartingZone() {
            assertTrue(zoneManager.hasUnlockedPathTo(STARTING_ZONE));
        }

        @Test
        @DisplayName("should not find path to locked zone even if path exists")
        void noPathToLockedZone() {
            // Unlock room_1 but room_2 remains locked
            room1.setUnlocked(true);

            // room_2 is connected via room_1 but is locked, so no unlocked path
            assertFalse(zoneManager.hasUnlockedPathTo("room_2"));
        }

        @Test
        @DisplayName("should find path through multiple unlocked zones")
        void pathThroughMultipleZones() {
            // Unlock zones in sequence
            room1.setUnlocked(true);
            room2.setUnlocked(true);
            room3.setUnlocked(true);

            assertTrue(zoneManager.hasUnlockedPathTo("room_3"));
        }

        @Test
        @DisplayName("should return false for unknown zone")
        void pathToUnknownZone() {
            assertFalse(zoneManager.hasUnlockedPathTo("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Reset")
    class ResetFunctionality {

        @Test
        @DisplayName("should reset all zones except starting zone")
        void resetAllZones() {
            room1.setUnlocked(true);
            room2.setUnlocked(true);
            room3.setUnlocked(true);

            zoneManager.resetAllZones();

            assertTrue(zoneManager.isZoneUnlocked(STARTING_ZONE));
            assertFalse(zoneManager.isZoneUnlocked("room_1"));
            assertFalse(zoneManager.isZoneUnlocked("room_2"));
            assertFalse(zoneManager.isZoneUnlocked("room_3"));
        }

        @Test
        @DisplayName("should clear all zones and recreate starting zone")
        void clearAll() {
            zoneManager.clearAll();

            // Starting zone should still exist
            assertNotNull(zoneManager.getZone(STARTING_ZONE));
            assertTrue(zoneManager.isZoneUnlocked(STARTING_ZONE));

            // Other zones should be gone
            assertNull(zoneManager.getZone("room_1"));
        }
    }
}
