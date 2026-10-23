package com.ecoserv.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** Чекпоинт: название (показывается игроку при сохранении). */
public class TileCheckpoint extends TileEntity {

    private String name = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        name = nbt.getString("name");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("name", name);
        return nbt;
    }
}
