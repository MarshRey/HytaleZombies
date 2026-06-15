package dev.hytalezombie.manager;

import dev.hytalezombie.model.Weapon;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registry of all weapons available in the game.
 * Provides access to wall weapons, mystery box weapons,
 * and starter weapons.
 */
public class WeaponRegistry {

    private final List<Weapon> allWeapons;
    private final List<Weapon> wallWeapons;
    private final List<Weapon> mysteryBoxWeapons;
    private final Map<String, Weapon> weaponsById;

    public WeaponRegistry() {
        this.allWeapons = new ArrayList<>();
        this.wallWeapons = new ArrayList<>();
        this.mysteryBoxWeapons = new ArrayList<>();
        this.weaponsById = new HashMap<>();
        initializeDefaults();
    }

    /**
     * Initializes the default set of weapons for the game.
     */
    private void initializeDefaults() {
        // --- Starter Pistol ---
        registerWeapon(new Weapon(
                "pistol", "M1911", Weapon.WeaponType.PISTOL,
                Weapon.Rarity.COMMON, 0, 25.0f, 160, 8,
                4.0f, 1.5f, 0, false
        ));

        // --- Wall Weapons ---
        registerWeapon(new Weapon(
                "rifle", "M14", Weapon.WeaponType.RIFLE,
                Weapon.Rarity.COMMON, 500, 55.0f, 120, 20,
                3.5f, 2.0f, 500, false
        ));

        registerWeapon(new Weapon(
                "shotgun", "Olympia", Weapon.WeaponType.SHOTGUN,
                Weapon.Rarity.UNCOMMON, 1200, 80.0f, 32, 2,
                1.5f, 2.5f, 1200, false
        ));

        registerWeapon(new Weapon(
                "smg", "MP5", Weapon.WeaponType.SMG,
                Weapon.Rarity.UNCOMMON, 1000, 30.0f, 240, 30,
                8.0f, 1.8f, 1000, false
        ));

        registerWeapon(new Weapon(
                "sniper", "Dragunov", Weapon.WeaponType.SNIPER,
                Weapon.Rarity.RARE, 1500, 150.0f, 40, 10,
                1.2f, 3.0f, 1500, false
        ));

        // --- Mystery Box Weapons ---
        registerWeapon(new Weapon(
                "ray_gun", "Ray Gun", Weapon.WeaponType.RAY_GUN,
                Weapon.Rarity.EPIC, 0, 200.0f, 80, 20,
                2.5f, 2.0f, 0, true
        ));

        registerWeapon(new Weapon(
                "ak47", "AK-47", Weapon.WeaponType.RIFLE,
                Weapon.Rarity.RARE, 0, 70.0f, 180, 30,
                5.0f, 2.2f, 0, true
        ));

        registerWeapon(new Weapon(
                "hunting_rifle", "Hunting Rifle", Weapon.WeaponType.RIFLE,
                Weapon.Rarity.RARE, 0, 120.0f, 60, 15,
                2.0f, 2.8f, 0, true
        ));

        registerWeapon(new Weapon(
                "spas12", "SPAS-12", Weapon.WeaponType.SHOTGUN,
                Weapon.Rarity.EPIC, 0, 100.0f, 48, 8,
                2.0f, 2.2f, 0, true
        ));

        registerWeapon(new Weapon(
                "thompson", "Thompson", Weapon.WeaponType.SMG,
                Weapon.Rarity.RARE, 0, 35.0f, 300, 50,
                9.0f, 2.0f, 0, true
        ));

        registerWeapon(new Weapon(
                "wunderwaffe", "Wunderwaffe DG-2", Weapon.WeaponType.SNIPER,
                Weapon.Rarity.LEGENDARY, 0, 500.0f, 30, 6,
                1.0f, 4.0f, 0, true
        ));

        registerWeapon(new Weapon(
                "mystery_melee", "Soul Scythe", Weapon.WeaponType.SHOTGUN,
                Weapon.Rarity.LEGENDARY, 0, 300.0f, 20, 1,
                0.8f, 3.5f, 0, true
        ));
    }

    /**
     * Registers a weapon in the registry.
     */
    public void registerWeapon(@Nonnull Weapon weapon) {
        allWeapons.add(weapon);
        weaponsById.put(weapon.getId(), weapon);
        if (weapon.isWallWeapon()) {
            wallWeapons.add(weapon);
        }
        if (weapon.isMysteryBox()) {
            mysteryBoxWeapons.add(weapon);
        }
    }

    /**
     * Gets a weapon by its unique ID.
     */
    @Nullable
    public Weapon getWeapon(@Nonnull String weaponId) {
        return weaponsById.get(weaponId);
    }

    /**
     * Gets the starter pistol.
     */
    @Nonnull
    public Weapon getStarterPistol() {
        Weapon pistol = weaponsById.get("pistol");
        if (pistol == null) {
            throw new IllegalStateException("Starter pistol not registered!");
        }
        return pistol;
    }

    /**
     * Gets all wall weapons (purchasable from the wall).
     */
    @Nonnull
    public List<Weapon> getWallWeapons() {
        return Collections.unmodifiableList(wallWeapons);
    }

    /**
     * Gets all mystery box weapons.
     */
    @Nonnull
    public List<Weapon> getMysteryBoxWeapons() {
        return Collections.unmodifiableList(mysteryBoxWeapons);
    }

    /**
     * Gets all registered weapons.
     */
    @Nonnull
    public List<Weapon> getAllWeapons() {
        return Collections.unmodifiableList(allWeapons);
    }

    /**
     * Gets a random weapon from the mystery box pool.
     */
    @Nonnull
    public Weapon getRandomMysteryBoxWeapon() {
        if (mysteryBoxWeapons.isEmpty()) {
            throw new IllegalStateException("No mystery box weapons registered!");
        }
        return mysteryBoxWeapons.get(
                ThreadLocalRandom.current().nextInt(mysteryBoxWeapons.size())
        );
    }

    /**
     * Gets the Pack-a-Punched version of a weapon by its ID.
     */
    @Nonnull
    public Weapon getPackAPunched(@Nonnull String weaponId) {
        Weapon original = weaponsById.get(weaponId);
        if (original == null) {
            throw new IllegalArgumentException("Unknown weapon: " + weaponId);
        }
        return original.packAPunch();
    }
}
