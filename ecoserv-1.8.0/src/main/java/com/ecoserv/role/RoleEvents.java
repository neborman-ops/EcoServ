package com.ecoserv.role;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Роли: синхронизация с клиентом и тег в чате. */
public class RoleEvents {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            RoleManager.sync((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        XaeroTp.forget(event.player.getUniqueID());
        com.ecoserv.util.Selection.clearAll(event.player.getUniqueID());
    }

    /** Тег роли перед сообщением в чате: «[VIP] <Ник> привет». */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onChat(ServerChatEvent event) {
        if (!EcoConfig.rolesChatPrefix || event.getPlayer() == null || event.getComponent() == null) {
            return;
        }
        String prefix = RoleManager.chatPrefix(event.getPlayer().getUniqueID());
        if (prefix.isEmpty()) {
            return;
        }
        ITextComponent line = new TextComponentString(ChatUtil.color(prefix) + "§r ");
        line.appendSibling(event.getComponent());
        event.setComponent(line);
    }
}
