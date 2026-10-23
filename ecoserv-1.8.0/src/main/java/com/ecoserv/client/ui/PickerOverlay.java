package com.ecoserv.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Выбор значения из списка для тех, кто не помнит id: все звуки игры и модов (с прослушиванием),
 * все предметы, игроки в сети, цвета. Список собирается на клиенте — там уже есть все реестры.
 */
@SideOnly(Side.CLIENT)
final class PickerOverlay extends Overlay {

    private static final int ROW = 14;
    private static final int CELL = 20;

    private static final class Entry {
        final String value;
        final String label;
        final String sub;
        final ItemStack icon;
        final SoundEvent sound;
        final int color;
        final String search;

        Entry(String value, String label, String sub, ItemStack icon, SoundEvent sound, int color) {
            this.value = value;
            this.label = label;
            this.sub = sub;
            this.icon = icon;
            this.sound = sound;
            this.color = color;
            this.search = GuiWindow.W.plain(label + " " + sub + " " + value);
        }
    }

    GuiWindow.WField field;
    private final String kind;
    private final List<Entry> all = new ArrayList<Entry>();
    private final List<Entry> shown = new ArrayList<Entry>();
    private final GuiTextField search;
    private int scroll;
    private int x;
    private int y;
    private int w;
    private int h;
    private int listY;
    private int listH;

    PickerOverlay(GuiWindow g, GuiWindow.WField field, String kind) {
        this.field = field;
        this.kind = kind;
        search = new GuiTextField(9200, UiTheme.font(), 0, 0, 160, 10);
        search.setEnableBackgroundDrawing(false);
        search.setMaxStringLength(64);
        search.setFocused(true);
        load();
        filter();
    }

    private boolean grid() {
        return "item".equals(kind) || "color".equals(kind);
    }

    private static String modName(String domain) {
        if ("minecraft".equals(domain)) {
            return "Minecraft";
        }
        Map<String, ModContainer> mods = Loader.instance().getIndexedModList();
        ModContainer mod = mods.get(domain);
        return mod == null ? domain : mod.getName();
    }

    private void load() {
        switch (kind) {
            case "sound": {
                List<ResourceLocation> keys = new ArrayList<ResourceLocation>(ForgeRegistries.SOUND_EVENTS.getKeys());
                Collections.sort(keys, (a, b) -> a.toString().compareTo(b.toString()));
                for (ResourceLocation rl : keys) {
                    all.add(new Entry(rl.toString(), rl.getPath(), modName(rl.getNamespace()), ItemStack.EMPTY,
                            ForgeRegistries.SOUND_EVENTS.getValue(rl), 0));
                }
                break;
            }
            case "item": {
                for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
                    NonNullList<ItemStack> list = NonNullList.create();
                    try {
                        item.getSubItems(CreativeTabs.SEARCH, list);
                    } catch (Throwable t) {
                        list.clear();
                    }
                    if (list.isEmpty()) {
                        list.add(new ItemStack(item));
                    }
                    for (ItemStack s : list) {
                        if (s.isEmpty() || s.getItem() == Items.AIR) {
                            continue;
                        }
                        ResourceLocation rl = s.getItem().getRegistryName();
                        String id = (rl == null ? "minecraft:air" : rl.toString()) + "@" + s.getMetadata();
                        String name;
                        try {
                            name = s.getDisplayName();
                        } catch (Throwable t) {
                            name = id;
                        }
                        all.add(new Entry(id, name, rl == null ? "" : modName(rl.getNamespace()), s, null, 0));
                    }
                }
                break;
            }
            case "player": {
                NetHandlerPlayClient net = Minecraft.getMinecraft().getConnection();
                List<String> names = new ArrayList<String>();
                if (net != null) {
                    for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
                        if (info != null && info.getGameProfile() != null && info.getGameProfile().getName() != null) {
                            names.add(info.getGameProfile().getName());
                        }
                    }
                }
                Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
                for (String n : names) {
                    all.add(new Entry(n, n, "в сети", ItemStack.EMPTY, null, 0));
                }
                break;
            }
            case "color": {
                int[] colors = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
                        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
                        0x87CEEB, 0x78A7FF, 0x1A1A2E, 0x2B0F3A, 0x4B0082, 0x8A2BE2, 0xFF1493, 0x00FFFF,
                        0xFF6347, 0xFFD700, 0x2E8B57, 0x3A5FCD, 0x101014, 0x6B4226, 0xC0C0C0, 0xF5F5DC};
                for (int c : colors) {
                    String hex = String.format("#%06X", c);
                    all.add(new Entry(hex, hex, "", ItemStack.EMPTY, null, c));
                }
                break;
            }
            default:
        }
    }

    private void filter() {
        shown.clear();
        String f = search.getText().toLowerCase(Locale.ROOT).trim();
        for (Entry e : all) {
            if (f.isEmpty() || e.search.contains(f)) {
                shown.add(e);
            }
        }
        scroll = 0;
    }

    private void layout(GuiWindow g) {
        w = Math.min(g.winW - 30, 360);
        h = Math.min(g.winH - 30, 240);
        x = g.left + (g.winW - w) / 2;
        y = g.top + (g.winH - h) / 2;
        search.x = x + 18;
        search.y = y + 26;
        listY = y + 42;
        listH = h - 42 - 22;
    }

    private int cols() {
        return Math.max(1, (w - 12) / CELL);
    }

    private int visibleRows() {
        return grid() ? Math.max(1, listH / CELL) : Math.max(1, listH / ROW);
    }

    private int totalRows() {
        return grid() ? (shown.size() + cols() - 1) / cols() : shown.size();
    }

    private String title() {
        switch (kind) {
            case "sound":
                return "Выбор звука (" + all.size() + ")";
            case "item":
                return "Выбор предмета (" + all.size() + ")";
            case "player":
                return "Игроки в сети";
            case "color":
                return "Выбор цвета";
            default:
                return "Выбор";
        }
    }

    @Override
    void draw(GuiWindow g, int mx, int my, float partialTicks) {
        layout(g);
        UiTheme.rect(x - 1, y - 1, w + 2, h + 2, 0xFF07080A);
        UiTheme.panel(x, y, w, h, 0xFF16181F, UiTheme.ACCENT);
        UiTheme.rect(x + 1, y + 1, w - 2, 18, UiTheme.TITLE);
        UiTheme.shadow(title(), x + 8, y + 6, UiTheme.TEXT);
        boolean closeHover = GuiWindow.inside(mx, my, x + w - 18, y + 2, 16, 16);
        UiTheme.center("✕", x + w - 10, y + 6, closeHover ? 0xFFFFFFFF : UiTheme.DIM);
        UiTheme.panel(x + 6, y + 22, w - 12, 15, UiTheme.INPUT, UiTheme.ACCENT);
        UiTheme.text("⌕", x + 9, y + 26, UiTheme.MUTED);
        if (search.getText().isEmpty()) {
            UiTheme.text("поиск по названию, моду или id…", x + 18, y + 26, UiTheme.MUTED);
        }
        search.drawTextBox();

        UiTheme.scissor(x + 2, listY, w - 4, listH);
        Entry hovered = null;
        if (grid()) {
            int cols = cols();
            for (int i = scroll * cols; i < shown.size() && i < (scroll + visibleRows()) * cols; i++) {
                Entry e = shown.get(i);
                int cx = x + 6 + (i % cols) * CELL;
                int cy = listY + (i / cols - scroll) * CELL;
                boolean hover = GuiWindow.inside(mx, my, cx, cy, CELL - 1, CELL - 1);
                boolean cur = e.value.equalsIgnoreCase(field.tf == null ? field.val : field.tf.getText());
                UiTheme.panel(cx, cy, CELL - 1, CELL - 1, hover ? 0xFF262A35 : 0xFF111318,
                        cur ? UiTheme.ACCENT : UiTheme.BORDER);
                if ("color".equals(kind)) {
                    UiTheme.rect(cx + 3, cy + 3, CELL - 7, CELL - 7, 0xFF000000 | e.color);
                } else {
                    UiTheme.item(e.icon, cx + 1, cy + 1);
                }
                if (hover) {
                    hovered = e;
                }
            }
        } else {
            for (int i = 0; i < visibleRows() && scroll + i < shown.size(); i++) {
                Entry e = shown.get(scroll + i);
                int ry = listY + i * ROW;
                boolean hover = GuiWindow.inside(mx, my, x + 4, ry, w - 8, ROW);
                if (hover) {
                    UiTheme.rect(x + 4, ry, w - 8, ROW, 0xFF262D3D);
                    hovered = e;
                }
                int tx = x + 8;
                if (e.sound != null) {
                    boolean playHover = GuiWindow.inside(mx, my, x + 6, ry + 1, 12, 12);
                    UiTheme.text("▶", x + 8, ry + 3, playHover ? 0xFF55D17C : UiTheme.DIM);
                    tx += 12;
                }
                String sub = e.sub;
                int subW = sub.isEmpty() ? 0 : UiTheme.width(sub) + 6;
                UiTheme.text(UiTheme.trim(e.label, w - (tx - x) - subW - 12), tx, ry + 3, UiTheme.TEXT);
                if (!sub.isEmpty()) {
                    UiTheme.text(sub, x + w - 10 - UiTheme.width(sub), ry + 3, UiTheme.MUTED);
                }
            }
        }
        UiTheme.endScissor();
        if (shown.isEmpty()) {
            UiTheme.center("ничего не найдено", x + w / 2, listY + 10, UiTheme.MUTED);
        }
        int total = totalRows();
        if (total > visibleRows()) {
            int thumbH = Math.max(10, listH * visibleRows() / total);
            int thumbY = listY + (listH - thumbH) * scroll / Math.max(1, total - visibleRows());
            UiTheme.rect(x + w - 5, thumbY, 3, thumbH, 0xFF6A7183);
        }
        String hint = "sound".equals(kind) ? "▶ — послушать, клик по названию — выбрать" : "клик — выбрать, колёсико — листать";
        if ("item".equals(kind)) {
            boolean handHover = GuiWindow.inside(mx, my, x + w - 86, y + h - 19, 80, 16);
            UiTheme.button(x + w - 86, y + h - 19, 80, 16, "Из руки", 1, handHover, false);
        }
        UiTheme.text(UiTheme.trim(hint, w - 100), x + 8, y + h - 14, UiTheme.MUTED);
        if (hovered != null) {
            List<String> tip = new ArrayList<String>();
            tip.add(hovered.label);
            if (!hovered.sub.isEmpty()) {
                tip.add(hovered.sub);
            }
            tip.add("§8" + hovered.value);
            g.setTooltip(tip);
        }
    }

    @Override
    boolean click(GuiWindow g, int mx, int my, int button) {
        layout(g);
        if (!GuiWindow.inside(mx, my, x, y, w, h) || GuiWindow.inside(mx, my, x + w - 18, y + 2, 16, 16)) {
            return false;
        }
        if (my >= y + 22 && my < y + 37) {
            search.setFocused(true);
            return true;
        }
        if ("item".equals(kind) && GuiWindow.inside(mx, my, x + w - 86, y + h - 19, 80, 16)) {
            ItemStack held = Minecraft.getMinecraft().player == null ? ItemStack.EMPTY
                    : Minecraft.getMinecraft().player.getHeldItemMainhand();
            if (!held.isEmpty() && held.getItem().getRegistryName() != null) {
                choose(g, held.getItem().getRegistryName() + "@" + held.getMetadata());
            }
            return true;
        }
        if (my < listY || my >= listY + listH) {
            return true;
        }
        if (grid()) {
            int cols = cols();
            int col = (mx - x - 6) / CELL;
            int row = (my - listY) / CELL + scroll;
            int i = row * cols + col;
            if (col >= 0 && col < cols && i >= 0 && i < shown.size()) {
                choose(g, shown.get(i).value);
            }
            return true;
        }
        int i = (my - listY) / ROW + scroll;
        if (i >= 0 && i < shown.size()) {
            Entry e = shown.get(i);
            if (e.sound != null && mx < x + 20) {
                Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(e.sound, 1.0F));
                return true;
            }
            choose(g, e.value);
        }
        return true;
    }

    private void choose(GuiWindow g, String value) {
        g.click();
        g.overlay = null;
        field.setAndApply(g, value);
    }

    @Override
    void key(GuiWindow g, char c, int code) {
        String before = search.getText();
        search.textboxKeyTyped(c, code);
        if (!before.equals(search.getText())) {
            filter();
        }
    }

    @Override
    void wheel(int dir) {
        scroll = Math.max(0, Math.min(Math.max(0, totalRows() - visibleRows()), scroll + dir * 2));
    }

    @Override
    void tick() {
        search.updateCursorCounter();
    }
}
