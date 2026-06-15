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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalezombie.commands.HytaleZombieCommand;
import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.entity.ZombieDamageEventSystem;
import dev.hytalezombie.entity.ZombieEntity;
import dev.hytalezombie.manager.*;
import dev.hytalezombie.map.MapLoader;
import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.nio.file.Files;
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
    private DebugManager debugManager;
    private ScoreboardManager scoreboardManager;

    // Game session orchestrator
    private GameSession gameSession;

    // Player entity refs for position tracking (playerId -> entityRef)
    private final Map<String, Ref<EntityStore>> playerEntityRefs;

    // Map loading
    private MapLoader.PrefabData loadedMapPrefab;
    private MapLoader.PrefabBounds loadedMapBounds;
    private boolean mapLoaded;

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
        this.debugManager = new DebugManager();

        // Initialize the game session orchestrator
        this.gameSession = new GameSession(
            HytaleZombiePlugin::getPluginConfig,
            roundManager,
            playerDataManager,
            barrierManager,
            spawnManager
        );

        // Initialize the scoreboard manager for HUD updates
        this.scoreboardManager = new ScoreboardManager(gameSession, playerDataManager);

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
                // Send welcome message using PlayerRef from the store
                com.hypixel.hytale.server.core.universe.PlayerRef playerRef = 
                    event.getPlayerRef().getStore()
                        .getComponent(event.getPlayerRef(), com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                if (playerRef != null) {
                    // Register the player in the scoreboard manager
                    scoreboardManager.registerPlayer(playerId, playerRef);
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

    /**
     * Sets up a default test map with basic zones, spawn nodes, and barriers.
     * Call this after starting a match to have a playable layout.
     *
     * <p>If a prefab map has been loaded via {@link #loadMap(Path, int, int, int)},
     * spawn nodes are automatically placed around the structure perimeter.</p>
     */
    public void setupDefaultMap() {
        spawnManager.clearAllNodes();
        spawnManager.markZoneOccupied("spawn_room");

        if (mapLoaded && loadedMapBounds != null) {
            // Auto-configure spawn nodes around the loaded map structure
            MapLoader.PrefabBounds b = loadedMapBounds;
            float midX = (b.minX + b.maxX) / 2.0f;
            float midZ = (b.minZ + b.maxZ) / 2.0f;
            float y = b.minY + 1; // Spawn zombies on the floor level + 1

            // Place spawn nodes at the four outer corners of the map
            float pad = 8.0f; // Distance outside the structure
            float outerMinX = b.minX - pad;
            float outerMaxX = b.maxX + pad;
            float outerMinZ = b.minZ - pad;
            float outerMaxZ = b.maxZ + pad;

            spawnManager.registerSpawnNode(new SpawnNode(
                "spawn_room",
                new Vector3f(outerMinX, y, outerMinZ),
                5.0f
            ));
            spawnManager.registerSpawnNode(new SpawnNode(
                "spawn_room",
                new Vector3f(outerMaxX, y, outerMinZ),
                5.0f
            ));
            spawnManager.registerSpawnNode(new SpawnNode(
                "spawn_room",
                new Vector3f(outerMinX, y, outerMaxZ),
                5.0f
            ));
            spawnManager.registerSpawnNode(new SpawnNode(
                "spawn_room",
                new Vector3f(outerMaxX, y, outerMaxZ),
                5.0f
            ));
            // Also add a node at the midpoint on each side
            spawnManager.registerSpawnNode(new SpawnNode(
                "spawn_room",
                new Vector3f(midX, y, outerMinZ),
                5.0f
            ));
            spawnManager.registerSpawnNode(new SpawnNode(
                "spawn_room",
                new Vector3f(midX, y, outerMaxZ),
                5.0f
            ));

            getLogger().at(Level.INFO).log(
                "Default map: loaded prefab structure ({0}x{1}x{2}) with 6 auto-placed spawn nodes around perimeter.",
                new Object[]{b.maxX - b.minX + 1, b.maxY - b.minY + 1, b.maxZ - b.minZ + 1}
            );
        } else {
            getLogger().at(Level.INFO).log(
                "Default map zone marked. No spawn nodes registered - use /hz setspawn to place your own, or /hz loadmap to import a structure."
            );
        }
    }

    /**
     * Loads a Hytale prefab JSON map from disk and places it into the world.
     *
     * <p>The prefab must be in the format produced by the schematic_converter.py tool.
     * Call this before {@link #setupDefaultMap()} to have spawn nodes auto-configured.</p>
     *
     * @param prefabPath path to the prefab JSON file
     * @param originX    world X coordinate for the prefab origin
     * @param originY    world Y coordinate for the prefab origin (floor level)
     * @param originZ    world Z coordinate for the prefab origin
     */
    public void loadMap(@Nonnull Path prefabPath, int originX, int originY, int originZ) {
        // Resolve relative paths against the plugin's data directory.
        // Falls back to current working directory if the resolved path doesn't exist.
        Path resolvedPath = prefabPath;
        if (!prefabPath.isAbsolute()) {
            Path dataDirPath = getDataDirectory().resolve(prefabPath);
            if (Files.exists(dataDirPath)) {
                resolvedPath = dataDirPath;
            } else {
                // Fallback: try relative to CWD (where the server was launched)
                if (Files.exists(prefabPath)) {
                    resolvedPath = prefabPath;
                } else {
                    // Neither exists — use data dir path so error message shows full path
                    resolvedPath = dataDirPath;
                }
            }
        }

        try {
            getLogger().at(Level.INFO).log("Loading map prefab: " + resolvedPath.toAbsolutePath());
            loadedMapPrefab = MapLoader.loadPrefab(resolvedPath);

            getLogger().at(Level.INFO).log("Prefab loaded: " + loadedMapPrefab.blocks.size()
                + " blocks, bounds=" + loadedMapPrefab.width + "x"
                + loadedMapPrefab.height + "x" + loadedMapPrefab.length
                + ", unresolved=" + loadedMapPrefab.unresolvedCount);

            if (gameSession.getWorld() == null) {
                getLogger().at(Level.WARNING).log("World reference not available yet. Map prefab parsed but "
                    + "not placed. It will be placed automatically when a player joins.");
                mapLoaded = true;
                loadedMapBounds = new MapLoader.PrefabBounds(
                    originX, originY, originZ,
                    originX + loadedMapPrefab.width - 1,
                    originY + loadedMapPrefab.height - 1,
                    originZ + loadedMapPrefab.length - 1
                );
                return;
            }

            World world = gameSession.getWorld();
            MapLoader.PrefabData prefab = loadedMapPrefab;
            int clearPad = 20;

            getLogger().at(Level.INFO).log("Clearing terrain around structure...");
            MapLoader.clearAreaAsync(world,
                originX - clearPad, 0, originZ - clearPad,
                originX + prefab.width + clearPad,
                originY + prefab.height + clearPad,
                originZ + prefab.length + clearPad
            ).thenCompose(v -> {
                getLogger().at(Level.INFO).log("Terrain cleared. Placing " + prefab.blocks.size() + " blocks...");
                return MapLoader.placePrefabAsync(world, prefab, originX, originY, originZ);
            }).thenAccept(bounds -> {
                loadedMapBounds = bounds;
                mapLoaded = true;
                getLogger().at(Level.INFO).log("Map placed at (" + originX + "," + originY + "," + originZ
                    + ") — bounds: (" + bounds.minX + "," + bounds.minY + "," + bounds.minZ
                    + ") to (" + bounds.maxX + "," + bounds.maxY + "," + bounds.maxZ + ")");
            }).exceptionally(ex -> {
                getLogger().at(Level.SEVERE).log("Failed to place map: " + ex.getMessage());
                ex.printStackTrace();
                return null;
            });

        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to load map: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Returns the center floor position of the loaded map, or null if no map is loaded.
     * This is a good position to teleport players to on join.
     */
    @Nullable
    public Vector3f getMapSpawnPoint() {
        if (loadedMapBounds != null) {
            return loadedMapBounds.getCenterFloor().add(new Vector3f(0.5f, 1.0f, 0.5f));
        }
        return null;
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

    public DebugManager getDebugManager() {
        return debugManager;
    }

    public GameSession getGameSession() {
        return gameSession;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
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
