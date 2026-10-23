package com.ecoserv.tile;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import java.util.UUID;

/**
 * Сундук добычи: образец содержимого (заполняет админ) и настройки. Личные копии игроков
 * хранятся отдельно (world/ecoserv/loot/&lt;id&gt;.dat), чтобы не раздувать данные чанка.
 */
public class TileLootChest extends TileEntity {

    public static final int SIZE = 27;

    private final InventoryBasic template = new InventoryBasic("loot", false, SIZE);
    private String chestId = "";
    private String name = "Сундук добычи";
    /** once — один раз на игрока, refill — снова наполняется через refillMinutes. */
    private String mode = "once";
    private int refillMinutes = 60;

    public InventoryBasic getTemplate() {
        return template;
    }

    public String getChestId() {
        if (chestId.isEmpty()) {
            chestId = UUID.randomUUID().toString();
            markDirty();
        }
        return chestId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.trim().isEmpty() ? "Сундук добычи" : name.trim();
        markDirty();
    }

    public String getMode() {
        return mode;
    }

    public void toggleMode() {
        mode = "once".equals(mode) ? "refill" : "once";
        markDirty();
    }

    public int getRefillMinutes() {
        return refillMinutes;
    }

    public void setRefillMinutes(int minutes) {
        refillMinutes = Math.max(1, minutes);
        markDirty();
    }

    public boolean isTemplateEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (!template.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        chestId = nbt.getString("chestId");
        name = nbt.hasKey("name") ? nbt.getString("name") : name;
        mode = nbt.hasKey("mode") ? nbt.getString("mode") : "once";
        refillMinutes = nbt.hasKey("refill") ? nbt.getInteger("refill") : 60;
        readInventory(template, nbt.getTagList("template", 10));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("chestId", chestId);
        nbt.setString("name", name);
        nbt.setString("mode", mode);
        nbt.setInteger("refill", refillMinutes);
        nbt.setTag("template", writeInventory(template));
        return nbt;
    }

    public static NBTTagList writeInventory(InventoryBasic inv) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
                tag.setByte("Slot", (byte) i);
                list.appendTag(tag);
            }
        }
        return list;
    }

    public static void readInventory(InventoryBasic inv, NBTTagList list) {
        inv.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getByte("Slot") & 255;
            if (slot < inv.getSizeInventory()) {
                inv.setInventorySlotContents(slot, new ItemStack(tag));
            }
        }
    }
}
