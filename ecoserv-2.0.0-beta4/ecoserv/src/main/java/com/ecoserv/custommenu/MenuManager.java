package com.ecoserv.custommenu;

import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.UiError;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.v2.V2Actions;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Свои меню: world/ecoserv/menus.json; кнопки в меню паузы; предметы-ключи. */
public final class MenuManager {

    public static final Pattern ID = Pattern.compile("^[a-z0-9_\\-]{1,32}$");
    public static final String ITEM_TAG = "ecoserv_menu";
    private static final Type MENUS = new TypeToken<ArrayList<CustomMenu>>() {
    }.getType();
    private static final Map<String, CustomMenu> MENU = new LinkedHashMap<String, CustomMenu>();

    private MenuManager() {
    }

    public static void load() {
        MENU.clear();
        List<CustomMenu> list = JsonUtil.readType(DataStore.file("menus.json"), MENUS, null);
        if (list == null) {
            list = new ArrayList<CustomMenu>();
            list.add(example());
        }
        for (CustomMenu m : list) {
            if (m != null && m.id != null && !m.id.isEmpty()) {
                m.normalize();
                MENU.put(m.id, m);
            }
        }
        save();
    }

    private static CustomMenu example() {
        CustomMenu m = new CustomMenu();
        m.id = "info";
        m.title = "&6Информация";
        m.subtitle = "&7Привет, %player%! Баланс: %money%";
        m.icon = "minecraft:book";
        m.escButton = true;
        m.escLabel = "Информация сервера";
        CustomMenu.Element h = new CustomMenu.Element();
        h.kind = "header";
        h.text = "Правила";
        m.elements.add(h);
        CustomMenu.Element t = new CustomMenu.Element();
        t.kind = "text";
        t.text = "&71. Не гриферить.\\n&72. Уважать других.\\n&73. Весело играть!";
        m.elements.add(t);
        CustomMenu.Element r = new CustomMenu.Element();
        r.kind = "row";
        r.text = "&fНа спавн";
        r.desc = "&7Телепорт на точку появления";
        r.icon = "minecraft:ender_pearl";
        r.steps.add("pcmd spawn");
        r.then = "close";
        m.elements.add(r);
        CustomMenu.Element b = new CustomMenu.Element();
        b.kind = "button";
        b.text = "Ежедневный бонус";
        b.color = "green";
        b.condition = "var bonus_day != %day%";
        b.steps.add("set bonus_day %day%");
        b.steps.add("money 50");
        b.steps.add("msg &aПолучено 50 монет!");
        m.elements.add(b);
        m.normalize();
        return m;
    }

    public static void save() {
        JsonUtil.write(DataStore.file("menus.json"), new ArrayList<CustomMenu>(MENU.values()));
    }

    public static List<CustomMenu> all() {
        return new ArrayList<CustomMenu>(MENU.values());
    }

    @Nullable
    public static CustomMenu get(String id) {
        return id == null ? null : MENU.get(id.toLowerCase(Locale.ROOT));
    }

    public static CustomMenu create(String id) {
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new UiError("id: латиница, цифры, _ и - (до 32).");
        }
        if (MENU.containsKey(id)) {
            throw new UiError("Меню «" + id + "» уже есть.");
        }
        CustomMenu m = new CustomMenu();
        m.id = id;
        m.title = id;
        m.normalize();
        MENU.put(id, m);
        save();
        return m;
    }

    public static void delete(CustomMenu m) {
        MENU.remove(m.id);
        save();
        syncEsc();
    }

    /** Может ли игрок открыть меню. */
    public static boolean allowed(EntityPlayerMP p, CustomMenu m) {
        return m.condition.trim().isEmpty() || ScriptSteps.condition(p, m.condition, m.id);
    }

    public static void open(EntityPlayerMP p, String id) {
        CustomMenu m = get(id);
        if (m == null) {
            ChatUtil.send(p, "&cМеню «" + id + "» не найдено.");
            return;
        }
        if (!allowed(p, m)) {
            ChatUtil.send(p, "&cЭто меню тебе недоступно.");
            return;
        }
        new MenuScreen(p, m.id).open();
    }

    /** Предмет-ключ: ПКМ открывает меню. */
    public static ItemStack keyItem(CustomMenu m) {
        ItemStack st = com.ecoserv.ui.UiUtil.icon(m.icon).copy();
        st.setCount(1);
        NBTTagCompound t = st.hasTagCompound() ? st.getTagCompound() : new NBTTagCompound();
        t.setString(ITEM_TAG, m.id);
        st.setTagCompound(t);
        st.setStackDisplayName(ChatUtil.color("&r" + m.title));
        NBTTagCompound disp = t.getCompoundTag("display");
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString(ChatUtil.color("&7ПКМ — открыть меню")));
        disp.setTag("Lore", lore);
        t.setTag("display", disp);
        return st;
    }

    // --- меню паузы ---------------------------------------------------------------------------------

    public static List<CustomMenu> escMenus() {
        List<CustomMenu> out = new ArrayList<CustomMenu>();
        for (CustomMenu m : MENU.values()) {
            if (m.escButton) {
                out.add(m);
            }
        }
        return out;
    }

    public static void sendEsc(EntityPlayerMP p) {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (CustomMenu m : escMenus()) {
            if (!allowed(p, m)) {
                continue;
            }
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", m.id);
            t.setString("label", ChatUtil.color(m.escLabel.isEmpty() ? m.title : m.escLabel));
            list.appendTag(t);
        }
        data.setTag("b", list);
        V2Actions.send(p, "esc_buttons", data);
    }

    public static void syncEsc() {
        for (EntityPlayerMP p : com.ecoserv.util.PlayerUtil.allOnline()) {
            sendEsc(p);
        }
    }
}
