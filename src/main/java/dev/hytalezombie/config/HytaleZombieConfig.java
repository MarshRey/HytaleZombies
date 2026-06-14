package dev.hytalezombie.config;

/**
 * Configuration for the HytaleZombie mod.
 * Stores game balance settings that can be tuned by the server admin.
 * 
 * In a real Hytale environment, this would use Hytale's Config/Codec system
 * to auto-load from JSON. Here, defaults are hardcoded and can be overridden
 * via setters for testing.
 */
public class HytaleZombieConfig {

    private int startingPoints = 500;
    private float zombieBaseHealth = 100.0f;
    private float zombieBaseSpeed = 1.0f;
    private int pointsPerKill = 100;
    private float healthScalingPerRound = 1.15f;
    private float speedScalingPerRound = 1.05f;
    private int zombieSpawnBaseCount = 5;
    private int zombiesPerPlayer = 2;
    private int spawnDelayTicks = 40; // 2 seconds at 20 ticks/sec

    public HytaleZombieConfig() {
    }

    public int getStartingPoints() { return startingPoints; }
    public float getZombieBaseHealth() { return zombieBaseHealth; }
    public float getZombieBaseSpeed() { return zombieBaseSpeed; }
    public int getPointsPerKill() { return pointsPerKill; }
    public float getHealthScalingPerRound() { return healthScalingPerRound; }
    public float getSpeedScalingPerRound() { return speedScalingPerRound; }
    public int getZombieSpawnBaseCount() { return zombieSpawnBaseCount; }
    public int getZombiesPerPlayer() { return zombiesPerPlayer; }
    public int getSpawnDelayTicks() { return spawnDelayTicks; }

    // Setters for testing
    public void setStartingPoints(int startingPoints) { this.startingPoints = startingPoints; }
    public void setZombieBaseHealth(float zombieBaseHealth) { this.zombieBaseHealth = zombieBaseHealth; }
    public void setZombieBaseSpeed(float zombieBaseSpeed) { this.zombieBaseSpeed = zombieBaseSpeed; }
    public void setPointsPerKill(int pointsPerKill) { this.pointsPerKill = pointsPerKill; }
    public void setHealthScalingPerRound(float healthScalingPerRound) { this.healthScalingPerRound = healthScalingPerRound; }
    public void setSpeedScalingPerRound(float speedScalingPerRound) { this.speedScalingPerRound = speedScalingPerRound; }
    public void setZombieSpawnBaseCount(int zombieSpawnBaseCount) { this.zombieSpawnBaseCount = zombieSpawnBaseCount; }
    public void setZombiesPerPlayer(int zombiesPerPlayer) { this.zombiesPerPlayer = zombiesPerPlayer; }
    public void setSpawnDelayTicks(int spawnDelayTicks) { this.spawnDelayTicks = spawnDelayTicks; }
}
