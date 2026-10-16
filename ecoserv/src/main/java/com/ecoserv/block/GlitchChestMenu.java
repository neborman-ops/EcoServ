package com.ecoserv.block;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.subworld.GlitchStorage;
import com.ecoserv.tile.TileGlitchChest;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** ГЛИТЧ-сундук: свои пропавшие предметы игрока (виды — по настройке сундука). ЛКМ — забрать. */
public class GlitchChestMenu extends ChestMenu {

    private static final int PER_PAGE = 45;

    private final BlockPos pos;
    private int page;
    private final List<Object[]> shown = new ArrayList<Object[]>();

    public GlitchChestMenu(EntityPlayerMP player, BlockPos pos, int page) {
        super(player, "ГЛИТЧ-сундук", 6);
        this.pos = pos;
        this.page = page;
    }

    private Set<String> categories() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileGlitchChest ? ((TileGlitchChest) te).categories() : null;
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(5, Icons.filler());
        Set<String> cats = categories();
        shown.clear();
        if (cats != null) {
            shown.addAll(GlitchStorage.view(player, cats));
        }
        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < shown.size(); i++) {
            GlitchStorage.Entry e = (GlitchStorage.Entry) shown.get(page * PER_PAGE + i)[1];
            ItemStack icon = e.stack.copy();
            List<String> lore = new ArrayList<String>();
            lore.add("&d" + GlitchStorage.title(e.category) + (e.note.isEmpty() ? "" : " &7(" + e.note + "&7)"));
            lore.add("&7" + Fmt.timestamp(e.time));
            lore.add("&8ЛКМ — забрать");
            setButton(i, Icons.build(icon, null, lore.toArray(new String[0])));
        }
        List<String> info = new ArrayList<String>();
        info.add("&7Здесь твои пропавшие предметы:");
        if (cats != null) {
            for (String c : cats) {
                info.add(" &d" + GlitchStorage.title(c));
            }
        }
        info.add("&7Всего: &f" + shown.size());
        info.add("&8Shift+ЛКМ — забрать всё на странице");
        setButton(49, Icons.of(net.minecraft.init.Items.ENDER_EYE, "&5ГЛИТЧ-сундук", info.toArray(new String[0])));
        if (page > 0) {
            setButton(45, Icons.arrowLeft("&eНазад"));
        }
        if (page < pages - 1) {
            setButton(53, Icons.arrowRight("&eДальше"));
        }
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (slot == 45 && page > 0) {
            page--;
            return;
        }
        if (slot == 53) {
            page++;
            return;
        }
        if (slot == 49 && type == ClickType.QUICK_MOVE) {
            int taken = 0;
            for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < shown.size(); i++) {
                if (take(shown.get(page * PER_PAGE + i))) {
                    taken++;
                } else {
                    break;
                }
            }
            if (taken > 0) {
                ChatUtil.actionBar(player, "&aЗабрано: &f" + taken);
            }
            return;
        }
        int index = page * PER_PAGE + slot;
        if (slot < PER_PAGE && index < shown.size()) {
            take(shown.get(index));
        }
    }

    /** Забрать запись в инвентарь (только если влезает целиком). */
    private boolean take(Object[] pair) {
        String owner = (String) pair[0];
        GlitchStorage.Entry e = (GlitchStorage.Entry) pair[1];
        ItemStack stack = e.stack.copy();
        if (!fits(stack)) {
            ChatUtil.actionBar(player, "&cИнвентарь полон.");
            return false;
        }
        if (!GlitchStorage.take(owner, e)) {
            return false;
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
