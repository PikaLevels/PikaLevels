package com.notthatlonely.pikalevels.render;

import com.notthatlonely.pikalevels.api.PikaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RenderHandler {

    private final Minecraft mc =
            Minecraft.getMinecraft();

    /*
     * =========================================================
     * CACHE
     * =========================================================
     */

    private final Map<String, PikaClient.Result> cache =
            new ConcurrentHashMap<>();

    /*
     * Last successful fetch time for each player.
     */
    private final Map<String, Long> lastFetch =
            new ConcurrentHashMap<>();

    /*
     * Players currently being fetched.
     *
     * This prevents duplicate API requests.
     */
    private final Map<String, Boolean> fetching =
            new ConcurrentHashMap<>();

    /*
     * Refresh every 120 seconds.
     */
    private static final long REFRESH_INTERVAL =
            TimeUnit.SECONDS.toMillis(120);

    private static boolean enabled = true;

    private static boolean leaderboardEnabled = false;

    /*
     * =========================================================
     * TOGGLES
     * =========================================================
     */

    public static void toggleEnabled() {

        enabled = !enabled;
    }

    public static boolean isEnabled() {

        return enabled;
    }

    public static void toggleLeaderboard() {

        leaderboardEnabled =
                !leaderboardEnabled;
    }

    public static boolean isLeaderboardEnabled() {

        return leaderboardEnabled;
    }

    /*
     * =========================================================
     * CONSTRUCTOR / DATA FETCHING
     * =========================================================
     */

    public RenderHandler() {

        new Thread(() -> {

            while (true) {

                try {

                    if (mc.theWorld != null &&
                            mc.thePlayer != null) {

                        for (Object obj :
                                mc.theWorld.playerEntities) {

                            if (!(obj instanceof EntityPlayer)) {
                                continue;
                            }

                            EntityPlayer other =
                                    (EntityPlayer) obj;

                            /*
                             * Don't fetch ourselves.
                             */
                            if (other == mc.thePlayer) {
                                continue;
                            }

                            String name =
                                    other.getName();

                            long now =
                                    System.currentTimeMillis();

                            /*
                             * Fetch if:
                             *
                             * 1. We have never fetched this player.
                             * OR
                             * 2. 120 seconds have passed.
                             */
                            boolean needsFetch =
                                    !cache.containsKey(name)
                                            ||
                                    now -
                                    lastFetch.getOrDefault(
                                            name,
                                            0L
                                    )
                                    >= REFRESH_INTERVAL;

                            if (!needsFetch) {
                                continue;
                            }

                            /*
                             * Don't start another request
                             * if one is already running.
                             */
                            if (fetching.containsKey(name)) {
                                continue;
                            }

                            /*
                             * Mark as currently fetching.
                             */
                            fetching.put(
                                    name,
                                    true
                            );

                            new Thread(() -> {

                                try {

                                    PikaClient.Result result =
                                            PikaClient.fetchData(name);

                                    /*
                                     * Result can also represent
                                     * a nicked player.
                                     *
                                     * For a 404:
                                     *
                                     * result.nick == true
                                     */
                                    if (result != null) {

                                        cache.put(
                                                name,
                                                result
                                        );

                                        /*
                                         * Only reset the
                                         * 120-second timer after
                                         * a successful result.
                                         */
                                        lastFetch.put(
                                                name,
                                                System.currentTimeMillis()
                                        );
                                    }

                                } catch (Exception ignored) {

                                } finally {

                                    /*
                                     * Request finished.
                                     */
                                    fetching.remove(name);
                                }

                            },
                            "PikaLevels-Fetch-" + name)
                                    .start();
                        }
                    }

                    /*
                     * Check players every 10 seconds.
                     *
                     * This is NOT an API request every 10 seconds.
                     * It only checks whether a player needs fetching.
                     */
                    Thread.sleep(10000);

                } catch (Exception ignored) {
                }
            }

        },
        "PikaLevels-FetchThread")
                .start();
    }

    /*
     * =========================================================
     * PLAYER NAMETAG
     * =========================================================
     */

    @SubscribeEvent
    public void onRenderPlayer(
            RenderLivingEvent.Specials.Pre event
    ) {

        if (!enabled) {
            return;
        }

        if (!(event.entity instanceof EntityOtherPlayerMP)) {
            return;
        }

        EntityOtherPlayerMP player =
                (EntityOtherPlayerMP) event.entity;

        /*
         * Don't render for sneaking or invisible players.
         */
        if (player.isSneaking() ||
                player.isInvisible()) {

            return;
        }

        String name =
                player.getName();

        PikaClient.Result result =
                cache.get(name);

        String level =
                "N/A";

        String guild =
                "N/A";

        int color =
                0xAAAAAA;

        /*
         * =====================================================
         * RESULT
         * =====================================================
         */

        if (result != null) {

            /*
             * 404 / NICK
             */
            if (result.nick) {

                level =
                        "NICK";

                color =
                        0xAA00FF;

            } else {

                level =
                        result.playerLevel != null
                                ? result.playerLevel
                                : "N/A";

                guild =
                        result.guildDisplay != null
                                ? result.guildDisplay
                                : "N/A";

                color =
                        result.rankColor;
            }
        }

        String text =
                "Lvl " + level + " | " + guild;

        renderText(
                text,
                event.x,
                event.y + player.height + 0.8,
                event.z,
                color
        );
    }

    /*
     * =========================================================
     * WORLD NAMETAG TEXT
     * =========================================================
     */

    private void renderText(
            String text,
            double x,
            double y,
            double z,
            int color
    ) {

        RenderManager renderManager =
                mc.getRenderManager();

        float viewerYaw =
                renderManager.playerViewY;

        float viewerPitch =
                renderManager.playerViewX;

        GlStateManager.pushMatrix();

        GlStateManager.translate(
                x,
                y + 0.3,
                z
        );

        GlStateManager.rotate(
                -viewerYaw,
                0,
                1,
                0
        );

        GlStateManager.rotate(
                viewerPitch,
                1,
                0,
                0
        );

        GlStateManager.scale(
                -0.025F,
                -0.025F,
                0.025F
        );

        GlStateManager.disableLighting();

        GlStateManager.disableDepth();

        int width =
                mc.fontRendererObj
                        .getStringWidth(text)
                        / 2;

        mc.fontRendererObj.drawString(
                text,
                -width,
                0,
                color
        );

        GlStateManager.enableDepth();

        GlStateManager.enableLighting();

        GlStateManager.popMatrix();
    }

    /*
     * =========================================================
     * LEADERBOARD OVERLAY
     * =========================================================
     */

    @SubscribeEvent
    public void onRenderOverlay(
            RenderGameOverlayEvent.Text event
    ) {

        if (!enabled ||
                !leaderboardEnabled ||
                mc.theWorld == null ||
                mc.thePlayer == null) {

            return;
        }

        List<PlayerStats> players =
                new ArrayList<>();

        /*
         * Collect all other players.
         */
        for (Object obj :
                mc.theWorld.playerEntities) {

            if (!(obj instanceof EntityOtherPlayerMP)) {
                continue;
            }

            EntityOtherPlayerMP player =
                    (EntityOtherPlayerMP) obj;

            String name =
                    player.getName();

            PikaClient.Result result =
                    cache.get(name);

            players.add(
                    new PlayerStats(
                            name,
                            result
                    )
            );
        }

        /*
         * Alphabetical order.
         */
        players.sort(
                (a, b) ->
                        a.name.compareToIgnoreCase(
                                b.name
                        )
        );

        /*
         * Maximum 16 players.
         */
        if (players.size() > 16) {

            players =
                    players.subList(
                            0,
                            16
                    );
        }

        drawLeaderboard(players);
    }

    /*
     * =========================================================
     * DRAW LEADERBOARD
     * =========================================================
     */

    private void drawLeaderboard(
            List<PlayerStats> players
    ) {

        /*
         * =====================================================
         * COLUMN WIDTHS
         * =====================================================
         */

        int playerWidth =
                90;

        int levelWidth =
                28;

        int kdWidth =
                35;

        int fkdrWidth =
                40;

        int wlrWidth =
                35;

        int hwsWidth =
                35;

        int tableWidth =
                playerWidth
                        + levelWidth
                        + kdWidth
                        + fkdrWidth
                        + wlrWidth
                        + hwsWidth;

        /*
         * =====================================================
         * HEIGHT
         * =====================================================
         */

        int lineHeight =
                mc.fontRendererObj.FONT_HEIGHT + 2;

        int headerHeight =
                lineHeight + 4;

        int tableHeight =
                headerHeight
                        + players.size() * lineHeight
                        + 10;

        /*
         * =====================================================
         * SCREEN SIZE
         * =====================================================
         */

        int screenHeight =
                mc.displayHeight
                        / mc.gameSettings.guiScale;

        /*
         * =====================================================
         * CENTER-LEFT POSITION
         * =====================================================
         *
         * X = 10
         *
         * Y = vertical center.
         */

        int x =
                10;

        int y =
                (screenHeight - tableHeight) / 2;

        /*
         * =====================================================
         * BACKGROUND
         * =====================================================
         */

        drawRect(
                x - 6,
                y - 6,
                x + tableWidth + 6,
                y + tableHeight + 6,
                0x88000000
        );

        /*
         * =====================================================
         * RED TOP BAR
         * =====================================================
         */

        int headerColor =
                0xFFAA0000;

        drawRect(
                x - 6,
                y - 6,
                x + tableWidth + 6,
                y + headerHeight,
                headerColor
        );

        /*
         * Dark red separator.
         */
        drawRect(
                x - 6,
                y + headerHeight,
                x + tableWidth + 6,
                y + headerHeight + 1,
                0xFF550000
        );

        /*
         * =====================================================
         * DASHED BORDER
         * =====================================================
         */

        drawThinBorder(
                x - 6,
                y - 6,
                x + tableWidth + 6,
                y + tableHeight + 6,
                0xFF000000
        );

        /*
         * =====================================================
         * HEADER TEXT
         * =====================================================
         */

        int headerTextColor =
                0xFFFFFFFF;

        drawColumn(
                "PLAYER",
                x,
                y,
                headerTextColor
        );

        drawColumn(
                "LVL",
                x + playerWidth,
                y,
                headerTextColor
        );

        drawColumn(
                "K/D",
                x
                        + playerWidth
                        + levelWidth,
                y,
                headerTextColor
        );

        drawColumn(
                "FKDR",
                x
                        + playerWidth
                        + levelWidth
                        + kdWidth,
                y,
                headerTextColor
        );

        drawColumn(
                "W/L",
                x
                        + playerWidth
                        + levelWidth
                        + kdWidth
                        + fkdrWidth,
                y,
                headerTextColor
        );

        drawColumn(
                "HWS",
                x
                        + playerWidth
                        + levelWidth
                        + kdWidth
                        + fkdrWidth
                        + wlrWidth,
                y,
                headerTextColor
        );

        /*
         * =====================================================
         * PLAYER ROWS
         * =====================================================
         */

        for (int i = 0;
             i < players.size();
             i++) {

            PlayerStats player =
                    players.get(i);

            int rowY =
                    y
                            + headerHeight
                            + i * lineHeight;

            drawPlayerRow(
                    player,
                    x,
                    rowY,
                    playerWidth,
                    levelWidth,
                    kdWidth,
                    fkdrWidth,
                    wlrWidth
            );
        }

        /*
         * =====================================================
         * CREDIT
         * =====================================================
         */

        drawCredit(
                x,
                y + tableHeight,
                tableWidth
        );
    }

    /*
     * =========================================================
     * DRAW PLAYER ROW
     * =========================================================
     */

    private void drawPlayerRow(
            PlayerStats player,
            int x,
            int y,
            int playerWidth,
            int levelWidth,
            int kdWidth,
            int fkdrWidth,
            int wlrWidth
    ) {

        PikaClient.Result result =
                player.result;

        int color =
                0xAAAAAA;

        String level =
                "N/A";

        String kd =
                "N/A";

        String fkdr =
                "N/A";

        String wlr =
                "N/A";

        String hws =
                "N/A";

        /*
         * =====================================================
         * RESULT
         * =====================================================
         */

        if (result != null) {

            /*
             * NICK
             */
            if (result.nick) {

                level =
                        "NICK";

                color =
                        0xAA00FF;

            } else {

                /*
                 * NORMAL PLAYER
                 */

                level =
                        result.playerLevel != null
                                ? result.playerLevel
                                : "N/A";

                kd =
                        formatStat(
                                result.kd
                        );

                fkdr =
                        formatStat(
                                result.fkdr
                        );

                wlr =
                        formatStat(
                                result.wlr
                        );

                hws =
                        String.valueOf(
                                result.hws
                        );

                color =
                        result.rankColor;
            }
        }

        /*
         * PLAYER
         */

        drawColumn(
                player.name,
                x,
                y,
                color
        );

        int offset =
                x + playerWidth;

        /*
         * LEVEL
         */

        drawColumn(
                level,
                offset,
                y,
                color
        );

        offset +=
                levelWidth;

        /*
         * K/D
         */

        drawColumn(
                kd,
                offset,
                y,
                color
        );

        offset +=
                kdWidth;

        /*
         * FKDR
         */

        drawColumn(
                fkdr,
                offset,
                y,
                color
        );

        offset +=
                fkdrWidth;

        /*
         * W/L
         */

        drawColumn(
                wlr,
                offset,
                y,
                color
        );

        offset +=
                wlrWidth;

        /*
         * HWS
         */

        drawColumn(
                hws,
                offset,
                y,
                color
        );
    }

    /*
     * =========================================================
     * FORMAT STAT
     * =========================================================
     */

    private String formatStat(
            double value
    ) {

        return String.format(
                Locale.US,
                "%.2f",
                value
        );
    }

    /*
     * =========================================================
     * DRAW COLUMN
     * =========================================================
     */

    private void drawColumn(
            String text,
            int x,
            int y,
            int color
    ) {

        GlStateManager.pushMatrix();

        GlStateManager.translate(
                x,
                y,
                0
        );

        GlStateManager.scale(
                0.8,
                0.8,
                1.0
        );

        mc.fontRendererObj.drawStringWithShadow(
                text,
                0,
                0,
                color
        );

        GlStateManager.popMatrix();
    }

    /*
     * =========================================================
     * CREDIT
     * =========================================================
     */

    private void drawCredit(
            int x,
            int bottomY,
            int tableWidth
    ) {

        String credit =
                "Made with \u2665 by NotThatLonely";

        int creditColor =
                0xFFAAAAAA;

        float scale =
                0.7F;

        int creditWidth =
                mc.fontRendererObj
                        .getStringWidth(credit);

        /*
         * Center of leaderboard.
         */
        int centerX =
                x + tableWidth / 2;

        /*
         * Convert position for scaled text.
         */
        int drawX =
                (int)
                        (
                                centerX / scale
                                        - creditWidth / 2.0F
                        );

        /*
         * Place it across the bottom border.
         */
        int drawY =
                (int)
                        (
                                (bottomY - 3)
                                        / scale
                        );

        GlStateManager.pushMatrix();

        GlStateManager.scale(
                scale,
                scale,
                1.0
        );

        mc.fontRendererObj.drawStringWithShadow(
                credit,
                drawX,
                drawY,
                creditColor
        );

        GlStateManager.popMatrix();
    }

    /*
     * =========================================================
     * DRAW RECTANGLE
     * =========================================================
     */

    private void drawRect(
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {

        net.minecraft.client.gui.Gui.drawRect(
                left,
                top,
                right,
                bottom,
                color
        );
    }

    /*
     * =========================================================
     * DASHED BORDER
     * =========================================================
     */

    private void drawThinBorder(
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {

        int step =
                4;

        /*
         * Top + bottom.
         */
        for (
                int x = left;
                x < right;
                x += step * 2
        ) {

            drawRect(
                    x,
                    top,
                    x + step,
                    top + 1,
                    color
            );

            drawRect(
                    x,
                    bottom - 1,
                    x + step,
                    bottom,
                    color
            );
        }

        /*
         * Left + right.
         */
        for (
                int y = top;
                y < bottom;
                y += step * 2
        ) {

            drawRect(
                    left,
                    y,
                    left + 1,
                    y + step,
                    color
            );

            drawRect(
                    right - 1,
                    y,
                    right,
                    y + step,
                    color
            );
        }
    }

    /*
     * =========================================================
     * PLAYER STATS
     * =========================================================
     */

    private static class PlayerStats {

        final String name;

        final PikaClient.Result result;

        PlayerStats(
                String name,
                PikaClient.Result result
        ) {

            this.name =
                    name;

            this.result =
                    result;
        }
    }
}
