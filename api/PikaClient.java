package com.notthatlonely.pikalevels.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class PikaClient {

    public static class Result {
        public final String playerLevel;
        public final String guildDisplay;
        public final String rankName; // e.g. "Titan", "Elite", "VIP", or null if none
        public final int rankColor; // color integer (e.g. 0xFFFFFF)
        public final boolean nick;  // true if player not found in API

        public Result(String playerLevel, String guildDisplay, String rankName, int rankColor, boolean nick) {
            this.playerLevel = playerLevel;
            this.guildDisplay = guildDisplay;
            this.rankName = rankName;
            this.rankColor = rankColor;
            this.nick = nick;
        }
    }

    public static Result fetchData(String playerName) {
        try {
            String url = "https://stats.pika-network.net/api/profile/" + playerName;
            URL u = new URL(url);
            HttpsURLConnection conn = (HttpsURLConnection) u.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 404) {
                // Player not found in API -> mark as nick
                return new Result("N/A", "N/A", null, 0xAAAAAA, true);
            }
            if (responseCode != 200) {
                return null; // Other error, treat as data not available yet
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JsonParser parser = new JsonParser();
            JsonObject root = parser.parse(sb.toString()).getAsJsonObject();

            // Default values
            String level = "N/A";
            String guild = "N/A";
            String rankName = null;
            int rankColor = 0xAAAAAA; // Default gray

            // Extract level
            if (root.has("rank")) {
                JsonObject rank = root.getAsJsonObject("rank");
                if (rank.has("level") && !rank.get("level").isJsonNull()) {
                    level = String.valueOf(rank.get("level").getAsInt());
                }
            }

            // Extract guild
            if (root.has("clan") && !root.get("clan").isJsonNull()) {
                JsonObject clan = root.getAsJsonObject("clan");

                if (clan.has("tag") && !clan.get("tag").getAsString().trim().isEmpty()) {
                    guild = clan.get("tag").getAsString();
                } else if (clan.has("name") && !clan.get("name").getAsString().trim().isEmpty()) {
                    guild = clan.get("name").getAsString();
                }
            }

            // Extract ranks
            if (root.has("ranks") && root.get("ranks").isJsonArray() && root.getAsJsonArray("ranks").size() > 0) {
                JsonObject firstRank = root.getAsJsonArray("ranks").get(0).getAsJsonObject();
                if (firstRank.has("displayName") && !firstRank.get("displayName").isJsonNull()) {
                    rankName = firstRank.get("displayName").getAsString();
                }
            }

            // Determine color based on rankName
            rankColor = getColorForRank(rankName);

            return new Result(level, guild, rankName, rankColor, false);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static int getColorForRank(String rankName) {
        if (rankName == null) return 0xAAAAAA; // Gray for non-ranked

        switch (rankName.toLowerCase()) {
            case "titan":
                return 0xFFFF55; // Yellow
            case "elite":
                return 0x55FFFF; // Cyan-ish
            case "vip":
                return 0x55FF55; // Green-ish
            default:
                return 0xAAAAAA; // Gray default
        }
    }
}
