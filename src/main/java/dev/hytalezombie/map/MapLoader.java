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
 */
public class MapLoader {

    private static final Logger LOGGER = Logger.getLogger(MapLoader.class.getName());
    private static final int BATCH_SIZE = 500;

    // ── Data classes ──────────────────────────────────────────

    public static class PrefabData {
        @Nonnull public final List<PlacedBlock> blocks;
        public final int width, height, length;
        public final int unresolvedCount;

        public PrefabData(@Nonnull List<PlacedBlock> blocks, int width, int height, int length, int unresolvedCount) {
            this.blocks = blocks; this.width = width; this.height = height; this.length = length;
            this.unresolvedCount = unresolvedCount;
        }
    }

    public static class PlacedBlock {
        public final int x, y, z;
        public final int blockId;
        @Nonnull public final String name;
        @Nullable public final BlockType blockType;

        public PlacedBlock(int x, int y, int z, int blockId, @Nonnull String name,
                           @Nullable BlockType blockType) {
            this.x = x; this.y = y; this.z = z;
            this.blockId = blockId; this.name = name; this.blockType = blockType;
        }
        public boolean isValid() { return blockId > 0 && blockType != null; }
    }

    public static class PrefabBounds {
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public PrefabBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public int width()  { return maxX - minX + 1; }
        public int height() { return maxY - minY + 1; }
        public int length() { return maxZ - minZ + 1; }

        @Nonnull
        public Vector3f getCenterFloor() {
            return new Vector3f((minX + maxX) / 2.0f + 0.5f, minY + 1.0f, (minZ + maxZ) / 2.0f + 0.5f);
        }
    }

    // ── Prefab loading ────────────────────────────────────────

    /**
     * Loads a Hytale prefab JSON file and resolves all block names
     * to Hytale BlockType numeric IDs.
     *
     * @throws IOException if the file cannot be read
     */
    @Nonnull
    public static PrefabData loadPrefab(@Nonnull Path prefabPath) throws IOException {
        LOGGER.info("Reading prefab file: " + prefabPath.toAbsolutePath());
        String json = Files.readString(prefabPath, StandardCharsets.UTF_8);
        LOGGER.info("Prefab file size: " + json.length() + " bytes");

        List<PlacedBlock> blocks = new ArrayList<>();
        int unresolvedCount = 0;
        int maxX = 0, maxY = 0, maxZ = 0;

        int pos = json.indexOf("\"blocks\"");
        if (pos < 0) throw new IOException("Invalid prefab JSON: missing 'blocks' key");
        pos = json.indexOf('[', pos);
        if (pos < 0) throw new IOException("Invalid prefab JSON: blocks array not found");

        int depth = 0;
        int blockStart = -1;
        pos++;

        int parseCount = 0;
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
                        parseCount++;
                    }
                    blockStart = -1;
                }
            }
            pos++;
        }

        LOGGER.info("Parsed " + parseCount + " blocks, bounds " + (maxX+1) + "x" + (maxY+1) + "x" + (maxZ+1)
            + ", unresolved=" + unresolvedCount);

        if (blocks.isEmpty()) {
            LOGGER.warning("Prefab file contained no valid blocks!");
        }
        return new PrefabData(blocks, maxX + 1, maxY + 1, maxZ + 1, unresolvedCount);
    }

    @Nullable
    private static PlacedBlock parseBlock(@Nonnull String json) {
        int x = extractInt(json, "x");
        int y = extractInt(json, "y");
        int z = extractInt(json, "z");
        String name = extractString(json, "name");
        if (name == null) return null;

        // Skip fluids — they're handled separately and aren't blocks
        if (name.startsWith("Water_") || name.startsWith("Lava_")) return null;

        // Resolve to BlockType
        BlockType bt = BlockType.fromString(name);
        int blockId;
        if (bt != null) {
            blockId = BlockType.getAssetMap().getIndex(name);
            if (blockId == Integer.MIN_VALUE) blockId = 0;
        } else {
            blockId = 0;
            LOGGER.warning("Unknown block type: " + name);
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

    // ── Block placement ───────────────────────────────────────

    /**
     * Places all prefab blocks into the world at the given origin,
     * using {@link WorldChunk#setBlock} for correct rendering/physics.
     *
     * @return future completing with the placed structure's world bounds
     */
    @Nonnull
    public static CompletableFuture<PrefabBounds> placePrefabAsync(
        @Nonnull World world, @Nonnull PrefabData prefab,
        int originX, int originY, int originZ
    ) {
        List<PlacedBlock> blocks = prefab.blocks;
        if (blocks.isEmpty()) {
            return CompletableFuture.completedFuture(
                new PrefabBounds(originX, originY, originZ, originX, originY, originZ));
        }

        PrefabBounds bounds = new PrefabBounds(
            originX, originY, originZ,
            originX + prefab.width - 1, originY + prefab.height - 1, originZ + prefab.length - 1);

        AtomicInteger placed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        CompletableFuture<Void> done = new CompletableFuture<>();

        ChunkStore chunkStore = world.getChunkStore();
        placeChunkBatch(chunkStore, blocks, 0, originX, originY, originZ, placed, skipped, done);

        return done.thenApply(v -> {
            LOGGER.info("Prefab placed: " + placed.get() + " blocks placed, " + skipped.get() + " skipped");
            return bounds;
        });
    }

    private static void placeChunkBatch(
        @Nonnull ChunkStore chunkStore, @Nonnull List<PlacedBlock> blocks,
        int startIndex, int originX, int originY, int originZ,
        @Nonnull AtomicInteger placed, @Nonnull AtomicInteger skipped,
        @Nonnull CompletableFuture<Void> done
    ) {
        int endIndex = Math.min(startIndex + BATCH_SIZE, blocks.size());
        Store<ChunkStore> store = chunkStore.getStore();
        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

        for (int i = startIndex; i < endIndex; i++) {
            PlacedBlock block = blocks.get(i);
            if (!block.isValid()) { skipped.incrementAndGet(); continue; }

            int wx = originX + block.x;
            int wy = originY + block.y;
            int wz = originZ + block.z;

            int cx = ChunkUtil.chunkCoordinate(wx);
            int cz = ChunkUtil.chunkCoordinate(wz);
            long chunkIndex = ChunkUtil.indexChunk(cx, cz);
            int localX = wx & 31;
            int localZ = wz & 31;

            CompletableFuture<Void> bf = chunkStore
                .getChunkReferenceAsync(chunkIndex, 2)
                .thenAccept(ref -> {
                    if (ref != null && ref.isValid()) {
                        WorldChunk chunk = store.getComponent(ref, WorldChunk.getComponentType());
                        if (chunk != null) {
                            boolean ok = chunk.setBlock(localX, wy, localZ,
                                block.blockId, block.blockType, 0, 0, 0);
                            if (ok) placed.incrementAndGet();
                            else skipped.incrementAndGet();
                        } else skipped.incrementAndGet();
                    } else skipped.incrementAndGet();
                })
                .exceptionally(ex -> { skipped.incrementAndGet(); return null; });

            batchFutures.add(bf);
        }

        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                if (endIndex < blocks.size()) {
                    placeChunkBatch(chunkStore, blocks, endIndex, originX, originY, originZ,
                        placed, skipped, done);
                } else {
                    done.complete(null);
                }
            })
            .exceptionally(ex -> {
                LOGGER.warning("Batch placement error: " + ex.getMessage());
                if (endIndex < blocks.size()) {
                    placeChunkBatch(chunkStore, blocks, endIndex, originX, originY, originZ,
                        placed, skipped, done);
                } else {
                    done.complete(null);
                }
                return null;
            });
    }

    // ── World clearing ────────────────────────────────────────

    /**
     * Clears a rectangular area by setting all blocks to air (ID 0).
     * Only clears blocks in sections that are already loaded.
     *
     * <p>Call this before {@link #placePrefabAsync} to create a void
     * world where the imported structure is the only geometry.</p>
     */
    @Nonnull
    public static CompletableFuture<Void> clearAreaAsync(
        @Nonnull World world,
        int minX, int minY, int minZ, int maxX, int maxY, int maxZ
    ) {
        LOGGER.info("Clearing area: (" + minX + "," + minY + "," + minZ + ") to ("
            + maxX + "," + maxY + "," + maxZ + ")");

        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();

        int sMinX = ChunkUtil.chunkCoordinate(minX);
        int sMinY = ChunkUtil.chunkCoordinate(minY);
        int sMinZ = ChunkUtil.chunkCoordinate(minZ);
        int sMaxX = ChunkUtil.chunkCoordinate(maxX);
        int sMaxY = ChunkUtil.chunkCoordinate(maxY);
        int sMaxZ = ChunkUtil.chunkCoordinate(maxZ);

        AtomicInteger cleared = new AtomicInteger(0);
        List<CompletableFuture<Void>> sectionFutures = new ArrayList<>();

        for (int sy = sMinY; sy <= sMaxY; sy++) {
            for (int sz = sMinZ; sz <= sMaxZ; sz++) {
                for (int sx = sMinX; sx <= sMaxX; sx++) {
                    final int secX = sx, secY = sy, secZ = sz;
                    int swX = secX << 5, swY = secY << 5, swZ = secZ << 5;
                    int swMaxX = swX + 31, swMaxY = swY + 31, swMaxZ = swZ + 31;

                    if (swMaxX < minX || swX > maxX) continue;
                    if (swMaxY < minY || swY > maxY) continue;
                    if (swMaxZ < minZ || swZ > maxZ) continue;

                    CompletableFuture<Void> f = chunkStore
                        .getChunkSectionReferenceAsync(secX, secY, secZ)
                        .thenAccept(ref -> {
                            if (ref != null && ref.isValid()) {
                                BlockSection section = store.getComponent(ref, BlockSection.getComponentType());
                                if (section != null) {
                                    int lMinX = Math.max(0, minX - swX);
                                    int lMinY = Math.max(0, minY - swY);
                                    int lMinZ = Math.max(0, minZ - swZ);
                                    int lMaxX = Math.min(31, maxX - swX);
                                    int lMaxY = Math.min(31, maxY - swY);
                                    int lMaxZ = Math.min(31, maxZ - swZ);

                                    for (int ly = lMinY; ly <= lMaxY; ly++)
                                        for (int lz = lMinZ; lz <= lMaxZ; lz++)
                                            for (int lx = lMinX; lx <= lMaxX; lx++) {
                                                section.set(lx, ly, lz, 0, 0, 0);
                                                cleared.incrementAndGet();
                                            }
                                }
                            }
                        })
                        .exceptionally(ex -> null);

                    sectionFutures.add(f);
                }
            }
        }

        return CompletableFuture.allOf(sectionFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> LOGGER.info("Cleared " + cleared.get() + " blocks"));
    }
}
