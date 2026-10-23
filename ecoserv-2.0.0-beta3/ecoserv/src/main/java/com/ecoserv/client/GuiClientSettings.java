package com.ecoserv.client;

import com.ecoserv.client.ui.AnimSprite;
import com.ecoserv.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Клиентские настройки (1.9.0) — окно с шестерёнкой: тема, эффекты (вспышки, качество чёрных дыр, искажения),
 * звуки, кто может присылать запросы. Всё хранится только на компьютере игрока.
 */
@SideOnly(Side.CLIENT)
public class GuiClientSettings extends GuiScreen {

    private interface Getter {
        String get();
    }

    private interface Change {
        /** dir: +1 — ЛКМ/вперёд, -1 — ПКМ/назад. */
        void change(int dir);
    }

    private static final class Row {
        final String label;
        final String desc;
        final Getter value;
        final Change change;
        final boolean toggle;
        final Getter on;

        Row(String label, String desc, Getter value, Change change, boolean toggle, @Nullable Getter on) {
            this.label = label;
            this.desc = desc;
            this.value = value;
            this.change = change;
            this.toggle = toggle;
            this.on = on;
        }
    }

    private static final String[] CATEGORIES = {"Вид", "Эффекты", "Звук", "Запросы"};
    private static int category;

    @Nullable
    private final GuiScreen parent;
    private final List<Row> rows = new ArrayList<Row>();
    private int left;
    private int top;
    private int w;
    private int h;
    private int scroll;
    private boolean dirty;

    public GuiClientSettings(@Nullable GuiScreen parent) {
        this.parent = parent;
    }

    /** 1.9.1: окно можно таскать за заголовок; позиция помнится до перезапуска игры. */
    private static int savedLeft = Integer.MIN_VALUE;
    private static int savedTop = Integer.MIN_VALUE;
    private boolean dragging;
    private int dragDX;
    private int dragDY;

    @Override
    public void initGui() {
        w = Math.min(380, width - 20);
        h = Math.min(260, height - 20);
        left = savedLeft == Integer.MIN_VALUE ? (width - w) / 2 : savedLeft;
        top = savedTop == Integer.MIN_VALUE ? (height - h) / 2 : savedTop;
        clampWindow();
        rebuild();
    }

    private void clampWindow() {
        left = Math.max(0, Math.min(left, width - w));
        top = Math.max(0, Math.min(top, height - h));
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (dragging) {
            left = mouseX - dragDX;
            top = mouseY - dragDY;
            clampWindow();
            savedLeft = left;
            savedTop = top;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
    }

    private void rebuild() {
        rows.clear();
        final ClientSettings s = ClientSettings.get();
        switch (category) {
            case 0:
                rows.add(new Row("Тема окон", "Цвета всех окон мода", () -> themeTitle(s.theme), dir -> {
                    s.theme = cycle(ClientSettings.THEMES, s.theme, dir);
                    com.ecoserv.client.ui.UiTheme.apply(s.theme);
                }, false, null));
                rows.add(new Row("Язык мода", "Меню, сообщения и подсказки мода. «Как в игре» — по языку Minecraft",
                        () -> langTitle(s.modLanguage), dir -> s.modLanguage = cycle(LANGS, s.modLanguage, dir), false, null));
                rows.add(toggle("Показывать, в каком меню ты", "Строка «Магазин › Аукцион › …» над окном",
                        () -> s.breadcrumbs, v -> s.breadcrumbs = v));
                rows.add(toggle("Скрывать слоты инвентаря в меню", "В меню-сундуках мода не показывать свой инвентарь",
                        () -> s.hideInventorySlots, v -> s.hideInventorySlots = v));
                rows.add(toggle("Анимированные иконки", "Выключи, если отвлекают", () -> s.animatedIcons, v -> s.animatedIcons = v));
                break;
            case 1:
                rows.add(toggle("Яркие вспышки", "Выключи, если вспышки неприятны: вспышки и свечение приглушаются",
                        () -> s.flashes, v -> s.flashes = v));
                rows.add(new Row("Качество чёрных дыр", "Авто подбирает по видеокарте и FPS. Максимальное — луч "
                        + "считается прямо в шейдере (точно по Шварцшильду)",
                        () -> ClientSettings.qualityTitle(s.holeQuality) + (s.holeQuality < 0
                                ? " §7(сейчас: " + ClientSettings.qualityTitle(ClientSettings.quality()) + ")" : ""),
                        dir -> s.holeQuality = wrap(s.holeQuality + 1 + dir, 5) - 1, false, null));
                rows.add(new Row("Сила искажений экрана", "Гиперпрыжок, сжатие, раздутие, линзы",
                        () -> s.distortion + "%", dir -> s.distortion = clamp(s.distortion + dir * 10, 0, 200), false, null));
                rows.add(toggle("Искажать интерфейс", "Хотбар и чат искажаются вместе с миром у порталов",
                        () -> s.distortHud, v -> s.distortHud = v));
                rows.add(toggle("Существа в окнах порталов", "Игроки и мобы в месте назначения", () -> s.portalEntities,
                        v -> s.portalEntities = v));
                rows.add(toggle("Глитч на весь экран", "При падении в ГЛИТЧ-бездну", () -> s.screenGlitch, v -> s.screenGlitch = v));
                rows.add(toggle("Динамический свет (GoodLight)", "Факел в руке, светящиеся предметы и горящие существа освещают всё вокруг",
                        () -> s.dynamicLight, v -> s.dynamicLight = v));
                rows.add(new Row("Радиус динамического света", "Больше — красивее, но тяжелее для FPS",
                        () -> s.dynamicLightRadius + "", dir -> s.dynamicLightRadius = clamp(s.dynamicLightRadius + dir, 3, 10), false, null));
                rows.add(toggle("Свет от других", "Факелы у других игроков и выброшенные светящиеся предметы",
                        () -> s.dynamicLightOthers, v -> s.dynamicLightOthers = v));
                rows.add(toggle("Тряска экрана", "Эффекты сервера (взрывы, катсцены)", () -> s.screenShake, v -> s.screenShake = v));
                break;
            case 2:
                rows.add(new Row("Громкость звуков мода", "Порталы, мини-игры, меню", () -> s.soundVolume + "%",
                        dir -> s.soundVolume = clamp(s.soundVolume + dir * 10, 0, 200), false, null));
                rows.add(toggle("Щелчки в меню", "Звук нажатия кнопок", () -> s.uiSounds, v -> s.uiSounds = v));
                break;
            default:
                rows.add(new Row("Обмен", "Кто может предлагать тебе обмен", () -> requestTitle(s.tradeRequests),
                        dir -> s.tradeRequests = cycle(ClientSettings.REQUESTS, s.tradeRequests, dir), false, null));
                rows.add(new Row("Денежные запросы", "Кто может просить у тебя денег", () -> requestTitle(s.moneyRequests),
                        dir -> s.moneyRequests = cycle(ClientSettings.REQUESTS, s.moneyRequests, dir), false, null));
                rows.add(new Row("Приглашения", "В команды и миры", () -> requestTitle(s.inviteRequests),
                        dir -> s.inviteRequests = cycle(ClientSettings.REQUESTS, s.inviteRequests, dir), false, null));
                rows.add(toggle("Запросы компьютеров", "Покупки и оплата через ComputerCraft", () -> s.computerRequests,
                        v -> s.computerRequests = v));
                rows.add(toggle("Всплывающие уведомления", "Сообщения мода в углу экрана", () -> s.toasts, v -> s.toasts = v));
        }
    }

    private interface BoolGet {
        boolean get();
    }

    private interface BoolSet {
        void set(boolean v);
    }

    private static Row toggle(String label, String desc, final BoolGet get, final BoolSet set) {
        return new Row(label, desc, () -> get.get() ? "вкл" : "выкл", dir -> set.set(!get.get()), true,
                () -> get.get() ? "1" : "");
    }

    private static String themeTitle(String t) {
        switch (t) {
            case "midnight":
                return "Полночь";
            case "amethyst":
                return "Аметист";
            case "forest":
                return "Лес";
            case "classic":
                return "Классика";
            default:
                return "Тёмная";
        }
    }

    private static final String[] LANGS = {"auto", "ru_ru", "en_us", "uk_ua", "de_de", "es_es", "fr_fr", "pl_pl", "pt_br", "zh_cn"};

    private static String langTitle(String l) {
        switch (l == null ? "auto" : l) {
            case "ru_ru":
                return "Русский";
            case "en_us":
                return "English";
            case "uk_ua":
                return "Українська";
            case "de_de":
                return "Deutsch";
            case "es_es":
                return "Español";
            case "fr_fr":
                return "Français";
            case "pl_pl":
                return "Polski";
            case "pt_br":
                return "Português";
            case "zh_cn":
                return "中文";
            default:
                return "Как в игре";
        }
    }

    private static String requestTitle(String t) {
        return "team".equals(t) ? "Только команда" : "none".equals(t) ? "Никто" : "Все";
    }

    private static String cycle(String[] values, String now, int dir) {
        int i = 0;
        for (int k = 0; k < values.length; k++) {
            if (values[k].equals(now)) {
                i = k;
            }
        }
        return values[wrap(i + dir, values.length)];
    }

    private static int wrap(int v, int n) {
        return ((v % n) + n) % n;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // --- отрисовка ------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        UiTheme.panel(left, top, w, h, UiTheme.WINDOW, UiTheme.BORDER);
        UiTheme.rect(left + 1, top + 1, w - 2, 20, UiTheme.TITLE);
        AnimSprite.GEAR.draw(left + 6, top + 3, true);
        UiTheme.text("§l" + ClientSettings.t("Клиентские настройки"), left + 26, top + 7, UiTheme.TEXT);
        UiTheme.text("§8" + ClientSettings.t("только на твоём компьютере"), left + w - 6 - UiTheme.width(ClientSettings.t("только на твоём компьютере")), top + 7, UiTheme.MUTED);
        // категории
        int side = 86;
        UiTheme.rect(left + 1, top + 21, side, h - 22, UiTheme.SIDEBAR);
        for (int i = 0; i < CATEGORIES.length; i++) {
            int y = top + 26 + i * 20;
            boolean hover = mouseX >= left + 4 && mouseX < left + side - 2 && mouseY >= y && mouseY < y + 18;
            if (i == category) {
                UiTheme.round(left + 4, y, side - 6, 18, UiTheme.ROW_HL);
                UiTheme.rect(left + 4, y + 3, 2, 12, UiTheme.ACCENT);
            } else if (hover) {
                UiTheme.round(left + 4, y, side - 6, 18, UiTheme.ROW_HOVER);
            }
            UiTheme.text(ClientSettings.t(CATEGORIES[i]), left + 12, y + 5, i == category ? UiTheme.TEXT : UiTheme.DIM);
        }
        // строки
        int x0 = left + side + 6;
        int rw = w - side - 12;
        int y0 = top + 26;
        String tip = null;
        UiTheme.scissor(x0, y0, rw, h - 56);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = y0 + i * 30 - scroll;
            if (y + 28 < y0 || y > top + h - 30) {
                continue;
            }
            boolean hover = mouseX >= x0 && mouseX < x0 + rw && mouseY >= y && mouseY < y + 28 && mouseY < top + h - 30;
            UiTheme.round(x0, y, rw, 28, hover ? UiTheme.ROW_HOVER : UiTheme.ROW);
            UiTheme.text(ClientSettings.t(r.label), x0 + 6, y + 5, UiTheme.TEXT);
            UiTheme.text(UiTheme.trim(ClientSettings.t(r.desc), rw - 100), x0 + 6, y + 16, UiTheme.MUTED);
            if (r.toggle) {
                UiTheme.toggle(x0 + rw - 32, y + 8, r.on != null && !r.on.get().isEmpty(), hover, false);
            } else {
                String v = ClientSettings.t(r.value.get());
                int vw = Math.max(54, UiTheme.width(v) + 16);
                UiTheme.button(x0 + rw - vw - 4, y + 7, vw, 14, v, 1, hover, false);
            }
            if (hover) {
                tip = ClientSettings.t(r.desc);
            }
        }
        UiTheme.endScissor();
        // низ
        int by = top + h - 24;
        boolean overDone = mouseX >= left + w - 86 && mouseX < left + w - 6 && mouseY >= by && mouseY < by + 18;
        boolean overReset = mouseX >= left + side + 6 && mouseX < left + side + 106 && mouseY >= by && mouseY < by + 18;
        UiTheme.button(left + w - 86, by, 80, 18, ClientSettings.t("Готово"), 2, overDone, false);
        UiTheme.button(left + side + 6, by, 100, 18, ClientSettings.t("По умолчанию"), 0, overReset, false);
        UiTheme.text("§8" + ClientSettings.t("ЛКМ — дальше, ПКМ — назад"), left + side + 112, by + 5, UiTheme.MUTED);
        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        int side = 86;
        if (mouseX >= left && mouseX < left + w && mouseY >= top && mouseY < top + 21) {
            if (button == 1) {
                // ПКМ по заголовку — вернуть окно на место
                savedLeft = savedTop = Integer.MIN_VALUE;
                initGui();
                return;
            }
            dragging = true;
            dragDX = mouseX - left;
            dragDY = mouseY - top;
            return;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            int y = top + 26 + i * 20;
            if (mouseX >= left + 4 && mouseX < left + side - 2 && mouseY >= y && mouseY < y + 18) {
                category = i;
                scroll = 0;
                click();
                rebuild();
                return;
            }
        }
        int x0 = left + side + 6;
        int rw = w - side - 12;
        int y0 = top + 26;
        for (int i = 0; i < rows.size(); i++) {
            int y = y0 + i * 30 - scroll;
            if (mouseX >= x0 && mouseX < x0 + rw && mouseY >= y && mouseY < y + 28 && mouseY < top + h - 30 && mouseY >= y0) {
                rows.get(i).change.change(button == 1 ? -1 : 1);
                dirty = true;
                click();
                return;
            }
        }
        int by = top + h - 24;
        if (mouseY >= by && mouseY < by + 18) {
            if (mouseX >= left + w - 86 && mouseX < left + w - 6) {
                click();
                close();
                return;
            }
            if (mouseX >= left + side + 6 && mouseX < left + side + 106) {
                ClientSettings fresh = new ClientSettings();
                copyDefaults(fresh);
                dirty = true;
                click();
                rebuild();
            }
        }
    }

    /** Сбросить текущую категорию к значениям по умолчанию. */
    private static void copyDefaults(ClientSettings d) {
        ClientSettings s = ClientSettings.get();
        switch (category) {
            case 0:
                s.theme = d.theme;
                s.modLanguage = d.modLanguage;
                s.breadcrumbs = d.breadcrumbs;
                s.hideInventorySlots = d.hideInventorySlots;
                s.animatedIcons = d.animatedIcons;
                com.ecoserv.client.ui.UiTheme.apply(s.theme);
                break;
            case 1:
                s.flashes = d.flashes;
                s.holeQuality = d.holeQuality;
                s.distortion = d.distortion;
                s.distortHud = d.distortHud;
                s.portalEntities = d.portalEntities;
                s.screenGlitch = d.screenGlitch;
                break;
            case 2:
                s.soundVolume = d.soundVolume;
                s.uiSounds = d.uiSounds;
                break;
            default:
                s.tradeRequests = d.tradeRequests;
                s.moneyRequests = d.moneyRequests;
                s.inviteRequests = d.inviteRequests;
                s.computerRequests = d.computerRequests;
                s.toasts = d.toasts;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int max = Math.max(0, rows.size() * 30 - (h - 56));
            scroll = clamp(scroll - Integer.signum(wheel) * 20, 0, max);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
        }
    }

    private void close() {
        if (dirty) {
            ClientSettings.get().save();
            dirty = false;
        }
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        if (dirty) {
            ClientSettings.get().save();
            dirty = false;
        }
    }

    private static void click() {
        if (ClientSettings.get().uiSounds) {
            Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
