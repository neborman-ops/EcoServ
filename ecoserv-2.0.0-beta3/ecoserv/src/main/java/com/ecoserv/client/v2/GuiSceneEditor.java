package com.ecoserv.client.v2;

import com.ecoserv.client.ui.UiTheme;
import com.ecoserv.content.ContentPack;
import com.ecoserv.content.CustomContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Редактор сцены (2.0 beta3) — как в Unity: свободная камера (ПКМ + WASD/QE, колесо), иерархия слева,
 * инспектор справа, гизмо перемещения/поворота/размера (W/E/R), добавление объектов, копия, удаление, отмена.
 */
@SideOnly(Side.CLIENT)
public class GuiSceneEditor extends GuiScreen {

    private static final String[] MARKERS = {"Точка появления", "Появление: красные", "Появление: синие", "Чекпоинт", "Финиш",
            "Зона смерти", "Место предмета", "Место моба", "Место NPC", "Точка камеры", "Точка пути", "Стык структуры",
            "Невидимый свет", "Звук", "Надпись в мире", "Свой маркер"};
    private static final String[] ELECTRIC = {"Солнечная панель", "Генератор", "Батарея", "Выключатель", "Разветвитель",
            "Логика И", "Логика ИЛИ", "Логика ИСКЛ-ИЛИ", "Таймер", "Датчик игрока", "Вход редстоуна", "Выход в редстоун", "Лампа",
            "Привод двери", "Сирена", "Измеритель"};
    private static final String[][] NPCS = {{"minecraft:villager", "Житель"}, {"minecraft:armor_stand", "Стойка"},
            {"minecraft:zombie", "Зомби"}, {"minecraft:skeleton", "Скелет"}, {"minecraft:witch", "Ведьма"},
            {"minecraft:iron_golem", "Голем"}, {"minecraft:wolf", "Волк"}, {"minecraft:pig", "Свинья"}};

    private static final int TOP = 20;
    private static final int LEFT_W = 150;
    private static final int RIGHT_W = 176;

    private long lastNanos;
    private boolean looking;
    private int lastMx;
    private int lastMy;
    private int dragHandle = -1;
    private EditorState.Obj dragStart;
    private double dragParam;
    private Vec3d dragPoint;
    private double dragAngle;
    private int dragMy;
    private long lastSend;
    private int hierScroll;
    private String hierFilter = "";
    private GuiTextField search;
    private final List<GuiTextField> fields = new ArrayList<GuiTextField>();
    private final List<String> fieldKeys = new ArrayList<String>();
    private EditorState.Obj fieldsFor;
    /** Меню «Добавить»: null — закрыто, иначе путь подменю. */
    private String menu;
    private int menuX;
    private int menuY;
    private final List<String[]> menuItems = new ArrayList<String[]>();
    private int menuScroll;

    public GuiSceneEditor() {
        EditorState.open = true;
        CameraFx.startEditor();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        search = new GuiTextField(1, fontRenderer, 6, TOP + 6, LEFT_W - 12, 12);
        search.setMaxStringLength(32);
        search.setText(hierFilter);
        fieldsFor = null;
        lastNanos = System.nanoTime();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        EditorState.open = false;
        CameraFx.stopEditor();
    }

    private static void send(String type, NBTTagCompound d) {
        ClientV2.send(type, d);
    }

    private static NBTTagCompound tag(String type, String id) {
        NBTTagCompound d = new NBTTagCompound();
        d.setString("type", type);
        d.setString("id", id);
        return d;
    }

    // --- камера --------------------------------------------------------------------------------------

    private void flyCamera(float dt) {
        boolean anyField = search.isFocused();
        for (GuiTextField f : fields) {
            anyField |= f.isFocused();
        }
        if (anyField || !looking) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        double speed = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 24 : 8) * dt;
        double yaw = Math.toRadians(CameraFx.eyaw);
        double pitch = Math.toRadians(CameraFx.epitch);
        double fwd = (key(mc.gameSettings.keyBindForward.getKeyCode()) ? 1 : 0) - (key(mc.gameSettings.keyBindBack.getKeyCode()) ? 1 : 0);
        double str = (key(mc.gameSettings.keyBindLeft.getKeyCode()) ? 1 : 0) - (key(mc.gameSettings.keyBindRight.getKeyCode()) ? 1 : 0);
        double up = (Keyboard.isKeyDown(Keyboard.KEY_E) || Keyboard.isKeyDown(Keyboard.KEY_SPACE) ? 1 : 0) - (Keyboard.isKeyDown(Keyboard.KEY_Q) ? 1 : 0);
        CameraFx.ex += (-Math.sin(yaw) * Math.cos(pitch) * fwd + Math.cos(yaw) * str) * speed;
        CameraFx.ez += (Math.cos(yaw) * Math.cos(pitch) * fwd + Math.sin(yaw) * str) * speed;
        CameraFx.ey += (-Math.sin(pitch) * fwd + up) * speed;
    }

    private static boolean key(int code) {
        return code > 0 && Keyboard.isKeyDown(code);
    }

    private void focus(EditorState.Obj o) {
        Vec3d p = o.pivot();
        double yaw = Math.toRadians(CameraFx.eyaw);
        double pitch = Math.toRadians(CameraFx.epitch);
        double dist = Math.max(4, Math.max(o.sx, o.sy) * 1.8);
        CameraFx.ex = p.x + Math.sin(yaw) * Math.cos(pitch) * dist;
        CameraFx.ey = p.y + Math.sin(pitch) * dist;
        CameraFx.ez = p.z - Math.cos(yaw) * Math.cos(pitch) * dist;
    }

    // --- рисование --------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        flyCamera(dt);
        updateHover(mx, my);
        // верхняя панель
        UiTheme.rect(0, 0, width, TOP, 0xE0101218);
        int x = 4;
        x = tool(x, "✥ Двигать (W)", EditorState.mode == 0, mx, my);
        x = tool(x, "⟳ Вращать (E)", EditorState.mode == 1, mx, my);
        x = tool(x, "⤢ Размер (R)", EditorState.mode == 2, mx, my);
        x += 6;
        x = tool(x, EditorState.snap ? "Сетка " + fmt(EditorState.step) : "Сетка выкл", EditorState.snap, mx, my);
        x += 6;
        x = tool(x, "+ Добавить", menu != null, mx, my);
        x += 6;
        x = tool(x, "↶ " + EditorState.undo, false, mx, my);
        x = tool(x, "↷ " + EditorState.redo, false, mx, my);
        x += 6;
        x = tool(x, "☀ Освещение", false, mx, my);
        x = tool(x, "▣ Контент", false, mx, my);
        x = tool(x, "⛏ Стройка", false, mx, my);
        tool(width - 70, "✕ Выйти", false, mx, my);
        // иерархия
        UiTheme.rect(0, TOP, LEFT_W, height - TOP - 14, 0xD0121419);
        UiTheme.rect(5, TOP + 5, LEFT_W - 10, 14, UiTheme.INPUT);
        search.x = 8;
        search.y = TOP + 8;
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) {
            UiTheme.text("§8поиск…", 8, TOP + 8, UiTheme.MUTED);
        }
        List<EditorState.Obj> list = filtered();
        int rowH = 11;
        int listTop = TOP + 24;
        int visible = (height - 14 - listTop) / rowH;
        hierScroll = Math.max(0, Math.min(Math.max(0, list.size() - visible), hierScroll));
        for (int i = 0; i < visible && i + hierScroll < list.size(); i++) {
            EditorState.Obj o = list.get(i + hierScroll);
            int y = listTop + i * rowH;
            boolean sel = o == EditorState.selected;
            boolean hov = mx < LEFT_W && my >= y && my < y + rowH;
            if (sel || hov) {
                UiTheme.rect(2, y, LEFT_W - 4, rowH, sel ? 0xFF2A4170 : 0xFF22262F);
            }
            UiTheme.rect(5, y + 3, 5, 5, 0xFF000000 | o.color);
            UiTheme.text(UiTheme.trim(o.label, LEFT_W - 18), 13, y + 2, sel ? 0xFFFFE080 : UiTheme.TEXT);
        }
        // инспектор
        inspector(mx, my);
        // строка подсказки
        UiTheme.rect(0, height - 14, width, 14, 0xE0101218);
        UiTheme.text("§7ПКМ — взгляд, ПКМ+WASD/QE — полёт, колесо — вперёд/назад, Shift — быстрее · F — к объекту · Del — удалить ·"
                + " Ctrl+D — копия · Ctrl+Z/Y · G — сетка", 4, height - 11, UiTheme.DIM);
        if (menu != null) {
            drawMenu(mx, my);
        }
        super.drawScreen(mx, my, partialTicks);
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? Integer.toString((int) d) : Double.toString(d);
    }

    private int tool(int x, String label, boolean on, int mx, int my) {
        int w = fontRenderer.getStringWidth(label) + 10;
        boolean hov = mx >= x && mx < x + w && my < TOP;
        UiTheme.round(x, 3, w, 14, on ? 0xFF2F5BB0 : hov ? 0xFF2A2F3A : 0xFF1C2028);
        UiTheme.text(label, x + 5, 6, on ? 0xFFFFFFFF : UiTheme.TEXT);
        return x + w + 2;
    }

    private String toolAt(int mx, int my) {
        if (my >= TOP) {
            return null;
        }
        String[] labels = {"✥ Двигать (W)", "⟳ Вращать (E)", "⤢ Размер (R)", "|",
                EditorState.snap ? "Сетка " + fmt(EditorState.step) : "Сетка выкл", "|", "+ Добавить", "|",
                "↶ " + EditorState.undo, "↷ " + EditorState.redo, "|", "☀ Освещение", "▣ Контент", "⛏ Стройка"};
        String[] ids = {"move", "rotate", "scale", "", "snap", "", "add", "", "undo", "redo", "", "lighting", "content", "build"};
        int x = 4;
        for (int i = 0; i < labels.length; i++) {
            if ("|".equals(labels[i])) {
                x += 6;
                continue;
            }
            int w = fontRenderer.getStringWidth(labels[i]) + 10;
            if (mx >= x && mx < x + w) {
                return ids[i];
            }
            x += w + 2;
        }
        if (mx >= width - 70) {
            return "exit";
        }
        return null;
    }

    private List<EditorState.Obj> filtered() {
        List<EditorState.Obj> out = new ArrayList<EditorState.Obj>();
        String f = search == null ? "" : search.getText().toLowerCase(Locale.ROOT);
        for (EditorState.Obj o : EditorState.OBJS) {
            if (f.isEmpty() || o.label.toLowerCase(Locale.ROOT).contains(f) || o.type.contains(f)) {
                out.add(o);
            }
        }
        return out;
    }

    // --- инспектор -----------------------------------------------------------------------------------------

    private void buildFields(EditorState.Obj o) {
        fields.clear();
        fieldKeys.clear();
        fieldsFor = o;
        if (o == null) {
            return;
        }
        int x0 = width - RIGHT_W + 52;
        int y = TOP + 40;
        addField("x", x0, y, o.x);
        addField("y", x0, y + 16, o.y);
        addField("z", x0, y + 32, o.z);
        y += 52;
        if (o.can("yaw")) {
            addField("yaw", x0, y, o.yaw);
            y += 16;
        }
        if (o.can("pitch")) {
            addField("pitch", x0, y, o.pitch);
            y += 16;
        }
        if (o.can("scale")) {
            addField("sx", x0, y, o.sx);
            y += 16;
            if (!"light".equals(o.type)) {
                addField("sy", x0, y, o.sy);
                y += 16;
            }
            if ("zone".equals(o.type)) {
                addField("sz", x0, y, o.sz);
            }
        }
    }

    private void addField(String key, int x, int y, double v) {
        GuiTextField f = new GuiTextField(100 + fields.size(), fontRenderer, x, y, RIGHT_W - 60, 12);
        f.setMaxStringLength(16);
        f.setText(String.format(Locale.ROOT, "%.2f", v));
        fields.add(f);
        fieldKeys.add(key);
    }

    private static String fieldTitle(EditorState.Obj o, String k) {
        switch (k) {
            case "yaw":
                return "Поворот";
            case "pitch":
                return "Наклон";
            case "sx":
                return "light".equals(o.type) ? "Дальн." : "Ширина";
            case "sy":
                return "Высота";
            case "sz":
                return "Глубина";
            default:
                return k.toUpperCase(Locale.ROOT);
        }
    }

    private void inspector(int mx, int my) {
        int x = width - RIGHT_W;
        UiTheme.rect(x, TOP, RIGHT_W, height - TOP - 14, 0xD0121419);
        EditorState.Obj o = EditorState.selected;
        if (o != fieldsFor) {
            buildFields(o);
        }
        if (o == null) {
            UiTheme.text("§7Инспектор", x + 6, TOP + 6, UiTheme.DIM);
            List<String> help = fontRenderer.listFormattedStringToWidth("§8Щёлкни по объекту в мире или в иерархии. «+ Добавить» —"
                    + " новый объект там, куда смотришь.", RIGHT_W - 12);
            for (int i = 0; i < help.size(); i++) {
                UiTheme.text(help.get(i), x + 6, TOP + 22 + i * 10, UiTheme.MUTED);
            }
            return;
        }
        UiTheme.text("§e" + UiTheme.trim(o.label, RIGHT_W - 12), x + 6, TOP + 6, 0xFFFFE080);
        UiTheme.text("§8" + o.type + " · " + UiTheme.trim(o.id, RIGHT_W - 60), x + 6, TOP + 18, UiTheme.MUTED);
        UiTheme.text("§7Положение", x + 6, TOP + 30, UiTheme.DIM);
        for (int i = 0; i < fields.size(); i++) {
            GuiTextField f = fields.get(i);
            UiTheme.text(fieldTitle(o, fieldKeys.get(i)), x + 6, f.y + 2, UiTheme.TEXT);
            UiTheme.rect(f.x - 3, f.y - 2, f.width + 4, 14, UiTheme.INPUT);
            if (!f.isFocused()) {
                double v = value(o, fieldKeys.get(i));
                f.setText(String.format(Locale.ROOT, "%.2f", v));
            }
            f.drawTextBox();
        }
        int by = fields.isEmpty() ? TOP + 44 : fields.get(fields.size() - 1).y + 22;
        button(x + 6, by, RIGHT_W - 12, "⚙ Все свойства…", mx, my);
        button(x + 6, by + 18, (RIGHT_W - 16) / 2, "⧉ Копия", mx, my);
        button(x + 10 + (RIGHT_W - 16) / 2, by + 18, (RIGHT_W - 16) / 2, "✕ Удалить", mx, my);
        button(x + 6, by + 36, RIGHT_W - 12, "◎ Показать (F)", mx, my);
    }

    private void button(int x, int y, int w, String label, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 15;
        UiTheme.round(x, y, w, 15, hov ? 0xFF2F5BB0 : 0xFF1F2430);
        UiTheme.center(label, x + w / 2, y + 4, UiTheme.TEXT);
    }

    private String inspectorButton(int mx, int my) {
        if (EditorState.selected == null || mx < width - RIGHT_W) {
            return null;
        }
        int x = width - RIGHT_W;
        int by = fields.isEmpty() ? TOP + 44 : fields.get(fields.size() - 1).y + 22;
        if (my >= by && my < by + 15) {
            return "props";
        }
        if (my >= by + 18 && my < by + 33) {
            return mx < x + 6 + (RIGHT_W - 16) / 2 ? "dup" : "delete";
        }
        if (my >= by + 36 && my < by + 51) {
            return "focus";
        }
        return null;
    }

    private static double value(EditorState.Obj o, String k) {
        switch (k) {
            case "x":
                return o.x;
            case "y":
                return o.y;
            case "z":
                return o.z;
            case "yaw":
                return o.yaw;
            case "pitch":
                return o.pitch;
            case "sx":
                return o.sx;
            case "sy":
                return o.sy;
            default:
                return o.sz;
        }
    }

    private void applyField(GuiTextField f, String k) {
        EditorState.Obj o = EditorState.selected;
        if (o == null) {
            return;
        }
        double v;
        try {
            v = Double.parseDouble(f.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return;
        }
        switch (k) {
            case "x":
                o.x = v;
                break;
            case "y":
                o.y = v;
                break;
            case "z":
                o.z = v;
                break;
            case "yaw":
                o.yaw = (float) v;
                break;
            case "pitch":
                o.pitch = (float) MathHelper.clamp(v, -90, 90);
                break;
            case "sx":
                o.sx = Math.max(0.1, v);
                break;
            case "sy":
                o.sy = Math.max(0.1, v);
                break;
            default:
                o.sz = Math.max(0.1, v);
        }
        send("ed_move", EditorState.moveTag(o));
    }

    // --- меню «Добавить» -------------------------------------------------------------------------------------

    private void openMenu(String path, int x, int y) {
        menu = path;
        menuX = x;
        menuY = y;
        menuScroll = 0;
        menuItems.clear();
        switch (path) {
            case "npc":
                for (String[] n : NPCS) {
                    menuItems.add(new String[]{"npc:" + n[0], n[1]});
                }
                break;
            case "marker":
                for (int i = 0; i < 16; i++) {
                    menuItems.add(new String[]{"marker:" + i, MARKERS[i]});
                }
                break;
            case "electric":
                for (int i = 0; i < 16; i++) {
                    menuItems.add(new String[]{"electric:" + i, ELECTRIC[i]});
                }
                break;
            case "cblock":
                for (ContentPack.BlockDef d : CustomContent.active().blocks) {
                    menuItems.add(new String[]{"cblock:" + d.slot, com.ecoserv.util.ChatUtil.strip(com.ecoserv.util.ChatUtil.color(d.name))});
                }
                if (menuItems.isEmpty()) {
                    menuItems.add(new String[]{"", "§8нет своих блоков"});
                }
                break;
            default:
                menuItems.add(new String[]{">npc", "NPC ▸"});
                menuItems.add(new String[]{"holo", "Голограмма"});
                menuItems.add(new String[]{"panel", "3D-панель"});
                menuItems.add(new String[]{"light:point", "Свет: точечный"});
                menuItems.add(new String[]{"light:spot", "Свет: прожектор"});
                menuItems.add(new String[]{"light:surface", "Свет: поверхность"});
                menuItems.add(new String[]{"zone", "Зона разработчика"});
                menuItems.add(new String[]{"logic", "Блок-программа"});
                menuItems.add(new String[]{">marker", "Маркер ▸"});
                menuItems.add(new String[]{">electric", "Электроника ▸"});
                menuItems.add(new String[]{">cblock", "Свой блок ▸"});
        }
    }

    private void drawMenu(int mx, int my) {
        int w = 150;
        int h = Math.min(menuItems.size(), 16) * 12 + 4;
        UiTheme.panel(menuX, menuY, w, h, 0xF0181B22, UiTheme.ACCENT);
        for (int i = 0; i < Math.min(16, menuItems.size() - menuScroll); i++) {
            String[] it = menuItems.get(i + menuScroll);
            int y = menuY + 2 + i * 12;
            boolean hov = mx >= menuX && mx < menuX + w && my >= y && my < y + 12;
            if (hov) {
                UiTheme.rect(menuX + 1, y, w - 2, 12, 0xFF2F5BB0);
            }
            UiTheme.text(it[1], menuX + 5, y + 2, UiTheme.TEXT);
        }
    }

    private boolean clickMenu(int mx, int my) {
        int w = 150;
        int h = Math.min(menuItems.size(), 16) * 12 + 4;
        if (mx < menuX || mx >= menuX + w || my < menuY || my >= menuY + h) {
            menu = null;
            return false;
        }
        int i = (my - menuY - 2) / 12 + menuScroll;
        if (i < 0 || i >= menuItems.size()) {
            return true;
        }
        String id = menuItems.get(i)[0];
        if (id.isEmpty()) {
            return true;
        }
        if (id.startsWith(">")) {
            openMenu(id.substring(1), menuX + 20, menuY + 10);
            return true;
        }
        menu = null;
        addAtView(id);
        return true;
    }

    /** Новый объект — на блоке, куда смотрит камера (или в 5 блоках перед ней). */
    private void addAtView(String kind) {
        Minecraft mc = Minecraft.getMinecraft();
        double yaw = Math.toRadians(CameraFx.eyaw);
        double pitch = Math.toRadians(CameraFx.epitch);
        Vec3d o = new Vec3d(CameraFx.ex, CameraFx.ey, CameraFx.ez);
        Vec3d d = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d at = o.add(d.scale(5));
        if (mc.world != null) {
            RayTraceResult r = mc.world.rayTraceBlocks(o, o.add(d.scale(64)));
            if (r != null && r.typeOfHit == RayTraceResult.Type.BLOCK) {
                at = new Vec3d(r.getBlockPos().offset(r.sideHit)).add(0.5, 0, 0.5);
            }
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setString("kind", kind);
        t.setDouble("x", at.x);
        t.setDouble("y", at.y);
        t.setDouble("z", at.z);
        t.setFloat("yaw", CameraFx.eyaw + 180.0F);
        send("ed_add", t);
    }

    // --- выбор и гизмо -----------------------------------------------------------------------------------------

    private boolean inViewport(int mx, int my) {
        return mx > LEFT_W && mx < width - RIGHT_W && my > TOP && my < height - 14 && menu == null;
    }

    private Vec3d[] mouseRay(int mx, int my) {
        if (!EditorState.haveMatrices) {
            return null;
        }
        return EditorState.ray(mx / (double) width, my / (double) height);
    }

    /** Какая ручка гизмо под мышью: 0–2 оси, 3 — плоскость XZ, 10 — поворот (рыскание), 11 — наклон. */
    private int handleAt(int mx, int my) {
        EditorState.Obj s = EditorState.selected;
        Vec3d[] r = mouseRay(mx, my);
        if (s == null || r == null) {
            return -1;
        }
        Vec3d p = s.pivot();
        double L = EditorRender.gizmoSize(p);
        if (EditorState.mode == 1) {
            if (s.can("yaw")) {
                Vec3d h = EditorState.onPlaneY(r[0], r[1], p.y);
                if (h != null && Math.abs(h.distanceTo(p) - L) < L * 0.12) {
                    return 10;
                }
            }
            if (s.can("pitch")) {
                // кольцо наклона — проверка по расстоянию до центра в плоскости
                double best = Double.MAX_VALUE;
                for (double t = 0; t < 128; t += 0.25) {
                    Vec3d q = r[0].add(r[1].scale(t));
                    double dd = Math.abs(q.distanceTo(p) - L * 0.8);
                    best = Math.min(best, dd);
                }
                if (best < L * 0.06) {
                    return 11;
                }
            }
            return -1;
        }
        double hs = L * 0.16;
        for (int a = 0; a < 3; a++) {
            Vec3d e = p.add(EditorRender.axisDir(a).scale(L));
            AxisAlignedBB bb = new AxisAlignedBB(e.x - hs, e.y - hs, e.z - hs, e.x + hs, e.y + hs, e.z + hs);
            if (bb.calculateIntercept(r[0], r[0].add(r[1].scale(256))) != null) {
                return a;
            }
            // по линии оси
            double t = EditorState.closestOnAxis(p, EditorRender.axisDir(a), r[0], r[1]);
            if (t > 0.15 * L && t < L) {
                Vec3d onAxis = p.add(EditorRender.axisDir(a).scale(t));
                double lt = onAxis.subtract(r[0]).dotProduct(r[1]);
                Vec3d onRay = r[0].add(r[1].scale(lt));
                if (onAxis.distanceTo(onRay) < L * 0.05) {
                    return a;
                }
            }
        }
        if (EditorState.mode == 0) {
            Vec3d h = EditorState.onPlaneY(r[0], r[1], p.y);
            if (h != null && h.x >= p.x && h.x <= p.x + L * 0.22 && h.z >= p.z && h.z <= p.z + L * 0.22) {
                return 3;
            }
        }
        return -1;
    }

    private void updateHover(int mx, int my) {
        EditorRender.hoverHandle = dragHandle >= 0 ? dragHandle : inViewport(mx, my) ? handleAt(mx, my) : -1;
    }

    private EditorState.Obj pick(int mx, int my) {
        Vec3d[] r = mouseRay(mx, my);
        if (r == null) {
            return null;
        }
        Vec3d end = r[0].add(r[1].scale(256));
        EditorState.Obj best = null;
        double bd = Double.MAX_VALUE;
        for (EditorState.Obj o : EditorState.OBJS) {
            RayTraceResult h = o.box().grow(0.05).calculateIntercept(r[0], end);
            if (h != null) {
                double d = h.hitVec.distanceTo(r[0]);
                // зоны большие — уступают мелким объектам внутри
                if ("zone".equals(o.type)) {
                    d += 1000;
                }
                if (d < bd) {
                    bd = d;
                    best = o;
                }
            }
        }
        return best;
    }

    private void startDrag(int handle, int mx, int my) {
        EditorState.Obj s = EditorState.selected;
        Vec3d[] r = mouseRay(mx, my);
        if (s == null || r == null) {
            return;
        }
        dragHandle = handle;
        dragStart = s.copy();
        dragMy = my;
        Vec3d p = s.pivot();
        if (handle >= 0 && handle <= 2) {
            dragParam = EditorState.closestOnAxis(p, EditorRender.axisDir(handle), r[0], r[1]);
        } else if (handle == 3 || handle == 10) {
            dragPoint = EditorState.onPlaneY(r[0], r[1], p.y);
            if (dragPoint != null) {
                dragAngle = Math.toDegrees(Math.atan2(dragPoint.z - p.z, dragPoint.x - p.x));
            }
        }
    }

    private void drag(int mx, int my) {
        EditorState.Obj s = EditorState.selected;
        Vec3d[] r = mouseRay(mx, my);
        if (s == null || r == null || dragStart == null) {
            return;
        }
        EditorState.Obj o0 = dragStart;
        Vec3d p0 = o0.pivot();
        double step = "block".equals(s.type) ? 1.0D : EditorState.step;
        if (dragHandle >= 0 && dragHandle <= 2) {
            double t = EditorState.closestOnAxis(p0, EditorRender.axisDir(dragHandle), r[0], r[1]);
            double delta = t - dragParam;
            if (EditorState.mode == 0) {
                double dd = EditorState.snap(delta, step);
                if ("block".equals(s.type)) {
                    dd = Math.round(delta);
                }
                s.x = o0.x + (dragHandle == 0 ? dd : 0);
                s.y = o0.y + (dragHandle == 1 ? dd : 0);
                s.z = o0.z + (dragHandle == 2 ? dd : 0);
            } else if (EditorState.mode == 2 && s.can("scale")) {
                double dd = EditorState.snap(delta, EditorState.step);
                if ("light".equals(s.type)) {
                    s.sx = s.sy = s.sz = Math.max(1, o0.sx + dd);
                } else {
                    if (dragHandle == 0) {
                        s.sx = Math.max(0.25, o0.sx + dd);
                    } else if (dragHandle == 1) {
                        s.sy = Math.max(0.25, o0.sy + dd);
                    } else {
                        if ("zone".equals(s.type)) {
                            s.sz = Math.max(1, o0.sz + dd);
                        } else {
                            s.sx = Math.max(0.25, o0.sx + dd);
                        }
                    }
                }
            }
        } else if (dragHandle == 3) {
            Vec3d h = EditorState.onPlaneY(r[0], r[1], p0.y);
            if (h != null && dragPoint != null) {
                double dx = EditorState.snap(h.x - dragPoint.x, step);
                double dz = EditorState.snap(h.z - dragPoint.z, step);
                if ("block".equals(s.type)) {
                    dx = Math.round(h.x - dragPoint.x);
                    dz = Math.round(h.z - dragPoint.z);
                }
                s.x = o0.x + dx;
                s.z = o0.z + dz;
            }
        } else if (dragHandle == 10) {
            Vec3d h = EditorState.onPlaneY(r[0], r[1], p0.y);
            if (h != null) {
                double a = Math.toDegrees(Math.atan2(h.z - p0.z, h.x - p0.x));
                double delta = MathHelper.wrapDegrees((float) (a - dragAngle));
                double v = o0.yaw + delta;
                s.yaw = (float) (EditorState.snap ? Math.round(v / 15.0) * 15.0 : v);
            }
        } else if (dragHandle == 11) {
            double v = o0.pitch + (my - dragMy) * 0.6;
            v = MathHelper.clamp(v, -90, 90);
            s.pitch = (float) (EditorState.snap ? Math.round(v / 15.0) * 15.0 : v);
        }
        long now = System.currentTimeMillis();
        if (now - lastSend > 250 && !"block".equals(s.type)) {
            lastSend = now;
            send("ed_move", EditorState.moveTag(s));
        }
    }

    // --- ввод -------------------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (menu != null && clickMenu(mx, my)) {
            return;
        }
        search.mouseClicked(mx, my, button);
        for (GuiTextField f : fields) {
            boolean was = f.isFocused();
            f.mouseClicked(mx, my, button);
            if (was && !f.isFocused()) {
                applyField(f, fieldKeys.get(fields.indexOf(f)));
            }
        }
        String t = toolAt(mx, my);
        if (t != null) {
            tool(t, mx);
            return;
        }
        String ib = inspectorButton(mx, my);
        if (ib != null && button == 0) {
            EditorState.Obj s = EditorState.selected;
            switch (ib) {
                case "props":
                    send("ed_props", tag(s.type, s.id));
                    break;
                case "dup":
                    send("ed_dup", tag(s.type, s.id));
                    break;
                case "delete":
                    send("ed_delete", tag(s.type, s.id));
                    EditorState.selected = null;
                    break;
                default:
                    focus(s);
            }
            return;
        }
        if (mx < LEFT_W && my > TOP + 22 && my < height - 14) {
            int i = (my - (TOP + 24)) / 11 + hierScroll;
            List<EditorState.Obj> list = filtered();
            if (i >= 0 && i < list.size()) {
                EditorState.Obj o = list.get(i);
                if (EditorState.selected == o && button == 0) {
                    focus(o);
                }
                EditorState.selected = o;
            }
            return;
        }
        if (!inViewport(mx, my)) {
            return;
        }
        if (button == 1) {
            looking = true;
            lastMx = mx;
            lastMy = my;
            return;
        }
        if (button == 0) {
            int h = handleAt(mx, my);
            if (h >= 0) {
                startDrag(h, mx, my);
                return;
            }
            EditorState.selected = pick(mx, my);
        }
    }

    private void tool(String t, int mx) {
        switch (t) {
            case "move":
                EditorState.mode = 0;
                break;
            case "rotate":
                EditorState.mode = 1;
                break;
            case "scale":
                EditorState.mode = 2;
                break;
            case "snap":
                cycleSnap();
                break;
            case "add":
                if (menu != null) {
                    menu = null;
                } else {
                    openMenu("", mx, TOP);
                }
                break;
            case "undo":
                send("ed_undo", new NBTTagCompound());
                break;
            case "redo":
                send("ed_redo", new NBTTagCompound());
                break;
            case "exit":
                mc.displayGuiScreen(null);
                break;
            default: {
                NBTTagCompound d = new NBTTagCompound();
                d.setString("tool", t);
                send("ed_tool", d);
            }
        }
    }

    private void cycleSnap() {
        if (!EditorState.snap) {
            EditorState.snap = true;
            EditorState.step = 0.25;
        } else if (EditorState.step < 0.5) {
            EditorState.step = 0.5;
        } else if (EditorState.step < 1) {
            EditorState.step = 1;
        } else {
            EditorState.snap = false;
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        if (looking) {
            CameraFx.eyaw += (mx - lastMx) * 0.6F;
            CameraFx.epitch = MathHelper.clamp(CameraFx.epitch + (my - lastMy) * 0.6F, -90.0F, 90.0F);
            lastMx = mx;
            lastMy = my;
            return;
        }
        if (dragHandle >= 0) {
            drag(mx, my);
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (state == 1) {
            looking = false;
        }
        if (dragHandle >= 0 && state == 0) {
            if (EditorState.selected != null) {
                send("ed_move", EditorState.moveTag(EditorState.selected));
            }
            dragHandle = -1;
            dragStart = null;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        if (menu != null) {
            menuScroll = Math.max(0, Math.min(Math.max(0, menuItems.size() - 16), menuScroll + (wheel > 0 ? -1 : 1)));
            return;
        }
        if (mx < LEFT_W) {
            hierScroll += wheel > 0 ? -3 : 3;
            return;
        }
        double yaw = Math.toRadians(CameraFx.eyaw);
        double pitch = Math.toRadians(CameraFx.epitch);
        double k = (wheel > 0 ? 1 : -1) * (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 6 : 2);
        CameraFx.ex += -Math.sin(yaw) * Math.cos(pitch) * k;
        CameraFx.ey += -Math.sin(pitch) * k;
        CameraFx.ez += Math.cos(yaw) * Math.cos(pitch) * k;
    }

    @Override
    protected void keyTyped(char c, int code) throws IOException {
        if (search.isFocused()) {
            if (code == Keyboard.KEY_ESCAPE || code == Keyboard.KEY_RETURN) {
                search.setFocused(false);
            } else {
                search.textboxKeyTyped(c, code);
                hierScroll = 0;
            }
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            GuiTextField f = fields.get(i);
            if (f.isFocused()) {
                if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER || code == Keyboard.KEY_TAB) {
                    applyField(f, fieldKeys.get(i));
                    f.setFocused(false);
                    if (code == Keyboard.KEY_TAB && i + 1 < fields.size()) {
                        fields.get(i + 1).setFocused(true);
                    }
                } else if (code == Keyboard.KEY_ESCAPE) {
                    f.setFocused(false);
                } else {
                    f.textboxKeyTyped(c, code);
                }
                return;
            }
        }
        if (code == Keyboard.KEY_ESCAPE) {
            if (menu != null) {
                menu = null;
                return;
            }
            mc.displayGuiScreen(null);
            return;
        }
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        EditorState.Obj s = EditorState.selected;
        if (ctrl && code == Keyboard.KEY_Z) {
            send("ed_undo", new NBTTagCompound());
        } else if (ctrl && code == Keyboard.KEY_Y) {
            send("ed_redo", new NBTTagCompound());
        } else if (ctrl && code == Keyboard.KEY_D && s != null) {
            send("ed_dup", tag(s.type, s.id));
        } else if (code == Keyboard.KEY_DELETE && s != null) {
            send("ed_delete", tag(s.type, s.id));
            EditorState.selected = null;
        } else if (code == Keyboard.KEY_F && s != null) {
            focus(s);
        } else if (code == Keyboard.KEY_G) {
            cycleSnap();
        } else if (!looking && code == Keyboard.KEY_W) {
            EditorState.mode = 0;
        } else if (!looking && code == Keyboard.KEY_E) {
            EditorState.mode = 1;
        } else if (!looking && code == Keyboard.KEY_R) {
            EditorState.mode = 2;
        }
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
        for (GuiTextField f : fields) {
            f.updateCursorCounter();
        }
    }
}
