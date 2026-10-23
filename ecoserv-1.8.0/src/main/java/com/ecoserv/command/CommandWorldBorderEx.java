package com.ecoserv.command;

import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.world.WorldBorders;
import com.ecoserv.world.WorldNames;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandWorldBorder;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /worldborder — как ванильная, но меняет барьер того мира, где стоит игрок (у каждого мира свой).
 * Другой мир: «/worldborder in &lt;мир&gt; set 1000». Из консоли без «in» — основной мир.
 */
public class CommandWorldBorderEx extends CommandWorldBorder {

    @Nullable
    private World current;

    @Override
    public String getUsage(ICommandSender sender) {
        return "/worldborder [in <мир>] <set|add|center|damage|warning|get> ...";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!WorldBorders.separate()) {
            super.execute(server, sender, args);
            return;
        }
        World world = sender.getEntityWorld();
        String[] rest = args;
        if (args.length >= 2 && "in".equalsIgnoreCase(args[0])) {
            Integer dim = WorldNames.resolve(args[1]);
            if (dim == null && SubworldRegistry.get(args[1]) != null) {
                SubworldDim d = SubworldRegistry.find(SubworldRegistry.key(args[1]), SubworldDim.SHARED);
                dim = d == null ? null : d.dim;
            }
            WorldServer w = dim == null ? null : server.getWorld(dim);
            if (w == null) {
                ChatUtil.send(sender, "&cМир &f" + args[1] + " &cне найден или не загрузился.");
                return;
            }
            world = w;
            rest = Arrays.copyOfRange(args, 2, args.length);
        }
        if (world == null) {
            world = server.getWorld(0);
        }
        current = world;
        try {
            super.execute(server, sender, rest);
        } finally {
            current = null;
        }
        int dim = world.provider.getDimension();
        if (dim != 0 && world instanceof WorldServer) {
            // у обычных миров рассылает наш слушатель; у подмиров — досылаем и запоминаем барьер локального мира
            if (SubworldRegistry.isOurs(dim)) {
                for (EntityPlayer p : world.playerEntities) {
                    if (p instanceof EntityPlayerMP) {
                        WorldBorders.send((EntityPlayerMP) p);
                    }
                }
                SubworldDim d = SubworldRegistry.dim(dim);
                if (d != null && d.isInstance()) {
                    WorldBorders.rememberSubworld(dim, world.getWorldBorder());
                } else if (rest.length > 0 && !"get".equalsIgnoreCase(rest[0])) {
                    ChatUtil.send(sender, "&eЭто шаблон или общий мир подмира: барьер продержится до перезапуска. "
                            + "Барьер локальных миров настраивается в настройках подмира.");
                }
            }
        }
        if (rest.length > 0 && !"get".equalsIgnoreCase(rest[0])) {
            ChatUtil.sendRaw(sender, "&7Барьер изменён только в мире &f" + WorldNames.displayName(dim)
                    + "&7. Другой мир: &f/worldborder in <мир> ...");
        }
    }

    @Override
    protected WorldBorder getWorldBorder(MinecraftServer server) {
        World w = current;
        return w != null ? w.getWorldBorder() : super.getWorldBorder(server);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> base = new ArrayList<String>(super.getTabCompletions(server, sender, args, targetPos));
            if ("in".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                base.add("in");
            }
            return base;
        }
        if (args.length == 2 && "in".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<String>();
            for (Integer dim : WorldNames.allDims()) {
                if (!SubworldRegistry.isOurs(dim)) {
                    names.addAll(WorldNames.namesFor(dim));
                }
            }
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (args.length >= 3 && "in".equalsIgnoreCase(args[0])) {
            return super.getTabCompletions(server, sender, Arrays.copyOfRange(args, 2, args.length), targetPos);
        }
        return super.getTabCompletions(server, sender, args, targetPos);
    }
}
