package com.ecoserv.block;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.tile.TileRoleBarrier;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Настройка барьера ролей: какие роли проходят, режим «наоборот», применить ко всей стене. */
public class RoleBarrierMenu extends ChestMenu {

    private static final int MAX_CONNECTED = 4096;
    private final BlockPos pos;

    public RoleBarrierMenu(EntityPlayerMP player, BlockPos pos) {
        super(player, "Барьер ролей", 6);
        this.pos = pos.toImmutable();
    }

    private TileRoleBarrier tile() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileRoleBarrier ? (TileRoleBarrier) te : null;
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(5, Icons.filler());
        TileRoleBarrier te = tile();
        if (te == null) {
            setButton(22, Icons.red("&cБарьер сломан"));
            setButton(53, Icons.close());
            return;
        }
        List<Role> roles = RoleManager.all();
        for (int i = 0; i < roles.size() && i < 45; i++) {
            Role role = roles.get(i);
            boolean on = te.getRoles().contains(role.id);
            ItemStack icon = Icons.of(Items.NAME_TAG, (on ? "&a✔ " : "&7") + role.title(),
                    "&7id: &f" + role.id,
                    on ? "&eКлик — убрать" : "&eКлик — выбрать");
            if (on) {
                Icons.glint(icon);
            }
            setButton(i, icon);
        }
        if (roles.isEmpty()) {
            setButton(22, Icons.of(Items.PAPER, "&7Ролей пока нет", "&fСоздать: /role create <id> <название>"));
        }
        setButton(45, Icons.of(Items.DYE, te.isInvert() ? 1 : 10,
                te.isInvert() ? "&cРежим: НЕ пускать выбранные роли" : "&aРежим: пускать только выбранные роли",
                "&8Клик — сменить режим"));
        setButton(47, Icons.of(Items.ENDER_EYE, "&fПрименить ко всей стене",
                "&7Те же настройки всем барьерам,", "&7которые касаются этого"));
        setButton(49, Icons.of(Items.BOOK, "&6Барьер ролей",
                "&7Игроки без нужной роли (и мобы) не проходят.",
                "&7Админы проходят всегда.",
                "&7Выбрано ролей: &f" + te.getRoles().size()
                        + (te.getRoles().isEmpty() && !te.isInvert() ? " &c(пока никого не пускает)" : "")));
        setButton(53, Icons.close());
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        TileRoleBarrier te = tile();
        if (te == null || !Perms.isAdmin(player)) {
            player.closeScreen();
            return;
        }
        if (slot == 53) {
            player.closeScreen();
            return;
        }
        if (slot == 45) {
            te.setInvert(!te.isInvert());
            return;
        }
        if (slot == 47) {
            int n = applyConnected(te);
            ChatUtil.send(player, "&aНастройки применены к барьерам: &f" + n);
            return;
        }
        List<Role> roles = RoleManager.all();
        if (slot < 45 && slot < roles.size()) {
            te.toggleRole(roles.get(slot).id);
        }
    }

    /** Обход соединённых барьеров (по граням) и копирование настроек. */
    private int applyConnected(TileRoleBarrier source) {
        Set<BlockPos> seen = new HashSet<BlockPos>();
        Deque<BlockPos> queue = new ArrayDeque<BlockPos>();
        queue.add(pos);
        seen.add(pos);
        int count = 0;
        while (!queue.isEmpty() && seen.size() < MAX_CONNECTED) {
            BlockPos p = queue.poll();
            for (EnumFacing side : EnumFacing.values()) {
                BlockPos next = p.offset(side);
                if (seen.contains(next) || !player.world.isBlockLoaded(next)) {
                    continue;
                }
                TileEntity te = player.world.getTileEntity(next);
                if (te instanceof TileRoleBarrier) {
                    seen.add(next);
                    queue.add(next);
                    ((TileRoleBarrier) te).copyFrom(source);
                    count++;
                }
            }
        }
        return count;
    }
}
