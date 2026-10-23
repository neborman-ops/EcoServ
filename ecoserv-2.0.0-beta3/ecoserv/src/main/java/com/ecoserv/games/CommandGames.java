package com.ecoserv.games;

import com.ecoserv.util.ChatUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /games — меню мини-игр; /games leave — выйти из лобби или игры. */
public class CommandGames extends CommandBase {

    @Override
    public String getName() {
        return "games";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("игры", "minigames");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/games [leave]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender instanceof EntityPlayerMP;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        if (args.length > 0 && ("leave".equalsIgnoreCase(args[0]) || "выйти".equalsIgnoreCase(args[0]))) {
            if (GameManager.sessionOf(p.getUniqueID()) == null) {
                ChatUtil.send(p, "&7Ты не в игре.");
                return;
            }
            GameManager.leave(p);
            ChatUtil.send(p, "&eТы вышел из игры.");
            return;
        }
        GamesScreen.open(p);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "leave") : Collections.emptyList();
    }
}
