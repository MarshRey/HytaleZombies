package dev.hytalezombie.manager;

import dev.hytalezombie.HytaleZombiePlugin;
import dev.hytalezombie.config.HytaleZombieConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoundManager")
class RoundManagerTest {

    @Mock
    private HytaleZombiePlugin plugin;

    @Mock
    private Logger logger;

    @Mock
    private HytaleZombieConfig config;

    private RoundManager roundManager;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(logger);

        // Set up default config values
        when(config.getZombieBaseHealth()).thenReturn(100.0f);
        when(config.getHealthScalingPerRound()).thenReturn(1.15f);
        when(config.getZombieBaseSpeed()).thenReturn(1.0f);
        when(config.getSpeedScalingPerRound()).thenReturn(1.05f);
        when(config.getZombieSpawnBaseCount()).thenReturn(5);
        when(config.getZombiesPerPlayer()).thenReturn(2);

        // Use the test-friendly constructor that accepts config directly
        roundManager = new RoundManager(plugin, config);
    }

    @Nested
    @DisplayName("Initial state")
    class InitialState {

        @Test
        @DisplayName("should start at round 0")
        void currentRound_default() {
            assertEquals(0, roundManager.getCurrentRound());
        }

        @Test
        @DisplayName("should have no active zombies")
        void activeZombieCount_default() {
            assertEquals(0, roundManager.getActiveZombieCount());
        }

        @Test
        @DisplayName("should not have an active match")
        void matchActive_default() {
            assertFalse(roundManager.isMatchActive());
        }
    }

    @Nested
    @DisplayName("Match lifecycle")
    class MatchLifecycle {

        @Test
        @DisplayName("should start match at round 1")
        void startMatch() {
            roundManager.startMatch();
            assertEquals(1, roundManager.getCurrentRound());
            assertTrue(roundManager.isMatchActive());
            assertEquals(0, roundManager.getActiveZombieCount());
        }

        @Test
        @DisplayName("should end match and reset state")
        void endMatch() {
            roundManager.startMatch();
            roundManager.incrementActiveZombies();
            roundManager.endMatch();

            assertEquals(0, roundManager.getCurrentRound());
            assertFalse(roundManager.isMatchActive());
            assertEquals(0, roundManager.getActiveZombieCount());
        }

        @Test
        @DisplayName("should not advance round when match is not active")
        void advanceRound_noMatch() {
            roundManager.advanceRound();
            assertEquals(0, roundManager.getCurrentRound());
        }

        @Test
        @DisplayName("should advance round when match is active")
        void advanceRound_withMatch() {
            roundManager.startMatch();
            roundManager.advanceRound();
            assertEquals(2, roundManager.getCurrentRound());
        }

        @Test
        @DisplayName("should advance round multiple times")
        void advanceRound_multiple() {
            roundManager.startMatch();
            roundManager.advanceRound();
            roundManager.advanceRound();
            roundManager.advanceRound();
            assertEquals(4, roundManager.getCurrentRound());
        }

        @Test
        @DisplayName("should log start and end messages")
        void logging() {
            roundManager.startMatch();
            verify(logger).info("HytaleZombie match started! Round 1");

            roundManager.endMatch();
            verify(logger).info("HytaleZombie match ended.");
        }
    }

    @Nested
    @DisplayName("Active zombie tracking")
    class ActiveZombieTracking {

        @BeforeEach
        void startMatch() {
            roundManager.startMatch();
        }

        @Test
        @DisplayName("should increment active zombie count")
        void incrementActiveZombies() {
            roundManager.incrementActiveZombies();
            assertEquals(1, roundManager.getActiveZombieCount());
        }

        @Test
        @DisplayName("should increment multiple times")
        void incrementActiveZombies_multiple() {
            roundManager.incrementActiveZombies();
            roundManager.incrementActiveZombies();
            roundManager.incrementActiveZombies();
            assertEquals(3, roundManager.getActiveZombieCount());
        }

        @Test
        @DisplayName("should decrement active zombie count")
        void decrementActiveZombies() {
            roundManager.incrementActiveZombies();
            roundManager.incrementActiveZombies();
            roundManager.decrementActiveZombies();
            assertEquals(1, roundManager.getActiveZombieCount());
        }

        @Test
        @DisplayName("should not go below zero when decrementing")
        void decrementActiveZombies_clampsToZero() {
            roundManager.decrementActiveZombies();
            assertEquals(0, roundManager.getActiveZombieCount());
        }

        @Test
        @DisplayName("should auto-advance round when all zombies eliminated")
        void decrementActiveZombies_autoAdvance() {
            roundManager.incrementActiveZombies();
            assertEquals(1, roundManager.getCurrentRound());
            roundManager.decrementActiveZombies();
            // Auto-advance triggers when count hits 0
            assertEquals(2, roundManager.getCurrentRound());
        }

        @Test
        @DisplayName("should not auto-advance when zombies remain")
        void decrementActiveZombies_noAdvanceWithRemaining() {
            roundManager.incrementActiveZombies();
            roundManager.incrementActiveZombies();
            roundManager.decrementActiveZombies();
            assertEquals(1, roundManager.getCurrentRound()); // Still round 1
            assertEquals(1, roundManager.getActiveZombieCount());
        }

        @Test
        @DisplayName("should not auto-advance if match is not active")
        void decrementActiveZombies_noAdvanceWithoutMatch() {
            roundManager.endMatch();
            roundManager.decrementActiveZombies();
            assertEquals(0, roundManager.getCurrentRound());
        }
    }

    @Nested
    @DisplayName("Scaling calculations")
    class ScalingCalculations {

        @BeforeEach
        void startMatch() {
            roundManager.startMatch(); // Round 1
        }

        @Test
        @DisplayName("should calculate round 1 health with no scaling")
        void getScaledZombieHealth_round1() {
            assertEquals(100.0f, roundManager.getScaledZombieHealth(), 0.001f);
        }

        @Test
        @DisplayName("should scale zombie health each round")
        void getScaledZombieHealth_scaling() {
            float healthR1 = roundManager.getScaledZombieHealth();
            roundManager.advanceRound(); // Round 2
            float healthR2 = roundManager.getScaledZombieHealth();

            assertEquals(100.0f, healthR1, 0.001f);
            assertEquals(100.0f * 1.15f, healthR2, 0.001f);
        }

        @Test
        @DisplayName("should scale health exponentially over many rounds")
        void getScaledZombieHealth_lateRounds() {
            // Advance to round 5
            for (int i = 0; i < 4; i++) {
                roundManager.advanceRound();
            }
            // Round 5 health = 100 * 1.15^4
            assertEquals(100.0f * Math.pow(1.15f, 4), roundManager.getScaledZombieHealth(), 0.01f);
        }

        @Test
        @DisplayName("should calculate round 1 speed with no scaling")
        void getScaledZombieSpeed_round1() {
            assertEquals(1.0f, roundManager.getScaledZombieSpeed(), 0.001f);
        }

        @Test
        @DisplayName("should scale zombie speed each round")
        void getScaledZombieSpeed_scaling() {
            float speedR1 = roundManager.getScaledZombieSpeed();
            roundManager.advanceRound(); // Round 2
            float speedR2 = roundManager.getScaledZombieSpeed();

            assertEquals(1.0f, speedR1, 0.001f);
            assertEquals(1.0f * 1.05f, speedR2, 0.001f);
        }

        @Test
        @DisplayName("should calculate spawn count based on player count and round")
        void getSpawnCount() {
            // Round 1: baseCount(5) + perPlayer(2)*players + round(1)*2
            int count = roundManager.getSpawnCount(1);
            assertEquals(5 + 2 + 2, count);
        }

        @Test
        @DisplayName("should increase spawn count with more players")
        void getSpawnCount_morePlayers() {
            int soloCount = roundManager.getSpawnCount(1);
            int teamCount = roundManager.getSpawnCount(4);
            assertTrue(teamCount > soloCount);
            assertEquals(5 + 2 + 2, soloCount);
            assertEquals(5 + 8 + 2, teamCount);
        }

        @Test
        @DisplayName("should increase spawn count with higher rounds")
        void getSpawnCount_higherRound() {
            roundManager.advanceRound(); // Now round 2
            int count = roundManager.getSpawnCount(1);
            assertEquals(5 + 2 + 4, count); // round 2 adds 4 instead of 2
        }

        @Test
        @DisplayName("should handle zero players gracefully")
        void getSpawnCount_zeroPlayers() {
            int count = roundManager.getSpawnCount(0);
            assertEquals(5 + 0 + 2, count); // base + 0 + round*2
        }

        @Test
        @DisplayName("should use config values for calculations")
        void getSpawnCount_usesConfig() {
            // Verify config was accessed for calculations
            roundManager.getSpawnCount(1);
            verify(config, atLeastOnce()).getZombieSpawnBaseCount();
            verify(config, atLeastOnce()).getZombiesPerPlayer();
        }
    }
}
