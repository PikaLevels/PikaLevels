package com.notthatlonely.pikalevels.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public class PikaClient {
    public static class Result {
        public final String playerLevel;
        public final String guildDisplay;

        public Result(String playerLevel, String guildDisplay) {
            this.playerLevel = playerLevel;
            this.guildDisplay = guildDisplay;
        }
    }

    public static Result fetchData(String playerName) {
        try {
            String url = "https://stats.pika-network.net/api/profile/" + playerName;
            URL u = new URL(url);
            HttpsURLConnection conn = (HttpsURLConnection) u.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            if (conn.getResponseCode() != 200) return null;

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

            return new Result(level, guild);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
