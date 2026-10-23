package com.ecoserv.block;

import com.ecoserv.subworld.GlitchStorage;
import com.ecoserv.tile.TileGlitchChest;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Fmt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** ГЛИТЧ-сундук: свои пропавшие предметы игрока (виды — по настройке сундука). Клик — забрать. */
public class GlitchChestScreen extends UiScreen {

    private final BlockPos pos;

    public GlitchChestScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos.toImmutable();
    }

    private Set<String> categories() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileGlitchChest ? ((TileGlitchChest) te).categories() : null;
    }

    @Override
    protected boolean canView() {
        return player.getDistanceSq(pos) < 64.0D && player.world.getTileEntity(pos) instanceof TileGlitchChest;
    }

    @Override
    protected void build(Ui ui) {
        ui.title("ГЛИТЧ-сундук").icon(new ItemStack(Items.ENDER_EYE)).compact();
        Set<String> cats = categories();
        if (cats == null) {
            ui.note(Ui.RED, "Сундука здесь больше нет.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String c : cats) {
            sb.append(GlitchStorage.title(c)).append(", ");
        }
        ui.note(Ui.PURPLE, "Здесь твои пропавшие предметы" + (sb.length() > 2 ? ": " + sb.substring(0, sb.length() - 2) : "")
                + ". Каждый игрок видит в этом сундуке свои вещи.");
        final List<Object[]> shown = new ArrayList<Object[]>(GlitchStorage.view(player, cats));
        ui.header("Предметы", Integer.toString(shown.size()));
        if (shown.isEmpty()) {
            ui.text("&8Пусто.");
            return;
        }
        Ui.Grid g = ui.grid();
        for (final Object[] pair : shown) {
            GlitchStorage.Entry e = (GlitchStorage.Entry) pair[1];
            g.cell(e.stack, false, ev -> {
                if (!take(pair)) {
                    throw new UiError("Инвентарь полон.");
                }
            }, "&d" + GlitchStorage.title(e.category) + (e.note.isEmpty() ? "" : " &7(" + e.note + "&7)"),
                    "&7" + Fmt.timestamp(e.time), "&8Клик — забрать");
        }
        ui.buttons().add("Забрать всё, что влезет", Ui.GREEN, () -> {
            int taken = 0;
            for (Object[] pair : shown) {
                if (!take(pair)) {
                    break;
                }
                taken++;
            }
            toast(taken > 0 ? "Забрано: " + taken : "Инвентарь полон.");
        });
    }

    /** Забрать запись в инвентарь (только если влезает целиком). */
    private boolean take(Object[] pair) {
        String owner = (String) pair[0];
        GlitchStorage.Entry e = (GlitchStorage.Entry) pair[1];
        ItemStack stack = e.stack.copy();
        if (!fits(stack)) {
            return false;
        }
        if (!GlitchStorage.take(owner, e)) {
            return true;
        }
        player.inventory.addItemStackToInventory(stack);
        if (!stack.isEmpty()) {
            player.dropItem(stack, false);
        }
        player.inventoryContainer.detectAndSendChanges();
        return true;
    }

    private boolean fits(ItemStack stack) {
        int room = 0;
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack s = player.inventory.mainInventory.get(i);
            if (s.isEmpty()) {
                return true;
            }
            if (ItemStack.areItemsEqual(s, stack) && ItemStack.areItemStackTagsEqual(s, stack)) {
                room += s.getMaxStackSize() - s.getCount();
            }
        }
        return room >= stack.getCount();
    }
}
