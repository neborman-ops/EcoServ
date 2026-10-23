package com.ecoserv.command;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /world — список миров, /world &lt;мир&gt; — телепорт.
 * Админ: /world &lt;мир&gt; &lt;игрок&gt; — отправить другого игрока.
 */
public class CommandWorld extends CommandBase {

    private final Map<UUID, Long> lastUse = new HashMap<UUID, Long>();

    @Override
    public String getName() {
        return "world";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("worlds");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/world [мир] | menu | help | add <ник> | invite <ник> [build] | leave";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            return true;
        }
        return Perms.isAdmin(sender) || Perms.has((EntityPlayerMP) sender, Perms.WORLD);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        // команда, гости и меню работают, даже если телепорт /world <мир> выключен
        if (args.length > 0 && WorldTeamCommands.handle(server, sender, args)) {
            return;
        }
        if (!EcoConfig.worldsEnabled) {
            ChatUtil.send(sender, "&cКоманда /world выключена на сервере.");
            return;
        }
        boolean admin = Perms.isAdmin(sender);
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            list(sender, admin);
            return;
        }
        if ("leave".equalsIgnoreCase(args[0])) {
            com.ecoserv.subworld.SubworldTeleport.leave(getCommandSenderAsPlayer(sender));
            return;
        }
        if ("buy".equalsIgnoreCase(args[0]) && args.length >= 2) {
            buy(getCommandSenderAsPlayer(sender), args[1]);
            return;
        }

        // подмир по имени: доступ, свой локальный мир команды
        com.ecoserv.subworld.Subworld sw = com.ecoserv.subworld.SubworldRegistry.get(args[0]);
        if (sw != null) {
            EntityPlayerMP who;
            if (args.length >= 2) {
                if (!admin) {
                    ChatUtil.send(sender, "&cОтправлять других игроков может только админ.");
                    return;
                }
                who = PlayerUtil.online(args[1]);
                if (who == null) {
                    ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне в сети.");
                    return;
                }
            } else {
                who = getCommandSenderAsPlayer(sender);
            }
            boolean inside = com.ecoserv.subworld.SubworldRegistry.subworldOf(who.dimension) == sw;
            if (com.ecoserv.subworld.SubworldTeleport.enter(who, sw, inside) && who != sender) {
                ChatUtil.send(sender, "&aИгрок &f" + who.getName() + " &aотправлен в подмир &f" + sw.name);
            }
            return;
        }

        Integer dim = WorldNames.resolve(args[0]);
        if (dim == null || !WorldNames.exists(dim)) {
            ChatUtil.send(sender, "&cМир &f" + args[0] + " &cне найден. Список: &f/world");
            return;
        }
        if (!admin && com.ecoserv.subworld.SubworldRegistry.isOurs(dim)) {
            ChatUtil.send(sender, "&cЭто мир подмира — войди по его имени: &f/world "
                    + WorldNames.displayName(dim));
            return;
        }
        boolean roleWorld = sender instanceof EntityPlayerMP
                && com.ecoserv.role.RoleManager.worldAllowed(((EntityPlayerMP) sender).getUniqueID(), dim);
        if (!admin && !WorldNames.allowedForPlayers(dim) && !roleWorld) {
            ChatUtil.send(sender, "&cВ мир &f" + WorldNames.nameOf(dim) + " &cможно попасть только обычным путём.");
            return;
        }
        if (!admin && sender instanceof EntityPlayerMP && args.length < 2) {
            // 1.8.0: доступ в обычный мир — как у подмиров
            com.ecoserv.subworld.SubworldAccess.Result access = com.ecoserv.world.WorldAccess.check((EntityPlayerMP) sender, dim);
            if (!access.ok) {
                ChatUtil.send(sender, access.reason == null ? "&cНет доступа." : access.reason);
                if (access.canBuy) {
                    ChatUtil.send(sender, ChatUtil.link("&a[Купить доступ]", "/world buy " + WorldNames.nameOf(dim),
                            "Купить доступ в мир"));
                }
                return;
            }
        }

        EntityPlayerMP target;
        if (args.length >= 2) {
            if (!admin) {
                ChatUtil.send(sender, "&cОтправлять других игроков может только админ.");
                return;
            }
            target = PlayerUtil.online(args[1]);
            if (target == null) {
                ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне в сети.");
                return;
            }
        } else {
            target = getCommandSenderAsPlayer(sender);
        }

        if (!admin && EcoConfig.worldCooldownSeconds > 0) {
            Long last = lastUse.get(target.getUniqueID());
            long wait = last == null ? 0L : last + EcoConfig.worldCooldownSeconds * 1000L - System.currentTimeMillis();
            if (wait > 0L) {
                ChatUtil.send(sender, "&eПодожди ещё &f" + ((wait + 999L) / 1000L) + " сек.");
                return;
            }
        }

        com.ecoserv.subworld.SubworldDim subDim = com.ecoserv.subworld.SubworldRegistry.dim(dim);
        if (subDim != null) {
            // мир подмира (сюда доходят только админы) — через вход подмира: занятый мир (установка,
            // публикация) не загрузится, точка прибытия безопасная, запоминается, куда вернуться
            if (com.ecoserv.subworld.SubworldTeleport.teleportTo(target, subDim, target.dimension == dim, null)) {
                ChatUtil.actionBar(target, "&a➜ &f" + WorldNames.nameOf(dim));
                if (target != sender) {
                    ChatUtil.send(sender, "&aИгрок &f" + target.getName() + " &aотправлен в &f" + WorldNames.nameOf(dim));
                }
            }
            return;
        }

        // в свой же мир — на его спавн, в другой — туда, где был в прошлый раз
        boolean toSpawn = target.dimension == dim;
        WorldTeleport.Target point = WorldTeleport.pickTarget(target, dim, toSpawn);
        if (point == null) {
            ChatUtil.send(sender, "&cМир &f" + WorldNames.nameOf(dim) + " &cне загрузился.");
            return;
        }
        if (target.dimension != dim) {
            WorldTeleport.rememberPosition(target);
        }
        if (!WorldTeleport.teleport(target, point)) {
            ChatUtil.send(sender, "&cПереход в этот мир запрещён другим модом.");
            return;
        }
        lastUse.put(target.getUniqueID(), System.currentTimeMillis());
        ChatUtil.actionBar(target, "&a➜ &f" + WorldNames.nameOf(dim));
        if (target != sender) {
            ChatUtil.send(sender, "&aИгрок &f" + target.getName() + " &aотправлен в &f" + WorldNames.nameOf(dim));
        }
    }

    private void buy(EntityPlayerMP player, String id) {
        com.ecoserv.subworld.Subworld sw = com.ecoserv.subworld.SubworldRegistry.get(id);
        if (sw == null) {
            Integer dim = WorldNames.resolve(id);
            com.ecoserv.world.WorldSettings.Settings ws = dim == null ? null : com.ecoserv.world.WorldSettings.peek(dim);
            if (ws != null && com.ecoserv.subworld.Subworld.ACCESS_PRICE.equals(ws.access)) {
                if (com.ecoserv.world.WorldAccess.check(player, dim).ok) {
                    ChatUtil.send(player, "&eДоступ уже открыт: &f/world " + WorldNames.nameOf(dim));
                } else if (com.ecoserv.world.WorldAccess.buy(player, dim)) {
                    ChatUtil.send(player, "&aДоступ в мир &f" + WorldNames.nameOf(dim) + " &aкуплен! Вход: &f/world "
                            + WorldNames.nameOf(dim));
                } else {
                    ChatUtil.send(player, "&cНе хватает денег: нужно &6" + com.ecoserv.util.Fmt.money(ws.price));
                }
                return;
            }
            ChatUtil.send(player, "&cПодмир &f" + id + " &cне найден.");
            return;
        }
        if (!com.ecoserv.subworld.Subworld.ACCESS_PRICE.equals(sw.access)) {
            ChatUtil.send(player, "&eДоступ в этот подмир не продаётся.");
            return;
        }
        if (com.ecoserv.subworld.SubworldAccess.check(player, sw).ok) {
            ChatUtil.send(player, "&eДоступ уже открыт: &f/world " + sw.id);
            return;
        }
        if (com.ecoserv.subworld.SubworldAccess.buy(player, sw)) {
            ChatUtil.send(player, "&aДоступ в «" + sw.name + "&a» куплен! Вход: &f/world " + sw.id);
        } else if (com.ecoserv.subworld.SubworldRegistry.loadFailed()) {
            ChatUtil.send(player, "&cПодмиры сейчас недоступны — сообщи админу.");
        } else {
            ChatUtil.send(player, "&cНе хватает денег: нужно &6" + com.ecoserv.util.Fmt.money(sw.price));
        }
    }

    /** Кликабельный список миров. */
    private void list(ICommandSender sender, boolean admin) {
        int current = sender instanceof EntityPlayerMP ? ((EntityPlayerMP) sender).dimension : Integer.MIN_VALUE;
        ITextComponent line = new TextComponentString(ChatUtil.color("&6Миры: "));
        int shown = 0;
        for (Integer dim : WorldNames.allDims()) {
            if (com.ecoserv.subworld.SubworldRegistry.isOurs(dim)) {
                // измерения подмиров показываем ниже по именам подмиров
                continue;
            }
            boolean forPlayers = WorldNames.allowedForPlayers(dim)
                    || sender instanceof EntityPlayerMP
                    && com.ecoserv.role.RoleManager.worldAllowed(((EntityPlayerMP) sender).getUniqueID(), dim);
            if (!admin && !forPlayers) {
                continue;
            }
            String name = WorldNames.nameOf(dim);
            String label = (dim == current ? "&a" : forPlayers ? "&f" : "&7") + "[" + name + "]";
            ITextComponent part = new TextComponentString(ChatUtil.color(label) + " ");
            Style style = new Style();
            style.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/world " + name));
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(ChatUtil.color(
                    "&fТелепорт в &e" + name + "\n&7измерение " + dim
                            + (forPlayers ? "" : "\n&cтолько для админов")
                            + (dim == current ? "\n&aты здесь — телепорт на спавн мира" : "")))));
            part.setStyle(style);
            line.appendSibling(part);
            shown++;
        }
        // подмиры
        String here = sender instanceof EntityPlayerMP
                ? com.ecoserv.world.WorldNames.groupOf(((EntityPlayerMP) sender).dimension) : null;
        for (com.ecoserv.subworld.Subworld sw : com.ecoserv.subworld.SubworldRegistry.all()) {
            if (!sw.enabled && !admin) {
                continue;
            }
            String status = sender instanceof EntityPlayerMP
                    ? com.ecoserv.subworld.SubworldAccess.status((EntityPlayerMP) sender, sw) : "";
            String label = (sw.id.equals(here) ? "&a" : sw.enabled ? "&b" : "&7") + "[" + sw.id + "]";
            ITextComponent part = new TextComponentString(ChatUtil.color(label) + " ");
            Style style = new Style();
            style.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/world " + sw.id));
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(ChatUtil.color(
                    "&fПодмир &e" + sw.name + "\n&7" + sw.typeTitle() + "\n&7доступ: " + status))));
            part.setStyle(style);
            line.appendSibling(part);
            shown++;
        }
        if (shown == 0) {
            ChatUtil.send(sender, "&eНет миров, куда можно телепортироваться.");
            return;
        }
        ChatUtil.send(sender, line);
        ChatUtil.sendRaw(sender, "&7Нажми на мир или напиши &f/world <мир>&7; меню миров — кнопка-планета в инвентаре "
                + "или &f/world menu&7; команда и гости — &f/world help");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        boolean admin = Perms.isAdmin(sender);
        if (args.length == 1) {
            List<String> names = new ArrayList<String>();
            for (Integer dim : WorldNames.allDims()) {
                if (com.ecoserv.subworld.SubworldRegistry.isOurs(dim)) {
                    continue;
                }
                if (admin || WorldNames.allowedForPlayers(dim)) {
                    names.addAll(WorldNames.namesFor(dim));
                }
            }
            for (com.ecoserv.subworld.Subworld sw : com.ecoserv.subworld.SubworldRegistry.all()) {
                if (sw.enabled || admin) {
                    names.add(sw.id);
                }
            }
            names.add("leave");
            names.addAll(WorldTeamCommands.SUBCOMMANDS);
            return getListOfStringsMatchingLastWord(args, names);
        }
        List<String> team = WorldTeamCommands.complete(server, sender, args);
        if (team != null) {
            return team;
        }
        if (args.length == 2 && admin) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return new ArrayList<String>();
    }
}
