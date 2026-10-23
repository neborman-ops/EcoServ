package com.ecoserv.dev;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Настройка маркера (2.0): вид, имя, данные, поворот. */
public class MarkerScreen extends UiScreen {

    private final BlockPos pos;

    public MarkerScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        TileEntity te = player.world.getTileEntity(pos);
        IBlockState st = player.world.getBlockState(pos);
        if (!(te instanceof TileMarker) || st.getBlock() != com.ecoserv.v2.ModRegistry2.MARKER) {
            ui.title("Маркер").note(Ui.RED, "Маркера здесь больше нет.");
            return;
        }
        final TileMarker m = (TileMarker) te;
        int type = st.getValue(BlockMarker.TYPE);
        ui.title("Маркер").subtitle(BlockMarker.TITLES[type]).icon(new ItemStack(com.ecoserv.v2.ModRegistry2.MARKER, 1, type));
        ui.text("&7" + BlockMarker.HINTS[type]);
        Ui.Select sel = ui.select("Вид", "", Integer.toString(type), v -> {
            int t = Integer.parseInt(v);
            player.world.setBlockState(pos, com.ecoserv.v2.ModRegistry2.MARKER.getDefaultState().withProperty(BlockMarker.TYPE, t), 3);
            TileEntity n = player.world.getTileEntity(pos);
            if (n instanceof TileMarker) {
                ((TileMarker) n).name = m.name;
                ((TileMarker) n).data = m.data;
                ((TileMarker) n).yaw = m.yaw;
                ((TileMarker) n).changed();
            }
        });
        for (int i = 0; i < 16; i++) {
            sel.option(Integer.toString(i), BlockMarker.TITLES[i], BlockMarker.HINTS[i]);
        }
        ui.field("Имя", "Чтобы находить маркер в сценариях, катсценах, играх", m.name, v -> {
            m.name = v.trim();
            m.changed();
        }).max(48);
        ui.field("Данные", BlockMarker.HINTS[type], m.data, v -> {
            m.data = v.trim();
            m.changed();
        }).clearable().max(300);
        ui.number("Поворот (°)", "Куда смотрит (для точек появления и камеры)", Math.round(m.yaw), -360, 360, v -> {
            m.yaw = v;
            m.changed();
        });
        ui.buttons().add("Поворот = как я смотрю", Ui.BLUE, () -> {
            m.yaw = player.rotationYaw;
            m.changed();
        });
        ui.text("&8" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }
}
