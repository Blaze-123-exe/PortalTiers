package com.portaltiers.tagger.model;

import java.util.HashMap;
import java.util.Map;

/**
 * One player entry from the PojavTiers API.
 * <p>
 * The primary data comes from {@code GET /api/overall}, which returns:
 * <pre>
 * {
 *   "rank": 1,
 *   "ign": "SomePlayer",
 *   "points": 810,
 *   "best_tier": "HT1",
 *   "title": "Combat Grandmaster",
 *   "region": "NA",
 *   "total_gamemodes": 9,
 *   "skin_url": "...",
 *   "retired": false
 * }
 * </pre>
 * The older portal fields (discordId, ranks, overallPoints, overallTier) are
 * kept for backward compatibility but are no longer populated by default.
 */
public class PlayerRanking {

    // ---------- New PojavTiers fields ----------
    public String bestTier;      // from "best_tier"
    public int points;           // from "points"
    public String region;        // from "region" (already existed, but now mapped from "region" in JSON)

    // ---------- Legacy fields (may be null/0/empty) ----------
    public String discordId;              // not provided by /api/overall
    public String minecraftUsername;      // still populated from "ign" → minecraftUsername
    public Map<String, String> ranks = new HashMap<>(); // per-gamemode tiers (empty with /api/overall)
    public int overallPoints;            // kept for old code, will be 0
    public String overallTier;           // kept for old code, will be null

    /**
     * Returns the raw tier string (e.g. "HT3") for the given gamemode, or
     * {@code null} if not ranked in that mode. Because the current overall API
     * does not include per‑gamemode data, this will always return null unless
     * you manually populate the {@code ranks} map via {@code /api/tiers/<ign>}.
     */
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

    /** Returns {@code true} if the player has any per‑gamemode tier data. */
    public boolean hasAnyTier() {
        return ranks != null && !ranks.isEmpty();
    }
}
