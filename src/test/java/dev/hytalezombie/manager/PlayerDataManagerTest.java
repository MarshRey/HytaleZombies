package dev.hytalezombie.manager;

import dev.hytalezombie.model.PlayerData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlayerDataManager")
class PlayerDataManagerTest {

    private static final String PLAYER_ID = "player-uuid-123";
    private static final String PLAYER_ID_2 = "player-uuid-456";

    private PlayerDataManager playerDataManager;

    @BeforeEach
    void setUp() {
        playerDataManager = new PlayerDataManager();
    }

    @Nested
    @DisplayName("Player data creation and retrieval")
    class PlayerDataCreation {

        @Test
        @DisplayName("should create new player data when getting or creating")
        void getOrCreatePlayerData_createsNew() {
            PlayerData data = playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            assertNotNull(data);
            assertEquals(PLAYER_ID, data.getPlayerId());
            assertEquals(0, data.getPoints());
        }

        @Test
        @DisplayName("should return existing player data")
        void getOrCreatePlayerData_returnsExisting() {
            PlayerData first = playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            PlayerData second = playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            assertSame(first, second); // Same instance
        }

        @Test
        @DisplayName("should create separate data for different players")
        void getOrCreatePlayerData_differentPlayers() {
            PlayerData data1 = playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            PlayerData data2 = playerDataManager.getOrCreatePlayerData(PLAYER_ID_2);
            assertNotSame(data1, data2);
            assertEquals(PLAYER_ID, data1.getPlayerId());
            assertEquals(PLAYER_ID_2, data2.getPlayerId());
        }

        @Test
        @DisplayName("should return null for non-existent player")
        void getPlayerData_nonexistent() {
            PlayerData data = playerDataManager.getPlayerData(PLAYER_ID);
            assertNull(data);
        }

        @Test
        @DisplayName("should return existing player data without creating")
        void getPlayerData_existing() {
            playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            PlayerData data = playerDataManager.getPlayerData(PLAYER_ID);
            assertNotNull(data);
        }
    }

    @Nested
    @DisplayName("Player data removal")
    class PlayerDataRemoval {

        @Test
        @DisplayName("should remove player data")
        void removePlayerData() {
            playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            assertNotNull(playerDataManager.getPlayerData(PLAYER_ID));

            playerDataManager.removePlayerData(PLAYER_ID);
            assertNull(playerDataManager.getPlayerData(PLAYER_ID));
        }

        @Test
        @DisplayName("should handle removing non-existent player")
        void removePlayerData_nonexistent() {
            playerDataManager.removePlayerData("non-existent");
            // Should not throw
            assertTrue(true);
        }
    }

    @Nested
    @DisplayName("Point checking")
    class PointChecking {

        @Test
        @DisplayName("should return false if player has no data")
        void hasEnoughPoints_noData() {
            assertFalse(playerDataManager.hasEnoughPoints(PLAYER_ID, 100));
        }

        @Test
        @DisplayName("should return true if player has enough points")
        void hasEnoughPoints_sufficient() {
            PlayerData data = playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            data.addPoints(500);
            assertTrue(playerDataManager.hasEnoughPoints(PLAYER_ID, 300));
        }

        @Test
        @DisplayName("should return false if player has insufficient points")
        void hasEnoughPoints_insufficient() {
            PlayerData data = playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            data.addPoints(100);
            assertFalse(playerDataManager.hasEnoughPoints(PLAYER_ID, 200));
        }

        @Test
        @DisplayName("should return true if player has exactly enough points")
        void hasEnoughPoints_exact() {
            PlayerData data = playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            data.addPoints(250);
            assertTrue(playerDataManager.hasEnoughPoints(PLAYER_ID, 250));
        }
    }

    @Nested
    @DisplayName("Reset all")
    class ResetAll {

        @Test
        @DisplayName("should reset all player data")
        void resetAll() {
            PlayerData data1 = playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            PlayerData data2 = playerDataManager.getOrCreatePlayerData(PLAYER_ID_2);

            data1.addPoints(500);
            data1.incrementKills();
            data2.addPoints(300);
            data2.setAlive(false);

            playerDataManager.resetAll();

            assertEquals(0, data1.getPoints());
            assertEquals(0, data1.getKills());
            assertTrue(data1.isAlive());

            assertEquals(0, data2.getPoints());
            assertTrue(data2.isAlive());
        }

        @Test
        @DisplayName("should preserve player entries after reset")
        void resetAll_preservesEntries() {
            playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            playerDataManager.getOrCreatePlayerData(PLAYER_ID_2);

            playerDataManager.resetAll();

            assertEquals(2, playerDataManager.getPlayerCount());
        }
    }

    @Nested
    @DisplayName("Player count")
    class PlayerCount {

        @Test
        @DisplayName("should start with zero players")
        void getPlayerCount_default() {
            assertEquals(0, playerDataManager.getPlayerCount());
        }

        @Test
        @DisplayName("should increase with new players")
        void getPlayerCount_afterAdding() {
            playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            assertEquals(1, playerDataManager.getPlayerCount());

            playerDataManager.getOrCreatePlayerData(PLAYER_ID_2);
            assertEquals(2, playerDataManager.getPlayerCount());
        }

        @Test
        @DisplayName("should decrease when players are removed")
        void getPlayerCount_afterRemoval() {
            playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            playerDataManager.getOrCreatePlayerData(PLAYER_ID_2);
            playerDataManager.removePlayerData(PLAYER_ID);
            assertEquals(1, playerDataManager.getPlayerCount());
        }

        @Test
        @DisplayName("should not duplicate count for same player")
        void getPlayerCount_noDuplicates() {
            playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            playerDataManager.getOrCreatePlayerData(PLAYER_ID);
            assertEquals(1, playerDataManager.getPlayerCount());
        }
    }
}
