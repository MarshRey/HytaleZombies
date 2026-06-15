package dev.hytalezombie.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.hytalezombie.HytaleZombiePlugin;
import dev.hytalezombie.manager.GameSession;
import dev.hytalezombie.model.Vector3f;
import dev.hytalezombie.spawn.SpawnNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
        ctx.sendMessage(Message.raw("Match: /hz start | stop | round [n] | info"));
        ctx.sendMessage(Message.raw("Spawn Points:"));
        ctx.sendMessage(Message.raw("  /hz setspawn <zone> [radius]          - Add spawn at 0,0,0"));
        ctx.sendMessage(Message.raw("  /hz setspawn <zone> <x> <y> <z> [r]  - Add spawn at coords"));
        ctx.sendMessage(Message.raw("  /hz delspawn <zone> [index]           - Remove zone's spawns"));
        ctx.sendMessage(Message.raw("  /hz listspawns [zone]                 - List all spawns"));
        ctx.sendMessage(Message.raw("  /hz clearspawns                       - Remove all spawns"));
        ctx.sendMessage(Message.raw("Zones:"));
        ctx.sendMessage(Message.raw("  /hz markzone <zone>                   - Mark zone as occupied"));
        ctx.sendMessage(Message.raw("  /hz unmarkzone <zone>                 - Unmark zone"));
        ctx.sendMessage(Message.raw("  /hz listzones                         - List all zones with spawns"));
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

            // Since we can't remove individual nodes, recreate the zone without this index
            // For a real implementation, add a removeSpawnNode method to SpawnManager
            ctx.sendMessage(Message.raw("[HytaleZombie] Cannot remove individual nodes yet."));
            ctx.sendMessage(Message.raw("  Use /hz delspawn " + zoneId + " (no index) to remove all in this zone."));
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
