package dev.hytalezombie.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalezombie.HytaleZombiePlugin;
import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.entity.EntitySpawnHelper;
import dev.hytalezombie.manager.GameSession;
import dev.hytalezombie.model.Barrier;
import dev.hytalezombie.model.MapZone;
import dev.hytalezombie.model.Perk;
import dev.hytalezombie.model.PowerUp;
import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.model.Vector3i;
import dev.hytalezombie.model.Weapon;
import dev.hytalezombie.spawn.SpawnNode;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Admin command for the HytaleZombie mod.
 * Usage: /hytalezombie <subcommand>
 */
public class HytaleZombieCommand extends AbstractCommand {

    private final HytaleZombiePlugin plugin;
    private final Map<String, Subcommand> subcommands = new HashMap<>();

    /**
     * Describes a single /hz subcommand: name, aliases, minimum arg count,
     * one-line usage, and handler.
     */
    private record Subcommand(
            String name,
            List<String> aliases,
            int minArgs,
            String usage,
            BiConsumer<CommandContext, String[]> handler
    ) {
        boolean matches(String input) {
            String lower = input.toLowerCase();
            return name.equals(lower) || aliases.contains(lower);
        }
    }

    public HytaleZombieCommand(HytaleZombiePlugin plugin) {
        super("hytalezombie", "Admin command for HytaleZombie");

        this.plugin = plugin;

        // Add aliases
        addAliases("hz", "zombie");

        // Require admin permission
        // requirePermission("hytalezombie.admin"); // Commented out for testing

        // Allow extra arguments since we parse subcommands manually in execute()
        setAllowsExtraArguments(true);

        registerSubcommands();
    }

    /**
     * Registers all /hz subcommands. Keeping this in one place makes the command
     * surface easy to scan and update.
     */
    private void registerSubcommands() {
        register("start", 0, "/hz start", (ctx, args) -> handleStart(ctx));
        register("stop", 0, "/hz stop", (ctx, args) -> handleStop(ctx));
        register("round", 0, "/hz round [n]", this::handleRound);
        register("info", 0, "/hz info", (ctx, args) -> handleInfo(ctx));
        register("state", 0, "/hz state", (ctx, args) -> handleState(ctx));
        register("nextround", 0, "/hz nextround", (ctx, args) -> handleNextRound(ctx));
        register("reset", 0, "/hz reset", (ctx, args) -> handleReset(ctx));

        register("map", 0, "/hz map", (ctx, args) -> {
            plugin.setupDefaultMap();
            ctx.sendMessage(Message.raw("[HytaleZombie] Default test map set up with spawn nodes."));
        });

        register("setspawn", 1, "/hz setspawn here [radius] OR /hz setspawn <zone> <x> <y> <z> [radius]", this::handleSetSpawn);
        register("addspawn", 0, "/hz addspawn here [radius]", this::handleAddSpawn);
        register("delspawn", 1, "/hz delspawn <zone> [index]", this::handleDelSpawn);
        register("listspawns", 0, "/hz listspawns [zone]", this::handleListSpawns);
        register("clearspawns", 0, "/hz clearspawns", (ctx, args) -> handleClearSpawns(ctx));

        register("addzone", 2, "/hz addzone <zoneId> <displayName> [cost]", this::handleAddZone);
        register("connectzone", 2, "/hz connectzone <zoneA> <zoneB>", this::handleConnectZone);
        register("setdoor", 8, "/hz setdoor <zoneA> <zoneB> <x1> <y1> <z1> <x2> <y2> <z2>", this::handleSetDoor);
        register("adddoor", 2, "/hz adddoor <zoneA> <zoneB> [width] [height]", this::handleAddDoor);
        register("removezone", 1, "/hz removezone <zoneId>", this::handleRemoveZone);
        register("markzone", 1, "/hz markzone <zone>", this::handleMarkZone);
        register("unmarkzone", 1, "/hz unmarkzone <zone>", this::handleUnmarkZone);
        register("listzones", 0, "/hz listzones", (ctx, args) -> handleListZones(ctx));

        register("barrier", 1, "/hz barrier <add|remove|list> ...", this::handleBarrier);

        register("spawnzombie", 0, "/hz spawnzombie <count> [zone]", this::handleSpawnZombie);
        register("spawnhere", 0, "/hz spawnhere", (ctx, args) -> handleSpawnHere(ctx));
        register("summon", 0, "/hz summon", (ctx, args) -> handleSpawnHere(ctx));
        register("killall", 0, "/hz killall", (ctx, args) -> handleKillAll(ctx));
        register("zombieinfo", 0, "/hz zombieinfo", (ctx, args) -> handleZombieInfo(ctx));
        register("spawninfo", 0, "/hz spawninfo", (ctx, args) -> handleSpawnInfo(ctx));

        register("points", 0, "/hz points [player] [amount]", this::handlePoints);
        register("powerup", 1, "/hz powerup <type>", this::handleGivePowerUp);
        register("giveweapon", 1, "/hz giveweapon <player> <weapon_id>", this::handleGiveWeapon);
        register("giveperk", 2, "/hz giveperk <player> <perk_type>", this::handleGivePerk);

        register("debug", 0, "/hz debug [spawns|barriers|zones]", this::handleDebug);
        register("config", 0, "/hz config [key] [value]", this::handleConfig);
        register("setup", 0, "/hz setup", (ctx, args) -> handleSetup(ctx));
        register("validate", 0, "/hz validate", (ctx, args) -> handleValidate(ctx));
        register("savemap", 0, "/hz savemap", (ctx, args) -> handleSaveMap(ctx));
    }

    private void register(String name, int minArgs, String usage, BiConsumer<CommandContext, String[]> handler, String... aliases) {
        Subcommand cmd = new Subcommand(name, Arrays.asList(aliases), minArgs, usage, handler);
        subcommands.put(name, cmd);
        for (String alias : aliases) {
            subcommands.put(alias, cmd);
        }
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String input = ctx.getInputString();
        String[] args = (input == null || input.isEmpty()) ? new String[0] : input.split(" ");

        plugin.getLogger().at(java.util.logging.Level.INFO).log("HytaleZombieCommand received input: '{0}'", input);

        // Strip the leading command name if the caller included it
        if (args.length > 0) {
            String first = args[0].toLowerCase();
            if (first.equals("hytalezombie") || first.equals("hz") || first.equals("zombie")) {
                args = Arrays.copyOfRange(args, 1, args.length);
            }
        }

        if (args.length == 0) {
            sendUsage(ctx);
            return CompletableFuture.completedFuture(null);
        }

        String subName = args[0].toLowerCase();
        Subcommand sub = subcommands.get(subName);
        if (sub == null) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Unknown subcommand: " + subName));
            sendUsage(ctx);
            return CompletableFuture.completedFuture(null);
        }

        if (args.length - 1 < sub.minArgs()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: " + sub.usage()));
            return CompletableFuture.completedFuture(null);
        }

        try {
            sub.handler().accept(ctx, args);
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Error handling /hz {0}: {1}", new Object[]{subName, e.getMessage()});
            ctx.sendMessage(Message.raw("[HytaleZombie] Command failed: " + e.getMessage()));
        }

        return CompletableFuture.completedFuture(null);
    }

    // ========================================================================
    //  USAGE
    // ========================================================================

    private void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== HytaleZombie Commands ==="));
        ctx.sendMessage(Message.raw("Match: /hz start | stop | round [n] | nextround | info | state | reset"));
        ctx.sendMessage(Message.raw("Map Setup:"));
        ctx.sendMessage(Message.raw("  /hz setup                             - Start map setup wizard"));
        ctx.sendMessage(Message.raw("  /hz validate                          - Check map is playable"));
        ctx.sendMessage(Message.raw("  /hz savemap                           - Save full map layout"));
        ctx.sendMessage(Message.raw("Spawn Points:"));
        ctx.sendMessage(Message.raw("  /hz setspawn here [radius]            - Add spawn at your feet"));
        ctx.sendMessage(Message.raw("  /hz setspawn <zone> <x> <y> <z> [r]  - Add spawn at coords"));
        ctx.sendMessage(Message.raw("  /hz addspawn here [radius]            - Alias for setspawn here"));
        ctx.sendMessage(Message.raw("  /hz delspawn <zone> [index]           - Remove zone's spawns"));
        ctx.sendMessage(Message.raw("  /hz listspawns [zone]                 - List all spawns"));
        ctx.sendMessage(Message.raw("  /hz clearspawns                       - Remove all spawns"));
        ctx.sendMessage(Message.raw("Zones & Doors:"));
        ctx.sendMessage(Message.raw("  /hz addzone <id> <name> [cost]        - Register a new zone"));
        ctx.sendMessage(Message.raw("  /hz connectzone <A> <B>               - Connect two zones"));
        ctx.sendMessage(Message.raw("  /hz setdoor <A> <B> x1 y1 z1 x2 y2 z2 - Door AABB from two corners"));
        ctx.sendMessage(Message.raw("  /hz adddoor <A> <B> [width] [height]  - Door centered on you"));
        ctx.sendMessage(Message.raw("  /hz removezone <zone>                 - Remove a zone"));
        ctx.sendMessage(Message.raw("  /hz markzone <zone>                   - Mark zone as occupied"));
        ctx.sendMessage(Message.raw("  /hz unmarkzone <zone>                 - Unmark zone"));
        ctx.sendMessage(Message.raw("  /hz listzones                         - List all zones"));
        ctx.sendMessage(Message.raw("Barriers:"));
        ctx.sendMessage(Message.raw("  /hz barrier add <zone> [x y z]        - Add barrier at target block"));
        ctx.sendMessage(Message.raw("  /hz barrier remove [x y z]            - Remove barrier"));
        ctx.sendMessage(Message.raw("  /hz barrier list [zone]               - List barriers"));
        ctx.sendMessage(Message.raw("Zombie Testing:"));
        ctx.sendMessage(Message.raw("  /hz spawnzombie <count> [zone]        - Spawn zombie(s) at spawn nodes"));
        ctx.sendMessage(Message.raw("  /hz spawnhere                         - Spawn zombie at your position"));
        ctx.sendMessage(Message.raw("  /hz summon                            - Alias for spawnhere"));
        ctx.sendMessage(Message.raw("  /hz killall                           - Kill all active zombies"));
        ctx.sendMessage(Message.raw("  /hz zombieinfo                        - List all zombie stats"));
        ctx.sendMessage(Message.raw("  /hz spawninfo                         - Spawn progress this round"));
        ctx.sendMessage(Message.raw("Economy & Items:"));
        ctx.sendMessage(Message.raw("  /hz points [player] [amount]          - Get/set points"));
        ctx.sendMessage(Message.raw("  /hz powerup <type>                    - Activate a power-up"));
        ctx.sendMessage(Message.raw("  /hz giveweapon <player> <weapon_id>   - Give player a weapon"));
        ctx.sendMessage(Message.raw("  /hz giveperk <player> <perk_type>     - Give player a perk"));
        ctx.sendMessage(Message.raw("Debug:"));
        ctx.sendMessage(Message.raw("  /hz debug [spawns|barriers|zones]     - Toggle debug markers"));
        ctx.sendMessage(Message.raw("  /hz config [key] [value]              - View/set config"));
        ctx.sendMessage(Message.raw("  /hz state                             - Full game state dump"));
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
        GameSession session = plugin.getGameSession();
        if (args.length > 1) {
            int round = parseIntSafe(args[1]);
            if (round < 1) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid round number: " + args[1]));
                return;
            }
            if (!session.isSessionActive()) {
                ctx.sendMessage(Message.raw("[HytaleZombie] No active match. Start one with /hz start first."));
                return;
            }
            plugin.getRoundManager().setRound(round);
            session.prepareRoundSpawns();
            ctx.sendMessage(Message.raw("[HytaleZombie] Set round to " + round + "!"));
            ctx.sendMessage(Message.raw("[HytaleZombie] " + session.getTotalZombiesForRound() + " zombies this round."));
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
     * /hz setspawn here [radius]
     *    or
     * /hz setspawn <zone> <x> <y> <z> [radius]
     *
     * Adds a spawn point for the given zone.
     * Use "here" to place a spawn at the admin's feet.
     * Use explicit coordinates to place a spawn anywhere.
     */
    private void handleSetSpawn(CommandContext ctx, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("help")) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage:"));
            ctx.sendMessage(Message.raw("  /hz setspawn here [radius]"));
            ctx.sendMessage(Message.raw("  /hz setspawn <zone> <x> <y> <z> [radius]"));
            return;
        }

        String zoneId;
        Vector3f position;
        float radius = 5.0f;

        if (args[1].equalsIgnoreCase("here")) {
            Optional<Vector3f> posOpt = getPlayerPosition(ctx);
            if (posOpt.isEmpty()) {
                ctx.sendMessage(Message.raw("[HytaleZombie] 'here' can only be used by a player."));
                return;
            }
            zoneId = plugin.getGameSession().getPlayerZone(getSenderPlayerId(ctx));
            position = posOpt.get();
            if (args.length >= 3) {
                radius = parseFloatSafe(args[2]);
                if (Float.isNaN(radius) || radius < 0) {
                    ctx.sendMessage(Message.raw("[HytaleZombie] Invalid radius: " + args[2]));
                    return;
                }
            }
        } else {
            if (args.length < 5) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz setspawn <zone> <x> <y> <z> [radius]"));
                return;
            }
            zoneId = args[1];
            float x = parseFloatSafe(args[2]);
            float y = parseFloatSafe(args[3]);
            float z = parseFloatSafe(args[4]);
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid coordinates. Use numbers for x y z."));
                return;
            }
            position = new Vector3f(x, y, z);
            if (args.length >= 6) {
                radius = parseFloatSafe(args[5]);
                if (Float.isNaN(radius) || radius < 0) {
                    ctx.sendMessage(Message.raw("[HytaleZombie] Invalid radius: " + args[5]));
                    return;
                }
            }
        }

        ensureZoneExists(zoneId);
        registerSpawnNode(ctx, zoneId, position, radius);
    }

    /**
     * /hz addspawn here [radius]
     *
     * Convenience alias that places a spawn node at the admin's feet
     * in their current zone.
     */
    private void handleAddSpawn(CommandContext ctx, String[] args) {
        Optional<Vector3f> posOpt = getPlayerPosition(ctx);
        if (posOpt.isEmpty()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] This command can only be used by a player."));
            return;
        }

        float radius = 5.0f;
        if (args.length >= 3) {
            radius = parseFloatSafe(args[2]);
            if (Float.isNaN(radius) || radius < 0) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid radius: " + args[2]));
                return;
            }
        }

        String zoneId = plugin.getGameSession().getPlayerZone(getSenderPlayerId(ctx));
        ensureZoneExists(zoneId);
        registerSpawnNode(ctx, zoneId, posOpt.get(), radius);
    }

    private void ensureZoneExists(String zoneId) {
        if (plugin.getZoneManager().getZone(zoneId) == null) {
            MapZone newZone = new MapZone(zoneId, zoneId, 1000);
            plugin.getZoneManager().registerZone(newZone);
        }
    }

    private void registerSpawnNode(CommandContext ctx, String zoneId, Vector3f position, float radius) {
        SpawnNode node = new SpawnNode(zoneId, position, radius);
        plugin.getSpawnManager().registerSpawnNode(node);
        plugin.getSpawnManager().markZoneOccupied(zoneId);
        plugin.saveMapData();
        plugin.getDebugManager().refreshMarkers(plugin);

        ctx.sendMessage(Message.raw("[HytaleZombie] Spawn point added!"));
        ctx.sendMessage(Message.raw("  Zone: " + zoneId));
        ctx.sendMessage(Message.raw("  Position: (" + String.format("%.1f", position.x()) + ", "
            + String.format("%.1f", position.y()) + ", " + String.format("%.1f", position.z()) + ")"));
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
                plugin.saveMapData();
                ctx.sendMessage(Message.raw("[HytaleZombie] Removed spawn node [" + index + "] from zone '" + zoneId + "'"));
            } else {
                ctx.sendMessage(Message.raw("[HytaleZombie] Failed to remove spawn node [" + index + "]"));
            }
        } else {
            // Remove all nodes in this zone
            plugin.getSpawnManager().removeNodesInZone(zoneId);
            plugin.saveMapData();
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
        plugin.saveMapData();
        ctx.sendMessage(Message.raw("[HytaleZombie] All spawn points cleared!"));
        ctx.sendMessage(Message.raw("  Use /hz setspawn <zone> to add new ones."));
    }

    // ========================================================================
    //  ZONE COMMANDS
    // ========================================================================

    /**
     * /hz addzone <zoneId> <displayName> [doorCost]
     *
     * Registers a new zone in the map. Zones must be connected via /hz connectzone
     * before they can be accessed, and door positions must be set via /hz setdoor
     * for automatic zone tracking.
     */
    private void handleAddZone(CommandContext ctx, String[] args) {
        if (args.length < 3) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz addzone <zoneId> <displayName> [doorCost]"));
            ctx.sendMessage(Message.raw("  Example: /hz addzone room_2 \"Room 2\" 1500"));
            return;
        }

        String zoneId = args[1];
        String displayName = args[2];
        int doorCost = 1000; // default
        if (args.length > 3) {
            try {
                doorCost = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid door cost: " + args[3]));
                return;
            }
        }

        dev.hytalezombie.model.MapZone zone = new dev.hytalezombie.model.MapZone(zoneId, displayName, doorCost);
        plugin.getZoneManager().registerZone(zone);
        plugin.saveMapData();
        plugin.getDebugManager().refreshMarkers(plugin);
        ctx.sendMessage(Message.raw("[HytaleZombie] Zone '" + zoneId + "' (" + displayName
            + ") registered. Cost to unlock: " + doorCost + " points."));
    }

    /**
     * /hz connectzone <zoneA> <zoneB>
     *
     * Connects two zones bidirectionally. Doors between them are placed
     * via /hz setdoor.
     */
    private void handleConnectZone(CommandContext ctx, String[] args) {
        if (args.length < 3) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz connectzone <zoneA> <zoneB>"));
            ctx.sendMessage(Message.raw("  Example: /hz connectzone spawn_room room_2"));
            return;
        }

        String zoneA = args[1];
        String zoneB = args[2];
        plugin.getZoneManager().connectZones(zoneA, zoneB);
        plugin.saveMapData();
        plugin.getDebugManager().refreshMarkers(plugin);
        ctx.sendMessage(Message.raw("[HytaleZombie] Zones '" + zoneA + "' and '" + zoneB
            + "' connected. Use /hz setdoor to place the door between them."));
    }

    /**
     * /hz setdoor <zoneA> <zoneB> <x1> <y1> <z1> <x2> <y2> <z2>
     *
     * Sets a door area between two connected zones using two corner points.
     * Stand at one side of the doorway, run the command; stand at the other side,
     * run it again with the second corner. The door becomes the full rectangular
     * area between the two points — works for any width/height/depth.
     *
     * <p>Both zones must already be connected via /hz connectzone.</p>
     */
    private void handleSetDoor(CommandContext ctx, String[] args) {
        if (args.length < 9) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz setdoor <zoneA> <zoneB> <x1> <y1> <z1> <x2> <y2> <z2>"));
            ctx.sendMessage(Message.raw("  Stand at one side of the door: /hz setdoor spawn_room room_2 10.5 64 -5.0"));
            ctx.sendMessage(Message.raw("  Then stand at the other side:   (same command with second position)"));
            ctx.sendMessage(Message.raw("  The door area is the AABB between the two points. Use all 6 coords at once."));
            ctx.sendMessage(Message.raw("  Example one-liner: /hz setdoor spawn_room room_2 10 64 -5 12 67 -5"));
            return;
        }

        String zoneA = args[1];
        String zoneB = args[2];
        float x1, y1, z1, x2, y2, z2;
        try {
            x1 = Float.parseFloat(args[3]);
            y1 = Float.parseFloat(args[4]);
            z1 = Float.parseFloat(args[5]);
            x2 = Float.parseFloat(args[6]);
            y2 = Float.parseFloat(args[7]);
            z2 = Float.parseFloat(args[8]);
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Invalid coordinates. Use numbers for all 6 values."));
            return;
        }

        try {
            plugin.getZoneManager().setDoorArea(zoneA, zoneB,
                new dev.hytalezombie.model.Vector3f(x1, y1, z1),
                new dev.hytalezombie.model.Vector3f(x2, y2, z2));
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.raw("[HytaleZombie] " + e.getMessage()));
            return;
        }

        var area = plugin.getZoneManager().getDoorArea(zoneA, zoneB);
        plugin.saveMapData();
        plugin.getDebugManager().refreshMarkers(plugin);
        ctx.sendMessage(Message.raw("[HytaleZombie] Door area placed between '" + zoneA + "' and '"
            + zoneB + "'. " + (area != null ? area.toString() : "")
            + ". Players entering this area will cross zones."));
    }

    /**
     * /hz removezone <zoneId>
     *
     * Removes a zone and cleans up all connections, door areas, and spawn nodes.
     * The starting zone cannot be removed.
     */
    private void handleRemoveZone(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz removezone <zoneId>"));
            ctx.sendMessage(Message.raw("  Removes the zone, its door areas, and spawn nodes."));
            ctx.sendMessage(Message.raw("  Connected zones are cleaned up automatically."));
            return;
        }

        String zoneId = args[1];
        try {
            plugin.getZoneManager().removeZone(zoneId, plugin.getSpawnManager());
            plugin.saveMapData();
            plugin.getDebugManager().refreshMarkers(plugin);
            ctx.sendMessage(Message.raw("[HytaleZombie] Zone '" + zoneId + "' removed. All connections and spawns cleaned up."));
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.raw("[HytaleZombie] " + e.getMessage()));
        }
    }

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
        plugin.saveMapData();
        plugin.getDebugManager().refreshMarkers(plugin);
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
        plugin.saveMapData();
        plugin.getDebugManager().refreshMarkers(plugin);
        ctx.sendMessage(Message.raw("[HytaleZombie] Zone '" + zoneId + "' marked as unoccupied."));
    }

    /**
     * /hz listzones
     *
     * Lists all registered zones from ZoneManager, including zones without spawn points.
     * Shows zone info (name, cost, door connections, door areas) and spawn node counts.
     */
    private void handleListZones(CommandContext ctx) {
        var zoneManager = plugin.getZoneManager();
        var spawnManager = plugin.getSpawnManager();
        java.util.Collection<dev.hytalezombie.model.MapZone> allZones = zoneManager.getAllZones();
        java.util.Set<String> occupied = plugin.getSpawnManager().getOccupiedZones();

        ctx.sendMessage(Message.raw("=== Zones (" + allZones.size() + " registered) ==="));

        if (allZones.isEmpty()) {
            ctx.sendMessage(Message.raw("  No zones registered. Use /hz addzone to create one."));
            return;
        }

        for (dev.hytalezombie.model.MapZone zone : allZones) {
            String zoneId = zone.getZoneId();
            java.util.List<SpawnNode> nodes = spawnManager.getNodesInZone(zoneId);
            boolean isOccupied = occupied.contains(zoneId);
            boolean isUnlocked = zone.isUnlocked();
            String lockIcon = isUnlocked ? "" : " [LOCKED]";
            String status = isOccupied ? "ACTIVE" : (isUnlocked ? "open" : "locked");

            StringBuilder sb = new StringBuilder();
            sb.append("  * ").append(zoneId).append(lockIcon);
            sb.append(" \"").append(zone.getDisplayName()).append("\"");
            sb.append(" — ").append(nodes.size()).append(" spawns");
            if (zone.getDoorCost() > 0) {
                sb.append(", cost=").append(zone.getDoorCost());
            }
            sb.append(", ").append(zone.getConnectedZoneIds().size()).append(" connections");

            var doorAreas = zone.getDoorAreas();
            if (!doorAreas.isEmpty()) {
                sb.append(", ").append(doorAreas.size()).append(" doors");
            }

            String markHint = (!isOccupied && isUnlocked) ? "  (/hz markzone " + zoneId + " to activate)" : "";
            if (!markHint.isEmpty()) {
                sb.append(markHint);
            }

            if (nodes.isEmpty() && spawnManager.getZoneIds().contains(zoneId)) {
                // Zone has spawns in SpawnManager but maybe from a different tracking
            }

            ctx.sendMessage(Message.raw("    " + sb.toString().trim()));
        }
        ctx.sendMessage(Message.raw("Tip: zones auto-track occupancy from player positions via door-crossing."));
    }

    // ========================================================================
    //  BARRIER COMMANDS
    // ========================================================================

    /**
     * /hz barrier add <zone> [x y z]
     * /hz barrier remove [x y z]
     * /hz barrier list [zone]
     *
     * Manages window barriers. If coordinates are omitted, uses the block
     * the admin is standing on.
     */
    private void handleBarrier(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage:"));
            ctx.sendMessage(Message.raw("  /hz barrier add <zone> [x y z]"));
            ctx.sendMessage(Message.raw("  /hz barrier remove [x y z]"));
            ctx.sendMessage(Message.raw("  /hz barrier list [zone]"));
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "add" -> handleBarrierAdd(ctx, args);
            case "remove", "delete", "del" -> handleBarrierRemove(ctx, args);
            case "list", "show" -> handleBarrierList(ctx, args);
            default -> {
                ctx.sendMessage(Message.raw("[HytaleZombie] Unknown barrier action: " + action));
                ctx.sendMessage(Message.raw("  Use add, remove, or list."));
            }
        }
    }

    private void handleBarrierAdd(CommandContext ctx, String[] args) {
        if (args.length < 3) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz barrier add <zone> [x y z]"));
            return;
        }

        String zoneId = args[2];
        Vector3i position;
        if (args.length >= 6) {
            try {
                int x = parseCoord(args[3], "x");
                int y = parseCoord(args[4], "y");
                int z = parseCoord(args[5], "z");
                position = new Vector3i(x, y, z);
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.raw("[HytaleZombie] " + e.getMessage()));
                return;
            }
        } else {
            Optional<Vector3i> posOpt = getPlayerBlockPosition(ctx);
            if (posOpt.isEmpty()) {
                ctx.sendMessage(Message.raw("[HytaleZombie] 'here' placement requires a player. Provide x y z instead."));
                return;
            }
            position = posOpt.get();
        }

        if (plugin.getBarrierManager().hasBarrierAt(position)) {
            ctx.sendMessage(Message.raw("[HytaleZombie] A barrier already exists at " + position + "."));
            return;
        }

        plugin.getBarrierManager().registerBarrier(new Barrier(zoneId, position));
        plugin.saveMapData();
        plugin.getDebugManager().refreshMarkers(plugin);
        ctx.sendMessage(Message.raw("[HytaleZombie] Barrier added in zone '" + zoneId + "' at " + position));
    }

    private void handleBarrierRemove(CommandContext ctx, String[] args) {
        Vector3i position;
        if (args.length >= 5) {
            try {
                int x = parseCoord(args[2], "x");
                int y = parseCoord(args[3], "y");
                int z = parseCoord(args[4], "z");
                position = new Vector3i(x, y, z);
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.raw("[HytaleZombie] " + e.getMessage()));
                return;
            }
        } else {
            Optional<Vector3i> posOpt = getPlayerBlockPosition(ctx);
            if (posOpt.isEmpty()) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Provide x y z or run as a player."));
                return;
            }
            position = posOpt.get();
        }

        if (!plugin.getBarrierManager().hasBarrierAt(position)) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No barrier at " + position + "."));
            return;
        }

        plugin.getBarrierManager().removeBarrier(position);
        plugin.saveMapData();
        plugin.getDebugManager().refreshMarkers(plugin);
        ctx.sendMessage(Message.raw("[HytaleZombie] Barrier removed at " + position));
    }

    private void handleBarrierList(CommandContext ctx, String[] args) {
        String filterZone = args.length > 2 ? args[2] : null;
        Collection<Barrier> barriers;
        if (filterZone != null) {
            barriers = plugin.getBarrierManager().getBarriersInZone(filterZone);
        } else {
            barriers = new ArrayList<>();
            for (String zoneId : plugin.getZoneManager().getAllZones().stream().map(MapZone::getZoneId).toList()) {
                barriers.addAll(plugin.getBarrierManager().getBarriersInZone(zoneId));
            }
        }

        ctx.sendMessage(Message.raw("=== Barriers ==="));
        if (barriers.isEmpty()) {
            ctx.sendMessage(Message.raw("  No barriers registered."));
            ctx.sendMessage(Message.raw("  Use /hz barrier add <zone> [x y z] to add one."));
            return;
        }

        for (Barrier barrier : barriers) {
            ctx.sendMessage(Message.raw("  * " + barrier.getZoneId() + " @ " + barrier.getBlockPosition()
                + " [" + barrier.getState() + "]"));
        }
    }

    // ========================================================================
    //  ZOMBIE TESTING COMMANDS
    // ========================================================================

    /**
     * /hz spawnzombie <count> [zone]
     *
     * Manually spawns zombies, bypassing the spawn delay timer.
     * Count is required; zone is optional and defaults to active zones.
     */
    private void handleSpawnZombie(CommandContext ctx, String[] args) {
        GameSession session = plugin.getGameSession();
        boolean matchActive = session.isSessionActive();

        if (args.length < 2) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz spawnzombie <count> [zone]"));
            return;
        }

        int count = parseIntSafe(args[1]);
        if (count < 1) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Invalid count: " + args[1]));
            return;
        }

        String zoneId = (args.length > 2) ? args[2] : null;
        if (zoneId != null && !plugin.getSpawnManager().hasNodesInZone(zoneId)) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Zone '" + zoneId + "' has no spawn nodes."));
            ctx.sendMessage(Message.raw("  Use /hz listspawns to see available zones."));
            return;
        }

        if (!matchActive) {
            ctx.sendMessage(Message.raw("[HytaleZombie] No active match - spawning test zombie(s) without round tracking."));
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
        String playerId;
        if (args.length > 1) {
            playerId = args[1];
        } else if (ctx.isPlayer()) {
            playerId = getSenderPlayerId(ctx);
        } else {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz points <player> [amount]"));
            return;
        }

        var playerData = plugin.getPlayerDataManager().getOrCreatePlayerData(playerId);

        if (args.length > 2) {
            try {
                int amount = Integer.parseInt(args[2]);
                playerData.setPoints(amount);
                ctx.sendMessage(Message.raw("[HytaleZombie] Set " + playerId + "'s points to " + amount));
            } catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw("[HytaleZombie] Invalid amount: " + args[2]));
            }
        } else {
            ctx.sendMessage(Message.raw("[HytaleZombie] " + playerId + " has " + playerData.getPoints() + " points."));
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
            ctx.sendMessage(Message.raw("  Example: /hz giveweapon player_1 ray_gun"));
            ctx.sendMessage(Message.raw("  Weapons: pistol, rifle, shotgun, smg, sniper, ray_gun, ak47, hunting_rifle, spas12, thompson, wunderwaffe, mystery_melee"));
            return;
        }

        String playerId = args[1];
        String weaponId = (args.length > 2) ? args[2] : "pistol";

        Weapon weapon = plugin.getWeaponRegistry().getWeapon(weaponId);
        if (weapon == null) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Unknown weapon: '" + weaponId + "'"));
            ctx.sendMessage(Message.raw("  Use /hz giveweapon <player> <weapon_id> with a valid weapon ID."));
            return;
        }

        GameSession session = plugin.getGameSession();
        if (session.isSessionActive()) {
            session.purchaseWeapon(playerId, weapon);
        }
        ctx.sendMessage(Message.raw("[HytaleZombie] Gave weapon '" + weapon.getDisplayName() + "' to " + playerId));
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
     * /hz debug [spawns|barriers|zones]
     *
     * Toggles debug markers. With no argument, toggles all markers.
     * With a layer argument, toggles only that layer.
     */
    private void handleDebug(CommandContext ctx, String[] args) {
        if (args.length < 2) {
            boolean isDebug = plugin.getDebugManager().toggleAll();
            ctx.sendMessage(Message.raw("[HytaleZombie] Debug mode " + (isDebug ? "ENABLED" : "DISABLED") + "."));
        } else {
            String layer = args[1].toLowerCase();
            boolean isDebug = plugin.getDebugManager().toggleLayer(layer);
            ctx.sendMessage(Message.raw("[HytaleZombie] Debug layer '" + layer + "' " + (isDebug ? "ENABLED" : "DISABLED") + "."));
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

    /**
     * /hz adddoor <zoneA> <zoneB> [width] [height]
     *
     * Creates a door area centered on the admin. Defaults to 3 blocks wide
     * and 3 blocks tall, spanning the admin's Y level up.
     */
    private void handleAddDoor(CommandContext ctx, String[] args) {
        if (args.length < 3) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Usage: /hz adddoor <zoneA> <zoneB> [width] [height]"));
            return;
        }

        String zoneA = args[1];
        String zoneB = args[2];
        int width = (args.length > 3) ? parseIntSafe(args[3]) : 3;
        int height = (args.length > 4) ? parseIntSafe(args[4]) : 3;
        if (width < 1) width = 3;
        if (height < 1) height = 3;

        Optional<Vector3f> posOpt = getPlayerPosition(ctx);
        if (posOpt.isEmpty()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] This command can only be used by a player."));
            return;
        }

        Vector3f center = posOpt.get();
        float halfWidth = width / 2.0f;
        Vector3f corner1 = new Vector3f(center.x() - halfWidth, center.y(), center.z() - halfWidth);
        Vector3f corner2 = new Vector3f(center.x() + halfWidth, center.y() + height - 1, center.z() + halfWidth);

        try {
            plugin.getZoneManager().setDoorArea(zoneA, zoneB, corner1, corner2);
            plugin.saveMapData();
            plugin.getDebugManager().refreshMarkers(plugin);
            ctx.sendMessage(Message.raw("[HytaleZombie] Door area added between '" + zoneA + "' and '" + zoneB + "'."));
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.raw("[HytaleZombie] " + e.getMessage()));
        }
    }

    /**
     * /hz reset
     *
     * Stops any active match and clears all map setup data (spawns, zones,
     * doors, barriers) except the starting zone.
     */
    private void handleReset(CommandContext ctx) {
        GameSession session = plugin.getGameSession();
        if (session.isSessionActive()) {
            session.endMatch();
        }
        plugin.getSpawnManager().clearAllNodes();
        plugin.getZoneManager().clearAll();
        plugin.getBarrierManager().clearAll();
        plugin.saveMapData();
        plugin.getDebugManager().clearMarkers(plugin);
        ctx.sendMessage(Message.raw("[HytaleZombie] Map reset complete. Starting zone preserved."));
    }

    /**
     * /hz setup
     *
     * Starts the map setup wizard and ensures spawn_room exists.
     */
    private void handleSetup(CommandContext ctx) {
        plugin.setupDefaultMap();
        ctx.sendMessage(Message.raw("=== HytaleZombie Map Setup ==="));
        ctx.sendMessage(Message.raw("1. Place spawn points: /hz addspawn here [radius]"));
        ctx.sendMessage(Message.raw("2. Add zones: /hz addzone <id> <name> [cost]"));
        ctx.sendMessage(Message.raw("3. Connect zones: /hz connectzone <A> <B>"));
        ctx.sendMessage(Message.raw("4. Add doors: /hz adddoor <A> <B> [width] [height]"));
        ctx.sendMessage(Message.raw("5. Add barriers: /hz barrier add <zone> [x y z]"));
        ctx.sendMessage(Message.raw("6. Validate: /hz validate"));
        ctx.sendMessage(Message.raw("7. Start match: /hz start"));
    }

    /**
     * /hz validate
     *
     * Checks whether the current map is ready to play.
     */
    private void handleValidate(CommandContext ctx) {
        List<String> issues = new ArrayList<>();

        if (plugin.getSpawnManager().getTotalSpawnCount() == 0) {
            issues.add("No spawn points registered. Use /hz addspawn here.");
        }

        if (!plugin.getSpawnManager().hasNodesInZone("spawn_room")) {
            issues.add("spawn_room has no spawn points.");
        }

        for (MapZone zone : plugin.getZoneManager().getAllZones()) {
            if (zone.isUnlocked() && !plugin.getSpawnManager().hasNodesInZone(zone.getZoneId())) {
                issues.add("Zone '" + zone.getZoneId() + "' is unlocked but has no spawn points.");
            }
        }

        if (issues.isEmpty()) {
            ctx.sendMessage(Message.raw("[HytaleZombie] Map is valid and ready to play!"));
        } else {
            ctx.sendMessage(Message.raw("[HytaleZombie] Validation issues:"));
            for (String issue : issues) {
                ctx.sendMessage(Message.raw("  - " + issue));
            }
        }
    }

    /**
     * /hz savemap
     *
     * Forces an immediate save of the full map layout.
     */
    private void handleSaveMap(CommandContext ctx) {
        plugin.saveMapData();
        ctx.sendMessage(Message.raw("[HytaleZombie] Map layout saved."));
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

    private int parseCoord(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + " coordinate: " + value);
        }
    }

    private float parseFloatSafe(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    /**
     * Returns the UUID string of the player who sent the command.
     * Falls back to "console" for non-player senders.
     */
    private String getSenderPlayerId(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            return "console";
        }
        try {
            return ctx.sender().getUuid().toString();
        } catch (Exception e) {
            plugin.getLogger().at(Level.FINE).log("Could not get sender UUID: {0}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * Gets the admin's current world position, or empty if not a player.
     * Uses the position cached by the game loop instead of reading ECS directly,
     * so it is safe to call from the command thread.
     */
    private Optional<Vector3f> getPlayerPosition(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            return Optional.empty();
        }
        String playerId = getSenderPlayerId(ctx);
        Vector3f pos = plugin.getGameSession().getPlayerPosition(playerId);
        return pos != null ? Optional.of(pos) : Optional.empty();
    }

    /**
     * Gets the block position the admin is standing on, or empty if not a player.
     */
    private Optional<Vector3i> getPlayerBlockPosition(CommandContext ctx) {
        return getPlayerPosition(ctx).map(pos -> new Vector3i((int) Math.floor(pos.x()), (int) Math.floor(pos.y()), (int) Math.floor(pos.z())));
    }

    // ========================================================================
    //  TAB COMPLETION
    // ========================================================================

    public java.util.List<String> getSuggestions(CommandContext ctx) {
        String input = ctx.getInputString();
        String[] parts = (input == null || input.isEmpty()) ? new String[0] : input.split(" ");

        // Strip leading command name
        String sub = parts.length > 0 ? parts[0].toLowerCase() : "";
        int argIndex = 0;
        if (sub.equals("hytalezombie") || sub.equals("hz") || sub.equals("zombie")) {
            argIndex = 1;
            sub = parts.length > 1 ? parts[1].toLowerCase() : "";
        }

        // Suggest subcommands
        if (parts.length <= argIndex + 1) {
            return java.util.List.of(
                "start", "stop", "round", "info", "state", "nextround", "reset",
                "setup", "validate", "savemap",
                "setspawn", "addspawn", "delspawn", "listspawns", "clearspawns",
                "addzone", "connectzone", "setdoor", "adddoor", "removezone",
                "markzone", "unmarkzone", "listzones",
                "barrier",
                "spawnzombie", "spawnhere", "summon", "killall", "zombieinfo", "spawninfo",
                "points", "powerup", "giveweapon", "giveperk",
                "debug", "config"
            );
        }

        // Layer suggestions for debug
        if (sub.equals("debug")) {
            return java.util.List.of("spawns", "barriers", "zones");
        }

        // Barrier action suggestions
        if (sub.equals("barrier")) {
            return java.util.List.of("add", "remove", "list");
        }

        // Zone name suggestions
        if (java.util.Set.of("setspawn", "delspawn", "addzone", "connectzone",
                "setdoor", "adddoor", "removezone", "markzone", "unmarkzone",
                "spawnzombie", "barrier").contains(sub)) {
            return plugin.getZoneManager().getAllZones().stream()
                .map(dev.hytalezombie.model.MapZone::getZoneId)
                .collect(java.util.stream.Collectors.toList());
        }
        return java.util.List.of();
    }
}