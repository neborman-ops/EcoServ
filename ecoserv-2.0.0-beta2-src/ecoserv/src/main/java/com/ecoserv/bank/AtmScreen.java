package com.ecoserv.bank;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Настройка банкомата (Shift+ПКМ владельцем): подпись и чей банк. */
public class AtmScreen extends UiScreen {

    private final BlockPos pos;

    public AtmScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos;
    }

    private TileBank tile() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileBank ? (TileBank) te : null;
    }

    @Override
    protected void build(Ui ui) {
        final TileBank t = tile();
        ui.title("Банкомат").icon(new ItemStack(Blocks.GOLD_BLOCK));
        if (t == null) {
            ui.note(Ui.RED, "Банкомата больше нет.");
            return;
        }
        ui.field("Подпись", "Видна в заголовке окна", t.getLabel(), t::setLabel).max(32);
        Ui.Select s = ui.select("Банк", "Чей банк обслуживает этот банкомат: его карточка откроется первой",
                t.getBank(), v -> t.setBank(v));
        boolean admin = Perms.isAdmin(player);
        for (Bank b : BankManager.banks()) {
            if (b.server() || admin || player.getUniqueID().toString().equals(b.owner)) {
                s.option(b.id, UiUtil.plain(b.name), b.server() ? "банк сервера" : "банк игрока " + b.ownerName, UiUtil.icon(b.icon));
            }
        }
        ui.buttons().add("Открыть банк", Ui.BLUE, () -> BankScreen.open(player, pos, t.getBank()));
    }
}
