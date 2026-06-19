package dev.hytalezombie.model;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Defines a door area between two zones as an axis-aligned bounding box.
 * A player crossing through any part of this area triggers a zone transition.
 *
 * <p>The area is defined by a center point with configurable width (X),
 * height (Y), and depth (Z). Defaults: width=1.0, height=3.0, depth=1.0
 * which creates a 1×3×1 block doorway.</p>
 */
public record DoorArea(@Nonnull Vector3f center, float width, float height, float depth) {

    /** Default door width in blocks (X axis). */
    public static final float DEFAULT_WIDTH = 1.0f;
    /** Default door height in blocks (Y axis). */
    public static final float DEFAULT_HEIGHT = 3.0f;
    /** Default door depth in blocks (Z axis). */
    public static final float DEFAULT_DEPTH = 1.0f;

    /**
     * Creates a door area with default depth (1.0).
     */
    public DoorArea(@Nonnull Vector3f center, float width, float height) {
        this(center, width, height, DEFAULT_DEPTH);
    }

    /**
     * Creates a door area with default size (1×3×1).
     */
    public DoorArea(@Nonnull Vector3f center) {
        this(center, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_DEPTH);
    }

    /**
     * Checks whether a world-space position is inside this door area.
     * Uses axis-aligned bounding box containment (inclusive on all edges).
     */
    public boolean contains(@Nonnull Vector3f pos) {
        float halfW = width / 2f;
        float halfH = height / 2f;
        float halfD = depth / 2f;
        return pos.x() >= center.x() - halfW && pos.x() <= center.x() + halfW
            && pos.y() >= center.y() - halfH && pos.y() <= center.y() + halfH
            && pos.z() >= center.z() - halfD && pos.z() <= center.z() + halfD;
    }

    @Override
    public String toString() {
        return "DoorArea{center=" + center + ", w=" + width + ", h=" + height + ", d=" + depth + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DoorArea doorArea = (DoorArea) o;
        return Float.compare(width, doorArea.width) == 0
            && Float.compare(height, doorArea.height) == 0
            && Float.compare(depth, doorArea.depth) == 0
            && center.equals(doorArea.center);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, width, height, depth);
    }
}
