package com.ecoserv.tile;

import com.ecoserv.role.RoleManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Настройки барьера ролей: какие роли проходят (или, в режиме «наоборот», не проходят). */
public class TileRoleBarrier extends TileEntity {

    private final List<String> roles = new ArrayList<String>();
    private boolean invert;

    public List<String> getRoles() {
        return roles;
    }

    public boolean isInvert() {
        return invert;
    }

    public void setInvert(boolean invert) {
        this.invert = invert;
        changed();
    }

    public void toggleRole(String role) {
        String r = role.toLowerCase(Locale.ROOT);
        if (!roles.remove(r)) {
            roles.add(r);
        }
        changed();
    }

    public void copyFrom(TileRoleBarrier other) {
        roles.clear();
        roles.addAll(other.roles);
        invert = other.invert;
        changed();
    }

    /** Пропустить ли игрока сквозь барьер. Работает и на клиенте (роли своего игрока). */
    public boolean canPass(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        boolean admin = player.world != null && player.world.isRemote ? RoleManager.clientAdmin
                : com.ecoserv.util.Perms.isAdmin(player);
        if (admin) {
            return true;
        }
        boolean has = RoleManager.hasAny(player, roles);
        return invert ? !has : has;
    }

    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        readSettings(nbt);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        writeSettings(nbt);
        return nbt;
    }

    private void readSettings(NBTTagCompound nbt) {
        roles.clear();
        NBTTagList list = nbt.getTagList("roles", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            String r = list.getStringTagAt(i);
            if (!r.isEmpty()) {
                roles.add(r);
            }
        }
        invert = nbt.getBoolean("invert");
    }

    private void writeSettings(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (String r : roles) {
            list.appendTag(new NBTTagString(r));
        }
        nbt.setTag("roles", list);
        nbt.setBoolean("invert", invert);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound nbt = super.getUpdateTag();
        writeSettings(nbt);
        return nbt;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readSettings(tag);
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 7, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readSettings(pkt.getNbtCompound());
    }
}
