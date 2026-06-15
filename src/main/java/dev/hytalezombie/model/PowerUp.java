package dev.hytalezombie.model;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a power-up that can drop from killed zombies.
 * Power-ups provide temporary buffs or immediate effects.
 */
public class PowerUp {

    public enum PowerUpType {
        MAX_AMMO("Max Ammo", "Refill all ammo", 15, true),
        DOUBLE_POINTS("Double Points", "2x points for kills", 30, true),
        NUKE("Nuke", "Kill all zombies", 0, false),
        INSTAKILL("Insta-Kill", "One-hit kills", 30, true),
        CARPENTER("Carpenter", "Repair all barriers", 0, false),
        BONUS_POINTS("Bonus Points", "Extra points", 0, false),
        FIRESALE("Firesale", "Mystery Box moves", 30, true);

        private final String displayName;
        private final String description;
        private final int durationSeconds;
        private final boolean timed;

        PowerUpType(String displayName, String description, int durationSeconds, boolean timed) {
            this.displayName = displayName;
            this.description = description;
            this.durationSeconds = durationSeconds;
            this.timed = timed;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getDurationSeconds() { return durationSeconds; }
        public boolean isTimed() { return timed; }
    }

    private final PowerUpType type;
    private long remainingTicks;

    public PowerUp(@Nonnull PowerUpType type) {
        this.type = type;
        this.remainingTicks = 0;
    }

    @Nonnull
    public PowerUpType getType() { return type; }

    public String getDisplayName() { return type.getDisplayName(); }

    public boolean isTimed() { return type.isTimed(); }

    /**
     * Activates the power-up with its configured duration.
     * @param ticksPerSecond the game tick rate (typically 20)
     */
    public void activate(int ticksPerSecond) {
        if (type.isTimed()) {
            this.remainingTicks = (long) type.getDurationSeconds() * ticksPerSecond;
        } else {
            this.remainingTicks = 0; // Instant effects handled externally
        }
    }

    /**
     * Ticks down the remaining duration. Returns true if expired.
     */
    public boolean tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        return remainingTicks <= 0 && type.isTimed();
    }

    public long getRemainingTicks() {
        return remainingTicks;
    }

    public boolean isExpired() {
        return type.isTimed() && remainingTicks <= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PowerUp powerUp = (PowerUp) o;
        return type == powerUp.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return "PowerUp{type=" + type + ", remaining=" + remainingTicks + "}";
    }
}
