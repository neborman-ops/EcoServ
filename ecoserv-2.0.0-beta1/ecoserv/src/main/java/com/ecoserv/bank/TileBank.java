package com.ecoserv.bank;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import java.util.UUID;

/** Банкомат (2.0): кто поставил и как подписан. Деньги в блоке не хранятся — только доступ к своим счетам. */
public class TileBank extends TileEntity {

    private String owner = "";
    private String ownerName = "";
    private String label = "Банкомат";

    public void setOwner(EntityPlayer player) {
        owner = player.getUniqueID().toString();
        ownerName = player.getName();
        markDirty();
    }

    public boolean isOwner(EntityPlayer player) {
        return player != null && owner.equals(player.getUniqueID().toString());
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null || label.trim().isEmpty() ? "Банкомат" : label.trim();
        markDirty();
    }

    public UUID ownerId() {
        try {
            return owner.isEmpty() ? null : UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        owner = nbt.getString("owner");
        ownerName = nbt.getString("ownerName");
        label = nbt.hasKey("label") ? nbt.getString("label") : "Банкомат";
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("owner", owner);
        nbt.setString("ownerName", ownerName);
        nbt.setString("label", label);
        return nbt;
    }
}
