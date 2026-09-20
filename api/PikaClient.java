package com.notthatlonely.pikalevels.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class PikaClient {

    /*
     * =========================================================
     * RESULT
     * =========================================================
     */

    public static class Result {

        public final String playerLevel;
        public final String guildDisplay;
        public final String rankName;
        public final int rankColor;
        public final boolean nick;

        public final double kd;
        public final double fkdr;
        public final double wlr;
        public final int hws;

        public Result(
                String playerLevel,
                String guildDisplay,
                String rankName,
                int rankColor,
                boolean nick,
                double kd,
                double fkdr,
                double wlr,
                int hws
        ) {

            this.playerLevel = playerLevel;
            this.guildDisplay = guildDisplay;
            this.rankName = rankName;
            this.rankColor = rankColor;
            this.nick = nick;

            this.kd = kd;
            this.fkdr = fkdr;
            this.wlr = wlr;
            this.hws = hws;
        }
    }

    /*
     * Special value returned by get() when the PROFILE
     * endpoint returns HTTP 404.
     *
     * This is deliberately different from null because
     * null means the request simply failed.
     */
    private static final String NICK_RESPONSE =
            "__PIKA_LEVELS_NICK__";

    /*
     * =========================================================
     * FETCH PLAYER DATA
     * =========================================================
     */

    public static Result fetchData(
            String playerName
    ) {

        try {

            String encodedName =
                    URLEncoder.encode(
                            playerName,
                            "UTF-8"
                    );

            /*
             * =================================================
             * PROFILE API
             * =================================================
             */

            String profileUrl =
                    "https://stats.pika-network.net/api/profile/"
                            + encodedName;

            String profileResponse =
                    get(profileUrl);

            /*
             * Request failed.
             */
            if (profileResponse == null) {
                return null;
            }

            /*
             * =================================================
             * NICK DETECTION
             * =================================================
             *
             * The Pika profile endpoint returned HTTP 404.
             *
             * In your testing, nicked players return 404.
             */

            if (NICK_RESPONSE.equals(profileResponse)) {

                return new Result(
                        "NICK",
                        "N/A",
                        null,
                        0xAA00FF,
                        true,
                        0.0,
                        0.0,
                        0.0,
                        0
                );
            }

            /*
             * =================================================
             * PARSE PROFILE
             * =================================================
             */

            JsonObject root =
                    new JsonParser()
                            .parse(profileResponse)
                            .getAsJsonObject();

            /*
             * =================================================
             * LEVEL
             * =================================================
             */

            String level =
                    "N/A";

            if (root.has("rank") &&
                    root.get("rank").isJsonObject()) {

                JsonObject rank =
                        root.getAsJsonObject("rank");

                if (rank.has("level") &&
                        !rank.get("level").isJsonNull()) {

                    level =
                            String.valueOf(
                                    rank.get("level").getAsInt()
                            );
                }
            }

            /*
             * =================================================
             * GUILD
             * =================================================
             */

            String guild =
                    "N/A";

            if (root.has("clan") &&
                    !root.get("clan").isJsonNull() &&
                    root.get("clan").isJsonObject()) {

                JsonObject clan =
                        root.getAsJsonObject("clan");

                /*
                 * Prefer guild tag.
                 */
                if (clan.has("tag") &&
                        !clan.get("tag").isJsonNull() &&
                        !clan.get("tag")
                                .getAsString()
                                .trim()
                                .isEmpty()) {

                    guild =
                            clan.get("tag").getAsString();

                } else if (
                        clan.has("name") &&
                        !clan.get("name").isJsonNull() &&
                        !clan.get("name")
                                .getAsString()
                                .trim()
                                .isEmpty()
                ) {

                    guild =
                            clan.get("name").getAsString();
                }
            }

            /*
             * =================================================
             * RANK
             * =================================================
             */

            String rankName =
                    null;

            if (root.has("ranks") &&
                    root.get("ranks").isJsonArray()) {

                for (JsonElement element :
                        root.getAsJsonArray("ranks")) {

                    if (!element.isJsonObject()) {
                        continue;
                    }

                    JsonObject rank =
                            element.getAsJsonObject();

                    if (!rank.has("displayName") ||
                            rank.get("displayName").isJsonNull()) {

                        continue;
                    }

                    String candidate =
                            rank.get("displayName")
                                    .getAsString();

                    String lower =
                            candidate.toLowerCase(
                                    Locale.ENGLISH
                            );

                    /*
                     * Only use the ranks we want.
                     */
                    if (lower.equals("titan") ||
                            lower.equals("elite") ||
                            lower.equals("vip")) {

                        rankName =
                                candidate;

                        break;
                    }
                }
            }

            int rankColor =
                    getColorForRank(rankName);

            /*
             * =================================================
             * BEDWARS STATISTICS
             * =================================================
             */

            double kd =
                    0.0;

            double fkdr =
                    0.0;

            double wlr =
                    0.0;

            int hws =
                    0;

            try {

                String bedwarsUrl =
                        "https://stats.pika-network.net/api/profile/"
                                + encodedName
                                + "/leaderboard?type=bedwars"
                                + "&interval=total"
                                + "&mode=ALL_MODES";

                String bedwarsResponse =
                        get(bedwarsUrl);

                /*
                 * A stats request failing does NOT mean
                 * the player is nicked.
                 */
                if (bedwarsResponse != null &&
                        !NICK_RESPONSE.equals(bedwarsResponse)) {

                    JsonObject stats =
                            new JsonParser()
                                    .parse(bedwarsResponse)
                                    .getAsJsonObject();

                    int kills =
                            getPlayerStat(
                                    stats,
                                    "Kills"
                            );

                    int deaths =
                            getPlayerStat(
                                    stats,
                                    "Deaths"
                            );

                    int finalKills =
                            getPlayerStat(
                                    stats,
                                    "Final kills"
                            );

                    int finalDeaths =
                            getPlayerStat(
                                    stats,
                                    "Final deaths"
                            );

                    int wins =
                            getPlayerStat(
                                    stats,
                                    "Wins"
                            );

                    int losses =
                            getPlayerStat(
                                    stats,
                                    "Losses"
                            );

                    hws =
                            getPlayerStat(
                                    stats,
                                    "Highest winstreak reached"
                            );

                    kd =
                            ratio(
                                    kills,
                                    deaths
                            );

                    fkdr =
                            ratio(
                                    finalKills,
                                    finalDeaths
                            );

                    wlr =
                            ratio(
                                    wins,
                                    losses
                            );
                }

            } catch (Exception ignored) {
                /*
                 * Keep default statistics.
                 */
            }

            /*
             * =================================================
             * NORMAL RESULT
             * =================================================
             */

            return new Result(
                    level,
                    guild,
                    rankName,
                    rankColor,
                    false,
                    kd,
                    fkdr,
                    wlr,
                    hws
            );

        } catch (Exception e) {

            System.out.println(
                    "[PikaLevels] Failed to fetch "
                            + playerName
                            + ": "
                            + e.getMessage()
            );

            return null;
        }
    }

    /*
     * =========================================================
     * HTTP GET
     * =========================================================
     *
     * 404 is handled specially ONLY by fetchData().
     *
     * Other callers receive null for errors.
     */

    private static String get(
            String urlString
    ) {

        HttpsURLConnection conn =
                null;

        try {

            URL url =
                    new URL(urlString);

            conn =
                    (HttpsURLConnection)
                            url.openConnection();

            conn.setRequestMethod(
                    "GET"
            );

            conn.setConnectTimeout(
                    5000
            );

            conn.setReadTimeout(
                    5000
            );

            conn.setRequestProperty(
                    "User-Agent",
                    "PikaLevels/1.0"
            );

            int responseCode =
                    conn.getResponseCode();

            /*
             * =================================================
             * 404 = NICK
             * =================================================
             */

            if (responseCode == 404) {

                return NICK_RESPONSE;
            }

            /*
             * =================================================
             * RATE LIMIT
             * =================================================
             */

            if (responseCode == 429) {

                System.out.println(
                        "[PikaLevels] Pika API rate limit reached."
                );

                return null;
            }

            /*
             * =================================================
             * OTHER HTTP ERRORS
             * =================================================
             */

            if (responseCode != 200) {

                System.out.println(
                        "[PikaLevels] API returned HTTP "
                                + responseCode
                );

                return null;
            }

            /*
             * =================================================
             * READ RESPONSE
             * =================================================
             */

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    conn.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder result =
                    new StringBuilder();

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                result.append(line);
            }

            reader.close();

            return result.toString();

        } catch (Exception e) {

            System.out.println(
                    "[PikaLevels] API request failed: "
                            + e.getMessage()
            );

            return null;

        } finally {

            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /*
     * =========================================================
     * GET PLAYER STAT
     * =========================================================
     */

    private static int getPlayerStat(
            JsonObject stats,
            String statName
    ) {

        try {

            if (!stats.has(statName) ||
                    !stats.get(statName).isJsonObject()) {

                return 0;
            }

            JsonObject stat =
                    stats.getAsJsonObject(statName);

            if (!stat.has("entries") ||
                    !stat.get("entries").isJsonArray()) {

                return 0;
            }

            if (stat.getAsJsonArray("entries")
                    .size() == 0) {

                return 0;
            }

            JsonObject entry =
                    stat.getAsJsonArray("entries")
                            .get(0)
                            .getAsJsonObject();

            if (!entry.has("value") ||
                    entry.get("value").isJsonNull()) {

                return 0;
            }

            return Integer.parseInt(
                    entry.get("value")
                            .getAsString()
            );

        } catch (Exception e) {

            return 0;
        }
    }

    /*
     * =========================================================
     * RATIO
     * =========================================================
     */

    private static double ratio(
            int numerator,
            int denominator
    ) {

        if (denominator <= 0) {

            return numerator > 0
                    ? numerator
                    : 0.0;
        }

        return (double) numerator
                / (double) denominator;
    }

    /*
     * =========================================================
     * RANK COLOR
     * =========================================================
     */

    private static int getColorForRank(
            String rankName
    ) {

        if (rankName == null) {
            return 0xAAAAAA;
        }

        switch (
                rankName.toLowerCase(
                        Locale.ENGLISH
                )
        ) {

            case "titan":
                return 0xFFFF55;

            case "elite":
                return 0x55FFFF;

            case "vip":
                return 0x55FF55;

            default:
                return 0xAAAAAA;
        }
    }
}
