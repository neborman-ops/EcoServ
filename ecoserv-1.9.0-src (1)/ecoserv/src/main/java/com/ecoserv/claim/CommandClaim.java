package com.ecoserv.claim;

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

/** /claim — меню-карта; /claim here|remove|info. */
public class CommandClaim extends CommandBase {

    @Override
    public String getName() {
        return "claim";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("приват", "claims");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/claim [here|remove|info]";
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
        int cx = ((int) Math.floor(p.posX)) >> 4;
        int cz = ((int) Math.floor(p.posZ)) >> 4;
        if (args.length == 0) {
            ClaimScreen.open(p);
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "here": {
                String err = ClaimManager.claim(p, p.dimension, cx, cz);
                ChatUtil.send(p, err == null ? "&aЧанк " + cx + ", " + cz + " теперь твой." : err);
                return;
            }
            case "remove": {
                String err = ClaimManager.unclaim(p, p.dimension, cx, cz);
                ChatUtil.send(p, err == null ? "&aЧанк освобождён." : err);
                return;
            }
            default: {
                ClaimManager.Claim c = ClaimManager.at(p.dimension, cx, cz);
                ChatUtil.send(p, c == null ? "&7Чанк " + cx + ", " + cz + " свободен."
                        : "&eЧанк " + cx + ", " + cz + ": &f" + c.ownerName + " &7(доверенные: "
                        + ClaimManager.describeTrusted(c.owner) + ")");
            }
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "here", "remove", "info") : Collections.emptyList();
    }
}
