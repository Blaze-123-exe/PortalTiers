package com.portaltiers.tagger.model;

import java.util.HashMap;
import java.util.Map;

/**
 * One entry from the Portal rankings API ({@code /api/rankings}).
 * <p>
 * Example JSON:
 * <pre>
 * {
 *   "discordId": "1155804161418461205",
 *   "minecraftUsername": "sanatanisam",
 *   "region": "AS",
 *   "ranks": { "Sword": "HT3", "NethOP": "HT3" },
 *   "overallPoints": 44,
 *   "overallTier": "Novice"
 * }
 * </pre>
 * The {@code ranks} map is keyed by gamemode (e.g. {@code "Sword"}) with a tier
 * string value (e.g. {@code "HT3"}).
 */
public class PlayerRanking {
    public String discordId;
    public String minecraftUsername;
    public String region;
    public Map<String, String> ranks = new HashMap<>();
    public int overallPoints;
    public String overallTier;

    /** Returns the raw tier string (e.g. "HT3") for the given gamemode, or null. */
    public String getTier(GameMode mode) {
        if (ranks == null || mode == null) return null;
        // direct key first
        String value = ranks.get(mode.apiKey());
        if (value != null) return value;
        // case-insensitive fallback
        for (Map.Entry<String, String> entry : ranks.entrySet()) {
            if (GameMode.fromKey(entry.getKey()) == mode) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean hasAnyTier() {
        return ranks != null && !ranks.isEmpty();
    }
}
