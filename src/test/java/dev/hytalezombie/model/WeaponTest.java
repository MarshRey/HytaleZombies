package dev.hytalezombie.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Weapon")
class WeaponTest {

    private static final String WEAPON_ID = "test_pistol";
    private static final String WEAPON_NAME = "Test Pistol";

    private Weapon createDefaultWeapon() {
        return new Weapon(WEAPON_ID, WEAPON_NAME, Weapon.WeaponType.PISTOL,
                Weapon.Rarity.COMMON, 500, 25.0f, 100, 8,
                4.0f, 1.5f);
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should store all properties")
        void createWeapon() {
            Weapon weapon = createDefaultWeapon();
            assertEquals(WEAPON_ID, weapon.getId());
            assertEquals(WEAPON_NAME, weapon.getDisplayName());
            assertEquals(Weapon.WeaponType.PISTOL, weapon.getType());
            assertEquals(Weapon.Rarity.COMMON, weapon.getRarity());
            assertEquals(500, weapon.getCost());
            assertEquals(25.0f, weapon.getDamage(), 0.001f);
            assertEquals(100, weapon.getMaxAmmo());
            assertEquals(8, weapon.getClipSize());
            assertEquals(4.0f, weapon.getFireRate(), 0.001f);
            assertEquals(1.5f, weapon.getReloadTime(), 0.001f);
        }

        @Test
        @DisplayName("should default to non-wall, non-mystery-box")
        void defaultFlags() {
            Weapon weapon = createDefaultWeapon();
            assertFalse(weapon.isWallWeapon());
            assertFalse(weapon.isMysteryBox());
        }

        @Test
        @DisplayName("should create wall weapon")
        void createWallWeapon() {
            Weapon weapon = new Weapon(WEAPON_ID, WEAPON_NAME, Weapon.WeaponType.RIFLE,
                    Weapon.Rarity.UNCOMMON, 0, 50.0f, 120, 20,
                    4.0f, 2.0f, 1000, false);
            assertTrue(weapon.isWallWeapon());
            assertEquals(1000, weapon.getWallBuyCost());
        }

        @Test
        @DisplayName("should create mystery box weapon")
        void createMysteryBoxWeapon() {
            Weapon weapon = new Weapon(WEAPON_ID, WEAPON_NAME, Weapon.WeaponType.RAY_GUN,
                    Weapon.Rarity.EPIC, 0, 200.0f, 80, 20,
                    2.5f, 2.0f, 0, true);
            assertTrue(weapon.isMysteryBox());
        }
    }

    @Nested
    @DisplayName("Pack-a-Punch")
    class PackAPunch {

        @Test
        @DisplayName("should double damage")
        void doubleDamage() {
            Weapon weapon = createDefaultWeapon();
            Weapon upgraded = weapon.packAPunch();
            assertEquals(25.0f * 2.0f, upgraded.getDamage(), 0.001f);
        }

        @Test
        @DisplayName("should double ammo capacity")
        void doubleAmmo() {
            Weapon weapon = createDefaultWeapon();
            Weapon upgraded = weapon.packAPunch();
            assertEquals(100 * 2, upgraded.getMaxAmmo());
            assertEquals(8 * 2, upgraded.getClipSize());
        }

        @Test
        @DisplayName("should increase fire rate")
        void increaseFireRate() {
            Weapon weapon = createDefaultWeapon();
            Weapon upgraded = weapon.packAPunch();
            assertEquals(4.0f * 1.2f, upgraded.getFireRate(), 0.001f);
        }

        @Test
        @DisplayName("should decrease reload time")
        void decreaseReloadTime() {
            Weapon weapon = createDefaultWeapon();
            Weapon upgraded = weapon.packAPunch();
            assertEquals(1.5f * 0.8f, upgraded.getReloadTime(), 0.001f);
        }

        @Test
        @DisplayName("should set rarity to LEGENDARY")
        void legendaryRarity() {
            Weapon weapon = createDefaultWeapon();
            Weapon upgraded = weapon.packAPunch();
            assertEquals(Weapon.Rarity.LEGENDARY, upgraded.getRarity());
        }

        @Test
        @DisplayName("should add Pack-a-Punched prefix")
        void addPrefix() {
            Weapon weapon = createDefaultWeapon();
            Weapon upgraded = weapon.packAPunch();
            assertTrue(upgraded.getDisplayName().contains("Pack-a-Punched"));
        }

        @Test
        @DisplayName("should not be wall or mystery box after upgrade")
        void upgradedFlags() {
            Weapon weapon = createDefaultWeapon();
            Weapon upgraded = weapon.packAPunch();
            assertFalse(upgraded.isWallWeapon());
            assertFalse(upgraded.isMysteryBox());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal based on ID")
        void equalsById() {
            Weapon weapon1 = createDefaultWeapon();
            Weapon weapon2 = new Weapon(WEAPON_ID, "Different Name", Weapon.WeaponType.SHOTGUN,
                    Weapon.Rarity.RARE, 999, 0, 0, 0, 0, 0);
            assertEquals(weapon1, weapon2);
            assertEquals(weapon1.hashCode(), weapon2.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different ID")
        void equalsDifferentId() {
            Weapon weapon1 = createDefaultWeapon();
            Weapon weapon2 = new Weapon("different_id", WEAPON_NAME, Weapon.WeaponType.PISTOL,
                    Weapon.Rarity.COMMON, 500, 25.0f, 100, 8, 4.0f, 1.5f);
            assertNotEquals(weapon1, weapon2);
        }
    }

    @Nested
    @DisplayName("String representation")
    class ToString {

        @Test
        @DisplayName("should include ID and name")
        void toStringContainsInfo() {
            Weapon weapon = createDefaultWeapon();
            String str = weapon.toString();
            assertTrue(str.contains(WEAPON_ID));
            assertTrue(str.contains(WEAPON_NAME));
            assertTrue(str.contains("500"));
        }
    }
}
