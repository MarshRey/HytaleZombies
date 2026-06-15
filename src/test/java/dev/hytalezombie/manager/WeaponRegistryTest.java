package dev.hytalezombie.manager;

import dev.hytalezombie.model.Weapon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WeaponRegistry")
class WeaponRegistryTest {

    private WeaponRegistry weaponRegistry;

    @BeforeEach
    void setUp() {
        weaponRegistry = new WeaponRegistry();
    }

    @Nested
    @DisplayName("Default weapons")
    class DefaultWeapons {

        @Test
        @DisplayName("should have starter pistol")
        void hasStarterPistol() {
            Weapon pistol = weaponRegistry.getWeapon("pistol");
            assertNotNull(pistol);
            assertEquals("M1911", pistol.getDisplayName());
            assertEquals(Weapon.WeaponType.PISTOL, pistol.getType());
        }

        @Test
        @DisplayName("starter pistol should be free")
        void starterPistolFree() {
            Weapon pistol = weaponRegistry.getStarterPistol();
            assertEquals(0, pistol.getCost());
        }

        @Test
        @DisplayName("should have wall weapons")
        void hasWallWeapons() {
            List<Weapon> wallWeapons = weaponRegistry.getWallWeapons();
            assertFalse(wallWeapons.isEmpty());
            assertTrue(wallWeapons.stream().allMatch(Weapon::isWallWeapon));
        }

        @Test
        @DisplayName("should have mystery box weapons")
        void hasMysteryBoxWeapons() {
            List<Weapon> mysteryBoxWeapons = weaponRegistry.getMysteryBoxWeapons();
            assertFalse(mysteryBoxWeapons.isEmpty());
            assertTrue(mysteryBoxWeapons.stream().allMatch(Weapon::isMysteryBox));
        }

        @Test
        @DisplayName("should have registered specific weapons")
        void hasSpecificWeapons() {
            assertNotNull(weaponRegistry.getWeapon("rifle"));
            assertNotNull(weaponRegistry.getWeapon("shotgun"));
            assertNotNull(weaponRegistry.getWeapon("smg"));
            assertNotNull(weaponRegistry.getWeapon("sniper"));
            assertNotNull(weaponRegistry.getWeapon("ray_gun"));
        }

        @Test
        @DisplayName("should have all weapons registered")
        void allWeaponsCount() {
            List<Weapon> all = weaponRegistry.getAllWeapons();
            // 4 wall weapons + 1 starter + 7 mystery box = 12
            assertEquals(12, all.size());
        }
    }

    @Nested
    @DisplayName("Custom weapon registration")
    class CustomRegistration {

        @Test
        @DisplayName("should register a new weapon")
        void registerWeapon() {
            Weapon custom = new Weapon("custom", "Custom Gun", Weapon.WeaponType.RIFLE,
                    Weapon.Rarity.EPIC, 2000, 100.0f, 200, 30, 6.0f, 2.0f);
            weaponRegistry.registerWeapon(custom);

            assertNotNull(weaponRegistry.getWeapon("custom"));
            assertTrue(weaponRegistry.getAllWeapons().contains(custom));
        }

        @Test
        @DisplayName("should register a wall weapon")
        void registerWallWeapon() {
            Weapon wallGun = new Weapon("wall_gun", "Wall Gun", Weapon.WeaponType.SMG,
                    Weapon.Rarity.COMMON, 800, 30.0f, 200, 25, 7.0f, 1.5f,
                    800, false);
            weaponRegistry.registerWeapon(wallGun);

            assertTrue(weaponRegistry.getWallWeapons().contains(wallGun));
        }

        @Test
        @DisplayName("should register a mystery box weapon")
        void registerMysteryBoxWeapon() {
            Weapon mysteryGun = new Weapon("mystery_gun", "Mystery Gun", Weapon.WeaponType.RAY_GUN,
                    Weapon.Rarity.LEGENDARY, 0, 500.0f, 50, 10,
                    3.0f, 3.0f, 0, true);
            weaponRegistry.registerWeapon(mysteryGun);

            assertTrue(weaponRegistry.getMysteryBoxWeapons().contains(mysteryGun));
        }
    }

    @Nested
    @DisplayName("Mystery box")
    class MysteryBox {

        @Test
        @DisplayName("should return a random mystery box weapon")
        void getRandomMysteryBoxWeapon() {
            Weapon random = weaponRegistry.getRandomMysteryBoxWeapon();
            assertNotNull(random);
            assertTrue(random.isMysteryBox());
        }

        @Test
        @DisplayName("should return varied weapons over multiple calls")
        void getVariedWeapons() {
            // Get several samples - they shouldn't all be the same
            boolean allSame = true;
            Weapon first = weaponRegistry.getRandomMysteryBoxWeapon();
            for (int i = 0; i < 20; i++) {
                if (!weaponRegistry.getRandomMysteryBoxWeapon().equals(first)) {
                    allSame = false;
                    break;
                }
            }
            assertFalse(allSame, "Mystery box should return varied weapons");
        }
    }

    @Nested
    @DisplayName("Pack-a-Punch")
    class PackAPunch {

        @Test
        @DisplayName("should upgrade weapon with Pack-a-Punch")
        void packAPunch() {
            Weapon original = weaponRegistry.getWeapon("rifle");
            assertNotNull(original);

            Weapon upgraded = weaponRegistry.getPackAPunched("rifle");

            assertNotNull(upgraded);
            assertTrue(upgraded.getDisplayName().contains("Pack-a-Punched"));
            assertEquals(original.getDamage() * 2.0f, upgraded.getDamage(), 0.001f);
            assertEquals(original.getMaxAmmo() * 2, upgraded.getMaxAmmo());
            assertEquals(original.getClipSize() * 2, upgraded.getClipSize());
            assertEquals(Weapon.Rarity.LEGENDARY, upgraded.getRarity());
        }

        @Test
        @DisplayName("should throw for unknown weapon")
        void packAPunchUnknown() {
            assertThrows(IllegalArgumentException.class,
                    () -> weaponRegistry.getPackAPunched("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Lookup")
    class Lookup {

        @Test
        @DisplayName("should return null for unknown weapon ID")
        void getWeaponUnknown() {
            assertNull(weaponRegistry.getWeapon("nonexistent"));
        }

        @Test
        @DisplayName("should get weapon by ID")
        void getWeapon() {
            Weapon weapon = weaponRegistry.getWeapon("ray_gun");
            assertNotNull(weapon);
            assertEquals("Ray Gun", weapon.getDisplayName());
            assertEquals(Weapon.WeaponType.RAY_GUN, weapon.getType());
            assertEquals(Weapon.Rarity.EPIC, weapon.getRarity());
        }
    }
}
