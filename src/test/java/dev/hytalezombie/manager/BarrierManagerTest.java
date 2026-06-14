package dev.hytalezombie.manager;

import dev.hytalezombie.model.Vector3i;
import dev.hytalezombie.model.Barrier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BarrierManager")
class BarrierManagerTest {

    private BarrierManager barrierManager;

    private Barrier barrierRoom1;
    private Barrier barrierRoom2;
    private Barrier barrierRoom2Alt;

    private static final Vector3i POS_1 = new Vector3i(10, 20, 30);
    private static final Vector3i POS_2 = new Vector3i(50, 20, 30);
    private static final Vector3i POS_3 = new Vector3i(55, 20, 35);

    @BeforeEach
    void setUp() {
        barrierManager = new BarrierManager();
        barrierRoom1 = new Barrier("room_1", POS_1);
        barrierRoom2 = new Barrier("room_2", POS_2);
        barrierRoom2Alt = new Barrier("room_2", POS_3);
    }

    @Nested
    @DisplayName("Barrier registration")
    class BarrierRegistration {

        @Test
        @DisplayName("should register a barrier")
        void registerBarrier() {
            barrierManager.registerBarrier(barrierRoom1);
            assertTrue(barrierManager.hasBarrierAt(POS_1));
        }

        @Test
        @DisplayName("should register multiple barriers")
        void registerBarrier_multiple() {
            barrierManager.registerBarrier(barrierRoom1);
            barrierManager.registerBarrier(barrierRoom2);
            barrierManager.registerBarrier(barrierRoom2Alt);

            assertTrue(barrierManager.hasBarrierAt(POS_1));
            assertTrue(barrierManager.hasBarrierAt(POS_2));
            assertTrue(barrierManager.hasBarrierAt(POS_3));
        }
    }

    @Nested
    @DisplayName("Barrier lookup")
    class BarrierLookup {

        @BeforeEach
        void setUp() {
            barrierManager.registerBarrier(barrierRoom1);
            barrierManager.registerBarrier(barrierRoom2);
            barrierManager.registerBarrier(barrierRoom2Alt);
        }

        @Test
        @DisplayName("should get barrier by position")
        void getBarrierAt() {
            Barrier found = barrierManager.getBarrierAt(POS_1);
            assertNotNull(found);
            assertEquals(barrierRoom1, found);
        }

        @Test
        @DisplayName("should return null for position without barrier")
        void getBarrierAt_nonexistent() {
            Barrier found = barrierManager.getBarrierAt(new Vector3i(999, 999, 999));
            assertNull(found);
        }

        @Test
        @DisplayName("should get all barriers in a zone")
        void getBarriersInZone() {
            List<Barrier> room2Barriers = barrierManager.getBarriersInZone("room_2");
            assertEquals(2, room2Barriers.size());
            assertTrue(room2Barriers.contains(barrierRoom2));
            assertTrue(room2Barriers.contains(barrierRoom2Alt));
        }

        @Test
        @DisplayName("should return empty list for zone without barriers")
        void getBarriersInZone_emptyZone() {
            List<Barrier> barriers = barrierManager.getBarriersInZone("empty_zone");
            assertTrue(barriers.isEmpty());
        }

        @Test
        @DisplayName("should check if position has barrier")
        void hasBarrierAt() {
            assertTrue(barrierManager.hasBarrierAt(POS_1));
            assertFalse(barrierManager.hasBarrierAt(new Vector3i(0, 0, 0)));
        }
    }

    @Nested
    @DisplayName("Barrier removal")
    class BarrierRemoval {

        @BeforeEach
        void setUp() {
            barrierManager.registerBarrier(barrierRoom1);
            barrierManager.registerBarrier(barrierRoom2);
        }

        @Test
        @DisplayName("should remove barrier by position")
        void removeBarrier() {
            barrierManager.removeBarrier(POS_1);
            assertFalse(barrierManager.hasBarrierAt(POS_1));
        }

        @Test
        @DisplayName("should also remove from zone list")
        void removeBarrier_fromZoneList() {
            barrierManager.removeBarrier(POS_2);
            List<Barrier> room2Barriers = barrierManager.getBarriersInZone("room_2");
            assertTrue(room2Barriers.isEmpty());
        }

        @Test
        @DisplayName("should not affect other barriers when removing")
        void removeBarrier_preservesOthers() {
            barrierManager.registerBarrier(barrierRoom2Alt);
            barrierManager.removeBarrier(POS_2);

            assertTrue(barrierManager.hasBarrierAt(POS_3));
            List<Barrier> room2Barriers = barrierManager.getBarriersInZone("room_2");
            assertEquals(1, room2Barriers.size());
            assertEquals(barrierRoom2Alt, room2Barriers.get(0));
        }

        @Test
        @DisplayName("should handle removing nonexistent barrier")
        void removeBarrier_nonexistent() {
            barrierManager.removeBarrier(new Vector3i(0, 0, 0));
            // Should not throw
            assertTrue(true);
        }
    }

    @Nested
    @DisplayName("Clear all")
    class ClearAll {

        @Test
        @DisplayName("should clear all registered barriers")
        void clearAll() {
            barrierManager.registerBarrier(barrierRoom1);
            barrierManager.registerBarrier(barrierRoom2);

            barrierManager.clearAll();

            assertFalse(barrierManager.hasBarrierAt(POS_1));
            assertFalse(barrierManager.hasBarrierAt(POS_2));
            assertTrue(barrierManager.getBarriersInZone("room_1").isEmpty());
            assertTrue(barrierManager.getBarriersInZone("room_2").isEmpty());
        }

        @Test
        @DisplayName("should handle clearing already empty manager")
        void clearAll_empty() {
            barrierManager.clearAll();
            // Should not throw
            assertTrue(true);
        }
    }
}
