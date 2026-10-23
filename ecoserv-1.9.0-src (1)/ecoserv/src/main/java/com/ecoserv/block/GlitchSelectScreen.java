package com.ecoserv.block;

import com.ecoserv.subworld.GlitchStorage;
import com.ecoserv.tile.TileGlitchChest;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Палка GLITCH SELECT по ГЛИТЧ-сундуку: какие виды пропавших предметов в нём показывать. */
public class GlitchSelectScreen extends UiScreen {

    private final BlockPos pos;

    public GlitchSelectScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos.toImmutable();
    }

    private TileGlitchChest chest() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileGlitchChest ? (TileGlitchChest) te : null;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player) && chest() != null;
    }

    static String hint(String category) {
        switch (category) {
            case GlitchStorage.EDIT:
                return "Блоки игроков, снесённые публикацией шаблона";
            case GlitchStorage.VOID:
                return "Предметы, упавшие в бездну в подмирах";
            case GlitchStorage.DEATH:
                return "Выпавшее при смерти в мире с пометкой GLITCH";
            default:
                return "Выданное админом: /subworld glitch give или админ-панель";
        }
    }

    @Override
    protected void build(Ui ui) {
        ui.title("GLITCH SELECT").subtitle("что показывает сундук").icon(UiIcons.glitch()).compact();
        final TileGlitchChest chest = chest();
        if (chest == null) {
            ui.note(Ui.RED, "Сундука здесь больше нет.");
            return;
        }
        ui.note(Ui.PURPLE, "Каждый игрок видит в сундуке свои вещи. Отметь, какие виды пропавших предметов сюда попадают.");
        for (final String c : GlitchStorage.CATEGORIES) {
            ui.toggle(GlitchStorage.title(c), hint(c), chest.categories().contains(c), on -> {
                if (chest.categories().contains(c) != on) {
                    chest.toggle(c);
                }
            });
        }
    }
}
