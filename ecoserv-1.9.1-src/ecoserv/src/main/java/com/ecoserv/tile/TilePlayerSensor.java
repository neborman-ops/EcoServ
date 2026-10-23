package com.ecoserv.tile;

import com.ecoserv.role.RoleManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Датчик игрока (п. 14): редстоун-сигнал, когда рядом игрок (с нужной ролью).
 * Режимы: any — 15, если есть хоть один; count — число игроков (до 15); distance — чем ближе, тем сильнее.
 */
public class TilePlayerSensor extends TileEntity implements ITickable {

    private int radius = 4;
    private String mode = "any";
    private final List<String> roles = new ArrayList<String>();
    private boolean invert;
    private UUID owner;
    private int output;
    private int timer;

    @Override
    public void update() {
        if (world == null || world.isRemote || ++timer % 10 != 0) {
            return;
        }
        int value = compute();
        if (value != output) {
            output = value;
            markDirty();
            IBlockState state = world.getBlockState(pos);
            world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
        }
    }

    private int compute() {
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(radius);
        List<EntityPlayer> players = world.getEntitiesWithinAABB(EntityPlayer.class, box);
        int count = 0;
        double best = Double.MAX_VALUE;
        for (EntityPlayer p : players) {
            if (p.isSpectator() || !p.isEntityAlive()) {
                continue;
            }
            if (!roles.isEmpty()) {
                boolean has = RoleManager.hasAny(p, roles);
                if (invert == has) {
                    continue;
                }
            }
            double d = Math.sqrt(p.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
            if (d > radius + 0.5D) {
                continue;
            }
            count++;
            best = Math.min(best, d);
        }
        if (count == 0) {
            return 0;
        }
        if ("count".equals(mode)) {
            return Math.min(15, count);
        }
        if ("distance".equals(mode)) {
            return Math.max(1, Math.min(15, (int) Math.round(15.0D * (1.0D - best / (radius + 1.0D)))));
        }
        return 15;
    }

    public int getOutput() {
        return output;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int r) {
        radius = Math.max(1, Math.min(16, r));
        markDirty();
    }

    public String getMode() {
        return mode;
    }

    public void nextMode() {
        mode = "any".equals(mode) ? "count" : "count".equals(mode) ? "distance" : "any";
        markDirty();
    }

    public List<String> getRoles() {
        return roles;
    }

    public void toggleRole(String role) {
        String r = role.toLowerCase(Locale.ROOT);
        if (!roles.remove(r)) {
            roles.add(r);
        }
        markDirty();
    }

    public boolean isInvert() {
        return invert;
    }

    public void setInvert(boolean invert) {
        this.invert = invert;
        markDirty();
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        radius = nbt.hasKey("radius") ? nbt.getInteger("radius") : 4;
        mode = nbt.hasKey("mode") ? nbt.getString("mode") : "any";
        invert = nbt.getBoolean("invert");
        output = nbt.getInteger("output");
        owner = nbt.hasUniqueId("owner") ? nbt.getUniqueId("owner") : null;
        roles.clear();
        NBTTagList list = nbt.getTagList("roles", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            roles.add(list.getStringTagAt(i));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("radius", radius);
        nbt.setString("mode", mode);
        nbt.setBoolean("invert", invert);
        nbt.setInteger("output", output);
        if (owner != null) {
            nbt.setUniqueId("owner", owner);
        }
        NBTTagList list = new NBTTagList();
        for (String r : roles) {
            list.appendTag(new NBTTagString(r));
        }
        nbt.setTag("roles", list);
        return nbt;
    }
}
