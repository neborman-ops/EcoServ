package com.ecoserv.block;

import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.tile.TileRoleBarrier;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
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
public class RoleBarrierScreen extends UiScreen {

    private static final int MAX_CONNECTED = 4096;
    private final BlockPos pos;

    public RoleBarrierScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos.toImmutable();
    }

    private TileRoleBarrier tile() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileRoleBarrier ? (TileRoleBarrier) te : null;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player) && tile() != null;
    }

    @Override
    protected void build(Ui ui) {
        ui.title("Барьер ролей").icon(UiIcons.role()).compact();
        final TileRoleBarrier te = tile();
        if (te == null) {
            ui.note(Ui.RED, "Барьер сломан.");
            return;
        }
        ui.note(Ui.BLUE, "Игроки без нужной роли (и мобы) не проходят сквозь барьер. Админы проходят всегда.");
        ui.select("Режим", "", te.isInvert() ? "deny" : "allow", v -> te.setInvert("deny".equals(v)))
                .option("allow", "Пускать только выбранные роли", "остальных не пускать")
                .option("deny", "НЕ пускать выбранные роли", "остальных пускать");
        List<Role> roles = RoleManager.all();
        ui.header("Роли", te.getRoles().size() + " выбрано");
        if (roles.isEmpty()) {
            ui.note(Ui.YELLOW, "Ролей пока нет — создай их: админ-панель → «Роли и теги».");
        }
        if (te.getRoles().isEmpty() && !te.isInvert()) {
            ui.note(Ui.YELLOW, "Ни одна роль не выбрана — барьер пока никого не пускает.");
        }
        for (final Role role : roles) {
            ui.toggle(role.title(), "&8" + role.id, te.getRoles().contains(role.id), on -> {
                if (te.getRoles().contains(role.id) != on) {
                    te.toggleRole(role.id);
                }
            });
        }
        ui.buttons().add("Применить ко всей стене", Ui.BLUE, () -> toast("Настройки применены к барьерам: " + applyConnected(te)))
                .tip("Те же настройки всем барьерам, которые касаются этого");
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
