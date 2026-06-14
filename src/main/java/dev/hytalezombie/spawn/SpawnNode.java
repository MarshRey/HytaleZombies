package dev.hytalezombie.spawn;

import dev.hytalezombie.model.Vector3f;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a spawn point for zombies within a specific zone of the map.
 * Each spawn node is tied to a named zone (e.g., "room_1", "room_2").
 */
public class SpawnNode {

    private final String zoneId;
    private final Vector3f position;
    private final float spawnRadius;

    public SpawnNode(@Nonnull String zoneId, @Nonnull Vector3f position, float spawnRadius) {
        this.zoneId = zoneId;
        this.position = position;
        this.spawnRadius = spawnRadius;
    }

    /**
     * The zone this spawn node belongs to.
     */
    @Nonnull
    public String getZoneId() {
        return zoneId;
    }

    /**
     * The exact world position for spawning.
     */
    @Nonnull
    public Vector3f getPosition() {
        return position;
    }

    /**
     * The radius around this position within which zombies may spawn.
     */
    public float getSpawnRadius() {
        return spawnRadius;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpawnNode spawnNode = (SpawnNode) o;
        return Float.compare(spawnRadius, spawnNode.spawnRadius) == 0
            && zoneId.equals(spawnNode.zoneId)
            && position.equals(spawnNode.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zoneId, position, spawnRadius);
    }

    @Override
    public String toString() {
        return "SpawnNode{zone='" + zoneId + "', pos=" + position + "}";
    }
}
