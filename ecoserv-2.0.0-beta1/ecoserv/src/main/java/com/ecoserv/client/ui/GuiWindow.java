package com.ecoserv.client.ui;

import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketUiAction;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Окно мода в стиле FTB Quests: заголовок, вкладки слева, прокручиваемый список строк, переключатели,
 * поля ввода, выпадающие списки, кнопки, сетка предметов. Что показывать, решает сервер (UiScreen),
 * окно только рисует и отправляет действия обратно.
 */
@SideOnly(Side.CLIENT)
public class GuiWindow extends GuiScreen {

    static final int TITLE_H = 22;
    static final int GAP = 3;

    private int window;
    private int revision;
    private String title = "";
    private String subtitle = "";
    private String status = "";
    @Nullable
    private String searchHint;
    private ItemStack icon = ItemStack.EMPTY;
    private int backId = -1;
    private boolean compact;
    private final List<Tab> tabs = new ArrayList<Tab>();
    private final List<SideEntry> sides = new ArrayList<SideEntry>();
    private final List<Btn> foot = new ArrayList<Btn>();
    private final List<W> body = new ArrayList<W>();
    private final List<W> visible = new ArrayList<W>();

    int left;
    int top;
    int winW;
    int winH;
    private int sideW;
    int cx;
    int cy;
    int cw;
    int ch;
    private int footY;
    private int scroll;
    private int contentHeight;
    private boolean draggingScroll;
    private int sideScroll;
    private int sideContent;

    private GuiTextField search;
    private String filter = "";
    @Nullable
    WField focused;
    private String toast;
    private boolean toastErr;
    private long toastUntil;
    @Nullable
    Overlay overlay;
    @Nullable
    private List<String> tooltip;
    private boolean closedByServer;
    private boolean initialized;

    public GuiWindow(NBTTagCompound model) {
        localize(model);
        read(model, false);
    }

    // --- переводы: «{t:ключ}» в текстах окна переводятся на язык игрока -------------------------------

    private static final String TR = "{t:";

    static String tr(String s) {
        if (s == null || s.indexOf(TR) < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int a = s.indexOf(TR, i);
            if (a < 0) {
                out.append(s.substring(i));
                break;
            }
            int b = s.indexOf('}', a);
            if (b < 0) {
                out.append(s.substring(i));
                break;
            }
            out.append(s, i, a);
            out.append(net.minecraft.client.resources.I18n.format(s.substring(a + TR.length(), b)));
            i = b + 1;
        }
        return out.toString();
    }

    static void localize(NBTTagCompound c) {
        for (String key : new ArrayList<String>(c.getKeySet())) {
            if ("icon".equals(key) || "item".equals(key)) {
                continue;
            }
            net.minecraft.nbt.NBTBase tag = c.getTag(key);
            if (tag instanceof net.minecraft.nbt.NBTTagString) {
                String v = ((net.minecraft.nbt.NBTTagString) tag).getString();
                if (v.indexOf(TR) >= 0) {
                    c.setString(key, tr(v));
                }
            } else if (tag instanceof NBTTagCompound) {
                localize((NBTTagCompound) tag);
            } else if (tag instanceof NBTTagList) {
                NBTTagList list = (NBTTagList) tag;
                if (list.getTagType() == 8) {
                    for (int i = 0; i < list.tagCount(); i++) {
                        String v = list.getStringTagAt(i);
                        if (v.indexOf(TR) >= 0) {
                            list.set(i, new net.minecraft.nbt.NBTTagString(tr(v)));
                        }
                    }
                } else if (list.getTagType() == 10) {
                    for (int i = 0; i < list.tagCount(); i++) {
                        localize(list.getCompoundTagAt(i));
                    }
                }
            }
        }
    }

    public int windowId() {
        return window;
    }

    // --- модель --------------------------------------------------------------------------------

    /** Новая версия того же окна: сохраняем прокрутку, фокус и набранный текст. */
    public void update(NBTTagCompound model) {
        localize(model);
        int keepScroll = scroll;
        int focusId = focused == null ? -1 : focused.id;
        String focusText = focused == null || focused.tf == null ? null : focused.tf.getText();
        int focusCursor = focused == null || focused.tf == null ? 0 : focused.tf.getCursorPosition();
        boolean typing = focused != null && focused.tf != null && !focused.tf.getText().equals(focused.val);
        PickerOverlay picker = overlay instanceof PickerOverlay ? (PickerOverlay) overlay : null;
        read(model, true);
        layout();
        scroll = Math.min(keepScroll, Math.max(0, contentHeight - ch));
        focused = null;
        if (focusId >= 0) {
            for (W w : body) {
                if (w instanceof WField && w.id == focusId && w.tf != null) {
                    focused = (WField) w;
                    if (typing && focusText != null) {
                        w.tf.setText(focusText);
                    }
                    w.tf.setFocused(true);
                    w.tf.setCursorPosition(Math.min(focusCursor, w.tf.getText().length()));
                }
            }
        }
        Overlay keep = overlay;
        overlay = null;
        if (picker != null) {
            for (W w : body) {
                if (w instanceof WField && w.id == picker.field.id) {
                    picker.field = (WField) w;
                    overlay = picker;
                }
            }
        } else if (keep instanceof DropdownOverlay) {
            // открытый список не закрываем при обновлении окна, если такой выбор ещё есть
            DropdownOverlay dd = (DropdownOverlay) keep;
            for (W w : body) {
                if (w instanceof WSelect && w.id == dd.selectId()) {
                    overlay = new DropdownOverlay(this, (WSelect) w, dd);
                }
            }
        } else if (keep instanceof ConfirmOverlay) {
            overlay = keep;
        }
    }

    /** Переход в другое окно мода: то же место на экране, новое содержимое с начала списка. */
    public void navigate(NBTTagCompound model) {
        localize(model);
        sideScroll = 0;
        if (focused != null && focused.tf != null) {
            focused.tf.setFocused(false);
        }
        focused = null;
        overlay = null;
        tooltip = null;
        draggingScroll = false;
        if (search != null) {
            search.setText("");
            search.setFocused(false);
        }
        filter = "";
        read(model, false);
        scroll = 0;
        if (initialized) {
            layout();
        }
    }

    /** 1.9.0: «хлебные крошки» — через какие окна игрок пришёл сюда. */
    private static final List<String> TRAIL = new ArrayList<String>();
    private static long trailSeen;

    private static void trail(String title) {
        String key = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(title);
        if (key == null || key.isEmpty()) {
            return;
        }
        if (System.currentTimeMillis() - trailSeen > 1500L) {
            TRAIL.clear();
        }
        trailSeen = System.currentTimeMillis();
        int at = TRAIL.indexOf(key);
        if (at >= 0) {
            while (TRAIL.size() > at + 1) {
                TRAIL.remove(TRAIL.size() - 1);
            }
        } else {
            TRAIL.add(key);
            while (TRAIL.size() > 6) {
                TRAIL.remove(0);
            }
        }
    }

    private void drawTrail() {
        trailSeen = System.currentTimeMillis();
        if (!com.ecoserv.client.ClientSettings.get().breadcrumbs || TRAIL.isEmpty() || top < 12) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TRAIL.size(); i++) {
            if (i > 0) {
                sb.append(" §8›§7 ");
            }
            sb.append(i == TRAIL.size() - 1 ? "§f" : "§7").append(TRAIL.get(i));
        }
        for (Tab t : tabs) {
            if (t.sel) {
                sb.append(" §8›§b ").append(net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(t.label));
            }
        }
        UiTheme.shadow(UiTheme.trim(sb.toString(), winW), left + 2, top - 11, 0xFFFFFFFF);
    }

    private void read(NBTTagCompound m, boolean isUpdate) {
        window = m.getInteger("win");
        revision = m.getInteger("rev");
        title = m.getString("title");
        if (!isUpdate) {
            trail(title);
        }
        subtitle = m.getString("sub");
        status = m.getString("status");
        searchHint = m.hasKey("search") ? m.getString("search") : null;
        icon = m.hasKey("icon", 10) ? new ItemStack(m.getCompoundTag("icon")) : ItemStack.EMPTY;
        backId = m.hasKey("back") ? id(m.getString("back")) : -1;
        compact = m.getBoolean("compact");
        tabs.clear();
        NBTTagList tl = m.getTagList("tabs", 10);
        for (int i = 0; i < tl.tagCount(); i++) {
            tabs.add(new Tab(tl.getCompoundTagAt(i)));
        }
        sides.clear();
        NBTTagList sl = m.getTagList("side", 10);
        for (int i = 0; i < sl.tagCount(); i++) {
            sides.add(new SideEntry(sl.getCompoundTagAt(i)));
        }
        foot.clear();
        NBTTagList fl = m.getTagList("foot", 10);
        for (int i = 0; i < fl.tagCount(); i++) {
            foot.add(new Btn(fl.getCompoundTagAt(i)));
        }
        body.clear();
        NBTTagList bl = m.getTagList("body", 10);
        for (int i = 0; i < bl.tagCount(); i++) {
            W w = W.of(bl.getCompoundTagAt(i));
            if (w != null) {
                body.add(w);
            }
        }
        if (m.hasKey("toast")) {
            toast = m.getString("toast");
            toastErr = m.getBoolean("toastErr");
            toastUntil = System.currentTimeMillis() + (toastErr ? 6000L : 3500L);
        }
    }

    static int id(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static List<String> strings(NBTTagList list) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < list.tagCount(); i++) {
            out.add(list.getStringTagAt(i));
        }
        return out;
    }

    // --- действия -------------------------------------------------------------------------------

    void send(int id, int button, boolean shift, String value) {
        if (id < 0) {
            return;
        }
        PacketHandler.sendToServer(new PacketUiAction(window, revision, id, button, shift, value));
    }

    void click() {
        if (com.ecoserv.client.ClientSettings.get().uiSounds) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(com.ecoserv.init.ModSounds.UI_CLICK, 1.0F));
        }
    }

    /** Нажатие с возможным подтверждением. */
    void press(final int id, @Nullable String confirm, final int button, final boolean shift) {
        if (id < 0) {
            return;
        }
        click();
        if (confirm != null && !confirm.isEmpty()) {
            overlay = new ConfirmOverlay(confirm, () -> send(id, button, shift, ""));
            return;
        }
        send(id, button, shift, "");
    }

    void focus(@Nullable WField field) {
        if (focused == field) {
            return;
        }
        if (focused != null) {
            focused.apply(this);
            if (focused.tf != null) {
                focused.tf.setFocused(false);
            }
        }
        focused = field;
        if (field != null && field.tf != null) {
            field.tf.setFocused(true);
        }
        if (search != null && field != null) {
            search.setFocused(false);
        }
    }

    void setTooltip(@Nullable List<String> lines) {
        if (lines != null && !lines.isEmpty()) {
            tooltip = lines;
        }
    }

    public void closeByServer() {
        closedByServer = true;
        mc.displayGuiScreen(null);
    }

    // --- раскладка -------------------------------------------------------------------------------

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        layout();
        initialized = true;
    }

    private void layout() {
        winW = Math.min(width - 16, 440);
        winH = Math.min(height - 12, 286);
        left = (width - winW) / 2;
        top = (height - winH) / 2;
        boolean hasSide = !compact && (!tabs.isEmpty() || !sides.isEmpty());
        sideW = hasSide ? Math.min(118, winW / 3) : 0;
        int footH = foot.isEmpty() && status.isEmpty() ? 6 : 24;
        footY = top + winH - footH;
        cx = left + sideW + 8;
        cw = left + winW - 14 - cx;
        int toolbar = searchHint != null ? 18 : 0;
        cy = top + TITLE_H + 6 + toolbar;
        ch = footY - 4 - cy;
        if (searchHint != null) {
            String text = search == null ? "" : search.getText();
            boolean wasFocused = search != null && search.isFocused();
            search = new GuiTextField(9000, fontRenderer, cx + 14, top + TITLE_H + 6, cw - 16, 12);
            search.setEnableBackgroundDrawing(false);
            search.setMaxStringLength(64);
            search.setText(text);
            search.setFocused(wasFocused);
        } else {
            search = null;
            filter = "";
        }
        relayoutBody();
    }

    void relayoutBody() {
        visible.clear();
        String f = filter.toLowerCase(Locale.ROOT);
        for (W w : body) {
            if (f.isEmpty() || (w.searchable() && w.searchText().contains(f))) {
                visible.add(w);
            }
        }
        int y = 0;
        for (W w : visible) {
            w.x = cx;
            w.w = cw;
            w.h = w.measure(this, cw);
            w.relY = y;
            y += w.h + GAP;
        }
        contentHeight = Math.max(0, y - GAP);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - ch)));
    }

    // --- отрисовка --------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!initialized) {
            return;
        }
        tooltip = null;
        drawRect(0, 0, width, height, 0x90000000);
        boolean overlayOpen = overlay != null;
        int mx = overlayOpen ? -9999 : mouseX;
        int my = overlayOpen ? -9999 : mouseY;

        UiTheme.rect(left - 1, top - 1, winW + 2, winH + 2, 0xFF07080A);
        UiTheme.panel(left, top, winW, winH, UiTheme.WINDOW, UiTheme.BORDER);
        drawTitle(mx, my);
        if (sideW > 0) {
            drawSide(mx, my);
        }
        if (search != null) {
            UiTheme.panel(cx, top + TITLE_H + 4, cw, 14, UiTheme.INPUT, search.isFocused() ? UiTheme.ACCENT : UiTheme.INPUT_BORDER);
            UiTheme.text("⌕", cx + 4, top + TITLE_H + 7, UiTheme.MUTED);
            if (search.getText().isEmpty() && !search.isFocused()) {
                UiTheme.text(searchHint, cx + 14, top + TITLE_H + 7, UiTheme.MUTED);
            }
            search.x = cx + 14;
            search.y = top + TITLE_H + 7;
            search.drawTextBox();
        }
        drawBody(mx, my);
        drawFooter(mx, my);
        drawToast();
        if (overlay != null) {
            UiTheme.rect(left, top, winW, winH, 0x80000000);
            overlay.draw(this, mouseX, mouseY, partialTicks);
        }
        if (tooltip != null) {
            drawTip(tooltip, mouseX, mouseY);
        }
        UiTheme.resetColor();
    }

    private void drawTitle(int mx, int my) {
        drawTrail();
        UiTheme.rect(left + 1, top + 1, winW - 2, TITLE_H - 1, UiTheme.TITLE);
        UiTheme.rect(left + 1, top + TITLE_H, winW - 2, 1, UiTheme.BORDER);
        int x = left + 6;
        if (backId >= 0) {
            boolean hover = inside(mx, my, x - 2, top + 3, 16, 16);
            UiTheme.round(x - 2, top + 3, 16, 16, hover ? UiTheme.ROW_HOVER : UiTheme.TITLE);
            UiTheme.center("←", x + 6, top + 7, hover ? UiTheme.TEXT : UiTheme.DIM);
            if (hover) {
                setTooltip(listOf("Назад"));
            }
            x += 18;
        }
        if (!icon.isEmpty()) {
            UiTheme.item(icon, x, top + 3);
            x += 20;
        }
        int closeX = left + winW - 18;
        int room = closeX - x - 6;
        String t = UiTheme.trim(title, room);
        UiTheme.shadow(t, x, top + 7, UiTheme.TEXT);
        int sx = x + UiTheme.width(t) + 8;
        if (!subtitle.isEmpty() && sx < closeX - 20) {
            UiTheme.text(UiTheme.trim(subtitle, closeX - sx - 4), sx, top + 7, UiTheme.MUTED);
        }
        boolean hover = inside(mx, my, closeX - 2, top + 3, 16, 16);
        UiTheme.round(closeX - 2, top + 3, 16, 16, hover ? 0xFFC9403A : UiTheme.TITLE);
        UiTheme.center("✕", closeX + 6, top + 7, hover ? 0xFFFFFFFF : UiTheme.DIM);
    }

    /** Видимая часть боковой панели. */
    private boolean inSide(int mx, int my) {
        return sideW > 0 && inside(mx, my, left + 1, top + TITLE_H + 1, sideW - 1, winH - TITLE_H - 2);
    }

    private int sideAreaH() {
        return winH - TITLE_H - 2;
    }

    private void drawSide(int mouseX, int mouseY) {
        int x = left + 1;
        int y = top + TITLE_H + 1;
        int areaH = sideAreaH();
        UiTheme.rect(x, y, sideW - 1, areaH, UiTheme.SIDEBAR);
        UiTheme.rect(x + sideW - 1, y, 1, areaH, UiTheme.BORDER);
        int content = 8 + tabs.size() * 22 + (sides.isEmpty() ? 0 : 10);
        for (SideEntry e : sides) {
            content += e.caption ? 13 : 20;
        }
        sideContent = content;
        sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, content - areaH)));
        boolean in = inSide(mouseX, mouseY);
        int mx = in ? mouseX : -9999;
        int my = in ? mouseY : -9999;
        UiTheme.scissor(x, y, sideW - 1, areaH);
        int ty = y + 4 - sideScroll;
        for (Tab tab : tabs) {
            tab.x = x + 3;
            tab.y = ty;
            tab.w = sideW - 7;
            tab.h = 20;
            boolean hover = inside(mx, my, tab.x, tab.y, tab.w, tab.h);
            if (tab.sel) {
                UiTheme.round(tab.x, tab.y, tab.w, tab.h, 0xFF1F2533);
                UiTheme.rect(tab.x, tab.y + 3, 2, tab.h - 6, UiTheme.ACCENT);
            } else if (hover) {
                UiTheme.round(tab.x, tab.y, tab.w, tab.h, 0xFF1A1E27);
            }
            int lx = tab.x + 6;
            if (!tab.icon.isEmpty()) {
                UiTheme.item(tab.icon, lx, tab.y + 2);
                lx += 20;
            }
            UiTheme.text(UiTheme.trim(tab.label, tab.x + tab.w - lx - 2), lx, tab.y + 6,
                    tab.sel ? 0xFFFFFFFF : hover ? UiTheme.TEXT : UiTheme.DIM);
            ty += 22;
        }
        if (!sides.isEmpty()) {
            ty += 4;
            UiTheme.rect(x + 8, ty, sideW - 17, 1, UiTheme.BORDER);
            ty += 6;
        }
        for (SideEntry s : sides) {
            s.x = x + 3;
            s.y = ty;
            s.w = sideW - 7;
            if (s.caption) {
                s.h = 12;
                UiTheme.text(UiTheme.trim(s.label, s.w - 6), s.x + 4, ty + 2, UiTheme.MUTED);
                ty += 13;
                continue;
            }
            s.h = 18;
            boolean hover = inside(mx, my, s.x, s.y, s.w, s.h);
            if (hover && !s.tip.isEmpty()) {
                setTooltip(s.tip);
            }
            int base = UiTheme.kind(s.color);
            UiTheme.round(s.x, s.y, s.w, s.h, hover ? UiTheme.alpha(base, 0xC0) : UiTheme.alpha(base, 0x55));
            int lx = s.x + 4;
            if (!s.icon.isEmpty()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(lx, s.y + 1, 0);
                GlStateManager.scale(0.9F, 0.9F, 1.0F);
                UiTheme.item(s.icon, 0, 0);
                GlStateManager.popMatrix();
                lx += 17;
            }
            UiTheme.text(UiTheme.trim(s.label, s.x + s.w - lx - 2), lx, s.y + 5, 0xFFFFFFFF);
            ty += 20;
        }
        UiTheme.endScissor();
        if (sideContent > areaH) {
            int thumbH = Math.max(12, areaH * areaH / Math.max(1, sideContent));
            int thumbY = y + (areaH - thumbH) * sideScroll / Math.max(1, sideContent - areaH);
            UiTheme.rect(x + sideW - 4, thumbY, 2, thumbH, 0xFF4A5060);
        }
    }

    private void drawBody(int mx, int my) {
        if (visible.isEmpty()) {
            UiTheme.center(filter.isEmpty() ? "Пусто" : "Ничего не найдено", cx + cw / 2, cy + 20, UiTheme.MUTED);
        }
        UiTheme.scissor(cx - 1, cy, cw + 2, ch);
        boolean mouseIn = inside(mx, my, cx, cy, cw, ch);
        for (W w : visible) {
            w.y = cy + w.relY - scroll;
            if (w.y + w.h < cy || w.y > cy + ch) {
                continue;
            }
            w.draw(this, mouseIn ? mx : -9999, mouseIn ? my : -9999);
        }
        UiTheme.endScissor();
        if (contentHeight > ch) {
            int trackX = cx + cw + 3;
            UiTheme.rect(trackX, cy, 4, ch, 0xFF101217);
            int thumbH = Math.max(16, ch * ch / Math.max(1, contentHeight));
            int maxScroll = Math.max(1, contentHeight - ch);
            int thumbY = cy + (ch - thumbH) * scroll / maxScroll;
            boolean hover = inside(mx, my, trackX - 1, cy, 6, ch) || draggingScroll;
            UiTheme.round(trackX, thumbY, 4, thumbH, hover ? 0xFF6A7183 : 0xFF3F4452);
        }
    }

    private void drawFooter(int mx, int my) {
        if (foot.isEmpty() && status.isEmpty()) {
            return;
        }
        UiTheme.rect(left + sideW + 1, footY, winW - sideW - 2, 1, UiTheme.BORDER);
        int x = left + winW - 8;
        for (int i = foot.size() - 1; i >= 0; i--) {
            Btn b = foot.get(i);
            b.w = Math.max(50, UiTheme.width(b.label) + 16);
            b.h = 16;
            x -= b.w;
            b.x = x;
            b.y = footY + 4;
            b.draw(this, mx, my);
            x -= 4;
        }
        if (!status.isEmpty()) {
            UiTheme.text(UiTheme.trim(status, x - (left + sideW + 8) - 4), left + sideW + 8, footY + 8, UiTheme.MUTED);
        }
    }

    private void drawToast() {
        if (toast == null || toast.isEmpty()) {
            return;
        }
        long left = toastUntil - System.currentTimeMillis();
        if (left <= 0) {
            toast = null;
            return;
        }
        int alpha = (int) Math.min(255, left / 2);
        List<String> lines = fontRenderer.listFormattedStringToWidth(toast, cw - 12);
        int h = lines.size() * 10 + 6;
        int y = cy + 2;
        int bg = toastErr ? 0xC9403A : 0x2E9E62;
        UiTheme.round(cx, y, cw, h, UiTheme.alpha(bg, Math.min(235, alpha)));
        for (int i = 0; i < lines.size(); i++) {
            UiTheme.text(lines.get(i), cx + 6, y + 4 + i * 10, UiTheme.alpha(0xFFFFFF, Math.max(40, alpha)));
        }
    }

    void drawTip(List<String> lines, int mx, int my) {
        List<String> wrapped = new ArrayList<String>();
        for (String l : lines) {
            wrapped.addAll(fontRenderer.listFormattedStringToWidth(l, 220));
        }
        int w = 0;
        for (String l : wrapped) {
            w = Math.max(w, UiTheme.width(l));
        }
        int h = wrapped.size() * 10 + 6;
        int x = mx + 12;
        int y = my - 4;
        if (x + w + 8 > width) {
            x = Math.max(2, mx - w - 16);
        }
        if (y + h > height) {
            y = Math.max(2, height - h - 2);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 400);
        UiTheme.panel(x - 1, y - 1, w + 10, h + 2, 0xF20B0C10, 0xFF3A4253);
        for (int i = 0; i < wrapped.size(); i++) {
            UiTheme.text(wrapped.get(i), x + 4, y + 3 + i * 10, i == 0 ? UiTheme.TEXT : UiTheme.DIM);
        }
        GlStateManager.popMatrix();
    }

    // --- ввод ---------------------------------------------------------------------------------------

    static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    static List<String> listOf(String... lines) {
        List<String> out = new ArrayList<String>();
        for (String l : lines) {
            out.add(l);
        }
        return out;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (overlay != null) {
            if (!overlay.click(this, mouseX, mouseY, button)) {
                overlay = null;
            }
            return;
        }
        boolean shift = isShiftKeyDown();
        // заголовок
        if (backId >= 0 && inside(mouseX, mouseY, left + 4, top + 3, 16, 16)) {
            press(backId, null, 0, false);
            return;
        }
        if (inside(mouseX, mouseY, left + winW - 20, top + 3, 16, 16)) {
            click();
            mc.displayGuiScreen(null);
            return;
        }
        // поиск
        if (search != null) {
            boolean in = inside(mouseX, mouseY, cx, top + TITLE_H + 4, cw, 14);
            if (in) {
                focus(null);
            }
            search.setFocused(in);
            if (in) {
                if (button == 1) {
                    search.setText("");
                    filter = "";
                    relayoutBody();
                }
                return;
            }
        }
        // боковая панель
        boolean sideClick = inSide(mouseX, mouseY);
        for (Tab t : tabs) {
            if (sideClick && inside(mouseX, mouseY, t.x, t.y, t.w, t.h)) {
                focus(null);
                if (!t.sel) {
                    scroll = 0;
                }
                press(t.id, null, button, shift);
                return;
            }
        }
        for (SideEntry s : sides) {
            if (sideClick && !s.caption && inside(mouseX, mouseY, s.x, s.y, s.w, s.h)) {
                focus(null);
                press(s.id, s.confirm, button, shift);
                return;
            }
        }
        // подвал
        for (Btn b : foot) {
            if (b.hit(mouseX, mouseY)) {
                focus(null);
                b.press(this, button, shift);
                return;
            }
        }
        // полоса прокрутки
        if (contentHeight > ch && inside(mouseX, mouseY, cx + cw + 2, cy, 6, ch)) {
            draggingScroll = true;
            scrollTo(mouseY);
            return;
        }
        // содержимое
        if (inside(mouseX, mouseY, cx, cy, cw, ch)) {
            for (W w : visible) {
                if (mouseY >= w.y && mouseY < w.y + w.h) {
                    if (!(w instanceof WField)) {
                        focus(null);
                    }
                    w.click(this, mouseX, mouseY, button);
                    return;
                }
            }
        }
        focus(null);
    }

    private void scrollTo(int mouseY) {
        int maxScroll = Math.max(0, contentHeight - ch);
        float f = (mouseY - cy) / (float) Math.max(1, ch);
        scroll = Math.max(0, Math.min(maxScroll, (int) (f * contentHeight - ch / 2.0F)));
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (draggingScroll) {
            scrollTo(mouseY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingScroll = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int dir = wheel > 0 ? -1 : 1;
        if (overlay != null) {
            overlay.wheel(dir);
            return;
        }
        int mx = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int my = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
        if (inSide(mx, my)) {
            sideScroll = Math.max(0, Math.min(Math.max(0, sideContent - sideAreaH()), sideScroll + dir * 22));
            return;
        }
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - ch), scroll + dir * 24));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (overlay != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                overlay = null;
                return;
            }
            overlay.key(this, typedChar, keyCode);
            return;
        }
        if (focused != null && focused.tf != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                focused.tf.setText(focused.val);
                focused.tf.setFocused(false);
                focused = null;
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                WField f = focused;
                focus(null);
                if (f.enterId >= 0) {
                    send(f.enterId, 0, false, "");
                }
                return;
            }
            if (focused.accepts(typedChar, keyCode)) {
                focused.tf.textboxKeyTyped(typedChar, keyCode);
            }
            return;
        }
        if (search != null && search.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                search.setFocused(false);
                return;
            }
            search.textboxKeyTyped(typedChar, keyCode);
            if (!search.getText().equals(filter)) {
                filter = search.getText();
                scroll = 0;
                relayoutBody();
            }
            return;
        }
        if (search != null && keyCode == Keyboard.KEY_F && isCtrlKeyDown()) {
            search.setFocused(true);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void updateScreen() {
        if (focused != null && focused.tf != null) {
            focused.tf.updateCursorCounter();
        }
        if (search != null) {
            search.updateCursorCounter();
        }
        if (overlay != null) {
            overlay.tick();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (focused != null) {
            focused.apply(this);
        }
        if (!closedByServer) {
            com.ecoserv.client.ClientHooks.windowClosed(window);
            PacketHandler.sendToServer(new PacketUiAction(window, revision, -1, 0, false, ""));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // --- элементы боковой панели и подвала -----------------------------------------------------------

    static final class Tab {
        final int id;
        final String label;
        final ItemStack icon;
        final boolean sel;
        int x;
        int y;
        int w;
        int h;

        Tab(NBTTagCompound t) {
            id = GuiWindow.id(t.getString("id"));
            label = t.getString("label");
            icon = t.hasKey("icon", 10) ? new ItemStack(t.getCompoundTag("icon")) : ItemStack.EMPTY;
            sel = t.getBoolean("sel");
        }
    }

    static final class SideEntry {
        final int id;
        final String label;
        final ItemStack icon;
        final int color;
        final boolean caption;
        final String confirm;
        final List<String> tip;
        int x;
        int y;
        int w;
        int h;

        SideEntry(NBTTagCompound t) {
            id = GuiWindow.id(t.getString("id"));
            label = t.getString("label");
            icon = t.hasKey("icon", 10) ? new ItemStack(t.getCompoundTag("icon")) : ItemStack.EMPTY;
            color = t.getInteger("color");
            caption = t.getBoolean("caption");
            confirm = t.getString("confirm");
            tip = strings(t.getTagList("tip", 8));
        }
    }

    /** Кнопка (подвал, строка, ряд кнопок). */
    static final class Btn {
        final int id;
        final String label;
        final int color;
        final String confirm;
        final boolean disabled;
        final List<String> tip;
        int x;
        int y;
        int w;
        int h;

        Btn(NBTTagCompound t) {
            id = GuiWindow.id(t.getString("id"));
            label = t.getString("label");
            color = t.getInteger("color");
            confirm = t.getString("confirm");
            disabled = t.getBoolean("dis");
            tip = strings(t.getTagList("tip", 8));
        }

        boolean hit(int mx, int my) {
            return inside(mx, my, x, y, w, h);
        }

        void draw(GuiWindow g, int mx, int my) {
            boolean hover = hit(mx, my);
            UiTheme.button(x, y, w, h, label, color, hover, disabled);
            if (hover && !tip.isEmpty()) {
                g.setTooltip(tip);
            }
        }

        void press(GuiWindow g, int button, boolean shift) {
            if (!disabled) {
                g.press(id, confirm, button, shift);
            }
        }
    }

    // --- элементы содержимого ---------------------------------------------------------------------------

    /** Элемент списка. */
    abstract static class W {
        int id = -1;
        boolean disabled;
        List<String> tip = new ArrayList<String>();
        /** Кнопка ↺ «по умолчанию» (-1 — нет). */
        int resetId = -1;
        String resetTip = "";
        int resetX;
        int resetY;
        int x;
        int y;
        int w;
        int h;
        int relY;
        @Nullable
        GuiTextField tf;

        abstract int measure(GuiWindow g, int width);

        abstract void draw(GuiWindow g, int mx, int my);

        void click(GuiWindow g, int mx, int my, int button) {
        }

        boolean searchable() {
            return false;
        }

        String searchText() {
            return "";
        }

        boolean hover(int mx, int my) {
            return inside(mx, my, x, y, w, h);
        }

        @Nullable
        static W of(NBTTagCompound t) {
            W w;
            switch (t.getString("t")) {
                case "h":
                    w = new WHeader(t);
                    break;
                case "p":
                    w = new WText(t);
                    break;
                case "note":
                    w = new WNote(t);
                    break;
                case "sp":
                    w = new WSpacer();
                    break;
                case "row":
                    w = new WRow(t);
                    break;
                case "tog":
                    w = new WToggle(t);
                    break;
                case "fld":
                    w = new WField(t);
                    break;
                case "sel":
                    w = new WSelect(t);
                    break;
                case "btns":
                    w = new WButtons(t);
                    break;
                case "grid":
                    w = new WGrid(t);
                    break;
                case "bar":
                    w = new WBar(t);
                    break;
                default:
                    return null;
            }
            w.id = GuiWindow.id(t.getString("id"));
            w.disabled = t.getBoolean("dis");
            w.tip = strings(t.getTagList("tip", 8));
            w.resetId = t.hasKey("reset") ? GuiWindow.id(t.getString("reset")) : -1;
            w.resetTip = t.getString("resetTip");
            return w;
        }

        /** Нарисовать кнопку ↺ (если есть) в точке rx, ry. */
        void drawReset(GuiWindow g, int mx, int my, int rx, int ry) {
            if (resetId < 0) {
                return;
            }
            resetX = rx;
            resetY = ry;
            boolean hover = inside(mx, my, rx, ry, 13, 12);
            UiTheme.round(rx, ry, 13, 12, hover ? 0xFF3A4253 : 0xFF262A33);
            UiTheme.center("↺", rx + 7, ry + 2, hover ? 0xFFFFD27A : UiTheme.DIM);
            if (hover) {
                g.setTooltip(listOf("Вернуть по умолчанию", resetTip));
            }
        }

        /** Нажали ↺? */
        boolean clickReset(GuiWindow g, int mx, int my) {
            if (resetId >= 0 && inside(mx, my, resetX, resetY, 13, 12)) {
                g.press(resetId, null, 0, false);
                return true;
            }
            return false;
        }

        static String plain(String s) {
            String p = TextFormatting.getTextWithoutFormattingCodes(s);
            return (p == null ? s : p).toLowerCase(Locale.ROOT);
        }
    }

    static final class WHeader extends W {
        final String text;
        final String right;

        WHeader(NBTTagCompound t) {
            text = t.getString("text");
            right = t.getString("right");
        }

        @Override
        int measure(GuiWindow g, int width) {
            return 16;
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            UiTheme.text(TextFormatting.BOLD + UiTheme.trim(text, w - 60), x + 1, y + 5, 0xFF8FB4FF);
            if (!right.isEmpty()) {
                UiTheme.text(right, x + w - UiTheme.width(right) - 1, y + 5, UiTheme.MUTED);
            }
            UiTheme.rect(x, y + 15, w, 1, UiTheme.BORDER);
        }
    }

    static final class WText extends W {
        final String text;
        List<String> lines = new ArrayList<String>();

        WText(NBTTagCompound t) {
            text = t.getString("text");
        }

        @Override
        int measure(GuiWindow g, int width) {
            lines = g.fontRenderer.listFormattedStringToWidth(text, width - 4);
            return lines.size() * 10 + 2;
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            for (int i = 0; i < lines.size(); i++) {
                UiTheme.text(lines.get(i), x + 2, y + 1 + i * 10, UiTheme.DIM);
            }
        }
    }

    static final class WNote extends W {
        final String text;
        final int color;
        List<String> lines = new ArrayList<String>();

        WNote(NBTTagCompound t) {
            text = t.getString("text");
            color = t.getInteger("color");
        }

        @Override
        int measure(GuiWindow g, int width) {
            lines = g.fontRenderer.listFormattedStringToWidth(text, width - 16);
            return lines.size() * 10 + 8;
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            int c = UiTheme.kind(color);
            UiTheme.round(x, y, w, h, UiTheme.alpha(c, 0x38));
            UiTheme.rect(x, y + 1, 2, h - 2, UiTheme.lighter(c, 0.2F));
            for (int i = 0; i < lines.size(); i++) {
                UiTheme.text(lines.get(i), x + 8, y + 4 + i * 10, UiTheme.TEXT);
            }
        }
    }

    static final class WSpacer extends W {
        @Override
        int measure(GuiWindow g, int width) {
            return 4;
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
        }
    }

    static final class Chip {
        final String text;
        final int color;

        Chip(NBTTagCompound t) {
            text = t.getString("text");
            color = t.getInteger("color");
        }
    }

    static final class WRow extends W {
        final ItemStack icon;
        final String title;
        final List<String> lines;
        final List<Chip> chips = new ArrayList<Chip>();
        final List<Btn> btns = new ArrayList<Btn>();
        final String confirm;
        final boolean hl;
        final boolean dim;

        WRow(NBTTagCompound t) {
            icon = t.hasKey("icon", 10) ? new ItemStack(t.getCompoundTag("icon")) : ItemStack.EMPTY;
            title = t.getString("title");
            lines = strings(t.getTagList("lines", 8));
            NBTTagList cl = t.getTagList("chips", 10);
            for (int i = 0; i < cl.tagCount(); i++) {
                chips.add(new Chip(cl.getCompoundTagAt(i)));
            }
            NBTTagList bl = t.getTagList("btns", 10);
            for (int i = 0; i < bl.tagCount(); i++) {
                btns.add(new Btn(bl.getCompoundTagAt(i)));
            }
            confirm = t.getString("confirm");
            hl = t.getBoolean("hl");
            dim = t.getBoolean("dim");
        }

        @Override
        boolean searchable() {
            return true;
        }

        @Override
        String searchText() {
            StringBuilder sb = new StringBuilder(plain(title));
            for (String l : lines) {
                sb.append(' ').append(plain(l));
            }
            return sb.toString();
        }

        @Override
        int measure(GuiWindow g, int width) {
            int n = Math.min(3, lines.size());
            return Math.max(26, 6 + 10 * (1 + n) + 3);
        }

        private int rightWidth() {
            int rw = 0;
            for (Btn b : btns) {
                rw += Math.max(36, UiTheme.width(b.label) + 12) + 3;
            }
            for (Chip c : chips) {
                rw += UiTheme.width(c.text) + 10 + 3;
            }
            return rw;
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            boolean overButton = false;
            int bx = x + w - 5;
            for (int i = btns.size() - 1; i >= 0; i--) {
                Btn b = btns.get(i);
                b.w = Math.max(36, UiTheme.width(b.label) + 12);
                b.h = 14;
                bx -= b.w;
                b.x = bx;
                b.y = y + (h - 14) / 2;
                overButton |= b.hit(mx, my);
                bx -= 3;
            }
            boolean hover = hover(mx, my) && !overButton && id >= 0;
            int bg = hl ? UiTheme.ROW_HL : hover ? UiTheme.ROW_HOVER : UiTheme.ROW;
            UiTheme.round(x, y, w, h, bg);
            if (hl) {
                UiTheme.rect(x, y + 3, 2, h - 6, UiTheme.ACCENT);
            }
            int ix = x + 5;
            if (!icon.isEmpty()) {
                UiTheme.item(icon, ix, y + (lines.isEmpty() ? (h - 16) / 2 : 5));
            }
            int tx = x + 26;
            int room = bx - tx - 4;
            for (Chip c : chips) {
                room -= UiTheme.width(c.text) + 13;
            }
            UiTheme.text(UiTheme.trim(title, Math.max(20, room)), tx, y + (lines.isEmpty() ? (h - 8) / 2 : 6),
                    dim ? UiTheme.MUTED : UiTheme.TEXT);
            int n = Math.min(3, lines.size());
            for (int i = 0; i < n; i++) {
                UiTheme.text(UiTheme.trim(lines.get(i), Math.max(20, bx - tx - 4)), tx, y + 16 + i * 10, UiTheme.DIM);
            }
            // плашки — слева от кнопок
            int chipX = bx;
            for (int i = chips.size() - 1; i >= 0; i--) {
                Chip c = chips.get(i);
                int cw = UiTheme.width(c.text) + 10;
                chipX -= cw;
                int cy = y + (lines.isEmpty() ? (h - 12) / 2 : 5);
                int col = UiTheme.kind(c.color);
                UiTheme.round(chipX, cy, cw, 12, UiTheme.alpha(col, 0x60));
                UiTheme.text(c.text, chipX + 5, cy + 2, 0xFFFFFFFF);
                chipX -= 3;
            }
            for (Btn b : btns) {
                b.draw(g, mx, my);
            }
            if (hover && !tip.isEmpty()) {
                g.setTooltip(tip);
            }
        }

        @Override
        void click(GuiWindow g, int mx, int my, int button) {
            for (Btn b : btns) {
                if (b.hit(mx, my)) {
                    b.press(g, button, isShiftKeyDown());
                    return;
                }
            }
            if (id >= 0 && !disabled) {
                g.press(id, confirm, button, isShiftKeyDown());
            }
        }
    }

    static final class WToggle extends W {
        final String label;
        final String desc;
        boolean val;
        List<String> lines = new ArrayList<String>();

        WToggle(NBTTagCompound t) {
            label = t.getString("label");
            desc = t.getString("desc");
            val = t.getBoolean("val");
        }

        @Override
        boolean searchable() {
            return true;
        }

        @Override
        String searchText() {
            return plain(label + " " + desc);
        }

        @Override
        int measure(GuiWindow g, int width) {
            lines = desc.isEmpty() ? new ArrayList<String>() : g.fontRenderer.listFormattedStringToWidth(desc, width - 44);
            return Math.max(22, 6 + 10 + lines.size() * 10 + 4);
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            boolean hover = hover(mx, my) && !disabled;
            UiTheme.round(x, y, w, h, hover ? UiTheme.ROW_HOVER : UiTheme.ROW);
            UiTheme.text(UiTheme.trim(label, w - 44), x + 6, y + 6, disabled ? UiTheme.MUTED : UiTheme.TEXT);
            for (int i = 0; i < lines.size(); i++) {
                UiTheme.text(lines.get(i), x + 6, y + 16 + i * 10, UiTheme.MUTED);
            }
            UiTheme.toggle(x + w - 32, y + (h - 12) / 2, val, hover, disabled);
            drawReset(g, mx, my, x + w - 50, y + (h - 12) / 2);
            if (hover && !tip.isEmpty() && !inside(mx, my, resetX, resetY, 13, 12)) {
                g.setTooltip(tip);
            }
        }

        @Override
        void click(GuiWindow g, int mx, int my, int button) {
            if (clickReset(g, mx, my)) {
                return;
            }
            if (disabled || id < 0) {
                return;
            }
            g.click();
            val = !val;
            g.send(id, 0, false, val ? "1" : "0");
        }
    }

    static final class WField extends W {
        final String label;
        final String desc;
        String val;
        final String kind;
        final int max;
        final String placeholder;
        final String pick;
        final boolean clear;
        final int enterId;
        List<String> lines = new ArrayList<String>();
        int boxX;
        int boxY;
        int boxW;

        WField(NBTTagCompound t) {
            label = t.getString("label");
            desc = t.getString("desc");
            val = t.getString("val");
            kind = t.getString("kind");
            max = Math.max(1, t.getInteger("max"));
            placeholder = t.getString("ph");
            pick = t.getString("pick");
            clear = t.getBoolean("clear");
            enterId = t.hasKey("enter") ? GuiWindow.id(t.getString("enter")) : -1;
        }

        @Override
        boolean searchable() {
            return true;
        }

        @Override
        String searchText() {
            return plain(label + " " + desc + ("pass".equals(kind) ? "" : " " + val));
        }

        int buttonsWidth() {
            int bw = 0;
            if (!pick.isEmpty()) {
                bw += 20;
            }
            if (clear) {
                bw += 18;
            }
            return bw;
        }

        @Override
        int measure(GuiWindow g, int width) {
            lines = desc.isEmpty() ? new ArrayList<String>() : g.fontRenderer.listFormattedStringToWidth(desc, width - 12);
            int newBoxW = width - 12 - buttonsWidth();
            if (tf == null || boxW != newBoxW) {
                String text = tf == null ? val : tf.getText();
                boolean focused = tf != null && tf.isFocused();
                tf = new GuiTextField(id, g.fontRenderer, 0, 0, Math.max(10, newBoxW - 8), 10);
                tf.setEnableBackgroundDrawing(false);
                tf.setMaxStringLength(max);
                tf.setText(text);
                tf.setFocused(focused);
                tf.setEnabled(!disabled);
            }
            boxW = newBoxW;
            return 6 + 10 + lines.size() * 10 + 2 + 16 + 5;
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            UiTheme.round(x, y, w, h, UiTheme.ROW);
            boolean changed = tf != null && !tf.getText().equals(val);
            UiTheme.text(UiTheme.trim(label, w - 90), x + 6, y + 6, disabled ? UiTheme.MUTED : UiTheme.TEXT);
            int right = x + w - 6;
            if (resetId >= 0) {
                drawReset(g, mx, my, right - 13, y + 4);
                right -= 17;
            }
            if (changed) {
                String hint = "Enter — сохранить";
                UiTheme.text(hint, right - UiTheme.width(hint), y + 6, 0xFFE8B339);
            }
            for (int i = 0; i < lines.size(); i++) {
                UiTheme.text(lines.get(i), x + 6, y + 16 + i * 10, UiTheme.MUTED);
            }
            boxX = x + 6;
            boxY = y + 6 + 10 + lines.size() * 10 + 2;
            boolean focused = g.focused == this;
            UiTheme.panel(boxX, boxY, boxW, 16, UiTheme.INPUT, focused ? UiTheme.ACCENT : UiTheme.INPUT_BORDER);
            if ("color".equals(kind)) {
                Integer c = com.ecoserv.ui.Ui.parseColor(tf == null ? val : tf.getText());
                if (c != null) {
                    UiTheme.rect(boxX + boxW - 14, boxY + 3, 10, 10, 0xFF000000 | c);
                }
            }
            if (tf != null) {
                tf.x = boxX + 4;
                tf.y = boxY + 4;
                if (tf.getText().isEmpty() && !focused && !placeholder.isEmpty()) {
                    UiTheme.text(UiTheme.trim(placeholder, boxW - 8), boxX + 4, boxY + 4, UiTheme.MUTED);
                }
                if ("pass".equals(kind)) {
                    // 2.0: пароль — точки вместо букв
                    StringBuilder dots = new StringBuilder();
                    for (int i = 0; i < tf.getText().length(); i++) {
                        dots.append('*');
                    }
                    String shown = UiTheme.trim(dots.toString(), boxW - 10);
                    UiTheme.text(shown, boxX + 4, boxY + 4, UiTheme.TEXT);
                    if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                        UiTheme.text("_", boxX + 4 + UiTheme.width(shown), boxY + 4, UiTheme.TEXT);
                    }
                } else {
                    tf.drawTextBox();
                }
            }
            int bx = boxX + boxW + 2;
            if (!pick.isEmpty()) {
                boolean hover = inside(mx, my, bx, boxY, 18, 16);
                UiTheme.button(bx, boxY, 18, 16, "…", 1, hover, disabled);
                if (hover) {
                    g.setTooltip(listOf(pickTitle()));
                }
                bx += 20;
            }
            if (clear) {
                boolean hover = inside(mx, my, bx, boxY, 16, 16);
                UiTheme.button(bx, boxY, 16, 16, "✕", 0, hover, disabled);
                if (hover) {
                    g.setTooltip(listOf("Очистить"));
                }
            }
            if (hover(mx, my) && !tip.isEmpty() && !inside(mx, my, boxX, boxY, boxW, 16)) {
                g.setTooltip(tip);
            }
        }

        String pickTitle() {
            switch (pick) {
                case "sound":
                    return "Выбрать звук из списка";
                case "item":
                    return "Выбрать предмет";
                case "player":
                    return "Выбрать игрока";
                case "color":
                    return "Выбрать цвет";
                default:
                    return "Выбрать";
            }
        }

        boolean accepts(char c, int code) {
            if (c < ' ' || "text".equals(kind) || "color".equals(kind)) {
                return true;
            }
            if ("int".equals(kind)) {
                return Character.isDigit(c) || c == '-' || c == ' ';
            }
            if ("money".equals(kind)) {
                return Character.isDigit(c) || ".,kmbкмб ".indexOf(Character.toLowerCase(c)) >= 0;
            }
            return true;
        }

        void apply(GuiWindow g) {
            if (tf == null || disabled) {
                return;
            }
            String v = tf.getText();
            if (!v.equals(val)) {
                val = v;
                g.send(id, 0, false, v);
            }
        }

        void setAndApply(GuiWindow g, String value) {
            if (tf != null) {
                tf.setText(value);
            }
            apply(g);
        }

        @Override
        void click(GuiWindow g, int mx, int my, int button) {
            if (clickReset(g, mx, my)) {
                g.focus(null);
                return;
            }
            if (disabled) {
                return;
            }
            int bx = boxX + boxW + 2;
            if (!pick.isEmpty()) {
                if (inside(mx, my, bx, boxY, 18, 16)) {
                    g.click();
                    g.focus(null);
                    g.overlay = new PickerOverlay(g, this, pick);
                    return;
                }
                bx += 20;
            }
            if (clear && inside(mx, my, bx, boxY, 16, 16)) {
                g.click();
                g.focus(null);
                setAndApply(g, "");
                return;
            }
            if (inside(mx, my, boxX, boxY, boxW, 16)) {
                g.focus(this);
                if (tf != null) {
                    if (button == 1) {
                        tf.setText("");
                    }
                    tf.mouseClicked(mx, my, 0);
                }
                return;
            }
            g.focus(null);
        }
    }

    static final class Opt {
        final String key;
        final String label;
        final String desc;
        final ItemStack icon;

        Opt(NBTTagCompound t) {
            key = t.getString("k");
            label = t.getString("l");
            desc = t.getString("d");
            icon = t.hasKey("icon", 10) ? new ItemStack(t.getCompoundTag("icon")) : ItemStack.EMPTY;
        }
    }

    static final class WSelect extends W {
        final String label;
        final String desc;
        String val;
        final List<Opt> opts = new ArrayList<Opt>();
        List<String> lines = new ArrayList<String>();
        int bx;
        int by;
        int bw;

        WSelect(NBTTagCompound t) {
            label = t.getString("label");
            desc = t.getString("desc");
            val = t.getString("val");
            NBTTagList ol = t.getTagList("opts", 10);
            for (int i = 0; i < ol.tagCount(); i++) {
                opts.add(new Opt(ol.getCompoundTagAt(i)));
            }
        }

        @Override
        boolean searchable() {
            return true;
        }

        @Override
        String searchText() {
            return plain(label + " " + desc + " " + current());
        }

        String current() {
            for (Opt o : opts) {
                if (o.key.equals(val)) {
                    return o.label;
                }
            }
            return val.isEmpty() ? "—" : val;
        }

        @Override
        int measure(GuiWindow g, int width) {
            bw = Math.min(150, Math.max(90, width / 3));
            lines = desc.isEmpty() ? new ArrayList<String>() : g.fontRenderer.listFormattedStringToWidth(desc, width - bw - 18);
            return Math.max(24, 6 + 10 + lines.size() * 10 + 4);
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            UiTheme.round(x, y, w, h, UiTheme.ROW);
            UiTheme.text(UiTheme.trim(label, w - bw - (resetId >= 0 ? 36 : 18)), x + 6, y + 6, disabled ? UiTheme.MUTED : UiTheme.TEXT);
            for (int i = 0; i < lines.size(); i++) {
                UiTheme.text(lines.get(i), x + 6, y + 16 + i * 10, UiTheme.MUTED);
            }
            bx = x + w - bw - 6;
            by = y + (h - 16) / 2;
            boolean hover = inside(mx, my, bx, by, bw, 16) && !disabled;
            UiTheme.panel(bx, by, bw, 16, hover ? 0xFF1A1D25 : UiTheme.INPUT, hover ? UiTheme.ACCENT : UiTheme.INPUT_BORDER);
            UiTheme.text(UiTheme.trim(current(), bw - 16), bx + 5, by + 4, disabled ? UiTheme.MUTED : UiTheme.TEXT);
            UiTheme.text("▾", bx + bw - 10, by + 4, UiTheme.DIM);
            drawReset(g, mx, my, bx - 17, by + 2);
            if (hover(mx, my) && !tip.isEmpty() && !inside(mx, my, bx, by, bw, 16)) {
                g.setTooltip(tip);
            }
        }

        @Override
        void click(GuiWindow g, int mx, int my, int button) {
            if (clickReset(g, mx, my)) {
                return;
            }
            if (disabled || id < 0 || opts.isEmpty()) {
                return;
            }
            if (!inside(mx, my, bx, by, bw, 16) && !inside(mx, my, x, y, w, h)) {
                return;
            }
            g.click();
            g.overlay = new DropdownOverlay(g, this);
        }

        void choose(GuiWindow g, String key) {
            if (!key.equals(val)) {
                val = key;
                g.send(id, 0, false, key);
            }
        }
    }

    static final class WButtons extends W {
        final List<Btn> btns = new ArrayList<Btn>();

        WButtons(NBTTagCompound t) {
            NBTTagList bl = t.getTagList("b", 10);
            for (int i = 0; i < bl.tagCount(); i++) {
                btns.add(new Btn(bl.getCompoundTagAt(i)));
            }
        }

        @Override
        int measure(GuiWindow g, int width) {
            return 20;
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            if (btns.isEmpty()) {
                return;
            }
            int n = btns.size();
            int bw = (w - 4 * (n - 1)) / n;
            for (int i = 0; i < n; i++) {
                Btn b = btns.get(i);
                b.x = x + i * (bw + 4);
                b.y = y + 1;
                b.w = i == n - 1 ? x + w - b.x : bw;
                b.h = 18;
                b.draw(g, mx, my);
            }
        }

        @Override
        void click(GuiWindow g, int mx, int my, int button) {
            for (Btn b : btns) {
                if (b.hit(mx, my)) {
                    b.press(g, button, isShiftKeyDown());
                    return;
                }
            }
        }
    }

    static final class Cell {
        final int id;
        final ItemStack item;
        final int count;
        final boolean sel;
        final List<String> tip;

        Cell(NBTTagCompound t) {
            id = GuiWindow.id(t.getString("id"));
            item = t.hasKey("item", 10) ? new ItemStack(t.getCompoundTag("item")) : ItemStack.EMPTY;
            count = t.getInteger("count");
            sel = t.getBoolean("sel");
            tip = strings(t.getTagList("tip", 8));
        }
    }

    static final class WGrid extends W {
        static final int CELL = 20;
        final List<Cell> cells = new ArrayList<Cell>();
        int cols = 1;
        /** Заданное число столбцов (0 — сколько влезет). */
        final int fixedCols;
        /** Сдвиг, чтобы сетка с заданными столбцами стояла по центру. */
        int offset;

        WGrid(NBTTagCompound t) {
            fixedCols = t.getInteger("cols");
            NBTTagList cl = t.getTagList("cells", 10);
            for (int i = 0; i < cl.tagCount(); i++) {
                cells.add(new Cell(cl.getCompoundTagAt(i)));
            }
        }

        @Override
        boolean searchable() {
            return true;
        }

        @Override
        String searchText() {
            StringBuilder sb = new StringBuilder();
            for (Cell c : cells) {
                if (!c.item.isEmpty()) {
                    sb.append(plain(c.item.getDisplayName())).append(' ');
                }
            }
            return sb.toString();
        }

        @Override
        int measure(GuiWindow g, int width) {
            cols = Math.max(1, width / CELL);
            if (fixedCols > 0) {
                cols = Math.min(cols, fixedCols);
            }
            offset = fixedCols > 0 ? Math.max(0, (width - cols * CELL) / 2) : 0;
            int rows = Math.max(1, (cells.size() + cols - 1) / cols);
            return rows * CELL;
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            for (int i = 0; i < cells.size(); i++) {
                Cell c = cells.get(i);
                int cx = x + offset + (i % cols) * CELL;
                int cy = y + (i / cols) * CELL;
                boolean hover = inside(mx, my, cx, cy, CELL - 1, CELL - 1);
                UiTheme.panel(cx, cy, CELL - 1, CELL - 1, hover ? 0xFF262A35 : 0xFF14161C,
                        c.sel ? UiTheme.ACCENT : hover ? UiTheme.BORDER_LIGHT : UiTheme.BORDER);
                if (!c.item.isEmpty()) {
                    ItemStack shown = c.item;
                    if (c.count > 64) {
                        shown = c.item.copy();
                        shown.setCount(1);
                    }
                    UiTheme.item(shown, cx + 1, cy + 1);
                    if (c.count > 64) {
                        String s = c.count >= 10000 ? (c.count / 1000) + "k" : Integer.toString(c.count);
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(0, 0, 300);
                        UiTheme.shadow(s, cx + CELL - 2 - UiTheme.width(s), cy + CELL - 9, 0xFFFFFFFF);
                        GlStateManager.popMatrix();
                    }
                }
                if (hover && !c.item.isEmpty()) {
                    List<String> lines = new ArrayList<String>();
                    try {
                        lines.addAll(c.item.getTooltip(g.mc.player, g.mc.gameSettings.advancedItemTooltips
                                ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL));
                    } catch (Throwable t) {
                        lines.add(c.item.getDisplayName());
                    }
                    if (c.count > 64) {
                        lines.add(TextFormatting.GRAY + "Количество: " + c.count);
                    }
                    lines.addAll(c.tip);
                    g.setTooltip(lines);
                }
            }
        }

        @Override
        void click(GuiWindow g, int mx, int my, int button) {
            if (mx < x + offset) {
                return;
            }
            int col = (mx - x - offset) / CELL;
            int row = (my - y) / CELL;
            int i = row * cols + col;
            if (col >= 0 && col < cols && i >= 0 && i < cells.size() && cells.get(i).id >= 0) {
                g.click();
                g.send(cells.get(i).id, button, isShiftKeyDown(), "");
            }
        }
    }

    static final class WBar extends W {
        final String label;
        final double val;
        final double max;
        final int color;

        WBar(NBTTagCompound t) {
            label = t.getString("label");
            val = t.getDouble("val");
            max = t.getDouble("max");
            color = t.getInteger("color");
        }

        @Override
        int measure(GuiWindow g, int width) {
            return 22;
        }

        @Override
        void draw(GuiWindow g, int mx, int my) {
            UiTheme.round(x, y, w, h, UiTheme.ROW);
            UiTheme.text(UiTheme.trim(label, w - 12), x + 6, y + 4, UiTheme.TEXT);
            int bw = w - 12;
            UiTheme.round(x + 6, y + 15, bw, 4, 0xFF0C0E12);
            double f = max <= 0 ? 0 : Math.max(0, Math.min(1, val / max));
            UiTheme.round(x + 6, y + 15, Math.max(2, (int) (bw * f)), 4, UiTheme.kind(color));
        }
    }
}
