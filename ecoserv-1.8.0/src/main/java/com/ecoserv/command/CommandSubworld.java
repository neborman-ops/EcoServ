package com.ecoserv.command;

import com.ecoserv.subworld.GlitchStorage;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldBorder;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldDims;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeams;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.subworld.SubworldUploads;
import com.ecoserv.subworld.menu.SubworldScreen;
import com.ecoserv.subworld.menu.WorldsScreen;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /subworld — управление подмирами для админов (и из консоли/RCON): список, настройки, публикация
 * шаблона, доступ игрокам, барьер, хранилище ГЛИТЧ. Игрокам — меню миров.
 */
public class CommandSubworld extends CommandBase {

    @Override
    public String getName() {
        return "subworld";
    }

    @Override
    public List<String> getAliases() {
        List<String> a = new ArrayList<String>();
        a.add("subworlds");
        a.add("sw");
        return a;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/subworld [list|info|menu|tp|publish|enable|disable|unlock|lock|expand|glitch|uploads|delete]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Nullable
    private static Subworld sub(ICommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            ChatUtil.send(sender, "&cУкажи подмир: &f/subworld list");
            return null;
        }
        Subworld sw = SubworldRegistry.get(args[index]);
        if (sw == null) {
            ChatUtil.send(sender, "&cПодмир &f" + args[index] + " &cне найден.");
        }
        return sw;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        boolean admin = Perms.isAdmin(sender);
        if (!admin) {
            // игрокам — только меню миров
            WorldsScreen.open(getCommandSenderAsPlayer(sender));
            return;
        }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "menu": {
                EntityPlayerMP p = getCommandSenderAsPlayer(sender);
                Subworld sw = args.length > 1 ? SubworldRegistry.get(args[1]) : null;
                if (sw != null) {
                    new SubworldScreen(p, sw.id).open();
                } else {
                    WorldsScreen.open(p);
                }
                return;
            }
            case "list": {
                if (SubworldRegistry.loadFailed()) {
                    ChatUtil.send(sender, SubworldRegistry.BROKEN_NOTICE);
                }
                List<Subworld> all = SubworldRegistry.all();
                ChatUtil.send(sender, "&6=== Подмиры (" + all.size() + ") ===");
                for (Subworld sw : all) {
                    int instances = 0;
                    for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
                        if (d.isInstance()) {
                            instances++;
                        }
                    }
                    ChatUtil.sendRaw(sender, " " + (sw.enabled ? "&a" : "&7") + sw.id + " &f" + sw.name + " &7— " + sw.typeTitle()
                            + ", доступ " + ChatUtil.strip(sw.accessTitle())
                            + (sw.perOwner() ? ", локальных миров: " + instances : "")
                            + (sw.glitch ? " &5GLITCH" : ""));
                }
                return;
            }
            case "info": {
                Subworld sw = sub(sender, args, 1);
                if (sw == null) {
                    return;
                }
                ChatUtil.send(sender, "&6" + sw.name + " &7(" + sw.id + ")" + (sw.enabled ? " &aвключён" : " &cвыключен"));
                ChatUtil.sendRaw(sender, " &7Тип: &f" + sw.typeTitle() + " &7доступ: &f" + ChatUtil.strip(sw.accessTitle()));
                for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
                    ChatUtil.sendRaw(sender, " &7измерение &f" + d.dim + " &7" + d.role
                            + (d.isInstance() ? " &f" + SubworldTeams.ownerName(d.owner) + " — " + d.saveName
                            + (d.border > 0 ? " &7барьер " + d.border : "") : ""));
                }
                return;
            }
            case "tp": {
                Subworld sw = sub(sender, args, 1);
                if (sw == null) {
                    return;
                }
                EntityPlayerMP p = getCommandSenderAsPlayer(sender);
                SubworldDim d = SubworldRegistry.find(sw.id, sw.perOwner() ? SubworldDim.TEMPLATE : SubworldDim.SHARED);
                if (d != null) {
                    SubworldTeleport.teleportTo(p, d, args.length > 2 && "spawn".equalsIgnoreCase(args[2]), null);
                }
                return;
            }
            case "publish": {
                Subworld sw = sub(sender, args, 1);
                if (sw != null) {
                    ChatUtil.send(sender, SubworldManager.publish(sw, sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null));
                }
                return;
            }
            case "enable":
            case "disable": {
                Subworld sw = sub(sender, args, 1);
                if (sw != null) {
                    sw.enabled = "enable".equals(sub);
                    SubworldRegistry.save();
                    SubworldDims.sync(sw);
                    ChatUtil.send(sender, "&aПодмир &f" + sw.id + (sw.enabled ? " &aвключён." : " &eвыключен."));
                }
                return;
            }
            case "unlock":
            case "lock": {
                Subworld sw = sub(sender, args, 1);
                if (sw == null) {
                    return;
                }
                if (args.length < 3) {
                    ChatUtil.send(sender, "&cИспользование: &f/subworld " + sub + " <подмир> <игрок>");
                    return;
                }
                UUID uuid = PlayerUtil.uuidOf(args[2]);
                if (uuid == null) {
                    ChatUtil.send(sender, "&cИгрок &f" + args[2] + " &cне найден.");
                    return;
                }
                if ("unlock".equals(sub)) {
                    SubworldRegistry.unlock(uuid.toString(), sw.id);
                    ChatUtil.send(sender, "&aДоступ в &f" + sw.id + " &aоткрыт игроку &f" + args[2]);
                } else {
                    boolean removed = SubworldRegistry.lock(uuid.toString(), sw.id);
                    ChatUtil.send(sender, removed ? "&aДоступ закрыт." : "&eУ игрока не было открытого доступа.");
                }
                return;
            }
            case "expand": {
                if (args.length < 3) {
                    ChatUtil.send(sender, "&cИспользование: &f/subworld expand <подмир> <игрок> [блоков]");
                    return;
                }
                Subworld sw = sub(sender, args, 1);
                if (sw == null) {
                    return;
                }
                UUID uuid = PlayerUtil.uuidOf(args[2]);
                if (uuid == null) {
                    ChatUtil.send(sender, "&cИгрок &f" + args[2] + " &cне найден.");
                    return;
                }
                int amount = args.length > 3 ? parseInt(args[3], 1, 100000) : 0;
                SubworldDim d = SubworldRegistry.activeInstance(sw.id, SubworldTeams.ownerKey(uuid, sw));
                if (d == null) {
                    ChatUtil.send(sender, "&eУ игрока нет локального мира в этом подмире.");
                    return;
                }
                int r = SubworldBorder.expand(d, amount);
                SubworldRegistry.saveIfDirty();
                ChatUtil.send(sender, r < 0 ? "&eВ этом мире нет барьера." : "&aБарьер: &f" + r);
                return;
            }
            case "glitch": {
                glitch(sender, args);
                return;
            }
            case "uploads": {
                List<SubworldUploads.Upload> list = SubworldUploads.list();
                ChatUtil.send(sender, "&6Свои миры в &f" + SubworldUploads.root().getPath() + " &6(" + list.size() + ")");
                for (SubworldUploads.Upload u : list) {
                    ChatUtil.sendRaw(sender, " " + (u.error == null ? "&a" : "&7") + u.name + " &7" + Fmt.bytes(u.size)
                            + (u.error == null ? "" : " &c" + u.error));
                }
                return;
            }
            case "delete": {
                Subworld sw = sub(sender, args, 1);
                if (sw == null) {
                    return;
                }
                if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
                    ChatUtil.send(sender, "&cЭто сотрёт все миры подмира. Подтверди: &f/subworld delete " + sw.id + " confirm");
                    return;
                }
                if (SubworldManager.isWorking(sw.id)) {
                    ChatUtil.send(sender, "&eПодмир сейчас готовится (установка мира или публикация) — удалить можно после окончания.");
                    return;
                }
                String note = SubworldManager.delete(sw);
                ChatUtil.send(sender, "&aПодмир &f" + sw.id + " &aудалён.");
                if (!note.isEmpty()) {
                    ChatUtil.send(sender, note);
                }
                return;
            }
            default:
                ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
        }
    }

    private void glitch(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) {
            ChatUtil.send(sender, "&cИспользование: &f/subworld glitch give <игрок> &7(предмет из руки) | &f/subworld glitch list <игрок>");
            return;
        }
        UUID uuid = PlayerUtil.uuidOf(args[2]);
        if (uuid == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[2] + " &cне найден.");
            return;
        }
        if ("give".equalsIgnoreCase(args[1])) {
            EntityPlayerMP p = getCommandSenderAsPlayer(sender);
            ItemStack held = p.getHeldItemMainhand();
            if (held.isEmpty()) {
                ChatUtil.send(sender, "&eВозьми предмет в руку.");
                return;
            }
            GlitchStorage.add("player:" + uuid, GlitchStorage.ADMIN, held.copy(), "от " + p.getName());
            ChatUtil.send(sender, "&aПредмет положен в хранилище ГЛИТЧ игрока &f" + args[2]);
            return;
        }
        int total = 0;
        for (String owner : GlitchStorage.ownersOf(uuid)) {
            total += GlitchStorage.count(owner);
        }
        ChatUtil.send(sender, "&7В хранилище ГЛИТЧ игрока &f" + args[2] + "&7: &f" + total + " &7предметов.");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (!Perms.isAdmin(sender)) {
            return new ArrayList<String>();
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "info", "menu", "tp", "publish", "enable", "disable",
                    "unlock", "lock", "expand", "glitch", "uploads", "delete");
        }
        if (args.length == 2 && "glitch".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "give", "list");
        }
        if (args.length == 2 && !"uploads".equalsIgnoreCase(args[0]) && !"list".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (Subworld sw : SubworldRegistry.all()) {
                ids.add(sw.id);
            }
            return getListOfStringsMatchingLastWord(args, ids);
        }
        if (args.length == 3) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return new ArrayList<String>();
    }
}
