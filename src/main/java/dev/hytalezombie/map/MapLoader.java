package dev.hytalezombie.map;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalezombie.model.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads a Hytale prefab JSON (converted from a Minecraft schematic)
 * and places all blocks into the world.
 *
 * <p>Usage:
 * <pre>{@code
 *   MapLoader.PrefabData prefab = MapLoader.loadPrefab(Paths.get("map.prefab.json"));
 *   MapLoader.placePrefab(world, prefab, 0, 64, 0);
 * }</pre>
 *
 * <p>The prefab JSON format matches the Hytale schematic converter output:
 * <pre>{@code
 * {
 *   "version": 8,
 *   "blockIdVersion": 8,
 *   "blocks": [
 *     {"x": 0, "y": 0, "z": 0, "name": "Rock_Stone"},
 *     ...
 *   ]
 * }
 * }</pre>
 */
public class MapLoader {

    private static final Logger LOGGER = Logger.getLogger(MapLoader.class.getName());

    /** Maximum blocks to place in a single world-thread batch. */
    private static final int BATCH_SIZE = 1000;

    /**
     * Parsed prefab data ready for placement.
     */
    public static class PrefabData {
        /** All blocks in the prefab with their relative positions. */
        @Nonnull
        public final List<PlacedBlock> blocks;

        /** The width (X extent) of the prefab in blocks. */
        public final int width;

        /** The height (Y extent) of the prefab in blocks. */
        public final int height;

        /** The length (Z extent) of the prefab in blocks. */
        public final int length;

        /** Number of blocks that could not be resolved to Hytale BlockTypes. */
        public final int unresolvedCount;

        public PrefabData(@Nonnull List<PlacedBlock> blocks, int width, int height, int length, int unresolvedCount) {
            this.blocks = blocks;
            this.width = width;
            this.height = height;
            this.length = length;
            this.unresolvedCount = unresolvedCount;
        }
    }

    /**
     * A single block from the prefab with its resolved Hytale block ID.
     */
    public static class PlacedBlock {
        public final int x, y, z;
        public final int blockId;
        @Nonnull
        public final String name;

        public PlacedBlock(int x, int y, int z, int blockId, @Nonnull String name) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.name = name;
        }
    }

    /**
     * Holds the bounds of a placed prefab in world coordinates.
     */
    public static class PrefabBounds {
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;

        public PrefabBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        /** Center XZ of the prefab floor. */
        public Vector3f getCenterFloor() {
            return new Vector3f(
                (minX + maxX) / 2.0f,
                minY,
                (minZ + maxZ) / 2.0f
            );
        }
    }

    /**
     * Loads a Hytale prefab JSON file and resolves all block names to
     * Hytale {@link BlockType} numeric IDs.
     *
     * @param prefabPath path to the prefab JSON file
     * @return parsed prefab data ready for placement
     * @throws IOException if the file cannot be read
     */
    @Nonnull
    public static PrefabData loadPrefab(@Nonnull Path prefabPath) throws IOException {
        String json = Files.readString(prefabPath, StandardCharsets.UTF_8);

        // Parse the JSON using minimal parsing (no external JSON lib needed for this simple format)
        List<PlacedBlock> blocks = new ArrayList<>();
        int unresolvedCount = 0;
        int maxX = 0, maxY = 0, maxZ = 0;

        // Simple parser for the prefab JSON format
        int pos = json.indexOf("\"blocks\"");
        if (pos < 0) {
            throw new IOException("Invalid prefab JSON: missing 'blocks' array");
        }
        pos = json.indexOf('[', pos);
        if (pos < 0) {
            throw new IOException("Invalid prefab JSON: blocks array not found");
        }

        // Parse each block object
        int depth = 0;
        int blockStart = -1;
        pos++;

        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == '{' && depth == 0) {
                blockStart = pos;
            }
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0 && blockStart >= 0) {
                    String blockJson = json.substring(blockStart, pos + 1);
                    PlacedBlock block = parseBlock(blockJson);
                    if (block != null) {
                        blocks.add(block);
                        if (block.x > maxX) maxX = block.x;
                        if (block.y > maxY) maxY = block.y;
                        if (block.z > maxZ) maxZ = block.z;
                        if (block.blockId == 0 && !"Empty".equals(block.name)) {
                            unresolvedCount++;
                        }
                    }
                    blockStart = -1;
                }
            }
            pos++;
        }

        if (blocks.isEmpty()) {
            LOGGER.warning("Prefab file contains no blocks");
        }

        return new PrefabData(blocks, maxX + 1, maxY + 1, maxZ + 1, unresolvedCount);
    }

    /**
     * Parses a single block JSON object like {"x":0,"y":0,"z":0,"name":"Rock_Stone"}.
     */
    @Nullable
    private static PlacedBlock parseBlock(@Nonnull String json) {
        int x = extractInt(json, "x");
        int y = extractInt(json, "y");
        int z = extractInt(json, "z");
        String name = extractString(json, "name");

        if (name == null) return null;

        int blockId = resolveBlockId(name);
        return new PlacedBlock(x, y, z, blockId, name);
    }

    private static int extractInt(@Nonnull String json, @Nonnull String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        idx += search.length();
        // Skip whitespace
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == idx) return 0;
        try {
            return Integer.parseInt(json.substring(idx, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Nullable
    private static String extractString(@Nonnull String json, @Nonnull String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        // Skip whitespace
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        if (idx >= json.length() || json.charAt(idx) != '"') return null;
        idx++;
        int end = json.indexOf('"', idx);
        if (end < 0) return null;
        return json.substring(idx, end);
    }

    /**
     * Resolves a Hytale block name (e.g., "Rock_Stone") to its numeric block ID.
     * Returns 0 (Empty) if the block type is unknown.
     */
    private static int resolveBlockId(@Nonnull String name) {
        if ("Empty".equals(name) || "Air".equals(name)) {
            return 0;
        }
        try {
            BlockType blockType = BlockType.fromString(name);
            if (blockType != null) {
                // Get the numeric index from the asset map
                return BlockType.getAssetMap().getIndex(name);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not resolve block type: {0}", name);
        }
        LOGGER.log(Level.WARNING, "Unknown block type in prefab: {0} — placing as Empty", name);
        return 0;
    }

    /**
     * Places all blocks from a {@link PrefabData} into the world at the given origin.
     *
     * <p>Blocks are placed on the world thread in batches to avoid overwhelming it.
     * This method blocks until all blocks are placed.</p>
     *
     * @param world  the Hytale world to place blocks in
     * @param prefab the parsed prefab data
     * @param originX world X coordinate for prefab's (0,0,0)
     * @param originY world Y coordinate for prefab's (0,0,0)
     * @param originZ world Z coordinate for prefab's (0,0,0)
     * @return bounds of the placed structure in world coordinates
     */
    @Nonnull
    public static PrefabBounds placePrefab(
        @Nonnull World world,
        @Nonnull PrefabData prefab,
        int originX, int originY, int originZ
    ) {
        List<PlacedBlock> blocks = prefab.blocks;
        if (blocks.isEmpty()) {
            return new PrefabBounds(originX, originY, originZ, originX, originY, originZ);
        }

        AtomicInteger placed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        CompletableFuture<Void> done = new CompletableFuture<>();

        // Place blocks in batches on the world thread
        placeBatch(world, blocks, 0, originX, originY, originZ, placed, skipped, done);

        // Wait for completion
        try {
            done.join();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during prefab placement: {0}", e.getMessage());
        }

        LOGGER.log(Level.INFO, "Prefab placed: {0} blocks placed, {1} skipped",
            new Object[]{placed.get(), skipped.get()});

        return new PrefabBounds(
            originX, originY, originZ,
            originX + prefab.width - 1,
            originY + prefab.height - 1,
            originZ + prefab.length - 1
        );
    }

    /**
     * Recursively places batches of blocks on the world thread.
     */
    private static void placeBatch(
        @Nonnull World world,
        @Nonnull List<PlacedBlock> blocks,
        int startIndex,
        int originX, int originY, int originZ,
        @Nonnull AtomicInteger placed,
        @Nonnull AtomicInteger skipped,
        @Nonnull CompletableFuture<Void> done
    ) {
        int endIndex = Math.min(startIndex + BATCH_SIZE, blocks.size());

        world.execute(() -> {
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> store = chunkStore.getStore();

            for (int i = startIndex; i < endIndex; i++) {
                PlacedBlock block = blocks.get(i);
                if (block.blockId == 0) {
                    skipped.incrementAndGet();
                    continue;
                }

                int worldX = originX + block.x;
                int worldY = originY + block.y;
                int worldZ = originZ + block.z;

                try {
                    // Get chunk section coordinates (32x32x32 sections)
                    int sectionX = ChunkUtil.chunkCoordinate(worldX);
                    int sectionY = ChunkUtil.chunkCoordinate(worldY);
                    int sectionZ = ChunkUtil.chunkCoordinate(worldZ);

                    // Local coordinates within the section (0-31)
                    int localX = worldX & 31;
                    int localY = worldY & 31;
                    int localZ = worldZ & 31;

                    // Get or load the chunk section
                    CompletableFuture<Ref<ChunkStore>> refFuture =
                        chunkStore.getChunkSectionReferenceAsync(sectionX, sectionY, sectionZ);

                    // Wait for section to load (world thread can block briefly)
                    while (!refFuture.isDone()) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    Ref<ChunkStore> ref = refFuture.join();
                    if (ref != null && ref.isValid()) {
                        BlockSection section = store.getComponent(ref, BlockSection.getComponentType());
                        if (section != null) {
                            section.set(localX, localY, localZ, block.blockId, 0, 0);
                            placed.incrementAndGet();
                        } else {
                            skipped.incrementAndGet();
                        }
                    } else {
                        skipped.incrementAndGet();
                    }
                } catch (Exception e) {
                    skipped.incrementAndGet();
                    LOGGER.log(Level.FINE, "Failed to place block at ({0},{1},{2}): {3}",
                        new Object[]{worldX, worldY, worldZ, e.getMessage()});
                }
            }

            // Schedule next batch or signal completion
            if (endIndex < blocks.size()) {
                placeBatch(world, blocks, endIndex, originX, originY, originZ, placed, skipped, done);
            } else {
                done.complete(null);
            }
        });
    }

    /**
     * Quick-load: places the prefab and returns a completion future.
     * Use this when you don't want to block the calling thread.
     */
    @Nonnull
    public static CompletableFuture<PrefabBounds> placePrefabAsync(
        @Nonnull World world,
        @Nonnull PrefabData prefab,
        int originX, int originY, int originZ
    ) {
        return CompletableFuture.supplyAsync(() ->
            placePrefab(world, prefab, originX, originY, originZ)
        );
    }
}
