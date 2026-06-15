package dev.hytalezombie.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PowerUp")
class PowerUpTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create a power-up with correct type")
        void createPowerUp() {
            PowerUp powerUp = new PowerUp(PowerUp.PowerUpType.NUKE);
            assertEquals(PowerUp.PowerUpType.NUKE, powerUp.getType());
            assertEquals("Nuke", powerUp.getDisplayName());
        }

        @Test
        @DisplayName("should start inactive")
        void startsInactive() {
            PowerUp powerUp = new PowerUp(PowerUp.PowerUpType.DOUBLE_POINTS);
            assertEquals(0, powerUp.getRemainingTicks());
        }
    }

    @Nested
    @DisplayName("Activation")
    class Activation {

        @Test
        @DisplayName("should activate with correct duration")
        void activate() {
            PowerUp powerUp = new PowerUp(PowerUp.PowerUpType.DOUBLE_POINTS);
            powerUp.activate(20); // 20 ticks/sec, 30 sec duration = 600 ticks

            assertEquals(600, powerUp.getRemainingTicks());
        }

        @Test
        @DisplayName("should tick down duration")
        void tick() {
            PowerUp powerUp = new PowerUp(PowerUp.PowerUpType.INSTAKILL);
            powerUp.activate(20);

            powerUp.tick();
            assertEquals(599, powerUp.getRemainingTicks());
        }

        @Test
        @DisplayName("should expire after duration elapses")
        void expire() {
            PowerUp powerUp = new PowerUp(PowerUp.PowerUpType.INSTAKILL);
            powerUp.activate(20); // 30 sec = 600 ticks

            for (int i = 0; i < 600; i++) {
                powerUp.tick();
            }

            assertTrue(powerUp.isExpired());
        }

        @Test
        @DisplayName("should not expire before duration elapses")
        void notExpired() {
            PowerUp powerUp = new PowerUp(PowerUp.PowerUpType.DOUBLE_POINTS);
            powerUp.activate(20);

            for (int i = 0; i < 100; i++) {
                powerUp.tick();
            }

            assertFalse(powerUp.isExpired());
        }

        @Test
        @DisplayName("should set zero ticks for instant power-ups")
        void instantActivation() {
            PowerUp powerUp = new PowerUp(PowerUp.PowerUpType.NUKE);
            powerUp.activate(20);

            assertEquals(0, powerUp.getRemainingTicks());
            assertFalse(powerUp.isExpired()); // Not timed, so never "expired"
        }

        @Test
        @DisplayName("should return expired false for non-timed power-ups")
        void nonTimedNeverExpired() {
            PowerUp powerUp = new PowerUp(PowerUp.PowerUpType.CARPENTER);
            powerUp.activate(20);

            assertFalse(powerUp.isExpired());

            // Ticking shouldn't change anything for instant power-ups
            powerUp.tick();
            assertEquals(0, powerUp.getRemainingTicks());
        }
    }

    @Nested
    @DisplayName("Power-up types")
    class PowerUpTypes {

        @Test
        @DisplayName("all timed power-ups should have durations")
        void timedTypesHaveDuration() {
            for (PowerUp.PowerUpType type : PowerUp.PowerUpType.values()) {
                if (type.isTimed()) {
                    assertTrue(type.getDurationSeconds() > 0);
                }
            }
        }

        @Test
        @DisplayName("all power-up types should have display names")
        void allHaveDisplayNames() {
            for (PowerUp.PowerUpType type : PowerUp.PowerUpType.values()) {
                assertNotNull(type.getDisplayName());
                assertFalse(type.getDisplayName().isEmpty());
            }
        }

        @Test
        @DisplayName("should identify timed vs instant")
        void timedClassification() {
            assertTrue(PowerUp.PowerUpType.DOUBLE_POINTS.isTimed());
            assertTrue(PowerUp.PowerUpType.INSTAKILL.isTimed());
            assertTrue(PowerUp.PowerUpType.MAX_AMMO.isTimed());
            assertTrue(PowerUp.PowerUpType.FIRESALE.isTimed());

            assertFalse(PowerUp.PowerUpType.NUKE.isTimed());
            assertFalse(PowerUp.PowerUpType.CARPENTER.isTimed());
            assertFalse(PowerUp.PowerUpType.BONUS_POINTS.isTimed());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal based on type")
        void equalsByType() {
            PowerUp p1 = new PowerUp(PowerUp.PowerUpType.NUKE);
            PowerUp p2 = new PowerUp(PowerUp.PowerUpType.NUKE);
            assertEquals(p1, p2);
            assertEquals(p1.hashCode(), p2.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different type")
        void equalsDifferentType() {
            PowerUp p1 = new PowerUp(PowerUp.PowerUpType.NUKE);
            PowerUp p2 = new PowerUp(PowerUp.PowerUpType.MAX_AMMO);
            assertNotEquals(p1, p2);
        }
    }
}
