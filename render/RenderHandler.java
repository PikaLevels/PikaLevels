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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RenderHandler {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, PikaClient.Result> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFetch = new ConcurrentHashMap<>();
    private static final long REFRESH_INTERVAL = TimeUnit.SECONDS.toMillis(120);

    private static boolean enabled = true;
    private static boolean leaderboardEnabled = false;

    public static void toggleEnabled() {
        enabled = !enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggleLeaderboard() {
        leaderboardEnabled = !leaderboardEnabled;
    }

    public static boolean isLeaderboardEnabled() {
        return leaderboardEnabled;
    }

    public RenderHandler() {
        new Thread(() -> {
            while (true) {
                try {
                    if (mc.theWorld != null && mc.thePlayer != null) {
                        for (Object obj : mc.theWorld.playerEntities) {
                            if (!(obj instanceof EntityPlayer)) continue;
                            EntityPlayer other = (EntityPlayer) obj;
                            if (other == mc.thePlayer) continue;

                            String name = other.getName();
                            long now = System.currentTimeMillis();

                            boolean needsFetch = !cache.containsKey(name) ||
                                    now - lastFetch.getOrDefault(name, 0L) >= REFRESH_INTERVAL;

                            if (needsFetch) {
                                new Thread(() -> {
                                    PikaClient.Result result = PikaClient.fetchData(name);
                                    if (result != null) {
                                        cache.put(name, result);
                                        lastFetch.put(name, System.currentTimeMillis());
                                    }
                                }, "PikaLevels-Fetch-" + name).start();
                            }
                        }
                    }

                    Thread.sleep(10000);
                } catch (Exception ignored) {
                }
            }
        }, "PikaLevels-FetchThread").start();
    }

    @SubscribeEvent
    public void onRenderPlayer(RenderLivingEvent.Specials.Pre event) {
        if (!enabled) return;
        if (!(event.entity instanceof EntityOtherPlayerMP)) return;

        EntityOtherPlayerMP player = (EntityOtherPlayerMP) event.entity;
        if (player.isSneaking() || player.isInvisible()) return;

        String name = player.getName();
        PikaClient.Result result = cache.get(name);

        String level = "N/A";
        String guild = "N/A";
        int color = 0xAAAAAA;

        if (result != null) {
            if (result.nick) {
                level = "NICK";
                color = 0xAA00FF;
            } else {
                level = (result.playerLevel != null && !result.playerLevel.equals("N/A")) ? result.playerLevel : "N/A";
                guild = (result.guildDisplay != null) ? result.guildDisplay : "N/A";
                color = (level.equals("N/A")) ? 0xAAAAAA : result.rankColor;
            }
        }

        String text = "Lvl " + level + " | " + guild;
        renderText(text, event.x, event.y + player.height + 0.8, event.z, color); // Raised slightly
    }

    private void renderText(String text, double x, double y, double z, int color) {
        RenderManager renderManager = mc.getRenderManager();
        float viewerYaw = renderManager.playerViewY;
        float viewerPitch = renderManager.playerViewX;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + 0.3, z);
        GlStateManager.rotate(-viewerYaw, 0, 1, 0);
        GlStateManager.rotate(viewerPitch, 1, 0, 0);
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        int width = mc.fontRendererObj.getStringWidth(text) / 2;
        mc.fontRendererObj.drawString(text, -width, 0, color);

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!enabled || !leaderboardEnabled || mc.theWorld == null || mc.thePlayer == null) return;

        List<ColoredLine> displayLines = mc.theWorld.playerEntities.stream()
                .filter(e -> e instanceof EntityOtherPlayerMP)
                .map(e -> (EntityOtherPlayerMP) e)
                .map(player -> {
                    String name = player.getName();
                    PikaClient.Result result = cache.get(name);

                    String displayName;
                    int color;

                    if (result == null) {
                        displayName = name + " - Lvl N/A";
                        color = 0xAAAAAA;
                    } else if (result.nick) {
                        displayName = name + " - NICK";
                        color = 0xAA00FF;
                    } else if (result.playerLevel == null || result.playerLevel.equals("N/A")) {
                        displayName = name + " - Lvl N/A";
                        color = 0xAAAAAA;
                    } else {
                        displayName = name + " - Lvl " + result.playerLevel;
                        color = result.rankColor;
                    }

                    return new ColoredLine(displayName, color);
                })
                .sorted((a, b) -> a.text.compareToIgnoreCase(b.text))
                .limit(16) // Limit to 16 players
                .collect(Collectors.toList());

        int boxWidth = 140;
        int lineHeight = mc.fontRendererObj.FONT_HEIGHT;
        int boxHeight = displayLines.size() * lineHeight + 10;

        int x = 10;
        int screenHeight = mc.displayHeight / mc.gameSettings.guiScale;
        int y = (screenHeight - boxHeight) / 2;

        // Background
        drawRect(x - 6, y - 6, x + boxWidth + 6, y + boxHeight + 6, 0x88000000);

        // Dashed border
        drawThinBorder(x - 6, y - 6, x + boxWidth + 6, y + boxHeight + 6, 0xFF000000);

        for (int i = 0; i < displayLines.size(); i++) {
            ColoredLine line = displayLines.get(i);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y + i * lineHeight, 0);
            GlStateManager.scale(0.8, 0.8, 1.0);
            mc.fontRendererObj.drawStringWithShadow(line.text, 0, 0, line.color);
            GlStateManager.popMatrix();
        }
    }

    private void drawRect(int left, int top, int right, int bottom, int color) {
        net.minecraft.client.gui.Gui.drawRect(left, top, right, bottom, color);
    }

    private void drawThinBorder(int left, int top, int right, int bottom, int color) {
        int step = 4;

        for (int x = left; x < right; x += step * 2) {
            drawRect(x, top, x + step, top + 1, color);
            drawRect(x, bottom - 1, x + step, bottom, color);
        }

        for (int y = top; y < bottom; y += step * 2) {
            drawRect(left, y, left + 1, y + step, color);
            drawRect(right - 1, y, right, y + step, color);
        }
    }

    private static class ColoredLine {
        public final String text;
        public final int color;

        public ColoredLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }
}
