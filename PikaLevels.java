package com.notthatlonely.pikalevels;

import com.notthatlonely.pikalevels.render.RenderHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = PikaLevels.MODID, name = PikaLevels.NAME, version = PikaLevels.VERSION)
public class PikaLevels {

    public static final String MODID = "pikalevels";
    public static final String NAME = "Pika Levels";
    public static final String VERSION = "1.0";

    private static KeyBinding toggleLevelsKey;
    private static KeyBinding toggleLeaderboardKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Register key bindings here
        toggleLevelsKey = new KeyBinding("Toggle Levels Display", Keyboard.KEY_L, NAME);
        toggleLeaderboardKey = new KeyBinding("Toggle Leaderboard", Keyboard.KEY_K, NAME);

        ClientRegistry.registerKeyBinding(toggleLevelsKey);
        ClientRegistry.registerKeyBinding(toggleLeaderboardKey);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new RenderHandler());

        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new com.notthatlonely.pikalevels.commands.LevelToggleCommand());
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new com.notthatlonely.pikalevels.commands.LeaderboardToggleCommand());

        System.out.println("PikaLevels mod loaded!");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();

        if (toggleLevelsKey.isPressed()) {
            RenderHandler.toggleEnabled();
            boolean enabled = RenderHandler.isEnabled();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText("Pika Levels display is now " + (enabled ? " aENABLED" : " cDISABLED")));
            }
            System.out.println("[PikaLevels] Levels display toggled: " + (enabled ? "ENABLED" : "DISABLED"));
        }

        if (toggleLeaderboardKey.isPressed()) {
            RenderHandler.toggleLeaderboard();
            boolean enabled = RenderHandler.isLeaderboardEnabled();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText("Pika Levels leaderboard is now " + (enabled ? " aENABLED" : " cDISABLED")));
            }
            System.out.println("[PikaLevels] Leaderboard toggled: " + (enabled ? "ENABLED" : "DISABLED"));
        }
    }
}
