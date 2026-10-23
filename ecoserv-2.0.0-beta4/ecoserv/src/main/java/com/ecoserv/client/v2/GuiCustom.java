package com.ecoserv.client.v2;

import com.ecoserv.gui.CustomGui;
import com.ecoserv.util.JsonUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 2.0 beta4: своё окно у игрока — нарисовано в дизайнере, тексты и полосы приходят от сервера. */
@SideOnly(Side.CLIENT)
public class GuiCustom extends GuiScreen {

    private CustomGui g;
    private final Map<Integer, GuiTextField> inputs = new HashMap<Integer, GuiTextField>();
    private boolean closedByServer;
    private float scale = 1.0F;
    private int left;
    private int top;

    public static void register() {
        ClientV2.on("cgui_open", d -> {
            CustomGui g = read(d);
            if (g != null) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiCustom(g));
            }
        });
        ClientV2.on("cgui_update", d -> {
            CustomGui g = read(d);
            Minecraft mc = Minecraft.getMinecraft();
            if (g != null && mc.currentScreen instanceof GuiCustom && ((GuiCustom) mc.currentScreen).g.id.equals(g.id)) {
                ((GuiCustom) mc.currentScreen).update(g);
            }
        });
        ClientV2.on("cgui_close", d -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen instanceof GuiCustom) {
                ((GuiCustom) mc.currentScreen).closedByServer = true;
                mc.displayGuiScreen(null);
            }
        });
    }

    static CustomGui read(NBTTagCompound d) {
        try {
            CustomGui g = JsonUtil.GSON.fromJson(d.getString("json"), CustomGui.class);
            if (g != null) {
                g.normalize();
            }
            return g;
        } catch (Exception e) {
            return null;
        }
    }

    public GuiCustom(CustomGui g) {
        this.g = g;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        layout();
        syncInputs();
    }

    private void layout() {
        scale = Math.min(1.0F, Math.min((width - 8) / (float) g.width, (height - 8) / (float) g.height));
        left = (int) ((width / scale - g.width) / 2);
        top = (int) ((height / scale - g.height) / 2);
    }

    private void update(CustomGui n) {
        Map<Integer, String> keep = new HashMap<Integer, String>();
        for (Map.Entry<Integer, GuiTextField> e : inputs.entrySet()) {
            keep.put(e.getKey(), e.getValue().getText());
        }
        g = n;
        layout();
        inputs.clear();
        syncInputs();
        for (Map.Entry<Integer, String> e : keep.entrySet()) {
            GuiTextField f = inputs.get(e.getKey());
            if (f != null) {
                f.setText(e.getValue());
            }
        }
    }

    private void syncInputs() {
        for (CustomGui.El e : g.elements) {
            if ("input".equals(e.kind) && !inputs.containsKey(e.index)) {
                GuiTextField f = new GuiTextField(500 + e.index, fontRenderer, 0, 0, e.w - 6, e.h);
                f.setEnableBackgroundDrawing(false);
                f.setMaxStringLength(200);
                f.setText(e.text);
                f.setTextColor(0xFF000000 | e.color);
                inputs.put(e.index, f);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (!closedByServer) {
            ClientV2.send("cgui_closed", new NBTTagCompound());
        }
    }

    private int mx(int mx) {
        return (int) (mx / scale) - left;
    }

    private int my(int my) {
        return (int) (my / scale) - top;
    }

    private static boolean inside(CustomGui.El e, int x, int y) {
        return x >= e.x && x < e.x + e.w && y >= e.y && y < e.y + e.h;
    }

    private static boolean clickable(CustomGui.El e) {
        return "button".equals(e.kind) || "item".equals(e.kind) || "image".equals(e.kind);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (g.dim) {
            drawDefaultBackground();
        }
        int x = mx(mouseX);
        int y = my(mouseY);
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1);
        GuiRender.window(g, left, top, false);
        CustomGui.El hovered = null;
        for (CustomGui.El e : g.elements) {
            boolean hov = inside(e, x, y);
            if (hov) {
                hovered = e;
            }
            GuiRender.element(e, left, top, hov && clickable(e), false);
            if ("input".equals(e.kind)) {
                GuiTextField f = inputs.get(e.index);
                if (f != null) {
                    f.x = left + e.x + 4;
                    f.y = top + e.y + (e.h - 8) / 2;
                    f.width = e.w - 8;
                    f.drawTextBox();
                    if (f.getText().isEmpty() && !f.isFocused() && !e.tooltip.isEmpty()) {
                        fontRenderer.drawString("§8" + e.tooltip, f.x, f.y, 0xFFFFFF);
                    }
                }
            }
        }
        GlStateManager.popMatrix();
        if (hovered != null) {
            List<String> tip = new ArrayList<String>();
            if (!hovered.tooltip.isEmpty() && !"input".equals(hovered.kind)) {
                tip.addAll(GuiRender.lines(hovered.tooltip, 200));
            } else if ("item".equals(hovered.kind)) {
                ItemStack st = GuiRender.stack(hovered.item);
                if (!st.isEmpty()) {
                    renderToolTip(st, mouseX, mouseY);
                }
            }
            if (!tip.isEmpty()) {
                drawHoveringText(tip, mouseX, mouseY);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        int x = mx(mouseX);
        int y = my(mouseY);
        for (GuiTextField f : inputs.values()) {
            f.mouseClicked((int) (mouseX / scale), (int) (mouseY / scale), button);
        }
        if (button != 0) {
            return;
        }
        if (g.closeButton && x >= g.width - 13 && x < g.width && y >= 0 && y < 14) {
            mc.displayGuiScreen(null);
            return;
        }
        for (int i = g.elements.size() - 1; i >= 0; i--) {
            CustomGui.El e = g.elements.get(i);
            if (clickable(e) && inside(e, x, y)) {
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                        net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                NBTTagCompound d = new NBTTagCompound();
                d.setString("id", g.id);
                d.setInteger("i", e.index);
                NBTTagCompound in = new NBTTagCompound();
                for (Map.Entry<Integer, GuiTextField> f : inputs.entrySet()) {
                    in.setString(Integer.toString(f.getKey()), f.getValue().getText());
                }
                d.setTag("in", in);
                ClientV2.send("cgui_click", d);
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char c, int code) throws IOException {
        for (GuiTextField f : inputs.values()) {
            if (f.isFocused()) {
                if (code == Keyboard.KEY_ESCAPE || code == Keyboard.KEY_RETURN) {
                    f.setFocused(false);
                } else {
                    f.textboxKeyTyped(c, code);
                }
                return;
            }
        }
        super.keyTyped(c, code);
    }

    @Override
    public void updateScreen() {
        for (GuiTextField f : inputs.values()) {
            f.updateCursorCounter();
        }
    }
}
