package com.ecoserv.auction;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;

/** Лот на аукционе: товар одного вида, выставленный игроком. */
public class Listing {

    public long id;
    /** UUID продавца строкой. */
    public String seller = "";
    public String sellerName = "";
    /** NBT одного предмета (count = 1). */
    public String itemNbt = "";
    /** Сколько ещё осталось в лоте. */
    public int count;
    public double pricePerItem;
    public long created = System.currentTimeMillis();
    /** Где стоит блок-склад, чтобы вернуть непроданное. */
    public int dim;
    public long pos;

    private transient ItemStack cached;

    public Listing() {
    }

    public static Listing of(ItemStack stack, int count, double price, String seller, String sellerName,
                             int dim, long pos) {
        Listing listing = new Listing();
        ItemStack single = stack.copy();
        single.setCount(1);
        listing.itemNbt = single.writeToNBT(new NBTTagCompound()).toString();
        listing.count = count;
        listing.pricePerItem = price;
        listing.seller = seller;
        listing.sellerName = sellerName;
        listing.dim = dim;
        listing.pos = pos;
        return listing;
    }

    /** Один предмет лота (для отображения и выдачи). */
    public ItemStack stack() {
        if (cached == null) {
            try {
                NBTTagCompound tag = JsonToNBT.getTagFromJson(itemNbt);
                cached = new ItemStack(tag);
            } catch (Exception e) {
                cached = ItemStack.EMPTY;
            }
        }
        return cached;
    }

    public void invalidate() {
        cached = null;
    }

    public String displayName() {
        ItemStack stack = stack();
        return stack.isEmpty() ? "?" : stack.getDisplayName();
    }

    /** Совпадает ли лот с предметом и ценой (для слияния одинаковых лотов). */
    public boolean matches(ItemStack stack, double price, String seller) {
        if (!this.seller.equals(seller) || Math.abs(this.pricePerItem - price) > 0.0001D) {
            return false;
        }
        ItemStack mine = stack();
        if (mine.isEmpty() || stack.isEmpty() || mine.getItem() != stack.getItem()) {
            return false;
        }
        if (mine.getItemDamage() != stack.getItemDamage()) {
            return false;
        }
        return String.valueOf(mine.getTagCompound()).equals(String.valueOf(stack.getTagCompound()));
    }

    public long totalPrice(int amount) {
        return (long) Math.ceil(pricePerItem * amount);
    }
}
