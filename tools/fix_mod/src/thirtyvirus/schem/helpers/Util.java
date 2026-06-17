/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.hypixel.hytale.component.Ref
 *  com.hypixel.hytale.component.Store
 *  com.hypixel.hytale.math.vector.Location
 *  com.hypixel.hytale.math.vector.Transform
 *  com.hypixel.hytale.math.vector.Vector3d
 *  com.hypixel.hytale.protocol.SoundCategory
 *  com.hypixel.hytale.server.core.HytaleServer
 *  com.hypixel.hytale.server.core.Message
 *  com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
 *  com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
 *  com.hypixel.hytale.server.core.command.system.CommandContext
 *  com.hypixel.hytale.server.core.command.system.CommandSender
 *  com.hypixel.hytale.server.core.entity.entities.Player
 *  com.hypixel.hytale.server.core.universe.PlayerRef
 *  com.hypixel.hytale.server.core.universe.Universe
 *  com.hypixel.hytale.server.core.universe.world.SoundUtil
 *  com.hypixel.hytale.server.core.universe.world.World
 *  com.hypixel.hytale.server.core.universe.world.storage.EntityStore
 *  javax.annotation.Nonnull
 *  javax.annotation.Nullable
 */
package thirtyvirus.schem.helpers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import thirtyvirus.schem.SchematicImporter;

public final class Util {
    public static final Color BLACK = new Color(0);
    public static final Color DARK_BLUE = new Color(170);
    public static final Color DARK_GREEN = new Color(43520);
    public static final Color DARK_AQUA = new Color(43690);
    public static final Color DARK_RED = new Color(0xAA0000);
    public static final Color DARK_PURPLE = new Color(0xAA00AA);
    public static final Color GOLD = new Color(0xFFAA00);
    public static final Color GRAY = new Color(0xAAAAAA);
    public static final Color DARK_GRAY = new Color(0x555555);
    public static final Color BLUE = new Color(0x5555FF);
    public static final Color GREEN = new Color(0x55FF55);
    public static final Color AQUA = new Color(0x55FFFF);
    public static final Color RED = new Color(0xFF5555);
    public static final Color LIGHT_PURPLE = new Color(0xFF55FF);
    public static final Color YELLOW = new Color(0xFFFF55);
    public static final Color WHITE = new Color(0xFFFFFF);
    public static final char SECTION = '\u00a7';
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<String, Long>();
    private static final Map<PlayerRef, Map<String, Object>> playerData = new ConcurrentHashMap<PlayerRef, Map<String, Object>>();
    private static final String VALID_CODES = "0123456789abcdefklmnorABCDEFKLMNOR";
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");
    public static final String SEPARATOR = "\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501";

    private Util() {
    }

    public static void log(String msg) {
        Util.log(msg, false);
    }

    public static void log(String msg, boolean warn) {
        String prefix = "[HSI] " + (warn ? "[WARN] " : "");
        System.out.println(prefix + msg);
    }

    public static void schedule(Runnable task, int ticks) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(task, (long)ticks * 50L, TimeUnit.MILLISECONDS);
    }

    public static ScheduledFuture<?> repeat(Runnable task, int delayTicks, int periodTicks) {
        return HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(task, (long)delayTicks * 50L, (long)periodTicks * 50L, TimeUnit.MILLISECONDS);
    }

    public static boolean cooldown(String id, int ticks) {
        long duration;
        long last;
        long now = System.currentTimeMillis();
        if (now - (last = cooldowns.getOrDefault(id, 0L).longValue()) >= (duration = (long)ticks * 50L)) {
            cooldowns.put(id, now);
            return true;
        }
        return false;
    }

    public static int remaining(String id, int totalTicks) {
        long duration;
        long last;
        long now = System.currentTimeMillis();
        long elapsed = now - (last = cooldowns.getOrDefault(id, now).longValue());
        return elapsed >= (duration = (long)totalTicks * 50L) ? 0 : (int)((duration - elapsed) / 50L);
    }

    public static void clearCooldown(String id) {
        cooldowns.remove(id);
    }

    public static void clearAllCooldowns() {
        cooldowns.clear();
    }

    public static void setData(PlayerRef player, String key, Object value) {
        playerData.computeIfAbsent(player, k -> new ConcurrentHashMap()).put(key, value);
    }

    @Nullable
    public static Object getData(PlayerRef player, String key) {
        Map<String, Object> data = playerData.get(player);
        return data != null ? data.get(key) : null;
    }

    public static int getDataInt(PlayerRef player, String key) {
        Object val = Util.getData(player, key);
        return val instanceof Number ? ((Number)val).intValue() : 0;
    }

    public static String getDataString(PlayerRef player, String key) {
        Object val = Util.getData(player, key);
        return val != null ? val.toString() : "";
    }

    public static boolean getDataBool(PlayerRef player, String key) {
        Object val = Util.getData(player, key);
        return val instanceof Boolean && (Boolean)val != false;
    }

    public static void clearData(PlayerRef player) {
        playerData.remove(player);
    }

    public static void clearAllData() {
        playerData.clear();
    }

    public static Color fromCode(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> BLACK;
            case '1' -> DARK_BLUE;
            case '2' -> DARK_GREEN;
            case '3' -> DARK_AQUA;
            case '4' -> DARK_RED;
            case '5' -> DARK_PURPLE;
            case '6' -> GOLD;
            case '7' -> GRAY;
            case '8' -> DARK_GRAY;
            case '9' -> BLUE;
            case 'a' -> GREEN;
            case 'b' -> AQUA;
            case 'c' -> RED;
            case 'd' -> LIGHT_PURPLE;
            case 'e' -> YELLOW;
            default -> WHITE;
        };
    }

    public static char toCode(Color color) {
        if (color == null) {
            return 'f';
        }
        int rgb = color.getRGB() & 0xFFFFFF;
        return switch (rgb) {
            case 0 -> '0';
            case 170 -> '1';
            case 43520 -> '2';
            case 43690 -> '3';
            case 0xAA0000 -> '4';
            case 0xAA00AA -> '5';
            case 0xFFAA00 -> '6';
            case 0xAAAAAA -> '7';
            case 0x555555 -> '8';
            case 0x5555FF -> '9';
            case 0x55FF55 -> 'a';
            case 0x55FFFF -> 'b';
            case 0xFF5555 -> 'c';
            case 0xFF55FF -> 'd';
            case 0xFFFF55 -> 'e';
            default -> 'f';
        };
    }

    public static String toHex(Color c) {
        return c == null ? "#ffffff" : String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static Color fromHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return WHITE;
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            return new Color(Integer.parseInt(hex, 16));
        }
        catch (Exception e) {
            return WHITE;
        }
    }

    public static String toCodeString(Color c) {
        return String.valueOf('\u00a7') + Util.toCode(c);
    }

    public static String colorize(@Nullable String text) {
        if (text == null || !text.contains("&")) {
            return text;
        }
        char[] chars = text.toCharArray();
        StringBuilder sb = new StringBuilder(chars.length);
        for (int i = 0; i < chars.length; ++i) {
            if (chars[i] == '&' && i + 1 < chars.length && VALID_CODES.indexOf(chars[i + 1]) != -1) {
                sb.append('\u00a7').append(Character.toLowerCase(chars[++i]));
                continue;
            }
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    public static String strip(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[&\u00a7][0-9a-fk-orA-FK-OR]", "");
    }

    public static Message toMessage(String text) {
        if (text == null || text.isEmpty()) {
            return Message.raw((String)"");
        }
        ArrayList<Message> segments = new ArrayList<Message>();
        StringBuilder current = new StringBuilder();
        Color color = null;
        for (int i = 0; i < text.length(); ++i) {
            if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f') {
                    if (current.length() > 0) {
                        Message seg = Message.raw((String)current.toString());
                        if (color != null) {
                            seg = seg.color(color);
                        }
                        segments.add(seg);
                        current = new StringBuilder();
                    }
                    color = Util.fromCode(code);
                    ++i;
                    continue;
                }
                if (Character.isLetterOrDigit(code)) {
                    ++i;
                    continue;
                }
            }
            current.append(text.charAt(i));
        }
        if (current.length() > 0) {
            Message seg = Message.raw((String)current.toString());
            if (color != null) {
                seg = seg.color(color);
            }
            segments.add(seg);
        }
        if (segments.isEmpty()) {
            return Message.raw((String)"");
        }
        if (segments.size() == 1) {
            return (Message)segments.get(0);
        }
        return Message.join((Message[])segments.toArray(new Message[0]));
    }

    public static void sound(Sound sound, PlayerRef player) {
        Util.sound(sound, player, 1.0f, 1.0f);
    }

    public static void sound(Sound sound, PlayerRef player, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }
        int idx = SoundEvent.getAssetMap().getIndex((Object)sound.getId());
        if (idx != 0) {
            SoundUtil.playSoundEvent2dToPlayer((PlayerRef)player, (int)idx, (SoundCategory)SoundCategory.SFX, (float)volume, (float)pitch);
        }
    }

    public static void msg(PlayerRef player, String message) {
        if (player == null || message == null) {
            return;
        }
        Message prefix = Util.toMessage(Util.colorize(SchematicImporter.prefix));
        Message content = Util.toMessage(Util.colorize(message));
        player.sendMessage(Message.join((Message[])new Message[]{prefix, content}));
    }

    public static void msg(PlayerRef player, List<String> messages) {
        if (messages != null) {
            messages.forEach(m -> Util.msg(player, m));
        }
    }

    public static void msg(CommandSender sender, String message) {
        if (sender instanceof PlayerRef) {
            PlayerRef p = (PlayerRef)sender;
            Util.msg(p, message);
        } else if (sender != null) {
            Util.log(Util.strip(message));
        }
    }

    public static void msg(CommandContext ctx, String message) {
        if (ctx == null || message == null) {
            return;
        }
        ctx.sendMessage(Util.toMessage(Util.colorize(SchematicImporter.prefix + message)));
    }

    public static void msg(CommandContext ctx, List<String> messages) {
        if (ctx == null || messages == null) {
            return;
        }
        for (String m : messages) {
            Util.msg(ctx, m);
        }
    }

    public static void warn(PlayerRef player, String message) {
        if (player == null || message == null) {
            return;
        }
        Util.sound(Sound.ERROR, player);
        boolean hasColor = message.startsWith("\u00a7") || message.startsWith("&");
        Util.msg(player, (String)(hasColor ? message : "&c" + message));
    }

    public static void warn(CommandSender sender, String message) {
        if (sender instanceof PlayerRef) {
            PlayerRef p = (PlayerRef)sender;
            Util.warn(p, message);
        } else if (sender != null) {
            Util.log("[WARN] " + Util.strip(message), true);
        }
    }

    @Nullable
    public static PlayerRef ref(CommandSender sender) {
        if (sender == null) {
            return null;
        }
        if (sender instanceof PlayerRef pr) { return pr; } Ref ref = ((Player)sender).getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store store = ref.getStore();
        if (store.isInThread()) {
            return (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
        }
        World world = ((EntityStore)store.getExternalData()).getWorld();
        return CompletableFuture.supplyAsync(() -> (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType()), (Executor)world).join();
    }

    @Nullable
    public static Vector3d pos(PlayerRef player) {
        if (sender == null) {
            return null;
        }
        Transform transform = player.getTransform();
        return transform != null ? transform.getPosition() : null;
    }

    @Nullable
    public static World world(PlayerRef player) {
        if (sender == null) {
            return null;
        }
        UUID worldUuid = player.getWorldUuid();
        return worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
    }

    @Nullable
    public static String getBlockAt(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        try {
            int blockIndex = world.getBlock(x, y, z);
            if (blockIndex <= 0) {
                return "air";
            }
            BlockType bt = (BlockType)BlockType.getAssetMap().getAsset(blockIndex);
            return bt != null ? bt.getId() : "air";
        }
        catch (Exception e) {
            return null;
        }
    }

    public static String titleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_') {
                cap = true;
                sb.append(' ');
                continue;
            }
            if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    public static String duration(int ticks) {
        int s = ticks / 20;
        if (s < 60) {
            return s + "s";
        }
        int m = s / 60;
        s %= 60;
        if (m < 60) {
            return m + "m " + s + "s";
        }
        int h = m / 60;
        return h + "h " + (m %= 60) + "m";
    }

    public static String number(long n) {
        return String.format("%,d", n);
    }

    public static String decimal(double v, int dec) {
        return String.format("%." + dec + "f", v);
    }

    public static String loc(Location l) {
        if (l == null) {
            return "null";
        }
        Vector3d p = l.getPosition();
        return String.format("(%.1f, %.1f, %.1f)", p.getX(), p.getY(), p.getZ());
    }

    public static String loc(Vector3d p) {
        return p == null ? "null" : String.format("(%.1f, %.1f, %.1f)", p.getX(), p.getY(), p.getZ());
    }

    public static String block(int x, int y, int z) {
        return String.format("(%d, %d, %d)", x, y, z);
    }

    public static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static boolean validatePlayer(PlayerRef player, String context) {
        if (sender == null) {
            Util.log("Null player in " + context, true);
            return false;
        }
        return true;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int parseInt(String s, int defaultValue) {
        if (Util.isEmpty(s)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s.trim());
        }
        catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double parseDouble(String s, double defaultValue) {
        if (Util.isEmpty(s)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(s.trim());
        }
        catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static Msg msg(@Nullable String template) {
        return new Msg(template);
    }

    public static enum Sound {
        ERROR("SFX_Creative_Play_Error"),
        CLICK("SFX_Stone_Build");

        private final String id;

        private Sound(String id) {
            this.id = id;
        }

        public String getId() {
            return this.id;
        }
    }

    public static final class Msg {
        private final String template;
        private final Map<String, String> placeholders = new HashMap<String, String>();

        Msg(String template) {
            this.template = template != null ? template : "";
        }

        public Msg with(@Nonnull String key, @Nullable Object value) {
            this.placeholders.put(key.toLowerCase(), value != null ? value.toString() : "");
            return this;
        }

        public Msg player(@Nullable PlayerRef p) {
            return this.with("player", p != null ? p.getUsername() : "Unknown");
        }

        public Msg name(String n) {
            return this.with("name", n);
        }

        public Msg amount(Number n) {
            return this.with("amount", n);
        }

        public Msg count(int c) {
            return this.with("count", c);
        }

        public String format() {
            String result = this.template;
            if (!this.placeholders.isEmpty()) {
                Matcher m = PLACEHOLDER.matcher(result);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    String key = m.group(1).toLowerCase();
                    m.appendReplacement(sb, Matcher.quoteReplacement(this.placeholders.getOrDefault(key, m.group(0))));
                }
                m.appendTail(sb);
                result = sb.toString();
            }
            return Util.colorize(result);
        }

        public void send(@Nonnull PlayerRef player) {
            Util.msg(player, this.format());
        }

        public void warn(@Nonnull PlayerRef player) {
            Util.warn(player, this.format());
        }
    }

    public static final class C {
        public static final String BLACK = "\u00a70";
        public static final String DARK_BLUE = "\u00a71";
        public static final String DARK_GREEN = "\u00a72";
        public static final String DARK_AQUA = "\u00a73";
        public static final String DARK_RED = "\u00a74";
        public static final String DARK_PURPLE = "\u00a75";
        public static final String GOLD = "\u00a76";
        public static final String GRAY = "\u00a77";
        public static final String DARK_GRAY = "\u00a78";
        public static final String BLUE = "\u00a79";
        public static final String GREEN = "\u00a7a";
        public static final String AQUA = "\u00a7b";
        public static final String RED = "\u00a7c";
        public static final String LIGHT_PURPLE = "\u00a7d";
        public static final String YELLOW = "\u00a7e";
        public static final String WHITE = "\u00a7f";
        public static final String BOLD = "\u00a7l";
        public static final String ITALIC = "\u00a7o";
        public static final String UNDERLINE = "\u00a7n";
        public static final String STRIKE = "\u00a7m";
        public static final String MAGIC = "\u00a7k";
        public static final String RESET = "\u00a7r";

        private C() {
        }
    }
}

