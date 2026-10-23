package com.ecoserv.dev;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.v2.ModRegistry2;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Режим разработчика (2.0): все инструменты в одном окне. /dev */
public class DevScreen extends UiScreen {

    public DevScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP p) {
        new DevScreen(p).open();
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        boolean dev = DevManager.isDev(player);
        ui.title("Режим разработчика").icon(UiIcons.anvil());
        ui.subtitle(dev ? "&aвключён — видно скрытое" : "&7выключен");
        ui.sideButton(dev ? "Выключить режим" : "Включить режим", UiIcons.power(), dev ? Ui.YELLOW : Ui.GREEN,
                () -> DevManager.setDev(player, !dev)).tip("Видно маркеры, зоны (красные/синие), блоки-программы, провода");
        DevTools.hubSections(ui, player, this);
    }

    /** Выдать предмет админу. */
    static void give(EntityPlayerMP p, ItemStack s) {
        PlayerUtil.giveOrDrop(p, s);
    }

    static ItemStack glasses() {
        return new ItemStack(ModRegistry2.DEV_GLASSES);
    }

    static ItemStack logic() {
        return new ItemStack(ModRegistry2.LOGIC);
    }

    static ItemStack barrier() {
        return new ItemStack(Blocks.BARRIER);
    }
}
