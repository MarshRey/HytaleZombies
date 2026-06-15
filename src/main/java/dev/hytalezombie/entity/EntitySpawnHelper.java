package dev.hytalezombie.entity;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalezombie.model.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Helper for assembling and spawning Hytale entity holders into a world.
 * Uses the same EntityStore component-based pattern as
 * {@link com.hypixel.hytale.server.core.entity.entities.BlockEntity#assembleDefaultBlockEntity}.
 */
public class EntitySpawnHelper {

    private static final Logger LOGGER = Logger.getLogger(EntitySpawnHelper.class.getName());

    /**
     * The default model identifier for zombies.
     * "NPC_Human_Male" is a built-in humanoid model in Hytale's base assets.
     * Fallback attempts: "NPC_Zombie", "entity/humanoid", "entity/zombie"
     */
    public static final String DEFAULT_ZOMBIE_MODEL = "NPC_Human_Male";

    private EntitySpawnHelper() {
        // Utility class
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
     * <p>This assembles a full {@link Holder}{@code <EntityStore>} with all
     * required components and queues it for addition on the next tick.</p>
     *
     * @param world    the Hytale world to spawn into
     * @param position the position to spawn at (from our logical {@link Vector3f})
     * @param modelId  the model asset ID to use (e.g. "NPC_Human_Male"), or null for default
     * @return a {@link SpawnResult} with the network ID and entity ref, or {@link SpawnResult#FAILED} if spawning failed
     */
    @Nonnull
    public static SpawnResult spawnZombie(
            @Nonnull World world,
            @Nonnull Vector3f position,
            @Nullable String modelId
    ) {
        String resolvedModelId = modelId != null ? modelId : DEFAULT_ZOMBIE_MODEL;

        // Resolve the model asset
        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(resolvedModelId);
        if (modelAsset == null) {
            LOGGER.warning("Model asset '" + resolvedModelId + "' not found! Cannot spawn zombie.");
            return SpawnResult.FAILED;
        }

        // Create the model at unit scale
        Model model = Model.createUnitScaleModel(modelAsset);

        // Build the entity holder with all required components
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        // 1. The entity component itself (ZombieEntity)
        ZombieEntity zombieEntity = new ZombieEntity(world);
        holder.addComponent(ZombieEntity.getComponentType(), zombieEntity);

        // 2. Transform (position + rotation)
        org.joml.Vector3d hytalePosition = new org.joml.Vector3d(position.x(), position.y(), position.z());
        holder.addComponent(
            TransformComponent.getComponentType(),
            new TransformComponent(hytalePosition, Rotation3f.IDENTITY)
        );

        // 3. Model (visual appearance)
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));

        // 4. UUID (required for network tracking)
        holder.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());

        // 5. Network ID (required for entity tracking)
        holder.ensureComponent(NetworkId.getComponentType());

        // 6. Bounding box from the model (for collision/hit detection)
        com.hypixel.hytale.math.shape.Box modelBox = model.getBoundingBox();
        if (modelBox != null) {
            holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(modelBox));
        }

        // 7. Scale component (optional, 1.0 = normal size)
        holder.ensureComponent(EntityScaleComponent.getComponentType());

        // Add the entity to the world store directly
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.LOAD);
        if (ref == null || !ref.isValid()) {
            LOGGER.warning("Failed to add zombie entity to the world store.");
            return SpawnResult.FAILED;
        }

        int networkId = zombieEntity.getNetworkId();
        LOGGER.fine("Spawned zombie entity (networkId=" + networkId + ") at " + position +
                    " using model '" + resolvedModelId + "'");
        return new SpawnResult(networkId, ref);
    }

    /**
     * Spawns a zombie entity with the default model.
     *
     * @param world    the Hytale world to spawn into
     * @param position the position to spawn at
     * @return a {@link SpawnResult} with the network ID and entity ref, or {@link SpawnResult#FAILED} if spawning failed
     */
    @Nonnull
    public static SpawnResult spawnZombie(@Nonnull World world, @Nonnull Vector3f position) {
        return spawnZombie(world, position, null);
    }
}
