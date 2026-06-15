package dev.hytalezombie.manager;

import dev.hytalezombie.config.HytaleZombieConfig;
import dev.hytalezombie.model.*;
import dev.hytalezombie.spawn.SpawnManager;
import dev.hytalezombie.spawn.SpawnNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GameSession")
class GameSessionTest {

    @Mock
    private HytaleZombieConfig config;

    @Mock
    private RoundManager roundManager;

    @Mock
    private PlayerDataManager playerDataManager;

    @Mock
    private BarrierManager barrierManager;

    @Mock
    private SpawnManager spawnManager;

    private GameSession gameSession;

    @BeforeEach
    void setUp() {
        // Default config values
        when(config.getZombieBaseHealth()).thenReturn(100.0f);
        when(config.getHealthScalingPerRound()).thenReturn(1.15f);
        when(config.getZombieBaseSpeed()).thenReturn(1.0f);
        when(config.getSpeedScalingPerRound()).thenReturn(1.05f);
        when(config.getZombieSpawnBaseCount()).thenReturn(5);
        when(config.getZombiesPerPlayer()).thenReturn(2);
        when(config.getPointsPerKill()).thenReturn(100);
        when(config.getStartingPoints()).thenReturn(500);
        when(config.getSpawnDelayTicks()).thenReturn(40);

        // Default round manager behavior
        when(roundManager.getCurrentRound()).thenReturn(1);
        when(roundManager.getScaledZombieHealth()).thenReturn(100.0f);
        when(roundManager.getScaledZombieSpeed()).thenReturn(1.0f);
        when(roundManager.getSpawnCount(anyInt())).thenReturn(10);

        gameSession = new GameSession(
                () -> config,
                roundManager,
                playerDataManager,
                barrierManager,
                spawnManager
        );
    }

    @Nested
    @DisplayName("Match lifecycle")
    class MatchLifecycle {

        @Test
        @DisplayName("should start match")
        void startMatch() {
            gameSession.startMatch();

            assertTrue(gameSession.isSessionActive());
            verify(roundManager).startMatch();
            verify(playerDataManager).resetAll();
        }

        @Test
        @DisplayName("should not start match if already active")
        void startMatch_alreadyActive() {
            gameSession.startMatch();
            gameSession.startMatch(); // Attempt second start

            verify(roundManager, times(1)).startMatch();
        }

        @Test
        @DisplayName("should end match")
        void endMatch() {
            gameSession.startMatch();
            gameSession.endMatch();

            assertFalse(gameSession.isSessionActive());
            verify(roundManager).endMatch();
        }

        @Test
        @DisplayName("should handle ending match that hasn't started")
        void endMatch_notStarted() {
            gameSession.endMatch(); // Should not throw
            assertFalse(gameSession.isSessionActive());
            verify(roundManager, never()).endMatch();
        }
    }

    @Nested
    @DisplayName("Zombie spawning")
    class ZombieSpawning {

        @BeforeEach
        void setUp() {
            when(playerDataManager.getPlayerCount()).thenReturn(1);
            when(spawnManager.getRandomSpawnNode()).thenReturn(
                    Optional.of(new SpawnNode("room_1", new Vector3f(10, 0, 10), 2.0f))
            );
            when(spawnManager.getRandomizedPosition(any())).thenReturn(new Vector3f(10, 0, 10));

            gameSession.startMatch();
        }

        @Test
        @DisplayName("should prepare round spawns")
        void prepareRoundSpawns() {
            gameSession.prepareRoundSpawns();

            assertEquals(10, gameSession.getTotalZombiesForRound());
            assertEquals(10, gameSession.getRemainingZombiesToSpawn());
            assertEquals(0, gameSession.getZombiesSpawnedThisRound());
        }

        @Test
        @DisplayName("should not prepare spawns with zero players")
        void prepareRoundSpawns_noPlayers() {
            when(playerDataManager.getPlayerCount()).thenReturn(0);

            gameSession.prepareRoundSpawns();

            assertEquals(0, gameSession.getTotalZombiesForRound());
        }

        @Test
        @DisplayName("should spawn zombies over ticks")
        void spawnZombiesOverTime() {
            gameSession.prepareRoundSpawns();

            // Spawning should happen after spawn delay ticks
            // Config says 40 ticks delay
            for (int i = 0; i < 41; i++) {
                gameSession.tick();
            }

            // After 41 ticks, one zombie should have spawned
            assertEquals(1, gameSession.getZombiesSpawnedThisRound());
            assertEquals(1, gameSession.getActiveZombieCount());
        }

        @Test
        @DisplayName("should spawn multiple zombies over time")
        void spawnMultipleZombies() {
            when(config.getSpawnDelayTicks()).thenReturn(0); // Instant spawn for testing
            gameSession.prepareRoundSpawns();

            // Tick enough to spawn several zombies
            for (int i = 0; i < 5; i++) {
                gameSession.tick();
            }

            // With delay 0, each tick spawns one zombie
            assertEquals(5, gameSession.getZombiesSpawnedThisRound());
            assertEquals(5, gameSession.getActiveZombieCount());
        }

        @Test
        @DisplayName("should not spawn when no spawn nodes available")
        void spawnNoNodes() {
            when(spawnManager.getRandomSpawnNode()).thenReturn(Optional.empty());
            when(config.getSpawnDelayTicks()).thenReturn(1);

            gameSession.prepareRoundSpawns();

            for (int i = 0; i < 10; i++) {
                gameSession.tick();
            }

            // No zombies should spawn without available nodes
            assertEquals(0, gameSession.getZombiesSpawnedThisRound());
        }

        @Test
        @DisplayName("should not spawn outside active session")
        void spawnNotActive() {
            gameSession.endMatch();
            gameSession.prepareRoundSpawns();

            gameSession.tick();

            assertEquals(0, gameSession.getZombiesSpawnedThisRound());
        }
    }

    @Nested
    @DisplayName("Zombie damage")
    class ZombieDamage {

        private PlayerData playerData;

        @BeforeEach
        void setUp() {
            playerData = new PlayerData("player-1");
            when(playerDataManager.getPlayerData("player-1")).thenReturn(playerData);
            when(playerDataManager.getPlayerCount()).thenReturn(1);
            when(config.getSpawnDelayTicks()).thenReturn(0); // Instant spawn
            when(config.getPointsPerKill()).thenReturn(100);
            when(spawnManager.getRandomSpawnNode()).thenReturn(
                    Optional.of(new SpawnNode("room_1", new Vector3f(10, 0, 10), 2.0f))
            );
            when(spawnManager.getRandomizedPosition(any())).thenReturn(new Vector3f(10, 0, 10));

            gameSession.startMatch();
            gameSession.prepareRoundSpawns();

            // Spawn one zombie (delay 0 = spawns on first tick)
            gameSession.tick();
        }

        @Test
        @DisplayName("should damage a zombie")
        void damageZombie() {
            Map<String, GameSession.ZombieInstance> zombies = gameSession.getActiveZombies();
            assertEquals(1, zombies.size());

            String zombieId = zombies.keySet().iterator().next();
            boolean killed = gameSession.damageZombie(zombieId, 50.0f, "player-1");

            assertFalse(killed);
            GameSession.ZombieInstance zombie = gameSession.getActiveZombies().get(zombieId);
            assertEquals(50.0f, zombie.getHealth(), 0.001f);
        }

        @Test
        @DisplayName("should kill a zombie when health reaches zero")
        void killZombie() {
            Map<String, GameSession.ZombieInstance> zombies = gameSession.getActiveZombies();
            String zombieId = zombies.keySet().iterator().next();

            boolean killed = gameSession.damageZombie(zombieId, 200.0f, "player-1");

            assertTrue(killed);
            assertFalse(gameSession.getActiveZombies().containsKey(zombieId));
            assertEquals(1, gameSession.getZombiesKilledThisRound());
        }

        @Test
        @DisplayName("should award points for kill")
        void awardKillPoints() {
            Map<String, GameSession.ZombieInstance> zombies = gameSession.getActiveZombies();
            String zombieId = zombies.keySet().iterator().next();

            gameSession.damageZombie(zombieId, 200.0f, "player-1");

            assertEquals(100, playerData.getPoints());
            assertEquals(1, playerData.getKills());
        }

        @Test
        @DisplayName("should award points for hits (10 per hit)")
        void awardHitPoints() {
            Map<String, GameSession.ZombieInstance> zombies = gameSession.getActiveZombies();
            String zombieId = zombies.keySet().iterator().next();

            gameSession.damageZombie(zombieId, 25.0f, "player-1");

            // 10 points per hit
            assertEquals(10, playerData.getPoints());
            assertEquals(0, playerData.getKills());
        }

        @Test
        @DisplayName("should handle hitting non-existent zombie")
        void damageNonExistentZombie() {
            boolean killed = gameSession.damageZombie("fake_zombie", 100.0f, "player-1");
            assertFalse(killed);
        }
    }

    @Nested
    @DisplayName("Power-up system")
    class PowerUpSystem {

        @BeforeEach
        void setUp() {
            gameSession.startMatch();
        }

        @Test
        @DisplayName("should activate a timed power-up")
        void activateTimedPowerUp() {
            gameSession.activatePowerUp(PowerUp.PowerUpType.DOUBLE_POINTS);

            assertTrue(gameSession.isPowerUpActive(PowerUp.PowerUpType.DOUBLE_POINTS));
            assertTrue(gameSession.getPowerUpRemainingTicks(PowerUp.PowerUpType.DOUBLE_POINTS) > 0);
        }

        @Test
        @DisplayName("should tick down power-up duration")
        void tickPowerUp() {
            gameSession.activatePowerUp(PowerUp.PowerUpType.DOUBLE_POINTS);

            long initialTicks = gameSession.getPowerUpRemainingTicks(PowerUp.PowerUpType.DOUBLE_POINTS);

            // Tick 10 times
            for (int i = 0; i < 10; i++) {
                gameSession.tick();
            }

            long remainingTicks = gameSession.getPowerUpRemainingTicks(PowerUp.PowerUpType.DOUBLE_POINTS);
            assertEquals(initialTicks - 10, remainingTicks);
        }

        @Test
        @DisplayName("should expire a timed power-up")
        void expirePowerUp() {
            // Insta-Kill has 30 second duration = 600 ticks at 20 ticks/sec
            gameSession.activatePowerUp(PowerUp.PowerUpType.INSTAKILL);
            assertTrue(gameSession.isPowerUpActive(PowerUp.PowerUpType.INSTAKILL));

            // Tick enough to expire (600+ ticks)
            for (int i = 0; i < 601; i++) {
                gameSession.tick();
            }

            assertFalse(gameSession.isPowerUpActive(PowerUp.PowerUpType.INSTAKILL));
        }

        @Test
        @DisplayName("should activate instant effect power-ups")
        void activateInstantPowerUp() {
            // Just verify no exception is thrown for instant power-ups
            gameSession.activatePowerUp(PowerUp.PowerUpType.NUKE);
            gameSession.activatePowerUp(PowerUp.PowerUpType.CARPENTER);
            gameSession.activatePowerUp(PowerUp.PowerUpType.MAX_AMMO);
            gameSession.activatePowerUp(PowerUp.PowerUpType.BONUS_POINTS);
            // All should complete without error
            assertTrue(true);
        }
    }

    @Nested
    @DisplayName("Economy / Purchases")
    class Economy {

        private PlayerData playerData;

        @BeforeEach
        void setUp() {
            playerData = new PlayerData("player-1");
            playerData.addPoints(1000);
            when(playerDataManager.getPlayerData("player-1")).thenReturn(playerData);
            gameSession.startMatch();
        }

        @Test
        @DisplayName("should purchase a weapon with sufficient points")
        void purchaseWeapon() {
            Weapon weapon = new Weapon("test_gun", "Test Gun", Weapon.WeaponType.PISTOL,
                    Weapon.Rarity.COMMON, 500, 25.0f, 100, 8, 4.0f, 1.5f);

            boolean purchased = gameSession.purchaseWeapon("player-1", weapon);

            assertTrue(purchased);
            assertEquals(500, playerData.getPoints());
            assertTrue(gameSession.getPlayerWeapons("player-1").contains(weapon));
        }

        @Test
        @DisplayName("should fail weapon purchase with insufficient points")
        void purchaseWeaponInsufficientFunds() {
            Weapon weapon = new Weapon("test_gun", "Test Gun", Weapon.WeaponType.PISTOL,
                    Weapon.Rarity.COMMON, 2000, 25.0f, 100, 8, 4.0f, 1.5f);

            boolean purchased = gameSession.purchaseWeapon("player-1", weapon);

            assertFalse(purchased);
            assertEquals(1000, playerData.getPoints());
        }

        @Test
        @DisplayName("should fail purchase for unknown player")
        void purchaseWeaponUnknownPlayer() {
            when(playerDataManager.getPlayerData("unknown")).thenReturn(null);

            Weapon weapon = new Weapon("test_gun", "Test Gun", Weapon.WeaponType.PISTOL,
                    Weapon.Rarity.COMMON, 500, 25.0f, 100, 8, 4.0f, 1.5f);

            assertFalse(gameSession.purchaseWeapon("unknown", weapon));
        }

        @Test
        @DisplayName("should purchase a perk with sufficient points")
        void purchasePerk() {
            playerData.addPoints(2000); // Now 3000, enough for Juggernog (2500)

            boolean purchased = gameSession.purchasePerk("player-1", Perk.PerkType.JUGGERNOG);

            assertTrue(purchased);
            assertTrue(gameSession.hasPerk("player-1", Perk.PerkType.JUGGERNOG));
            assertEquals(500, playerData.getPoints());
        }

        @Test
        @DisplayName("should fail perk purchase with insufficient points")
        void purchasePerkInsufficientFunds() {
            // Juggernog costs 2500, player has 1000
            boolean purchased = gameSession.purchasePerk("player-1", Perk.PerkType.JUGGERNOG);

            assertFalse(purchased);
            assertFalse(gameSession.hasPerk("player-1", Perk.PerkType.JUGGERNOG));
        }

        @Test
        @DisplayName("should purchase a cheap perk")
        void purchaseCheapPerk() {
            // Quick Revive costs 1500, player has 1000 - still too much
            // Let's give more points
            playerData.addPoints(1000); // now 2000

            boolean purchased = gameSession.purchasePerk("player-1", Perk.PerkType.QUICK_REVIVE);

            assertTrue(purchased);
            assertTrue(gameSession.hasPerk("player-1", Perk.PerkType.QUICK_REVIVE));
            assertEquals(500, playerData.getPoints());
        }

        @Test
        @DisplayName("should purchase a door/zone")
        void purchaseDoor() {
            MapZone zone = new MapZone("room_2", "Room 2", 500);

            boolean purchased = gameSession.purchaseDoor("player-1", zone);

            assertTrue(purchased);
            assertTrue(zone.isUnlocked());
            assertEquals(500, playerData.getPoints());
        }

        @Test
        @DisplayName("should not charge for already unlocked zone")
        void purchaseDoorAlreadyUnlocked() {
            MapZone zone = new MapZone("room_2", "Room 2", 500);
            zone.setUnlocked(true);

            boolean purchased = gameSession.purchaseDoor("player-1", zone);

            assertTrue(purchased);
            assertEquals(1000, playerData.getPoints()); // Not charged
        }

        @Test
        @DisplayName("should fail door purchase with insufficient points")
        void purchaseDoorInsufficientFunds() {
            MapZone zone = new MapZone("room_3", "Room 3", 2000);

            boolean purchased = gameSession.purchaseDoor("player-1", zone);

            assertFalse(purchased);
            assertFalse(zone.isUnlocked());
        }
    }

    @Nested
    @DisplayName("Nuke power-up")
    class NukeEffect {

        @BeforeEach
        void setUp() {
            when(playerDataManager.getPlayerData(anyString())).thenReturn(new PlayerData("test"));
            when(playerDataManager.getPlayerCount()).thenReturn(1);
            when(config.getSpawnDelayTicks()).thenReturn(0);
            when(config.getPointsPerKill()).thenReturn(100);
            when(spawnManager.getRandomSpawnNode()).thenReturn(
                    Optional.of(new SpawnNode("room_1", new Vector3f(10, 0, 10), 2.0f))
            );
            when(spawnManager.getRandomizedPosition(any())).thenReturn(new Vector3f(10, 0, 10));

            gameSession.startMatch();
            gameSession.prepareRoundSpawns();
        }

        @Test
        @DisplayName("should kill all active zombies with nuke")
        void nukeKillsAll() {
            // Spawn a few zombies (delay 0 = spawns on each tick)
            for (int i = 0; i < 5; i++) {
                gameSession.tick();
            }

            assertEquals(5, gameSession.getActiveZombieCount());

            gameSession.activatePowerUp(PowerUp.PowerUpType.NUKE);

            assertEquals(0, gameSession.getActiveZombieCount());
            assertEquals(5, gameSession.getZombiesKilledThisRound());
        }
    }

    @Nested
    @DisplayName("Double points power-up")
    class DoublePointsEffect {

        private PlayerData playerData;

        @BeforeEach
        void setUp() {
            playerData = new PlayerData("player-1");
            when(playerDataManager.getPlayerData("player-1")).thenReturn(playerData);
            when(playerDataManager.getPlayerCount()).thenReturn(1);
            when(config.getSpawnDelayTicks()).thenReturn(0);
            when(config.getPointsPerKill()).thenReturn(100);
            when(spawnManager.getRandomSpawnNode()).thenReturn(
                    Optional.of(new SpawnNode("room_1", new Vector3f(10, 0, 10), 2.0f))
            );
            when(spawnManager.getRandomizedPosition(any())).thenReturn(new Vector3f(10, 0, 10));

            gameSession.startMatch();
            gameSession.prepareRoundSpawns();
            gameSession.tick(); // Spawn one zombie
        }

        @Test
        @DisplayName("should double points for kills")
        void doubleKillPoints() {
            gameSession.activatePowerUp(PowerUp.PowerUpType.DOUBLE_POINTS);

            Map<String, GameSession.ZombieInstance> zombies = gameSession.getActiveZombies();
            String zombieId = zombies.keySet().iterator().next();

            gameSession.damageZombie(zombieId, 200.0f, "player-1");

            // Double Points: kill gives 200 instead of 100
            assertEquals(200, playerData.getPoints());
        }

        @Test
        @DisplayName("should double points for hits")
        void doubleHitPoints() {
            gameSession.activatePowerUp(PowerUp.PowerUpType.DOUBLE_POINTS);

            Map<String, GameSession.ZombieInstance> zombies = gameSession.getActiveZombies();
            String zombieId = zombies.keySet().iterator().next();

            gameSession.damageZombie(zombieId, 25.0f, "player-1");

            // Double Points: hit gives 20 instead of 10
            assertEquals(20, playerData.getPoints());
        }
    }

    @Nested
    @DisplayName("Insta-Kill power-up")
    class InstaKillEffect {

        @BeforeEach
        void setUp() {
            when(playerDataManager.getPlayerData(anyString())).thenReturn(new PlayerData("test"));
            when(playerDataManager.getPlayerCount()).thenReturn(1);
            when(config.getSpawnDelayTicks()).thenReturn(0);
            when(config.getPointsPerKill()).thenReturn(100);
            when(spawnManager.getRandomSpawnNode()).thenReturn(
                    Optional.of(new SpawnNode("room_1", new Vector3f(10, 0, 10), 2.0f))
            );
            when(spawnManager.getRandomizedPosition(any())).thenReturn(new Vector3f(10, 0, 10));

            gameSession.startMatch();
            gameSession.prepareRoundSpawns();
            gameSession.tick();
        }

        @Test
        @DisplayName("should kill zombie in one hit with insta-kill")
        void instaKillOneHit() {
            gameSession.activatePowerUp(PowerUp.PowerUpType.INSTAKILL);

            Map<String, GameSession.ZombieInstance> zombies = gameSession.getActiveZombies();
            String zombieId = zombies.keySet().iterator().next();

            // Even a tiny amount of damage should kill with insta-kill
            boolean killed = gameSession.damageZombie(zombieId, 1.0f, "test");

            assertTrue(killed);
        }
    }

    @Nested
    @DisplayName("Player data integration")
    class PlayerDataIntegration {

        @Test
        @DisplayName("should have no weapons initially for a player")
        void playerWeaponsEmpty() {
            assertTrue(gameSession.getPlayerWeapons("player-1").isEmpty());
        }

        @Test
        @DisplayName("should have no perks initially for a player")
        void playerPerksEmpty() {
            assertTrue(gameSession.getPlayerPerks("player-1").isEmpty());
        }

        @Test
        @DisplayName("should check perk status")
        void hasPerk() {
            assertFalse(gameSession.hasPerk("player-1", Perk.PerkType.JUGGERNOG));
        }

        @Test
        @DisplayName("should not have perks for unknown players")
        void hasPerkUnknownPlayer() {
            assertFalse(gameSession.hasPerk("unknown", Perk.PerkType.JUGGERNOG));
        }
    }
}
