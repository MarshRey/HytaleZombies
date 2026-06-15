package dev.hytalezombie.entity;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.hytalezombie.model.Vector3f;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Helper for spawning zombie NPCs via Hytale's {@link NPCPlugin}.
 * Uses the NPC system's built-in AI (pathfinding, BodyMotion, animation)
 * instead of manual entity assembly and position-setting.
 *
 * <p>Entity spawning is enqueued on the world's thread via
 * {@link World#execute(Runnable)} as required by the Hytale ECS docs.</p>
 */
public class EntitySpawnHelper {

    private static final Logger LOGGER = Logger.getLogger(EntitySpawnHelper.class.getName());

    /** The NPC role name for our zombie. Must match the JSON asset filename. */
    public static final String ZOMBIE_ROLE = "hz_zombie";

    /**
     * The default model identifier for zombies.
     * Uses Hytale's built-in {@code Zombie} asset from the entity list.
     */
    public static final String DEFAULT_ZOMBIE_MODEL = "Zombie";

    /**
     * All available zombie model variants in Hytale's asset map.
     */
    private static final String[] ZOMBIE_MODELS = {
        "Zombie",
        "Zombie_Aberrant",
        "Zombie_Aberrant_Big",
        "Zombie_Aberrant_Small",
        "Zombie_Burnt",
        "Zombie_Frost",
        "Zombie_Sand",
        "Zombie_Werewolf"
    };

    private static final Random RANDOM = new Random();

    private EntitySpawnHelper() {
        // Utility class
    }

    /**
     * Returns a random zombie model identifier from the available variants.
     */
    @Nonnull
    public static String getRandomZombieModel() {
        return ZOMBIE_MODELS[RANDOM.nextInt(ZOMBIE_MODELS.length)];
    }

    /**
     * Result of a successful entity spawn.
     */
    public record SpawnResult(int networkId, @Nullable Ref<EntityStore> entityRef, @Nullable NPCEntity npcEntity, @Nullable UUID entityUuid) {
        /** Sentinel for failed spawns. */
        public static final SpawnResult FAILED = new SpawnResult(-1, null, null, null);
    }

    /**
     * Spawns a zombie NPC into the given world at the specified position
     * using Hytale's NPC system. The NPC will use the "hz_zombie" role
     * which defines pursuit behavior and walking animation.
     *
     * @param world     the Hytale world to spawn into
     * @param position  the position to spawn at
     * @param modelId   the model asset ID to use (e.g. "Zombie"), or null for random
     * @param uuid      pre-generated UUID, or null for random
     * @return a {@link CompletableFuture} completing with the {@link SpawnResult}
     */
    @Nonnull
    public static CompletableFuture<SpawnResult> spawnZombie(
            @Nonnull World world,
            @Nonnull Vector3f position,
            @Nullable String modelId,
            @Nullable UUID uuid
    ) {
        CompletableFuture<SpawnResult> future = new CompletableFuture<>();
        String resolvedModelId = modelId != null ? modelId : getRandomZombieModel();

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                NPCPlugin npcPlugin = NPCPlugin.get();
                if (npcPlugin == null) {
                    LOGGER.warning("NPCPlugin not available! Cannot spawn zombie NPC.");
                    future.complete(SpawnResult.FAILED);
                    return;
                }

                int roleIndex = npcPlugin.getIndex(ZOMBIE_ROLE);
                if (roleIndex < 0) {
                    LOGGER.warning("Zombie NPC role '" + ZOMBIE_ROLE + "' not found! "
                            + "Make sure the role JSON is in assets/hytalezombie/Server/NPC/Roles/");
                    future.complete(SpawnResult.FAILED);
                    return;
                }

                // Resolve the model asset and create the model
                Model model = null;
                ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(resolvedModelId);
                if (modelAsset != null) {
                    model = Model.createUnitScaleModel(modelAsset);
                }

                // Use NPCPlugin to spawn the entity with full AI support.
                // UUID is handled by NPCPlugin (randomly generated); we read it back post-spawn.
                Vector3d pos = new Vector3d(position.x(), position.y(), position.z());
                var result = npcPlugin.spawnEntity(store, roleIndex, pos, Rotation3f.IDENTITY, model,
                    null, // no preAddToWorld needed
                    // postSpawn: complete the future with spawn result + read back UUID
                    (npcEntity, ref, s) -> {
                        UUID actualUuid = null;
                        int networkId = -1;
                        com.hypixel.hytale.server.core.entity.UUIDComponent uuidComp =
                            s.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                        if (uuidComp != null) {
                            actualUuid = uuidComp.getUuid();
                        }
                        future.complete(new SpawnResult(networkId, ref, npcEntity, actualUuid));
                    }
                );

                if (result == null) {
                    LOGGER.warning("NPCPlugin.spawnEntity returned null for role '" + ZOMBIE_ROLE + "'");
                    future.complete(SpawnResult.FAILED);
                }

            } catch (Exception e) {
                LOGGER.warning("Exception while spawning zombie NPC: " + e.getMessage());
                future.complete(SpawnResult.FAILED);
            }
        });

        return future;
    }

    /**
     * Spawns a zombie NPC with a random UUID.
     */
    @Nonnull
    public static CompletableFuture<SpawnResult> spawnZombie(
            @Nonnull World world,
            @Nonnull Vector3f position,
            @Nullable String modelId
    ) {
        return spawnZombie(world, position, modelId, null);
    }

    /**
     * Spawns a zombie NPC with the default model and random variant.
     */
    @Nonnull
    public static CompletableFuture<SpawnResult> spawnZombie(@Nonnull World world, @Nonnull Vector3f position) {
        return spawnZombie(world, position, null);
    }

    /**
     * Spawns a zombie NPC at a player's position by reading their TransformComponent.
     */
    @Nonnull
    public static CompletableFuture<SpawnResult> spawnZombieAtPlayer(
            @Nonnull World world,
            @Nonnull Ref<EntityStore> playerRef,
            @Nullable String modelId
    ) {
        CompletableFuture<SpawnResult> future = new CompletableFuture<>();

        world.execute(() -> {
            try {
                Store<EntityStore> store = playerRef.getStore();
                TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
                Vector3d playerPos;
                if (transform != null) {
                    playerPos = transform.getPosition();
                } else {
                    playerPos = new Vector3d(0, 1, 0);
                }
                Vector3f position = new Vector3f((float) playerPos.x(), (float) playerPos.y(), (float) playerPos.z());

                // Reuse the main spawn logic
                spawnZombie(world, position, modelId).thenAccept(result -> {
                    if (result != SpawnResult.FAILED) {
                        future.complete(result);
                    } else {
                        future.complete(SpawnResult.FAILED);
                    }
                }).exceptionally(ex -> {
                    future.complete(SpawnResult.FAILED);
                    return null;
                });

            } catch (Exception e) {
                LOGGER.warning("Exception in spawnZombieAtPlayer: " + e.getMessage());
                future.complete(SpawnResult.FAILED);
            }
        });

        return future;
    }
}
