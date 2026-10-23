package com.ecoserv.client.v2;

import com.ecoserv.client.ui.UiTheme;
import com.ecoserv.content.ContentPack;
import com.ecoserv.content.CustomContent;
import com.ecoserv.display.Display;
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
 * Редактор сцены — как в Unity. 2.0 beta4: свободное перемещение (оси, плоскости, центр — в плоскости экрана,
 * или просто тащи выбранный объект), свободный поворот по трём кольцам и шару (как block_display), размер по
 * локальным осям и общий; понятная камера: ПКМ — обзор и полёт, СКМ — сдвиг, Alt+ЛКМ — облёт вокруг объекта,
 * ПКМ+колесо — скорость; камеры сцены с предпросмотром.
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
    private static final String[][] DISPLAY_BLOCKS = {{"minecraft:grass@0", "Трава"}, {"minecraft:stone@0", "Камень"},
            {"minecraft:planks@0", "Доски"}, {"minecraft:glass@0", "Стекло"}, {"minecraft:glowstone@0", "Светокамень"},
            {"minecraft:sea_lantern@0", "Морской фонарь"}, {"minecraft:diamond_block@0", "Алмазный блок"},
            {"minecraft:diamond_sword@0", "Алмазный меч"}, {"minecraft:apple@0", "Яблоко"}};

    private static final int TOP = 20;
    private static final int LEFT_W = 150;
    private static final int RIGHT_W = 180;
    private static final int BOTTOM = 24;

    /** Скорость полёта камеры (сохраняется между открытиями). */
    private static double camSpeed = 8.0D;

    private long lastNanos;
    private boolean looking;
    private boolean panning;
    private boolean orbiting;
    private int lastMx;
    private int lastMy;
    private int dragHandle = -1;
    private EditorState.Obj dragStart;
    private double dragParam;
    private Vec3d dragAxis;
    private Vec3d dragNormal;
    private Vec3d dragPoint;
    private double dragAngle;
    private int dragMx;
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
    /** Кнопки панели инструментов и инспектора этого кадра: x, y, w, h, id. */
    private final List<Object[]> hits = new ArrayList<Object[]>();
    private String status = "";
    private long statusUntil;

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

    private void say(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    // --- камера --------------------------------------------------------------------------------------

    private boolean typing() {
        boolean any = search.isFocused();
        for (GuiTextField f : fields) {
            any |= f.isFocused();
        }
        return any;
    }

    private static Vec3d camForward() {
        double yaw = Math.toRadians(CameraFx.eyaw);
        double pitch = Math.toRadians(CameraFx.epitch);
        return new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    private void flyCamera(float dt) {
        if (typing() || !looking) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        double speed = camSpeed * (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 3 : 1) * dt;
        double yaw = Math.toRadians(CameraFx.eyaw);
        double pitch = Math.toRadians(CameraFx.epitch);
        double fwd = (key(mc.gameSettings.keyBindForward.getKeyCode()) ? 1 : 0) - (key(mc.gameSettings.keyBindBack.getKeyCode()) ? 1 : 0);
        double str = (key(mc.gameSettings.keyBindLeft.getKeyCode()) ? 1 : 0) - (key(mc.gameSettings.keyBindRight.getKeyCode()) ? 1 : 0);
        double up = (Keyboard.isKeyDown(Keyboard.KEY_E) || Keyboard.isKeyDown(Keyboard.KEY_SPACE) ? 1 : 0) - (Keyboard.isKeyDown(Keyboard.KEY_Q) ? 1 : 0);
        CameraFx.ex += (-Math.sin(yaw) * Math.cos(pitch) * fwd + Math.cos(yaw) * str) * speed;
        CameraFx.ez += (Math.cos(yaw) * Math.cos(pitch) * fwd + Math.sin(yaw) * str) * speed;
        CameraFx.ey += (-Math.sin(pitch) * fwd + up) * speed;
    }

    private static double wrap(double a) {
        double r = a % 360.0D;
        if (r >= 180.0D) {
            r -= 360.0D;
        }
        if (r < -180.0D) {
            r += 360.0D;
        }
        return r;
    }

    private static boolean key(int code) {
        return code > 0 && Keyboard.isKeyDown(code);
    }

    private void focus(EditorState.Obj o) {
        Vec3d p = o.pivot();
        double yaw = Math.toRadians(CameraFx.eyaw);
        double pitch = Math.toRadians(CameraFx.epitch);
        double dist = Math.max(4, Math.max(o.sx, o.sy) * 1.8);
        if ("camera".equals(o.type)) {
            dist = 4;
        }
        CameraFx.ex = p.x + Math.sin(yaw) * Math.cos(pitch) * dist;
        CameraFx.ey = p.y + Math.sin(pitch) * dist;
        CameraFx.ez = p.z - Math.cos(yaw) * Math.cos(pitch) * dist;
    }

    /** Точка, вокруг которой облетаем: выбранный объект или 8 блоков перед камерой. */
    private Vec3d orbitCenter() {
        if (EditorState.selected != null) {
            return EditorState.selected.pivot();
        }
        return new Vec3d(CameraFx.ex, CameraFx.ey, CameraFx.ez).add(camForward().scale(8));
    }

    private void orbit(int dx, int dy) {
        Vec3d c = orbitCenter();
        double dist = new Vec3d(CameraFx.ex, CameraFx.ey, CameraFx.ez).distanceTo(c);
        CameraFx.eyaw += dx * 0.6F;
        CameraFx.epitch = MathHelper.clamp(CameraFx.epitch + dy * 0.6F, -89.0F, 89.0F);
        Vec3d f = camForward();
        CameraFx.ex = c.x - f.x * dist;
        CameraFx.ey = c.y - f.y * dist;
        CameraFx.ez = c.z - f.z * dist;
    }

    /** Сдвиг камеры (СКМ): мир «едет» за мышью. */
    private void pan(int dx, int dy) {
        double yaw = Math.toRadians(CameraFx.eyaw);
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d up = right.crossProduct(camForward()).normalize();
        double k = 0.012 * camSpeed;
        CameraFx.ex += (right.x * dx + up.x * dy) * k;
        CameraFx.ey += (right.y * dx + up.y * dy) * k;
        CameraFx.ez += (right.z * dx + up.z * dy) * k;
    }

    // --- рисование --------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        flyCamera(dt);
        updateHover(mx, my);
        hits.clear();
        // верхняя панель
        UiTheme.rect(0, 0, width, TOP, 0xE0101218);
        int x = 4;
        x = tool(x, "✥ Двигать (W)", "move", EditorState.mode == 0, mx, my);
        x = tool(x, "⟳ Вращать (E)", "rotate", EditorState.mode == 1, mx, my);
        x = tool(x, "⤢ Размер (R)", "scale", EditorState.mode == 2, mx, my);
        x += 4;
        x = tool(x, EditorState.snap ? "▦ Сетка " + fmt(EditorState.step) : "▦ Свободно", "snap", EditorState.snap, mx, my);
        x += 4;
        x = tool(x, "+ Добавить", "add", menu != null, mx, my);
        x += 4;
        x = tool(x, "↶ " + EditorState.undo, "undo", false, mx, my);
        x = tool(x, "↷ " + EditorState.redo, "redo", false, mx, my);
        x += 4;
        x = tool(x, "☀ Свет", "lighting", false, mx, my);
        x = tool(x, "🎥 Камеры", "cameras", false, mx, my);
        x = tool(x, "▤ Окна GUI", "gui", false, mx, my);
        x = tool(x, "▣ Контент", "content", false, mx, my);
        x = tool(x, "⛏ Стройка", "build", false, mx, my);
        tool(width - 62, "✕ Выйти", "exit", false, mx, my);
        // иерархия
        UiTheme.rect(0, TOP, LEFT_W, height - TOP - BOTTOM, 0xD0121419);
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
        int visible = (height - BOTTOM - listTop) / rowH;
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
        inspector(mx, my);
        viewWidget();
        // подсказка внизу — два ряда, по делу
        UiTheme.rect(0, height - BOTTOM, width, BOTTOM, 0xE0101218);
        String line1 = "§fКамера: §7ПКМ — смотреть, ПКМ+WASD — лететь (Q/E — вниз/вверх, Shift — быстрее), ПКМ+колесо — скорость §f×"
                + fmt(camSpeed) + "§7, СКМ — сдвиг, Alt+ЛКМ — облёт, колесо — ближе/дальше, F — к объекту";
        String line2 = dragHint();
        UiTheme.text(UiTheme.trim(line1, width - 8), 4, height - BOTTOM + 3, UiTheme.DIM);
        UiTheme.text(UiTheme.trim(line2, width - 8), 4, height - BOTTOM + 13, UiTheme.DIM);
        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            int w = fontRenderer.getStringWidth(status) + 12;
            UiTheme.round((width - w) / 2, TOP + 8, w, 14, 0xE02F5BB0);
            UiTheme.center(status, width / 2, TOP + 11, 0xFFFFFFFF);
        }
        if (menu != null) {
            drawMenu(mx, my);
        }
        super.drawScreen(mx, my, partialTicks);
    }

    private String dragHint() {
        EditorState.Obj s = EditorState.selected;
        if (s == null) {
            return "§fОбъекты: §7щёлкни по объекту · «+ Добавить» — новый там, куда смотришь · Del — удалить · Ctrl+D — копия · Ctrl+Z/Y — отмена/повтор";
        }
        switch (EditorState.mode) {
            case 1:
                return s.can("rot3") ? "§fВращение: §7тяни кольцо §cX§7/§aY§7/§9Z§7 — вокруг оси, внутри белого круга — свободно во все стороны."
                        + " Ctrl — шагами по 15°"
                        : "§fВращение: §7тяни кольцо (§aзелёное§7 — поворот, §cкрасное§7 — наклон, §9синее§7 — крен). Ctrl — по 15°";
            case 2:
                return "§fРазмер: §7тяни кубик на оси — по одной оси, белый в центре — всё сразу";
            default:
                return "§fПеремещение: §7тяни сам объект или белый центр — свободно, стрелки — по оси, квадратики — в плоскости."
                        + " Ctrl — по сетке (G — сетка всегда)";
        }
    }

    /** Мини-компас в углу: куда смотрят оси мира. */
    private void viewWidget() {
        if (!EditorState.haveMatrices) {
            return;
        }
        int cx = width - RIGHT_W - 26;
        int cy = TOP + 26;
        UiTheme.round(cx - 20, cy - 20, 40, 40, 0x90101218);
        String[] names = {"X", "Y", "Z"};
        int[] colors = {0xFFFF5050, 0xFF50FF50, 0xFF5080FF};
        for (int a = 0; a < 3; a++) {
            Vec3d d = EditorRender.axisDir(a);
            double sx = EditorState.M[0] * d.x + EditorState.M[4] * d.y + EditorState.M[8] * d.z;
            double sy = EditorState.M[1] * d.x + EditorState.M[5] * d.y + EditorState.M[9] * d.z;
            int ex = cx + (int) Math.round(sx * 14);
            int ey = cy - (int) Math.round(sy * 14);
            lineGui(cx, cy, ex, ey, colors[a]);
            UiTheme.text(names[a], ex - 2, ey - 4, colors[a]);
        }
    }

    private static void lineGui(int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (steps == 0 ? 0 : (x2 - x1) * i / steps);
            int y = y1 + (steps == 0 ? 0 : (y2 - y1) * i / steps);
            UiTheme.rect(x, y, 1, 1, color);
        }
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? Integer.toString((int) d) : String.format(Locale.ROOT, "%.2f", d).replaceAll("0+$", "");
    }

    private int tool(int x, String label, String id, boolean on, int mx, int my) {
        int w = fontRenderer.getStringWidth(label) + 10;
        boolean hov = mx >= x && mx < x + w && my < TOP;
        UiTheme.round(x, 3, w, 14, on ? 0xFF2F5BB0 : hov ? 0xFF2A2F3A : 0xFF1C2028);
        UiTheme.text(label, x + 5, 6, on ? 0xFFFFFFFF : UiTheme.TEXT);
        hits.add(new Object[]{x, 3, w, 14, "tool:" + id});
        return x + w + 2;
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
        int x0 = width - RIGHT_W + 56;
        int y = TOP + 44;
        for (String k : new String[]{"x", "y", "z"}) {
            addField(k, x0, y, value(o, k));
            y += 15;
        }
        y += 14;
        if (o.can("rot3")) {
            for (String k : new String[]{"eyaw", "epitch", "eroll"}) {
                addField(k, x0, y, value(o, k));
                y += 15;
            }
        } else {
            if (o.can("yaw")) {
                addField("yaw", x0, y, o.yaw);
                y += 15;
            }
            if (o.can("pitch")) {
                addField("pitch", x0, y, o.pitch);
                y += 15;
            }
            if (o.can("roll")) {
                addField("roll", x0, y, o.roll);
                y += 15;
            }
        }
        if ("camera".equals(o.type)) {
            addField("sx", x0, y, o.sx);
            y += 15;
        }
        if (o.can("scale") || o.can("scale3")) {
            y += 14;
            addField("sx", x0, y, o.sx);
            y += 15;
            if (!"light".equals(o.type)) {
                addField("sy", x0, y, o.sy);
                y += 15;
            }
            if ("zone".equals(o.type) || o.can("scale3")) {
                addField("sz", x0, y, o.sz);
            }
        }
    }

    private void addField(String key, int x, int y, double v) {
        GuiTextField f = new GuiTextField(100 + fields.size(), fontRenderer, x, y, RIGHT_W - 64, 12);
        f.setMaxStringLength(16);
        f.setText(String.format(Locale.ROOT, "%.2f", v));
        fields.add(f);
        fieldKeys.add(key);
    }

    private static String fieldTitle(EditorState.Obj o, String k) {
        switch (k) {
            case "yaw":
            case "eyaw":
                return "Поворот Y";
            case "pitch":
            case "epitch":
                return "Наклон X";
            case "roll":
            case "eroll":
                return "Крен Z";
            case "sx":
                return "camera".equals(o.type) ? "FOV°" : "light".equals(o.type) ? "Дальность" : o.can("scale3") ? "Размер X" : "Ширина";
            case "sy":
                return o.can("scale3") ? "Размер Y" : "Высота";
            case "sz":
                return o.can("scale3") ? "Размер Z" : "Глубина";
            default:
                return k.toUpperCase(Locale.ROOT);
        }
    }

    private void inspector(int mx, int my) {
        int x = width - RIGHT_W;
        UiTheme.rect(x, TOP, RIGHT_W, height - TOP - BOTTOM, 0xD0121419);
        EditorState.Obj o = EditorState.selected;
        if (o != fieldsFor) {
            buildFields(o);
        }
        if (o == null) {
            UiTheme.text("§7Инспектор", x + 6, TOP + 6, UiTheme.DIM);
            List<String> help = fontRenderer.listFormattedStringToWidth("§8Щёлкни по объекту в мире или в списке слева. «+ Добавить» —"
                    + " новый объект там, куда смотришь (блок-дисплей, камера, свет, NPC…).", RIGHT_W - 12);
            for (int i = 0; i < help.size(); i++) {
                UiTheme.text(help.get(i), x + 6, TOP + 22 + i * 10, UiTheme.MUTED);
            }
            return;
        }
        UiTheme.text("§e" + UiTheme.trim(o.label, RIGHT_W - 12), x + 6, TOP + 6, 0xFFFFE080);
        UiTheme.text("§8" + typeTitle(o.type) + " · " + UiTheme.trim(o.id, RIGHT_W - 70), x + 6, TOP + 17, UiTheme.MUTED);
        UiTheme.text("§7Положение", x + 6, TOP + 32, UiTheme.DIM);
        String lastGroup = "pos";
        for (int i = 0; i < fields.size(); i++) {
            GuiTextField f = fields.get(i);
            String k = fieldKeys.get(i);
            String group = k.length() == 1 ? "pos" : k.startsWith("s") ? "size" : "rot";
            if ("camera".equals(o.type) && "sx".equals(k)) {
                group = "rot";
            }
            if (!group.equals(lastGroup)) {
                UiTheme.text(group.equals("rot") ? "§7Поворот" + (o.can("rot3") ? " §8(свободный)" : "") : "§7Размер", x + 6, f.y - 12, UiTheme.DIM);
                lastGroup = group;
            }
            UiTheme.text(fieldTitle(o, k), x + 6, f.y + 2, UiTheme.TEXT);
            UiTheme.rect(f.x - 3, f.y - 2, f.width + 4, 14, UiTheme.INPUT);
            if (!f.isFocused()) {
                f.setText(String.format(Locale.ROOT, "%.2f", value(o, k)));
            }
            f.drawTextBox();
        }
        int by = fields.isEmpty() ? TOP + 44 : fields.get(fields.size() - 1).y + 20;
        int half = (RIGHT_W - 16) / 2;
        if ("camera".equals(o.type)) {
            button(x + 6, by, RIGHT_W - 12, "🎥 Вид из камеры", "cam_preview", mx, my);
            button(x + 6, by + 17, RIGHT_W - 12, "📷 Камера = мой вид", "cam_set", mx, my);
            button(x + 6, by + 34, RIGHT_W - 12, "👁 Посмотреть как игрок", "cam_view", mx, my);
            by += 54;
        }
        if (o.can("rot3")) {
            button(x + 6, by, half, "Сброс поворота", "rot_reset", mx, my);
            button(x + 10 + half, by, half, "Размер 1", "size_reset", mx, my);
            by += 17;
        }
        button(x + 6, by, RIGHT_W - 12, "⚙ Все свойства…", "props", mx, my);
        button(x + 6, by + 17, half, "⧉ Копия", "dup", mx, my);
        button(x + 10 + half, by + 17, half, "✕ Удалить", "delete", mx, my);
        button(x + 6, by + 34, RIGHT_W - 12, "◎ Показать (F)", "focus", mx, my);
    }

    private static String typeTitle(String t) {
        switch (t) {
            case "display":
                return "блок-дисплей";
            case "camera":
                return "камера";
            case "light":
                return "свет";
            case "panel":
                return "3D-панель";
            case "holo":
                return "голограмма";
            case "zone":
                return "зона";
            case "block":
                return "блок";
            case "campoint":
                return "точка катсцены";
            default:
                return t;
        }
    }

    private void button(int x, int y, int w, String label, String id, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 15;
        UiTheme.round(x, y, w, 15, hov ? 0xFF2F5BB0 : 0xFF1F2430);
        UiTheme.center(label, x + w / 2, y + 4, UiTheme.TEXT);
        hits.add(new Object[]{x, y, w, 15, "btn:" + id});
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
            case "roll":
                return o.roll;
            case "eyaw":
                return Display.toEuler(o.qx, o.qy, o.qz, o.qw)[0];
            case "epitch":
                return Display.toEuler(o.qx, o.qy, o.qz, o.qw)[1];
            case "eroll":
                return Display.toEuler(o.qx, o.qy, o.qz, o.qw)[2];
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
            case "roll":
                o.roll = (float) v;
                break;
            case "eyaw":
            case "epitch":
            case "eroll": {
                double[] e = Display.toEuler(o.qx, o.qy, o.qz, o.qw);
                if ("eyaw".equals(k)) {
                    e[0] = v;
                } else if ("epitch".equals(k)) {
                    e[1] = v;
                } else {
                    e[2] = v;
                }
                double[] q = Display.fromEuler(e[0], e[1], e[2]);
                o.qx = q[0];
                o.qy = q[1];
                o.qz = q[2];
                o.qw = q[3];
                break;
            }
            case "sx":
                o.sx = Math.max("camera".equals(o.type) ? 10 : 0.05, v);
                break;
            case "sy":
                o.sy = Math.max(0.05, v);
                break;
            default:
                o.sz = Math.max(0.05, v);
        }
        send("ed_move", EditorState.moveTag(o));
    }

    // --- меню «Добавить» -------------------------------------------------------------------------------------

    private void openMenu(String path, int x, int y) {
        menu = path;
        menuX = Math.min(x, width - 160);
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
            case "display":
                menuItems.add(new String[]{"display:hand", "§eИз руки"});
                for (String[] b : DISPLAY_BLOCKS) {
                    menuItems.add(new String[]{"display:" + b[0], b[1]});
                }
                for (ContentPack.BlockDef d : CustomContent.active().blocks) {
                    menuItems.add(new String[]{"display:cblock:" + d.slot, "§b" + com.ecoserv.util.ChatUtil.strip(com.ecoserv.util.ChatUtil.color(d.name))});
                }
                for (ContentPack.ItemDef d : CustomContent.active().items) {
                    menuItems.add(new String[]{"display:citem:" + d.slot, "§d" + com.ecoserv.util.ChatUtil.strip(com.ecoserv.util.ChatUtil.color(d.name))});
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
                menuItems.add(new String[]{">display", "▣ Блок-дисплей ▸"});
                menuItems.add(new String[]{"camera", "🎥 Камера"});
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
            UiTheme.text(UiTheme.trim(it[1], w - 10), menuX + 5, y + 2, UiTheme.TEXT);
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
        Vec3d o = new Vec3d(CameraFx.ex, CameraFx.ey, CameraFx.ez);
        Vec3d d = camForward();
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
        say("Добавлено — выбери его в списке слева");
    }

    // --- выбор и гизмо -----------------------------------------------------------------------------------------

    private boolean inViewport(int mx, int my) {
        return mx > LEFT_W && mx < width - RIGHT_W && my > TOP && my < height - BOTTOM && menu == null;
    }

    private Vec3d[] mouseRay(int mx, int my) {
        if (!EditorState.haveMatrices) {
            return null;
        }
        return EditorState.ray(mx / (double) width, my / (double) height);
    }

    private static double rayPointDist(Vec3d[] r, Vec3d p) {
        Vec3d w = p.subtract(r[0]);
        double t = Math.max(0, w.dotProduct(r[1]));
        return r[0].add(r[1].scale(t)).distanceTo(p);
    }

    /** Насколько луч близок к окружности (центр p, ось n, радиус R). */
    private static double ringDist(Vec3d[] r, Vec3d p, Vec3d n, double R) {
        Vec3d nn = n.normalize();
        double tc = Math.max(0, p.subtract(r[0]).dotProduct(r[1]));
        double best = Double.MAX_VALUE;
        for (int i = -60; i <= 60; i++) {
            double t = tc + i * R / 30.0;
            if (t < 0) {
                continue;
            }
            Vec3d q = r[0].add(r[1].scale(t)).subtract(p);
            double along = q.dotProduct(nn);
            Vec3d perp = q.subtract(nn.scale(along));
            double d = Math.sqrt(Math.pow(Math.sqrt(perp.x * perp.x + perp.y * perp.y + perp.z * perp.z) - R, 2) + along * along);
            best = Math.min(best, d);
        }
        return best;
    }

    /** Угол точки под мышью вокруг оси n (градусы). */
    private static double ringAngle(Vec3d[] r, Vec3d p, Vec3d n) {
        Vec3d nn = n.normalize();
        Vec3d h = EditorState.onPlane(r[0], r[1], p, nn);
        if (h == null) {
            double t = Math.max(0, p.subtract(r[0]).dotProduct(r[1]));
            h = r[0].add(r[1].scale(t));
        }
        Vec3d v = h.subtract(p);
        Vec3d u = Math.abs(nn.y) < 0.9 ? nn.crossProduct(new Vec3d(0, 1, 0)).normalize() : nn.crossProduct(new Vec3d(1, 0, 0)).normalize();
        Vec3d w = nn.crossProduct(u).normalize();
        return Math.toDegrees(Math.atan2(v.dotProduct(w), v.dotProduct(u)));
    }

    private static Vec3d axisFor(EditorState.Obj s, int a) {
        return EditorState.mode == 2 && s.can("rot3") ? s.rotate(EditorRender.axisDir(a)).normalize() : EditorRender.axisDir(a);
    }

    private static Vec3d planeNormal(int handle) {
        return handle == 3 ? new Vec3d(0, 1, 0) : handle == 4 ? new Vec3d(0, 0, 1) : new Vec3d(1, 0, 0);
    }

    /**
     * Ручка под мышью: 0–2 оси; 3 — плоскость XZ, 4 — XY, 5 — YZ, 6 — центр (свободно); 7 — общий размер;
     * 20–22 — кольца поворота X/Y/Z, 23 — свободное вращение.
     */
    private int handleAt(int mx, int my) {
        EditorState.Obj s = EditorState.selected;
        Vec3d[] r = mouseRay(mx, my);
        if (s == null || r == null) {
            return -1;
        }
        Vec3d p = s.pivot();
        double L = EditorRender.gizmoSize(p);
        if (EditorState.mode == 1) {
            Vec3d[] axes = EditorRender.rotationAxes(s);
            int best = -1;
            double bestD = L * 0.1;
            for (int i = 0; i < 3; i++) {
                if (axes[i] == null) {
                    continue;
                }
                double d = ringDist(r, p, axes[i], L);
                if (d < bestD) {
                    bestD = d;
                    best = 20 + i;
                }
            }
            if (best >= 0) {
                return best;
            }
            if (s.can("rot3") && rayPointDist(r, p) < L * 1.1) {
                return 23;
            }
            return -1;
        }
        if (EditorState.mode == 0 && rayPointDist(r, p) < L * 0.1) {
            return 6;
        }
        if (EditorState.mode == 2 && rayPointDist(r, p) < L * 0.13) {
            return 7;
        }
        if (EditorState.mode == 2 && !s.can("scale") && !s.can("scale3")) {
            return -1;
        }
        double hs = L * 0.14;
        for (int a = 0; a < 3; a++) {
            Vec3d dir = axisFor(s, a);
            Vec3d e = p.add(dir.scale(L));
            AxisAlignedBB bb = new AxisAlignedBB(e.x - hs, e.y - hs, e.z - hs, e.x + hs, e.y + hs, e.z + hs);
            if (bb.calculateIntercept(r[0], r[0].add(r[1].scale(512))) != null) {
                return a;
            }
            double t = EditorState.closestOnAxis(p, dir, r[0], r[1]);
            if (t > 0.15 * L && t < L) {
                Vec3d onAxis = p.add(dir.scale(t));
                double lt = onAxis.subtract(r[0]).dotProduct(r[1]);
                Vec3d onRay = r[0].add(r[1].scale(lt));
                if (onAxis.distanceTo(onRay) < L * 0.05) {
                    return a;
                }
            }
        }
        if (EditorState.mode == 0) {
            double[] rect = EditorRender.planeRect(L);
            for (int hnd = 3; hnd <= 5; hnd++) {
                Vec3d h = EditorState.onPlane(r[0], r[1], p, planeNormal(hnd));
                if (h == null) {
                    continue;
                }
                Vec3d v = h.subtract(p);
                double a1 = hnd == 5 ? v.y : v.x;
                double a2 = hnd == 3 ? v.z : hnd == 4 ? v.y : v.z;
                if (a1 >= rect[0] && a1 <= rect[1] && a2 >= rect[0] && a2 <= rect[1]) {
                    return hnd;
                }
            }
        }
        return -1;
    }

    private void updateHover(int mx, int my) {
        EditorRender.hoverHandle = dragHandle >= 0 ? dragHandle : inViewport(mx, my) && !looking && !panning && !orbiting ? handleAt(mx, my) : -1;
    }

    private EditorState.Obj pick(int mx, int my) {
        Vec3d[] r = mouseRay(mx, my);
        if (r == null) {
            return null;
        }
        Vec3d end = r[0].add(r[1].scale(512));
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
        dragMx = mx;
        dragMy = my;
        Vec3d p = s.pivot();
        if (handle >= 0 && handle <= 2) {
            dragAxis = axisFor(s, handle);
            dragParam = EditorState.closestOnAxis(p, dragAxis, r[0], r[1]);
        } else if (handle >= 3 && handle <= 6) {
            dragNormal = handle == 6 ? EditorState.viewBasis()[2] : planeNormal(handle);
            dragPoint = EditorState.onPlane(r[0], r[1], p, dragNormal);
            if (dragPoint == null) {
                dragHandle = -1;
            }
        } else if (handle >= 20 && handle <= 22) {
            dragAxis = EditorRender.rotationAxes(s)[handle - 20];
            dragAngle = ringAngle(r, p, dragAxis);
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
        boolean block = "block".equals(s.type);
        double step = block ? 1.0D : EditorState.step;
        if (dragHandle >= 0 && dragHandle <= 2) {
            double t = EditorState.closestOnAxis(p0, dragAxis, r[0], r[1]);
            double delta = t - dragParam;
            if (EditorState.mode == 0) {
                double dd = block ? Math.round(delta) : EditorState.snap(delta, step);
                s.x = o0.x + dragAxis.x * dd;
                s.y = o0.y + dragAxis.y * dd;
                s.z = o0.z + dragAxis.z * dd;
            } else if (EditorState.mode == 2) {
                double dd = EditorState.snap(delta, EditorState.step);
                if ("light".equals(s.type)) {
                    s.sx = s.sy = s.sz = Math.max(1, o0.sx + dd);
                } else if (s.can("scale3")) {
                    // в обе стороны от центра
                    if (dragHandle == 0) {
                        s.sx = Math.max(0.05, o0.sx + dd * 2);
                    } else if (dragHandle == 1) {
                        s.sy = Math.max(0.05, o0.sy + dd * 2);
                    } else {
                        s.sz = Math.max(0.05, o0.sz + dd * 2);
                    }
                } else if (dragHandle == 0) {
                    s.sx = Math.max(0.25, o0.sx + dd);
                } else if (dragHandle == 1) {
                    s.sy = Math.max(0.25, o0.sy + dd);
                } else if ("zone".equals(s.type)) {
                    s.sz = Math.max(1, o0.sz + dd);
                } else {
                    s.sx = Math.max(0.25, o0.sx + dd);
                }
            }
        } else if (dragHandle >= 3 && dragHandle <= 6) {
            Vec3d h = EditorState.onPlane(r[0], r[1], p0, dragNormal);
            if (h != null && dragPoint != null) {
                Vec3d d = h.subtract(dragPoint);
                double dx = block ? Math.round(d.x) : EditorState.snap(d.x, step);
                double dy = block ? Math.round(d.y) : EditorState.snap(d.y, step);
                double dz = block ? Math.round(d.z) : EditorState.snap(d.z, step);
                s.x = o0.x + dx;
                s.y = o0.y + dy;
                s.z = o0.z + dz;
            }
        } else if (dragHandle == 7) {
            double f = Math.exp((dragMy - my) * 0.012);
            if ("light".equals(s.type)) {
                s.sx = s.sy = s.sz = Math.max(1, Math.min(60, o0.sx * f));
            } else if (s.can("scale3")) {
                s.sx = Math.max(0.05, o0.sx * f);
                s.sy = Math.max(0.05, o0.sy * f);
                s.sz = Math.max(0.05, o0.sz * f);
            } else if (s.can("scale")) {
                s.sx = Math.max(0.25, o0.sx * f);
                s.sy = Math.max(0.25, o0.sy * f);
                if ("zone".equals(s.type)) {
                    s.sz = Math.max(1, o0.sz * f);
                }
            }
        } else if (dragHandle >= 20 && dragHandle <= 22) {
            double a = ringAngle(r, p0, dragAxis);
            double delta = wrap(a - dragAngle);
            if (EditorState.snapping()) {
                delta = Math.round(delta / 15.0) * 15.0;
            }
            if (s.can("rot3")) {
                double[] q = EditorState.mulQ(EditorState.axisQ(dragAxis, Math.toRadians(delta)), new double[]{o0.qx, o0.qy, o0.qz, o0.qw});
                s.qx = q[0];
                s.qy = q[1];
                s.qz = q[2];
                s.qw = q[3];
            } else if (dragHandle == 21) {
                s.yaw = (float) wrap(o0.yaw - delta);
            } else if (dragHandle == 20) {
                s.pitch = (float) MathHelper.clamp(o0.pitch - delta, -90, 90);
            } else {
                s.roll = (float) wrap(o0.roll + delta);
            }
        } else if (dragHandle == 23) {
            Vec3d[] vb = EditorState.viewBasis();
            double ax = (mx - dragMx) * 0.8;
            double ay = (my - dragMy) * 0.8;
            if (EditorState.snapping()) {
                ax = Math.round(ax / 15.0) * 15.0;
                ay = Math.round(ay / 15.0) * 15.0;
            }
            double[] q = EditorState.mulQ(EditorState.axisQ(vb[1], Math.toRadians(ax)),
                    EditorState.mulQ(EditorState.axisQ(vb[0], Math.toRadians(ay)), new double[]{o0.qx, o0.qy, o0.qz, o0.qw}));
            s.qx = q[0];
            s.qy = q[1];
            s.qz = q[2];
            s.qw = q[3];
        }
        long now = System.currentTimeMillis();
        // дисплеи двигаются у клиента сразу; на сервер — не чаще 4 раз в секунду
        if (now - lastSend > 250 && !block) {
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
        String hit = hitAt(mx, my);
        if (hit != null && button == 0) {
            if (hit.startsWith("tool:")) {
                tool(hit.substring(5), mx);
            } else {
                inspectorButton(hit.substring(4));
            }
            return;
        }
        if (mx < LEFT_W && my > TOP + 22 && my < height - BOTTOM) {
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
        lastMx = mx;
        lastMy = my;
        if (button == 1) {
            looking = true;
            return;
        }
        if (button == 2) {
            panning = true;
            return;
        }
        if (button == 0) {
            if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
                orbiting = true;
                return;
            }
            int h = handleAt(mx, my);
            if (h >= 0) {
                startDrag(h, mx, my);
                return;
            }
            EditorState.Obj picked = pick(mx, my);
            if (picked != null && picked == EditorState.selected && EditorState.mode == 0 && picked.can("move")) {
                // тащим сам объект — свободно в плоскости экрана
                startDrag(6, mx, my);
                return;
            }
            EditorState.selected = picked;
        }
    }

    private void inspectorButton(String id) {
        EditorState.Obj s = EditorState.selected;
        if (s == null) {
            return;
        }
        switch (id) {
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
            case "rot_reset":
                s.qx = s.qy = s.qz = 0;
                s.qw = 1;
                send("ed_move", EditorState.moveTag(s));
                break;
            case "size_reset":
                s.sx = s.sy = s.sz = 1;
                send("ed_move", EditorState.moveTag(s));
                break;
            case "cam_preview":
                CameraFx.ex = s.x;
                CameraFx.ey = s.y;
                CameraFx.ez = s.z;
                CameraFx.eyaw = s.yaw;
                CameraFx.epitch = s.pitch;
                say("Это вид из камеры «" + s.label + "»");
                break;
            case "cam_set":
                s.x = CameraFx.ex;
                s.y = CameraFx.ey;
                s.z = CameraFx.ez;
                s.yaw = CameraFx.eyaw;
                s.pitch = CameraFx.epitch;
                send("ed_move", EditorState.moveTag(s));
                say("Камера смотрит так же, как ты сейчас");
                break;
            case "cam_view":
                send("ed_camview", tag(s.type, s.id));
                mc.displayGuiScreen(null);
                break;
            default:
                focus(s);
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
        say(EditorState.snap ? "Сетка " + fmt(EditorState.step) + " (Ctrl — свободно)" : "Свободно (Ctrl — по сетке)");
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        int dx = mx - lastMx;
        int dy = my - lastMy;
        lastMx = mx;
        lastMy = my;
        if (looking) {
            CameraFx.eyaw += dx * 0.6F;
            CameraFx.epitch = MathHelper.clamp(CameraFx.epitch + dy * 0.6F, -90.0F, 90.0F);
            return;
        }
        if (panning) {
            pan(-dx, dy);
            return;
        }
        if (orbiting) {
            orbit(dx, dy);
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
        if (state == 2) {
            panning = false;
        }
        if (state == 0) {
            orbiting = false;
            if (dragHandle >= 0) {
                if (EditorState.selected != null) {
                    send("ed_move", EditorState.moveTag(EditorState.selected));
                }
                dragHandle = -1;
                dragStart = null;
            }
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
        if (looking) {
            camSpeed = MathHelper.clamp(camSpeed * (wheel > 0 ? 1.25 : 0.8), 0.5, 96);
            say("Скорость камеры ×" + fmt(Math.round(camSpeed * 10) / 10.0));
            return;
        }
        Vec3d f = camForward();
        double k = (wheel > 0 ? 1 : -1) * Math.max(0.5, camSpeed * 0.25) * (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 3 : 1);
        CameraFx.ex += f.x * k;
        CameraFx.ey += f.y * k;
        CameraFx.ez += f.z * k;
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
