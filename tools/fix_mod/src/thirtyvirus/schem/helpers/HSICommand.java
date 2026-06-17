/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.hypixel.hytale.assetstore.map.BlockTypeAssetMap
 *  com.hypixel.hytale.component.Store
 *  com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
 *  com.hypixel.hytale.server.core.command.system.AbstractCommand
 *  com.hypixel.hytale.server.core.command.system.CommandContext
 *  com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection
 *  com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand
 *  com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
 *  com.hypixel.hytale.server.core.entity.entities.Player
 *  com.hypixel.hytale.server.core.universe.PlayerRef
 *  com.hypixel.hytale.server.core.universe.world.World
 *  com.hypixel.hytale.server.core.universe.world.storage.EntityStore
 *  javax.annotation.Nonnull
 */
package thirtyvirus.schem.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import thirtyvirus.schem.SchematicImporter;
import thirtyvirus.schem.helpers.PluginConfig;
import thirtyvirus.schem.helpers.Util;
import thirtyvirus.schem.ui.menus.SchematicFileSelectorMenu;

public class HSICommand
extends AbstractCommandCollection {
    public HSICommand() {
        super("hsi", "Schematic Importer commands");
        this.addSubCommand((AbstractCommand)new HelpCommand());
        this.addSubCommand((AbstractCommand)new SchematicCommand());
        this.addSubCommand((AbstractCommand)new InfoCommand());
        this.addSubCommand((AbstractCommand)new ExportCommand());
        this.addSubCommand((AbstractCommand)new ReloadCommand());
        this.addSubCommand((AbstractCommand)new ListBlocksCommand());
    }

    public static class HelpCommand
    extends CommandBase {
        public HelpCommand() {
            super("help", "Display all available commands");
        }

        protected void executeSync(@Nonnull CommandContext ctx) {
            Util.msg(ctx, List.of("&8&l========== &d&lSchematic Importer &8&l==========", "", "&6Commands:", "&e  /hsi help &7- Show this help menu", "&e  /hsi info &7- Show plugin info", "&e  /hsi schematic &7- Open schematic file browser", "&e  /hsi export &7- Export mappings to JSON", "&e  /hsi reload &7- Reload configuration", "&e  /hsi listblocks <pattern> &7- Search Hytale blocks", "", "&6Supported Formats:", "&7  - &a.schem &7(Sponge/WorldEdit)", "&7  - &a.litematic &7(Litematica)", "", "&7Place schematics in the &eschematics&7 folder.", "&8&l================================================"));
        }
    }

    public static class SchematicCommand
    extends AbstractWorldCommand {
        public SchematicCommand() {
            super("schematic", "Open schematic file browser");
        }

        protected void execute(@Nonnull CommandContext ctx, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            if (!ctx.isPlayer()) {
                Util.msg(ctx, SchematicImporter.getPhrase("player_only"));
                return;
            }
            PlayerRef playerRef = (PlayerRef)ctx.sender();
            if (playerRef == null) {
                Util.msg(ctx, SchematicImporter.getPhrase("player_ref_error"));
                return;
            }
            SchematicFileSelectorMenu.open(playerRef);
        }
    }

    public static class InfoCommand
    extends CommandBase {
        public InfoCommand() {
            super("info", "Display plugin information");
        }

        protected void executeSync(@Nonnull CommandContext ctx) {
            Util.msg(ctx, List.of("&d&lSchematic Importer", "&7Version: &e" + SchematicImporter.getVersion(), "&7Author: &ethirtyvirus", "", "&7Import Minecraft schematics into Hytale!", "&7Use &e/hsi schematic&7 to get started."));
        }
    }

    public static class ExportCommand
    extends CommandBase {
        public ExportCommand() {
            super("export", "Export block mappings to a file");
        }

        protected void executeSync(@Nonnull CommandContext ctx) {
            PluginConfig config = PluginConfig.getInstance();
            if (config == null) {
                Util.msg(ctx, "&cMaterial mapping config not initialized!");
                return;
            }
            Map<String, String> blockMappings = config.getBlockMappings();
            Map<String, String> itemMappings = config.getItemMappings();
            if (blockMappings.isEmpty() && itemMappings.isEmpty()) {
                Util.msg(ctx, "&eNo custom mappings to export. Use the schematic mapper to create mappings first.");
                return;
            }
            String filename = "mappings_export_" + System.currentTimeMillis() + ".json";
            PluginConfig pluginConfig = PluginConfig.getInstance();
            Path outputFolder = pluginConfig != null ? pluginConfig.getDataFolder() : Path.of(".", new String[0]);
            Path outputPath = outputFolder.resolve(filename);
            try {
                LinkedHashMap<String, Object> exportData = new LinkedHashMap<String, Object>();
                exportData.put("exportedAt", new Date().toString());
                exportData.put("blocks", blockMappings);
                exportData.put("items", itemMappings);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (BufferedWriter writer = Files.newBufferedWriter(outputPath, new OpenOption[0]);){
                    gson.toJson(exportData, (Appendable)writer);
                }
                Util.msg(ctx, "&aExported " + blockMappings.size() + " block mappings and " + itemMappings.size() + " item mappings!");
                Util.msg(ctx, "&7File: &e" + String.valueOf(outputPath.toAbsolutePath()));
                Util.log("Exported mappings to " + String.valueOf(outputPath.toAbsolutePath()));
            }
            catch (Exception e) {
                Util.msg(ctx, "&cFailed to export mappings: " + e.getMessage());
                Util.log("Failed to export mappings: " + e.getMessage(), true);
            }
        }
    }

    public static class ReloadCommand
    extends CommandBase {
        public ReloadCommand() {
            super("reload", "Reload plugin configuration");
        }

        protected void executeSync(@Nonnull CommandContext ctx) {
            try {
                PluginConfig config = PluginConfig.getInstance();
                if (config != null) {
                    config.reload();
                }
                Util.msg(ctx, "&aConfiguration reloaded!");
                Util.log("Configuration reloaded by " + String.valueOf(ctx.sender()));
            }
            catch (Exception e) {
                Util.msg(ctx, "&cFailed to reload config: " + e.getMessage());
                Util.log("Failed to reload config: " + e.getMessage(), true);
            }
        }
    }

    public static class ListBlocksCommand
    extends CommandBase {
        public ListBlocksCommand() {
            super("listblocks", "List Hytale blocks matching a pattern");
        }

        protected void executeSync(@Nonnull CommandContext ctx) {
            String pattern = "trunk";
            Util.msg(ctx, "&6Searching for blocks containing: &e" + pattern);
            ArrayList<String> matches = new ArrayList<String>();
            try {
                BlockTypeAssetMap assetMap = BlockType.getAssetMap();
                for (int i = 1; i < 10000; ++i) {
                    String id;
                    BlockType bt = (BlockType)assetMap.getAsset(i);
                    if (bt == null || (id = bt.getId()) == null || !id.toLowerCase().contains(pattern)) continue;
                    matches.add(id);
                }
            }
            catch (Exception e) {
                Util.msg(ctx, "&cError searching blocks: " + e.getMessage());
                return;
            }
            matches.sort(String::compareToIgnoreCase);
            if (matches.isEmpty()) {
                Util.msg(ctx, "&cNo blocks found containing: " + pattern);
                return;
            }
            Util.msg(ctx, "&aFound " + matches.size() + " blocks:");
            int shown = 0;
            for (String block : matches) {
                if (shown >= 30) {
                    Util.msg(ctx, "&7... and " + (matches.size() - shown) + " more");
                    break;
                }
                String highlighted = block.replaceAll("(?i)(" + pattern + ")", "&e$1&7");
                Util.msg(ctx, "&7- &f" + highlighted);
                ++shown;
            }
            Util.log("Full list of blocks matching '" + pattern + "':");
            for (String block : matches) {
                Util.log("  " + block);
            }
        }
    }
}

