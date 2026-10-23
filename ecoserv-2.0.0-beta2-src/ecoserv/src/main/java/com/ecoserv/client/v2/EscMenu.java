package com.ecoserv.client.v2;

import com.ecoserv.client.ClientSettings;
import com.ecoserv.client.GuiClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Меню паузы (ESC), 2.0: кнопки своих меню сервера (слева вверху) и кнопка клиентских настроек EcoServ
 * (справа вверху) — она есть всегда, даже если шестерёнку в инвентаре не видно.
 */
@SideOnly(Side.CLIENT)
public class EscMenu {

    private static final int BASE = 47200;
    private static final int SETTINGS = 47199;
    private static final List<String[]> BUTTONS = new ArrayList<String[]>();

    public static void register() {
        ClientV2.on("esc_buttons", d -> {
            BUTTONS.clear();
            NBTTagList l = d.getTagList("b", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound t = l.getCompoundTagAt(i);
                BUTTONS.add(new String[]{t.getString("id"), t.getString("label")});
            }
        });
    }

    public static void reset() {
        BUTTONS.clear();
    }

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiIngameMenu)) {
            return;
        }
        int w = event.getGui().width;
        event.getButtonList().add(new GuiButton(SETTINGS, w - 124, 4, 120, 20, "⚙ " + ClientSettings.t("Настройки EcoServ")));
        for (int i = 0; i < BUTTONS.size() && i < 8; i++) {
            event.getButtonList().add(new GuiButton(BASE + i, 4, 4 + i * 22, 140, 20, BUTTONS.get(i)[1]));
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.getGui() instanceof GuiIngameMenu) || event.getButton() == null) {
            return;
        }
        int id = event.getButton().id;
        if (id == SETTINGS) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiClientSettings(event.getGui()));
            return;
        }
        int i = id - BASE;
        if (i >= 0 && i < BUTTONS.size()) {
            event.setCanceled(true);
            NBTTagCompound d = new NBTTagCompound();
            d.setString("id", BUTTONS.get(i)[0]);
            Minecraft.getMinecraft().displayGuiScreen(null);
            ClientV2.send("esc_click", d);
        }
    }
}
