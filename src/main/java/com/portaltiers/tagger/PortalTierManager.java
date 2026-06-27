package com.portaltiers.tagger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.portaltiers.tagger.config.PortalConfig;
import com.portaltiers.tagger.model.GameMode;
import com.portaltiers.tagger.model.PlayerRanking;
import com.portaltiers.tagger.util.Http;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central state for Portal Tier Tagger: fetches and caches the rankings list
 * from the Portal API and builds the coloured tier badges shown in-game.
 */
public final class PortalTierManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("PortalTierTagger");
    public static final Identifier ICON_FONT = Identifier.of("portaltiertagger", "icons");

    private static final Gson GSON = new Gson();

    /** Single daemon thread for blocking HTTP, so we never spam the network or block the game. */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PortalTierTagger-fetch");
        t.setDaemon(true);
        return t;
    });

    /** username (lowercase) -> ranking */
    private static final Map<String, PlayerRanking> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    /** Bumped on every successful refresh; mixins use it to invalidate per-entry caches. */
    public static volatile int cacheVersion = 0;
    public static volatile boolean loaded = false;
    /** When the last fetch was *attempted* (used for retry back-off). */
    private static volatile long lastAttemptMillis = 0L;
    /** Consecutive failures since the last success (for log throttling). */
    private static volatile int failureStreak = 0;

    private PortalTierManager() {}

    /** A resolved tier to display: which gamemode and the raw tier code. */
    public record DisplayedTier(GameMode mode, String tierCode) {}

    // ------------------------------------------------------------------
    // Networking
    // ------------------------------------------------------------------

    /**
     * Refreshes the cache when due. Uses back-off so a failing endpoint is retried
     * about once a minute (NOT every tick), and refreshed on the configured interval
     * once data has loaded successfully.
     */
    public static void maybeRefresh() {
        long now = System.currentTimeMillis();
        int intervalMin = Math.max(1, PortalConfig.get().refreshIntervalMinutes);
        long wait = loaded ? intervalMin * 60_000L : 60_000L; // retry ~1/min until first success
        if (now - lastAttemptMillis >= wait) {
            refreshNow();
        }
    }

    /** Forces a refresh of the rankings cache on a background daemon thread. */
    public static void refreshNow() {
        if (!fetching.compareAndSet(false, true)) return; // already fetching
        lastAttemptMillis = System.currentTimeMillis();
        final String url = PortalConfig.get().apiUrl;

        EXEC.submit(() -> {
            try {
                String body = Http.get(url);
                ingest(body);
                failureStreak = 0;
            } catch (Exception e) {
                if (failureStreak == 0) {
                    LOGGER.warn("Failed to fetch Portal rankings from {} : {}", url, e.toString());
                    notifyPlayer("§c[Portal] Could not load tiers — retrying every minute…");
                } else if (failureStreak % 20 == 0) {
                    LOGGER.warn("Still failing to fetch Portal rankings ({} attempts): {}",
                            failureStreak, e.toString());
                }
                failureStreak++;
            } finally {
                fetching.set(false);
            }
        });
    }

    /** Sends a one-line message to the player if they are in-game (thread-safe). */
    private static void notifyPlayer(String legacyText) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(legacyText), false);
            }
        });
    }

    private static void ingest(String body) {
        try {
            List<PlayerRanking> list = GSON.fromJson(
                    body, new TypeToken<List<PlayerRanking>>() {}.getType());
            if (list == null) return;

            Map<String, PlayerRanking> next = new ConcurrentHashMap<>();
            for (PlayerRanking pr : list) {
                if (pr == null || pr.minecraftUsername == null) continue;
                String key = pr.minecraftUsername.toLowerCase(Locale.ROOT);
                if (key.equals("unknown")) continue;
                next.put(key, pr);
            }

            CACHE.clear();
            CACHE.putAll(next);
            boolean firstLoad = !loaded;
            loaded = true;
            cacheVersion++;
            LOGGER.info("Loaded {} Portal player rankings", CACHE.size());
            if (firstLoad) {
                notifyPlayer("§a[Portal] Loaded " + CACHE.size() + " player tiers!");
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Portal rankings", e);
        }
    }

    public static void clearCache() {
        CACHE.clear();
        loaded = false;
        cacheVersion++;
    }

    public static int size() {
        return CACHE.size();
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    public static PlayerRanking lookup(String username) {
        if (username == null) return null;
        return CACHE.get(username.toLowerCase(Locale.ROOT));
    }

    /** Resolves which tier to display for a username, honouring the highest-tier mode. */
    public static DisplayedTier resolve(String username) {
        PlayerRanking pr = lookup(username);
        if (pr == null || !pr.hasAnyTier()) return null;

        PortalConfig cfg = PortalConfig.get();
        GameMode selected = cfg.gamemode;
        String selTier = pr.getTier(selected);

        switch (cfg.highestMode) {
            case ALWAYS -> {
                DisplayedTier best = highest(pr);
                return best != null ? best : (selTier != null ? new DisplayedTier(selected, selTier) : null);
            }
            case IF_NONE -> {
                if (selTier != null) return new DisplayedTier(selected, selTier);
                return highest(pr);
            }
            default -> { // NEVER
                return selTier != null ? new DisplayedTier(selected, selTier) : null;
            }
        }
    }

    /** Finds the player's single best tier across all gamemodes. */
    public static DisplayedTier highest(PlayerRanking pr) {
        DisplayedTier best = null;
        int bestValue = Integer.MAX_VALUE;
        for (Map.Entry<String, String> e : pr.ranks.entrySet()) {
            GameMode mode = GameMode.fromKey(e.getKey());
            if (mode == null) continue;
            int v = tierValue(e.getValue());
            if (v < bestValue) {
                bestValue = v;
                best = new DisplayedTier(mode, e.getValue());
            }
        }
        return best;
    }

    /** Lower is better. HT1 -> 2, LT1 -> 3, HT2 -> 4, ... LT5 -> 11. */
    public static int tierValue(String tier) {
        if (tier == null || tier.length() < 3) return Integer.MAX_VALUE;
        String t = tier.toUpperCase(Locale.ROOT).replace("R", "");
        boolean low = t.startsWith("L");
        int num;
        try {
            num = Integer.parseInt(t.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
        return num * 2 + (low ? 1 : 0);
    }

    /** Points awarded for a tier code (used in the player info command). */
    public static int tierPoints(String tier) {
        if (tier == null) return 0;
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "HT1" -> 60;
            case "LT1" -> 45;
            case "HT2" -> 30;
            case "LT2" -> 20;
            case "HT3" -> 10;
            case "LT3" -> 6;
            case "HT4" -> 4;
            case "LT4" -> 3;
            case "HT5" -> 2;
            case "LT5" -> 1;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Text building
    // ------------------------------------------------------------------

    /** Builds the coloured badge (icon + tier code) for a displayed tier. */
    public static MutableText buildBadge(DisplayedTier dt) {
        PortalConfig cfg = PortalConfig.get();
        MutableText badge = Text.empty();

        if (cfg.showIcons) {
            badge.append(Text.literal(String.valueOf(dt.mode().iconChar()))
                    .setStyle(Style.EMPTY.withFont(ICON_FONT).withColor(dt.mode().iconColor())));
            badge.append(Text.literal(" "));
        }

        String label = cfg.useBrackets ? "[" + dt.tierCode() + "]" : dt.tierCode();
        int color = cfg.getTierColor(dt.tierCode());
        badge.append(Text.literal(label).setStyle(Style.EMPTY.withColor(color)));
        return badge;
    }

    /** Prepends the tier badge (+ separator) to a player's display name. */
    public static Text appendTier(String username, Text original) {
        DisplayedTier dt = resolve(username);
        if (dt == null) return original;

        MutableText result = buildBadge(dt);
        result.append(Text.literal(PortalConfig.get().separator).formatted(Formatting.GRAY));
        result.append(original);
        return result;
    }

    // ------------------------------------------------------------------
    // Chat deep replacement
    // ------------------------------------------------------------------

    private static Set<String> onlineNames() {
        Set<String> names = new HashSet<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return names;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            if (e.getProfile() != null && e.getProfile().getName() != null) {
                names.add(e.getProfile().getName());
            }
        }
        return names;
    }

    /** Walks a chat message tree and prepends tier badges before player names. */
    public static Text deepReplace(Text message) {
        if (message == null) return null;
        Set<String> names = onlineNames();
        if (names.isEmpty()) return message;
        return replaceNode(message, names);
    }

    private static Text replaceNode(Text node, Set<String> names) {
        MutableText self = node.copyContentOnly();
        self.setStyle(node.getStyle());

        String content = self.getString();
        String trimmed = content.trim();

        MutableText rebuilt;
        if (!trimmed.isEmpty() && names.contains(trimmed)) {
            DisplayedTier dt = resolve(trimmed);
            if (dt != null) {
                rebuilt = buildBadge(dt);
                rebuilt.append(Text.literal(PortalConfig.get().separator).formatted(Formatting.GRAY));
                rebuilt.append(self);
            } else {
                rebuilt = self;
            }
        } else {
            rebuilt = self;
        }

        for (Text sibling : node.getSiblings()) {
            rebuilt.append(replaceNode(sibling, names));
        }
        return rebuilt;
    }
}
