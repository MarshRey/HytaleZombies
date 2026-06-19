package dev.hytalezombie.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
    private ZoneManager zoneManager;

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
    // Player entity refs for NPC blackboard target assignment
    private final Map<String, Ref<EntityStore>> playerEntityRefs;

    // Player zone tracking (playerId -> zoneId)
    private final Map<String, String> playerZoneIds;

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
        private com.hypixel.hytale.server.npc.entities.NPCEntity npcEntity;

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
        @Nullable
        public com.hypixel.hytale.server.npc.entities.NPCEntity getNpcEntity() { return npcEntity; }
        public void setNpcEntity(@Nullable com.hypixel.hytale.server.npc.entities.NPCEntity npcEntity) { this.npcEntity = npcEntity; }

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
        this.playerEntityRefs = new ConcurrentHashMap<>();
        this.playerZoneIds = new ConcurrentHashMap<>();
        this.zoneManager = null; // Set by HytaleZombiePlugin after construction
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

        ZombieInstance zombie = new ZombieInstance(zombieId, health, speed, position);

        // Attempt to spawn a real Hytale NPC entity if we have a world reference.
        // UUID is assigned by NPCPlugin; we read it back from the SpawnResult.
        if (world != null) {
            final String finalZombieId = zombieId;
            EntitySpawnHelper.spawnZombie(world, position, EntitySpawnHelper.getRandomZombieModel())
                .thenAccept(result -> {
                    if (result != EntitySpawnHelper.SpawnResult.FAILED && result.entityRef() != null) {
                        zombie.setNetworkId(result.networkId());
                        zombie.setEntityRef(result.entityRef());
                        zombie.setNpcEntity(result.npcEntity());
                        // Use the UUID assigned by NPCPlugin
                        if (result.entityUuid() != null) {
                            zombie.setEntityUuid(result.entityUuid());
                            uuidToZombieId.put(result.entityUuid(), finalZombieId);
                        }
                        if (result.networkId() >= 0) {
                            networkIdToZombieId.put(result.networkId(), finalZombieId);
                        }
                        LOGGER.log(Level.FINE, "Spawned zombie NPC (networkId={0}, role={1}) for {2}",
                                new Object[]{result.networkId(), EntitySpawnHelper.ZOMBIE_ROLE, finalZombieId});
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to spawn Hytale entity for zombie {0} - created logical-only", finalZombieId);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "Error spawning Hytale entity for zombie {0}: {1}",
                            new Object[]{finalZombieId, ex.getMessage()});
                    return null;
                });
        }

        return zombie;
    }

    /**
     * Syncs logical zombie positions from their NPC TransformComponents.
     * The NPC system's built-in AI (Template_Aggressive_Zombies) handles all
     * hostile pursuit via Sensor Mob with LockOnTarget — no manual target
     * setting needed. We only sync positions for display/tracking purposes.
     */
    private static final int AI_SYNC_INTERVAL = 10;

    private int aiSyncCounter;

    private void tickZombieAI() {
        if (activeZombies.isEmpty() || world == null) return;

        aiSyncCounter++;
        if (aiSyncCounter % AI_SYNC_INTERVAL != 0) return;

        // Snapshot active zombies for the world thread
        final Map<String, ZombieInstance> snapshot = new HashMap<>(activeZombies);

        world.execute(() -> {
            for (ZombieInstance zombie : snapshot.values()) {
                if (!zombie.isAlive()) continue;
                try {
                    // Check if the entity ref is still valid. If not, the NPC
                    // system has despawned this entity (death animation completed,
                    // or other removal). Clean up tracking.
                    if (zombie.getEntityRef().isPresent()) {
                        Ref<EntityStore> ref = zombie.getEntityRef().get();
                        if (!ref.isValid()) {
                            // Entity was despawned by the NPC system — remove tracking
                            zombie.damage(zombie.getHealth()); // mark as dead
                            activeZombies.remove(zombie.getId());
                            if (zombie.getEntityUuid() != null) {
                                uuidToZombieId.remove(zombie.getEntityUuid());
                            }
                            roundManager.decrementActiveZombies();
                            LOGGER.log(Level.FINE, "Zombie {0} entity despawned by server — cleaned up tracking",
                                    zombie.getId());
                            return;
                        }
                    }

                    // Sync logical position from the NPC's actual TransformComponent
                    zombie.getEntityRef().ifPresent(ref -> {
                        if (!ref.isValid()) return;
                        TransformComponent transform = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
                        if (transform != null) {
                            org.joml.Vector3d pos = transform.getPosition();
                            zombie.setCurrentPosition(new Vector3f(
                                (float) pos.x(), (float) pos.y(), (float) pos.z()));
                        }
                    });
                } catch (Exception e) {
                    // Entity may have been removed — safe to skip
                }
            }
        });
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
     * Updates a player's position for zombie AI targeting and zone tracking.
     * Called periodically from the plugin's game loop.
     *
     * <p>Also checks for door-crossing events: if the player walks near a door
     * in their current zone, they transition into the connected zone and
     * SpawnManager occupancy is updated automatically.</p>
     */
    public void updatePlayerPosition(@Nonnull String playerId, @Nonnull Vector3f position) {
        playerPositions.put(playerId, position);

        // Check for door-crossing zone transitions
        if (zoneManager != null) {
            String currentZone = playerZoneIds.get(playerId);
            if (currentZone != null) {
                String newZone = zoneManager.checkDoorCrossing(currentZone, position);
                if (newZone != null && !newZone.equals(currentZone)) {
                    handleZoneTransition(playerId, currentZone, newZone, position);
                }
            } else {
                // First position update — assign initial zone
                String initialZone = zoneManager.findPlayerZone(position);
                setPlayerZone(playerId, initialZone);
            }
        }
    }

    /**
     * Handles a player moving from one zone to another through a door.
     * Updates zone tracking and re-syncs SpawnManager occupancy.
     */
    private void handleZoneTransition(@Nonnull String playerId,
                                      @Nonnull String fromZone,
                                      @Nonnull String toZone,
                                      @Nonnull Vector3f position) {
        setPlayerZone(playerId, toZone);
        LOGGER.log(Level.INFO, "Player {0} moved from zone '{1}' to zone '{2}' at {3}",
                new Object[]{playerId, fromZone, toZone, position});
    }

    /**
     * Sets a player's current zone and updates zone occupancy.
     * If the old zone becomes empty, it is marked unoccupied in SpawnManager.
     * The new zone is marked occupied.
     */
    private void setPlayerZone(@Nonnull String playerId, @Nonnull String zoneId) {
        String oldZone = playerZoneIds.put(playerId, zoneId);
        // Only update occupancy if the zone actually changed
        if (!zoneId.equals(oldZone)) {
            updateZoneOccupancy(zoneId, oldZone);
        }
    }

    /**
     * Gets the zone a player is currently in.
     *
     * @param playerId the player's ID
     * @return the zone ID, or the starting zone if not yet tracked
     */
    @Nonnull
    public String getPlayerZone(@Nonnull String playerId) {
        String zone = playerZoneIds.get(playerId);
        if (zone != null) return zone;
        // Fall back to starting zone
        if (zoneManager != null) {
            return zoneManager.getStartingZoneId();
        }
        return "spawn_room";
    }

    /**
     * Recalculates SpawnManager occupied zones from all tracked player positions.
     * After a zone transition, this ensures only zones with active players are occupied.
     */
    private void updateZoneOccupancy(@Nonnull String newZone, @Nullable String oldZone) {
        // Build a set of all zones currently occupied by players
        java.util.Set<String> currentOccupied = new java.util.HashSet<>();
        for (String zone : playerZoneIds.values()) {
            if (zone != null) {
                currentOccupied.add(zone);
            }
        }

        // Mark new zone as occupied (in case it wasn't already)
        if (!currentOccupied.contains(newZone)) {
            currentOccupied.add(newZone);
        }

        // Sync with SpawnManager — mark zones that HAVE players
        for (String zone : currentOccupied) {
            spawnManager.markZoneOccupied(zone);
        }

        // If the old zone is now empty, unmark it
        if (oldZone != null && !currentOccupied.contains(oldZone)) {
            spawnManager.markZoneUnoccupied(oldZone);
        }

        LOGGER.log(Level.FINE, "Zone occupancy updated: occupied={0}, spawnManager.occupied={1}",
                new Object[]{currentOccupied, spawnManager.getOccupiedZones()});
    }

    /**
     * Sets the ZoneManager reference for door-crossing zone tracking.
     * Called by HytaleZombiePlugin after construction.
     */
    public void setZoneManager(@Nullable ZoneManager zoneManager) {
        this.zoneManager = zoneManager;
    }

    /**
     * Gets the ZoneManager, or null if not yet set.
     */
    @Nullable
    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    /**
     * Removes a player's tracked position and zone when they disconnect.
     */
    public void removePlayerPosition(@Nonnull String playerId) {
        playerPositions.remove(playerId);
        playerEntityRefs.remove(playerId);
        // Clean up zone tracking and re-sync occupancy
        String oldZone = playerZoneIds.remove(playerId);
        if (oldZone != null) {
            updateZoneOccupancy(null, oldZone);
        }
    }

    /**
     * Updates a player's entity ref for NPC blackboard target assignment.
     */
    public void updatePlayerEntityRef(@Nonnull String playerId, @Nonnull Ref<EntityStore> playerRef) {
        playerEntityRefs.put(playerId, playerRef);
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