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
        String name = player.getName();
        PikaClient.Result result = cache.get(name);

        String level = (result != null) ? result.playerLevel : "N/A";
        String guild = (result != null) ? result.guildDisplay : "N/A";
        String text = "Lvl " + level + " | " + guild;

        renderText(text, event.x, event.y + player.height + 0.5, event.z);
    }

    private void renderText(String text, double x, double y, double z) {
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
        mc.fontRendererObj.drawString(text, -width, 0, 0xAAAAAA);

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!enabled || !leaderboardEnabled || mc.theWorld == null || mc.thePlayer == null) return;

        List<String> displayLines = mc.theWorld.playerEntities.stream()
                .filter(e -> e instanceof EntityOtherPlayerMP)
                .map(e -> (EntityOtherPlayerMP) e)
                .map(player -> {
                    String name = player.getName();
                    PikaClient.Result result = cache.get(name);
                    String level = (result != null) ? result.playerLevel : "N/A";
                    return name + " - Lvl " + level;
                })
                .sorted()
                .limit(10)
                .collect(Collectors.toList());

        int x = 10;
        int y = mc.displayHeight / mc.gameSettings.guiScale / 4;
        int boxWidth = 120;
        int lineHeight = mc.fontRendererObj.FONT_HEIGHT + 2;
        int boxHeight = displayLines.size() * lineHeight + 6;

        drawRect(x - 4, y - 4, x + boxWidth + 4, y + boxHeight, 0x88000000);

        for (int i = 0; i < displayLines.size(); i++) {
            String line = displayLines.get(i);
            mc.fontRendererObj.drawStringWithShadow(line, x, y + i * lineHeight, 0xFFFFFF);
        }
    }

    private void drawRect(int left, int top, int right, int bottom, int color) {
        net.minecraft.client.gui.Gui.drawRect(left, top, right, bottom, color);
    }
}
