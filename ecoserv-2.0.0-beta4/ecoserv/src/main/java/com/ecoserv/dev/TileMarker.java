package com.ecoserv.dev;

import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;

/** Маркер (2.0): имя, данные (зависит от вида), поворот. Звуковой маркер проигрывает звук рядом с игроками. */
public class TileMarker extends TileEntity implements ITickable {

    public String name = "";
    public String data = "";
    public float yaw;
    private int timer;

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (getBlockMetadata() == BlockMarker.SOUND && !data.isEmpty() && ++timer % 100 == 0) {
            String[] x = data.trim().split("\\s+");
            SoundEvent s;
            try {
                s = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(x[0]));
            } catch (Exception e) {
                s = null;
            }
            if (s != null && world.getClosestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 24.0D, false) != null) {
                float vol = x.length > 1 ? parse(x[1], 1.0F) : 1.0F;
                world.playSound(null, pos, s, SoundCategory.AMBIENT, vol, 1.0F);
            }
        }
    }

    private static float parse(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        read(nbt);
    }

    private void read(NBTTagCompound nbt) {
        name = nbt.getString("name");
        data = nbt.getString("data");
        yaw = nbt.getFloat("yaw");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("name", name);
        nbt.setString("data", data);
        nbt.setFloat("yaw", yaw);
        return nbt;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        read(pkt.getNbtCompound());
    }

    /** Для поиска маркеров на арене / в структуре. */
    public static boolean isPlayerNear(TileMarker t, double r) {
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            if (p.world == t.world && p.getDistanceSq(t.pos) < r * r) {
                return true;
            }
        }
        return false;
    }
}
