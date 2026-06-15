package dev.hytalezombie.map;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
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
 * and places all blocks into the world using {@link WorldChunk#setBlock}.
 *
 * <p>Blocks are placed asynchronously in batched groups by chunk. After all
 * blocks are placed, the surrounding area is cleared to air and the world
 * border is clamped to the structure bounds — effectively making the imported
 * structure the entire playable world.</p>
 *
 * <p>The prefab JSON format matches the Hytale schematic converter output:
 * <pre>{@code
 * {
 *   "version": 8,
 *   "blockIdVersion": 8,
 *   "bounds": {"width": 64, "height": 32, "length": 64},
 *   "blocks": [
 *     {"x": 0, "y": 0, "z": 0, "name": "Rock_Stone"},
 *     ...
 *   ]
 * }
 * }</pre>
 */
public class MapLoader {

    private static final Logger LOGGER = Logger.getLogger(MapLoader.class.getName());

    /** Maximum blocks to place per batch (batches are grouped by chunk). */
    private static final int BATCH_SIZE = 500;

    /**
     * Parsed prefab data ready for placement.
     */
    public static class PrefabData {
        @Nonnull
        public final List<PlacedBlock> blocks;
        public final int width, height, length;
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
     * A single block from the prefab with its resolved Hytale block ID and BlockType.
     */
    public static class PlacedBlock {
        public final int x, y, z;
        public final int blockId;
        @Nonnull
        public final String name;
        @Nullable
        public final BlockType blockType;

        public PlacedBlock(int x, int y, int z, int blockId, @Nonnull String name,
                           @Nullable BlockType blockType) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.name = name;
            this.blockType = blockType;
        }

        /** Returns true if this block was resolved to a valid Hytale BlockType. */
        public boolean isValid() {
            return blockId > 0 && blockType != null;
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

        /** Width in blocks (X). */
        public int width() { return maxX - minX + 1; }
        /** Height in blocks (Y). */
        public int height() { return maxY - minY + 1; }
        /** Length in blocks (Z). */
        public int length() { return maxZ - minZ + 1; }

        /** Center XZ of the prefab floor (y = floor level). */
        @Nonnull
        public Vector3f getCenterFloor() {
            return new Vector3f(
                (minX + maxX) / 2.0f + 0.5f,
                minY + 1.0f,
                (minZ + maxZ) / 2.0f + 0.5f
            );
        }
    }

    // ──────────────────────────────────────────────
    //  Prefab loading
    // ──────────────────────────────────────────────

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
        List<PlacedBlock> blocks = new ArrayList<>();
        int unresolvedCount = 0;
        int maxX = 0, maxY = 0, maxZ = 0;

        int pos = json.indexOf("\"blocks\"");
        if (pos < 0) throw new IOException("Invalid prefab JSON: missing 'blocks' array");
        pos = json.indexOf('[', pos);
        if (pos < 0) throw new IOException("Invalid prefab JSON: blocks array not found");

        int depth = 0;
        int blockStart = -1;
        pos++;

        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == '{' && depth == 0) blockStart = pos;
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0 && blockStart >= 0) {
                    PlacedBlock block = parseBlock(json.substring(blockStart, pos + 1));
                    if (block != null) {
                        blocks.add(block);
                        if (block.x > maxX) maxX = block.x;
                        if (block.y > maxY) maxY = block.y;
                        if (block.z > maxZ) maxZ = block.z;
                        if (!block.isValid()) unresolvedCount++;
                    }
                    blockStart = -1;
                }
            }
            pos++;
        }

        if (blocks.isEmpty()) LOGGER.warning("Prefab file contains no blocks");
        return new PrefabData(blocks, maxX + 1, maxY + 1, maxZ + 1, unresolvedCount);
    }

    @Nullable
    private static PlacedBlock parseBlock(@Nonnull String json) {
        int x = extractInt(json, "x");
        int y = extractInt(json, "y");
        int z = extractInt(json, "z");
        String name = extractString(json, "name");
        if (name == null) return null;

        // Resolve to BlockType and numeric ID
        BlockType bt = BlockType.fromString(name);
        int blockId;
        if (bt != null) {
            blockId = BlockType.getAssetMap().getIndex(name);
            if (blockId == Integer.MIN_VALUE) blockId = 0;
        } else {
            blockId = 0;
            if (!"Empty".equals(name) && !"Air".equals(name)) {
                LOGGER.log(Level.WARNING, "Unknown block type in prefab: {0}", name);
            }
        }
        return new PlacedBlock(x, y, z, blockId, name, bt);
    }

    private static int extractInt(@Nonnull String json, @Nonnull String key) {
        int idx = json.indexOf("\"" + key + "\":");
        if (idx < 0) return 0;
        idx += key.length() + 3;
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == idx) return 0;
        try { return Integer.parseInt(json.substring(idx, end)); }
        catch (NumberFormatException e) { return 0; }
    }

    @Nullable
    private static String extractString(@Nonnull String json, @Nonnull String key) {
        int idx = json.indexOf("\"" + key + "\":");
        if (idx < 0) return null;
        idx += key.length() + 3;
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        if (idx >= json.length() || json.charAt(idx) != '"') return null;
        int end = json.indexOf('"', idx + 1);
        if (end < 0) return null;
        return json.substring(idx + 1, end);
    }

    // ──────────────────────────────────────────────
    //  Block placement (using WorldChunk.setBlock)
    // ──────────────────────────────────────────────

    /**
     * Places all blocks from a {@link PrefabData} into the world at the given origin.
     *
     * <p>Uses {@link WorldChunk#setBlock} which handles physics, filler blocks,
     * lighting, and entity state automatically. Blocks are grouped by chunk and
     * placed in parallel batches.</p>
     *
     * <p>The future completes when all blocks have been placed.</p>
     *
     * @param world   the Hytale world
     * @param prefab  the parsed prefab data
     * @param originX world X for prefab (0,0,0)
     * @param originY world Y for prefab (0,0,0)
     * @param originZ world Z for prefab (0,0,0)
     * @return future completing with the placed structure's world bounds
     */
    @Nonnull
    public static CompletableFuture<PrefabBounds> placePrefabAsync(
        @Nonnull World world,
        @Nonnull PrefabData prefab,
        int originX, int originY, int originZ
    ) {
        List<PlacedBlock> blocks = prefab.blocks;
        if (blocks.isEmpty()) {
            return CompletableFuture.completedFuture(
                new PrefabBounds(originX, originY, originZ, originX, originY, originZ));
        }

        PrefabBounds bounds = new PrefabBounds(
            originX, originY, originZ,
            originX + prefab.width - 1,
            originY + prefab.height - 1,
            originZ + prefab.length - 1
        );

        AtomicInteger placed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        CompletableFuture<Void> done = new CompletableFuture<>();

        ChunkStore chunkStore = world.getChunkStore();

        // Start the first batch
        placeChunkBatch(chunkStore, blocks, 0, originX, originY, originZ, placed, skipped, done);

        return done.thenApply(v -> {
            LOGGER.log(Level.INFO, "Prefab placed: {0} blocks placed, {1} skipped, {2} unresolved",
                new Object[]{placed.get(), skipped.get(), prefab.unresolvedCount});
            return bounds;
        });
    }

    /**
     * Places a batch of blocks by chunk. For each block, resolves its chunk reference
     * asynchronously and calls {@link WorldChunk#setBlock}. When all blocks in the
     * batch have their futures scheduled, the next batch starts.
     */
    private static void placeChunkBatch(
        @Nonnull ChunkStore chunkStore,
        @Nonnull List<PlacedBlock> blocks,
        int startIndex,
        int originX, int originY, int originZ,
        @Nonnull AtomicInteger placed,
        @Nonnull AtomicInteger skipped,
        @Nonnull CompletableFuture<Void> done
    ) {
        int endIndex = Math.min(startIndex + BATCH_SIZE, blocks.size());
        Store<ChunkStore> store = chunkStore.getStore();

        // Group blocks by chunk index for efficient placement
        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

        for (int i = startIndex; i < endIndex; i++) {
            PlacedBlock block = blocks.get(i);
            if (!block.isValid()) {
                skipped.incrementAndGet();
                continue;
            }

            int worldX = originX + block.x;
            int worldY = originY + block.y;
            int worldZ = originZ + block.z;

            int chunkX = ChunkUtil.chunkCoordinate(worldX);
            int chunkZ = ChunkUtil.chunkCoordinate(worldZ);
            long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);

            // Local coords within the chunk (0-31)
            int localX = worldX & 31;
            int localZ = worldZ & 31;

            // Get chunk reference asynchronously
            CompletableFuture<Void> blockFuture = chunkStore
                .getChunkReferenceAsync(chunkIndex, 2)
                .thenAccept(ref -> {
                    if (ref != null && ref.isValid()) {
                        WorldChunk chunk = store.getComponent(ref, WorldChunk.getComponentType());
                        if (chunk != null) {
                            boolean ok = chunk.setBlock(localX, worldY, localZ,
                                block.blockId, block.blockType, 0, 0, 0);
                            if (ok) {
                                placed.incrementAndGet();
                            } else {
                                skipped.incrementAndGet();
                            }
                        } else {
                            skipped.incrementAndGet();
                        }
                    } else {
                        skipped.incrementAndGet();
                    }
                })
                .exceptionally(ex -> {
                    skipped.incrementAndGet();
                    LOGGER.log(Level.FINE, "Failed to place block at ({0},{1},{2}): {3}",
                        new Object[]{worldX, worldY, worldZ, ex.getMessage()});
                    return null;
                });

            batchFutures.add(blockFuture);
        }

        // When this batch completes, start the next batch or signal done
        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                if (endIndex < blocks.size()) {
                    placeChunkBatch(chunkStore, blocks, endIndex,
                        originX, originY, originZ, placed, skipped, done);
                } else {
                    done.complete(null);
                }
            })
            .exceptionally(ex -> {
                LOGGER.log(Level.WARNING, "Batch placement error: {0}", ex.getMessage());
                if (endIndex < blocks.size()) {
                    placeChunkBatch(chunkStore, blocks, endIndex,
                        originX, originY, originZ, placed, skipped, done);
                } else {
                    done.complete(null);
                }
                return null;
            });
    }

    // ──────────────────────────────────────────────
    //  World clearing — replace terrain with void
    // ──────────────────────────────────────────────

    /**
     * Clears a rectangular area by setting all blocks to air (Empty, ID 0).
     * Only clears blocks in chunks that are already loaded.
     *
     * <p>Call this before {@link #placePrefabAsync} to create a void world
     * where the imported structure is the only geometry.</p>
     *
     * <p>Clearing is done in 32×32×32 section batches to avoid overwhelming
     * the chunk system.</p>
     *
     * @param world the Hytale world
     * @param minX  minimum world X (inclusive)
     * @param minY  minimum world Y (inclusive)
     * @param minZ  minimum world Z (inclusive)
     * @param maxX  maximum world X (inclusive)
     * @param maxY  maximum world Y (inclusive)
     * @param maxZ  maximum world Z (inclusive)
     * @return future completing when all blocks in the loaded area are cleared
     */
    @Nonnull
    public static CompletableFuture<Void> clearAreaAsync(
        @Nonnull World world,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
    ) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();

        // Calculate section bounds
        int sectionMinX = ChunkUtil.chunkCoordinate(minX);
        int sectionMinY = ChunkUtil.chunkCoordinate(minY);
        int sectionMinZ = ChunkUtil.chunkCoordinate(minZ);
        int sectionMaxX = ChunkUtil.chunkCoordinate(maxX);
        int sectionMaxY = ChunkUtil.chunkCoordinate(maxY);
        int sectionMaxZ = ChunkUtil.chunkCoordinate(maxZ);

        AtomicInteger cleared = new AtomicInteger(0);
        List<CompletableFuture<Void>> sectionFutures = new ArrayList<>();

        for (int sy = sectionMinY; sy <= sectionMaxY; sy++) {
            for (int sz = sectionMinZ; sz <= sectionMaxZ; sz++) {
                for (int sx = sectionMinX; sx <= sectionMaxX; sx++) {
                    final int secX = sx;
                    final int secY = sy;
                    final int secZ = sz;

                    int secWorldMinX = secX << 5;
                    int secWorldMinY = secY << 5;
                    int secWorldMinZ = secZ << 5;
                    int secWorldMaxX = secWorldMinX + 31;
                    int secWorldMaxY = secWorldMinY + 31;
                    int secWorldMaxZ = secWorldMinZ + 31;

                    // Only process sections that intersect the target area
                    if (secWorldMaxX < minX || secWorldMinX > maxX) continue;
                    if (secWorldMaxY < minY || secWorldMinY > maxY) continue;
                    if (secWorldMaxZ < minZ || secWorldMinZ > maxZ) continue;

                    CompletableFuture<Void> f = chunkStore
                        .getChunkSectionReferenceAsync(secX, secY, secZ)
                        .thenAccept(ref -> {
                            if (ref != null && ref.isValid()) {
                                BlockSection section = store.getComponent(ref, BlockSection.getComponentType());
                                if (section != null) {
                                    // Clear only the blocks within the target area
                                    int localMinX = Math.max(0, minX - secWorldMinX);
                                    int localMinY = Math.max(0, minY - secWorldMinY);
                                    int localMinZ = Math.max(0, minZ - secWorldMinZ);
                                    int localMaxX = Math.min(31, maxX - secWorldMinX);
                                    int localMaxY = Math.min(31, maxY - secWorldMinY);
                                    int localMaxZ = Math.min(31, maxZ - secWorldMinZ);

                                    for (int ly = localMinY; ly <= localMaxY; ly++) {
                                        for (int lz = localMinZ; lz <= localMaxZ; lz++) {
                                            for (int lx = localMinX; lx <= localMaxX; lx++) {
                                                section.set(lx, ly, lz, 0, 0, 0);
                                                cleared.incrementAndGet();
                                            }
                                        }
                                    }
                                }
                            }
                        })
                        .exceptionally(ex -> {
                            LOGGER.log(Level.FINE, "Failed to clear section ({0},{1},{2}): {3}",
                                new Object[]{secX, secY, secZ, ex.getMessage()});
                            return null;
                        });

                    sectionFutures.add(f);
                }
            }
        }

        return CompletableFuture.allOf(sectionFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> LOGGER.log(Level.INFO, "Cleared {0} blocks in area ({1},{2},{3})→({4},{5},{6})",
                new Object[]{cleared.get(), minX, minY, minZ, maxX, maxY, maxZ}));
    }

}
