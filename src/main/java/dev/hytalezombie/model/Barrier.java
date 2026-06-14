package dev.hytalezombie.model;

import dev.hytalezombie.model.Vector3i;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a window barrier that zombies must break through
 * before they can reach players. Players can repair barriers
 * by holding right-click on them.
 */
public class Barrier {

    public enum BarrierState {
        INTACT,
        DAMAGED,
        BROKEN
    }

    private final String zoneId;
    private final Vector3i blockPosition;
    private BarrierState state;
    private int repairProgress;

    /**
     * How many repair ticks are needed to go from DAMAGED to INTACT.
     */
    public static final int REPAIR_TICKS_REQUIRED = 40; // 2 seconds

    /**
     * How much damage (in hits) the barrier can take before breaking.
     */
    public static final int MAX_DURABILITY = 5;

    private int durability;

    public Barrier(@Nonnull String zoneId, @Nonnull Vector3i blockPosition) {
        this.zoneId = zoneId;
        this.blockPosition = blockPosition;
        this.state = BarrierState.INTACT;
        this.repairProgress = 0;
        this.durability = MAX_DURABILITY;
    }

    @Nonnull
    public String getZoneId() {
        return zoneId;
    }

    @Nonnull
    public Vector3i getBlockPosition() {
        return blockPosition;
    }

    @Nonnull
    public BarrierState getState() {
        return state;
    }

    /**
     * Called when a zombie hits the barrier.
     * @return true if the barrier broke from this hit
     */
    public boolean onZombieHit() {
        if (state == BarrierState.BROKEN) return false;

        durability--;
        if (durability <= MAX_DURABILITY / 2) {
            state = BarrierState.DAMAGED;
        }
        if (durability <= 0) {
            state = BarrierState.BROKEN;
            return true;
        }
        return false;
    }

    /**
     * Called every tick a player is repairing the barrier.
     * @return true if the repair completed
     */
    public boolean onRepairTick() {
        if (state == BarrierState.INTACT) return true;
        if (state == BarrierState.BROKEN) return false;

        repairProgress++;
        if (repairProgress >= REPAIR_TICKS_REQUIRED) {
            state = BarrierState.INTACT;
            durability = MAX_DURABILITY;
            repairProgress = 0;
            return true;
        }
        return false;
    }

    /**
     * Resets the repair progress if the player stops holding right-click.
     */
    public void resetRepairProgress() {
        this.repairProgress = 0;
    }

    public int getDurability() {
        return durability;
    }

    public int getRepairProgress() {
        return repairProgress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Barrier barrier = (Barrier) o;
        return zoneId.equals(barrier.zoneId) && blockPosition.equals(barrier.blockPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zoneId, blockPosition);
    }
}
