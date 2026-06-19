package dev.hytalezombie.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Custom HUD overlay for the HytaleZombie game mode.
 * Displays round number, active zombie count, and player points
 * as a persistent overlay in the top-right corner of the screen.
 *
 * <p>Usage:
 * <pre>{@code
 *   ZombieHud hud = new ZombieHud(playerRef);
 *   player.getHudManager().setCustomHud(playerRef, hud);
 *   hud.updateDisplay(1, 5, 9, 500);
 * }</pre>
 */
public class ZombieHud extends CustomUIHud {

    /** Unique key for this HUD in HudManager. */
    public static final String HUD_KEY = "hytalezombie_main";

    /** Path to the .ui markup file, relative to Common/UI/Custom/ */
    private static final String UI_DOCUMENT = "Hud/ZombieHud.ui";

    // Cached values to avoid redundant updates
    private int lastRound = -1;
    private int lastActiveZombies = -1;
    private int lastTotalZombies = -1;
    private int lastPoints = -1;
    private String lastZone = "";

    /**
     * @param playerRef the PlayerRef for the player this HUD belongs to
     */
    public ZombieHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, HUD_KEY);
    }

    /**
     * Called when the HUD is first shown or rebuilt.
     * Appends the .ui document and sets initial placeholder values.
     */
    @Override
    public void build(@Nonnull UICommandBuilder cmd) {
        cmd.append(UI_DOCUMENT);
        // Reset cache so first updateDisplay always sends values
        lastRound = -1;
        lastActiveZombies = -1;
        lastTotalZombies = -1;
        lastPoints = -1;
        lastZone = "";
    }

    /**
     * Updates the HUD display with current game state.
     * Only sends commands for values that actually changed.
     *
     * @param round          current round number
     * @param activeZombies  number of alive zombies
     * @param totalZombies   total zombies to spawn this round
     * @param points         player's current points
     * @param zoneName       the zone the player is currently in
     */
    public void updateDisplay(int round, int activeZombies, int totalZombies, int points,
                              @Nonnull String zoneName) {
        UICommandBuilder cmd = new UICommandBuilder();
        boolean changed = false;

        if (round != lastRound) {
            cmd.set("#RoundLabel.Text", "Round " + round);
            lastRound = round;
            changed = true;
        }

        if (activeZombies != lastActiveZombies || totalZombies != lastTotalZombies) {
            cmd.set("#ZombieLabel.Text", "Zombies: " + activeZombies + " / " + totalZombies);
            lastActiveZombies = activeZombies;
            lastTotalZombies = totalZombies;
            changed = true;
        }

        if (points != lastPoints) {
            cmd.set("#PointsLabel.Text", "Points: " + points);
            lastPoints = points;
            changed = true;
        }

        if (!zoneName.equals(lastZone)) {
            cmd.set("#ZoneLabel.Text", "Zone: " + zoneName);
            lastZone = zoneName;
            changed = true;
        }

        if (changed) {
            // false = incremental update (don't clear, only apply set() calls)
            this.update(false, cmd);
        }
    }
}
