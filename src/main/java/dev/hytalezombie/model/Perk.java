package dev.hytalezombie.model;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a Perk-a-Cola machine in the game.
 * Perks provide passive bonuses to players.
 */
public class Perk {

    public enum PerkType {
        JUGGERNOG("Juggernog", "Increase max health", 2500),
        SPEED_COLA("Speed Cola", "Faster reload speed", 3000),
        QUICK_REVIVE("Quick Revive", "Faster revive speed", 1500),
        DOUBLE_TAP("Double Tap", "Increased fire rate", 2000),
        STAMIN_UP("Stamin-Up", "Faster movement speed", 2000),
        DEADSHOT_DAIQUIRI("Deadshot Daiquiri", "Tighter hip-fire accuracy", 1500),
        ELECTRIC_CHERRY("Electric Cherry", "Electric burst on reload", 2000),
        MASTER_KEY("Master Key", "Bullets penetrate barriers", 3000),
        PHOENIX_UP("Phoenix Up", "Auto-revive nearby players", 5000),
        TOMBSTONE("Tombstone", "Drop a tombstone on death", 3000),
        WHOS_WHO("Who's Who", "Second chance on down", 2000),
        MULE_KICK("Mule Kick", "Carry a third weapon", 4000);

        private final String displayName;
        private final String description;
        private final int cost;

        PerkType(String displayName, String description, int cost) {
            this.displayName = displayName;
            this.description = description;
            this.cost = cost;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getCost() { return cost; }
    }

    private final PerkType type;
    private boolean isActive;

    public Perk(@Nonnull PerkType type) {
        this.type = type;
        this.isActive = false;
    }

    @Nonnull
    public PerkType getType() { return type; }

    public String getDisplayName() { return type.getDisplayName(); }

    public String getDescription() { return type.getDescription(); }

    public int getCost() { return type.getCost(); }

    public boolean isActive() { return isActive; }

    public void setActive(boolean active) { isActive = active; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Perk perk = (Perk) o;
        return type == perk.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return "Perk{type=" + type + ", active=" + isActive + "}";
    }
}
