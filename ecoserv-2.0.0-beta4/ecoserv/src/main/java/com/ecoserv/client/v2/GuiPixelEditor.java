package com.ecoserv.client.v2;

import com.ecoserv.client.ui.UiTheme;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Рисовалка текстур (2.0 beta3): кисть, ластик, заливка, пипетка, палитра и цвет по HEX, отмена, отражение,
 * светлее/темнее, сдвиг оттенка, картинка из предмета в руке, просмотр плиткой 3×3. Сохраняет PNG на сервер.
 */
@SideOnly(Side.CLIENT)
public class GuiPixelEditor extends GuiScreen {

    private static final int[] PALETTE = {0x000000, 0x404040, 0x808080, 0xC0C0C0, 0xFFFFFF, 0x7F3300, 0xB5651D, 0xFFD39B,
            0xFF0000, 0xFF7F00, 0xFFFF00, 0x7FFF00, 0x00C000, 0x006000, 0x00FFFF, 0x0080FF, 0x0000FF, 0x400080, 0x8000FF,
            0xFF00FF, 0xFF80C0, 0x3C2414, 0x6B8E23, 0x87CEEB};
    private static final String[] TOOLS = {"Кисть", "Ластик", "Заливка", "Пипетка"};

    private final String key;
    private int size;
    private int[] px;
    private int color = 0xFF3080FF;
    private int tool;
    private final Deque<int[]> undo = new ArrayDeque<int[]>();
    private int cell;
    private int cx;
    private int cy;
    private GuiTextField hex;
    private boolean painting;

    public GuiPixelEditor(String key, int size, byte[] png) {
        this.key = key;
        this.size = size == 32 ? 32 : 16;
        this.px = new int[this.size * this.size];
        if (png != null) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                if (img != null) {
                    this.size = Math.min(64, img.getWidth());
                    this.px = new int[this.size * this.size];
                    for (int y = 0; y < this.size; y++) {
                        for (int x = 0; x < this.size; x++) {
                            px[y * this.size + x] = img.getRGB(x, Math.min(y, img.getHeight() - 1));
                        }
                    }
                }
            } catch (IOException ignored) {
                // пустая
            }
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        cell = Math.max(4, Math.min((height - 50) / size, (width - 260) / size));
        cx = 12;
        cy = 28;
        hex = new GuiTextField(1, fontRenderer, cx + size * cell + 70, cy + 150, 60, 12);
        hex.setMaxStringLength(7);
        hex.setText(String.format("#%06X", color & 0xFFFFFF));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void push() {
        undo.push(px.clone());
        while (undo.size() > 40) {
            undo.removeLast();
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        UiTheme.shadow("Текстура «" + key + "» · " + size + "×" + size, 12, 10, 0xFFFFD27A);
        // шахматка + пиксели
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int bx = cx + x * cell;
                int by = cy + y * cell;
                UiTheme.rect(bx, by, cell, cell, ((x + y) & 1) == 0 ? 0xFF3A3A3A : 0xFF2A2A2A);
                int c = px[y * size + x];
                if ((c >>> 24) > 0) {
                    UiTheme.rect(bx, by, cell, cell, c);
                }
            }
        }
        int hx = (mx - cx) / Math.max(1, cell);
        int hy = (my - cy) / Math.max(1, cell);
        if (mx >= cx && my >= cy && hx < size && hy < size) {
            UiTheme.border(cx + hx * cell, cy + hy * cell, cell, cell, 0xFFFFFFFF);
        }
        UiTheme.border(cx - 1, cy - 1, size * cell + 2, size * cell + 2, UiTheme.BORDER);
        // панель справа
        int rx = cx + size * cell + 12;
        for (int i = 0; i < TOOLS.length; i++) {
            btn(rx, cy + i * 18, 70, TOOLS[i], tool == i, mx, my);
        }
        // цвет
        UiTheme.text("Цвет", rx + 80, cy, UiTheme.DIM);
        UiTheme.rect(rx + 80, cy + 10, 50, 24, 0xFF000000);
        UiTheme.rect(rx + 81, cy + 11, 48, 22, color);
        for (int i = 0; i < PALETTE.length; i++) {
            int x = rx + (i % 8) * 17;
            int y = cy + 80 + (i / 8) * 13;
            UiTheme.rect(x, y, 15, 11, 0xFF000000);
            UiTheme.rect(x + 1, y + 1, 13, 9, 0xFF000000 | PALETTE[i]);
        }
        UiTheme.text("HEX", rx + 40, cy + 152, UiTheme.DIM);
        UiTheme.rect(hex.x - 3, hex.y - 2, hex.width + 4, 14, UiTheme.INPUT);
        hex.drawTextBox();
        int by = cy + 172;
        btn(rx, by, 66, "Отменить", false, mx, my);
        btn(rx + 70, by, 66, "Очистить", false, mx, my);
        btn(rx, by + 18, 66, "Отразить ↔", false, mx, my);
        btn(rx + 70, by + 18, 66, "Отразить ↕", false, mx, my);
        btn(rx, by + 36, 66, "Светлее", false, mx, my);
        btn(rx + 70, by + 36, 66, "Темнее", false, mx, my);
        btn(rx, by + 54, 66, "Оттенок +", false, mx, my);
        btn(rx + 70, by + 54, 66, "Оттенок −", false, mx, my);
        btn(rx, by + 72, 136, "Из предмета в руке", false, mx, my);
        btn(rx, by + 96, 66, "§aСохранить", false, mx, my);
        btn(rx + 70, by + 96, 66, "Закрыть", false, mx, my);
        // плитка 3×3
        int tx = rx + 150;
        int s = 2;
        if (tx + size * s * 3 < width) {
            UiTheme.text("Плиткой", tx, cy, UiTheme.DIM);
            for (int ty = 0; ty < 3; ty++) {
                for (int tx2 = 0; tx2 < 3; tx2++) {
                    for (int y = 0; y < size; y++) {
                        for (int x = 0; x < size; x++) {
                            int c = px[y * size + x];
                            if ((c >>> 24) > 0) {
                                UiTheme.rect(tx + (tx2 * size + x) * s, cy + 10 + (ty * size + y) * s, s, s, c);
                            }
                        }
                    }
                }
            }
        }
        UiTheme.text("§8ЛКМ — рисовать, ПКМ — пипетка, Ctrl+Z — отменить", 12, height - 12, UiTheme.MUTED);
        super.drawScreen(mx, my, pt);
    }

    private void btn(int x, int y, int w, String label, boolean on, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 15;
        UiTheme.round(x, y, w, 15, on ? 0xFF2F5BB0 : hov ? 0xFF2A2F3A : 0xFF1C2028);
        UiTheme.center(label, x + w / 2, y + 4, UiTheme.TEXT);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void paintAt(int mx, int my, int button) {
        int x = (mx - cx) / Math.max(1, cell);
        int y = (my - cy) / Math.max(1, cell);
        if (mx < cx || my < cy || x >= size || y >= size) {
            return;
        }
        int i = y * size + x;
        if (button == 1 || tool == 3) {
            if ((px[i] >>> 24) > 0) {
                color = px[i] | 0xFF000000;
                hex.setText(String.format("#%06X", color & 0xFFFFFF));
            }
            return;
        }
        if (tool == 2) {
            fill(x, y, px[i], color);
            return;
        }
        px[i] = tool == 1 ? 0 : color;
    }

    private void fill(int x, int y, int from, int to) {
        if (from == to) {
            return;
        }
        Deque<int[]> q = new ArrayDeque<int[]>();
        q.add(new int[]{x, y});
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[0] < 0 || p[1] < 0 || p[0] >= size || p[1] >= size || px[p[1] * size + p[0]] != from) {
                continue;
            }
            px[p[1] * size + p[0]] = to;
            q.add(new int[]{p[0] + 1, p[1]});
            q.add(new int[]{p[0] - 1, p[1]});
            q.add(new int[]{p[0], p[1] + 1});
            q.add(new int[]{p[0], p[1] - 1});
        }
    }

    private void adjust(float dh, float db) {
        push();
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            if ((c >>> 24) == 0) {
                continue;
            }
            float[] hsb = Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
            float h = (hsb[0] + dh + 1.0F) % 1.0F;
            float b = Math.max(0, Math.min(1, hsb[2] + db));
            px[i] = (c & 0xFF000000) | (Color.HSBtoRGB(h, hsb[1], b) & 0xFFFFFF);
        }
    }

    private void fromHand() {
        ItemStack st = mc.player.getHeldItemMainhand();
        if (st.isEmpty()) {
            return;
        }
        try {
            IBakedModel m = mc.getRenderItem().getItemModelWithOverrides(st, mc.world, mc.player);
            TextureAtlasSprite sp = m.getParticleTexture();
            int[][] data = sp.getFrameTextureData(0);
            int w = sp.getIconWidth();
            int h = sp.getIconHeight();
            push();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int sx = x * w / size;
                    int sy = y * h / size;
                    px[y * size + x] = data[0][sy * w + sx];
                }
            }
        } catch (Throwable t) {
            // нет картинки
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        hex.mouseClicked(mx, my, button);
        if (mx >= cx && my >= cy && mx < cx + size * cell && my < cy + size * cell) {
            push();
            painting = true;
            paintAt(mx, my, button);
            return;
        }
        int rx = cx + size * cell + 12;
        for (int i = 0; i < TOOLS.length; i++) {
            if (in(mx, my, rx, cy + i * 18, 70, 15)) {
                tool = i;
                return;
            }
        }
        for (int i = 0; i < PALETTE.length; i++) {
            if (in(mx, my, rx + (i % 8) * 17, cy + 80 + (i / 8) * 13, 15, 11)) {
                color = 0xFF000000 | PALETTE[i];
                hex.setText(String.format("#%06X", PALETTE[i]));
                if (tool == 1 || tool == 3) {
                    tool = 0;
                }
                return;
            }
        }
        int by = cy + 172;
        if (in(mx, my, rx, by, 66, 15) && !undo.isEmpty()) {
            px = undo.pop();
        } else if (in(mx, my, rx + 70, by, 66, 15)) {
            push();
            px = new int[size * size];
        } else if (in(mx, my, rx, by + 18, 66, 15)) {
            push();
            int[] n = new int[px.length];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    n[y * size + x] = px[y * size + size - 1 - x];
                }
            }
            px = n;
        } else if (in(mx, my, rx + 70, by + 18, 66, 15)) {
            push();
            int[] n = new int[px.length];
            for (int y = 0; y < size; y++) {
                System.arraycopy(px, (size - 1 - y) * size, n, y * size, size);
            }
            px = n;
        } else if (in(mx, my, rx, by + 36, 66, 15)) {
            adjust(0, 0.08F);
        } else if (in(mx, my, rx + 70, by + 36, 66, 15)) {
            adjust(0, -0.08F);
        } else if (in(mx, my, rx, by + 54, 66, 15)) {
            adjust(0.05F, 0);
        } else if (in(mx, my, rx + 70, by + 54, 66, 15)) {
            adjust(-0.05F, 0);
        } else if (in(mx, my, rx, by + 72, 136, 15)) {
            fromHand();
        } else if (in(mx, my, rx, by + 96, 66, 15)) {
            save();
        } else if (in(mx, my, rx + 70, by + 96, 66, 15)) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        if (painting && (tool == 0 || tool == 1)) {
            paintAt(mx, my, button);
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        painting = false;
    }

    private void save() {
        try {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    img.setRGB(x, y, px[y * size + x]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            NBTTagCompound d = new NBTTagCompound();
            d.setString("k", key);
            d.setByteArray("png", out.toByteArray());
            ClientV2.send("px_save", d);
            mc.displayGuiScreen(null);
        } catch (IOException e) {
            // не записалось
        }
    }

    @Override
    protected void keyTyped(char c, int code) throws IOException {
        if (hex.isFocused()) {
            if (code == Keyboard.KEY_RETURN) {
                hex.setFocused(false);
            } else {
                hex.textboxKeyTyped(c, code);
            }
            String t = hex.getText().replace("#", "");
            if (t.length() == 6) {
                try {
                    color = 0xFF000000 | Integer.parseInt(t, 16);
                } catch (NumberFormatException ignored) {
                    // ввод не закончен
                }
            }
            return;
        }
        if (code == Keyboard.KEY_Z && isCtrlKeyDown() && !undo.isEmpty()) {
            px = undo.pop();
            return;
        }
        super.keyTyped(c, code);
    }

    @Override
    public void updateScreen() {
        hex.updateCursorCounter();
    }
}
