package dev.hytalezombie.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalezombie.HytaleZombiePlugin;
import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.entity.EntitySpawnHelper;
import dev.hytalezombie.manager.GameSession;
import dev.hytalezombie.model.Perk;
import dev.hytalezombie.model.PowerUp;
import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.model.Weapon;
import dev.hytalezombie.spawn.SpawnNode;

import org.joml.Vector3d;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Admin command for the HytaleZombie mod.
 * Usage: /hytalezombie <subcommand>
 */
public class HytaleZombieCommand extends AbstractCommand {

    private final HytaleZombiePlugin plugin;

    public HytaleZombieCommand(HytaleZombiePlugin plugin) {
        super("hytalezombie", "Admin command for HytaleZombie");

        this.plugin = plugin;

        // Add aliases
        addAliases("hz", "zombie");

        // Require admin permission
        // requirePermission("hytalezombie.admin"); // Commented out for testing

        // Allow extra arguments since we parse subcommands manually in execute()
        setAllowsExtraArguments(true);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        // CommandContext provides getInputString() for the full input
        String input = ctx.getInputString();
        String[] args = (input == null || input.isEmpty()) ? new String[0] : input.split(" ");

        // Log the raw input for debugging
        plugin.getLogger().at(java.util.logging.Level.INFO).log("HytaleZombieCommand received input: '{0}'", input);

        if (args.length == 0) {
            sendUsage(ctx);
            return CompletableFuture.completedFuture(null);
        }

        String subCommand = args[0].toLowerCase();

        // Handle the case where getInputString() includes the command name itself
        // e.g., "hytalezombie start" instead of just "start"
        if (subCommand.equals("hytalezombie") || subCommand.equals("hz") || subCommand.equals("zombie")) {
            if (args.length > 1) {
                subCommand = args[1].toLowerCase();
                // Rebuild args without the command name
                String[] newArgs = new String[args.length - 1];
                System.arraycopy(args, 1, newArgs, 0, args.length - 1);
                args = newArgs;
            } else {
                sendUsage(ctx);
                return CompletableFuture.completedFuture(null);
            }
        }

        switch (subCommand) {
            // ===== Match Controls =====
            case "start":
                handleStart(ctx);
                break;

            case "stop":
                handleStop(ctx);
                break;

            case "round":
                handleRound(ctx, args);
                break;

            case "info":
                handleInfo(ctx);
                break;

            // ===== Map Setup =====
            case "map":
                plugin.setupDefaultMap();
                ctx.sendMessage(Message.raw("[HytaleZombie] Default test map set up with spawn nodes."));
                break;

            // Map prefabs are now handled by Hytale's built-in prefab system.
            // Place .prefab files in run/.cache/prefabs/Hytale_Hytale/Server/Prefabs/

            case "setspawn":
                handleSetSpawn(ctx, args);
                break;

            case "delspawn":
                handleDelSpawn(ctx, args);
                break;

            case "listspawns":
            case "listspawn":
                handleListSpawns(ctx, args);
                break;

            case "clearspawns":
                handleClearSpawns(ctx);
                break;

            // ===== Zombie Testing =====
            case "spawnzombie":
            case "spawn":
                handleSpawnZombie(ctx, args);
                break;

            case "spawnhere":
            case "summon":
                handleSpawnHere(ctx);
                break;

            case "killall":
                handleKillAll(ctx);
                break;

            case "zombieinfo":
                handleZombieInfo(ctx);
                break;

            case "spawninfo":
                handleSpawnInfo(ctx);
                break;

            // ===== Economy & Items =====
            case "points":
                handlePoints(ctx, args);
                break;

            case "powerup":
                handleGivePowerUp(ctx, args);
                break;

            case "giveweapon":
                handleGiveWeapon(ctx, args);
                break;

            case "giveperk":
                handleGivePerk(ctx, args);
                break;

            // ===== Round Testing =====
            case "nextround":
                handleNextRound(ctx);
                break;

            // ===== Debug =====
            case "debug":
                handleDebug(ctx);
                break;

            case "state":
                handleState(ctx);
                break;

            case "config":
                handleConfig(ctx, args);
                break;

            // ===== Zone Management =====
            case "markzone":
                handleMarkZone(ctx, args);
                break;

            case "unmarkzone":
                handleUnmarkZone(ctx, args);
                break;

            case "listzones":
                handleListZones(ctx);
                break;

            default:
                ctx.sendMessage(Message.raw("[HytaleZombie] Unknown subcommand: " + subCommand));
                sendUsage(ctx);
                break;
        }

        return CompletableFuture.completedFuture(null);
    }

    // ========================================================================
    //  USAGE
    // ========================================================================

    private void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== HytaleZombie Commands ==="));
        ctx.sendMessage(Message.raw("Match: /hz start | stop | round [n] | nextround | info | state"));
        ctx.sendMessage(Message.raw("Map Setup:"));
        ctx.sendMessage(Message.raw("  Prefabs: Place .prefab files in run/.cache/prefabs/Hytale_Hytale/Server/Prefabs/"));
        ctx.sendMessage(Message.raw("Spawn Points:"));
        ctx.sendMessage(Message.raw("  /hz setspawn <zone> [radius]          - Add spawn at 0,0,0"));
        ctx.sendMessage(Message.raw("  /hz setspawn <zone> <x> <y> <z> [r]  - Add spawn at coords"));
        ctx.sendMessage(Message.raw("  /hz delspawn <zone> [index]           - Remove zone's spawns"));
        ctx.sendMessage(Message.raw("  /hz listspawns [zone]                 - List all spawns"));
        ctx.sendMessage(Message.raw("  /hz clearspawns                       - Remove all spawns"));
        ctx.sendMessage(Message.raw("Zombie Testing:"));
        ctx.sendMessage(Message.raw("  /hz spawnzombie [zone] [count]        - Spawn zombie(s) at spawn nodes"));
        ctx.sendMessage(Message.raw("  /hz spawnhere                         - Spawn zombie right at your position"));
        ctx.sendMessage(Message.raw("  /hz summon                            - (alias for spawnhere)"));
        ctx.sendMessage(Message.raw("  /hz killall                           - Kill all active zombies"));
        ctx.sendMessage(Message.raw("  /hz zombieinfo                        - List all zombie stats"));
        ctx.sendMessage(Message.raw("  /hz spawninfo                         - Spawn progress this round"));
        ctx.sendMessage(Message.raw("Zones:"));
        ctx.sendMessage(Message.raw("  /hz markzone <zone>                   - Mark zone as occupied"));
        ctx.sendMessage(Message.raw("  /hz unmarkzone <zone>                 - Unmark zone"));
        ctx.sendMessage(Message.raw("  /hz listzones                         - List all zones with spawns"));
        ctx.sendMessage(Message.raw("Economy & Items:"));
        ctx.sendMessage(Message.raw("  /hz points [player] [amount]          - Get/set points"));
        ctx.sendMessage(Message.raw("  /hz powerup <type>                    - Activate a power-up"));
        ctx.sendMessage(Message.raw("  /hz giveweapon <player> <weapon_id>   - Give player a weapon"));
        ctx.sendMessage(Message.raw("  /hz giveperk <player> <perk_type>     - Give player a perk"));
        ctx.sendMessage(Message.raw("Debug:"));
        ctx.sendMessage(Message.raw("  /hz debug                             - Toggle debug mode"));
        ctx.sendMessage(Message.raw("  /hz config [key] [value]              - View/set config"));
        ctx.sendMessage(Message.raw("  /hz state                             - Full game state dump"));
        ctx.sendMessage(Message.raw("Tip: /hz setspawn spawn_room 10.5 0 -20.3 4.0"));
    }

    // ========================================================================
    //  MATCH COMMANDS
    // ========================================================================

    private void handleStart(CommandContext ctx) {
        GameSession session = plugin.getGameSession();
        if (session.isSessionActive()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] A match is already in progress!"));
            return;
        }
        plugin.setupDefaultMap();
        session.startMatch();
        session.prepareRoundSpawns();
        ctx.sendMessage(Message.raw("[HytaleZombie] Match started! Round 1 - Zombies incoming!"));
        ctx.sendMessage(Message.raw("[HytaleZombie] " + session.getTotalZombiesForRound() + " zombies this round."));
    }

    private void handleStop(CommandContext ctx) {
        GameSession session = plugin.getGameSession();
        if (!session.isSessionActive()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No match is currently running."));
            return;
        }
        session.endMatch();
        ctx.sendMessage(Message.raw("[HytaleZombie] Match stopped."));
    }

    private void handleRound(CommandContext ctx, String[] args) {
        int round = args.length > 1 ? parseIntSafe(args[1]) : -1;
        if (round > 0) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Set round to: " + round));
        } else {
            ctx.sendMessage(Message.raw(
                "[HytaleZombie] Current round: " + plugin.getRoundManager().getCurrentRound()
            ));
        }
    }

    private void handleInfo(CommandContext ctx) {
        GameSession session = plugin.getGameSession();
        ctx.sendMessage(Message.raw("[HytaleZombie] HytaleZombie v0.0.1 - Round-based survival"));
        ctx.sendMessage(Message.raw("  Match active: " + session.isSessionActive()));
        ctx.sendMessage(Message.raw("  Round: " + plugin.getRoundManager().getCurrentRound()));
        ctx.sendMessage(Message.raw("  Active zombies: " + session.getActiveZombieCount()));
        ctx.sendMessage(Message.raw("  Players: " + session.getPlayerCount()));
    }

    // ========================================================================
    //  SPAWN POINT COMMANDS
    // ========================================================================

    /**
     * /hz setspawn <zone> [radius]
     *    or
     * /hz setspawn <zone> <x> <y> <z> [radius]
     *
     * Adds a spawn point for the given zone.
     * With just <zone> and [radius], uses coordinates (0, 0, 0).
     * With <zone> <x> <y> <z>, uses the specified coordinates.
     * Example: /hz setspawn room_1
     *          /hz setspawn room_1 10.5 0 -20.3 4.0
     *          /hz setspawn room_2 3.5
     */
    private void handleSetSpawn(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz setspawn <zone> [radius]"));
            ctx.sendMessage(Message.raw("  Set spawn by coordinates: /hz setspawn <zone> <x> <y> <z> [radius]"));
            ctx.sendMessage(Message.raw("  Example: /hz setspawn spawn_room"));
            ctx.sendMessage(Message.raw("  Example: /hz setspawn room_2 10.5 0 -20.3 4.0"));
            ctx.sendMessage(Message.raw("  Example: /hz setspawn room_2 3.5"));
            return;
        }

        String zoneId = args[1];
        float x = 0, y = 0, z = 0;
        float radius = 5.0f; // default radius

        if (args.length >= 5) {
            // Format: setspawn <zone> <x> <y> <z> [radius]
            try {
                x = Float.parseFloat(args[2]);
                y = Float.parseFloat(args[3]);
                z = Float.parseFloat(args[4]);
            } catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid coordinates. Use numbers for x y z."));
                ctx.sendMessage(Message.raw("  Example: /hz setspawn spawn_room 10.5 0 -20.3"));
                return;
            }
            if (args.length >= 6) {
                try {
                    radius = Float.parseFloat(args[5]);
                    if (radius < 0) {
                        ctx.sendMessage(Message.raw("[HytaleZombie] Radius must be a positive number!"));
                        return;
                    }
                } catch (NumberFormatException e) {
                    ctx.sendMessage(Message.raw("[HytaleZombie] Invalid radius: " + args[5]));
                    return;
                }
            }
        } else if (args.length >= 3) {
            // Could be: setspawn <zone> <radius>
            try {
                radius = Float.parseFloat(args[2]);
                if (radius < 0) {
                    ctx.sendMessage(Message.raw("[HytaleZombie] Radius must be a positive number!"));
                    return;
                }
            } catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid radius: " + args[2]));
                ctx.sendMessage(Message.raw("  If providing coordinates, use all 3: /hz setspawn " + zoneId + " <x> <y> <z> [radius]"));
                return;
            }
        }

        Vector3f position = new Vector3f(x, y, z);
        SpawnNode node = new SpawnNode(zoneId, position, radius);

        plugin.getSpawnManager().registerSpawnNode(node);
        plugin.getSpawnManager().markZoneOccupied(zoneId);

        ctx.sendMessage(Message.raw("[HytaleZombie] Spawn point added!"));
        ctx.sendMessage(Message.raw("  Zone: " + zoneId));
        ctx.sendMessage(Message.raw("  Position: (" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ", " + String.format("%.1f", z) + ")"));
        ctx.sendMessage(Message.raw("  Radius: " + String.format("%.1f", radius) + " blocks"));
    }

    /**
     * /hz delspawn <zone> [index]
     *
     * Removes a spawn point from a zone.
     * Use /hz listspawns <zone> to see indices.
     * If no index is given, removes ALL spawns in that zone.
     */
    private void handleDelSpawn(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz delspawn <zone> [index]"));
            ctx.sendMessage(Message.raw("  Use /hz listspawns <zone> to find the index to remove."));
            ctx.sendMessage(Message.raw("  To remove all spawns in a zone: /hz delspawn <zone>"));
            ctx.sendMessage(Message.raw("  To remove a specific one:  /hz delspawn <zone> 2"));
            return;
        }

        String zoneId = args[1];

        // Check if the zone exists
        if (!plugin.getSpawnManager().hasNodesInZone(zoneId)) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Zone '" + zoneId + "' has no spawn nodes."));
            ctx.sendMessage(Message.raw("  Use /hz listspawns to see all zones."));
            return;
        }

        if (args.length > 2) {
            // Remove a specific index
            int index;
            try {
                index = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid index: " + args[2]));
                return;
            }

            java.util.List<SpawnNode> nodes = plugin.getSpawnManager().getNodesInZone(zoneId);
            if (index < 0 || index >= nodes.size()) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Index " + index + " out of range (0-" + (nodes.size() - 1) + ")"));
                ctx.sendMessage(Message.raw("  Use /hz listspawns " + zoneId + " to see valid indices."));
                return;
            }

            // Remove a specific node by index
            if (plugin.getSpawnManager().removeSpawnNode(zoneId, index)) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Removed spawn node [" + index + "] from zone '" + zoneId + "'"));
            } else {
                ctx.sendMessage(Message.raw("[HytaleZombie] Failed to remove spawn node [" + index + "]"));
            }
        } else {
            // Remove all nodes in this zone
            plugin.getSpawnManager().removeNodesInZone(zoneId);
            ctx.sendMessage(Message.raw("[HytaleZombie] All spawn nodes in zone '" + zoneId + "' removed!"));
        }
    }

    /**
     * /hz listspawns [zone]
     *
     * Lists all registered spawn points.
     * If a zone is specified, only shows spawns in that zone.
     */
    private void handleListSpawns(CommandContext ctx, String[] args) {
        String filterZone = args.length > 1 ? args[1] : null;

        ctx.sendMessage(Message.raw("=== Spawn Nodes ==="));

        if (filterZone != null && !filterZone.isEmpty()) {
            // List spawns for a specific zone
            if (!plugin.getSpawnManager().hasNodesInZone(filterZone)) {
                ctx.sendMessage(Message.raw("  No spawn nodes in zone '" + filterZone + "'"));
                return;
            }

            java.util.List<SpawnNode> nodes = plugin.getSpawnManager().getNodesInZone(filterZone);
            ctx.sendMessage(Message.raw("  Zone: " + filterZone + " (" + nodes.size() + " spawns)"));
            for (int i = 0; i < nodes.size(); i++) {
                SpawnNode n = nodes.get(i);
                ctx.sendMessage(Message.raw("    [" + i + "] " + n.getPosition() + "  radius: " + n.getSpawnRadius()));
            }
        } else {
            // List all zones
            java.util.Set<String> zoneIds = plugin.getSpawnManager().getZoneIds();
            if (zoneIds.isEmpty()) {
                ctx.sendMessage(Message.raw("  No spawn nodes registered."));
                ctx.sendMessage(Message.raw("  Use /hz setspawn <zone> [radius] to add one."));
                return;
            }

            int totalSpawns = 0;
            for (String zoneId : zoneIds) {
                java.util.List<SpawnNode> nodes = plugin.getSpawnManager().getNodesInZone(zoneId);
                boolean occupied = plugin.getSpawnManager().getOccupiedZones().contains(zoneId);
                String status = occupied ? "(active)" : "(inactive)";
                ctx.sendMessage(Message.raw("  * " + zoneId + " - " + nodes.size() + " nodes " + status));
                totalSpawns += nodes.size();
            }
            ctx.sendMessage(Message.raw("  Total: " + totalSpawns + " spawn points across " + zoneIds.size() + " zones"));
            ctx.sendMessage(Message.raw("  Use /hz listspawns <zone> to see individual positions."));
        }
    }

    /**
     * /hz clearspawns
     *
     * Removes ALL spawn nodes and zone markers.
     */
    private void handleClearSpawns(CommandContext ctx) {
        plugin.getSpawnManager().clearAllNodes();
        ctx.sendMessage(Message.raw("[HytaleZombie] All spawn points cleared!"));
        ctx.sendMessage(Message.raw("  Use /hz setspawn <zone> to add new ones."));
    }

    // ========================================================================
    //  ZONE COMMANDS
    // ========================================================================

    /**
     * /hz markzone <zone>
     *
     * Manually marks a zone as occupied (zombies will spawn there).
     */
    private void handleMarkZone(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz markzone <zone>"));
            ctx.sendMessage(Message.raw("  Example: /hz markzone room_2"));
            return;
        }

        String zoneId = args[1];
        plugin.getSpawnManager().markZoneOccupied(zoneId);
        ctx.sendMessage(Message.raw("[HytaleZombie] Zone '" + zoneId + "' marked as occupied! Zombies will now spawn here."));
    }

    /**
     * /hz unmarkzone <zone>
     *
     * Manually marks a zone as unoccupied (zombies stop spawning there).
     */
    private void handleUnmarkZone(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz unmarkzone <zone>"));
            ctx.sendMessage(Message.raw("  Example: /hz unmarkzone room_2"));
            return;
        }

        String zoneId = args[1];
        plugin.getSpawnManager().markZoneUnoccupied(zoneId);
        ctx.sendMessage(Message.raw("[HytaleZombie] Zone '" + zoneId + "' marked as unoccupied."));
    }

    /**
     * /hz listzones
     *
     * Lists all zones that have spawn nodes registered.
     */
    private void handleListZones(CommandContext ctx) {
        java.util.Set<String> zoneIds = plugin.getSpawnManager().getZoneIds();
        java.util.Set<String> occupied = plugin.getSpawnManager().getOccupiedZones();

        ctx.sendMessage(Message.raw("=== Registered Zones (" + zoneIds.size() + ") ==="));

        if (zoneIds.isEmpty()) {
            ctx.sendMessage(Message.raw("  No zones registered."));
            ctx.sendMessage(Message.raw("  Use /hz setspawn <zone> [radius] to add one."));
            return;
        }

        for (String zoneId : zoneIds) {
            java.util.List<SpawnNode> nodes = plugin.getSpawnManager().getNodesInZone(zoneId);
            boolean isOccupied = occupied.contains(zoneId);
            String status = isOccupied ? "ACTIVE" : "inactive";
            String markHint = isOccupied ? "" : "  (/hz markzone " + zoneId + " to enable)";
            ctx.sendMessage(Message.raw("  * " + zoneId + " - " + nodes.size() + " spawns - " + status + markHint));
        }
    }

    // ========================================================================
    //  ZOMBIE TESTING COMMANDS
    // ========================================================================

    /**
     * /hz spawnzombie [zone] [count]
     *    or
     * /hz spawnzombie [count]
     *
     * Manually spawns zombies, bypassing the spawn delay timer.
     * If a zone is specified, spawns from nodes in that zone.
     * If count is specified, spawns that many at once.
     */
    private void handleSpawnZombie(CommandContext ctx, String[] args) {
        GameSession session = plugin.getGameSession();
        boolean matchActive = session.isSessionActive();

        if (!matchActive) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No active match - spawning test zombie(s) without round tracking."));
        }

        String zoneId = null;
        int count = 1;

        // Parse arguments: spawnzombie [zone|count] [count]
        if (args.length > 1) {
            // Try to parse as count first
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                // Not a number, treat as zone name
                zoneId = args[1];
            }
        }
        if (args.length > 2) {
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid count: " + args[2]));
                return;
            }
        }

        // Validate zone if specified
        if (zoneId != null && !plugin.getSpawnManager().hasNodesInZone(zoneId)) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Zone '" + zoneId + "' has no spawn nodes."));
            ctx.sendMessage(Message.raw("  Use /hz listspawns to see available zones."));
            return;
        }

        // Clamp count to prevent lag
        if (count > 50) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Capping at 50 zombies to prevent lag."));
            count = 50;
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Optional<GameSession.ZombieInstance> result;
            if (matchActive) {
                result = session.spawnZombieInstance(zoneId);
            } else {
                result = session.spawnTestZombie(zoneId);
            }
            if (result.isPresent()) {
                spawned++;
            } else {
                break; // No more spawn nodes available
            }
        }

        String zoneInfo = (zoneId != null) ? " from zone '" + zoneId + "'" : "";
        ctx.sendMessage(Message.raw("[HytaleZombie] Spawned " + spawned + " zombie(s)" + zoneInfo + "."));
    }

    /**
     * /hz spawnhere
     *
     * Spawns a zombie entity directly at the player's position, bypassing
     * the spawn-node system entirely. This is useful for testing whether
     * entity spawning itself works (model loading, world.execute(), etc.).
     * Reports the NetworkId on success, or a clear error on failure.
     */
    private void handleSpawnHere(CommandContext ctx) {
        // 1. Must be a player
        if (!ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Only players can use /hz spawnhere."));
            return;
        }

        // 2. Get the player's entity reference (safe on ForkJoinPool)
        Ref<EntityStore> playerRef = ctx.senderAsPlayerRef();
        if (playerRef == null) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Could not get player entity reference."));
            return;
        }

        // 3. Pick a random zombie model (no store access needed)
        String modelName = EntitySpawnHelper.getRandomZombieModel();

        // 4. Get the cached World reference from the GameSession
        //    (set during PlayerReadyEvent, no store access needed)
        World world = plugin.getGameSession().getWorld();
        if (world == null) {
            ctx.sendMessage(Message.raw("[HytaleZombie] World reference is NULL. Has a player fully joined?"));
            ctx.sendMessage(Message.raw("  The world is set in PlayerReadyEvent on server join."));
            ctx.sendMessage(Message.raw("  Try rejoining the server and running /hz spawnhere again."));
            return;
        }

        // 5. Spawn using the player-ref method which runs ALL store operations
        //    (reading TransformComponent, building holder, addEntity) inside a
        //    single world.execute() block on the WorldThread.
        ctx.sendMessage(Message.raw("[HytaleZombie] Spawning " + modelName + " at your position..."));

        EntitySpawnHelper.spawnZombieAtPlayer(world, playerRef, modelName)
            .thenAccept(result -> {
                if (result != EntitySpawnHelper.SpawnResult.FAILED && result.entityRef() != null) {
                    ctx.sendMessage(Message.raw("[HytaleZombie] SPAWN SUCCESS: entity spawned with NetworkId=" + result.networkId()));
                    plugin.getLogger().at(Level.INFO).log(
                        "spawnhere SUCCESS: networkId={0} for {1}",
                        result.networkId(), modelName
                    );
                } else {
                    ctx.sendMessage(Message.raw("[HytaleZombie] SPAWN FAILED: store.addEntity returned null or invalid ref."));
                    ctx.sendMessage(Message.raw("  Possible causes:"));
                    ctx.sendMessage(Message.raw("  - Model asset '" + modelName + "' not found in runtime"));
                    ctx.sendMessage(Message.raw("  - EntityStore rejected the holder (missing a required component)"));
                    ctx.sendMessage(Message.raw("  - World tick is not processing the execute() queue"));
                    plugin.getLogger().at(Level.WARNING).log(
                        "spawnhere FAILED: store.addEntity returned null/invalid ref for {0}",
                        modelName
                    );
                }
            })
            .exceptionally(ex -> {
                ctx.sendMessage(Message.raw("[HytaleZombie] SPAWN ERROR: " + ex.getMessage()));
                plugin.getLogger().at(Level.SEVERE).log(
                    "spawnhere EXCEPTION: {0}", ex.getMessage()
                );
                return null;
            });
    }

    /**
     * /hz killall
     *
     * Kills all active zombies instantly (nuke effect without power-up).
     */
    private void handleKillAll(CommandContext ctx) {
        GameSession session = plugin.getGameSession();
        if (!session.isSessionActive()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No active match."));
            return;
        }
        int count = session.getActiveZombieCount();
        if (count == 0) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No zombies to kill."));
            return;
        }
        session.nukeAllZombies();
        ctx.sendMessage(Message.raw("[HytaleZombie] Killed " + count + " zombie(s)."));
    }

    /**
     * /hz zombieinfo
     *
     * Lists all active zombies with their stats.
     */
    private void handleZombieInfo(CommandContext ctx) {
        GameSession session = plugin.getGameSession();
        if (!session.isSessionActive()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No active match."));
            return;
        }

        java.util.Map<String, GameSession.ZombieInstance> zombies = session.getActiveZombies();
        if (zombies.isEmpty()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No active zombies."));
            return;
        }

        DecimalFormat df = new DecimalFormat("#.#");
        ctx.sendMessage(Message.raw("=== Active Zombies (" + zombies.size() + ") ==="));
        int i = 0;
        for (GameSession.ZombieInstance zombie : zombies.values()) {
            ctx.sendMessage(Message.raw("  [" + i + "] " + zombie.getId()
                + "  HP: " + df.format(zombie.getHealth()) + "/" + df.format(zombie.getMaxHealth())
                + "  Speed: " + df.format(zombie.getSpeed())
                + "  Pos: " + zombie.getSpawnPosition()));
            i++;
        }
    }

    /**
     * /hz spawninfo
     *
     * Shows spawn progress for the current round.
     */
    private void handleSpawnInfo(CommandContext ctx) {
        GameSession session = plugin.getGameSession();
        if (!session.isSessionActive()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No active match."));
            return;
        }

        ctx.sendMessage(Message.raw("=== Spawn Progress ==="));
        ctx.sendMessage(Message.raw("  Round: " + plugin.getRoundManager().getCurrentRound()));
        ctx.sendMessage(Message.raw("  Spawned: " + session.getZombiesSpawnedThisRound() + "/" + session.getTotalZombiesForRound()));
        ctx.sendMessage(Message.raw("  Alive: " + session.getActiveZombieCount()));
        ctx.sendMessage(Message.raw("  Killed: " + session.getZombiesKilledThisRound()));
        ctx.sendMessage(Message.raw("  Remaining to spawn: " + session.getRemainingZombiesToSpawn()));
        ctx.sendMessage(Message.raw("  Active spawn zones: " + plugin.getSpawnManager().getOccupiedZones()));
    }

    // ========================================================================
    //  ECONOMY & ITEMS COMMANDS
    // ========================================================================

    /**
     * /hz points [player] [amount]
     *
     * Shows a player's current points, or sets them to a specific amount.
     */
    private void handlePoints(CommandContext ctx, String[] args) {
        String playerId = (args.length > 1) ? args[1] : "player1";

        if (args.length > 2) {
            // Set points to a specific value
            try {
                int amount = Integer.parseInt(args[2]);
                // Simulate full override via player data
                ctx.sendMessage(Message.raw("[HytaleZombie] Set " + playerId + "'s points to " + amount));
            } catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid amount: " + args[2]));
            }
        } else {
            // Show current points
            int points = 0;
            var playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
            if (playerData != null) {
                points = playerData.getPoints();
            }
            ctx.sendMessage(Message.raw("[HytaleZombie] " + playerId + " has " + points + " points."));
        }
    }

    /**
     * /hz powerup <type>
     *
     * Activates a power-up.
     * Types: nuke, instakill, doublepoints, maxammo, carpenter, bonuspoints, firesale
     */
    private void handleGivePowerUp(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz powerup <type>"));
            ctx.sendMessage(Message.raw("  Types: nuke, instakill, doublepoints, maxammo, carpenter, bonuspoints, firesale"));
            return;
        }

        GameSession session = plugin.getGameSession();
        if (!session.isSessionActive()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No active match."));
            return;
        }

        try {
            PowerUp.PowerUpType type = PowerUp.PowerUpType.valueOf(args[1].toUpperCase());
            session.activatePowerUp(type);
            ctx.sendMessage(Message.raw("[HytaleZombie] Power-up activated: " + type.getDisplayName()));
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Unknown power-up type: " + args[1]));
            ctx.sendMessage(Message.raw("  Valid: nuke, instakill, doublepoints, maxammo, carpenter, bonuspoints, firesale"));
        }
    }

    /**
     * /hz giveweapon <player> <weapon_id>
     *
     * Gives a player a weapon without spending points.
     * For testing purposes.
     */
    private void handleGiveWeapon(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz giveweapon <player> <weapon_id>"));
            ctx.sendMessage(Message.raw("  Example: /hz giveweapon player_1 raygun"));
            return;
        }

        String playerId = args[1];
        String weaponId = (args.length > 2) ? args[2] : "pistol";

        // Create a test weapon for the player
        Weapon weapon = new Weapon(
            weaponId,
            weaponId.substring(0, 1).toUpperCase() + weaponId.substring(1),
            Weapon.WeaponType.PISTOL,
            Weapon.Rarity.COMMON,
            0, 25.0f, 120, 12, 4.0f, 1.5f
        );

        GameSession session = plugin.getGameSession();
        if (session.isSessionActive()) {
            session.purchaseWeapon(playerId, weapon);
        }
        ctx.sendMessage(Message.raw("[HytaleZombie] Gave weapon '" + weaponId + "' to " + playerId));
    }

    /**
     * /hz giveperk <player> <perk_type>
     *
     * Gives a player a perk without spending points.
     * For testing purposes.
     */
    private void handleGivePerk(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz giveperk <player> <perk_type>"));
            ctx.sendMessage(Message.raw("  Example: /hz giveperk player_1 juggernog"));
            ctx.sendMessage(Message.raw("  Perks: juggernog, speedcola, quickrevive, doubletap, staminup, deadshot"));
            return;
        }

        String playerId = args[1];

        if (args.length < 3) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz giveperk <player> <perk_type>"));
            return;
        }

        try {
            // Convert from command-friendly names to enum names
            String perkInput = args[2].toUpperCase();
            // Handle common shorthand names
            if (perkInput.equals("JUGGERNOG") || perkInput.equals("JUGG")) {
                perkInput = "JUGGERNOG";
            } else if (perkInput.equals("SPEEDCOLA") || perkInput.equals("SPEED")) {
                perkInput = "SPEED_COLA";
            } else if (perkInput.equals("QUICKREVIVE") || perkInput.equals("REVIVE")) {
                perkInput = "QUICK_REVIVE";
            } else if (perkInput.equals("DOUBLETAP") || perkInput.equals("TAP")) {
                perkInput = "DOUBLE_TAP";
            } else if (perkInput.equals("STAMINUP") || perkInput.equals("STAMINA")) {
                perkInput = "STAMIN_UP";
            } else if (perkInput.equals("DEADSHOT")) {
                perkInput = "DEADSHOT_DAIQUIRI";
            }

            Perk.PerkType perkType = Perk.PerkType.valueOf(perkInput);
            GameSession session = plugin.getGameSession();
            if (session.isSessionActive()) {
                session.purchasePerk(playerId, perkType);
            }
            ctx.sendMessage(Message.raw("[HytaleZombie] Gave perk '" + perkType.getDisplayName() + "' to " + playerId));
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Unknown perk type: " + args[2]));
        }
    }

    // ========================================================================
    //  ROUND TESTING COMMANDS
    // ========================================================================

    /**
     * /hz nextround
     *
     * Force-advances to the next round immediately.
     */
    private void handleNextRound(CommandContext ctx) {
        GameSession session = plugin.getGameSession();
        if (!session.isSessionActive()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No active match."));
            return;
        }

        // Kill all remaining zombies
        int killed = session.getActiveZombieCount();
        if (killed > 0) {
            session.nukeAllZombies();
        }

        plugin.getRoundManager().advanceRound();
        session.prepareRoundSpawns();

        ctx.sendMessage(Message.raw("[HytaleZombie] Advanced to round " + plugin.getRoundManager().getCurrentRound() + "!"));
        ctx.sendMessage(Message.raw("[HytaleZombie] " + session.getTotalZombiesForRound() + " zombies incoming."));
    }

    // ========================================================================
    //  DEBUG COMMANDS
    // ========================================================================

    /**
     * /hz debug
     *
     * Toggles debug mode (spawn node visualization, barrier info, etc.).
     */
    private void handleDebug(CommandContext ctx) {
        boolean isDebug = plugin.getDebugManager().toggle();
        if (isDebug) {
            plugin.getDebugManager().visualizeSpawnNodes(plugin.getSpawnManager());
            ctx.sendMessage(Message.raw("[HytaleZombie] Debug mode ENABLED."));
        } else {
            ctx.sendMessage(Message.raw("[HytaleZombie] Debug mode DISABLED."));
        }
    }

    /**
     * /hz config [key] [value]
     *
     * Shows all config values, or sets a specific one.
     * Keys: startingPoints, zombieBaseHealth, zombieBaseSpeed, pointsPerKill,
     *        healthScaling, speedScaling, zombieSpawnBaseCount, zombiesPerPlayer, spawnDelayTicks
     */
    private void handleConfig(CommandContext ctx, String[] args) {
        HytaleZombieConfig cfg = HytaleZombiePlugin.getPluginConfig();

        if (args.length < 2) {
            // List all config values
            ctx.sendMessage(Message.raw("=== HytaleZombie Config ==="));
            ctx.sendMessage(Message.raw("  startingPoints = " + cfg.getStartingPoints()));
            ctx.sendMessage(Message.raw("  zombieBaseHealth = " + cfg.getZombieBaseHealth()));
            ctx.sendMessage(Message.raw("  zombieBaseSpeed = " + cfg.getZombieBaseSpeed()));
            ctx.sendMessage(Message.raw("  pointsPerKill = " + cfg.getPointsPerKill()));
            ctx.sendMessage(Message.raw("  healthScaling = " + cfg.getHealthScalingPerRound()));
            ctx.sendMessage(Message.raw("  speedScaling = " + cfg.getSpeedScalingPerRound()));
            ctx.sendMessage(Message.raw("  zombieSpawnBaseCount = " + cfg.getZombieSpawnBaseCount()));
            ctx.sendMessage(Message.raw("  zombiesPerPlayer = " + cfg.getZombiesPerPlayer()));
            ctx.sendMessage(Message.raw("  spawnDelayTicks = " + cfg.getSpawnDelayTicks()));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Usage: /hz config <key> <value>"));
            ctx.sendMessage(Message.raw("  Example: /hz config zombieBaseHealth 200"));
            return;
        }

        if (args.length < 3) {
            // Show single config value
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz config <key> <value>"));
            return;
        }

        String key = args[1].toLowerCase();
        String value = args[2];

        try {
            switch (key) {
                case "startingpoints" -> {
                    int v = Integer.parseInt(value);
                    cfg.setStartingPoints(v);
                    ctx.sendMessage(Message.raw("[HytaleZombie] startingPoints = " + v));
                }
                case "zombiebasehealth" -> {
                    float v = Float.parseFloat(value);
                    cfg.setZombieBaseHealth(v);
                    ctx.sendMessage(Message.raw("[HytaleZombie] zombieBaseHealth = " + v));
                }
                case "zombiebasespeed" -> {
                    float v = Float.parseFloat(value);
                    cfg.setZombieBaseSpeed(v);
                    ctx.sendMessage(Message.raw("[HytaleZombie] zombieBaseSpeed = " + v));
                }
                case "pointsperkill" -> {
                    int v = Integer.parseInt(value);
                    cfg.setPointsPerKill(v);
                    ctx.sendMessage(Message.raw("[HytaleZombie] pointsPerKill = " + v));
                }
                case "healthscaling" -> {
                    float v = Float.parseFloat(value);
                    cfg.setHealthScalingPerRound(v);
                    ctx.sendMessage(Message.raw("[HytaleZombie] healthScaling = " + v));
                }
                case "speedscaling" -> {
                    float v = Float.parseFloat(value);
                    cfg.setSpeedScalingPerRound(v);
                    ctx.sendMessage(Message.raw("[HytaleZombie] speedScaling = " + v));
                }
                case "zombiespawnbasecount" -> {
                    int v = Integer.parseInt(value);
                    cfg.setZombieSpawnBaseCount(v);
                    ctx.sendMessage(Message.raw("[HytaleZombie] zombieSpawnBaseCount = " + v));
                }
                case "zombiesperplayer" -> {
                    int v = Integer.parseInt(value);
                    cfg.setZombiesPerPlayer(v);
                    ctx.sendMessage(Message.raw("[HytaleZombie] zombiesPerPlayer = " + v));
                }
                case "spawndelayticks" -> {
                    int v = Integer.parseInt(value);
                    cfg.setSpawnDelayTicks(v);
                    ctx.sendMessage(Message.raw("[HytaleZombie] spawnDelayTicks = " + v));
                }
                default -> ctx.sendMessage(Message.raw("[HytaleZombie] Unknown config key: " + key));
            }
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Invalid value: " + value));
        }
    }

    /**
     * /hz state
     *
     * Dumps full game state for debugging.
     */
    private void handleState(CommandContext ctx) {
        GameSession session = plugin.getGameSession();
        var roundManager = plugin.getRoundManager();
        var spawnManager = plugin.getSpawnManager();
        HytaleZombieConfig cfg = HytaleZombiePlugin.getPluginConfig();

        ctx.sendMessage(Message.raw("=== HytaleZombie State ==="));
        ctx.sendMessage(Message.raw("Match: " + (session.isSessionActive() ? "ACTIVE" : "INACTIVE")));
        ctx.sendMessage(Message.raw("Tick: " + session.getTickCounter()));

        // World reference status (visible even without active match)
        World w = session.getWorld();
        ctx.sendMessage(Message.raw("World ref: " + (w != null ? "SET (" + w.getName() + ")" : "NULL - no player has fully joined yet")));

        if (session.isSessionActive()) {
            ctx.sendMessage(Message.raw("Round: " + roundManager.getCurrentRound()));
            ctx.sendMessage(Message.raw("Active Zombies: " + session.getActiveZombieCount()));
            ctx.sendMessage(Message.raw("Spawned: " + session.getZombiesSpawnedThisRound() + " / " + session.getTotalZombiesForRound()));
            ctx.sendMessage(Message.raw("Killed This Round: " + session.getZombiesKilledThisRound()));
            ctx.sendMessage(Message.raw("Players: " + session.getPlayerCount()));
            ctx.sendMessage(Message.raw("Spawn Zones: " + spawnManager.getZoneIds().size() + " total, " + spawnManager.getOccupiedZones().size() + " active"));
            ctx.sendMessage(Message.raw("Total Spawn Nodes: " + spawnManager.getTotalSpawnCount()));

            // Round scaling info
            ctx.sendMessage(Message.raw("Zombie Base HP: " + cfg.getZombieBaseHealth() + " (scaled: " + String.format("%.1f", roundManager.getScaledZombieHealth()) + ")"));
            ctx.sendMessage(Message.raw("Zombie Base Speed: " + cfg.getZombieBaseSpeed() + " (scaled: " + String.format("%.2f", roundManager.getScaledZombieSpeed()) + ")"));
            ctx.sendMessage(Message.raw("Spawn Delay: " + cfg.getSpawnDelayTicks() + " ticks (" + (cfg.getSpawnDelayTicks() * 50) + "ms)"));

            // Active power-ups
            var activePUs = session.getActivePowerUps();
            if (activePUs.isEmpty()) {
                ctx.sendMessage(Message.raw("Active Power-ups: none"));
            } else {
                StringBuilder sb = new StringBuilder("Active Power-ups: ");
                boolean first = true;
                for (var entry : activePUs.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(entry.getKey().getDisplayName());
                    if (entry.getValue().isTimed()) {
                        sb.append(" (").append(entry.getValue().getRemainingTicks() / 20).append("s)");
                    }
                    first = false;
                }
                ctx.sendMessage(Message.raw(sb.toString()));
            }
        }
    }

    // ========================================================================
    //  UTILITIES
    // ========================================================================

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
