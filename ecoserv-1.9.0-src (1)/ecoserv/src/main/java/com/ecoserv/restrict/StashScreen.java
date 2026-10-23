package com.ecoserv.restrict;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** Буфер предметов (/stash): клик по предмету — забрать, если он разрешён в этом мире. */
public class StashScreen extends UiScreen {

    private final UUID owner;
    private final String ownerName;
    private final boolean readOnly;

    public StashScreen(EntityPlayerMP viewer, UUID owner, String ownerName) {
        super(viewer);
        this.owner = owner;
        this.ownerName = ownerName;
        this.readOnly = !viewer.getUniqueID().equals(owner);
    }

    @Override
    protected boolean canView() {
        return !readOnly || Perms.isAdmin(player);
    }

    /** Буфер меняется сам (автовозврат раз в секунду). */
    @Override
    protected int refreshSeconds() {
        return 1;
    }

    @Override
    protected void build(Ui ui) {
        ui.title(readOnly ? "Буфер: " + ownerName : "Буфер предметов").icon(UiIcons.backpack()).compact();
        final List<ItemStack> items = Stash.get(owner);
        ui.note(Ui.BLUE, "Сюда уходят предметы, которые нельзя носить в текущем мире (" + WorldNames.nameOf(player.dimension)
                + "). Они возвращаются сами, когда ты в мире, где они разрешены, и есть свободное место.");
        ui.header("Предметы", Stash.count(owner) + " шт");
        if (items.isEmpty()) {
            ui.text("&8Буфер пуст.");
            return;
        }
        boolean bypass = ItemRules.bypass(player);
        Ui.Grid g = ui.grid();
        for (int i = 0; i < items.size(); i++) {
            final ItemStack stack = items.get(i);
            boolean allowed = bypass || !ItemRules.denies(player.dimension, stack, RestrictAction.CARRY);
            g.cell(stack, false, readOnly ? null : e -> take(stack),
                    readOnly ? "&7(просмотр)" : allowed ? "&aКлик — забрать" : "&cВ этом мире носить нельзя");
        }
        if (!readOnly) {
            ui.buttons().add("Забрать всё, что можно", Ui.GREEN, () -> {
                if (!RestrictionService.isAlive(player)) {
                    throw new UiError("Сначала возродись.");
                }
                RestrictionService.scan(player, true);
            });
        }
    }

    /** Забрать именно этот предмет (буфер мог измениться, пока окно было открыто). */
    private void take(ItemStack stack) {
        if (!RestrictionService.isAlive(player)) {
            throw new UiError("Сначала возродись.");
        }
        List<ItemStack> items = Stash.get(owner);
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == stack) {
                index = i;
            }
        }
        if (index < 0 || stack.isEmpty()) {
            return;
        }
        if (!ItemRules.bypass(player) && ItemRules.denies(player.dimension, stack, RestrictAction.CARRY)) {
            throw new UiError("Этот предмет нельзя носить в этом мире.");
        }
        int inserted = RestrictionService.insert(player.inventory, stack);
        if (inserted <= 0) {
            throw new UiError("Нет места в инвентаре.");
        }
        if (stack.isEmpty()) {
            items.remove(index);
        }
        Stash.markDirty();
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }
}
