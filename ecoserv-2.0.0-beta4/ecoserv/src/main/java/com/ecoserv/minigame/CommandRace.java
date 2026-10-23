package com.ecoserv.minigame;

import com.ecoserv.util.ChatUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /race (/гонка) — окно «Квестовой гонки»; /race сдать — сдать текущий шаг; /race выйти — выйти/сдаться. */
public class CommandRace extends CommandBase {

    @Override
    public String getName() {
        return "race";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("гонка", "minigame");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/race [сдать|выйти|арена]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
        try {
            switch (sub) {
                case "сдать":
                case "submit":
                    RaceManager.submit(player);
                    ChatUtil.send(player, "&aШаг сдан!");
                    break;
                case "выйти":
                case "leave":
                    RaceManager.leave(player);
                    break;
                case "арена":
                case "arena":
                    RaceManager.backToArena(player);
                    break;
                default:
                    RaceScreen.open(player);
            }
        } catch (IllegalStateException e) {
            ChatUtil.send(player, "&c" + e.getMessage());
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "сдать", "выйти", "арена") : new ArrayList<String>();
    }
}
