package dev.hytalezombie;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.task.TaskRegistration;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalezombie.commands.HytaleZombieCommand;
import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.entity.ZombieDamageEventSystem;
import dev.hytalezombie.entity.ZombieEntity;
import dev.hytalezombie.manager.*;
import dev.hytalezombie.ui.ZombieHud;
import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Plugin entry point for HytaleZombie.
 * Extends Hytale's JavaPlugin to integrate with the Hytale server SDK.
 */
public class HytaleZombiePlugin extends JavaPlugin {

    private static HytaleZombieConfig config;

    // Core managers
    private RoundManager roundManager;
    private PlayerDataManager playerDataManager;
    private BarrierManager barrierManager;
    private SpawnManager spawnManager;
    private ZoneManager zoneManager;
    private DebugManager debugManager;
    private ScoreboardManager scoreboardManager;

    // Game session orchestrator
    private GameSession gameSession;

    // Player entity refs for position tracking (playerId -> entityRef)
    private final Map<String, Ref<EntityStore>> playerEntityRefs;

    // Custom HUD overlays per player (playerId -> ZombieHud)
    private final Map<String, ZombieHud> playerHuds;

    // Spawn node persistence
    /** Path to the spawn nodes JSON file, relative to the server's run/ directory. */
    static final Path SPAWN_DATA_PATH = Path.of("hytalezombie_data", "spawn_nodes.json");

    // Game loop scheduling
    private ScheduledExecutorService gameLoopExecutor;
    private TaskRegistration gameLoopTask;
    private int positionUpdateCounter;

    /**
     * Constructor called by Hytale's plugin system via JavaPluginInit.
     */
    public HytaleZombiePlugin(JavaPluginInit init) {
        super(init);
        this.config = new HytaleZombieConfig();
        this.playerEntityRefs = new ConcurrentHashMap<>();
        this.playerHuds = new ConcurrentHashMap<>();
    }

    /**
     * Called after the plugin is constructed and registered.
     * Use preLoad for initialization/construction only (plugin is NOT enabled yet).
     */
    @Override
    public CompletableFuture<Void> preLoad() {
        getLogger().at(Level.INFO).log("HytaleZombie is initializing...");

        // Initialize managers
        this.roundManager = new RoundManager(HytaleZombiePlugin::getPluginConfig);
        this.playerDataManager = new PlayerDataManager();
        this.barrierManager = new BarrierManager();
        this.spawnManager = new SpawnManager();
        this.zoneManager = new ZoneManager("spawn_room");
        this.debugManager = new DebugManager();

        // Initialize the game session orchestrator
        this.gameSession = new GameSession(
            HytaleZombiePlugin::getPluginConfig,
            roundManager,
            playerDataManager,
            barrierManager,
            spawnManager
        );

        // Wire ZoneManager into GameSession for door-crossing zone tracking
        this.gameSession.setZoneManager(zoneManager);

        // Initialize the scoreboard manager for HUD updates
        this.scoreboardManager = new ScoreboardManager(gameSession, playerDataManager);

        // Load persisted spawn nodes (survives server restarts)
        spawnManager.loadFromFile(SPAWN_DATA_PATH);

        getLogger().at(Level.INFO).log("HytaleZombie initialization complete.");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Called during plugin setup, before {@link #start()}.
     * Register entity types, components, and systems here.
     * The plugin state is {@code SETUP} at this point ??? commands/events
     * should still be registered in {@link #start()} (state is {@code ENABLED}).
     */
    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("HytaleZombie is setting up...");

        // Register the ZombieEntity with Hytale's EntityModule.
        // This enables:
        //   - ZombieEntity.getComponentType() to return a valid ComponentType
        //   - ECS systems to query and interact with zombie entities
        //   - Entity persistence and networking via the built-in codec
        getEntityRegistry().registerEntity(
            "hz_zombie",
            ZombieEntity.class,
            ZombieEntity::new,
            ZombieEntity.CODEC
        );

        // Register the damage event system so zombie hits/kills are forwarded
        // to GameSession for point tracking and round management.
        getEntityStoreRegistry().registerSystem(new ZombieDamageEventSystem(gameSession));

        getLogger().at(Level.INFO).log("HytaleZombie setup complete (ZombieEntity registered as 'hz_zombie', damage system active).");
    }

    /**
     * Called when the plugin is enabled/started.
     * Register commands, events, and start the game loop here
     * (plugin state is ENABLED at this point).
     */
    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("HytaleZombie is starting...");

        // Register the /hytalezombie command
        CommandRegistry commandRegistry = getCommandRegistry();
        commandRegistry.registerCommand(new HytaleZombieCommand(this));

        // Register PlayerReadyEvent listener to greet players and create their data
        getEventRegistry().registerGlobal(
            PlayerReadyEvent.class,
            event -> {
                // Get the Player entity from the event
                com.hypixel.hytale.server.core.entity.entities.Player player = event.getPlayer();
                // Get the UUID from the Ref<EntityStore> via the event
                String playerId = event.getPlayerRef().getStore()
                    .getComponent(event.getPlayerRef(), com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType())
                    .getUuid()
                    .toString();
                getLogger().at(Level.INFO).log("Player ready: {0}", playerId);

                // Set the World reference on first player join (needed for entity spawning)
                if (gameSession.getWorld() == null) {
                    com.hypixel.hytale.server.core.universe.world.World world =
                        event.getPlayerRef().getStore().getExternalData().getWorld();
                    if (world != null) {
                        gameSession.setWorld(world);
                        getLogger().at(Level.INFO).log("World reference set for entity spawning: {0}", world.getName());
                    }
                }

                playerDataManager.getOrCreatePlayerData(playerId);
                // Store the player's entity ref for position tracking
                playerEntityRefs.put(playerId, event.getPlayerRef());
                // Also forward to GameSession for NPC blackboard target assignment
                gameSession.updatePlayerEntityRef(playerId, event.getPlayerRef());
                // Initialize player zone — assign to starting zone
                gameSession.getPlayerZone(playerId); // triggers lazy init via getPlayerZone()
                // Send welcome message using PlayerRef from the store
                com.hypixel.hytale.server.core.universe.PlayerRef playerRef = 
                    event.getPlayerRef().getStore()
                        .getComponent(event.getPlayerRef(), com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                if (playerRef != null) {
                    // Register the player in the scoreboard manager
                    scoreboardManager.registerPlayer(playerId, playerRef);

                    // Create and register the Custom HUD overlay for this player
                    try {
                        com.hypixel.hytale.server.core.entity.entities.Player playerEntity =
                            event.getPlayerRef().getStore().getComponent(
                                event.getPlayerRef(),
                                com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()
                            );
                        if (playerEntity != null) {
                            com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager hudManager =
                                playerEntity.getHudManager();
                            ZombieHud zombieHud = new ZombieHud(playerRef);
                            hudManager.addCustomHud(playerRef, zombieHud);
                            playerHuds.put(playerId, zombieHud);
                            getLogger().at(Level.FINE).log("Custom HUD registered for player {0}", playerId);
                        }
                    } catch (Exception e) {
                        getLogger().at(Level.WARNING).log(
                            "Failed to register Custom HUD for player {0}: {1}",
                            playerId, e.getMessage()
                        );
                    }
                    playerRef.sendMessage(
                        Message.raw("[HytaleZombie] Welcome! Type /hytalezombie for help.")
                    );
                }

                // Initial position update for zombie AI targeting
                updatePlayerPositionFromECS(playerId, event.getPlayerRef());
            }
        );

        // Register the game loop task (20 ticks per second)
        startGameLoop();

        // Register shutdown hook for cleanup
        registerShutdownHook();

        getLogger().at(Level.INFO).log("HytaleZombie started successfully!");
    }

    /**
     * Starts the 20-tick-per-second game loop using Hytale's task system.
     * Runs the GameSession.tick() method each cycle.
     */
    private void startGameLoop() {
        // Use a ScheduledExecutorService to tick 20 times per second
        this.gameLoopExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HytaleZombie-GameLoop");
            t.setDaemon(true);
            return t;
        });

        java.util.concurrent.ScheduledFuture<Void> future = 
            (java.util.concurrent.ScheduledFuture<Void>) gameLoopExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        if (gameSession != null && gameSession.isSessionActive()) {
                            gameSession.tick();
                        }
                        // Always tick the scoreboard (sends updates even between matches)
                        if (scoreboardManager != null) {
                            scoreboardManager.tick();
                        }
                        // Update Custom HUD overlays for all players
                        tickHudUpdates();
                        // Periodically update player positions for zombie AI targeting
                        tickPlayerPositionUpdates();
                    } catch (Exception e) {
                        getLogger().at(Level.WARNING).log(
                            "Error in game loop tick: {0}", e.getMessage()
                        );
                    }
                },
                0, 50, TimeUnit.MILLISECONDS // 20 ticks per second (1000ms / 20 = 50ms)
            );

        // Register with Hytale's task system for lifecycle management
        this.gameLoopTask = getTaskRegistry().registerTask(future);
    }

    /**
     * How many game ticks between player position updates for zombie AI.
     * Higher = less frequent reads from the ECS (saves world thread time).
     */
    private static final int POSITION_UPDATE_INTERVAL = 10; // 2 updates per second

    /**
     * Periodically reads player positions from the ECS and forwards them
     * to GameSession for zombie AI targeting.
     */
    private void tickPlayerPositionUpdates() {
        positionUpdateCounter++;
        if (positionUpdateCounter < POSITION_UPDATE_INTERVAL) return;
        positionUpdateCounter = 0;

        if (playerEntityRefs.isEmpty() || gameSession == null) return;

        // Read positions on the world thread
        for (Map.Entry<String, Ref<EntityStore>> entry : playerEntityRefs.entrySet()) {
            updatePlayerPositionFromECS(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Reads a single player's position from the ECS and updates GameSession.
     */
    private void updatePlayerPositionFromECS(String playerId, Ref<EntityStore> playerRef) {
        if (playerRef == null || !playerRef.isValid()) return;
        try {
            Store<EntityStore> store = playerRef.getStore();
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform != null) {
                org.joml.Vector3d pos = transform.getPosition();
                gameSession.updatePlayerPosition(playerId,
                    new Vector3f((float) pos.x(), (float) pos.y(), (float) pos.z()));
            }
        } catch (Exception e) {
            getLogger().at(Level.FINE).log("Could not read player position for {0}: {1}",
                playerId, e.getMessage());
        }
    }

    /** How many ticks between HUD updates (20 ticks = 1 second). */
    private static final int HUD_UPDATE_INTERVAL = 20;

    private int hudUpdateCounter;

    /**
     * Periodically updates all player Custom HUD overlays with current
     * game state (round, zombie count, points).
     */
    private void tickHudUpdates() {
        if (playerHuds.isEmpty()) return;

        hudUpdateCounter++;
        if (hudUpdateCounter < HUD_UPDATE_INTERVAL) return;
        hudUpdateCounter = 0;

        if (gameSession == null) return;

        int round = gameSession.getRoundManager().getCurrentRound();
        int activeZombies = gameSession.getActiveZombieCount();
        int totalZombies = gameSession.getTotalZombiesForRound();

        for (Map.Entry<String, ZombieHud> entry : playerHuds.entrySet()) {
            String playerId = entry.getKey();
            ZombieHud hud = entry.getValue();
            if (hud == null) continue;

            try {
                int points = 0;
                dev.hytalezombie.model.PlayerData data = playerDataManager.getPlayerData(playerId);
                if (data != null) {
                    points = data.getPoints();
                }
                String zoneName = gameSession.getPlayerZone(playerId);
                hud.updateDisplay(round, activeZombies, totalZombies, points, zoneName);
            } catch (Exception e) {
                getLogger().at(Level.FINE).log("Failed to update HUD for player {0}: {1}",
                    playerId, e.getMessage());
            }
        }
    }

    /**
     * Sets up a default test map with basic zones, spawn nodes, and barriers.
     * Call this after starting a match to have a playable layout.
     *
     * <p>Map import is handled by the SchematicImporter mod.
     * Place .schematic/.litematic files on the server and use the mod's commands.</p>
     *
     * <p>The spawn_room zone is already registered in ZoneManager from preLoad().
     * Additional zones can be added via /hz addzone and connected via /hz connectzone.
     * Door positions are set via /hz setdoor.</p>
     */
    public void setupDefaultMap() {
        // Only set up defaults on first run (no spawn nodes registered yet).
        // Subsequent calls preserve user-placed spawn nodes loaded from persistence.
        if (spawnManager.getTotalSpawnCount() > 0) {
            getLogger().at(Level.INFO).log(
                "Spawn nodes already registered ({0} total) — skipping default map setup.",
                spawnManager.getTotalSpawnCount()
            );
            return;
        }

        // The spawn_room zone is already registered in ZoneManager from preLoad().
        // Mark it as occupied so zombies can spawn there.
        spawnManager.markZoneOccupied("spawn_room");
        saveSpawnData();

        getLogger().at(Level.INFO).log(
            "Default map zone 'spawn_room' marked. Use /hz setspawn to place spawn points, "
            + "/hz addzone to add zones, /hz connectzone to connect them, and /hz setdoor to place doors."
        );
    }

    /**
     * Registers a shutdown hook to clean up the game loop executor when the plugin stops.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (gameLoopExecutor != null && !gameLoopExecutor.isShutdown()) {
                gameLoopExecutor.shutdown();
                try {
                    if (!gameLoopExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                        gameLoopExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    gameLoopExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }, "HytaleZombie-Shutdown"));
    }

    // --- Accessors ---

    public static HytaleZombieConfig getPluginConfig() {
        return config;
    }

    public RoundManager getRoundManager() {
        return roundManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public BarrierManager getBarrierManager() {
        return barrierManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public DebugManager getDebugManager() {
        return debugManager;
    }

    public GameSession getGameSession() {
        return gameSession;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    /**
     * Saves the current spawn nodes and occupied zones to disk.
     * Called automatically after every spawn mutation command.
     */
    public void saveSpawnData() {
        if (spawnManager != null) {
            spawnManager.saveToFile(SPAWN_DATA_PATH);
        }
    }

    public void setSpawnManager(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    public void setDebugManager(DebugManager debugManager) {
        this.debugManager = debugManager;
    }

    public void setRoundManager(RoundManager roundManager) {
        this.roundManager = roundManager;
    }

    public void setPlayerDataManager(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    public void setBarrierManager(BarrierManager barrierManager) {
        this.barrierManager = barrierManager;
    }

    public void setGameSession(GameSession gameSession) {
        this.gameSession = gameSession;
    }
}
