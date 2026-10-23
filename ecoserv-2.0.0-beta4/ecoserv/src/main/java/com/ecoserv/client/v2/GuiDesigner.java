package com.ecoserv.client.v2;

import com.ecoserv.client.ui.UiTheme;
import com.ecoserv.content.CustomContent;
import com.ecoserv.gui.CustomGui;
import com.ecoserv.util.JsonUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 2.0 beta4: визуальный дизайнер окон — как редактор интерфейсов. Слева палитра (перетащи на окно) и слои,
 * в центре окно с сеткой и умными направляющими, справа свойства выбранного. Тянешь — двигаешь, за уголки —
 * меняешь размер, двойной щелчок — правка текста, цвета — палитрой.
 */
@SideOnly(Side.CLIENT)
public class GuiDesigner extends GuiScreen {

    /** Палитра: вид, название, описание, ширина, высота. */
    private static final Object[][] PALETTE = {
            {"text", "Т  Текст", "Надпись; & — цвета, %player%, %money%", 90, 12},
            {"button", "▭  Кнопка", "Нажимается — запускает действия", 70, 18},
            {"rect", "■  Плашка", "Цветной прямоугольник-фон", 80, 40},
            {"item", "◆  Предмет", "Иконка предмета, можно нажимать", 18, 18},
            {"image", "🖼  Картинка", "Своя текстура из «Контента»", 32, 32},
            {"bar", "▬  Полоса", "Заполняется по переменной игрока", 100, 10},
            {"input", "⌨  Поле ввода", "Игрок пишет текст → переменная", 100, 16},
            {"line", "—  Линия", "Разделитель", 100, 1},
    };
    private static final int[] COLORS = {0xFFFFFF, 0xC0C0C0, 0x808080, 0x404040, 0x000000, 0x171920, 0xFF5555, 0xFFAA00,
            0xFFFF55, 0x55FF55, 0x2E8B57, 0x55FFFF, 0x2F5BB0, 0x5555FF, 0xAA00AA, 0xFF55FF, 0x8B2E2E, 0x8B5A2B, 0x1F232C,
            0x3A3F4C, 0x4E8CFF, 0xFFD9A0, 0xB4D2FF, 0x0C0E12};

    private static final int TOP = 22;
    private static final int LEFT_W = 124;
    private static final int RIGHT_W = 176;
    private static final int BOTTOM = 14;

    private CustomGui g;
    private String origId;
    private String[] guiIds = new String[0];
    private final Deque<String> undo = new ArrayDeque<String>();
    private final Deque<String> redo = new ArrayDeque<String>();
    private boolean dirty;
    private boolean confirmExit;
    /** -1 — ничего, -2 — само окно, иначе индекс элемента. */
    private int sel = -1;
    private int grid = 4;
    private boolean preview;
    private float zoom = 1.0F;
    private int canvasX;
    private int canvasY;
    // перетаскивание
    private int dragMode = -1; // 0 — двигать, 1..8 — уголки/стороны, 9 — размер окна, 10 — новый из палитры
    private String dragKind;
    private int dragMx;
    private int dragMy;
    private int[] dragRect;
    private boolean dragMoved;
    private final List<int[]> guides = new ArrayList<int[]>();
    private long lastClick;
    private int lastClickIdx = -9;
    private CustomGui.El clipboard;
    /** После двойного щелчка — поставить курсор в поле текста. */
    private boolean focusText;
    // свойства
    private final Map<String, GuiTextField> fields = new LinkedHashMap<String, GuiTextField>();
    private int fieldsFor = -9;
    private final List<Object[]> hits = new ArrayList<Object[]>();
    // выбор цвета
    private String colorProp;
    private int colorX;
    private int colorY;
    private GuiTextField hex;
    private int layerScroll;
    private String status = "";
    private long statusUntil;

    public static void register() {
        ClientV2.on("cgui_design", d -> {
            CustomGui g = GuiCustom.read(d);
            if (g != null) {
                GuiDesigner x = new GuiDesigner(g);
                x.guiIds = d.getString("ids").split(",");
                Minecraft.getMinecraft().displayGuiScreen(x);
            }
        });
    }

    public GuiDesigner(CustomGui g) {
        this.g = g;
        this.origId = g.id;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        fieldsFor = -9;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void say(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    // --- история -------------------------------------------------------------------------------------

    private String snapshot() {
        return JsonUtil.GSON.toJson(g);
    }

    /** Запомнить состояние перед изменением. */
    private void remember() {
        undo.push(snapshot());
        while (undo.size() > 80) {
            undo.removeLast();
        }
        redo.clear();
        dirty = true;
        confirmExit = false;
    }

    private void restore(String json) {
        g = JsonUtil.GSON.fromJson(json, CustomGui.class);
        g.normalize();
        if (sel >= g.elements.size()) {
            sel = -1;
        }
        fieldsFor = -9;
        dirty = true;
    }

    private void doUndo() {
        if (!undo.isEmpty()) {
            redo.push(snapshot());
            restore(undo.pop());
            say("Отменено");
        }
    }

    private void doRedo() {
        if (!redo.isEmpty()) {
            undo.push(snapshot());
            restore(redo.pop());
            say("Повторено");
        }
    }

    private void save() {
        applyFields();
        NBTTagCompound d = new NBTTagCompound();
        d.setString("json", snapshot());
        d.setString("old", origId);
        ClientV2.send("cgui_save", d);
        origId = g.id;
        dirty = false;
        say("Сохранено");
    }

    // --- раскладка -----------------------------------------------------------------------------------

    private void layout() {
        int areaW = width - LEFT_W - RIGHT_W;
        int areaH = height - TOP - BOTTOM;
        float fit = Math.min((areaW - 20) / (float) g.width, (areaH - 20) / (float) g.height);
        if (zoom > fit && fit < 1) {
            zoom = Math.max(0.25F, fit);
        }
        canvasX = (int) ((LEFT_W + (areaW - g.width * zoom) / 2) / zoom);
        canvasY = (int) ((TOP + (areaH - g.height * zoom) / 2) / zoom);
    }

    /** Мышь → координаты окна. */
    private int cx(int mx) {
        return (int) Math.floor(mx / zoom) - canvasX;
    }

    private int cy(int my) {
        return (int) Math.floor(my / zoom) - canvasY;
    }

    private boolean inCanvasArea(int mx, int my) {
        return mx >= LEFT_W && mx < width - RIGHT_W && my >= TOP && my < height - BOTTOM && colorProp == null;
    }

    private CustomGui.El selected() {
        return sel >= 0 && sel < g.elements.size() ? g.elements.get(sel) : null;
    }

    // --- рисование -----------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        layout();
        hits.clear();
        drawRect(0, 0, width, height, 0xFF0B0D11);
        // холст
        GlStateManager.pushMatrix();
        GlStateManager.scale(zoom, zoom, 1);
        int wx = canvasX;
        int wy = canvasY;
        // тень окна
        UiTheme.rect(wx + 3, wy + 3, g.width, g.height, 0x60000000);
        GuiRender.window(g, wx, wy, true);
        if (grid > 0 && !preview) {
            for (int gx = grid; gx < g.width; gx += grid * 2) {
                for (int gy = grid; gy < g.height; gy += grid * 2) {
                    UiTheme.rect(wx + gx, wy + gy, 1, 1, 0x30FFFFFF);
                }
            }
        }
        int cmx = cx(mx);
        int cmy = cy(my);
        for (int i = 0; i < g.elements.size(); i++) {
            CustomGui.El e = g.elements.get(i);
            boolean hov = inCanvasArea(mx, my) && cmx >= e.x && cmx < e.x + e.w && cmy >= e.y && cmy < e.y + e.h;
            GuiRender.element(e, wx, wy, hov && preview, true);
            if (!preview && hov && i != sel && dragMode < 0) {
                UiTheme.border(wx + e.x - 1, wy + e.y - 1, e.w + 2, e.h + 2, 0x804E8CFF);
            }
        }
        if (!preview) {
            CustomGui.El s = selected();
            if (s != null) {
                UiTheme.border(wx + s.x - 1, wy + s.y - 1, s.w + 2, s.h + 2, 0xFF4E8CFF);
                for (int h = 1; h <= 8; h++) {
                    int[] p = handlePos(s, h);
                    UiTheme.rect(wx + p[0] - 2, wy + p[1] - 2, 5, 5, 0xFF4E8CFF);
                    UiTheme.rect(wx + p[0] - 1, wy + p[1] - 1, 3, 3, 0xFFFFFFFF);
                }
                // размер у курсора при изменении
                if (dragMode >= 0 && dragMode <= 8) {
                    String t = s.w + "×" + s.h + "  (" + s.x + ", " + s.y + ")";
                    UiTheme.round(wx + s.x, wy + s.y + s.h + 3, fontRenderer.getStringWidth(t) + 6, 11, 0xE02F5BB0);
                    UiTheme.text(t, wx + s.x + 3, wy + s.y + s.h + 5, 0xFFFFFFFF);
                }
            }
            if (sel == -2) {
                UiTheme.border(wx - 2, wy - 2, g.width + 4, g.height + 4, 0xFFFFD040);
            }
            // уголок размера окна
            UiTheme.rect(wx + g.width - 5, wy + g.height - 5, 7, 7, sel == -2 ? 0xFFFFD040 : 0xFF606878);
            for (int[] gl : guides) {
                if (gl[0] == 0) {
                    UiTheme.rect(wx + gl[1], wy - 6, 1, g.height + 12, 0xFFFF40C0);
                } else {
                    UiTheme.rect(wx - 6, wy + gl[1], g.width + 12, 1, 0xFFFF40C0);
                }
            }
        }
        // новый элемент из палитры — призрак
        if (dragMode == 10 && dragRect != null) {
            UiTheme.rect(wx + dragRect[0], wy + dragRect[1], dragRect[2], dragRect[3], 0x604E8CFF);
            UiTheme.border(wx + dragRect[0], wy + dragRect[1], dragRect[2], dragRect[3], 0xFF4E8CFF);
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        toolbar(mx, my);
        palette(mx, my);
        properties(mx, my);
        // подсказка
        UiTheme.rect(0, height - BOTTOM, width, BOTTOM, 0xF0101218);
        UiTheme.text(UiTheme.trim(hint(), width - 8), 4, height - BOTTOM + 3, UiTheme.DIM);
        if (colorProp != null) {
            colorPopup(mx, my);
        }
        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            int w = fontRenderer.getStringWidth(status) + 12;
            UiTheme.round((width - w) / 2, TOP + 6, w, 14, 0xE02F5BB0);
            UiTheme.center(status, width / 2, TOP + 9, 0xFFFFFFFF);
        }
        // подсказки палитры
        Object[] pe = paletteAt(mx, my);
        if (pe != null && dragMode < 0) {
            drawHoveringText(Arrays.asList("§f" + pe[1], "§7" + pe[2], "§8Перетащи на окно или щёлкни"), mx, my);
        }
        super.drawScreen(mx, my, partialTicks);
    }

    private String hint() {
        if (preview) {
            return "§fПредпросмотр: §7так окно увидит игрок. Нажми «Предпросмотр» ещё раз, чтобы вернуться к правке.";
        }
        if (dragMode == 10) {
            return "§fОтпусти над окном — элемент появится там.";
        }
        if (sel >= 0) {
            return "§7Тяни — двигать, уголки — размер, двойной щелчок — текст · стрелки — на 1 (Shift — на 8) · Del · Ctrl+D — копия ·"
                    + " Ctrl+C/V · Ctrl+Z/Y · Ctrl+S — сохранить";
        }
        if (sel == -2) {
            return "§7Это само окно: справа — заголовок, размер, цвета. Тяни жёлтый уголок, чтобы изменить размер.";
        }
        return "§7Перетащи элемент из палитры слева на окно · щёлкни по элементу — выбрать · по пустому месту окна — свойства окна ·"
                + " Ctrl+колесо — масштаб";
    }

    private int btn(int x, int y, int w, int h, String label, String id, boolean on, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h && colorProp == null;
        UiTheme.round(x, y, w, h, on ? 0xFF2F5BB0 : hov ? 0xFF2A2F3A : 0xFF1C2028);
        UiTheme.center(UiTheme.trim(label, w - 4), x + w / 2, y + (h - 8) / 2, on ? 0xFFFFFFFF : UiTheme.TEXT);
        hits.add(new Object[]{x, y, w, h, id});
        return x + w + 2;
    }

    private void toolbar(int mx, int my) {
        UiTheme.rect(0, 0, width, TOP, 0xF0101218);
        int x = 4;
        UiTheme.text("§f▤ " + UiTheme.trim(g.id, 80) + (dirty ? " §e●" : ""), x, 7, 0xFFFFFFFF);
        x += Math.min(100, fontRenderer.getStringWidth("▤ " + g.id) + 16);
        x = btn(x, 3, fontRenderer.getStringWidth("💾 Сохранить") + 10, 15, "💾 Сохранить", "save", dirty, mx, my);
        x = btn(x, 3, fontRenderer.getStringWidth("▶ Проверить") + 10, 15, "▶ Проверить", "test", false, mx, my);
        x = btn(x, 3, fontRenderer.getStringWidth("👁 Предпросмотр") + 10, 15, "👁 Предпросмотр", "preview", preview, mx, my);
        x += 4;
        x = btn(x, 3, 22, 15, "↶", "undo", false, mx, my);
        x = btn(x, 3, 22, 15, "↷", "redo", false, mx, my);
        x += 4;
        String gl = grid == 0 ? "Сетка: нет" : "Сетка: " + grid;
        x = btn(x, 3, fontRenderer.getStringWidth(gl) + 10, 15, gl, "grid", grid > 0, mx, my);
        String zl = Math.round(zoom * 100) + "%";
        x = btn(x, 3, fontRenderer.getStringWidth(zl) + 10, 15, zl, "zoom", false, mx, my);
        btn(width - 132, 3, 64, 15, "Все окна…", "list", false, mx, my);
        btn(width - 66, 3, 62, 15, confirmExit ? "§eТочно?" : "✕ Закрыть", "close", false, mx, my);
    }

    private Object[] paletteAt(int mx, int my) {
        if (mx >= LEFT_W || my < TOP + 16) {
            return null;
        }
        int i = (my - TOP - 16) / 16;
        return i >= 0 && i < PALETTE.length ? PALETTE[i] : null;
    }

    private void palette(int mx, int my) {
        UiTheme.rect(0, TOP, LEFT_W, height - TOP - BOTTOM, 0xF0121419);
        UiTheme.text("§7Палитра", 6, TOP + 4, UiTheme.DIM);
        for (int i = 0; i < PALETTE.length; i++) {
            int y = TOP + 16 + i * 16;
            boolean hov = mx < LEFT_W && my >= y && my < y + 16 && colorProp == null;
            UiTheme.round(4, y + 1, LEFT_W - 8, 14, hov ? 0xFF2A3550 : 0xFF1B1E26);
            UiTheme.text((String) PALETTE[i][1], 9, y + 4, UiTheme.TEXT);
        }
        int ly = TOP + 20 + PALETTE.length * 16;
        UiTheme.text("§7Слои §8(сверху — ближе)", 6, ly, UiTheme.DIM);
        ly += 12;
        UiTheme.round(4, ly, LEFT_W - 8, 12, sel == -2 ? 0xFF2A4170 : 0xFF1B1E26);
        UiTheme.text("§e▣ Окно", 9, ly + 2, 0xFFFFE080);
        hits.add(new Object[]{4, ly, LEFT_W - 8, 12, "layer:-2"});
        ly += 13;
        int rows = Math.max(1, (height - BOTTOM - ly) / 12);
        layerScroll = Math.max(0, Math.min(Math.max(0, g.elements.size() - rows), layerScroll));
        for (int r = 0; r < rows && r + layerScroll < g.elements.size(); r++) {
            int i = g.elements.size() - 1 - (r + layerScroll);
            CustomGui.El e = g.elements.get(i);
            int y = ly + r * 12;
            boolean s = i == sel;
            boolean hov = mx < LEFT_W && my >= y && my < y + 12;
            UiTheme.round(4, y, LEFT_W - 8, 11, s ? 0xFF2A4170 : hov ? 0xFF22262F : 0xFF15171D);
            String name = kindIcon(e.kind) + " " + (e.text.isEmpty() ? kindTitle(e.kind) : com.ecoserv.util.ChatUtil.strip(e.text));
            UiTheme.text(UiTheme.trim(name, LEFT_W - 16), 8, y + 2, s ? 0xFFFFE080 : UiTheme.TEXT);
            hits.add(new Object[]{4, y, LEFT_W - 8, 11, "layer:" + i});
        }
    }

    private static String kindIcon(String k) {
        for (Object[] p : PALETTE) {
            if (p[0].equals(k)) {
                return ((String) p[1]).split(" ")[0];
            }
        }
        return "?";
    }

    private static String kindTitle(String k) {
        for (Object[] p : PALETTE) {
            if (p[0].equals(k)) {
                return ((String) p[1]).substring(((String) p[1]).indexOf(' ')).trim();
            }
        }
        return k;
    }

    // --- свойства -------------------------------------------------------------------------------------

    private GuiTextField field(String key, String value, int maxLen) {
        GuiTextField f = fields.get(key);
        if (f == null) {
            f = new GuiTextField(700 + fields.size(), fontRenderer, 0, 0, 50, 12);
            f.setMaxStringLength(maxLen);
            f.setEnableBackgroundDrawing(false);
            f.setText(value);
            fields.put(key, f);
        } else if (!f.isFocused()) {
            f.setText(value);
        }
        return f;
    }

    private void drawField(String key, String label, String value, int x, int y, int w, int maxLen) {
        GuiTextField f = field(key, value, maxLen);
        if (!label.isEmpty()) {
            UiTheme.text(label, x, y + 2, UiTheme.DIM);
            int lw = fontRenderer.getStringWidth(label) + 4;
            x += lw;
            w -= lw;
        }
        UiTheme.rect(x, y - 1, w, 12, f.isFocused() ? 0xFF0F1830 : UiTheme.INPUT);
        UiTheme.border(x, y - 1, w, 12, f.isFocused() ? 0xFF4E8CFF : 0xFF2C303B);
        f.x = x + 3;
        f.y = y + 1;
        f.width = w - 6;
        f.drawTextBox();
    }

    private void swatch(int x, int y, String label, int rgb, boolean none, String id, int mx, int my) {
        UiTheme.text(label, x, y + 2, UiTheme.DIM);
        int sx = x + fontRenderer.getStringWidth(label) + 4;
        UiTheme.rect(sx, y, 22, 11, 0xFF000000);
        if (none) {
            UiTheme.rect(sx + 1, y + 1, 20, 9, 0xFF2A2D35);
            UiTheme.text("§8нет", sx + 3, y + 2, 0xFFFFFFFF);
        } else {
            UiTheme.rect(sx + 1, y + 1, 20, 9, 0xFF000000 | rgb);
        }
        hits.add(new Object[]{sx, y, 22, 11, "color:" + id});
    }

    private void properties(int mx, int my) {
        int x = width - RIGHT_W;
        UiTheme.rect(x, TOP, RIGHT_W, height - TOP - BOTTOM, 0xF0121419);
        int px = x + 6;
        int pw = RIGHT_W - 12;
        if (fieldsFor != sel) {
            applyFields();
            fields.clear();
            fieldsFor = sel;
        }
        CustomGui.El e = selected();
        int y = TOP + 6;
        if (sel == -2) {
            UiTheme.text("§e▣ Окно", px, y, 0xFFFFE080);
            y += 14;
            drawField("id", "id", g.id, px, y, pw, 32);
            y += 15;
            drawField("title", "Заголовок", g.title, px, y, pw, 100);
            y += 15;
            drawField("w", "Ширина", Integer.toString(g.width), px, y, pw / 2 - 2, 4);
            drawField("h", "Высота", Integer.toString(g.height), px + pw / 2 + 2, y, pw / 2 - 2, 4);
            y += 17;
            swatch(px, y, "Фон", g.bg, false, "wbg", mx, my);
            swatch(px + 60, y, "Рамка", g.border, g.border < 0, "wborder", mx, my);
            y += 15;
            UiTheme.text("§7Прозрачность фона: §f" + Math.round(g.bgAlpha * 100) + "%", px, y + 2, UiTheme.DIM);
            btn(x + RIGHT_W - 40, y, 16, 12, "−", "walpha:-", false, mx, my);
            btn(x + RIGHT_W - 22, y, 16, 12, "+", "walpha:+", false, mx, my);
            y += 16;
            btn(px, y, pw, 14, (g.dim ? "☑" : "☐") + " Затемнять мир за окном", "wdim", false, mx, my);
            y += 16;
            btn(px, y, pw, 14, (g.closeButton ? "☑" : "☐") + " Крестик «закрыть»", "wclose", false, mx, my);
            y += 16;
            btn(px, y, pw, 14, "Обновлять: " + (g.refresh == 0 ? "нет" : "раз в " + g.refresh + " с"), "wrefresh", false, mx, my);
            y += 20;
            List<String> help = fontRenderer.listFormattedStringToWidth("§8Открыть окно игроку: шаг §7gui " + g.id
                    + "§8, команда §7/gui open " + g.id + " <ник>§8, кнопка 3D-панели или NPC.", pw);
            for (String l : help) {
                UiTheme.text(l, px, y, 0xFFFFFFFF);
                y += 10;
            }
            return;
        }
        if (e == null) {
            UiTheme.text("§7Свойства", px, y, UiTheme.DIM);
            List<String> help = fontRenderer.listFormattedStringToWidth("§8Выбери элемент на окне — тут появятся его текст, размер,"
                    + " цвета и действия. Щёлкни по пустому месту окна — свойства самого окна.", pw);
            for (int i = 0; i < help.size(); i++) {
                UiTheme.text(help.get(i), px, y + 14 + i * 10, 0xFFFFFFFF);
            }
            return;
        }
        UiTheme.text("§e" + kindIcon(e.kind) + " " + kindTitle(e.kind), px, y, 0xFFFFE080);
        y += 14;
        boolean hasText = !"line".equals(e.kind) && !"image".equals(e.kind);
        if (hasText) {
            drawField("text", "item".equals(e.kind) ? "Число" : "input".equals(e.kind) ? "Текст" : "Текст", e.text, px, y, pw, 300);
            y += 15;
        }
        if (focusText) {
            focusText = false;
            GuiTextField t = fields.get("text");
            if (t != null) {
                t.setFocused(true);
                t.setCursorPositionEnd();
            }
        }
        drawField("x", "X", Integer.toString(e.x), px, y, pw / 2 - 2, 4);
        drawField("y", "Y", Integer.toString(e.y), px + pw / 2 + 2, y, pw / 2 - 2, 4);
        y += 15;
        drawField("ew", "Ш", Integer.toString(e.w), px, y, pw / 2 - 2, 4);
        drawField("eh", "В", Integer.toString(e.h), px + pw / 2 + 2, y, pw / 2 - 2, 4);
        y += 16;
        if (hasText && !"item".equals(e.kind)) {
            swatch(px, y, "Текст", e.color, false, "color", mx, my);
        }
        if (!"text".equals(e.kind) || e.bgAlpha > 0) {
            swatch(px + 62, y, "Фон", e.bg, false, "bg", mx, my);
        }
        y += 14;
        swatch(px, y, "Рамка", e.border, e.border < 0, "border", mx, my);
        UiTheme.text("§7фон " + Math.round(e.bgAlpha * 100) + "%", px + 62, y + 2, UiTheme.DIM);
        btn(x + RIGHT_W - 40, y, 16, 12, "−", "alpha:-", false, mx, my);
        btn(x + RIGHT_W - 22, y, 16, 12, "+", "alpha:+", false, mx, my);
        y += 15;
        if (hasText && !"item".equals(e.kind) && !"input".equals(e.kind)) {
            btn(px, y, 18, 13, "◧", "align:0", e.align == 0, mx, my);
            btn(px + 20, y, 18, 13, "▣", "align:1", e.align == 1, mx, my);
            btn(px + 40, y, 18, 13, "◨", "align:2", e.align == 2, mx, my);
            UiTheme.text("§7×" + fmt(e.scale), px + 64, y + 3, UiTheme.DIM);
            btn(px + 92, y, 16, 13, "A−", "scale:-", false, mx, my);
            btn(px + 110, y, 16, 13, "A+", "scale:+", false, mx, my);
            btn(px + 128, y, 36, 13, e.shadow ? "Тень✔" : "Тень", "shadow", e.shadow, mx, my);
            y += 16;
        }
        if ("button".equals(e.kind) || "rect".equals(e.kind)) {
            btn(px, y, pw, 13, (e.round ? "☑" : "☐") + " Скруглённые углы", "round", false, mx, my);
            y += 15;
        }
        if ("item".equals(e.kind)) {
            drawField("item", "Предмет", e.item, px, y, pw - 44, 80);
            btn(x + RIGHT_W - 48, y - 1, 42, 13, "Из руки", "item_hand", false, mx, my);
            y += 15;
        }
        if ("image".equals(e.kind)) {
            drawField("image", "Текстура", e.image, px, y, pw - 24, 40);
            btn(x + RIGHT_W - 28, y - 1, 22, 13, "▸", "image_next", false, mx, my);
            y += 13;
            UiTheme.text("§8▸ — следующая из «Контента»", px, y + 1, 0xFFFFFFFF);
            y += 12;
        }
        if ("bar".equals(e.kind) || "input".equals(e.kind)) {
            drawField("var", "Переменная", e.var, px, y, pw, 40);
            y += 15;
            if ("bar".equals(e.kind)) {
                drawField("max", "Максимум", fmt(e.max), px, y, pw, 10);
                y += 15;
            }
        }
        drawField("tooltip", "input".equals(e.kind) ? "Подсказка" : "При наведении", e.tooltip, px, y, pw, 200);
        y += 17;
        if ("button".equals(e.kind) || "item".equals(e.kind) || "image".equals(e.kind)) {
            btn(px, y, pw, 15, "⚡ Действия при нажатии (" + e.steps.size() + ")…", "steps", !e.steps.isEmpty(), mx, my);
            y += 17;
            btn(px, y, pw, 14, "Потом: " + thenTitle(e.then), "then", false, mx, my);
            y += 17;
        }
        btn(px, y, pw / 2 - 1, 14, "▲ Вперёд", "front", false, mx, my);
        btn(px + pw / 2 + 1, y, pw / 2 - 1, 14, "▼ Назад", "back", false, mx, my);
        y += 16;
        btn(px, y, pw / 2 - 1, 14, "⧉ Копия", "dup", false, mx, my);
        btn(px + pw / 2 + 1, y, pw / 2 - 1, 14, "§c✕ Удалить", "delete", false, mx, my);
        y += 16;
        btn(px, y, pw, 14, "По центру окна ↔", "center", false, mx, my);
    }

    private static String thenTitle(String t) {
        if ("close".equals(t)) {
            return "закрыть окно";
        }
        if (t.startsWith("gui:")) {
            return "окно «" + t.substring(4) + "»";
        }
        return "окно остаётся";
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? Integer.toString((int) d) : String.format(Locale.ROOT, "%.2f", d).replaceAll("0+$", "");
    }

    private static int num(String s, int def) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim().replace(',', '.')));
        } catch (Exception e) {
            return def;
        }
    }

    /** Перенести значения из полей в модель. */
    private void applyFields() {
        if (fields.isEmpty()) {
            return;
        }
        String before = snapshot();
        if (fieldsFor == -2) {
            g.id = get("id", g.id).trim().toLowerCase(Locale.ROOT);
            g.title = get("title", g.title);
            g.width = Math.max(40, Math.min(1000, num(get("w", "" + g.width), g.width)));
            g.height = Math.max(30, Math.min(1000, num(get("h", "" + g.height), g.height)));
        } else if (fieldsFor >= 0 && fieldsFor < g.elements.size()) {
            CustomGui.El e = g.elements.get(fieldsFor);
            e.text = get("text", e.text);
            e.x = num(get("x", "" + e.x), e.x);
            e.y = num(get("y", "" + e.y), e.y);
            e.w = Math.max(1, num(get("ew", "" + e.w), e.w));
            e.h = Math.max(1, num(get("eh", "" + e.h), e.h));
            e.item = get("item", e.item);
            e.image = get("image", e.image);
            e.var = get("var", e.var);
            e.tooltip = get("tooltip", e.tooltip);
            try {
                e.max = Double.parseDouble(get("max", fmt(e.max)).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                // оставить
            }
        }
        String after = snapshot();
        if (!before.equals(after)) {
            undo.push(before);
            redo.clear();
            dirty = true;
        }
    }

    private String get(String key, String def) {
        GuiTextField f = fields.get(key);
        return f == null ? def : f.getText();
    }

    // --- выбор цвета ------------------------------------------------------------------------------------

    private int colorValue(String id) {
        CustomGui.El e = selected();
        switch (id) {
            case "wbg":
                return g.bg;
            case "wborder":
                return g.border;
            case "color":
                return e == null ? 0 : e.color;
            case "bg":
                return e == null ? 0 : e.bg;
            default:
                return e == null ? -1 : e.border;
        }
    }

    private void setColor(String id, int rgb) {
        remember();
        CustomGui.El e = selected();
        switch (id) {
            case "wbg":
                g.bg = rgb;
                break;
            case "wborder":
                g.border = rgb;
                break;
            case "color":
                if (e != null) {
                    e.color = rgb;
                }
                break;
            case "bg":
                if (e != null) {
                    e.bg = rgb;
                    if (e.bgAlpha < 0.05) {
                        e.bgAlpha = 1;
                    }
                }
                break;
            default:
                if (e != null) {
                    e.border = rgb;
                }
        }
    }

    private void colorPopup(int mx, int my) {
        int w = 150;
        int h = 96;
        int x = Math.min(colorX, width - w - 4);
        int y = Math.min(colorY, height - h - 4);
        UiTheme.panel(x, y, w, h, 0xF8181B22, UiTheme.ACCENT);
        for (int i = 0; i < COLORS.length; i++) {
            int cx = x + 6 + (i % 8) * 17;
            int cy = y + 6 + (i / 8) * 14;
            UiTheme.rect(cx, cy, 15, 12, 0xFF000000);
            UiTheme.rect(cx + 1, cy + 1, 13, 10, 0xFF000000 | COLORS[i]);
        }
        int by = y + 50;
        for (int i = 0; i < w - 12; i++) {
            UiTheme.rect(x + 6 + i, by, 1, 10, 0xFF000000 | Color.HSBtoRGB(i / (float) (w - 13), 0.8F, 1.0F));
        }
        if (hex == null) {
            hex = new GuiTextField(9900, fontRenderer, 0, 0, 56, 12);
            hex.setMaxStringLength(7);
            hex.setEnableBackgroundDrawing(false);
        }
        UiTheme.text("§7HEX", x + 6, y + 68, UiTheme.DIM);
        UiTheme.rect(x + 30, y + 66, 60, 12, UiTheme.INPUT);
        hex.x = x + 33;
        hex.y = y + 68;
        if (!hex.isFocused()) {
            int v = colorValue(colorProp);
            hex.setText(v < 0 ? "" : String.format("#%06X", v & 0xFFFFFF));
        }
        hex.drawTextBox();
        boolean canNone = "border".equals(colorProp) || "wborder".equals(colorProp);
        if (canNone) {
            btn(x + 96, y + 65, 48, 14, "Без рамки", "cnone", false, -1, -1);
        }
        UiTheme.text("§8Enter — применить, Esc — закрыть", x + 6, y + 84, 0xFFFFFFFF);
    }

    private boolean clickColor(int mx, int my) {
        int w = 150;
        int h = 96;
        int x = Math.min(colorX, width - w - 4);
        int y = Math.min(colorY, height - h - 4);
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            colorProp = null;
            return false;
        }
        for (int i = 0; i < COLORS.length; i++) {
            int cx = x + 6 + (i % 8) * 17;
            int cy = y + 6 + (i / 8) * 14;
            if (mx >= cx && mx < cx + 15 && my >= cy && my < cy + 12) {
                setColor(colorProp, COLORS[i]);
                return true;
            }
        }
        if (my >= y + 50 && my < y + 60 && mx >= x + 6 && mx < x + w - 6) {
            setColor(colorProp, Color.HSBtoRGB((mx - x - 6) / (float) (w - 13), 0.8F, 1.0F) & 0xFFFFFF);
            return true;
        }
        boolean canNone = "border".equals(colorProp) || "wborder".equals(colorProp);
        if (canNone && mx >= x + 96 && mx < x + 144 && my >= y + 65 && my < y + 79) {
            setColor(colorProp, -1);
            colorProp = null;
            return true;
        }
        hex.mouseClicked(mx, my, 0);
        return true;
    }

    // --- ручки и попадания ---------------------------------------------------------------------------

    /** Ручки: 1 ↖, 2 ↑, 3 ↗, 4 →, 5 ↘, 6 ↓, 7 ↙, 8 ←. */
    private static int[] handlePos(CustomGui.El e, int h) {
        int x0 = e.x;
        int y0 = e.y;
        int x1 = e.x + e.w;
        int y1 = e.y + e.h;
        int xm = e.x + e.w / 2;
        int ym = e.y + e.h / 2;
        switch (h) {
            case 1:
                return new int[]{x0, y0};
            case 2:
                return new int[]{xm, y0};
            case 3:
                return new int[]{x1, y0};
            case 4:
                return new int[]{x1, ym};
            case 5:
                return new int[]{x1, y1};
            case 6:
                return new int[]{xm, y1};
            case 7:
                return new int[]{x0, y1};
            default:
                return new int[]{x0, ym};
        }
    }

    private int handleAt(int mx, int my) {
        CustomGui.El e = selected();
        if (e == null) {
            return -1;
        }
        int x = cx(mx);
        int y = cy(my);
        int tol = Math.max(3, (int) (4 / zoom));
        for (int h = 1; h <= 8; h++) {
            int[] p = handlePos(e, h);
            if (Math.abs(x - p[0]) <= tol && Math.abs(y - p[1]) <= tol) {
                return h;
            }
        }
        return -1;
    }

    private int elementAt(int mx, int my) {
        int x = cx(mx);
        int y = cy(my);
        for (int i = g.elements.size() - 1; i >= 0; i--) {
            CustomGui.El e = g.elements.get(i);
            if (x >= e.x - 1 && x < e.x + e.w + 1 && y >= e.y - 1 && y < e.y + e.h + 1) {
                return i;
            }
        }
        return -1;
    }

    private String hitAt(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            int x = (Integer) h[0];
            int y = (Integer) h[1];
            if (mx >= x && mx < x + (Integer) h[2] && my >= y && my < y + (Integer) h[3]) {
                return (String) h[4];
            }
        }
        return null;
    }

    // --- умные направляющие --------------------------------------------------------------------------

    /** Подогнать прямоугольник к краям/центрам окна и других элементов (или к сетке). */
    private int[] snapRect(int[] r, boolean moveX, boolean moveY, boolean resizeL, boolean resizeR, boolean resizeT, boolean resizeB) {
        guides.clear();
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU)) {
            return r;
        }
        int tol = Math.max(2, (int) (4 / zoom));
        List<Integer> xs = new ArrayList<Integer>(Arrays.asList(0, g.width / 2, g.width, 6, g.width - 6));
        List<Integer> ys = new ArrayList<Integer>(Arrays.asList(0, g.height / 2, g.height, 6, g.height - 6, 20));
        for (int i = 0; i < g.elements.size(); i++) {
            if (i == sel) {
                continue;
            }
            CustomGui.El o = g.elements.get(i);
            xs.add(o.x);
            xs.add(o.x + o.w / 2);
            xs.add(o.x + o.w);
            ys.add(o.y);
            ys.add(o.y + o.h / 2);
            ys.add(o.y + o.h);
        }
        int x = r[0];
        int y = r[1];
        int w = r[2];
        int h = r[3];
        boolean gx = false;
        boolean gy = false;
        if (moveX) {
            int[] cand = {x, x + w / 2, x + w};
            int best = tol + 1;
            int shift = 0;
            int line = 0;
            for (int k = 0; k < 3; k++) {
                for (int v : xs) {
                    int d = v - cand[k];
                    if (Math.abs(d) < Math.abs(best)) {
                        best = d;
                        shift = d;
                        line = v;
                    }
                }
            }
            if (Math.abs(best) <= tol) {
                x += shift;
                guides.add(new int[]{0, line});
                gx = true;
            }
        }
        if (moveY) {
            int[] cand = {y, y + h / 2, y + h};
            int best = tol + 1;
            int shift = 0;
            int line = 0;
            for (int k = 0; k < 3; k++) {
                for (int v : ys) {
                    int d = v - cand[k];
                    if (Math.abs(d) < Math.abs(best)) {
                        best = d;
                        shift = d;
                        line = v;
                    }
                }
            }
            if (Math.abs(best) <= tol) {
                y += shift;
                guides.add(new int[]{1, line});
                gy = true;
            }
        }
        if (resizeR || resizeL) {
            int edge = resizeR ? x + w : x;
            for (int v : xs) {
                if (Math.abs(v - edge) <= tol) {
                    if (resizeR) {
                        w = v - x;
                    } else {
                        w += x - v;
                        x = v;
                    }
                    guides.add(new int[]{0, v});
                    gx = true;
                    break;
                }
            }
        }
        if (resizeB || resizeT) {
            int edge = resizeB ? y + h : y;
            for (int v : ys) {
                if (Math.abs(v - edge) <= tol) {
                    if (resizeB) {
                        h = v - y;
                    } else {
                        h += y - v;
                        y = v;
                    }
                    guides.add(new int[]{1, v});
                    gy = true;
                    break;
                }
            }
        }
        if (grid > 0) {
            if (!gx) {
                if (moveX) {
                    x = Math.round(x / (float) grid) * grid;
                } else if (resizeR) {
                    w = Math.round((x + w) / (float) grid) * grid - x;
                } else if (resizeL) {
                    int nx = Math.round(x / (float) grid) * grid;
                    w += x - nx;
                    x = nx;
                }
            }
            if (!gy) {
                if (moveY) {
                    y = Math.round(y / (float) grid) * grid;
                } else if (resizeB) {
                    h = Math.round((y + h) / (float) grid) * grid - y;
                } else if (resizeT) {
                    int ny = Math.round(y / (float) grid) * grid;
                    h += y - ny;
                    y = ny;
                }
            }
        }
        return new int[]{x, y, Math.max(1, w), Math.max(1, h)};
    }

    // --- ввод ----------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (colorProp != null && clickColor(mx, my)) {
            return;
        }
        boolean fieldClicked = false;
        for (GuiTextField f : fields.values()) {
            boolean was = f.isFocused();
            f.mouseClicked(mx, my, button);
            if (f.isFocused()) {
                fieldClicked = true;
            }
            if (was && !f.isFocused()) {
                applyFields();
            }
        }
        if (fieldClicked) {
            return;
        }
        String hit = hitAt(mx, my);
        if (hit != null && button == 0) {
            action(hit, mx, my);
            return;
        }
        Object[] pe = paletteAt(mx, my);
        if (pe != null && button == 0) {
            dragMode = 10;
            dragKind = (String) pe[0];
            dragMx = mx;
            dragMy = my;
            dragRect = null;
            dragMoved = false;
            return;
        }
        if (!inCanvasArea(mx, my) || preview) {
            return;
        }
        if (button == 1) {
            sel = elementAt(mx, my);
            return;
        }
        if (button != 0) {
            return;
        }
        int x = cx(mx);
        int y = cy(my);
        // уголок окна
        if (Math.abs(x - g.width) <= 5 && Math.abs(y - g.height) <= 5) {
            remember();
            sel = -2;
            dragMode = 9;
            dragMx = mx;
            dragMy = my;
            dragRect = new int[]{0, 0, g.width, g.height};
            return;
        }
        int h = handleAt(mx, my);
        if (h > 0) {
            remember();
            CustomGui.El e = selected();
            dragMode = h;
            dragMx = mx;
            dragMy = my;
            dragRect = new int[]{e.x, e.y, e.w, e.h};
            return;
        }
        int i = elementAt(mx, my);
        if (i >= 0) {
            long now = System.currentTimeMillis();
            boolean dbl = i == lastClickIdx && now - lastClick < 350;
            lastClick = now;
            lastClickIdx = i;
            sel = i;
            if (dbl) {
                focusText = true;
                return;
            }
            remember();
            CustomGui.El e = g.elements.get(i);
            dragMode = 0;
            dragMx = mx;
            dragMy = my;
            dragMoved = false;
            dragRect = new int[]{e.x, e.y, e.w, e.h};
            return;
        }
        sel = x >= 0 && y >= 0 && x < g.width && y < g.height ? -2 : -1;
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        if (dragMode < 0 || button != 0) {
            return;
        }
        int dx = (int) Math.round((mx - dragMx) / zoom);
        int dy = (int) Math.round((my - dragMy) / zoom);
        if (dragMode == 10) {
            if (Math.abs(mx - dragMx) + Math.abs(my - dragMy) > 3) {
                dragMoved = true;
            }
            Object[] pe = paletteFor(dragKind);
            int w = (Integer) pe[3];
            int h = (Integer) pe[4];
            dragRect = snapRect(new int[]{cx(mx) - w / 2, cy(my) - h / 2, w, h}, true, true, false, false, false, false);
            return;
        }
        if (dragMode == 9) {
            g.width = Math.max(40, Math.min(1000, dragRect[2] + dx));
            g.height = Math.max(30, Math.min(1000, dragRect[3] + dy));
            if (grid > 0) {
                g.width = Math.round(g.width / (float) grid) * grid;
                g.height = Math.round(g.height / (float) grid) * grid;
            }
            fieldsFor = -9;
            dirty = true;
            return;
        }
        CustomGui.El e = selected();
        if (e == null) {
            return;
        }
        if (dx != 0 || dy != 0) {
            dragMoved = true;
        }
        int x = dragRect[0];
        int y = dragRect[1];
        int w = dragRect[2];
        int h = dragRect[3];
        boolean l = false;
        boolean r = false;
        boolean t = false;
        boolean b = false;
        if (dragMode == 0) {
            x += dx;
            y += dy;
        } else {
            if (dragMode == 1 || dragMode == 7 || dragMode == 8) {
                x += dx;
                w -= dx;
                l = true;
            }
            if (dragMode == 3 || dragMode == 4 || dragMode == 5) {
                w += dx;
                r = true;
            }
            if (dragMode == 1 || dragMode == 2 || dragMode == 3) {
                y += dy;
                h -= dy;
                t = true;
            }
            if (dragMode == 5 || dragMode == 6 || dragMode == 7) {
                h += dy;
                b = true;
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && dragRect[3] > 0) {
                // сохранить пропорции
                double k = dragRect[2] / (double) dragRect[3];
                if (l || r) {
                    h = (int) Math.round(w / k);
                } else {
                    w = (int) Math.round(h * k);
                }
            }
        }
        int[] s = snapRect(new int[]{x, y, Math.max(1, w), Math.max(1, h)}, dragMode == 0, dragMode == 0, l, r, t, b);
        e.x = s[0];
        e.y = s[1];
        e.w = s[2];
        e.h = s[3];
        fieldsFor = -9;
        dirty = true;
    }

    private static Object[] paletteFor(String kind) {
        for (Object[] p : PALETTE) {
            if (p[0].equals(kind)) {
                return p;
            }
        }
        return PALETTE[0];
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragMode == 10) {
            Object[] pe = paletteFor(dragKind);
            int w = (Integer) pe[3];
            int h = (Integer) pe[4];
            int x;
            int y;
            if (dragMoved && dragRect != null && inCanvasArea(mx, my)) {
                x = dragRect[0];
                y = dragRect[1];
            } else if (!dragMoved) {
                x = (g.width - w) / 2;
                y = (g.height - h) / 2;
            } else {
                dragMode = -1;
                guides.clear();
                return;
            }
            addElement(dragKind, x, y, w, h);
        } else if (dragMode == 0 && !dragMoved && !undo.isEmpty()) {
            // просто щелчок — изменений нет
            undo.pop();
        }
        dragMode = -1;
        dragRect = null;
        guides.clear();
    }

    private void addElement(String kind, int x, int y, int w, int h) {
        remember();
        CustomGui.El e = new CustomGui.El();
        e.kind = kind;
        e.x = Math.max(0, Math.min(g.width - Math.min(w, g.width), x));
        e.y = Math.max(0, Math.min(g.height - Math.min(h, g.height), y));
        e.w = w;
        e.h = h;
        switch (kind) {
            case "text":
                e.text = "Текст";
                e.bgAlpha = 0;
                break;
            case "button":
                e.text = "Кнопка";
                e.align = 1;
                break;
            case "rect":
                e.bg = 0x1F232C;
                e.border = 0x2C303B;
                break;
            case "item":
                e.bgAlpha = 0;
                e.item = "minecraft:diamond@0";
                break;
            case "bar":
                e.bg = 0x2E8B57;
                e.var = "progress";
                break;
            case "input":
                e.var = "answer";
                e.tooltip = "Напиши здесь…";
                break;
            case "line":
                e.bg = 0x3A3F4C;
                break;
            default:
        }
        e.normalize();
        g.elements.add(e);
        sel = g.elements.size() - 1;
        fieldsFor = -9;
        say(kindTitle(kind) + " добавлен");
    }

    private void action(String id, int mx, int my) {
        CustomGui.El e = selected();
        if (id.startsWith("layer:")) {
            sel = Integer.parseInt(id.substring(6));
            return;
        }
        if (id.startsWith("color:")) {
            applyFields();
            colorProp = id.substring(6);
            colorX = mx - 20;
            colorY = my + 10;
            if (hex != null) {
                hex.setFocused(false);
            }
            return;
        }
        if (id.startsWith("align:") && e != null) {
            remember();
            e.align = Integer.parseInt(id.substring(6));
            return;
        }
        if (id.startsWith("scale:") && e != null) {
            remember();
            e.scale = Math.max(0.5, Math.min(4, e.scale + (id.endsWith("+") ? 0.25 : -0.25)));
            return;
        }
        if (id.startsWith("alpha:") && e != null) {
            remember();
            e.bgAlpha = Math.max(0, Math.min(1, Math.round((e.bgAlpha + (id.endsWith("+") ? 0.1 : -0.1)) * 10) / 10.0));
            return;
        }
        if (id.startsWith("walpha:")) {
            remember();
            g.bgAlpha = Math.max(0, Math.min(1, Math.round((g.bgAlpha + (id.endsWith("+") ? 0.1 : -0.1)) * 10) / 10.0));
            return;
        }
        switch (id) {
            case "save":
                save();
                break;
            case "test": {
                save();
                NBTTagCompound d = new NBTTagCompound();
                d.setString("id", g.id);
                ClientV2.send("cgui_test", d);
                break;
            }
            case "preview":
                preview = !preview;
                break;
            case "undo":
                doUndo();
                break;
            case "redo":
                doRedo();
                break;
            case "grid":
                grid = grid == 0 ? 2 : grid == 2 ? 4 : grid == 4 ? 8 : 0;
                say(grid == 0 ? "Без сетки (направляющие остаются, Alt — без них)" : "Сетка " + grid);
                break;
            case "zoom":
                zoom = zoom >= 2 ? 0.5F : zoom >= 1.5 ? 2F : zoom >= 1 ? 1.5F : 1F;
                break;
            case "list":
                if (dirty) {
                    save();
                }
                ClientV2.send("cgui_list", new NBTTagCompound());
                break;
            case "close":
                if (dirty && !confirmExit) {
                    confirmExit = true;
                    say("Есть несохранённые изменения — нажми ещё раз, чтобы выйти без сохранения");
                } else {
                    mc.displayGuiScreen(null);
                }
                break;
            case "wdim":
                remember();
                g.dim = !g.dim;
                break;
            case "wclose":
                remember();
                g.closeButton = !g.closeButton;
                break;
            case "wrefresh":
                remember();
                g.refresh = g.refresh == 0 ? 1 : g.refresh == 1 ? 2 : g.refresh == 2 ? 5 : g.refresh == 5 ? 10 : 0;
                break;
            case "shadow":
                if (e != null) {
                    remember();
                    e.shadow = !e.shadow;
                }
                break;
            case "round":
                if (e != null) {
                    remember();
                    e.round = !e.round;
                }
                break;
            case "item_hand":
                if (e != null && mc.player != null) {
                    ItemStack h = mc.player.getHeldItemMainhand();
                    if (!h.isEmpty()) {
                        remember();
                        ResourceLocation rl = h.getItem().getRegistryName();
                        e.item = (rl == null ? "minecraft:stone" : rl.toString()) + "@" + h.getMetadata();
                        fieldsFor = -9;
                    } else {
                        say("Возьми предмет в руку");
                    }
                }
                break;
            case "image_next":
                if (e != null) {
                    List<String> keys = new ArrayList<String>(CustomContent.active().textures.keySet());
                    if (keys.isEmpty()) {
                        say("Нарисуй текстуру в «Контенте» (/content → Текстуры) и примени");
                    } else {
                        remember();
                        int i = keys.indexOf(e.image);
                        e.image = keys.get((i + 1) % keys.size());
                        fieldsFor = -9;
                    }
                }
                break;
            case "steps":
                if (e != null) {
                    save();
                    NBTTagCompound d = new NBTTagCompound();
                    d.setString("id", g.id);
                    d.setInteger("i", sel);
                    ClientV2.send("cgui_steps", d);
                }
                break;
            case "then":
                if (e != null) {
                    remember();
                    List<String> opts = new ArrayList<String>(Arrays.asList("", "close"));
                    for (String x : guiIds) {
                        if (!x.isEmpty() && !x.equals(g.id)) {
                            opts.add("gui:" + x);
                        }
                    }
                    int i = opts.indexOf(e.then);
                    e.then = opts.get((i + 1) % opts.size());
                }
                break;
            case "front":
                if (e != null && sel < g.elements.size() - 1) {
                    remember();
                    g.elements.remove(sel);
                    g.elements.add(sel + 1, e);
                    sel++;
                }
                break;
            case "back":
                if (e != null && sel > 0) {
                    remember();
                    g.elements.remove(sel);
                    g.elements.add(sel - 1, e);
                    sel--;
                }
                break;
            case "dup":
                duplicate();
                break;
            case "delete":
                delete();
                break;
            case "center":
                if (e != null) {
                    remember();
                    e.x = (g.width - e.w) / 2;
                    fieldsFor = -9;
                }
                break;
            case "cnone":
                break;
            default:
        }
    }

    private CustomGui.El copyOf(CustomGui.El e) {
        return JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(e), CustomGui.El.class);
    }

    private void duplicate() {
        CustomGui.El e = selected();
        if (e == null) {
            return;
        }
        remember();
        CustomGui.El c = copyOf(e);
        c.x += 6;
        c.y += 6;
        g.elements.add(c);
        sel = g.elements.size() - 1;
        fieldsFor = -9;
    }

    private void delete() {
        if (selected() == null) {
            return;
        }
        remember();
        g.elements.remove(sel);
        sel = -1;
        fieldsFor = -9;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        if (mx < LEFT_W) {
            layerScroll += wheel > 0 ? -2 : 2;
            return;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) || mx < width - RIGHT_W) {
            zoom = Math.max(0.25F, Math.min(3.0F, zoom * (wheel > 0 ? 1.15F : 0.87F)));
        }
    }

    @Override
    protected void keyTyped(char c, int code) throws IOException {
        if (colorProp != null) {
            if (hex != null && hex.isFocused()) {
                if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                    try {
                        setColor(colorProp, Integer.parseInt(hex.getText().replace("#", "").trim(), 16) & 0xFFFFFF);
                    } catch (NumberFormatException ignored) {
                        say("HEX — например #FF8800");
                    }
                    hex.setFocused(false);
                } else if (code == Keyboard.KEY_ESCAPE) {
                    hex.setFocused(false);
                } else {
                    hex.textboxKeyTyped(c, code);
                }
                return;
            }
            if (code == Keyboard.KEY_ESCAPE) {
                colorProp = null;
                return;
            }
        }
        for (GuiTextField f : fields.values()) {
            if (f.isFocused()) {
                if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER || code == Keyboard.KEY_ESCAPE) {
                    f.setFocused(false);
                    applyFields();
                } else if (code == Keyboard.KEY_TAB) {
                    f.setFocused(false);
                    applyFields();
                    List<GuiTextField> l = new ArrayList<GuiTextField>(fields.values());
                    int i = l.indexOf(f);
                    if (i + 1 < l.size()) {
                        l.get(i + 1).setFocused(true);
                    }
                } else {
                    f.textboxKeyTyped(c, code);
                }
                return;
            }
        }
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        CustomGui.El e = selected();
        if (code == Keyboard.KEY_ESCAPE) {
            if (preview) {
                preview = false;
            } else if (sel != -1) {
                sel = -1;
            } else {
                action("close", 0, 0);
            }
            return;
        }
        if (ctrl && code == Keyboard.KEY_S) {
            save();
        } else if (ctrl && code == Keyboard.KEY_Z) {
            doUndo();
        } else if (ctrl && code == Keyboard.KEY_Y) {
            doRedo();
        } else if (ctrl && code == Keyboard.KEY_D) {
            duplicate();
        } else if (ctrl && code == Keyboard.KEY_C && e != null) {
            clipboard = copyOf(e);
            say("Скопировано");
        } else if (ctrl && code == Keyboard.KEY_V && clipboard != null) {
            remember();
            CustomGui.El p = copyOf(clipboard);
            p.x += 8;
            p.y += 8;
            g.elements.add(p);
            sel = g.elements.size() - 1;
            fieldsFor = -9;
        } else if (code == Keyboard.KEY_DELETE || code == Keyboard.KEY_BACK) {
            delete();
        } else if (e != null && (code == Keyboard.KEY_LEFT || code == Keyboard.KEY_RIGHT || code == Keyboard.KEY_UP || code == Keyboard.KEY_DOWN)) {
            remember();
            int s = shift ? 8 : 1;
            if (code == Keyboard.KEY_LEFT) {
                e.x -= s;
            } else if (code == Keyboard.KEY_RIGHT) {
                e.x += s;
            } else if (code == Keyboard.KEY_UP) {
                e.y -= s;
            } else {
                e.y += s;
            }
            fieldsFor = -9;
        } else if (code == Keyboard.KEY_P) {
            preview = !preview;
        }
    }

    @Override
    public void updateScreen() {
        for (GuiTextField f : fields.values()) {
            f.updateCursorCounter();
        }
        if (hex != null) {
            hex.updateCursorCounter();
        }
    }
}
