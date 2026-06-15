# HytaleZombie - Project Context

## Overview

HytaleZombie is a round-based wave survival game mode for Hytale, inspired by Call of Duty Zombies. Players survive waves of increasingly difficult zombies, earn points, purchase weapons/upgrades, repair barriers, and unlock new areas of the map.

The **core game logic is fully implemented at the logical/game-engine level** with 138 passing unit tests. The spawning system, round progression, economy, power-ups, barriers, and map zones are all coded and tested. The only remaining step to see a basic round of zombies in-game is hooking it up to the Hytale server SDK to spawn real entities.

---

## 🎯 The Goal: A Simple Round of Zombies (Already Achieved in Code)

### What counts as a "basic round"?

1. **Three spawn points** exist in the map (all in one zone, e.g. `"spawn_room"`)
2. When `/hytalezombie start` is run, round 1 begins
3. **Zombies spawn one at a time** at a configurable interval (default: every 2 seconds / 40 ticks)
4. Each zombie spawns at **one of the three spawn points** (chosen randomly), at a **randomized offset** within that node's radius
5. Zombies **keep spawning at that interval** until the total number for the round is reached
6. When all zombies for the round are spawned **AND** eliminated, the round **auto-advances**
7. Round 2 begins with **more zombies** that are **stronger and faster**, repeating the same pattern

**The code already does ALL of the above.** It just needs the Hytale entity system to make it real in-game.

---

## 🔁 How the Spawning Cycle Works (End-to-End Walkthrough)

Here is the complete flow from server start to round completion, with every relevant file cited:

### Step 1: Plugin Initialization

**`HytaleZombiePlugin.java`** — lifecycle methods:

```
preLoad():  Creates RoundManager, PlayerDataManager, BarrierManager, SpawnManager, GameSession
            (runs when plugin is loaded, plugin state is NOT enabled yet)

start():    Registers /hytalezombie command via CommandRegistry
            Starts a 20-tick-per-second game loop (ScheduledExecutorService, 50ms intervals)
            Registers PlayerReadyEvent handler to greet players + create PlayerData
            (runs when plugin is ENABLED — commands/events cannot be registered earlier)
```

### Step 2: Admin Starts the Game

**`HytaleZombieCommand.java`** — `execute("start")`:

```
plugin.setupDefaultMap()        → registers 3+ spawn points
session.startMatch()            → roundManager.startMatch() → round=1, match active
session.prepareRoundSpawns()    → calculates total zombies for round 1
```

The `setupDefaultMap()` call mimics what you'd do manually with `/hz setspawn` commands. For custom maps, you can use `/hz map` to load defaults or `/hz setspawn <zone> <x> <y> <z>` to place spawns at specific world coordinates.

### Step 3: The Game Loop Ticks (20 times per second)

**`GameSession.java`** — `tick()` (called every 50ms):

```
if !sessionActive → return
tickCounter++
tickPowerUps()              → countdown active power-up timers
handleSpawning()            → spawn a zombie if interval has elapsed (see Step 4)
checkRoundComplete()        → advance round if all done (see Step 5)
```

### Step 4: Zombie Spawning

**`GameSession.java`** — `handleSpawning()`:

```
if zombiesSpawnedThisRound >= totalZombiesToSpawn → return (all done)

if spawnTicker < spawnDelayTicks (default 40):
    spawnTicker++             → waiting for the interval
    return

spawnTicker = 0               → reset the interval counter

// Pick one of the 3 spawn points at random
spawnManager.getRandomSpawnNode()
    → returns a SpawnNode (picks randomly from Node A, Node B, Node C)

// Calculate a randomized position within that node's spawn radius
position = spawnManager.getRandomizedPosition(node)
    → random X/Z offset within node.radius, maintains Y level

// Create the zombie with stats scaled to current round
ZombieInstance zombie = createZombie(position)
    → health = roundManager.getScaledZombieHealth()   (100.0 * 1.15^(round-1))
    → speed  = roundManager.getScaledZombieSpeed()    (1.0 * 1.05^(round-1))

activeZombies.put(zombie.getId(), zombie)
roundManager.incrementActiveZombies()
zombiesSpawnedThisRound++
```

### Step 5: Round Completion

**`GameSession.java`** — `checkRoundComplete()`:

```
checkRoundComplete():
    if all zombies spawned AND all zombies eliminated (activeZombies.isEmpty()):
        → auto-advance happens in RoundManager.decrementActiveZombies()
        → when activeZombieCount hits 0 → advanceRound() → round increments
```

---

## 📍 The Three Spawn Points (Defined in Code)

**`HytaleZombiePlugin.java`** — `setupDefaultMap()`:

```java
spawnManager.markZoneOccupied("spawn_room");

// Node A
spawnManager.registerSpawnNode(new SpawnNode(
    "spawn_room", new Vector3f(0, 0, 0), 5.0f
));
// Node B
spawnManager.registerSpawnNode(new SpawnNode(
    "spawn_room", new Vector3f(10, 0, 10), 5.0f
));
// Node C
spawnManager.registerSpawnNode(new SpawnNode(
    "spawn_room", new Vector3f(-10, 0, -10), 5.0f
));
```

**How each spawn point works:**
- All three belong to zone `"spawn_room"` (marked as occupied so zombies spawn from here)
- Each has a **position** (the XYZ coordinate in your Hytale world)
- Each has a **spawnRadius** of 5.0 — a zombie spawns at a **random offset** (up to 5 blocks away in X/Z) from the node's center position
- Selection is random: `SpawnManager.getRandomSpawnNode()` picks uniformly from all active nodes

**To change these positions**, edit `setupDefaultMap()` in `HytaleZombiePlugin.java` and set the Vector3f values to your desired world coordinates.

---

## ⏱ Spawn Interval Controls

The `spawnDelayTicks` in `HytaleZombieConfig.java` controls the time between each zombie spawn:

| `spawnDelayTicks` | Real Time | Effect |
|---|---|---|
| **40** (default) | **2 seconds** | Standard pace (20 ticks/sec × 40 = 2000ms) |
| **20** | **1 second** | Fast pace |
| 60 | 3 seconds | Slow pace (harder because zombies build up) |
| 10 | 0.5 seconds | Very fast, intense |
| 1 | 0.05 seconds | Nearly instant — all zombies flood out immediately |

The spawn ticker is an internal counter that increments by 1 each `tick()` call. When it reaches `spawnDelayTicks`, one zombie is spawned and the ticker resets to 0.

---

## 📊 Zombie Count & Scaling Formulas

### Zombies Per Round

Calculated in **`RoundManager.getSpawnCount(playerCount)`**:

```
totalZombies = ZombieSpawnBaseCount(5) + (ZombiesPerPlayer(2) × playerCount) + (currentRound × 2)
```

**Examples (1 player):**
| Round | Calculation | Zombies |
|-------|-------------|---------|
| 1 | 5 + (2×1) + (1×2) | **9** |
| 2 | 5 + (2×1) + (2×2) | **11** |
| 5 | 5 + (2×1) + (5×2) | **17** |
| 10 | 5 + (2×1) + (10×2) | **27** |

**Examples (4 players):**
| Round | Calculation | Zombies |
|-------|-------------|---------|
| 1 | 5 + (2×4) + (1×2) | **15** |
| 5 | 5 + (2×4) + (5×2) | **23** |
| 10 | 5 + (2×4) + (10×2) | **33** |

### Zombie Health Scaling

Calculated in **`RoundManager.getScaledZombieHealth()`**:

```
health = baseHealth(100) × (healthScalingPerRound(1.15))^(round-1)
```

| Round | HP | Notes |
|-------|----|-------|
| 1 | 100 | Base |
| 2 | 115 | 15% increase |
| 3 | ~132 | |
| 5 | ~175 | Nearly double |
| 10 | ~352 | 3.5× base |
| 20 | ~1,424 | Getting tough |

### Zombie Speed Scaling

Calculated in **`RoundManager.getScaledZombieSpeed()`**:

```
speed = baseSpeed(1.0) × (speedScalingPerRound(1.05))^(round-1)
```

| Round | Speed | Effect |
|-------|-------|--------|
| 1 | 1.0× | Walking pace |
| 5 | ~1.22× | Noticeably faster |
| 10 | ~1.55× | Sprinting |
| 20 | ~2.53× | Extremely fast |

### Points System

| Action | Points (normal) | Points (Double Points) | Where |
|--------|-----------------|------------------------|-------|
| Hit a zombie (not kill) | **10** | **20** | `GameSession.damageZombie()` |
| Kill a zombie | **100** | **200** | `GameSession.damageZombie()` |

---

## 🧪 How to Test the Spawning Cycle (Without a Server)

All spawning logic can be tested through the existing unit tests. Run:

```bash
./gradlew test --tests "dev.hytalezombie.manager.GameSessionTest"
```

**Key tests in `GameSessionTest.java`:**

| Test Class | Test Method | What It Proves |
|---|---|---|
| `ZombieSpawning` | `prepareRoundSpawns()` | After `startMatch()`, `prepareRoundSpawns()` sets the correct zombie count |
| `ZombieSpawning` | `spawnZombiesOverTime()` | After exactly 41 ticks (delay 40 + 1), exactly 1 zombie is spawned |
| `ZombieSpawning` | `spawnMultipleZombies()` | With delay 0, each tick spawns one zombie |
| `ZombieSpawning` | `spawnNoNodes()` | If no spawn nodes registered, no zombies spawn (graceful failure) |
| `ZombieDamage` | `killZombie()` | Killing a zombie awards 100 points and increments kills |
| `ZombieDamage` | `awardHitPoints()` | Hitting (not killing) awards 10 points |
| `DoublePointsEffect` | `doubleKillPoints()` | Double Points doubles kill rewards to 200 |
| `InstaKillEffect` | `instaKillOneHit()` | Insta-Kill kills in one hit regardless of HP |

**`RoundManagerTest.java` — tests the round/scaling math:**

| Test Method | What It Proves |
|---|---|
| `getScaledZombieHealth_round1()` | Round 1 health = base (100) |
| `getScaledZombieHealth_lateRounds()` | Round 5 health = 100 × 1.15^4 |
| `getSpawnCount()` | Spawn count includes base + per-player + round bonus |
| `decrementActiveZombies_autoAdvance()` | When all zombies die, round auto-advances |

**`SpawnManagerTest.java` — tests the spawn node system:**

| Test Method | What It Proves |
|---|---|
| `registerSpawnNode()` | Nodes register per-zone |
| `markZoneOccupied()` | Only occupied zones' nodes are active |
| `getRandomSpawnNode()` | Random selection from active nodes |
| `getRandomizedPosition_withinRadius()` | Position offset stays within spawn radius |
| `getRandomizedPosition_sameY()` | Y coordinate is preserved |

---

## ✅ What's Already Working (The Logical Layer — Fully Tested)

| Feature | Status | Key File(s) |
|---------|--------|-------------|
| 3 spawn points in one zone | ✅ | `HytaleZombiePlugin.setupDefaultMap()` |
| Random selection between spawn points | ✅ | `SpawnManager.getRandomSpawnNode()` |
| Randomized offset within spawn radius | ✅ | `SpawnManager.getRandomizedPosition(node)` |
| Interval-based spawning (configurable delay) | ✅ | `GameSession.handleSpawning()` |
| Per-round zombie count calculation | ✅ | `RoundManager.getSpawnCount()` |
| Scaled zombie health per round | ✅ | `RoundManager.getScaledZombieHealth()` |
| Scaled zombie speed per round | ✅ | `RoundManager.getScaledZombieSpeed()` |
| Auto-advance round when all clear | ✅ | `RoundManager.decrementActiveZombies()` |
| 20-tick-per-second game loop | ✅ | `HytaleZombiePlugin.startGameLoop()` |
| Points awarded on hits (10) and kills (100) | ✅ | `GameSession.damageZombie()` |
| Double Points / Insta-Kill power-ups | ✅ | `GameSession.activatePowerUp()` |
| Nuke kills all active zombies | ✅ | `GameSession.nukeAllZombies()` |
| Weapon purchasing economy | ✅ | `GameSession.purchaseWeapon()` |
| Perk purchasing economy | ✅ | `GameSession.purchasePerk()` |
| Door/zone unlocking | ✅ | `GameSession.purchaseDoor()` |
| Barriers with repair mechanics | ✅ | `Barrier.java`, `BarrierManager.java` |
| Player data tracking (points, kills, downs) | ✅ | `PlayerDataManager.java` |
| Map zone connectivity system | ✅ | `ZoneManager.java`, `MapZone.java` |
| 12 perk types with costs | ✅ | `Perk.java` |
| 7 power-up types | ✅ | `PowerUp.java` |
| Weapon registry with wall + mystery box | ✅ | `WeaponRegistry.java` |
| 138 unit tests (all passing) | ✅ | `src/test/` |

---

## ⚠️ Important: Plugin Lifecycle Gotcha

Commands and events **cannot** be registered in `preLoad()`. The plugin state is `NONE` during `preLoad()`,
and the Hytale `CommandRegistry`/`EventRegistry` will throw:

> **"The plugin ... is not enabled!"**

Always register commands, events, and game loops in the **`start()`** method instead, which runs after
the plugin state transitions through `SETUP` → `START` → `ENABLED`.

```java
// ✅ CORRECT — move registrations here
@Override
protected void start() {
    getCommandRegistry().registerCommand(new MyCommand(this));
    getEventRegistry().registerGlobal(MyEvent.class, event -> { ... });
    startGameLoop();
}
```

## 🔌 What Needs Hytale SDK Integration

### The Single Blocking Issue: Entity Spawning

`GameSession.createZombie()` currently creates a **logical** `ZombieInstance` stored in a `HashMap<String, ZombieInstance>`. To see zombies in the actual game, this method needs to call the Hytale SDK to **spawn a real monster entity** at the calculated position.

**`GameSession.java`** — the `createZombie()` method (line ~233):

```java
private ZombieInstance createZombie(Vector3f position) {
    float health = roundManager.getScaledZombieHealth();
    float speed = roundManager.getScaledZombieSpeed();
    String zombieId = "zombie_" + tickCounter + "_" + UUID.randomUUID().toString().substring(0, 8);

    // TODO: Replace with Hytale entity spawning:
    // Entity zombieEntity = entityFactory.createZombie(position);
    // zombieEntity.setHealth(health);
    // zombieEntity.setSpeed(speed);
    // 
    // And register a damage listener:
    // eventRegistry.listen(zombieEntity, DamageEvent.class, event -> {
    //     gameSession.damageZombie(
    //         zombieEntity.getId(), event.getDamage(), event.getPlayerId()
    //     );
    // });

    return new ZombieInstance(zombieId, health, speed, position);
}
```

Once actual entities are spawned, the rest works automatically — round advancement, damage tracking, points, everything.

### Required Integration Touchpoints (All Already Wired)

| Integration Point | File | Status |
|---|---|---|
| Extend `JavaPlugin` | `HytaleZombiePlugin.java` | Already extends it |
| Register commands via Hytale's API | `HytaleZombieCommand.java` | Already extends `AbstractCommand` |
| Handle `PlayerReadyEvent` | `HytaleZombiePlugin.java` | Registered in `start()` (plugin must be ENABLED) |
| Game loop via Hytale's task system | `HytaleZombiePlugin.java` | Already uses `TaskRegistration` |
| Send messages to players with Hytale's `Message` API | `HytaleZombieCommand.java` | Already uses `Message.raw()` |

---

## 🚀 Quick Start Guide

### Run the server:

```bash
# Windows
.\gradlew.bat setupHytaleDev     # Download assets, decompile server sources
.\gradlew.bat runServer           # Launch local Hytale server with plugin
```

When the server starts, output will show a URL to authorize your server. Click it, log in with your Hytale account, then launch the game client and connect to `localhost`.

### See the spawning system work (tests):

```bash
cd ~/Desktop/Code/Personal/HytaleZombies
./gradlew test
```

### Key files to read:

1. **`src/main/java/dev/hytalezombie/manager/GameSession.java`** — The main orchestrator. Read `tick()`, `handleSpawning()`, `createZombie()`, `damageZombie()`, `checkRoundComplete()`
2. **`src/main/java/dev/hytalezombie/HytaleZombiePlugin.java`** — Entry point, game loop, `setupDefaultMap()` where you set spawn positions
3. **`src/main/java/dev/hytalezombie/manager/RoundManager.java`** — Round tracking, scaling math, spawn count formula
4. **`src/main/java/dev/hytalezombie/spawn/SpawnManager.java`** — Spawn node registry, random selection, position randomization
5. **`src/main/java/dev/hytalezombie/config/HytaleZombieConfig.java`** — All tunable values (spawn delay, health, speed, costs)

### To make it real in-game:

The **only** thing blocking a playable basic round is replacing the logical zombie creation in `GameSession.createZombie()` with actual Hytale entity spawning. Everything else — the timing, the counts, the scaling, the round advancement, the points — is fully built and tested.

---

## 🎮 Game Commands

### Match Controls
| Command | What It Does |
|---------|-------------|
| `/hytalezombie start` or `/hz start` | Starts a match, sets up default map with 3 spawn points, begins round 1 |
| `/hytalezombie stop` or `/hz stop` | Ends the current match |
| `/hytalezombie round [n]` or `/hz round [n]` | Shows or sets the current round number |
| `/hytalezombie info` or `/hz info` | Shows match status, round, active zombies, player count |

### Map Setup Commands
| Command | What It Does |
|---------|-------------|
| `/hz map` | Registers the default test map with spawn nodes |
| `/hz setspawn <zone> [radius]` | Adds a spawn point at (0,0,0) for a zone |
| `/hz setspawn <zone> <x> <y> <z> [r]` | Adds a spawn point at specific coordinates |
| `/hz delspawn <zone> [index]` | Removes spawn points from a zone |
| `/hz listspawns [zone]` | Lists all registered spawn points |
| `/hz clearspawns` | Removes all spawn points |
| `/hz markzone <zone>` | Marks a zone as occupied (zombies will spawn there) |
| `/hz unmarkzone <zone>` | Unmarks a zone |
| `/hz listzones` | Lists all zones with spawns |

---

## 🔧 Config Reference (`HytaleZombieConfig.java`)

| Property | Default | What It Controls |
|----------|---------|------------------|
| `startingPoints` | **500** | Points each player starts with |
| `zombieBaseHealth` | **100.0** | Round 1 zombie HP |
| `zombieBaseSpeed` | **1.0** | Round 1 zombie speed multiplier |
| `healthScalingPerRound` | **1.15** | Each round, HP multiplies by this (15% increase) |
| `speedScalingPerRound` | **1.05** | Each round, speed multiplies by this (5% increase) |
| `pointsPerKill` | **100** | Points awarded for killing a zombie |
| `zombieSpawnBaseCount` | **5** | Minimum number of zombies per round |
| `zombiesPerPlayer` | **2** | Extra zombies per additional player |
| `spawnDelayTicks` | **40** | Ticks between each zombie spawn (40 = 2 seconds) |

---

## 🗺 Project File Map

```
src/main/java/dev/hytalezombie/
├── HytaleZombiePlugin.java             # Entry point + setupDefaultMap() + game loop
├── commands/
│   └── HytaleZombieCommand.java        # /hytalezombie start|stop|round|info|map|setspawn|delspawn|listspawns|clearspawns|markzone|unmarkzone|listzones
├── config/
│   └── HytaleZombieConfig.java         # All tunable values
├── events/
│   └── PlayerConnectionListener.java   # Player join utility
├── manager/
│   ├── BarrierManager.java             # CRUD for window barriers
│   ├── GameManagerProvider.java        # Interface for accessing all managers
│   ├── GameSession.java                # MAIN GAME ORCHESTRATOR (tick, spawn, damage, economy)
│   ├── PlayerDataManager.java          # Per-player state management
│   ├── RoundManager.java               # Round tracking + scaling calculations
│   ├── WeaponRegistry.java             # All weapon definitions
│   └── ZoneManager.java                # Map zone connectivity + door unlocking
├── model/
│   ├── Barrier.java                    # Barrier state machine (INTACT→DAMAGED→BROKEN)
│   ├── MapZone.java                    # Named zone with door cost
│   ├── Perk.java                       # Perk-a-Cola definitions (12 perks)
│   ├── PlayerData.java                 # Points, kills, downs, alive
│   ├── PowerUp.java                    # Power-up types + timed duration
│   ├── Vector3f.java                   # Float 3D vector (entity positions)
│   ├── Vector3i.java                   # Int 3D vector (block positions)
│   └── Weapon.java                     # Weapon stats + Pack-a-Punch
└── spawn/
    ├── SpawnManager.java               # Spawn node registry + random selection
    └── SpawnNode.java                  # A single spawn point (zoneId, position, radius)
```

---

## Build System

- **Gradle** (Kotlin DSL) with Hytale-Tools plugin
- **Java toolchain**: configured via `gradle.properties` (`java_version`)
- **Test deps**: JUnit 5.11.0, Mockito 5.16.1, ByteBuddy 1.17.5
- **Run tests**: `./gradlew test`
- **Build plugin**: `./gradlew build`
- **Run Hytale dev server**: `./gradlew runServer`

---

## GitHub Repository

https://github.com/MarshRey/HytaleZombies.git
