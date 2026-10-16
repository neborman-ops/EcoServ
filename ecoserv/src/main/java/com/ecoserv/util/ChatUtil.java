package com.ecoserv.util;

import com.ecoserv.config.EcoConfig;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.List;

public final class ChatUtil {

    private ChatUtil() {
    }

    /** Переводит '&' в цветовой код Minecraft. */
    public static String color(String text) {
        return text == null ? "" : text.replace('&', '§');
    }

    public static String prefix() {
        return color(EcoConfig.chatPrefix);
    }

    /** Кликабельная строка: клик выполняет команду, при наведении — подсказка. */
    public static ITextComponent link(String text, String command, @javax.annotation.Nullable String hover) {
        ITextComponent c = component(text);
        c.getStyle().setClickEvent(new net.minecraft.util.text.event.ClickEvent(
                net.minecraft.util.text.event.ClickEvent.Action.RUN_COMMAND, command));
        if (hover != null) {
            c.getStyle().setHoverEvent(new net.minecraft.util.text.event.HoverEvent(
                    net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT, component(hover)));
        }
        return c;
    }

    public static ITextComponent component(String text) {
        return new TextComponentString(color(text));
    }

    /** Убирает цветовые коды (§x) — для RCON, логов и панелей. */
    public static String strip(String text) {
        return text == null ? "" : text.replaceAll("(?i)§[0-9a-fk-or]", "").replace("§", "");
    }

    /**
     * RCON (панели хостинга, скрипты) показывает текст как есть: цветовые коды там — мусор,
     * а строки ванильный RCON склеивает без переносов. Поэтому для него — чистый текст с \n.
     */
    public static boolean isRcon(ICommandSender sender) {
        return sender instanceof net.minecraft.network.rcon.RConConsoleSource;
    }

    public static void send(ICommandSender sender, String text) {
        if (sender == null) {
            return;
        }
        if (isRcon(sender)) {
            sender.sendMessage(new TextComponentString(strip(color(text)) + "\n"));
            return;
        }
        sender.sendMessage(new TextComponentString(prefix() + color(text)));
    }

    public static void sendRaw(ICommandSender sender, String text) {
        if (sender == null) {
            return;
        }
        if (isRcon(sender)) {
            sender.sendMessage(new TextComponentString(strip(color(text)) + "\n"));
            return;
        }
        sender.sendMessage(new TextComponentString(color(text)));
    }

    public static void send(ICommandSender sender, ITextComponent component) {
        if (sender == null || component == null) {
            return;
        }
        if (isRcon(sender)) {
            sender.sendMessage(new TextComponentString(strip(component.getUnformattedText()) + "\n"));
            return;
        }
        sender.sendMessage(component);
    }

    /** Строка над хотбаром (не в чат). */
    public static void actionBar(net.minecraft.entity.player.EntityPlayer player, String text) {
        if (player != null) {
            player.sendStatusMessage(new TextComponentString(color(text)), true);
        }
    }

    /** Сообщение всем онлайн-игрокам. */
    public static void broadcast(String text) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        ITextComponent msg = new TextComponentString(prefix() + color(text));
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            players.get(i).sendMessage(msg);
        }
        server.sendMessage(msg);
    }

    /** Сообщение только администраторам. */
    public static void broadcastAdmins(String text) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        ITextComponent msg = new TextComponentString(prefix() + color(text));
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            EntityPlayerMP p = players.get(i);
            if (Perms.isAdmin(p)) {
                p.sendMessage(msg);
            }
        }
        server.sendMessage(msg);
    }
}
