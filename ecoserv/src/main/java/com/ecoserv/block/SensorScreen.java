package com.ecoserv.block;

import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.tile.TilePlayerSensor;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Настройка датчика игрока: радиус, режим сигнала, роли. */
public class SensorScreen extends UiScreen {

    private final BlockPos pos;

    public SensorScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos.toImmutable();
    }

    private TilePlayerSensor tile() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TilePlayerSensor ? (TilePlayerSensor) te : null;
    }

    @Override
    protected boolean canView() {
        TilePlayerSensor s = tile();
        return s != null && (player.getUniqueID().equals(s.getOwner()) || Perms.isAdmin(player));
    }

    @Override
    protected int refreshSeconds() {
        return 1;
    }

    @Override
    protected void build(Ui ui) {
        ui.title("Датчик игрока").icon(new ItemStack(Items.REDSTONE)).compact();
        final TilePlayerSensor s = tile();
        if (s == null) {
            ui.note(Ui.RED, "Датчика здесь больше нет.");
            return;
        }
        ui.note(Ui.BLUE, "Даёт редстоун-сигнал, когда рядом игрок. Роли ниже — фильтр (необязательно).");
        ui.bar("Сигнал сейчас: " + s.getOutput(), s.getOutput(), 15, Ui.RED);
        ui.number("Радиус", "От 1 до 16 блоков", s.getRadius(), 1, 16, v -> s.setRadius((int) v));
        String mode = s.getMode();
        ui.select("Сигнал", "", mode, v -> {
            for (int i = 0; i < 3 && !v.equals(s.getMode()); i++) {
                s.nextMode();
            }
        }).option("any", "15, если рядом кто-то есть", "")
                .option("count", "Число игроков", "")
                .option("distance", "Чем ближе, тем сильнее", "");
        ui.select("Роли", "", s.isInvert() ? "without" : "with", v -> s.setInvert("without".equals(v)))
                .option("with", "Реагирует на игроков с выбранными ролями", "если роли не выбраны — на всех")
                .option("without", "Реагирует на игроков БЕЗ выбранных ролей", "");
        List<Role> roles = RoleManager.all();
        for (final Role role : roles) {
            ui.toggle(role.title(), "&8" + role.id, s.getRoles().contains(role.id), on -> {
                if (s.getRoles().contains(role.id) != on) {
                    s.toggleRole(role.id);
                }
            });
        }
    }
}
