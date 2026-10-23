package com.ecoserv.command;

import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import com.ecoserv.zone.Zone;
import com.ecoserv.zone.ZoneManager;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** /zone — управление зонами палочек BLOCK DESTROY (только админы). */
public class CommandZone extends CommandBase {

    @Override
    public String getName() {
        return "zone";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/zone list | info | remove <id|here> | vert <id> | type <id> <danger|silent> | name <id> <имя> | tp <id>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return Perms.isAdmin(sender) || super.checkPermission(server, sender);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase();
        EntityPlayerMP player = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;

        if ("list".equals(sub)) {
            List<Zone> zones = ZoneManager.all();
            ChatUtil.send(sender, "&6=== Защищённые зоны (" + zones.size() + ") ===");
            if (zones.isEmpty()) {
                ChatUtil.sendRaw(sender, " &7нет. Возьми палочку BLOCK DESTROY и кликни ПКМ по двум углам.");
            }
            for (int i = 0; i < zones.size(); i++) {
                Zone zone = zones.get(i);
                ITextComponent line = ChatUtil.component(" " + (zone.isDanger() ? "&c" : "&9") + zone.describe()
                        + " &7[" + WorldNames.nameOf(zone.dim) + "]");
                line.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/zone tp " + zone.id));
                line.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        ChatUtil.component("&eКлик — телепорт к зоне\n&7создал: " + zone.creator)));
                ChatUtil.send(sender, line);
            }
            return;
        }

        if ("info".equals(sub)) {
            if (player == null) {
                ChatUtil.send(sender, "&cТолько в игре.");
                return;
            }
            Zone zone = ZoneManager.at(player.dimension, new BlockPos(player));
            ChatUtil.send(sender, zone == null ? "&7Ты не в защищённой зоне." : "&6Ты в зоне: &f" + zone.describe());
            return;
        }

        if (args.length < 2) {
            ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
            return;
        }

        Zone zone;
        if ("here".equalsIgnoreCase(args[1]) && player != null) {
            zone = ZoneManager.at(player.dimension, new BlockPos(player));
        } else {
            try {
                zone = ZoneManager.byId(Integer.parseInt(args[1].replace("#", "")));
            } catch (NumberFormatException e) {
                zone = null;
            }
        }
        if (zone == null) {
            ChatUtil.send(sender, "&cЗона &f" + args[1] + " &cне найдена. Список: &f/zone list");
            return;
        }

        if ("remove".equals(sub) || "delete".equals(sub)) {
            ZoneManager.remove(zone.id);
            ChatUtil.send(sender, "&aЗона #" + zone.id + " удалена.");
            return;
        }
        if ("vert".equals(sub)) {
            zone.minY = 0;
            zone.maxY = 255;
            ZoneManager.changed();
            ChatUtil.send(sender, "&aЗона #" + zone.id + " теперь на всю высоту мира: &f" + zone.describe());
            return;
        }
        if ("type".equals(sub) && args.length >= 3) {
            String type = args[2].toLowerCase();
            if (!Zone.DANGER.equals(type) && !Zone.SILENT.equals(type)) {
                ChatUtil.send(sender, "&cТип: danger (пишет предупреждение) или silent (молча).");
                return;
            }
            zone.type = type;
            ZoneManager.changed();
            ChatUtil.send(sender, "&aЗона #" + zone.id + ": &f" + zone.typeTitle());
            return;
        }
        if ("name".equals(sub) && args.length >= 3) {
            zone.name = buildString(args, 2);
            ZoneManager.changed();
            ChatUtil.send(sender, "&aЗона #" + zone.id + " названа &f«" + zone.name + "»");
            return;
        }
        if ("tp".equals(sub)) {
            if (player == null) {
                ChatUtil.send(sender, "&cТолько в игре.");
                return;
            }
            BlockPos center = new BlockPos((zone.minX + zone.maxX) / 2, Math.min(250, zone.maxY + 1),
                    (zone.minZ + zone.maxZ) / 2);
            WorldTeleport.Target target = WorldTeleport.near(player, zone.dim, center);
            if (target == null) {
                ChatUtil.send(sender, "&cМир зоны не загрузился.");
                return;
            }
            if (player.dimension != zone.dim) {
                WorldTeleport.rememberPosition(player);
            }
            if (!WorldTeleport.teleport(player, target)) {
                ChatUtil.send(sender, "&cПереход в этот мир запрещён другим модом.");
                return;
            }
            ChatUtil.send(sender, "&aТелепорт к зоне #" + zone.id);
            return;
        }
        ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "info", "remove", "vert", "type", "name", "tp");
        }
        if (args.length == 2) {
            List<String> ids = new ArrayList<String>();
            ids.add("here");
            for (Zone zone : ZoneManager.all()) {
                ids.add(Integer.toString(zone.id));
            }
            return getListOfStringsMatchingLastWord(args, ids);
        }
        if (args.length == 3 && "type".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, Zone.DANGER, Zone.SILENT);
        }
        return new ArrayList<String>();
    }
}
