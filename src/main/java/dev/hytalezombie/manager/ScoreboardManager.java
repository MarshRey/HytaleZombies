package dev.hytalezombie.manager;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages a persistent text-based scoreboard display for all players.
 * Sends formatted scoreboard updates to every tracked player every tick interval.
 * Shows round number, zombie count, and player points.
 */
public class ScoreboardManager {

    private static final Logger LOGGER = Logger.getLogger(ScoreboardManager.class.getName());

    /** How many ticks between scoreboard updates (20 ticks = 1 second). */
    private static final int UPDATE_INTERVAL = 20;

    private final GameSession gameSession;
    private final PlayerDataManager playerDataManager;

    /** Player UUID → PlayerRef for sending messages. */
    private final Map<String, PlayerRef> playerRefs;

    /** Ticks since last scoreboard broadcast. */
    private int updateCounter;

    private int lastRound;
    private int lastZombieCount;
    private int lastTotalZombies;
    private int lastPoints;
    private boolean dirty;

    /**
     * @param gameSession       the game session (for round/zombie state)
     * @param playerDataManager the player data manager (for points)
     */
    public ScoreboardManager(@Nonnull GameSession gameSession, @Nonnull PlayerDataManager playerDataManager) {
        this.gameSession = gameSession;
        this.playerDataManager = playerDataManager;
        this.playerRefs = new ConcurrentHashMap<>();
        this.updateCounter = 0;
        this.lastRound = -1;
        this.lastZombieCount = -1;
        this.lastTotalZombies = -1;
        this.lastPoints = -1;
        this.dirty = true;
    }

    /**
     * Registers or updates a player's chat message target.
     *
     * @param playerId the player's UUID string
     * @param ref      the PlayerRef for sending messages
     */
    public void registerPlayer(@Nonnull String playerId, @Nonnull PlayerRef ref) {
        playerRefs.put(playerId, ref);
        dirty = true;
    }

    /**
     * Removes a disconnected player.
     *
     * @param playerId the player's UUID string
     */
    public void removePlayer(@Nonnull String playerId) {
        playerRefs.remove(playerId);
    }

    /**
     * Returns the number of tracked players with valid PlayerRefs.
     */
    public int getTrackedPlayerCount() {
        return playerRefs.size();
    }

    /**
     * Called every game tick. Sends scoreboard updates at the configured interval.
     */
    public void tick() {
        updateCounter++;
        if (updateCounter < UPDATE_INTERVAL) return;
        updateCounter = 0;

        broadcastScoreboard();
    }

    /**
     * Forces an immediate scoreboard broadcast on the next tick.
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * Broadcasts the current game state to all tracked players via chat messages.
     * Only sends if the state has changed since last broadcast.
     */
    private void broadcastScoreboard() {
        if (playerRefs.isEmpty() || !gameSession.isSessionActive()) return;

        int round = gameSession.getRoundManager().getCurrentRound();
        int zombieCount = gameSession.getActiveZombieCount();
        int totalZombies = gameSession.getTotalZombiesForRound();

        // Check if state actually changed
        if (!dirty && round == lastRound && zombieCount == lastZombieCount && totalZombies == lastTotalZombies) {
            return;
        }
        dirty = false;
        lastRound = round;
        lastZombieCount = zombieCount;
        lastTotalZombies = totalZombies;

        // Build the scoreboard message parts (plain text, no Hytale formatting)
        StringBuilder sb = new StringBuilder();
        sb.append("[HZ] Round ").append(round)
          .append(" | Zombies: ").append(zombieCount).append("/").append(totalZombies);

        String baseStr = sb.toString();

        for (Map.Entry<String, PlayerRef> entry : playerRefs.entrySet()) {
            String playerId = entry.getKey();
            PlayerRef ref = entry.getValue();
            if (ref == null) continue;

            try {
                // Get this player's points
                int points = 0;
                dev.hytalezombie.model.PlayerData data = playerDataManager.getPlayerData(playerId);
                if (data != null) {
                    points = data.getPoints();
                }

                // Build per-player scoreboard line
                String scoreboard = baseStr + " | Points: " + points;
                ref.sendMessage(Message.raw(scoreboard));
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to send scoreboard to player {0}: {1}",
                        new Object[]{playerId, e.getMessage()});
            }
        }
    }
}
