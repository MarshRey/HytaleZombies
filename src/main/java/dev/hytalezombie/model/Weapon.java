package dev.hytalezombie.model;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a purchasable weapon in the game.
 * Includes stats for damage, fire rate, ammo, and cost.
 */
public class Weapon {

    public enum WeaponType {
        PISTOL,
        SHOTGUN,
        RIFLE,
        SMG,
        SNIPER,
        RAY_GUN // Special weapon from Mystery Box
    }

    public enum Rarity {
        COMMON,
        UNCOMMON,
        RARE,
        EPIC,
        LEGENDARY
    }

    private final String id;
    private final String displayName;
    private final WeaponType type;
    private final Rarity rarity;
    private final int cost;
    private final float damage;
    private final int maxAmmo;
    private final int clipSize;
    private final float fireRate; // shots per second
    private final float reloadTime; // seconds
    private final int wallBuyCost; // 0 if not a wall weapon
    private final boolean isMysteryBox;

    public Weapon(@Nonnull String id, @Nonnull String displayName, @Nonnull WeaponType type,
                  @Nonnull Rarity rarity, int cost, float damage, int maxAmmo, int clipSize,
                  float fireRate, float reloadTime) {
        this(id, displayName, type, rarity, cost, damage, maxAmmo, clipSize, fireRate, reloadTime, 0, false);
    }

    public Weapon(@Nonnull String id, @Nonnull String displayName, @Nonnull WeaponType type,
                  @Nonnull Rarity rarity, int cost, float damage, int maxAmmo, int clipSize,
                  float fireRate, float reloadTime, int wallBuyCost, boolean isMysteryBox) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.rarity = rarity;
        this.cost = cost;
        this.damage = damage;
        this.maxAmmo = maxAmmo;
        this.clipSize = clipSize;
        this.fireRate = fireRate;
        this.reloadTime = reloadTime;
        this.wallBuyCost = wallBuyCost;
        this.isMysteryBox = isMysteryBox;
    }

    @Nonnull
    public String getId() { return id; }

    @Nonnull
    public String getDisplayName() { return displayName; }

    @Nonnull
    public WeaponType getType() { return type; }

    @Nonnull
    public Rarity getRarity() { return rarity; }

    public int getCost() { return cost; }

    public float getDamage() { return damage; }

    public int getMaxAmmo() { return maxAmmo; }

    public int getClipSize() { return clipSize; }

    public float getFireRate() { return fireRate; }

    public float getReloadTime() { return reloadTime; }

    public int getWallBuyCost() { return wallBuyCost; }

    public boolean isWallWeapon() { return wallBuyCost > 0; }

    public boolean isMysteryBox() { return isMysteryBox; }

    /**
     * Creates a Pack-a-Punched version of this weapon.
     * Boosts damage by 2x, doubles magazine, adds "Pack-a-Punched" prefix.
     */
    public Weapon packAPunch() {
        return new Weapon(
            id + "_pap",
            "Pack-a-Punched " + displayName,
            type, Rarity.LEGENDARY,
            cost, damage * 2.0f,
            maxAmmo * 2, clipSize * 2,
            fireRate * 1.2f,
            reloadTime * 0.8f,
            0, false
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Weapon weapon = (Weapon) o;
        return id.equals(weapon.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Weapon{id='" + id + "', name='" + displayName + "', cost=" + cost + "}";
    }
}
