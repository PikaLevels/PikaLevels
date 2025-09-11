package com.notthatlonely.pikalevels.commands;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.util.Collections;
import java.util.List;

public class LeaderboardToggleCommand implements ICommand {

    @Override
    public String getCommandName() {
        return "toggleleaderboard";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/toggleleaderboard";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        com.notthatlonely.pikalevels.render.RenderHandler.toggleLeaderboard();
        boolean current = com.notthatlonely.pikalevels.render.RenderHandler.isLeaderboardEnabled();
        sender.addChatMessage(new ChatComponentText("Pika Levels leaderboard is now " + (current ? "§aENABLED" : "§cDISABLED")));
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, net.minecraft.util.BlockPos pos) {
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(ICommand o) {
        return 0;
    }
}
