package com.ecoserv.menu;

import com.ecoserv.util.ChatUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ввод значения через чат вместо текстового поля в GUI:
 * меню закрывается, игрок пишет число или текст в чат, сообщение перехватывается.
 */
public final class ChatPrompt {

    public interface Callback {
        void accept(EntityPlayerMP player, String text);
    }

    private static class Pending {
        Callback callback;
        long expires;
    }

    private static final Map<UUID, Pending> waiting = new HashMap<UUID, Pending>();

    private ChatPrompt() {
    }

    public static void ask(EntityPlayerMP player, String question, Callback callback) {
        Pending pending = new Pending();
        pending.callback = callback;
        pending.expires = System.currentTimeMillis() + 60_000L;
        waiting.put(player.getUniqueID(), pending);
        player.closeScreen();
        ChatUtil.send(player, question);
        ChatUtil.send(player, "&7Напиши значение в чат. &fотмена &7- чтобы отменить.");
    }

    public static void cancel(EntityPlayerMP player) {
        waiting.remove(player.getUniqueID());
    }

    public static boolean isWaiting(EntityPlayerMP player) {
        Pending pending = waiting.get(player.getUniqueID());
        return pending != null && System.currentTimeMillis() < pending.expires;
    }

    /** Перехват чата. Регистрируется в общем обработчике событий. */
    public static class Handler {
        @SubscribeEvent
        public void onChat(ServerChatEvent event) {
            EntityPlayerMP player = event.getPlayer();
            if (player == null) {
                return;
            }
            Pending pending = waiting.get(player.getUniqueID());
            if (pending == null) {
                return;
            }
            waiting.remove(player.getUniqueID());
            event.setCanceled(true);

            if (System.currentTimeMillis() > pending.expires) {
                ChatUtil.send(player, "&eВремя ввода истекло.");
                return;
            }
            String text = event.getMessage() == null ? "" : event.getMessage().trim();
            if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
                ChatUtil.send(player, "&eОтменено.");
                return;
            }
            try {
                pending.callback.accept(player, text);
            } catch (Throwable t) {
                ChatUtil.send(player, "&cОшибка: " + t);
            }
        }
    }
}
