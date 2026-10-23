package com.ecoserv.dev;

import com.ecoserv.script.ScriptRunner;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Блок-программа (2.0): когда сработать (нажали, сигнал редстоуна, игрок рядом, таймер, событие) и что сделать
 * (шаги сценария). Локальный (синий) — шаги для того, кто его запустил; глобальный (красный) — для всех в мире.
 * Может выдавать сигнал редстоуна.
 */
public class TileLogic extends TileEntity implements ITickable {

    public static final String[] TRIGGERS = {"click", "redstone", "near", "timer", "event"};

    private static final Set<TileLogic> LOADED = Collections.newSetFromMap(new WeakHashMap<TileLogic, Boolean>());

    public String name = "";
    public String trigger = "click";
    public String scope = DevZone.LOCAL;
    public int radius = 4;
    public int interval = 100;
    public String eventName = "";
    public String condition = "";
    public List<String> steps = new ArrayList<String>();
    public int pulseTicks = 10;
    public int cooldown = 20;
    public List<String> roles = new ArrayList<String>();
    public boolean enabled = true;

    private int pulse;
    private long lastFire;
    private boolean wasPowered;
    private int timer;
    private final java.util.Set<String> near = new java.util.HashSet<String>();

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

    public boolean global() {
        return DevZone.GLOBAL.equals(scope);
    }

    public int output() {
        return pulse > 0 ? 15 : 0;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (pulse > 0 && --pulse == 0) {
            notifyNeighbors();
        }
        if (!enabled) {
            return;
        }
        switch (trigger) {
            case "redstone": {
                boolean powered = world.isBlockPowered(pos);
                if (powered && !wasPowered) {
                    fire(nearest(), "redstone");
                }
                wasPowered = powered;
                break;
            }
            case "near": {
                if (++timer % 10 != 0) {
                    break;
                }
                List<EntityPlayer> list = world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(pos).grow(radius));
                java.util.Set<String> now = new java.util.HashSet<String>();
                for (EntityPlayer p : list) {
                    if (p instanceof EntityPlayerMP && !p.isSpectator()) {
                        now.add(p.getUniqueID().toString());
                        if (!near.contains(p.getUniqueID().toString())) {
                            fire((EntityPlayerMP) p, "near");
                        }
                    }
                }
                near.clear();
                near.addAll(now);
                break;
            }
            case "timer": {
                if (++timer >= Math.max(5, interval)) {
                    timer = 0;
                    fire(nearest(), "timer");
                }
                break;
            }
            default:
        }
    }

    @Nullable
    private EntityPlayerMP nearest() {
        EntityPlayer p = world.getClosestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 64.0D, false);
        return p instanceof EntityPlayerMP ? (EntityPlayerMP) p : null;
    }

    /** Сработать (игрок — кто запустил; может быть null для таймера без игроков рядом). */
    public void fire(@Nullable EntityPlayerMP who, String why) {
        long now = world.getTotalWorldTime();
        if (now - lastFire < cooldown) {
            return;
        }
        if (who != null && !roles.isEmpty() && !Perms.isAdmin(who) && !com.ecoserv.role.RoleManager.hasAny(who, roles)) {
            return;
        }
        if (who != null && !condition.isEmpty() && !ScriptSteps.condition(who, condition, name)) {
            return;
        }
        lastFire = now;
        if (pulseTicks > 0) {
            pulse = pulseTicks;
            notifyNeighbors();
        }
        String label = "logic_" + (name.isEmpty() ? pos.toLong() : name);
        if (global()) {
            for (EntityPlayerMP p : PlayerUtil.allOnline()) {
                if (com.ecoserv.region.RegionManager.regionDim(p.dimension) == com.ecoserv.region.RegionManager.regionDim(world.provider.getDimension())) {
                    ScriptRunner.startSteps(p, label, steps, name);
                }
            }
        } else if (who != null) {
            ScriptRunner.startSteps(who, label, steps, name);
        }
        if (who != null && !name.isEmpty()) {
            // событие «logic:имя» — для привязок и квестов
            DevManager.fire("logic", who, name);
        }
    }

    private void notifyNeighbors() {
        IBlockState st = world.getBlockState(pos);
        world.notifyNeighborsOfStateChange(pos, st.getBlock(), false);
    }

    /** Событие из DevEvents — блоки с триггером «событие». */
    static void onEvent(String event, @Nullable String context, @Nullable EntityPlayerMP p) {
        if (LOADED.isEmpty()) {
            return;
        }
        String full = context == null || context.isEmpty() ? event : event + ":" + context;
        for (TileLogic t : new ArrayList<TileLogic>(LOADED)) {
            if (t.isInvalid() || !t.enabled || !"event".equals(t.trigger) || t.eventName.isEmpty()) {
                continue;
            }
            if ("logic".equals(event) && t.name.equalsIgnoreCase(context)) {
                continue; // сам себя не запускает
            }
            if (t.eventName.equalsIgnoreCase(event) || t.eventName.equalsIgnoreCase(full)) {
                t.fire(p, "event");
            }
        }
    }

    public void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    // --- NBT ----------------------------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        readData(nbt);
    }

    private void readData(NBTTagCompound nbt) {
        name = nbt.getString("name");
        trigger = nbt.hasKey("trigger") ? nbt.getString("trigger") : "click";
        scope = nbt.hasKey("scope") ? nbt.getString("scope") : DevZone.LOCAL;
        radius = nbt.hasKey("radius") ? nbt.getInteger("radius") : 4;
        interval = nbt.hasKey("interval") ? nbt.getInteger("interval") : 100;
        eventName = nbt.getString("event");
        condition = nbt.getString("cond");
        pulseTicks = nbt.hasKey("pulse") ? nbt.getInteger("pulse") : 10;
        cooldown = nbt.hasKey("cooldown") ? nbt.getInteger("cooldown") : 20;
        enabled = !nbt.hasKey("enabled") || nbt.getBoolean("enabled");
        steps = new ArrayList<String>();
        NBTTagList l = nbt.getTagList("steps", 8);
        for (int i = 0; i < l.tagCount(); i++) {
            steps.add(l.getStringTagAt(i));
        }
        roles = new ArrayList<String>();
        NBTTagList r = nbt.getTagList("roles", 8);
        for (int i = 0; i < r.tagCount(); i++) {
            roles.add(r.getStringTagAt(i));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("name", name);
        nbt.setString("trigger", trigger);
        nbt.setString("scope", scope);
        nbt.setInteger("radius", radius);
        nbt.setInteger("interval", interval);
        nbt.setString("event", eventName);
        nbt.setString("cond", condition);
        nbt.setInteger("pulse", pulseTicks);
        nbt.setInteger("cooldown", cooldown);
        nbt.setBoolean("enabled", enabled);
        NBTTagList l = new NBTTagList();
        for (String s : steps) {
            l.appendTag(new NBTTagString(s));
        }
        nbt.setTag("steps", l);
        NBTTagList r = new NBTTagList();
        for (String s : roles) {
            r.appendTag(new NBTTagString(s));
        }
        nbt.setTag("roles", r);
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
        readData(pkt.getNbtCompound());
    }
}
