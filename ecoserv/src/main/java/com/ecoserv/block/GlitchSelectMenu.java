package com.ecoserv.block;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.subworld.GlitchStorage;
import com.ecoserv.tile.TileGlitchChest;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Палка GLITCH SELECT по ГЛИТЧ-сундуку: какие виды пропавших предметов в нём показывать. */
public class GlitchSelectMenu extends ChestMenu {

    private final BlockPos pos;

    public GlitchSelectMenu(EntityPlayerMP player, BlockPos pos) {
        super(player, "GLITCH SELECT", 3);
        this.pos = pos;
    }

    private TileGlitchChest chest() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileGlitchChest ? (TileGlitchChest) te : null;
    }

    private static ItemStack icon(String category) {
        switch (category) {
            case GlitchStorage.EDIT:
                return new ItemStack(net.minecraft.init.Blocks.TNT);
            case GlitchStorage.VOID:
                return new ItemStack(Items.ENDER_PEARL);
            case GlitchStorage.DEATH:
                return new ItemStack(Items.SKULL, 1, 0);
            default:
                return new ItemStack(Items.NETHER_STAR);
        }
    }

    private static String hint(String category) {
        switch (category) {
            case GlitchStorage.EDIT:
                return "&7Блоки игроков, снесённые публикацией шаблона";
            case GlitchStorage.VOID:
                return "&7Предметы, упавшие в бездну в подмирах";
            case GlitchStorage.DEATH:
                return "&7Выпавшее при смерти в мире с пометкой GLITCH";
            default:
                return "&7Выданное админом: /subworld glitch give";
        }
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(2, Icons.filler());
        TileGlitchChest chest = chest();
        setButton(4, Icons.of(Items.ENDER_EYE, "&5Что попадает в этот сундук", "&7Каждый игрок видит в нём свои вещи.",
                "&8ЛКМ по виду — вкл/выкл"));
        int slot = 10;
        for (String c : GlitchStorage.CATEGORIES) {
            boolean on = chest != null && chest.categories().contains(c);
            ItemStack icon = Icons.of(icon(c), (on ? "&a✔ " : "&7") + GlitchStorage.title(c), hint(c),
                    on ? "&aпопадает" : "&8не показывается");
            setButton(slot, on ? Icons.glint(icon) : icon);
            slot += 2;
        }
        setButton(22, Icons.close());
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (!Perms.isAdmin(player)) {
            player.closeScreen();
            return;
        }
        if (slot == 22) {
            player.closeScreen();
            return;
        }
        int index = (slot - 10) / 2;
        TileGlitchChest chest = chest();
        if (chest != null && slot >= 10 && (slot - 10) % 2 == 0 && index < GlitchStorage.CATEGORIES.size()) {
            chest.toggle(GlitchStorage.CATEGORIES.get(index));
        }
    }
}
