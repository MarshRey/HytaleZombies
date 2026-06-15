# HytaleZombie

A round-based wave survival game mode for Hytale, inspired by Call of Duty Zombies. Players survive waves of increasingly difficult zombies, earn points, purchase weapons/upgrades, repair barriers, and unlock new areas of the map.

The **core game logic is fully implemented** with **245+ passing unit tests**. The spawning system, round progression, economy, power-ups, barriers, and map zones are all coded and tested.

---

## Quick Start

### Prerequisites

- **Java 25** installed ([Eclipse Adoptium](https://adoptium.net/) or [JetBrains Runtime](https://hytalemodding.dev/en/docs/guides/plugin/setting-up-env) for hotswap)
- Hytale game client installed

### Setup & Run

```powershell
# 1. Prepare the Hytale development environment (download assets, decompile server sources)
.\gradlew.bat setupHytaleDev

# 2. Clean old log files from previous runs (prevents stale .lck file build errors)
Remove-Item -Path "run\logs\*" -Force -ErrorAction SilentlyContinue

# 3. Launch the local Hytale server with your plugin
.\gradlew.bat runServer
```

> ?? **Important**: If the server was previously started, stale `.lck` lock files in `run\logs\` will cause a build error:
> ```
> Failed to create MD5 hash for file: run\logs\..._server.log.lck
> ```
> Simply clean the logs directory and re-run. Set up a convenience alias:
> ```powershell
> function hyRun { Remove-Item -Path "run\logs\*" -Force -ErrorAction SilentlyContinue; .\gradlew.bat runServer }
> ```

> On Windows use `gradlew.bat`; on macOS/Linux use `./gradlew`.

### First-Time Authentication

When the server starts for the first time, it will output a URL asking you to authorize it. Click the link, log in with your Hytale account, and authorize the server.

### Join the Server

1. Launch the **Hytale game client**
2. Go to **Multiplayer / Direct Connect**
3. Connect to **`localhost`** (or `127.0.0.1`)

---

## Server Lifecycle

### Stopping the Server

When you're done, shut down the Hytale server cleanly:

**In the server console window**, type:
```
/stop
```

### Cleaning Up Before Re-Running

Each time you run `.\gradlew.bat runServer`, a new Java process starts. Re-running without stopping the previous instance will leave **stale server processes** that all bind to port 5520, causing connection issues like **"Server Auth Unavailable"**.

**One-liner to clean everything and restart:**
```powershell
function hyCleanRun {
    # Kill any java processes using port 5520 (the Hytale server port)
    Get-NetUDPEndpoint -LocalPort 5520 -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        ForEach-Object { Stop-Process -Id $_ -Force }
    # Clean stale lock files
    Remove-Item -Path "run\logs\*" -Force -ErrorAction SilentlyContinue
    # Launch fresh
    .\gradlew.bat runServer
}
```

**Manual cleanup if you prefer:**
```powershell
# 1. Kill any Hytale server processes by port
Get-NetUDPEndpoint -LocalPort 5520 -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object { Stop-Process -Id $_ -Force }

# 2. Clean old logs
Remove-Item -Path "run\logs\*" -Force -ErrorAction SilentlyContinue

# 3. Re-launch
.\gradlew.bat runServer
```

> ?? **Why this matters**: Hytale uses **QUIC (UDP)** for networking, not TCP. When multiple server processes bind to port 5520, your game client's packets can be delivered to **any** of the stale processes, causing auth failures. Always kill old processes before re-running.

---

## Useful Commands

```powershell
# Build & setup
.\gradlew.bat setupHytaleDev        # Prepare dev environment
.\gradlew.bat build                 # Build the plugin jar
.\gradlew.bat compileJava           # Compile only (faster)

# Run server
.\gradlew.bat runServer             # Launch Hytale server with plugin

### Setup & Run
.\gradlew.bat runServer "-Ddebug=true" "-Dhotswap=true"   # With debug + hotswap

# Diagnostics
.\gradlew.bat hytaleDoctor          # Print diagnostic summary
.\gradlew.bat hytaleJvmDoctor       # Check JVM hot swap support

# Testing
.\gradlew.bat test                  # Run all 138 unit tests
.\gradlew.bat test --tests "*GameSessionTest*"   # Run specific tests

# Refresh dependencies if something fails
.\gradlew.bat build --refresh-dependencies
```

---

## Game Commands (In-Game)

### Match Controls
| Command | Aliases | What It Does |
|---------|---------|-------------|
| `/hytalezombie start` | `/hz start`, `/zombie start` | Starts a match, marks spawn_room zone, begins round 1 |
| `/hytalezombie stop` | `/hz stop`, `/zombie stop` | Ends the current match |
| `/hytalezombie round [n]` | `/hz round [n]` | Shows or sets the current round number |
| `/hytalezombie info` | `/hz info` | Shows match status, round, active zombies, player count |
| `/hz state` | ? | Full state dump (match, round, zombies, power-ups, config) |
| `/hz nextround` | ? | Force-advance to next round (kills remaining zombies) |

### Zombie Testing
| Command | What It Does |
|---------|-------------|
| `/hz spawnzombie [zone] [count]` | Spawns zombie(s) manually - works even without an active match (no round tracking). Useful for testing |
| `/hz killall` | Kills all active zombies instantly |
| `/hz zombieinfo` | Lists all active zombies with HP, speed, and position |
| `/hz spawninfo` | Shows spawn progress (spawned/killed/remaining this round) |

### Economy & Items
| Command | What It Does |
|---------|-------------|
| `/hz points [player] [amount]` | Shows or sets a player's points |
| `/hz powerup <type>` | Activates a power-up (nuke, instakill, doublepoints, maxammo, etc.) |
| `/hz giveweapon <player> <weapon_id>` | Gives a player a weapon without cost |
| `/hz giveperk <player> <perk_type>` | Gives a player a perk without cost |

### Map Setup Commands
| Command | What It Does |
|---------|-------------|
| `/hz map` | Registers the default test map with spawn nodes |
| `/hz setspawn <zone> [radius]` | Adds a spawn point at (0,0,0) for a zone |
| `/hz setspawn <zone> <x> <y> <z> [r]` | Adds a spawn point at specific coordinates |
| `/hz delspawn <zone> [index]` | Removes spawn points from a zone (or specific index) |
| `/hz listspawns [zone]` | Lists all registered spawn points |
| `/hz clearspawns` | Removes all spawn points |
| `/hz markzone <zone>` | Marks a zone as occupied (zombies will spawn there) |
| `/hz unmarkzone <zone>` | Unmarks a zone |
| `/hz listzones` | Lists all zones with spawns |

### Debug Commands
| Command | What It Does |
|---------|-------------|
| `/hz debug` | Toggles debug mode (spawn node visualization logs) |
| `/hz config` | Lists all config values |
| `/hz config <key> <value>` | Live-tweaks a config value without restart |
| `/hz state` | Full game state dump |

Requires the `hytalezombie.admin` permission.

---

## Features

| Feature | Status | Description |
|---------|--------|-------------|
| ✅ Spawn system | ✅ | Interval-based zombie spawning at configurable spawn points |
| ✅ Round progression | ✅ | Auto-advance with scaling difficulty (HP, speed, count) |
| ✅ Economy / Points | ✅ | Points for hits/kills, spend on weapons, perks, doors |
| ✅ Power-ups | ✅ | Nuke, Insta-Kill, Double Points, Max Ammo, Carpenter, etc. |
| ✅ Zombie AI | ✅ | Zombies use Velocity-based movement with Hytale physics (gravity, collision); face nearest player |
| ✅ Persistent HUD | ✅ | Scoreboard updates every second (round, zombies, points) |
| ✅ Entity integration | ✅ | Hytale SDK entity spawning + damage routing via UUID; safe CommandBuffer-based entity removal |
| ✅ 12 Perks | ✅ | Juggernog, Speed Cola, Quick Revive, etc. |
| ✅ 7 Power-ups | ✅ | All classic zombie power-ups implemented |
| ✅ Barriers | ✅ | Repair mechanics for window barriers |
| ✅ Map zones | ✅ | Zone connectivity + door unlocking |
| ✅ 138+ tests | ✅ | Unit tests for all core systems |

---

## How the Spawning Cycle Works

### High-Level Flow

1. **Run `/hz start`** ? marks spawn_room zone, starts round 1. No default spawns - use /hz setspawn to place them
2. **Place spawn points** with `/hz setspawn <zone> <x> <y> <z>` to place spawn points at world coordinates
3. **Game loop ticks 20 times/second** ? every 40 ticks (2 seconds), a zombie spawns at a random spawn point with a random offset inside its radius
4. **Zombies keep spawning** until the total count for the round is reached
5. **When all zombies are eliminated**, the round auto-advances
6. **Round 2+** has more zombies with higher health and speed

### Zombie Scaling

| Round | Zombies (1 player) | HP | Speed |
|-------|-------------------|----|-------|
| 1 | 9 | 100 | 1.0? |
| 2 | 11 | 115 | 1.05? |
| 5 | 17 | ~175 | ~1.22? |
| 10 | 27 | ~352 | ~1.55? |
| 20 | 47 | ~1,424 | ~2.53? |

### Points System

| Action | Points | With Double Points |
|--------|--------|--------------------|
| Hit a zombie (not kill) | 10 | 20 |
| Kill a zombie | 100 | 200 |
| Starting points | 500 | ? |

---

## Config Reference

All tunable values in `src/main/java/dev/hytalezombie/config/HytaleZombieConfig.java`:

| Property | Default | What It Controls |
|----------|---------|------------------|
| `startingPoints` | 500 | Points each player starts with |
| `zombieBaseHealth` | 100.0 | Round 1 zombie HP |
| `zombieBaseSpeed` | 1.0 | Round 1 zombie speed multiplier |
| `healthScalingPerRound` | 1.15 | Each round, HP ? this (15% increase) |
| `speedScalingPerRound` | 1.05 | Each round, speed ? this (5% increase) |
| `pointsPerKill` | 100 | Points for killing a zombie |
| `pointsPerHit` | 10 | Points for hitting a zombie |
| `zombieSpawnBaseCount` | 5 | Minimum zombies per round |
| `zombiesPerPlayer` | 2 | Extra zombies per additional player |
| `spawnDelayTicks` | 40 | Ticks between spawns (40 = 2 seconds at 20 TPS) |

---

## Project Structure

```
src/
??? main/
?   ??? java/dev/hytalezombie/
?   ?   ??? HytaleZombiePlugin.java       # Entry point + game loop + setupDefaultMap()
?   ?   ??? commands/
?   ?   ?   ??? HytaleZombieCommand.java  # /hytalezombie command handler
?   ?   ??? config/
?   ?   ?   ??? HytaleZombieConfig.java   # All tunable game values
?   ?   ??? events/
?   ?   ?   ??? PlayerConnectionListener.java
?   ?   ??? manager/
?   ?   ?   ??? BarrierManager.java       # Window barrier CRUD
?   ?   ?   ??? DebugManager.java         # Debug mode + in-world visualization
?   ?   ?   ??? GameManagerProvider.java  # Manager access interface
?   ?   ?   ??? GameSession.java          # MAIN ORCHESTRATOR (tick, spawn, damage, economy, AI)
?   ?   ??? ScoreboardManager.java    # Persistent HUD display (round/zombies/points)
?   ?   ?   ??? PlayerDataManager.java    # Per-player state
?   ?   ?   ??? RoundManager.java         # Round tracking + scaling math
?   ?   ?   ??? WeaponRegistry.java       # Weapon definitions
?   ?   ?   ??? ZoneManager.java          # Map zone connectivity + doors
?   ?   ??? model/
?   ?   ?   ??? Barrier.java              # Barrier state machine
?   ?   ?   ??? MapZone.java              # Named zone with door cost
?   ?   ?   ??? Perk.java                 # 12 perk definitions
?   ?   ?   ??? PlayerData.java           # Points, kills, downs, alive
?   ?   ?   ??? PowerUp.java              # Power-up types + durations
?   ?   ?   ??? Vector3f.java             # Float 3D vector
?   ?   ?   ??? Vector3i.java             # Int 3D vector
?   ?   ?   ??? Weapon.java               # Weapon stats + Pack-a-Punch
?   ?   ??? spawn/
?   ?       ??? SpawnManager.java         # Spawn node registry + selection
?   ?       ??? SpawnNode.java            # Single spawn point (zone, position, radius)
?   ??? resources/
?       ??? manifest.json                 # Plugin manifest
??? test/
    ??? java/dev/hytalezombie/
        ??? manager/
        ?   ??? BarrierManagerTest.java
        ?   ??? GameSessionTest.java
        ?   ??? PlayerDataManagerTest.java
        ?   ??? RoundManagerTest.java
        ?   ??? WeaponRegistryTest.java
        ?   ??? ZoneManagerTest.java
        ??? model/
        ?   ??? BarrierTest.java
        ?   ??? MapZoneTest.java
        ?   ??? PerkTest.java
        ?   ??? PlayerDataTest.java
        ?   ??? PowerUpTest.java
        ?   ??? WeaponTest.java
        ??? spawn/
            ??? SpawnManagerTest.java
            ??? SpawnNodeTest.java
```

---

## Build System

- **Gradle** (Kotlin DSL) with [Hytale-Tools Plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin)
- **Java toolchain**: 25 (configured in `gradle.properties`)
- **Test deps**: JUnit 5.11.0, Mockito 5.16.1, ByteBuddy 1.17.5

The Hytale Gradle Plugin handles:
- ? Manifest generation and validation
- ? Server jar decompilation + IDE source attachment
- ? Asset zip downloading and caching
- ? Local dev server launch
- ? Plugin packaging

### Gradle Properties (`gradle.properties`)

Key properties to customize for your setup:

| Property | Description |
|----------|-------------|
| `mod_id` | Unique lowercase mod identifier |
| `mod_name` | Human-readable mod name |
| `main_class` | Fully-qualified plugin entry point |
| `hytaleHomeOverride` | Path to local Hytale installation's Assets.zip |
| `hytale_version` | Hytale server version target |

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| **Gradle sync fails** | Check Java 25 is installed and configured under **File ? Project Structure ? SDKs** |
| **Plugin not loading** | Make sure commands are registered in `start()`, not `preLoad()` ? the plugin must be ENABLED first |
| **"The plugin ... is not enabled!"** | Move command/event registration from `preLoad()` to `start()` |
| **Assets zip not found** | Set `hytaleHomeOverride` in `gradle.properties` to your Hytale install's `Assets.zip` |
| **Symlink creation failed (Windows)** | The plugin falls back to Windows junctions; run terminal as Admin if needed |
| **Hot reload not working** | Use JetBrains Runtime and run `.\gradlew.bat hytaleJvmDoctor` |
| **Build fails with missing deps** | Run `.\gradlew.bat build --refresh-dependencies` |
| **"Failed to create MD5 hash for file: ...server.log.lck"** | A stale lock file from a previous server session. Run `Remove-Item -Path "run\logs\*" -Force` then re-launch |
| **"Server Auth Unavailable" when connecting** | Multiple stale server processes are fighting over port 5520. Run the cleanup steps in **Server Lifecycle** above to kill all old processes, then re-launch |
| **Can't connect / connection refused** | Make sure the server has fully booted (look for `Hytale Server Booted!` in the logs). Try `localhost` without a port number ? the client defaults to 5520 |

---

## Resources

- [Hytale Gradle Plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin)
- [Hytale Modding Guides](https://hytalemodding.dev)
- [Hytale Modding Discord](https://discord.gg/hytalemodding)

## License

MIT
