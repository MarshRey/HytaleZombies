package dev.hytalezombie.model;

import dev.hytalezombie.model.Vector3i;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Barrier")
class BarrierTest {

    private static final String ZONE_ID = "room_1";
    private static final Vector3i POSITION = new Vector3i(10, 20, 30);

    private Barrier barrier;

    @BeforeEach
    void setUp() {
        barrier = new Barrier(ZONE_ID, POSITION);
    }

    @Nested
    @DisplayName("Initial state")
    class InitialState {

        @Test
        @DisplayName("should have correct zone ID")
        void getZoneId() {
            assertEquals(ZONE_ID, barrier.getZoneId());
        }

        @Test
        @DisplayName("should have correct block position")
        void getBlockPosition() {
            assertEquals(POSITION, barrier.getBlockPosition());
        }

        @Test
        @DisplayName("should start intact")
        void getState_intact() {
            assertEquals(Barrier.BarrierState.INTACT, barrier.getState());
        }

        @Test
        @DisplayName("should have full durability")
        void getDurability() {
            assertEquals(Barrier.MAX_DURABILITY, barrier.getDurability());
        }

        @Test
        @DisplayName("should have zero repair progress")
        void getRepairProgress() {
            assertEquals(0, barrier.getRepairProgress());
        }
    }

    @Nested
    @DisplayName("Zombie hits")
    class ZombieHits {

        @Test
        @DisplayName("should reduce durability on hit")
        void onZombieHit_reducesDurability() {
            barrier.onZombieHit();
            assertEquals(Barrier.MAX_DURABILITY - 1, barrier.getDurability());
        }

        @Test
        @DisplayName("should become damaged after 3 hits (half durability)")
        void onZombieHit_becomesDamaged() {
            for (int i = 0; i < 3; i++) {
                barrier.onZombieHit();
            }
            assertEquals(Barrier.BarrierState.DAMAGED, barrier.getState());
        }

        @Test
        @DisplayName("should become broken after max durability hits")
        void onZombieHit_becomesBroken() {
            for (int i = 0; i < Barrier.MAX_DURABILITY; i++) {
                barrier.onZombieHit();
            }
            assertEquals(Barrier.BarrierState.BROKEN, barrier.getState());
            assertEquals(0, barrier.getDurability());
        }

        @Test
        @DisplayName("should return true when barrier breaks")
        void onZombieHit_returnsTrueOnBreak() {
            for (int i = 0; i < Barrier.MAX_DURABILITY - 1; i++) {
                barrier.onZombieHit();
            }
            boolean broke = barrier.onZombieHit();
            assertTrue(broke);
        }

        @Test
        @DisplayName("should return false when barrier does not break")
        void onZombieHit_returnsFalseOnNotBreak() {
            boolean broke = barrier.onZombieHit();
            assertFalse(broke);
        }

        @Test
        @DisplayName("should do nothing when already broken")
        void onZombieHit_alreadyBroken() {
            // Break the barrier
            for (int i = 0; i < Barrier.MAX_DURABILITY; i++) {
                barrier.onZombieHit();
            }

            // Hit again
            boolean broke = barrier.onZombieHit();
            assertFalse(broke);
            assertEquals(0, barrier.getDurability());
            assertEquals(Barrier.BarrierState.BROKEN, barrier.getState());
        }
    }

    @Nested
    @DisplayName("Repair mechanics")
    class RepairMechanics {

        @Test
        @DisplayName("should make progress on repair tick")
        void onRepairTick_progress() {
            // Damage the barrier first
            barrier.onZombieHit();
            barrier.onZombieHit();
            barrier.onZombieHit(); // Now DAMAGED

            barrier.onRepairTick();
            assertEquals(1, barrier.getRepairProgress());
        }

        @Test
        @DisplayName("should repair to intact after required ticks")
        void onRepairTick_completes() {
            // Damage the barrier to DAMAGED state
            for (int i = 0; i < 3; i++) {
                barrier.onZombieHit();
            }
            assertEquals(Barrier.BarrierState.DAMAGED, barrier.getState());

            // Repair fully
            for (int i = 0; i < Barrier.REPAIR_TICKS_REQUIRED; i++) {
                barrier.onRepairTick();
            }

            assertEquals(Barrier.BarrierState.INTACT, barrier.getState());
            assertEquals(Barrier.MAX_DURABILITY, barrier.getDurability());
            assertEquals(0, barrier.getRepairProgress());
        }

        @Test
        @DisplayName("should return true when repair completes")
        void onRepairTick_returnsTrueOnComplete() {
            // Damage to DAMAGED
            for (int i = 0; i < 3; i++) {
                barrier.onZombieHit();
            }

            // Repair all but last tick
            for (int i = 0; i < Barrier.REPAIR_TICKS_REQUIRED - 1; i++) {
                barrier.onRepairTick();
            }
            boolean completed = barrier.onRepairTick();
            assertTrue(completed);
        }

        @Test
        @DisplayName("should return true immediately if already intact")
        void onRepairTick_alreadyIntact() {
            boolean completed = barrier.onRepairTick();
            assertTrue(completed);
        }

        @Test
        @DisplayName("should return false if barrier is broken")
        void onRepairTick_broken() {
            // Break it fully
            for (int i = 0; i < Barrier.MAX_DURABILITY; i++) {
                barrier.onZombieHit();
            }

            boolean completed = barrier.onRepairTick();
            assertFalse(completed);
            assertEquals(Barrier.BarrierState.BROKEN, barrier.getState());
        }

        @Test
        @DisplayName("should reset repair progress")
        void resetRepairProgress() {
            // Damage then start repair
            for (int i = 0; i < 3; i++) {
                barrier.onZombieHit();
            }
            barrier.onRepairTick();
            barrier.onRepairTick();
            assertTrue(barrier.getRepairProgress() > 0);

            barrier.resetRepairProgress();
            assertEquals(0, barrier.getRepairProgress());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal to another barrier with same zone and position")
        void equals_sameZoneAndPosition() {
            Barrier other = new Barrier(ZONE_ID, POSITION);
            assertEquals(barrier, other);
            assertEquals(barrier.hashCode(), other.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different position")
        void equals_differentPosition() {
            Barrier other = new Barrier(ZONE_ID, new Vector3i(99, 99, 99));
            assertNotEquals(barrier, other);
        }

        @Test
        @DisplayName("should not be equal with different zone")
        void equals_differentZone() {
            Barrier other = new Barrier("room_2", POSITION);
            assertNotEquals(barrier, other);
        }

        @Test
        @DisplayName("should not be equal to null")
        void equals_null() {
            assertNotEquals(null, barrier);
        }
    }
}
