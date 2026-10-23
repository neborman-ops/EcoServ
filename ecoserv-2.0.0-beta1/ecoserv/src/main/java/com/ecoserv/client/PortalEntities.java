package com.ecoserv.client;

import com.ecoserv.network.packet.PacketBlackHole;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Существа в месте назначения, видимые в окне портала (1.9.0). Это «куклы» — не в мире клиента, не тикают;
 * сервер раз в 4 тика присылает позиции, здесь они плавно переезжают, а ноги шагают по скорости.
 */
@SideOnly(Side.CLIENT)
final class PortalEntities {

    static final class Doll {
        final Entity entity;
        final int kind;
        /** Позиции относительно «глаза» места назначения: прошлая, текущая и цель с сервера. */
        double px;
        double py;
        double pz;
        double cx;
        double cy;
        double cz;
        double tx;
        double ty;
        double tz;
        float yaw;
        float pyaw;
        float tyaw;
        float head;
        float phead;
        float thead;
        float pitch;
        long seen;
        boolean fresh = true;

        Doll(Entity entity, int kind) {
            this.entity = entity;
            this.kind = kind;
        }
    }

    final Map<Integer, Doll> dolls = new HashMap<Integer, Doll>();

    void onPacket(List<PacketBlackHole.Ent> ents) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PacketBlackHole.Ent e : ents) {
            Doll d = dolls.get(e.id);
            if (d == null) {
                Entity made = create(e);
                if (made == null) {
                    continue;
                }
                d = new Doll(made, e.kind);
                dolls.put(e.id, d);
            }
            d.tx = e.x;
            d.ty = e.y;
            d.tz = e.z;
            d.tyaw = e.yaw;
            d.thead = e.headYaw;
            d.pitch = e.pitch;
            if (d.fresh) {
                d.px = d.cx = e.x;
                d.py = d.cy = e.y;
                d.pz = d.cz = e.z;
                d.pyaw = d.yaw = e.yaw;
                d.phead = d.head = e.headYaw;
                d.fresh = false;
            }
            d.seen = now;
            apply(d, e);
        }
        Iterator<Doll> it = dolls.values().iterator();
        while (it.hasNext()) {
            if (now - it.next().seen > 1500L) {
                it.remove();
            }
        }
    }

    @Nullable
    private static Entity create(PacketBlackHole.Ent e) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (e.kind == 0) {
                UUID id;
                try {
                    id = UUID.fromString(e.uuid);
                } catch (IllegalArgumentException x) {
                    id = UUID.randomUUID();
                }
                return new EntityOtherPlayerMP(mc.world, new GameProfile(id, e.name));
            }
            if (e.kind == 2) {
                return new EntityItem(mc.world);
            }
            return EntityList.createEntityByIDFromName(new ResourceLocation(e.type), mc.world);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void apply(Doll d, PacketBlackHole.Ent e) {
        Entity en = d.entity;
        en.setSneaking((e.flags & 1) != 0);
        if (en instanceof EntityAgeable) {
            ((EntityAgeable) en).setGrowingAge((e.flags & 4) != 0 ? -24000 : 0);
        }
        if (en instanceof EntityItem && e.gear.length > 0 && e.gear[0] != null) {
            ((EntityItem) en).setItem(e.gear[0]);
        } else if (en instanceof EntityLivingBase && e.gear.length > 0) {
            EntityEquipmentSlot[] slots = EntityEquipmentSlot.values();
            for (int i = 0; i < slots.length && i < e.gear.length; i++) {
                ItemStack s = e.gear[i];
                ((EntityLivingBase) en).setItemStackToSlot(slots[i], s == null ? ItemStack.EMPTY : s);
            }
        }
    }

    /** Раз в тик клиента: шаг к цели (сервер шлёт раз в 4 тика — догоняем за 4 тика). */
    void tick() {
        for (Doll d : dolls.values()) {
            d.px = d.cx;
            d.py = d.cy;
            d.pz = d.cz;
            d.pyaw = d.yaw;
            d.phead = d.head;
            d.cx += (d.tx - d.cx) * 0.4D;
            d.cy += (d.ty - d.cy) * 0.4D;
            d.cz += (d.tz - d.cz) * 0.4D;
            d.yaw = d.yaw + wrap(d.tyaw - d.yaw) * 0.4F;
            d.head = d.head + wrap(d.thead - d.head) * 0.4F;
            if (d.entity instanceof EntityLivingBase) {
                EntityLivingBase l = (EntityLivingBase) d.entity;
                double vx = d.cx - d.px;
                double vz = d.cz - d.pz;
                float speed = (float) Math.min(1.0D, Math.sqrt(vx * vx + vz * vz) * 4.0D);
                l.prevLimbSwingAmount = l.limbSwingAmount;
                l.limbSwingAmount += (speed - l.limbSwingAmount) * 0.4F;
                l.limbSwing += l.limbSwingAmount;
            }
            d.entity.ticksExisted++;
        }
    }

    private static float wrap(float a) {
        a %= 360.0F;
        if (a >= 180.0F) {
            a -= 360.0F;
        }
        if (a < -180.0F) {
            a += 360.0F;
        }
        return a;
    }

    List<Doll> all() {
        return new ArrayList<Doll>(dolls.values());
    }

    void clear() {
        dolls.clear();
    }
}
