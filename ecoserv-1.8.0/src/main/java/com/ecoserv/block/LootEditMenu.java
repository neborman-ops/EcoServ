package com.ecoserv.block;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.tile.TileLootChest;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Админ: образец содержимого сундука добычи (27 слотов) и настройки. */
public class LootEditMenu extends ChestMenu {

    private final BlockPos pos;

    public LootEditMenu(EntityPlayerMP player, BlockPos pos) {
        super(player, "Образец добычи", 4, content(templateOf(player, pos), TileLootChest.SIZE));
        this.pos = pos.toImmutable();
    }

    private static IInventory templateOf(EntityPlayerMP player, BlockPos pos) {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileLootChest ? ((TileLootChest) te).getTemplate() : new InventoryBasic("empty", false, 27);
    }

    private TileLootChest tile() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileLootChest ? (TileLootChest) te : null;
    }

    @Override
    protected void buildButtons() {
        for (int i = 27; i < 36; i++) {
            setButton(i, Icons.filler());
        }
        setButton(31, Icons.of(Items.BOOK, "&6Образец добычи",
                "&7Положи сюда предметы — это образец.",
                "&7Каждый игрок получает свою копию.",
                "&8Клик — назад к настройкам сундука"));
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (tile() == null || !Perms.isAdmin(player)) {
            player.closeScreen();
            return;
        }
        if (slot == 31) {
            new LootChestScreen(player, pos).open();
        }
    }

    @Override
    protected void onClosed() {
        TileLootChest chest = tile();
        if (chest != null) {
            chest.markDirty();
        }
    }
}
