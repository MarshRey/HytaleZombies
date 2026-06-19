# Agent Instructions

> **Always read this file first when starting work on this project.**

## Core Rules for Every Session

1. **Update `README.md`**: After any code change, feature addition, build fix, or documentation improvement, update `README.md` to reflect the current state of the project. This includes:
   - Test counts (run `./gradlew test` and update the count in the README)
   - New files or directories added to the project file map
   - New commands or configuration options
   - Build instructions or troubleshooting steps that changed
   - Entity spawning or ECS API changes

2. **Push to Git**: After making changes and updating the README, commit and push to the remote repository. The commit should be meaningful and describe what was done.

3. **Keep `AGENTS.md` Current**: If this file becomes outdated, update it. This is the primary handoff document for future agents.

---


# HytaleZombie - Project Context

## Overview

HytaleZombie is a round-based wave survival game mode for Hytale, inspired by Call of Duty Zombies. Players survive waves of increasingly difficult zombies, earn points, purchase weapons/upgrades, repair barriers, and unlock new areas of the map.

The **core game logic is fully implemented at the logical/game-engine level** with 138 passing unit tests. The spawning system, round progression, economy, power-ups, barriers, and map zones are all coded and tested. The only remaining step to see a basic round of zombies in-game is hooking it up to the Hytale server SDK to spawn real entities.

---

## ?? The Goal: A Simple Round of Zombies (Already Achieved in Code)

### What counts as a "basic round"?

1. **Spawn points** are placed by you with `/hz setspawn` (no default coordinates)
2. When `/hytalezombie start` is run, round 1 begins
3. **Zombies spawn one at a time** at a configurable interval (default: every 2 seconds / 40 ticks)
4. Each zombie spawns at **one of the three spawn points** (chosen randomly), at a **randomized offset** within that node's radius
5. Zombies **keep spawning at that interval** until the total number for the round is reached
6. When all zombies for the round are spawned **AND** eliminated, the round **auto-advances**
7. Round 2 begins with **more zombies** that are **stronger and faster**, repeating the same pattern

**The code already does ALL of the above.** It just needs the Hytale entity system to make it real in-game.

---

## ?? How the Spawning Cycle Works (End-to-End Walkthrough)

Here is the complete flow from server start to round completion, with every relevant file cited:

### Step 1: Plugin Initialization

**`HytaleZombiePlugin.java`** ? lifecycle methods:

```
preLoad():  Creates RoundManager, PlayerDataManager, BarrierManager, SpawnManager, GameSession
            (runs when plugin is loaded, plugin state is NOT enabled yet)

start():    Registers /hytalezombie command via CommandRegistry
            Starts a 20-tick-per-second game loop (ScheduledExecutorService, 50ms intervals)
            Registers PlayerReadyEvent handler to greet players + create PlayerData
            (runs when plugin is ENABLED ? commands/events cannot be registered earlier)
```

### Step 2: Admin Starts the Game

**`HytaleZombieCommand.java`** ? `execute("start")`:

```
plugin.setupDefaultMap()        ? marks spawn_room zone (add spawns with /hz setspawn)
session.startMatch()            ? roundManager.startMatch() ? round=1, match active
session.prepareRoundSpawns()    ? calculates total zombies for round 1
```

`setupDefaultMap()` marks spawn_room zone only. Use `/hz setspawn` commands. For custom maps, you can `/hz setspawn <zone> <x> <y> <z>` to place spawns at specific world coordinates.

### Step 3: The Game Loop Ticks (20 times per second)

**`GameSession.java`** ? `tick()` (called every 50ms):

```
if !sessionActive ? return
tickCounter++
tickPowerUps()              ? countdown active power-up timers
handleSpawning()            ? spawn a zombie if interval has elapsed (see Step 4)
checkRoundComplete()        ? advance round if all done (see Step 5)
```

### Step 4: Zombie Spawning

**`GameSession.java`** ? `handleSpawning()`:

```
if zombiesSpawnedThisRound >= totalZombiesToSpawn ? return (all done)

if spawnTicker < spawnDelayTicks (default 40):
    spawnTicker++             ? waiting for the interval
    return

spawnTicker = 0               ? reset the interval counter

// Pick one of the 3 spawn points at random
spawnManager.getRandomSpawnNode()
    ? returns a SpawnNode (picks randomly from Node A, Node B, Node C)

// Calculate a randomized position within that node's spawn radius
position = spawnManager.getRandomizedPosition(node)
    ? random X/Z offset within node.radius, maintains Y level

// Create the zombie with stats scaled to current round
ZombieInstance zombie = createZombie(position)
    ? health = roundManager.getScaledZombieHealth()   (100.0 * 1.15^(round-1))
    ? speed  = roundManager.getScaledZombieSpeed()    (1.0 * 1.05^(round-1))

activeZombies.put(zombie.getId(), zombie)
roundManager.incrementActiveZombies()
zombiesSpawnedThisRound++
```

### Step 5: Round Completion

**`GameSession.java`** ? `checkRoundComplete()`:

```
checkRoundComplete():
    if all zombies spawned AND all zombies eliminated (activeZombies.isEmpty()):
        ? auto-advance happens in RoundManager.decrementActiveZombies()
        ? when activeZombieCount hits 0 ? advanceRound() ? round increments
```

---

## ?? The Three Spawn Points (Defined in Code)

**`HytaleZombiePlugin.java`** ? `setupDefaultMap()`:

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
- Each has a **spawnRadius** of 5.0 ? a zombie spawns at a **random offset** (up to 5 blocks away in X/Z) from the node's center position
- Selection is random: `SpawnManager.getRandomSpawnNode()` picks uniformly from all active nodes

**To change these positions**, edit `setupDefaultMap()` in `HytaleZombiePlugin.java` and set the Vector3f values to your desired world coordinates.

---

## ? Spawn Interval Controls

The `spawnDelayTicks` in `HytaleZombieConfig.java` controls the time between each zombie spawn:

| `spawnDelayTicks` | Real Time | Effect |
|---|---|---|
| **40** (default) | **2 seconds** | Standard pace (20 ticks/sec ? 40 = 2000ms) |
| **20** | **1 second** | Fast pace |
| 60 | 3 seconds | Slow pace (harder because zombies build up) |
| 10 | 0.5 seconds | Very fast, intense |
| 1 | 0.05 seconds | Nearly instant ? all zombies flood out immediately |

The spawn ticker is an internal counter that increments by 1 each `tick()` call. When it reaches `spawnDelayTicks`, one zombie is spawned and the ticker resets to 0.

---

## ?? Zombie Count & Scaling Formulas

### Zombies Per Round

Calculated in **`RoundManager.getSpawnCount(playerCount)`**:

```
totalZombies = ZombieSpawnBaseCount(5) + (ZombiesPerPlayer(2) ? playerCount) + (currentRound ? 2)
```

**Examples (1 player):**
| Round | Calculation | Zombies |
|-------|-------------|---------|
| 1 | 5 + (2?1) + (1?2) | **9** |
| 2 | 5 + (2?1) + (2?2) | **11** |
| 5 | 5 + (2?1) + (5?2) | **17** |
| 10 | 5 + (2?1) + (10?2) | **27** |

**Examples (4 players):**
| Round | Calculation | Zombies |
|-------|-------------|---------|
| 1 | 5 + (2?4) + (1?2) | **15** |
| 5 | 5 + (2?4) + (5?2) | **23** |
| 10 | 5 + (2?4) + (10?2) | **33** |

### Zombie Health Scaling

Calculated in **`RoundManager.getScaledZombieHealth()`**:

```
health = baseHealth(100) ? (healthScalingPerRound(1.15))^(round-1)
```

| Round | HP | Notes |
|-------|----|-------|
| 1 | 100 | Base |
| 2 | 115 | 15% increase |
| 3 | ~132 | |
| 5 | ~175 | Nearly double |
| 10 | ~352 | 3.5? base |
| 20 | ~1,424 | Getting tough |

### Zombie Speed Scaling

Calculated in **`RoundManager.getScaledZombieSpeed()`**:

```
speed = baseSpeed(1.0) ? (speedScalingPerRound(1.05))^(round-1)
```

| Round | Speed | Effect |
|-------|-------|--------|
| 1 | 1.0? | Walking pace |
| 5 | ~1.22? | Noticeably faster |
| 10 | ~1.55? | Sprinting |
| 20 | ~2.53? | Extremely fast |

### Points System

| Action | Points (normal) | Points (Double Points) | Where |
|--------|-----------------|------------------------|-------|
| Hit a zombie (not kill) | **10** | **20** | `GameSession.damageZombie()` |
| Kill a zombie | **100** | **200** | `GameSession.damageZombie()` |

---

## ?? How to Test the Spawning Cycle (Without a Server)

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

**`RoundManagerTest.java` ? tests the round/scaling math:**

| Test Method | What It Proves |
|---|---|
| `getScaledZombieHealth_round1()` | Round 1 health = base (100) |
| `getScaledZombieHealth_lateRounds()` | Round 5 health = 100 ? 1.15^4 |
| `getSpawnCount()` | Spawn count includes base + per-player + round bonus |
| `decrementActiveZombies_autoAdvance()` | When all zombies die, round auto-advances |

**`SpawnManagerTest.java` ? tests the spawn node system:**

| Test Method | What It Proves |
|---|---|
| `registerSpawnNode()` | Nodes register per-zone |
| `markZoneOccupied()` | Only occupied zones' nodes are active |
| `getRandomSpawnNode()` | Random selection from active nodes |
| `getRandomizedPosition_withinRadius()` | Position offset stays within spawn radius |
| `getRandomizedPosition_sameY()` | Y coordinate is preserved |

---

## ? What's Already Working (The Logical Layer ? Fully Tested)

| Feature | Status | Key File(s) |
|---------|--------|-------------|
| Spawn points placed by user | ? | `HytaleZombiePlugin.setupDefaultMap()` |
| Random selection between spawn points | ? | `SpawnManager.getRandomSpawnNode()` |
| Randomized offset within spawn radius | ? | `SpawnManager.getRandomizedPosition(node)` |
| Interval-based spawning (configurable delay) | ? | `GameSession.handleSpawning()` |
| Per-round zombie count calculation | ? | `RoundManager.getSpawnCount()` |
| Scaled zombie health per round | ? | `RoundManager.getScaledZombieHealth()` |
| Scaled zombie speed per round | ? | `RoundManager.getScaledZombieSpeed()` |
| Auto-advance round when all clear | ? | `RoundManager.decrementActiveZombies()` |
| 20-tick-per-second game loop | ? | `HytaleZombiePlugin.startGameLoop()` |
| Points awarded on hits (10) and kills (100) | ? | `GameSession.damageZombie()` |
| Double Points / Insta-Kill power-ups | ? | `GameSession.activatePowerUp()` |
| Nuke kills all active zombies | ? | `GameSession.nukeAllZombies()` |
| Weapon purchasing economy | ? | `GameSession.purchaseWeapon()` |
| Perk purchasing economy | ? | `GameSession.purchasePerk()` |
| Door/zone unlocking | ? | `GameSession.purchaseDoor()` |
| Barriers with repair mechanics | ? | `Barrier.java`, `BarrierManager.java` |
| Player data tracking (points, kills, downs) | ? | `PlayerDataManager.java` |
| Map zone connectivity system | ? | `ZoneManager.java`, `MapZone.java` |
| 12 perk types with costs | ? | `Perk.java` |
| 7 power-up types | ? | `PowerUp.java` |
| Weapon registry with wall + mystery box | ? | `WeaponRegistry.java` |
| 138 unit tests (all passing) | ? | `src/test/` |
| Door-crossing zone tracking | ? | `ZoneManager.java`, `MapZone.java`, `GameSession.java` |
| Automatic spawn zone occupancy from player positions | ? | `GameSession.playerZoneIds`, `SpawnManager.occupiedZones` |

## ? Hytale SDK Integration (Entity System ? Complete)

| Feature | Status | Key File(s) |
|---------|--------|-------------|
| ZombieEntity class (extends LivingEntity) | ? | `src/main/java/dev/hytalezombie/entity/ZombieEntity.java` |
| EntitySpawnHelper (assembles ECS holder) | ? | `src/main/java/dev/hytalezombie/entity/EntitySpawnHelper.java` |
| ZombieEntity registered with EntityModule | ? | `HytaleZombiePlugin.setup()` ? registered as `"hz_zombie"` |
| World reference accessible to GameSession | ? | `GameSession.setWorld()` ? set from `PlayerReadyEvent` in `start()` |
| createZombie() spawns real entities | ? | `GameSession.createZombie()` calls `EntitySpawnHelper.spawnZombie()` |
| Damage event listener for zombie hits/kills | ? | `ZombieDamageEventSystem.java` ? registered in `setup()` |
| Entity removal on death | ? | `ZombieDamageEventSystem` uses `CommandBuffer.removeEntity()` (safe during ECS iteration); `nukeAllZombies()` defers via `world.execute()` |
| Zombie AI movement | ? | `GameSession.tickZombieAI()` sets `Velocity` + `MovementStates` through Hytale physics (gravity, collision); replaces old `teleportPosition()` warp |

---

## ?? Important: Plugin Lifecycle

The Hytale plugin lifecycle follows: `NONE` ? `SETUP` (via `setup()`) ? `START`/`ENABLED` (via `start()`).

| Phase | When | What you can do | What you CAN'T do |
|-------|------|-----------------|-------------------|
| `preLoad()` | Plugin loaded, state is `NONE` | Create managers, init fields | Register entities, commands, events, game loops |
| `setup()` | Plugin transitioning to `SETUP` state | Register **entity types** via `getEntityRegistry().registerEntity()` | Register commands, events (plugin not yet ENABLED) |
| `start()` | Plugin is `ENABLED` | Register commands, events, start game loops | ? |

Commands and events **cannot** be registered in `preLoad()` or `setup()`. Only in `start()`:

```java
// ? CORRECT ? move registrations here
@Override
protected void start() {
    getCommandRegistry().registerCommand(new MyCommand(this));
    getEventRegistry().registerGlobal(MyEvent.class, event -> { ... });
    startGameLoop();
}
```

## ?? Hytale SDK Integration ? Step-by-Step Plan

### Step 1 ? ? Register ZombieEntity with EntityModule
**File**: `HytaleZombiePlugin.java` ? `setup()` method added.
**Status**: ? COMPLETE

The `setup()` method now calls:
```java
getEntityRegistry().registerEntity(
    "hz_zombie",
    ZombieEntity.class,
    ZombieEntity::new,
    ZombieEntity.CODEC
);
```
This registers `ZombieEntity` with Hytale's ECS framework so that:
- `ZombieEntity.getComponentType()` returns a valid `ComponentType<EntityStore, ZombieEntity>`
- The entity codec (for serialization/networking) is registered
- ECS systems can query for `ZombieEntity` components

### Step 2 ? ? Provide World Reference for Entity Spawning
**Files**: `GameSession.java`, `HytaleZombiePlugin.java`
**Status**: ? COMPLETE

Added a `World` field to `GameSession` with `setWorld()`/`getWorld()` accessors. The world is obtained from the first `PlayerReadyEvent`:
```java
// In HytaleZombiePlugin.start() ? PlayerReadyEvent handler:
if (gameSession.getWorld() == null) {
    World world = event.getPlayerRef().getStore().getExternalData().getWorld();
    if (world != null) {
        gameSession.setWorld(world);
    }
}
```

### Step 3 ? ? Update createZombie() to Spawn Real Entities
**File**: `GameSession.java`, `EntitySpawnHelper.java`
**Status**: ? COMPLETE

`EntitySpawnHelper.spawnZombie()` now returns a `SpawnResult` record containing both the `networkId` and the `Ref<EntityStore>` for later removal. `GameSession.createZombie()` calls it conditionally when a `World` reference is available, falling back to logical-only zombies for test compatibility:

```java
private ZombieInstance createZombie(Vector3f position) {
    // ... create ZombieInstance with scaled health/speed ...
    
    if (world != null) {
        EntitySpawnHelper.SpawnResult result = EntitySpawnHelper.spawnZombie(world, position);
        if (result != EntitySpawnHelper.SpawnResult.FAILED && result.entityRef() != null) {
            zombie.setNetworkId(result.networkId());
            zombie.setEntityRef(result.entityRef());
            networkIdToZombieId.put(result.networkId(), zombieId);
        }
    }
    return zombie;
}
```

### Step 4 ? ? Hook Damage Events
**Files**: `ZombieDamageEventSystem.java` (new), `HytaleZombiePlugin.java`
**Status**: ? COMPLETE

Created `ZombieDamageEventSystem` extending `DamageEventSystem`. It:
- Queries for `ZombieEntity` components
- Runs in the `DamageModule.inspectDamageGroup`
- Resolves zombie network ID ? logical ID via `GameSession.getZombieIdByNetworkId()`
- Extracts the attacker's UUID from the damage source
- Forwards hits to `GameSession.damageZombie()`

Registered in `HytaleZombiePlugin.setup()`:
```java
getEntityStoreRegistry().registerSystem(new ZombieDamageEventSystem(gameSession));
```

### Step 5 ? ? Handle Entity Removal on Death
**Files**: `GameSession.java`
**Status**: ? COMPLETE

In `damageZombie()`, when a zombie dies:
```java
if (world != null) {
    zombie.getEntityRef().ifPresent(ref -> {
        CommandBuffer<EntityStore> cmdBuffer = world.getEntityStore().getStore().getCommandBuffer();
        if (cmdBuffer != null && ref.isValid()) {
            cmdBuffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    });
    if (zombie.getNetworkId() >= 0) {
        networkIdToZombieId.remove(zombie.getNetworkId());
    }
}
```

Same pattern applied to `nukeAllZombies()` for bulk entity removal.

### Integration Touchpoints (All Wired)

| Integration Point | File | Status |
|---|---|---|
| Extend `JavaPlugin` | `HytaleZombiePlugin.java` | ? Already extends it |
| Register commands via Hytale's API | `HytaleZombieCommand.java` | ? Already extends `AbstractCommand` |
| Handle `PlayerReadyEvent` | `HytaleZombiePlugin.java` | ? Registered in `start()` + sets World |
| Game loop via Hytale's task system | `HytaleZombiePlugin.java` | ? Uses `TaskRegistration` |
| Send messages with `Message` API | `HytaleZombieCommand.java` | ? Uses `Message.raw()` |
| Entity registration with EntityModule | `HytaleZombiePlugin.java` | ? `setup()` calls `getEntityRegistry().registerEntity()` |
| Damage event system | `ZombieDamageEventSystem.java` | ? Registered in `setup()` |

---

## ?? Quick Start Guide

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

1. **`src/main/java/dev/hytalezombie/manager/GameSession.java`** ? The main orchestrator. Read `tick()`, `handleSpawning()`, `createZombie()`, `damageZombie()`, `checkRoundComplete()`
2. **`src/main/java/dev/hytalezombie/HytaleZombiePlugin.java`** ? Entry point, game loop, `setupDefaultMap()` where you set spawn positions
3. **`src/main/java/dev/hytalezombie/manager/RoundManager.java`** ? Round tracking, scaling math, spawn count formula
4. **`src/main/java/dev/hytalezombie/spawn/SpawnManager.java`** ? Spawn node registry, random selection, position randomization
5. **`src/main/java/dev/hytalezombie/config/HytaleZombieConfig.java`** ? All tunable values (spawn delay, health, speed, costs)

### Entity spawning is now live:

All five Hytale SDK integration steps are complete. When a player joins and `/hz start` is run:
1. The `World` reference is captured from `PlayerReadyEvent`
2. `createZombie()` spawns real `ZombieEntity` instances via `EntitySpawnHelper`
3. `ZombieDamageEventSystem` forwards damage events for point tracking
4. Entities are removed from the world on death or nuke

The game loop, timing, scaling, points, and round advancement were already fully built and tested (138 unit tests).

---

## ?? Game Commands

### Match Controls
| Command | What It Does |
|---------|-------------|
| `/hytalezombie start` or `/hz start` | Starts a match, marks spawn_room zone, begins round 1. Use /hz setspawn to add spawn points |
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

## ?? Config Reference (`HytaleZombieConfig.java`)

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

## ?? Project File Map

```
src/main/java/dev/hytalezombie/
??? HytaleZombiePlugin.java             # Entry point + setup() + start() + game loop
?   ??? setup()                         # Registers ZombieEntity with EntityModule
?   ??? start()                         # Registers commands, events, game loop
?   ??? setupDefaultMap()               # No default spawns - use /hz setspawn in-game
??? commands/
?   ??? HytaleZombieCommand.java        # /hytalezombie command handler
??? config/
?   ??? HytaleZombieConfig.java         # All tunable values
??? entity/
?   ??? EntitySpawnHelper.java          # Assembles ECS entity holder and spawns into world
?   ??? ZombieEntity.java               # Zombie entity class (extends LivingEntity)
??? events/
?   ??? PlayerConnectionListener.java   # Player join utility
??? manager/
?   ??? BarrierManager.java             # CRUD for window barriers
?   ??? DebugManager.java               # Debug mode + spawn node visualization
?   ??? GameManagerProvider.java        # Interface for accessing all managers
?   ??? GameSession.java                # MAIN GAME ORCHESTRATOR (tick, spawn, damage, economy, zone tracking)
?   ??? PlayerDataManager.java          # Per-player state management
?   ??? RoundManager.java               # Round tracking + scaling calculations
?   ??? WeaponRegistry.java             # All weapon definitions
?   ??? ZoneManager.java                # Map zone connectivity, door positions, door-crossing detection
??? model/
?   ??? Barrier.java                    # Barrier state machine (INTACT?DAMAGED?BROKEN)
?   ??? MapZone.java                    # Named zone with door cost + door positions
?   ??? Perk.java                       # Perk-a-Cola definitions (12 perks)
?   ??? PlayerData.java                 # Points, kills, downs, alive
?   ??? PowerUp.java                    # Power-up types + timed duration
?   ??? Vector3f.java                   # Float 3D vector (entity positions)
?   ??? Vector3i.java                   # Int 3D vector (block positions)
?   ??? Weapon.java                     # Weapon stats + Pack-a-Punch
??? spawn/
    ??? SpawnManager.java               # Spawn node registry + random selection
    ??? SpawnNode.java                  # A single spawn point (zoneId, position, radius)
??? manager/
?   ??? ...                             # (existing managers)
?   ??? ScoreboardManager.java          # Player tracking + HUD state bridge
│ ?   ??? ZombieHud.java                  # Custom HUD overlay (CustomUIHud API) (round, zombies, points)
```

---

## Recent Changes & Bug Fixes

### ✅ Phase 1: Fixed Async Race Condition (UUID-based Damage Routing)
**Bug**: `ZombieDamageEventSystem` used `zombie.getNetworkId()` for entity lookup, but the networkId was set asynchronously via `CompletableFuture`. Damage events could arrive before the mapping existed, causing kills to be silently lost.

**Fix**: `GameSession.createZombie()` now generates a `UUID` synchronously and stores it in `uuidToZombieId`. `EntitySpawnHelper` accepts an optional UUID parameter. `ZombieDamageEventSystem` reads `UUIDComponent` and calls `gameSession.getZombieIdByUuid()`.

### ✅ Phase 2: Fixed Round Advancement Deadlock
**Bug**: When all zombies were killed, `decrementActiveZombies()` incremented the round counter via `advanceRound()` but `prepareRoundSpawns()` was never called. Round 2+ had `totalZombiesToSpawn = 0`.

**Fix**: Removed auto-advance from `RoundManager.decrementActiveZombies()`. `GameSession.checkRoundComplete()` now calls `advanceRound()` + `prepareRoundSpawns()`.

### ✅ Phase 3: Zombie AI / Movement
**Feature**: Zombies move toward a target point at scaled speed using tick-based movement synced to Hytale entity `TransformComponent` positions via `world.execute()`.

### ✅ Phase 4: Persistent Scoreboard / HUD
**Feature**: Chat-based scoreboard via `ScoreboardManager` (since replaced by Custom HUD in Phase 6).

### ✅ Phase 4.5: Custom HUD Overlay
**Feature**: Replaced chat-based scoreboard with a proper server-driven Custom HUD overlay via Hytale's `CustomUIHud` API. The `ZombieHud` class (in `ui/`) shows round number, active zombie count, and player points as a persistent overlay in the top-right corner using `.ui` markup at `Common/UI/Custom/Hud/ZombieHud.ui`. Registered per-player via `HudManager.addCustomHud()` on join, updated every 20 ticks from the game loop.

### ✅ Phase 4.6: Spawn Node Persistence
**Feature**: Spawn nodes and occupied zones are automatically saved to `run/hytalezombie_data/spawn_nodes.json` after every mutation command (`setspawn`, `delspawn`, `clearspawns`, `markzone`, `unmarkzone`). Loaded on plugin startup via `SpawnManager.loadFromFile()`. Uses Gson for JSON serialization. Survives server restarts.

### ✅ Phase 5: Fixed NPC Role Loading, Desync, and Loot Drops
**Bug 1 (Role not found)**: `hz_zombie.json` referenced `Template_Creature` (doesn't exist). The NPCPlugin's `AssetRegistryLoader` loads from `src/main/resources/Server/` — the role was only at `assets/hytalezombie/Server/NPC/Roles/`, unreachable from the CLASSPATH dev path.

**Fix 1**: Changed `Reference` to `Template_Aggressive_Zombies` (Hytale's actual zombie template). Copied role to `src/main/resources/Server/NPC/Roles/hz_zombie.json` for CLASSPATH dev loading. Used proper `Modify` structure matching Hytale's variant pattern. Removed custom Instructions/MotionControllers — the template provides all AI behavior.

**Bug 2 (Desync)**: `ZombieDamageEventSystem` filtered by `npc.getRoleName() == "hz_zombie"`. During NPC death, the role name changes, causing the handler to lose track. Zombies became vanilla entities — dropping loot and not being cleaned up.

**Fix 2**: Replaced role-name check with UUID-only filtering (`uuidToZombieId` map). UUID components never change during NPC lifecycle. Removed `buffer.removeEntity()` call — NPC death system handles death animation and despawn natively.

**Bug 3 (Loot drops)**: Default `DropList: "Drop_Zombie"` in role template caused item drops on death.

**Fix 3**: Set `DropList: "Empty"` in role JSON.

**Feature**: Added `tickZombieAI()` cleanup for zombies whose entity refs become invalid (despawned by NPC system).

### ✅ Phase 6: Door-Crossing Zone Tracking
**Feature**: Automatic zone occupancy tracking via door-crossing detection. Players are assigned to zones based on their world position and transition between zones by walking near defined door positions.

**How it works:**
1. **Zone registration**: `/hz addzone <zoneId> <displayName> [cost]` registers a new zone in `ZoneManager`
2. **Zone connectivity**: `/hz connectzone <zoneA> <zoneB>` creates a bidirectional connection
3. **Door placement**: `/hz setdoor <zoneA> <zoneB> <x> <y> <z>` sets the world-space door position
4. **Automatic tracking**: As players move, their position is checked against door positions in their current zone. When within 2.5 blocks of a door, they transition to the connected zone.
5. **Spawn occupancy**: `SpawnManager.occupiedZones` is automatically synced — zombies only spawn in zones with active players.

**Key files:**
- `MapZone.java` — Added `doorPositions` map, `checkDoorCrossing()` distance check, `DOOR_CROSSING_RADIUS = 2.5f`
- `ZoneManager.java` — Added `setDoorPosition()`, `checkDoorCrossing()`, `findPlayerZone()`, `getDoorPosition()`
- `GameSession.java` — Added `ZoneManager` field, `playerZoneIds` map, `updateZoneOccupancy()`, `handleZoneTransition()`
- `HytaleZombiePlugin.java` — Creates `ZoneManager("spawn_room")`, wires into `GameSession`
- `HytaleZombieCommand.java` — Added `/hz addzone`, `/hz connectzone`, `/hz setdoor` commands

---

## 🗺️ Map Prefabs (Minecraft → Hytale)

Map import is handled by the **SchematicImporter** mod (`run/mods/SchematicImporter-1.1.0.jar`).
No custom code needed — the mod converts `.schematic`/`.litematic` files to Hytale prefabs in-game.

### How to Import a Minecraft Map

1. **Place your `.schematic` or `.litematic` file** on the server (any accessible path).

2. **Use the SchematicImporter mod's in-game commands** to convert and place the structure.
   See the mod's documentation for exact command syntax.

3. **Set up spawn nodes** around the placed structure using `/hz setspawn`.

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
