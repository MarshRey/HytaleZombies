package dev.hytalezombie.model;

import java.util.Objects;

/**
 * Float-based 3D vector for entity positions.
 * Local stub matching Hytale's Vector3f API for compilation without the SDK.
 */
public class Vector3f {
    private final float x, y, z;

    public Vector3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vector3f vector3f = (Vector3f) o;
        return Float.compare(x, vector3f.x) == 0
            && Float.compare(y, vector3f.y) == 0
            && Float.compare(z, vector3f.z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
