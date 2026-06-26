package dev.hytalezombie.util;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import dev.hytalezombie.model.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Places and removes temporary debug marker blocks in the Hytale world.
 * Tracks the original block at each position so markers can be cleaned up
 * without permanently altering the map.
 * <p>
 * All world mutations are scheduled via {@link World#execute(Runnable)} for
 * thread safety.
 */
public class WorldMarkerUtil {

    private static final Logger LOGGER = Logger.getLogger(WorldMarkerUtil.class.getName());

    /** Marker block keys to try, in order, for each marker type. */
    public enum MarkerType {
        SPAWN_CENTER(List.of("Glass", "wool_white", "Planks")),
        SPAWN_RADIUS(List.of("Glowstone", "Torch", "Glass")),
        BARRIER(List.of("wool_red", "Redstone_Block", "Netherrack")),
        DOOR(List.of("wool_blue", "Lapis_Lazuli_Block", "Glass_Blue")),
        ZONE(List.of("wool_green", "Emerald_Block", "Leaves"));

        private final List<String> blockKeys;

        MarkerType(List<String> blockKeys) {
            this.blockKeys = blockKeys;
        }

        public List<String> getBlockKeys() {
            return blockKeys;
        }
    }

    /** A single placed marker, storing the original block key for restoration. */
    private record PlacedMarker(Vector3i position, String originalBlockKey) {}

    private final Map<MarkerType, List<PlacedMarker>> placedMarkers = new HashMap<>();

    /**
     * Clears all markers of a specific type from the world.
     */
    public void clearMarkers(@Nullable World world, @Nonnull MarkerType type) {
        if (world == null) return;
        List<PlacedMarker> markers = placedMarkers.remove(type);
        if (markers == null || markers.isEmpty()) return;

        world.execute(() -> {
            for (PlacedMarker marker : markers) {
                setBlockAt(world, marker.position(), marker.originalBlockKey());
            }
            LOGGER.log(Level.FINE, "Cleared {0} {1} marker(s)", new Object[]{markers.size(), type});
        });
    }

    /**
     * Clears every placed marker from the world.
     */
    public void clearAllMarkers(@Nullable World world) {
        if (world == null) return;
        List<MarkerType> types = new ArrayList<>(placedMarkers.keySet());
        for (MarkerType type : types) {
            clearMarkers(world, type);
        }
    }

    /**
     * Places a single marker block at the given position.
     */
    public void placeMarker(@Nullable World world, @Nonnull MarkerType type, @Nonnull Vector3i position) {
        if (world == null) return;
        String markerKey = resolveBlockKey(type);
        if (markerKey == null) {
            LOGGER.log(Level.WARNING, "No marker block found for type {0}", type);
            return;
        }

        world.execute(() -> {
            String original = getBlockKeyAt(world, position);
            if (setBlockAt(world, position, markerKey)) {
                placedMarkers.computeIfAbsent(type, k -> new ArrayList<>())
                    .add(new PlacedMarker(position, original));
                LOGGER.log(Level.FINE, "Placed {0} marker at {1}", new Object[]{type, position});
            }
        });
    }

    /**
     * Places a marker at every position in a list.
     */
    public void placeMarkers(@Nullable World world, @Nonnull MarkerType type, @Nonnull List<Vector3i> positions) {
        for (Vector3i pos : positions) {
            placeMarker(world, type, pos);
        }
    }

    /**
     * Returns all positions currently marked by the given type.
     */
    @Nonnull
    public List<Vector3i> getMarkedPositions(@Nonnull MarkerType type) {
        return placedMarkers.getOrDefault(type, Collections.emptyList()).stream()
            .map(PlacedMarker::position)
            .toList();
    }

    /**
     * Resolves a marker type to the first available block key.
     */
    @Nullable
    public String resolveBlockKey(@Nonnull MarkerType type) {
        for (String key : type.getBlockKeys()) {
            if (BlockType.getAssetMap().getIndex(key) != Integer.MIN_VALUE) {
                return key;
            }
        }
        return null;
    }

    /**
     * Reads the block key at a world position using a cached chunk accessor.
     */
    @Nonnull
    public String getBlockKeyAt(@Nonnull World world, @Nonnull Vector3i position) {
        try {
            LocalCachedChunkAccessor accessor = LocalCachedChunkAccessor.atWorldCoords(
                world, position.x(), position.z(), 1);
            int blockId = accessor.getBlock(position.x(), position.y(), position.z());
            BlockType type = BlockType.getAssetMap().getAsset(blockId);
            return type != null ? type.getId() : "Air";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not read block at {0}: {1}",
                new Object[]{position, e.getMessage()});
            return "Air";
        }
    }

    /**
     * Sets the block at a world position using a cached chunk accessor.
     */
    public boolean setBlockAt(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String blockKey) {
        try {
            LocalCachedChunkAccessor accessor = LocalCachedChunkAccessor.atWorldCoords(
                world, position.x(), position.z(), 1);
            accessor.setBlock(position.x(), position.y(), position.z(), blockKey);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not set block at {0}: {1}",
                new Object[]{position, e.getMessage()});
            return false;
        }
    }
}
