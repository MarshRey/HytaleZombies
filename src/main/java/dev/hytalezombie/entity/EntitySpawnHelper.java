package dev.hytalezombie.entity;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalezombie.model.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Helper for assembling and spawning Hytale entity holders into a world.
 * Uses the same EntityStore component-based pattern as
 * {@link com.hypixel.hytale.server.core.entity.entities.BlockEntity#assembleDefaultBlockEntity}.
 *
 * <p>Entity spawning is enqueued on the world's thread via {@link World#execute(Runnable)}
 * as required by the Hytale ECS documentation. The method returns a
 * {@link CompletableFuture} that completes when the entity has been added
 * during the next world tick.</p>
 */
public class EntitySpawnHelper {

    private static final Logger LOGGER = Logger.getLogger(EntitySpawnHelper.class.getName());

    /**
     * The default model identifier for zombies.
     * Uses Hytale's built-in {@code Zombie} asset from the entity list.
     */
    public static final String DEFAULT_ZOMBIE_MODEL = "Zombie";

    /**
     * All available zombie model variants in Hytale's asset map.
     * Used by {@link #getRandomZombieModel()} to randomize spawn appearances.
     *
     * @see <a href="https://hytalemodding.dev/en/docs/server/entities">Hytale Entity List</a>
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
     *
     * @return one of the {@link #ZOMBIE_MODELS} entries
     */
    @Nonnull
    public static String getRandomZombieModel() {
        return ZOMBIE_MODELS[RANDOM.nextInt(ZOMBIE_MODELS.length)];
    }

    /**
     * Result of a successful entity spawn, carrying both the network ID
     * and the entity reference for later removal.
     */
    public record SpawnResult(int networkId, @Nonnull Ref<EntityStore> entityRef) {
        /** Sentinel for failed spawns. */
        public static final SpawnResult FAILED = new SpawnResult(-1, null);
    }

    /**
     * Spawns a zombie entity into the given world at the specified position.
     *
     * <p>Because Hytale enqueues entity operations on the world's thread via
     * {@link World#execute(Runnable)}, this method returns a
     * {@link CompletableFuture} that resolves once the entity has been added
     * on the next world tick.</p>
     *
     * <p>The entity is assembled with all required components documented in
     * the Hytale Modding guide:
     * <a href="https://hytalemodding.dev/en/docs/guides/plugin/spawning-entities">
     * Spawning Entities</a>.</p>
     *
     * @param world    the Hytale world to spawn into
     * @param position the position to spawn at (from our logical {@link Vector3f})
     * @param modelId  the model asset ID to use (e.g. "Zombie"), or null for default
     * @return a {@link CompletableFuture} completing with the {@link SpawnResult},
     *         or {@link SpawnResult#FAILED} if spawning failed
     */
    @Nonnull
    public static CompletableFuture<SpawnResult> spawnZombie(
            @Nonnull World world,
            @Nonnull Vector3f position,
            @Nullable String modelId,
            @Nullable UUID uuid
    ) {
        CompletableFuture<SpawnResult> future = new CompletableFuture<>();
        String resolvedModelId = modelId != null ? modelId : DEFAULT_ZOMBIE_MODEL;
        UUID entityUuid = uuid != null ? uuid : UUID.randomUUID();

        // Entity operations must run on the world's thread
        world.execute(() -> {
            try {
                // Resolve the model asset
                ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(resolvedModelId);
                if (modelAsset == null) {
                    LOGGER.warning("Model asset '" + resolvedModelId + "' not found! Cannot spawn zombie.");
                    future.complete(SpawnResult.FAILED);
                    return;
                }

                // Create the model at unit scale
                Model model = Model.createUnitScaleModel(modelAsset);

                // Get the entity store (must be done on world thread)
                Store<EntityStore> store = world.getEntityStore().getStore();

                // Build the entity holder with all required components
                Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

                // 1. The entity component itself (ZombieEntity)
                // NOTE: Use no-arg constructor! The LegacyEntityHolderSystem registered
                // in EntityModule calls loadIntoWorld() during addEntity(), which asserts
                // that this.world is null. Using ZombieEntity(world) pre-sets this.world
                // and causes an "Entity is already in a world" crash.
                ZombieEntity zombieEntity = new ZombieEntity();
                holder.addComponent(ZombieEntity.getComponentType(), zombieEntity);

                // 2. Transform (position + rotation)
                org.joml.Vector3d hytalePosition = new org.joml.Vector3d(position.x(), position.y(), position.z());
                holder.addComponent(
                    TransformComponent.getComponentType(),
                    new TransformComponent(hytalePosition, Rotation3f.IDENTITY)
                );

                // 3. Model (visual appearance)
                holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));

                // 4. PersistentModel (required for entity persistence and rendering)
                holder.addComponent(
                    PersistentModel.getComponentType(),
                    new PersistentModel(model.toReference())
                );

                // 5. UUID (required for network tracking) — use caller-provided UUID if given
                holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUuid));

                // 6. Network ID (explicitly taken from the store's ID pool, as per docs)
                int networkId = store.getExternalData().takeNextNetworkId();
                holder.addComponent(NetworkId.getComponentType(), new NetworkId(networkId));

                // 7. Bounding box from the model (for collision/hit detection)
                com.hypixel.hytale.math.shape.Box modelBox = model.getBoundingBox();
                if (modelBox != null) {
                    holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(modelBox));
                }

                // 8. Scale component (optional, 1.0 = normal size)
                holder.ensureComponent(EntityScaleComponent.getComponentType());

                // 9. Add the entity to the world store with proper SPAWN reason.
                // NOTE: Intentionally NOT adding Interactable — zombies are enemies,
                // not NPCs, and Interactable causes an unwanted "F to interact" prompt.
                Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
                if (ref == null || !ref.isValid()) {
                    LOGGER.warning("Failed to add zombie entity to the world store.");
                    future.complete(SpawnResult.FAILED);
                    return;
                }

                LOGGER.fine("Spawned zombie entity (networkId=" + networkId + ") at " + position +
                            " using model '" + resolvedModelId + "'");
                future.complete(new SpawnResult(networkId, ref));

            } catch (Exception e) {
                LOGGER.warning("Exception while spawning zombie: " + e.getMessage());
                future.complete(SpawnResult.FAILED);
            }
        });

        return future;
    }

    /**
     * Spawns a zombie entity with a random UUID.
     * Convenience overload for callers that don't need to pre-register the UUID mapping.
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
     * Spawns a zombie entity with the default model and a random zombie variant.
     *
     * @param world    the Hytale world to spawn into
     * @param position the position to spawn at
     * @return a {@link CompletableFuture} completing with the {@link SpawnResult}
     */
    @Nonnull
    public static CompletableFuture<SpawnResult> spawnZombie(@Nonnull World world, @Nonnull Vector3f position) {
        return spawnZombie(world, position, null);
    }

    /**
     * Spawns a zombie entity at a player's position by reading their
     * {@link TransformComponent} and assembling the entity in a single
     * {@link World#execute(Runnable)} block.
     *
     * <p>This is the method to use from command handlers, because
     * {@code Store.getComponent()} asserts it runs on the {@code WorldThread}
     * while command dispatch runs on the common {@code ForkJoinPool}.
     * All store operations happen inside a single {@code world.execute()}
     * invocation to avoid thread assertion errors.</p>
     *
     * @param world    the Hytale world to spawn into
     * @param playerRef  the player's entity reference (from {@code CommandContext.senderAsPlayerRef()})
     * @param modelId  the model asset ID to use (e.g. "Zombie"), or null for random
     * @return a {@link CompletableFuture} completing with the {@link SpawnResult}
     */
    @Nonnull
    public static CompletableFuture<SpawnResult> spawnZombieAtPlayer(
            @Nonnull World world,
            @Nonnull Ref<EntityStore> playerRef,
            @Nullable String modelId
    ) {
        CompletableFuture<SpawnResult> future = new CompletableFuture<>();
        String resolvedModelId = modelId != null ? modelId : DEFAULT_ZOMBIE_MODEL;

        // All store operations happen on the world's thread
        world.execute(() -> {
            try {
                Store<EntityStore> store = playerRef.getStore();

                // Read player position from TransformComponent
                TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
                org.joml.Vector3d playerPos;
                if (transform != null) {
                    playerPos = transform.getPosition();
                } else {
                    LOGGER.warning("Player has no TransformComponent; falling back to (0,1,0)");
                    playerPos = new org.joml.Vector3d(0, 1, 0);
                }
                Vector3f position = new Vector3f((float) playerPos.x(), (float) playerPos.y(), (float) playerPos.z());

                // Resolve the model asset
                ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(resolvedModelId);
                if (modelAsset == null) {
                    LOGGER.warning("Model asset '" + resolvedModelId + "' not found! Cannot spawn zombie.");
                    future.complete(SpawnResult.FAILED);
                    return;
                }

                // Create the model
                Model model = Model.createUnitScaleModel(modelAsset);

                // Build the entity holder with all required components
                Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

                // 1. The entity component itself (ZombieEntity)
                // NOTE: Use no-arg constructor! The LegacyEntityHolderSystem registered
                // in EntityModule calls loadIntoWorld() during addEntity(), which asserts
                // that this.world is null. Using ZombieEntity(world) pre-sets this.world
                // and causes an "Entity is already in a world" crash.
                ZombieEntity zombieEntity = new ZombieEntity();
                holder.addComponent(ZombieEntity.getComponentType(), zombieEntity);

                // 2. Transform (player position + identity rotation)
                holder.addComponent(
                    TransformComponent.getComponentType(),
                    new TransformComponent(new org.joml.Vector3d(position.x(), position.y(), position.z()), Rotation3f.IDENTITY)
                );

                // 3. Model (visual appearance)
                holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));

                // 4. PersistentModel (required for entity persistence and rendering)
                holder.addComponent(
                    PersistentModel.getComponentType(),
                    new PersistentModel(model.toReference())
                );

                // 5. UUID (required for network tracking)
                holder.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());

                // 6. Network ID (explicitly taken from the store's ID pool)
                int networkId = store.getExternalData().takeNextNetworkId();
                holder.addComponent(NetworkId.getComponentType(), new NetworkId(networkId));

                // 7. Bounding box from the model (for collision/hit detection)
                com.hypixel.hytale.math.shape.Box modelBox = model.getBoundingBox();
                if (modelBox != null) {
                    holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(modelBox));
                }

                // 8. Scale component (optional, 1.0 = normal size)
                holder.ensureComponent(EntityScaleComponent.getComponentType());

                // 9. Add the entity to the world store with proper SPAWN reason.
                // NOTE: Intentionally NOT adding Interactable — zombies are enemies,
                // not NPCs, and Interactable causes an unwanted "F to interact" prompt.
                Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
                if (ref == null || !ref.isValid()) {
                    LOGGER.warning("Failed to add zombie entity at player's position.");
                    future.complete(SpawnResult.FAILED);
                    return;
                }

                LOGGER.fine("Spawned zombie at player (networkId=" + networkId + ") using model '" + resolvedModelId
                            + "' at " + position);
                future.complete(new SpawnResult(networkId, ref));

            } catch (Exception e) {
                LOGGER.warning("Exception in spawnZombieAtPlayer: " + e.getMessage());
                future.complete(SpawnResult.FAILED);
            }
        });

        return future;
    }
}
