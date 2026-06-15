package dev.hytalezombie;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.task.TaskRegistration;
import dev.hytalezombie.commands.HytaleZombieCommand;
import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.entity.ZombieDamageEventSystem;
import dev.hytalezombie.entity.ZombieEntity;
import dev.hytalezombie.manager.*;
import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;

import javax.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;
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

    // Game session orchestrator
    private GameSession gameSession;

    // Game loop scheduling
    private ScheduledExecutorService gameLoopExecutor;
    private TaskRegistration gameLoopTask;

    /**
     * Constructor called by Hytale's plugin system via JavaPluginInit.
     */
    public HytaleZombiePlugin(JavaPluginInit init) {
        super(init);
        this.config = new HytaleZombieConfig();
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

        getLogger().at(Level.INFO).log("HytaleZombie initialization complete.");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Called during plugin setup, before {@link #start()}.
     * Register entity types, components, and systems here.
     * The plugin state is {@code SETUP} at this point — commands/events
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
                // Send welcome message using PlayerRef from the store
                com.hypixel.hytale.server.core.universe.PlayerRef playerRef = 
                    event.getPlayerRef().getStore()
                        .getComponent(event.getPlayerRef(), com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                if (playerRef != null) {
                    playerRef.sendMessage(
                        Message.raw("[HytaleZombie] Welcome! Type /hytalezombie for help.")
                    );
                }
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
     * Sets up a default test map with basic zones, spawn nodes, and barriers.
     * Call this after starting a match to have a playable layout.
     */
    public void setupDefaultMap() {
        // This would typically load from config, but for testing we hardcode a layout
        
        // Mark the starting zone as occupied so zombies spawn there
        spawnManager.markZoneOccupied("spawn_room");

        // Register some spawn nodes in the starting zone
        spawnManager.registerSpawnNode(new SpawnNode(
            "spawn_room", new Vector3f(0, 0, 0), 5.0f
        ));
        spawnManager.registerSpawnNode(new SpawnNode(
            "spawn_room", new Vector3f(10, 0, 10), 5.0f
        ));
        spawnManager.registerSpawnNode(new SpawnNode(
            "spawn_room", new Vector3f(-10, 0, -10), 5.0f
        ));
        spawnManager.registerSpawnNode(new SpawnNode(
            "spawn_room", new Vector3f(5, 0, -15), 4.0f
        ));
        spawnManager.registerSpawnNode(new SpawnNode(
            "spawn_room", new Vector3f(-15, 0, 5), 4.0f
        ));

        // Additional zone: room_2 (further out, more spawns)
        spawnManager.registerSpawnNode(new SpawnNode(
            "room_2", new Vector3f(30, 0, 30), 6.0f
        ));
        spawnManager.registerSpawnNode(new SpawnNode(
            "room_2", new Vector3f(40, 0, 20), 6.0f
        ));
        spawnManager.registerSpawnNode(new SpawnNode(
            "room_2", new Vector3f(25, 0, 40), 6.0f
        ));

        // Additional zone: open_area (wider spread)
        spawnManager.registerSpawnNode(new SpawnNode(
            "open_area", new Vector3f(-30, 0, -30), 8.0f
        ));
        spawnManager.registerSpawnNode(new SpawnNode(
            "open_area", new Vector3f(-50, 0, -50), 8.0f
        ));

        getLogger().at(Level.INFO).log("Default test map with spawn nodes has been set up.");
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
