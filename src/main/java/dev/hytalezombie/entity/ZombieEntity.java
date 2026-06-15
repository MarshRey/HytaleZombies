package dev.hytalezombie.entity;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * A custom zombie entity for HytaleZombie.
 * Extends LivingEntity so it can have health, take damage, and be a proper entity.
 * The model is assigned via {@link com.hypixel.hytale.server.core.modules.entity.component.ModelComponent}
 * when the entity holder is assembled in {@link EntitySpawnHelper}.
 */
public class ZombieEntity extends LivingEntity {

    public static final BuilderCodec<ZombieEntity> CODEC = BuilderCodec.builder(
        ZombieEntity.class, ZombieEntity::new, LivingEntity.CODEC
    ).build();

    /**
     * Gets the component type for ZombieEntity from the EntityModule registry.
     * This is registered in {@link dev.hytalezombie.HytaleZombiePlugin#setup()} via
     * {@code getEntityRegistry().registerEntity("hz_zombie", ZombieEntity.class, ...)}.
     */
    @Nonnull
    public static ComponentType<EntityStore, ZombieEntity> getComponentType() {
        return EntityModule.get().getComponentType(ZombieEntity.class);
    }

    public ZombieEntity() {
        super();
    }

    public ZombieEntity(@Nonnull World world) {
        super(world);
    }

    @Nonnull
    @Override
    public String toString() {
        return "ZombieEntity{id=" + getNetworkId() + ", uuid=" + getUuid() + "} " + super.toString();
    }
}
