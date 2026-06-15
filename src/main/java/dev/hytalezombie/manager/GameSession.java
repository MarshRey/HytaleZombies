package dev.hytalezombie.manager;

import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.model.*;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;

import javax.annotation.Nonnull;
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
        private boolean isAlive;
        private boolean isAttackingBarrier;
        private String targetBarrierZone;

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
            this.isAlive = true;
            this.isAttackingBarrier = false;
            this.targetBarrierZone = null;
        }

        public String getId() { return id; }
        public Optional<String> getTargetPlayerId() { return Optional.ofNullable(targetPlayerId); }
        public float getHealth() { return health; }
        public float getMaxHealth() { return maxHealth; }
        public float getSpeed() { return speed; }
        public Vector3f getSpawnPosition() { return spawnPosition; }
        public boolean isAlive() { return isAlive; }
        public boolean isAttackingBarrier() { return isAttackingBarrier; }
        public Optional<String> getTargetBarrierZone() { return Optional.ofNullable(targetBarrierZone); }

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
     */
    private ZombieInstance createZombie(Vector3f position) {
        float health = roundManager.getScaledZombieHealth();
        float speed = roundManager.getScaledZombieSpeed();
        String zombieId = "zombie_" + tickCounter + "_" + UUID.randomUUID().toString().substring(0, 8);

        return new ZombieInstance(zombieId, health, speed, position);
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
            // The round manager auto-advances via decrementActiveZombies
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
}
