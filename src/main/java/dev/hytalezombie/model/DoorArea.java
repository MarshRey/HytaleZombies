package dev.hytalezombie.model;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Defines a door area between two zones as an axis-aligned bounding box.
 * A player crossing through any part of this area triggers a zone transition.
 *
 * <p>The area is defined by two opposite corner points. The AABB is computed
 * from the min/max of each axis, so the two points can be given in any order
 * and the door will always encompass the full rectangular region between them.
 * This makes it intuitive: "stand at one side of the doorway, record position;
 * stand at the other side, record position."</p>
 */
public record DoorArea(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

    /**
     * Creates a DoorArea from two opposite corner positions.
     * Order doesn't matter — min/max are computed from the two points.
     */
    @Nonnull
    public static DoorArea fromCorners(@Nonnull Vector3f a, @Nonnull Vector3f b) {
        return new DoorArea(
            Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
            Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())
        );
    }

    /**
     * Checks whether a world-space position is inside this door area.
     * Uses axis-aligned bounding box containment (inclusive on all edges).
     */
    public boolean contains(@Nonnull Vector3f pos) {
        return pos.x() >= minX && pos.x() <= maxX
            && pos.y() >= minY && pos.y() <= maxY
            && pos.z() >= minZ && pos.z() <= maxZ;
    }

    /** Width along X axis. */
    public float width() { return maxX - minX; }
    /** Height along Y axis. */
    public float height() { return maxY - minY; }
    /** Depth along Z axis. */
    public float depth() { return maxZ - minZ; }

    @Override
    public String toString() {
        return "DoorArea[" + minX + ".." + maxX + ", " + minY + ".." + maxY + ", " + minZ + ".." + maxZ
            + "] (" + width() + "×" + height() + "×" + depth() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DoorArea doorArea = (DoorArea) o;
        return Float.compare(minX, doorArea.minX) == 0
            && Float.compare(minY, doorArea.minY) == 0
            && Float.compare(minZ, doorArea.minZ) == 0
            && Float.compare(maxX, doorArea.maxX) == 0
            && Float.compare(maxY, doorArea.maxY) == 0
            && Float.compare(maxZ, doorArea.maxZ) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
