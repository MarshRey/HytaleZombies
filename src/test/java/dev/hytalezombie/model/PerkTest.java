package dev.hytalezombie.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Perk")
class PerkTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create a perk with correct type")
        void createPerk() {
            Perk perk = new Perk(Perk.PerkType.JUGGERNOG);
            assertEquals(Perk.PerkType.JUGGERNOG, perk.getType());
            assertEquals("Juggernog", perk.getDisplayName());
            assertTrue(perk.getDescription().contains("health"));
            assertEquals(2500, perk.getCost());
        }

        @Test
        @DisplayName("should start inactive")
        void startsInactive() {
            Perk perk = new Perk(Perk.PerkType.SPEED_COLA);
            assertFalse(perk.isActive());
        }

        @Test
        @DisplayName("should create all perk types")
        void createAllPerkTypes() {
            for (Perk.PerkType type : Perk.PerkType.values()) {
                Perk perk = new Perk(type);
                assertEquals(type, perk.getType());
                assertFalse(perk.isActive());
            }
        }
    }

    @Nested
    @DisplayName("Activation")
    class Activation {

        @Test
        @DisplayName("should toggle active state")
        void setActive() {
            Perk perk = new Perk(Perk.PerkType.QUICK_REVIVE);
            perk.setActive(true);
            assertTrue(perk.isActive());
            perk.setActive(false);
            assertFalse(perk.isActive());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal based on type")
        void equalsByType() {
            Perk perk1 = new Perk(Perk.PerkType.JUGGERNOG);
            Perk perk2 = new Perk(Perk.PerkType.JUGGERNOG);
            assertEquals(perk1, perk2);
            assertEquals(perk1.hashCode(), perk2.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different type")
        void equalsDifferentType() {
            Perk perk1 = new Perk(Perk.PerkType.JUGGERNOG);
            Perk perk2 = new Perk(Perk.PerkType.SPEED_COLA);
            assertNotEquals(perk1, perk2);
        }
    }

    @Nested
    @DisplayName("Perk type properties")
    class PerkTypeProperties {

        @Test
        @DisplayName("all perk types should have display names")
        void allHaveDisplayNames() {
            for (Perk.PerkType type : Perk.PerkType.values()) {
                assertNotNull(type.getDisplayName());
                assertFalse(type.getDisplayName().isEmpty());
            }
        }

        @Test
        @DisplayName("all perk types should have costs")
        void allHaveCosts() {
            for (Perk.PerkType type : Perk.PerkType.values()) {
                assertTrue(type.getCost() > 0);
            }
        }

        @Test
        @DisplayName("all perk types should have descriptions")
        void allHaveDescriptions() {
            for (Perk.PerkType type : Perk.PerkType.values()) {
                assertNotNull(type.getDescription());
                assertFalse(type.getDescription().isEmpty());
            }
        }
    }
}
