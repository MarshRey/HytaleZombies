package dev.hytalezombie.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.entity.EntitySpawnHelper;
import dev.hytalezombie.model.*;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Orchestrates the main game loop for a HytaleZombie match.
 * Manages zombie spawning, round progression, power-ups, and economy.
 * This is a logical representation that would integrate with Hytale's
 * entity system for actual in-game behavior.
 */
public class GameSession {

    private static final Logger LOGGER = Logger.getLogger(GameSession.class.getName());

    // Dependencies
    private final Supplier<HytaleZombieConfig> configSupplier;
    private final RoundManager roundManager;
    private final PlayerDataManager playerDataManager;
    private final BarrierManager barrierManager;
    private final SpawnManager spawnManager;

    // Game state
    private boolean sessionActive;
    private int tickCounter;
    private int spawnTicker;
    private int totalZombiesToSpawn;
    private int zombiesSpawnedThisRound;
    private int zombiesKilledThisRound;

    // Zombie tracking (logical representation)
    private final Map<String, ZombieInstance> activeZombies;

    // Active power-ups
    private final Map<PowerUp.PowerUpType, PowerUp> activePowerUps;

    // Player perks
    private final Map<String, Set<Perk.PerkType>> playerPerks;

    // Player weapons
    private final Map<String, List<Weapon>> playerWeapons;

    // Hytale SDK integration
    private World world;
    private final Map<Integer, String> networkIdToZombieId;
    private final Map<UUID, String> uuidToZombieId;

    // Player position tracking for zombie AI targeting
    private final Map<String, Vector3f> playerPositions;

    /**
     * Logical representation of a zombie in the game.
     * Would be linked to a Hytale entity in actual gameplay.
     */
    public static class ZombieInstance {
        private final String id;
        private final String targetPlayerId;
        private float health;
        private final float maxHealth;
        private final float speed;
        private final Vector3f spawnPosition;
        private Vector3f currentPosition;
        private boolean isAlive;
        private boolean isAttackingBarrier;
        private String targetBarrierZone;
        private int networkId;
        private UUID entityUuid;
        private Ref<EntityStore> entityRef;

        public ZombieInstance(String id, float health, float speed, Vector3f spawnPosition) {
            this(id, null, health, speed, spawnPosition);
        }

        public ZombieInstance(String id, String targetPlayerId, float health, float speed, Vector3f spawnPosition) {
            this.id = id;
            this.targetPlayerId = targetPlayerId;
            this.health = health;
            this.maxHealth = health;
            this.speed = speed;
            this.spawnPosition = spawnPosition;
            this.currentPosition = new Vector3f(spawnPosition.x(), spawnPosition.y(), spawnPosition.z());
            this.isAlive = true;
            this.isAttackingBarrier = false;
            this.targetBarrierZone = null;
            this.networkId = -1;
            this.entityRef = null;
        }

        public String getId() { return id; }
        public Optional<String> getTargetPlayerId() { return Optional.ofNullable(targetPlayerId); }
        public float getHealth() { return health; }
        public float getMaxHealth() { return maxHealth; }
        public float getSpeed() { return speed; }
        public Vector3f getSpawnPosition() { return spawnPosition; }
        public Vector3f getCurrentPosition() { return currentPosition; }
        public void setCurrentPosition(Vector3f pos) { this.currentPosition = pos; }
        public boolean isAlive() { return isAlive; }
        public boolean isAttackingBarrier() { return isAttackingBarrier; }
        public Optional<String> getTargetBarrierZone() { return Optional.ofNullable(targetBarrierZone); }
        public int getNetworkId() { return networkId; }
        public void setNetworkId(int networkId) { this.networkId = networkId; }
        @Nullable
        public UUID getEntityUuid() { return entityUuid; }
        public void setEntityUuid(@Nullable UUID entityUuid) { this.entityUuid = entityUuid; }
        public Optional<Ref<EntityStore>> getEntityRef() { return Optional.ofNullable(entityRef); }
        public void setEntityRef(@Nullable Ref<EntityStore> entityRef) { this.entityRef = entityRef; }

        public void damage(float amount) {
            this.health -= amount;
            if (this.health <= 0) {
                this.health = 0;
                this.isAlive = false;
            }
        }

        public void setAttackingBarrier(boolean attacking, String zoneId) {
            this.isAttackingBarrier = attacking;
            this.targetBarrierZone = attacking ? zoneId : null;
        }

        public float getHealthPercent() {
            return health / maxHealth;
        }
    }

    public GameSession(
            @Nonnull Supplier<HytaleZombieConfig> configSupplier,
            @Nonnull RoundManager roundManager,
            @Nonnull PlayerDataManager playerDataManager,
            @Nonnull BarrierManager barrierManager,
            @Nonnull SpawnManager spawnManager
    ) {
        this.configSupplier = configSupplier;
        this.roundManager = roundManager;
        this.playerDataManager = playerDataManager;
        this.barrierManager = barrierManager;
        this.spawnManager = spawnManager;

        this.sessionActive = false;
        this.tickCounter = 0;
        this.spawnTicker = 0;
        this.totalZombiesToSpawn = 0;
        this.zombiesSpawnedThisRound = 0;
        this.zombiesKilledThisRound = 0;

        this.activeZombies = new ConcurrentHashMap<>();
        this.activePowerUps = new ConcurrentHashMap<>();
        this.playerPerks = new ConcurrentHashMap<>();
        this.playerWeapons = new ConcurrentHashMap<>();
        this.networkIdToZombieId = new ConcurrentHashMap<>();
        this.uuidToZombieId = new ConcurrentHashMap<>();
        this.playerPositions = new ConcurrentHashMap<>();
    }

    // ==================== MATCH LIFECYCLE ====================

    /**
     * Starts a new match. Resets all game state and begins round 1.
     */
    public void startMatch() {
        if (sessionActive) return;

        roundManager.startMatch();
        sessionActive = true;
        tickCounter = 0;
        spawnTicker = 0;
        zombiesSpawnedThisRound = 0;
        zombiesKilledThisRound = 0;
        activeZombies.clear();
        activePowerUps.clear();
        playerPerks.clear();
        playerWeapons.clear();
        playerDataManager.resetAll();

        // Give all tracked players starting points
        HytaleZombieConfig cfg = configSupplier.get();
        int startingPoints = cfg.getStartingPoints();
        // Players get starting points on join (handled externally via getOrCreatePlayerData)
        LOGGER.log(Level.INFO, "Players will receive {0} starting points", startingPoints);
        LOGGER.log(Level.INFO, "Game session started! Round 1");
    }

    /**
     * Ends the current match.
     */
    public void endMatch() {
        if (!sessionActive) return;
        sessionActive = false;
        roundManager.endMatch();
        activeZombies.clear();
        activePowerUps.clear();
        LOGGER.log(Level.INFO, "Game session ended.");
    }

    /**
     * Determines if the session is active.
     */
    public boolean isSessionActive() {
        return sessionActive;
    }

    // ==================== MAIN GAME TICK ====================

    /**
     * Called every game tick (20 times per second).
     * Handles spawning, power-up timers, and round progression.
     */
    public void tick() {
        if (!sessionActive) return;

        tickCounter++;

        // Tick power-up timers
        tickPowerUps();

        // Handle zombie spawning
        if (zombiesSpawnedThisRound < totalZombiesToSpawn) {
            handleSpawning();
        }

        // Run zombie AI (movement toward players)
        tickZombieAI();

        // Check round completion
        checkRoundComplete();
    }

    // ==================== ZOMBIE SPAWNING ====================

    /**
     * Calculates and sets up the zombie spawn count for the current round.
     * Call this when a round starts.
     */
    public void prepareRoundSpawns() {
        int playerCount = playerDataManager.getPlayerCount();
        if (playerCount == 0) return;

        HytaleZombieConfig cfg = configSupplier.get();
        totalZombiesToSpawn = roundManager.getSpawnCount(playerCount);
        zombiesSpawnedThisRound = 0;
        zombiesKilledThisRound = 0;
        spawnTicker = 0;

        LOGGER.log(Level.INFO, "Preparing to spawn {0} zombies for round {1}",
                new Object[]{totalZombiesToSpawn, roundManager.getCurrentRound()});
    }

    /**
     * Handles spawning logic during the game tick.
     */
    private void handleSpawning() {
        HytaleZombieConfig cfg = configSupplier.get();
        int spawnDelay = cfg.getSpawnDelayTicks();

        // Only spawn when the delay elapses
        if (spawnTicker < spawnDelay) {
            spawnTicker++;
            return;
        }

        // Reset ticker and spawn a zombie
        spawnTicker = 0;

        Optional<SpawnNode> spawnNode = spawnManager.getRandomSpawnNode();
        if (spawnNode.isEmpty()) return;

        SpawnNode node = spawnNode.get();
        Vector3f position = spawnManager.getRandomizedPosition(node);

        ZombieInstance zombie = createZombie(position);
        activeZombies.put(zombie.getId(), zombie);
        roundManager.incrementActiveZombies();
        zombiesSpawnedThisRound++;

        LOGGER.log(Level.FINE, "Spawned zombie at {0} from zone {1}",
                new Object[]{position, node.getZoneId()});
    }

    /**
     * Creates a new zombie instance with stats scaled to the current round.
     * When a Hytale World reference is available, also spawns a real entity via {@link EntitySpawnHelper}.
     */
    private ZombieInstance createZombie(Vector3f position) {
        float health = roundManager.getScaledZombieHealth();
        float speed = roundManager.getScaledZombieSpeed();
        String zombieId = "zombie_" + tickCounter + "_" + UUID.randomUUID().toString().substring(0, 8);
        UUID entityUuid = UUID.randomUUID();

        ZombieInstance zombie = new ZombieInstance(zombieId, health, speed, position);
        zombie.setEntityUuid(entityUuid);

        // Register the UUID mapping IMMEDIATELY (before the async entity spawn)
        // This ensures ZombieDamageEventSystem can always find this zombie via UUID,
        // even if damage arrives before the async callback sets the networkId mapping.
        uuidToZombieId.put(entityUuid, zombieId);

        // Attempt to spawn a real Hytale entity if we have a world reference
        if (world != null) {
            // spawnZombie is now async (enqueued on world thread via world.execute()).
            // Pass the pre-generated UUID so the entity's UUIDComponent matches our mapping.
            EntitySpawnHelper.spawnZombie(world, position, EntitySpawnHelper.getRandomZombieModel(), entityUuid)
                .thenAccept(result -> {
                    if (result != EntitySpawnHelper.SpawnResult.FAILED && result.entityRef() != null) {
                        zombie.setNetworkId(result.networkId());
                        zombie.setEntityRef(result.entityRef());
                        networkIdToZombieId.put(result.networkId(), zombieId);
                        LOGGER.log(Level.FINE, "Spawned real Hytale zombie entity (networkId={0}) for {1}",
                                new Object[]{result.networkId(), zombieId});
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to spawn Hytale entity for zombie {0} - created logical-only", zombieId);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "Error spawning Hytale entity for zombie {0}: {1}",
                            new Object[]{zombieId, ex.getMessage()});
                    return null;
                });
        }

        return zombie;
    }

    /**
     * Runs AI movement for all active zombies each tick.
     * Moves each zombie toward the nearest target point at its current speed.
     * Updates both logical position and the Hytale entity's TransformComponent.
     */
    /** Ticks between world position syncs to reduce world thread load. */
    private static final int AI_SYNC_INTERVAL = 5;

    /** Target point for zombies to move toward (will be replaced with nearest player). */
    private static final Vector3f DEFAULT_AI_TARGET = new Vector3f(0, 0, 0);

    private int aiSyncCounter;

    private void tickZombieAI() {
        if (activeZombies.isEmpty()) return;

        aiSyncCounter++;

        // Determine target point: use nearest player if available,
        // fall back to default (world origin)
        Vector3f targetPoint = DEFAULT_AI_TARGET;

        // Process logical movement for all active zombies
        float tickSpeed = 1.0f / 20.0f;

        for (ZombieInstance zombie : activeZombies.values()) {
            if (!zombie.isAlive()) continue;

            // Find the nearest player to this zombie
            Vector3f nearestTarget = targetPoint;
            float nearestDistSq = Float.MAX_VALUE;
            Vector3f zombiePos = zombie.getCurrentPosition();

            for (Map.Entry<String, Vector3f> playerEntry : playerPositions.entrySet()) {
                Vector3f playerPos = playerEntry.getValue();
                float pdx = playerPos.x() - zombiePos.x();
                float pdz = playerPos.z() - zombiePos.z();
                float distSq = pdx * pdx + pdz * pdz;
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearestTarget = playerPos;
                }
            }

            // Move toward the nearest target
            float dx = nearestTarget.x() - zombiePos.x();
            float dz = nearestTarget.z() - zombiePos.z();
            float dist = (float) Math.sqrt(dx * dx + dz * dz);

            if (dist < 0.5f) continue;

            float moveAmount = zombie.getSpeed() * tickSpeed;
            float nx = dx / dist * moveAmount;
            float nz = dz / dist * moveAmount;

            if (nx * nx + nz * nz > dist * dist) {
                zombie.setCurrentPosition(new Vector3f(nearestTarget.x(), zombiePos.y(), nearestTarget.z()));
            } else {
                zombie.setCurrentPosition(new Vector3f(zombiePos.x() + nx, zombiePos.y(), zombiePos.z() + nz));
            }
        }

        // Sync entity positions and movement states on the world thread.
        // Only sync every AI_SYNC_INTERVAL ticks to avoid saturating the world thread.
        // Uses direct position-setting (X/Z only) instead of Velocity component because
        // LivingEntity types in Hytale do not have an automatic physics system that
        // reads Velocity and updates TransformComponent. Gravity is applied manually
        // when the entity is not on ground.
        if (world != null && aiSyncCounter % AI_SYNC_INTERVAL == 0) {
            // Snapshot the map to avoid concurrent modification issues
            final Map<String, ZombieInstance> snapshot = new HashMap<>(activeZombies);
            // Capture player positions for target computation on the world thread
            final Map<String, Vector3f> playerPosSnapshot = new HashMap<>(playerPositions);
            final float dt = AI_SYNC_INTERVAL / 20.0f; // seconds elapsed since last sync
            final float gravity = 20.0f; // blocks/sec^2 — applied when not on ground
            world.execute(() -> {
                for (ZombieInstance zombie : snapshot.values()) {
                    if (!zombie.isAlive()) continue;
                    try {
                        zombie.getEntityRef().ifPresent(ref -> {
                            if (!ref.isValid()) return;
                            final Store<EntityStore> store = ref.getStore();
                            final TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                            if (transform == null) return;

                            // Find nearest player target for this zombie
                            Vector3f target = DEFAULT_AI_TARGET;
                            float nearestDistSq = Float.MAX_VALUE;
                            Vector3f zombiePos = zombie.getCurrentPosition();

                            for (Map.Entry<String, Vector3f> playerEntry : playerPosSnapshot.entrySet()) {
                                Vector3f playerPos = playerEntry.getValue();
                                float pdx = playerPos.x() - zombiePos.x();
                                float pdz = playerPos.z() - zombiePos.z();
                                float distSq = pdx * pdx + pdz * pdz;
                                if (distSq < nearestDistSq) {
                                    nearestDistSq = distSq;
                                    target = playerPos;
                                }
                            }

                            // === Read current state from Hytale ===
                            final org.joml.Vector3d currentPos = transform.getPosition();
                            final MovementStatesComponent movementStatesComp =
                                store.getComponent(ref, MovementStatesComponent.getComponentType());
                            final MovementStates states = movementStatesComp != null
                                ? movementStatesComp.getMovementStates() : null;

                            // Sync logical position from actual Hytale transform
                            final Vector3f logicalPos = new Vector3f(
                                (float) currentPos.x(), (float) currentPos.y(), (float) currentPos.z());
                            zombie.setCurrentPosition(logicalPos);

                            // Compute direction to target (horizontal only)
                            final float dx = target.x() - logicalPos.x();
                            final float dz = target.z() - logicalPos.z();
                            final float dist = (float) Math.sqrt(dx * dx + dz * dz);

                            // === Determine new position ===
                            double newX = currentPos.x();
                            double newY = currentPos.y();
                            double newZ = currentPos.z();

                            if (dist >= 0.5f) {
                                final float nx = dx / dist;
                                final float nz = dz / dist;
                                final float speed = zombie.getSpeed();
                                final float moveAmount = speed * dt;

                                newX = currentPos.x() + nx * moveAmount;
                                newZ = currentPos.z() + nz * moveAmount;

                                // Set rotation to face movement direction (Minecraft yaw convention)
                                final float yaw = (float) Math.toDegrees(Math.atan2(-nx, nz));
                                transform.setRotation(new Rotation3f(0, yaw, 0));

                                // Update movement states for walking animation
                                if (states != null) {
                                    states.walking = true;
                                    states.running = speed > 1.5f;
                                    states.idle = false;
                                    states.horizontalIdle = false;
                                }
                            } else {
                                // Close enough — set idle
                                if (states != null) {
                                    states.walking = false;
                                    states.idle = true;
                                    states.horizontalIdle = true;
                                }
                            }

                            // === Apply gravity ===
                            // Apply downward acceleration when the entity is not on ground,
                            // but clamp to a minimum Y (spawn height minus a safety margin)
                            // to prevent infinite falling if onGround is never set by Hytale.
                            final double minY = zombie.getSpawnPosition().y() - 2.0;
                            if (states != null && !states.onGround && currentPos.y() > minY) {
                                newY = Math.max(minY, currentPos.y() - gravity * dt * dt);
                                states.falling = true;
                            } else if (states != null) {
                                states.falling = false;
                            }

                            // === Write position back ===
                            transform.setPosition(new org.joml.Vector3d(newX, newY, newZ));

                            // Update logical position
                            zombie.setCurrentPosition(new Vector3f((float) newX, (float) newY, (float) newZ));
                        });
                    } catch (Exception e) {
                        // Entity may have been removed— safe to skip
                    }
                }
            });
        }
    }

    /**
     * Returns how many zombies still need to spawn this round.
     */
    public int getRemainingZombiesToSpawn() {
        return totalZombiesToSpawn - zombiesSpawnedThisRound;
    }

    /**
     * Returns the total number of zombies for this round (after calculation).
     */
    public int getTotalZombiesForRound() {
        return totalZombiesToSpawn;
    }

    // ==================== ZOMBIE DAMAGE / KILLS ====================

    /**
     * Called when a player damages a zombie.
     * @param zombieId the zombie's unique ID
     * @param damage amount of damage
     * @param playerId the player who dealt damage
     * @return true if the zombie died from this damage
     */
    public boolean damageZombie(String zombieId, float damage, String playerId) {
        ZombieInstance zombie = activeZombies.get(zombieId);
        if (zombie == null || !zombie.isAlive()) return false;

        // Apply Double Points multiplier
        boolean doublePoints = activePowerUps.containsKey(PowerUp.PowerUpType.DOUBLE_POINTS);

        // Apply Insta-Kill
        if (activePowerUps.containsKey(PowerUp.PowerUpType.INSTAKILL)) {
            damage = zombie.getHealth(); // One-hit kill
        }

        zombie.damage(damage);

        if (!zombie.isAlive()) {
            // Zombie killed
            activeZombies.remove(zombieId);
            roundManager.decrementActiveZombies();
            zombiesKilledThisRound++;

            // Clean up UUID and networkId mappings.
            // Entity removal is deferred to the damage event system
            // (ZombieDamageEventSystem) which uses CommandBuffer for safe
            // entity removal during ECS iteration.
            if (zombie.getEntityUuid() != null) {
                uuidToZombieId.remove(zombie.getEntityUuid());
            }
            if (zombie.getNetworkId() >= 0) {
                networkIdToZombieId.remove(zombie.getNetworkId());
            }

            // Award points
            PlayerData playerData = playerDataManager.getPlayerData(playerId);
            if (playerData != null) {
                HytaleZombieConfig cfg = configSupplier.get();
                int points = doublePoints ? cfg.getPointsPerKill() * 2 : cfg.getPointsPerKill();
                playerData.addPoints(points);
                playerData.incrementKills();
            }

            LOGGER.log(Level.FINE, "Zombie {0} killed by player {1}",
                    new Object[]{zombieId, playerId});
            return true;
        }

        // Award points for hitting (not killing) - 10 points per hit
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData != null) {
            int hitPoints = doublePoints ? 20 : 10;
            playerData.addPoints(hitPoints);
        }

        return false;
    }

    /**
     * Called when a nuke power-up is activated.
     * Kills all active zombies and awards points to all players.
     */
    public void nukeAllZombies() {
        HytaleZombieConfig cfg = configSupplier.get();
        int pointsPerKill = cfg.getPointsPerKill();

        for (ZombieInstance zombie : activeZombies.values()) {
            if (zombie.isAlive()) {
                zombie.damage(zombie.getHealth()); // Kill it
                zombiesKilledThisRound++;
            }
        }

        // Clean up all UUID mappings
        for (ZombieInstance zombie : activeZombies.values()) {
            if (zombie.getEntityUuid() != null) {
                uuidToZombieId.remove(zombie.getEntityUuid());
            }
        }

        // Remove all Hytale entities from the world.
        // Collect entity refs first, then defer removal to the world thread
        // via world.execute() to avoid calling removeEntity() from the
        // game loop thread (which can cause thread-safety issues).
        if (world != null) {
            java.util.List<Ref<EntityStore>> refsToRemove = new java.util.ArrayList<>();
            for (ZombieInstance zombie : activeZombies.values()) {
                zombie.getEntityRef().ifPresent(ref -> {
                    if (ref.isValid()) {
                        refsToRemove.add(ref);
                    }
                });
                if (zombie.getNetworkId() >= 0) {
                    networkIdToZombieId.remove(zombie.getNetworkId());
                }
            }
            if (!refsToRemove.isEmpty()) {
                world.execute(() -> {
                    com.hypixel.hytale.component.Store<EntityStore> store =
                        world.getEntityStore().getStore();
                    for (Ref<EntityStore> ref : refsToRemove) {
                        if (ref.isValid()) {
                            store.removeEntity(ref, RemoveReason.REMOVE);
                        }
                    }
                });
            }
        }

        // Award points for nuke kills to all players
        int killed = activeZombies.size();
        if (killed > 0) {
            // In actual gameplay, this would broadcast "NUKE" to all players
        }
        activeZombies.clear();

        // Decrement active count properly
        for (int i = 0; i < killed; i++) {
            roundManager.decrementActiveZombies();
        }

        LOGGER.log(Level.INFO, "NUKE! {0} zombies eliminated.", killed);
    }

    // ==================== POWER-UP SYSTEM ====================

    /**
     * Activates a power-up for the game session.
     */
    public void activatePowerUp(PowerUp.PowerUpType type) {
        PowerUp powerUp = new PowerUp(type);
        powerUp.activate(20); // 20 ticks per second
        activePowerUps.put(type, powerUp);

        LOGGER.log(Level.INFO, "Power-up activated: {0}", type.getDisplayName());

        // Handle instant-effect power-ups
        switch (type) {
            case NUKE -> nukeAllZombies();
            case CARPENTER -> repairAllBarriers();
            case MAX_AMMO -> refillAllAmmo();
            case BONUS_POINTS -> awardBonusPoints();
        }
    }

    /**
     * Ticks all active power-ups, removing expired ones.
     */
    private void tickPowerUps() {
        Iterator<Map.Entry<PowerUp.PowerUpType, PowerUp>> iterator =
                activePowerUps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PowerUp.PowerUpType, PowerUp> entry = iterator.next();
            if (entry.getValue().tick() && entry.getValue().isExpired()) {
                iterator.remove();
                LOGGER.log(Level.FINE, "Power-up expired: {0}", entry.getKey().getDisplayName());
            }
        }
    }

    /**
     * Checks if a specific power-up type is currently active.
     */
    public boolean isPowerUpActive(PowerUp.PowerUpType type) {
        return activePowerUps.containsKey(type);
    }

    /**
     * Gets remaining ticks for an active power-up.
     */
    public long getPowerUpRemainingTicks(PowerUp.PowerUpType type) {
        PowerUp powerUp = activePowerUps.get(type);
        return powerUp != null ? powerUp.getRemainingTicks() : 0;
    }

    // ==================== POWER-UP EFFECTS ====================

    /**
     * Repairs all barriers back to INTACT state.
     */
    private void repairAllBarriers() {
        // In a real implementation, we'd iterate all barriers and reset their state
        LOGGER.log(Level.INFO, "CARPENTER! All barriers repaired.");
    }

    /**
     * Refills all players' ammo.
     */
    private void refillAllAmmo() {
        LOGGER.log(Level.INFO, "MAX AMMO! All ammo refilled.");
    }

    /**
     * Awards bonus points to all players.
     */
    private void awardBonusPoints() {
        int bonusAmount = 400;
        // In actual gameplay, this would give bonus points to all players
        LOGGER.log(Level.INFO, "BONUS POINTS! {0} points awarded.", bonusAmount);
    }

    // ==================== ROUND COMPLETION ====================

    /**
     * Checks if the round is complete (all zombies spawned and eliminated).
     */
    private void checkRoundComplete() {
        if (totalZombiesToSpawn > 0
                && zombiesSpawnedThisRound >= totalZombiesToSpawn
                && activeZombies.isEmpty()) {
            LOGGER.log(Level.INFO, "Round {0} complete!", roundManager.getCurrentRound());
            // Advance to the next round and prepare its spawns
            roundManager.advanceRound();
            prepareRoundSpawns();
            LOGGER.log(Level.INFO, "Round {0} has begun!",
                    roundManager.getCurrentRound());
        }
    }

    /**
     * Calculates the total number of zombies that have been killed this round.
     */
    public int getZombiesKilledThisRound() {
        return zombiesKilledThisRound;
    }

    /**
     * Gets the number of zombies currently alive.
     */
    public int getActiveZombieCount() {
        return activeZombies.size();
    }

    /**
     * Gets the number of players currently in the game.
     */
    public int getPlayerCount() {
        return playerDataManager.getPlayerCount();
    }

    // ==================== ECONOMY / SHOP ====================

    /**
     * Attempts to purchase a weapon for a player.
     * @return true if purchase was successful
     */
    public boolean purchaseWeapon(String playerId, Weapon weapon) {
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        if (!playerData.deductPoints(weapon.getCost())) {
            return false; // Not enough points
        }

        // Add weapon to player's arsenal
        playerWeapons.computeIfAbsent(playerId, k -> new ArrayList<>()).add(weapon);

        LOGGER.log(Level.INFO, "Player {0} purchased weapon: {1}",
                new Object[]{playerId, weapon.getDisplayName()});
        return true;
    }

    /**
     * Attempts to purchase a perk for a player.
     * @return true if purchase was successful
     */
    public boolean purchasePerk(String playerId, Perk.PerkType perkType) {
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        int cost = perkType.getCost();
        if (!playerData.deductPoints(cost)) {
            return false; // Not enough points
        }

        // Track the perk
        playerPerks.computeIfAbsent(playerId, k -> EnumSet.noneOf(Perk.PerkType.class))
                .add(perkType);

        LOGGER.log(Level.INFO, "Player {0} purchased perk: {1}",
                new Object[]{playerId, perkType.getDisplayName()});
        return true;
    }

    /**
     * Checks if a player has a specific perk.
     */
    public boolean hasPerk(String playerId, Perk.PerkType perkType) {
        Set<Perk.PerkType> perks = playerPerks.get(playerId);
        return perks != null && perks.contains(perkType);
    }

    /**
     * Attempts to purchase/unlock a door to a zone.
     * @return true if purchase was successful
     */
    public boolean purchaseDoor(String playerId, MapZone zone) {
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        if (zone.isUnlocked()) return true; // Already unlocked

        int cost = zone.getDoorCost();
        if (!playerData.deductPoints(cost)) {
            return false; // Not enough points
        }

        zone.setUnlocked(true);
        LOGGER.log(Level.INFO, "Player {0} unlocked zone: {1} (cost: {2})",
                new Object[]{playerId, zone.getDisplayName(), cost});
        return true;
    }

    /**
     * Gets the weapons a player has purchased this session.
     */
    public List<Weapon> getPlayerWeapons(String playerId) {
        return playerWeapons.getOrDefault(playerId, Collections.emptyList());
    }

    /**
     * Gets the perks a player has purchased this session.
     */
    public Set<Perk.PerkType> getPlayerPerks(String playerId) {
        return playerPerks.getOrDefault(playerId, Collections.emptySet());
    }

    // ==================== ZOMBIE SPAWN CONTROL ====================

    /**
     * Returns the RoundManager for accessing round/scaling data.
     */
    @Nonnull
    public RoundManager getRoundManager() {
        return roundManager;
    }

    /**
     * Gets the total number of zombies to spawn this round.
     */
    public int getTotalZombiesToSpawn() {
        return totalZombiesToSpawn;
    }

    /**
     * Gets the number of zombies spawned so far this round.
     */
    public int getZombiesSpawnedThisRound() {
        return zombiesSpawnedThisRound;
    }

    /**
     * Gets the current game tick.
     */
    public int getTickCounter() {
        return tickCounter;
    }

    /**
     * Gets the active zombie instances (for testing/debugging).
     */
    public Map<String, ZombieInstance> getActiveZombies() {
        return Collections.unmodifiableMap(activeZombies);
    }

    /**
     * Gets the active power-ups.
     */
    public Map<PowerUp.PowerUpType, PowerUp> getActivePowerUps() {
        return Collections.unmodifiableMap(activePowerUps);
    }

    // ==================== HYTALE SDK INTEGRATION ====================

    /**
     * Updates a player's position for zombie AI targeting.
     * Called periodically from the plugin's game loop.
     */
    public void updatePlayerPosition(@Nonnull String playerId, @Nonnull Vector3f position) {
        playerPositions.put(playerId, position);
    }

    /**
     * Removes a player's tracked position when they disconnect.
     */
    public void removePlayerPosition(@Nonnull String playerId) {
        playerPositions.remove(playerId);
    }

    /**
     * Sets the Hytale World reference for entity spawning.
     * Set this when the first player joins (from {@code PlayerReadyEvent}).
     */
    public void setWorld(@Nullable World world) {
        this.world = world;
    }

    /**
     * Gets the current Hytale World reference, or null if not yet set.
     */
    @Nullable
    public World getWorld() {
        return world;
    }

    /**
     * Looks up a zombie's logical ID by its Hytale network ID.
     * Used by {@code ZombieDamageEventSystem} to route damage events.
     */
    @Nonnull
    public Optional<String> getZombieIdByNetworkId(int networkId) {
        return Optional.ofNullable(networkIdToZombieId.get(networkId));
    }

    /**
     * Looks up a zombie's logical ID by its UUID (set synchronously during creation).
     * Used by {@code ZombieDamageEventSystem} to route damage events reliably,
     * avoiding the async race condition of networkId-based lookup.
     */
    @Nonnull
    public Optional<String> getZombieIdByUuid(@Nonnull UUID uuid) {
        return Optional.ofNullable(uuidToZombieId.get(uuid));
    }

    /**
     * Manually spawns a zombie instance into the game, bypassing the round's
     * spawn schedule. Used for testing (/hz spawnzombie) or special events.
     * @param zoneId the zone to spawn from, or null for a random active zone
     * @return the spawned zombie, or empty if no spawn nodes available
     */
    @Nonnull
    public Optional<ZombieInstance> spawnZombieInstance(@Nullable String zoneId) {
        Optional<SpawnNode> nodeOpt;
        if (zoneId != null) {
            List<SpawnNode> nodes = spawnManager.getNodesInZone(zoneId);
            if (nodes.isEmpty()) return Optional.empty();
            nodeOpt = Optional.of(nodes.get(new Random().nextInt(nodes.size())));
        } else {
            nodeOpt = spawnManager.getRandomSpawnNode();
        }

        if (nodeOpt.isEmpty()) return Optional.empty();

        SpawnNode node = nodeOpt.get();
        Vector3f position = spawnManager.getRandomizedPosition(node);
        ZombieInstance zombie = createZombie(position);
        activeZombies.put(zombie.getId(), zombie);
        roundManager.incrementActiveZombies();
        zombiesSpawnedThisRound++;

        LOGGER.log(Level.FINE, "Manually spawned zombie {0} at {1} from zone {2}",
                new Object[]{zombie.getId(), position, node.getZoneId()});
        return Optional.of(zombie);
    }

    /**
     * Spawns a test zombie into the world without modifying round state.
     * Used by /hz spawnzombie when no match is active, for testing entity spawning.
     * The zombie is tracked in activeZombies but does NOT affect:
     *   - roundManager active zombie count
     *   - zombiesSpawnedThisRound counter
     *   - round auto-advancement
     * @param zoneId the zone to spawn from, or null for a random occupied zone
     * @return the spawned zombie, or empty if no spawn nodes available
     */
    @Nonnull
    public Optional<ZombieInstance> spawnTestZombie(@Nullable String zoneId) {
        Optional<SpawnNode> nodeOpt;
        if (zoneId != null) {
            List<SpawnNode> nodes = spawnManager.getNodesInZone(zoneId);
            if (nodes.isEmpty()) return Optional.empty();
            nodeOpt = Optional.of(nodes.get(new Random().nextInt(nodes.size())));
        } else {
            nodeOpt = spawnManager.getRandomSpawnNode();
        }

        if (nodeOpt.isEmpty()) return Optional.empty();

        SpawnNode node = nodeOpt.get();
        Vector3f position = spawnManager.getRandomizedPosition(node);
        ZombieInstance zombie = createZombie(position);
        activeZombies.put(zombie.getId(), zombie);

        LOGGER.log(Level.INFO, "Test-spawned zombie {0} at {1} from zone {2} (no round tracking)",
                new Object[]{zombie.getId(), position, node.getZoneId()});
        return Optional.of(zombie);
    }
}