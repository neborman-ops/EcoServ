package com.ecoserv.customitem;

import com.ecoserv.script.ScriptRunner;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Свои предметы: world/ecoserv/custom_items.json. Предмет помечен NBT ecoserv_item = id. */
public final class CustomItems {

    public static final String TAG = "ecoserv_item";
    public static final Pattern ID = Pattern.compile("^[a-z0-9_\\-]{1,32}$");
    private static final Type LIST = new TypeToken<ArrayList<CustomItem>>() {
    }.getType();
    private static final Map<String, CustomItem> ALL = new LinkedHashMap<String, CustomItem>();

    private CustomItems() {
    }

    public static void load() {
        ALL.clear();
        List<CustomItem> list = JsonUtil.readType(DataStore.file("custom_items.json"), LIST, null);
        if (list == null) {
            list = new ArrayList<CustomItem>();
            CustomItem c = new CustomItem();
            c.id = "teleport_pearl";
            c.base = "minecraft:ender_eye";
            c.name = "&dЖемчужина дома";
            c.lore.add("&7ПКМ — домой на спавн");
            c.steps.add("particle portal 40");
            c.steps.add("pcmd spawn");
            c.cooldown = 30;
            list.add(c);
            CustomItem s = new CustomItem();
            s.id = "hero_sword";
            s.base = "minecraft:iron_sword";
            s.name = "&6Меч героя";
            s.lore.add("&7Лёгкий и быстрый");
            s.damage = 9;
            s.attackSpeed = 2.0;
            s.unbreakable = true;
            list.add(s);
        }
        for (CustomItem c : list) {
            if (c != null && c.id != null && !c.id.isEmpty()) {
                c.normalize();
                ALL.put(c.id, c);
            }
        }
        save();
    }

    public static void save() {
        JsonUtil.write(DataStore.file("custom_items.json"), new ArrayList<CustomItem>(ALL.values()));
    }

    public static List<CustomItem> all() {
        return new ArrayList<CustomItem>(ALL.values());
    }

    @Nullable
    public static CustomItem get(String id) {
        return id == null ? null : ALL.get(id.toLowerCase(Locale.ROOT));
    }

    public static CustomItem create(String id) {
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new UiError("id: латиница, цифры, _ и - (до 32).");
        }
        if (ALL.containsKey(id)) {
            throw new UiError("Предмет «" + id + "» уже есть.");
        }
        CustomItem c = new CustomItem();
        c.id = id;
        c.normalize();
        ALL.put(id, c);
        save();
        return c;
    }

    public static void delete(CustomItem c) {
        ALL.remove(c.id);
        save();
    }

    private static NBTTagCompound attr(String name, double amount, int op, String slot) {
        NBTTagCompound a = new NBTTagCompound();
        a.setString("AttributeName", name);
        a.setString("Name", "ecoserv");
        a.setDouble("Amount", amount);
        a.setInteger("Operation", op);
        UUID u = UUID.nameUUIDFromBytes((name + slot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        a.setLong("UUIDMost", u.getMostSignificantBits());
        a.setLong("UUIDLeast", u.getLeastSignificantBits());
        a.setString("Slot", slot);
        return a;
    }

    /** Собрать предмет. */
    public static ItemStack stack(CustomItem c, int count) {
        ItemStack st = UiUtil.icon(c.base).copy();
        if (st.isEmpty()) {
            return st;
        }
        st.setCount(Math.max(1, Math.min(st.getMaxStackSize(), count)));
        NBTTagCompound t = st.hasTagCompound() ? st.getTagCompound() : new NBTTagCompound();
        t.setString(TAG, c.id);
        NBTTagCompound disp = new NBTTagCompound();
        disp.setString("Name", ChatUtil.color("&r" + c.name));
        if (!c.lore.isEmpty()) {
            NBTTagList lore = new NBTTagList();
            for (String l : c.lore) {
                lore.appendTag(new NBTTagString(ChatUtil.color("&r" + l)));
            }
            disp.setTag("Lore", lore);
        }
        t.setTag("display", disp);
        if (c.unbreakable) {
            t.setBoolean("Unbreakable", true);
        }
        int hide = 0;
        if (c.glow) {
            NBTTagList ench = new NBTTagList();
            NBTTagCompound e = new NBTTagCompound();
            e.setShort("id", (short) 34);
            e.setShort("lvl", (short) 1);
            ench.appendTag(e);
            t.setTag("ench", ench);
            hide |= 1;
        }
        NBTTagList attrs = new NBTTagList();
        if (c.damage > 0) {
            attrs.appendTag(attr("generic.attackDamage", c.damage - 1.0D, 0, "mainhand"));
        }
        if (c.attackSpeed > 0) {
            attrs.appendTag(attr("generic.attackSpeed", c.attackSpeed - 4.0D, 0, "mainhand"));
        }
        if (c.moveSpeed != 0) {
            attrs.appendTag(attr("generic.movementSpeed", c.moveSpeed / 100.0D, 1, "mainhand"));
        }
        if (c.health != 0) {
            attrs.appendTag(attr("generic.maxHealth", c.health, 0, "mainhand"));
        }
        if (attrs.tagCount() > 0) {
            t.setTag("AttributeModifiers", attrs);
        }
        if (hide != 0) {
            t.setInteger("HideFlags", hide);
        }
        st.setTagCompound(t);
        return st;
    }

    @Nullable
    public static CustomItem of(ItemStack st) {
        if (st.isEmpty() || !st.hasTagCompound() || !st.getTagCompound().hasKey(TAG)) {
            return null;
        }
        return get(st.getTagCompound().getString(TAG));
    }

    public static class Events {
        @SubscribeEvent
        public void onUse(PlayerInteractEvent.RightClickItem event) {
            CustomItem c = of(event.getItemStack());
            if (c == null || c.steps.isEmpty()) {
                return;
            }
            event.setCanceled(true);
            if (event.getWorld().isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) {
                return;
            }
            EntityPlayerMP p = (EntityPlayerMP) event.getEntityPlayer();
            ItemStack st = event.getItemStack();
            if (p.getCooldownTracker().hasCooldown(st.getItem())) {
                return;
            }
            if (!c.condition.trim().isEmpty() && !ScriptSteps.condition(p, c.condition, c.id)) {
                ChatUtil.actionBar(p, "&cСейчас нельзя.");
                return;
            }
            ScriptRunner.startSteps(p, "item:" + c.id, c.steps, c.id);
            if (c.cooldown > 0) {
                p.getCooldownTracker().setCooldown(st.getItem(), c.cooldown * 20);
            }
            if (c.consume && !p.isCreative()) {
                st.shrink(1);
            }
        }
    }
}
