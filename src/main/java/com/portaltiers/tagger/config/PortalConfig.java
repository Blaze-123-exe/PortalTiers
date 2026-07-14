package com.portaltiers.tagger.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.portaltiers.tagger.model.GameMode;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent client configuration for Pojav Tier Tagger.
 * Stored at {@code config/pojavtiertagger.json} (the old portal config is
 * superseded; delete it manually if you like).
 */
public class PortalConfig {   // class name kept as-is
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("pojavtiertagger.json");

    public enum HighestMode {
        NEVER,
        IF_NONE,
        ALWAYS
    }

    // --- general ---
    public boolean enabled = true;
    public boolean showIcons = true;
    public boolean showInNametag = true;
    public boolean showInTab = true;
    public boolean showInChat = true;
    public boolean useBrackets = false;

    // --- behaviour (currently ignored – the API now returns only best_tier) ---
    public GameMode gamemode = GameMode.SWORD;
    public HighestMode highestMode = HighestMode.IF_NONE;
    public int refreshIntervalMinutes = 30;

    // --- separator between the tier badge and the name ---
    public String separator = " | ";

    // --- colours, keyed by tier code (HT1..LT5) as 0xRRGGBB ints ---
    public Map<String, Integer> tierColors = defaultColors();

    public static Map<String, Integer> defaultColors() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("HT1", 0xFFFF55);
        m.put("HT2", 0xFF7070);
        m.put("HT3", 0xFF55FF);
        m.put("HT4", 0x55FFFF);
        m.put("HT5", 0x00AA00);
        m.put("LT1", 0x55FF55);
        m.put("LT2", 0x5555FF);
        m.put("LT3", 0x0000AA);
        m.put("LT4", 0xAAAAAA);
        m.put("LT5", 0x555555);
        return m;
    }

    public int getTierColor(String tierCode) {
        if (tierCode == null) return 0xD3D3D3;
        Integer c = tierColors.get(tierCode.toUpperCase());
        return c != null ? c : 0xD3D3D3;
    }

    // ------------------------------------------------------------------
    private static PortalConfig INSTANCE;

    public static PortalConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static PortalConfig load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH)) {
                    PortalConfig cfg = GSON.fromJson(reader, PortalConfig.class);
                    if (cfg != null) {
                        if (cfg.tierColors == null || cfg.tierColors.isEmpty()) {
                            cfg.tierColors = defaultColors();
                        }
                        if (cfg.gamemode == null) cfg.gamemode = GameMode.SWORD;
                        if (cfg.highestMode == null) cfg.highestMode = HighestMode.IF_NONE;
                        return cfg;
                    }
                }
            }
        } catch (Exception e) { }
        PortalConfig cfg = new PortalConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        INSTANCE = this;
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) { }
    }
}
