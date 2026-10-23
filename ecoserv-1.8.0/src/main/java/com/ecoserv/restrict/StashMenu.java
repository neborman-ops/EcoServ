package com.ecoserv.restrict;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import java.util.List;
import java.util.UUID;

/** Просмотр буфера (/stash): клик по предмету забирает его, если он разрешён в этом мире. */
public class StashMenu extends ChestMenu {

    private static final int PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_TAKE_ALL = 47;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_CLOSE = 51;
    private static final int SLOT_NEXT = 53;

    private final UUID owner;
    private final boolean readOnly;
    private int page;
    private int ticks;

    public StashMenu(EntityPlayerMP viewer, UUID owner, String ownerName) {
        super(viewer, viewer.getUniqueID().equals(owner) ? "Буфер предметов" : "Буфер: " + ownerName, 6);
        this.owner = owner;
        this.readOnly = !viewer.getUniqueID().equals(owner);
    }

    private List<ItemStack> items() {
        return Stash.get(owner);
    }

    /** Буфер может меняться сам (автовозврат раз в секунду) — перерисовываем окно раз в полсекунды. */
    @Override
    public void detectAndSendChanges() {
        if (++ticks % 10 == 0) {
            buildButtons();
        }
        super.detectAndSendChanges();
    }

    @Override
    protected void buildButtons() {
        List<ItemStack> items = items();
        int pages = Math.max(1, (items.size() + PAGE - 1) / PAGE);
        if (page >= pages) {
            page = pages - 1;
        }
        boolean bypass = ItemRules.bypass(player);
        for (int i = 0; i < PAGE; i++) {
            int index = page * PAGE + i;
            if (index >= items.size()) {
                setButton(i, ItemStack.EMPTY);
                continue;
            }
            ItemStack stack = items.get(index);
            boolean allowed = bypass || !ItemRules.denies(player.dimension, stack, RestrictAction.CARRY);
            setButton(i, withLore(stack, readOnly ? "&7(просмотр)"
                    : allowed ? "&aКлик — забрать в инвентарь" : "&cВ этом мире носить нельзя"));
        }
        for (int i = PAGE; i < topSize; i++) {
            setButton(i, Icons.filler());
        }
        if (page > 0) {
            setButton(SLOT_PREV, Icons.arrowLeft("&fНазад"));
        }
        if (page < pages - 1) {
            setButton(SLOT_NEXT, Icons.arrowRight("&fДальше"));
        }
        if (!readOnly) {
            setButton(SLOT_TAKE_ALL, Icons.of(Item.getItemFromBlock(Blocks.HOPPER), "&aЗабрать всё, что можно",
                    "&7Разрешённое в этом мире — сколько влезет"));
        }
        setButton(SLOT_INFO, Icons.of(Items.BOOK, "&6Буфер: " + Stash.count(owner) + " шт",
                "&7Сюда уходят предметы, которые нельзя",
                "&7носить в текущем мире (" + WorldNames.nameOf(player.dimension) + ").",
                "&7Они возвращаются сами, когда ты в мире,",
                "&7где они разрешены и есть свободное место.",
                "&7Страница " + (page + 1) + "/" + pages));
        setButton(SLOT_CLOSE, Icons.close());
    }

    /** Копия предмета с подсказкой в описании (сам предмет в буфере не меняется). */
    private static ItemStack withLore(ItemStack original, String line) {
        ItemStack copy = original.copy();
        NBTTagCompound tag = copy.hasTagCompound() ? copy.getTagCompound() : new NBTTagCompound();
        NBTTagCompound display = tag.getCompoundTag("display");
        NBTTagList lore = display.hasKey("Lore", Constants.NBT.TAG_LIST)
                ? display.getTagList("Lore", Constants.NBT.TAG_STRING) : new NBTTagList();
        lore.appendTag(new NBTTagString(ChatUtil.color(line)));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        copy.setTagCompound(tag);
        return copy;
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (slot == SLOT_CLOSE) {
            player.closeScreen();
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            page--;
            return;
        }
        if (slot == SLOT_NEXT) {
            page++;
            return;
        }
        if (readOnly || !RestrictionService.isAlive(player)) {
            return;
        }
        if (slot == SLOT_TAKE_ALL) {
            RestrictionService.scan(player, true);
            return;
        }
        if (slot < 0 || slot >= PAGE) {
            return;
        }
        List<ItemStack> items = items();
        int index = page * PAGE + slot;
        if (index >= items.size()) {
            return;
        }
        ItemStack stack = items.get(index);
        if (!ItemRules.bypass(player) && ItemRules.denies(player.dimension, stack, RestrictAction.CARRY)) {
            ChatUtil.actionBar(player, "&cЭтот предмет нельзя носить в этом мире");
            return;
        }
        int inserted = RestrictionService.insert(player.inventory, stack);
        if (inserted <= 0) {
            ChatUtil.actionBar(player, "&eНет места в инвентаре");
            return;
        }
        if (stack.isEmpty()) {
            items.remove(index);
        }
        Stash.markDirty();
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }
}
