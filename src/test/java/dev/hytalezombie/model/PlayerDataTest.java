package dev.hytalezombie.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlayerData")
class PlayerDataTest {

    private static final String TEST_PLAYER_ID = "player-uuid-1234";
    private PlayerData playerData;

    @BeforeEach
    void setUp() {
        playerData = new PlayerData(TEST_PLAYER_ID);
    }

    @Nested
    @DisplayName("Initial state")
    class InitialState {

        @Test
        @DisplayName("should have correct player ID")
        void getPlayerId() {
            assertEquals(TEST_PLAYER_ID, playerData.getPlayerId());
        }

        @Test
        @DisplayName("should start with zero points")
        void getPoints_default() {
            assertEquals(0, playerData.getPoints());
        }

        @Test
        @DisplayName("should start with zero kills")
        void getKills_default() {
            assertEquals(0, playerData.getKills());
        }

        @Test
        @DisplayName("should start with zero downs")
        void getDowns_default() {
            assertEquals(0, playerData.getDowns());
        }

        @Test
        @DisplayName("should start alive")
        void isAlive_default() {
            assertTrue(playerData.isAlive());
        }
    }

    @Nested
    @DisplayName("Points management")
    class PointsManagement {

        @Test
        @DisplayName("should add points correctly")
        void addPoints() {
            playerData.addPoints(100);
            assertEquals(100, playerData.getPoints());
        }

        @Test
        @DisplayName("should handle multiple point additions")
        void addPoints_multiple() {
            playerData.addPoints(50);
            playerData.addPoints(150);
            assertEquals(200, playerData.getPoints());
        }

        @Test
        @DisplayName("should not allow points to go below zero when adding negative")
        void addPoints_negativeClampsToZero() {
            playerData.addPoints(-50);
            assertEquals(0, playerData.getPoints());
        }

        @Test
        @DisplayName("should deduct points when sufficient")
        void deductPoints_sufficient() {
            playerData.addPoints(200);
            boolean result = playerData.deductPoints(150);
            assertTrue(result);
            assertEquals(50, playerData.getPoints());
        }

        @Test
        @DisplayName("should fail deduction when insufficient points")
        void deductPoints_insufficient() {
            playerData.addPoints(50);
            boolean result = playerData.deductPoints(100);
            assertFalse(result);
            assertEquals(50, playerData.getPoints());
        }

        @Test
        @DisplayName("should deduct exactly zero points")
        void deductPoints_zero() {
            boolean result = playerData.deductPoints(0);
            assertTrue(result);
            assertEquals(0, playerData.getPoints());
        }

        @Test
        @DisplayName("should set points to a specific value")
        void setPoints() {
            playerData.setPoints(500);
            assertEquals(500, playerData.getPoints());
        }

        @Test
        @DisplayName("should clamp set points to zero")
        void setPoints_clampsToZero() {
            playerData.setPoints(-100);
            assertEquals(0, playerData.getPoints());
        }
    }

    @Nested
    @DisplayName("Kill tracking")
    class KillTracking {

        @Test
        @DisplayName("should increment kills")
        void incrementKills() {
            playerData.incrementKills();
            assertEquals(1, playerData.getKills());
        }

        @Test
        @DisplayName("should increment kills multiple times")
        void incrementKills_multiple() {
            playerData.incrementKills();
            playerData.incrementKills();
            playerData.incrementKills();
            assertEquals(3, playerData.getKills());
        }
    }

    @Nested
    @DisplayName("Down tracking")
    class DownTracking {

        @Test
        @DisplayName("should increment downs")
        void incrementDowns() {
            playerData.incrementDowns();
            assertEquals(1, playerData.getDowns());
        }

        @Test
        @DisplayName("should increment downs multiple times")
        void incrementDowns_multiple() {
            playerData.incrementDowns();
            playerData.incrementDowns();
            assertEquals(2, playerData.getDowns());
        }
    }

    @Nested
    @DisplayName("Life state")
    class LifeState {

        @Test
        @DisplayName("should start alive")
        void startsAlive() {
            assertTrue(playerData.isAlive());
        }

        @Test
        @DisplayName("should set alive state")
        void setAlive() {
            playerData.setAlive(false);
            assertFalse(playerData.isAlive());
            playerData.setAlive(true);
            assertTrue(playerData.isAlive());
        }
    }

    @Nested
    @DisplayName("Reset")
    class Reset {

        @Test
        @DisplayName("should reset all data to initial state")
        void reset() {
            // Set up some state
            playerData.addPoints(300);
            playerData.incrementKills();
            playerData.incrementDowns();
            playerData.setAlive(false);

            // Reset
            playerData.reset();

            // Verify all values are back to defaults
            assertEquals(0, playerData.getPoints());
            assertEquals(0, playerData.getKills());
            assertEquals(0, playerData.getDowns());
            assertTrue(playerData.isAlive());
            assertEquals(TEST_PLAYER_ID, playerData.getPlayerId());
        }
    }
}
