package com.ecoserv.block;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.tile.TilePlayerSensor;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Настройка датчика игрока: радиус, режим сигнала, роли. */
public class SensorMenu extends ChestMenu {

    private final BlockPos pos;

    public SensorMenu(EntityPlayerMP player, BlockPos pos) {
        super(player, "Датчик игрока", 4);
        this.pos = pos.toImmutable();
    }

    private TilePlayerSensor tile() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TilePlayerSensor ? (TilePlayerSensor) te : null;
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, Icons.filler());
        }
        TilePlayerSensor s = tile();
        if (s == null) {
            return;
        }
        setButton(1, Icons.red("&c− радиус"));
        setButton(2, Icons.of(Items.COMPASS, "&fРадиус: &e" + s.getRadius() + " бл.", "&7от 1 до 16"));
        setButton(3, Icons.green("&a+ радиус"));
        String mode = "count".equals(s.getMode()) ? "число игроков" : "distance".equals(s.getMode()) ? "чем ближе, тем сильнее" : "15, если рядом кто-то есть";
        setButton(5, Icons.of(Items.REDSTONE, "&fСигнал: &e" + mode, "&7Сейчас: &f" + s.getOutput(), "&8Клик — сменить режим"));
        setButton(7, Icons.of(Items.DYE, s.isInvert() ? 1 : 10,
                s.isInvert() ? "&cРеагирует на игроков БЕЗ выбранных ролей" : "&aРеагирует на игроков с выбранными ролями",
                "&7Если роли не выбраны — на всех", "&8Клик — сменить"));
        List<Role> roles = RoleManager.all();
        for (int i = 0; i < roles.size() && i < 18; i++) {
            Role role = roles.get(i);
            boolean on = s.getRoles().contains(role.id);
            ItemStack icon = Icons.of(Items.NAME_TAG, (on ? "&a✔ " : "&7") + role.title(), on ? "&eКлик — убрать" : "&eКлик — выбрать");
            if (on) {
                Icons.glint(icon);
            }
            setButton(18 + i, icon);
        }
        setButton(13, Icons.of(Items.BOOK, "&6Датчик игрока",
                "&7Даёт редстоун-сигнал, когда рядом игрок.",
                "&7Роли ниже — фильтр (необязательно)."));
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        TilePlayerSensor s = tile();
        if (s == null || (!player.getUniqueID().equals(s.getOwner()) && !Perms.isAdmin(player))) {
            player.closeScreen();
            return;
        }
        if (slot == 1) {
            s.setRadius(s.getRadius() - 1);
        } else if (slot == 3) {
            s.setRadius(s.getRadius() + 1);
        } else if (slot == 5) {
            s.nextMode();
        } else if (slot == 7) {
            s.setInvert(!s.isInvert());
        } else if (slot >= 18 && slot < 36) {
            List<Role> roles = RoleManager.all();
            int index = slot - 18;
            if (index < roles.size()) {
                s.toggleRole(roles.get(index).id);
            }
        }
    }
}
