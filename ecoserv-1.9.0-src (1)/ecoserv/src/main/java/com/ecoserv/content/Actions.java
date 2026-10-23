package com.ecoserv.content;

import com.ecoserv.EcoServ;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Общие действия для контента: сообщение, заголовок на экране, звук, команда от сервера.
 * Используются зонами-триггерами, сценариями, блоками-чекпоинтами и т.п.
 * Подстановки: %player%, %uuid%, %x% %y% %z%, %name%.
 */
public final class Actions {

    private Actions() {
    }

    public static String placeholders(String text, @Nullable EntityPlayerMP player, @Nullable String name) {
        if (text == null) {
            return "";
        }
        String s = text;
        if (player != null) {
            s = s.replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueID().toString())
                    .replace("%x%", Integer.toString((int) Math.floor(player.posX)))
                    .replace("%y%", Integer.toString((int) Math.floor(player.posY)))
                    .replace("%z%", Integer.toString((int) Math.floor(player.posZ)));
        }
        if (name != null) {
            s = s.replace("%name%", name);
        }
        return s;
    }

    public static void message(EntityPlayerMP player, String text, @Nullable String name) {
        if (player != null && text != null && !text.trim().isEmpty()) {
            ChatUtil.sendRaw(player, placeholders(text, player, name));
        }
    }

    /** «Заголовок|подзаголовок» на экране игрока. */
    public static void title(EntityPlayerMP player, String text, @Nullable String name, int fadeIn, int stay, int fadeOut) {
        if (player == null || player.connection == null || text == null || text.trim().isEmpty()) {
            return;
        }
        String full = placeholders(text, player, name);
        int bar = full.indexOf('|');
        String title = bar >= 0 ? full.substring(0, bar) : full;
        String sub = bar >= 0 ? full.substring(bar + 1) : "";
        player.connection.sendPacket(new SPacketTitle(fadeIn, stay, fadeOut));
        if (!sub.trim().isEmpty()) {
            player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE,
                    new TextComponentString(ChatUtil.color(sub))));
        }
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE,
                new TextComponentString(ChatUtil.color(title))));
    }

    /** Звук только этому игроку. false — звука с таким id нет. */
    public static boolean sound(EntityPlayerMP player, String soundId, float volume, float pitch) {
        if (player == null || player.connection == null || soundId == null || soundId.trim().isEmpty()) {
            return false;
        }
        SoundEvent sound;
        try {
            sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId.trim()));
        } catch (Exception e) {
            return false;
        }
        if (sound == null) {
            return false;
        }
        player.connection.sendPacket(new SPacketSoundEffect(sound, SoundCategory.MASTER, player.posX, player.posY,
                player.posZ, volume, pitch));
        return true;
    }

    /** Команда от имени сервера (как из консоли). */
    public static void serverCommand(String command, @Nullable EntityPlayerMP player, @Nullable String name) {
        MinecraftServer server = PlayerUtil.server();
        if (server == null || command == null) {
            return;
        }
        String c = placeholders(command.trim(), player, name);
        if (c.startsWith("/")) {
            c = c.substring(1);
        }
        if (c.isEmpty()) {
            return;
        }
        try {
            server.getCommandManager().executeCommand(server, c);
        } catch (Throwable t) {
            EcoServ.LOG.error("Команда «{}» упала", c, t);
        }
    }
}
