package dev.hytalezombie.entity;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalezombie.manager.GameSession;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ECS system that listens for damage events targeting {@link ZombieEntity} instances
 * and forwards them to {@link GameSession#damageZombie(String, float, String)}.
 * <p>
 * Registered in {@link dev.hytalezombie.HytaleZombiePlugin#setup()} via
 * {@code getEntityStoreRegistry().registerSystem(...)}.
 */
public class ZombieDamageEventSystem extends DamageEventSystem {

    private static final Logger LOGGER = Logger.getLogger(ZombieDamageEventSystem.class.getName());

    private final GameSession gameSession;

    /**
     * @param gameSession the game session orchestrator (for damage/kill tracking)
     */
    public ZombieDamageEventSystem(@Nonnull GameSession gameSession) {
        this.gameSession = gameSession;
    }

    /**
     * Query for entities that have the {@link ZombieEntity} component.
     */
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return ZombieEntity.getComponentType();
    }

    /**
     * Run in the damage-inspection phase so we can intercept before other systems.
     */
    @Nonnull
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    /**
     * Called by the ECS framework for each zombie entity that takes damage.
     *
     * @param index  entity index within the chunk
     * @param chunk  the archetype chunk containing matching entities
     * @param store  the entity store
     * @param buffer the command buffer (for potential entity removal)
     * @param damage the damage event payload
     */
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull Damage damage
    ) {
        // Get the ZombieEntity component from the chunk
        ZombieEntity zombie = chunk.getComponent(index, ZombieEntity.getComponentType());
        if (zombie == null) return;

        // Use the UUID component for reliable lookup (set synchronously during spawn,
        // avoiding the async race condition of networkId-based lookup).
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        String zombieId = gameSession.getZombieIdByUuid(uuidComp.getUuid()).orElse(null);
        if (zombieId == null) {
            // Zombie not tracked by our game session (might be from another plugin or test)
            return;
        }

        int networkId = zombie.getNetworkId();

        float damageAmount = damage.getAmount();

        // Try to identify the attacker (the player who dealt damage).
        // Damage.Source can be EntitySource, CommandSource, or EnvironmentSource.
        String attackerPlayerId = null;
        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef.isValid()) {
                try {
                    UUIDComponent attackerUuidComp = store.getComponent(attackerRef, UUIDComponent.getComponentType());
                    if (attackerUuidComp != null) {
                        attackerPlayerId = attackerUuidComp.getUuid().toString();
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Could not resolve attacker UUID for damage to zombie {0}: {1}",
                            new Object[]{zombieId, e.getMessage()});
                }
            }
        }

        // Fallback: if we can't identify the attacker, still record damage
        String resolvedPlayerId = attackerPlayerId != null ? attackerPlayerId : "unknown";

        // Forward to the game session for damage tracking, points, and kill handling
        boolean killed = gameSession.damageZombie(zombieId, damageAmount, resolvedPlayerId);

        if (killed) {
            // Use CommandBuffer for safe entity removal during ECS iteration.
            // This avoids concurrent modification of the entity store that would
            // occur if removeEntity() were called directly during system execution.
            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
            buffer.removeEntity(entityRef, RemoveReason.REMOVE);

            LOGGER.log(Level.FINE, "ZombieDamageEventSystem: zombie {0} (networkId={1}) killed by player {2}",
                    new Object[]{zombieId, networkId, resolvedPlayerId});
        }
    }
}
