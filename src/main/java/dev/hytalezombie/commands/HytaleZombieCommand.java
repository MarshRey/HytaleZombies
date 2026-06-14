package dev.hytalezombie.commands;

import dev.hytalezombie.HytaleZombiePlugin;

import java.util.Arrays;

/**
 * Admin command for the HytaleZombie mod.
 * Usage: /hytalezombie <subcommand>
 */
public class HytaleZombieCommand {

    private final HytaleZombiePlugin plugin;

    public HytaleZombieCommand(HytaleZombiePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Execute a command with the given arguments.
     * Returns the response message, or null if no response.
     */
    public String execute(String[] args) {
        if (args.length == 0) {
            return "§6[HytaleZombie] §eUsage: /hytalezombie <start|stop|round|info>";
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                plugin.getRoundManager().startMatch();
                return "§6[HytaleZombie] §aMatch started!";
            case "stop":
                plugin.getRoundManager().endMatch();
                return "§6[HytaleZombie] §cMatch stopped.";
            case "round":
                int round = args.length > 1 ? parseIntSafe(args[1]) : -1;
                if (round > 0) {
                    return "§6[HytaleZombie] §eSet round to: " + round;
                } else {
                    return "§6[HytaleZombie] §eCurrent round: " + plugin.getRoundManager().getCurrentRound();
                }
            case "info":
                return "§6[HytaleZombie] §aHytaleZombie v0.0.1 - Round-based survival";
            default:
                return "§6[HytaleZombie] §cUnknown subcommand: " + subCommand;
        }
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
