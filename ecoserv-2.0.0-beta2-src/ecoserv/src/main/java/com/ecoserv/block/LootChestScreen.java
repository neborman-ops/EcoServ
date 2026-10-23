package com.ecoserv.block;

import com.ecoserv.content.LootStore;
import com.ecoserv.tile.TileLootChest;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Сундук добычи (админ): название, режим, пополнение, образец содержимого. */
public class LootChestScreen extends UiScreen {

    private final BlockPos pos;

    public LootChestScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos.toImmutable();
    }

    private TileLootChest tile() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileLootChest ? (TileLootChest) te : null;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player) && tile() != null;
    }

    @Override
    protected void build(Ui ui) {
        ui.title("Сундук добычи").icon(UiIcons.vending()).compact();
        final TileLootChest chest = tile();
        if (chest == null) {
            ui.note(Ui.RED, "Сундука здесь больше нет.");
            return;
        }
        ui.note(Ui.BLUE, "Положи предметы в образец — каждый игрок получит свою копию. Сам образец игроки не видят.");
        ui.field("Название", "Показывается в заголовке сундука", chest.getName(), v -> {
            chest.setName(v);
            chest.markDirty();
        }).max(48);
        boolean once = "once".equals(chest.getMode());
        ui.select("Режим", "", once ? "once" : "refill", v -> {
            boolean wantOnce = "once".equals(v);
            if (wantOnce != "once".equals(chest.getMode())) {
                chest.toggleMode();
            }
        }).option("once", "Один раз на игрока", "забрал — больше не наполняется")
                .option("refill", "Пополняется", "через заданное время снова полный");
        if (!once) {
            ui.number("Пополнение через (минут)", "", chest.getRefillMinutes(), 1, 60 * 24 * 365, v -> chest.setRefillMinutes((int) v));
        }
        ui.header("Образец", chest.isTemplateEmpty() ? "&eпустой" : "");
        Ui.Buttons b = ui.buttons();
        b.add("Изменить образец", Ui.BLUE, () -> {
            if (tile() == null) {
                throw new UiError("Сундука здесь больше нет.");
            }
            new LootEditMenu(player, pos).open();
        }).tip("Откроется сундук на 27 слотов: положи туда добычу");
        b.add("Выдать заново всем", Ui.YELLOW, () -> {
            LootStore.resetAll(chest.getChestId());
            toast("Личные копии сброшены — добыча снова доступна всем.");
        }).confirm("Сбросить личные копии у всех игроков — каждый получит добычу снова?");
    }
}
