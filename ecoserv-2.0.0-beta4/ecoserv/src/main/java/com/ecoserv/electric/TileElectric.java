package com.ecoserv.electric;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Электро-блок (2.0): провода (выходы), настройки и текущий ток. */
public class TileElectric extends TileEntity {

    static final Set<TileElectric> LOADED = Collections.newSetFromMap(new WeakHashMap<TileElectric, Boolean>());

    public final List<BlockPos> outputs = new ArrayList<BlockPos>();
    /** Выключатель включён. */
    public boolean on = true;
    /** Батарея: запас и отдача в секунду. */
    public long energy;
    public int capacity = 20000;
    public int rate = 25;
    /** Генератор: топливо в тиках. */
    public int fuel;
    /** Таймер: период в секундах. */
    public int interval = 2;
    /** Датчик/привод двери: радиус. */
    public int range = 4;
    public String label = "";

    // текущее состояние (считает сеть)
    public int in;
    public int out;
    public int demand;
    public boolean lit;
    int poweredInputs;
    int inputs;

    public int type() {
        return getBlockMetadata() & 15;
    }

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) {
            LOADED.add(this);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnload() {
        LOADED.remove(this);
    }

    public void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    /** Состояние изменилось — обновить вид и свет. */
    void setLit(boolean v) {
        if (lit != v) {
            lit = v;
            changed();
            world.checkLight(pos);
            if (type() == ElectricTypes.RS_OUT) {
                world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), false);
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        readData(nbt);
    }

    void readData(NBTTagCompound nbt) {
        outputs.clear();
        int[] l = nbt.getIntArray("out");
        for (int i = 0; i + 2 < l.length; i += 3) {
            outputs.add(new BlockPos(l[i], l[i + 1], l[i + 2]));
        }
        on = !nbt.hasKey("on") || nbt.getBoolean("on");
        energy = nbt.getLong("energy");
        capacity = nbt.hasKey("cap") ? nbt.getInteger("cap") : 20000;
        rate = nbt.hasKey("rate") ? nbt.getInteger("rate") : 25;
        fuel = nbt.getInteger("fuel");
        interval = nbt.hasKey("interval") ? nbt.getInteger("interval") : 2;
        range = nbt.hasKey("range") ? nbt.getInteger("range") : 4;
        label = nbt.getString("label");
        in = nbt.getInteger("in");
        out = nbt.getInteger("o");
        boolean wasLit = lit;
        lit = nbt.getBoolean("lit");
        if (world != null && world.isRemote && wasLit != lit) {
            world.markBlockRangeForRenderUpdate(pos, pos);
            world.checkLight(pos);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        int[] l = new int[outputs.size() * 3];
        for (int i = 0; i < outputs.size(); i++) {
            BlockPos p = outputs.get(i);
            l[i * 3] = p.getX();
            l[i * 3 + 1] = p.getY();
            l[i * 3 + 2] = p.getZ();
        }
        nbt.setIntArray("out", l);
        nbt.setBoolean("on", on);
        nbt.setLong("energy", energy);
        nbt.setInteger("cap", capacity);
        nbt.setInteger("rate", rate);
        nbt.setInteger("fuel", fuel);
        nbt.setInteger("interval", interval);
        nbt.setInteger("range", range);
        nbt.setString("label", label);
        nbt.setInteger("in", in);
        nbt.setInteger("o", out);
        nbt.setBoolean("lit", lit);
        return nbt;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readData(pkt.getNbtCompound());
    }
}
