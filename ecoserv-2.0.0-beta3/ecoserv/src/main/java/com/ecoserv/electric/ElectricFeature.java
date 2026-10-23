package com.ecoserv.electric;

import com.ecoserv.dev.DevTools;
import com.ecoserv.ui.Ui;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.v2.ModRegistry2;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Подключение электричества: провод, события, раздел /dev. */
public final class ElectricFeature {

    public static final String WIRE_TAG = "ecoserv_wire";
    private static final Map<UUID, BlockPos> FROM = new HashMap<UUID, BlockPos>();

    private ElectricFeature() {
    }

    public static ItemStack wire() {
        ItemStack st = new ItemStack(Items.STRING);
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean(WIRE_TAG, true);
        st.setTagCompound(t);
        st.setStackDisplayName(ChatUtil.color("&eПровод"));
        return st;
    }

    public static boolean isWire(ItemStack st) {
        return !st.isEmpty() && st.hasTagCompound() && st.getTagCompound().getBoolean(WIRE_TAG);
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new Events());
        DevTools.add((ui, p, from) -> {
            ui.header("Электричество");
            ui.row(new ItemStack(ModRegistry2.ELECTRIC, 1, ElectricTypes.SOLAR), "&fЭлектроника (как в Rust)")
                    .line("&7Панели, генератор, батарея, выключатели, логика, лампы, двери, сирена — соединяй проводом")
                    .button("Набор", Ui.GREEN, () -> {
                        for (int i = 0; i < 16; i++) {
                            PlayerUtil.giveOrDrop(p, new ItemStack(ModRegistry2.ELECTRIC, i == ElectricTypes.LAMP ? 4 : 1, i));
                        }
                        PlayerUtil.giveOrDrop(p, wire());
                    });
        });
    }

    public static class Events {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onRight(PlayerInteractEvent.RightClickBlock event) {
            if (!isWire(event.getItemStack())) {
                return;
            }
            TileEntity te = event.getWorld().getTileEntity(event.getPos());
            if (!(te instanceof TileElectric)) {
                return;
            }
            event.setCanceled(true);
            if (event.getWorld().isRemote || event.getHand() != net.minecraft.util.EnumHand.MAIN_HAND
                    || !(event.getEntityPlayer() instanceof EntityPlayerMP)) {
                return;
            }
            EntityPlayerMP p = (EntityPlayerMP) event.getEntityPlayer();
            TileElectric t = (TileElectric) te;
            BlockPos pos = event.getPos().toImmutable();
            if (p.isSneaking()) {
                t.outputs.clear();
                t.changed();
                FROM.remove(p.getUniqueID());
                ChatUtil.actionBar(p, "&7Провода сняты.");
                return;
            }
            BlockPos src = FROM.get(p.getUniqueID());
            if (src == null || src.equals(pos)) {
                FROM.put(p.getUniqueID(), pos);
                ChatUtil.actionBar(p, "&eОткуда: &f" + ElectricTypes.TITLES[t.type()] + " &7— теперь ПКМ по приёмнику");
                return;
            }
            FROM.remove(p.getUniqueID());
            TileEntity ste = event.getWorld().getTileEntity(src);
            if (!(ste instanceof TileElectric)) {
                ChatUtil.actionBar(p, "&cНачало провода пропало.");
                return;
            }
            if (src.distanceSq(pos) > 32 * 32) {
                ChatUtil.actionBar(p, "&cСлишком далеко (провод до 32 блоков).");
                return;
            }
            TileElectric s = (TileElectric) ste;
            int max = ElectricTypes.OUTS[s.type()];
            if (s.outputs.contains(pos)) {
                s.outputs.remove(pos);
                s.changed();
                ChatUtil.actionBar(p, "&7Провод убран.");
                return;
            }
            while (s.outputs.size() >= max) {
                s.outputs.remove(0);
            }
            s.outputs.add(pos);
            s.changed();
            ChatUtil.actionBar(p, "&aСоединено: &f" + ElectricTypes.TITLES[s.type()] + " → " + ElectricTypes.TITLES[t.type()]);
        }
    }
}
