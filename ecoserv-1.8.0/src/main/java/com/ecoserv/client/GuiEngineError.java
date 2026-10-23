package com.ecoserv.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Random;

/** Окно ENGINE ERROR (ГЛИТЧ-бездна): заголовок, текст ошибки и кнопка ОК; тексты — из настроек подмира. */
@SideOnly(Side.CLIENT)
public class GuiEngineError extends GuiScreen {

    private static final Random RANDOM = new Random();
    private static final int WIDTH = 260;

    private final String title;
    private final String text;
    private final String button;
    private List<String> lines;
    private int top;
    private int boxHeight;
    private boolean done;

    public GuiEngineError(String title, String text, String button) {
        this.title = title;
        this.text = text == null ? "" : text;
        this.button = button;
    }

    @Override
    public void initGui() {
        lines = fontRenderer.listFormattedStringToWidth(text, WIDTH - 20);
        boxHeight = 44 + lines.size() * 10 + 30;
        top = (height - boxHeight) / 2;
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 40, top + boxHeight - 28, 80, 20, button));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, width, height, 0x60000000, 0x90200020);
        int left = (width - WIDTH) / 2;
        // окно и полоса заголовка
        drawRect(left - 1, top - 1, left + WIDTH + 1, top + boxHeight + 1, 0xFFFF1493);
        drawRect(left, top, left + WIDTH, top + boxHeight, 0xF0101014);
        drawRect(left, top, left + WIDTH, top + 20, 0xFF3A0018);
        // заголовок с «глитчем»: смещённые по цвету копии
        int tx = width / 2 - fontRenderer.getStringWidth(title) / 2;
        int jitter = RANDOM.nextInt(10) == 0 ? RANDOM.nextInt(5) - 2 : 0;
        fontRenderer.drawString(title, tx - 1 + jitter, top + 6, 0x00FFFF);
        fontRenderer.drawString(title, tx + 1 - jitter, top + 6, 0xFF1493);
        fontRenderer.drawString(title, tx, top + 6, 0xFFFFFF);
        fontRenderer.drawString("⚠", left + 6, top + 6, 0xFFD000);
        int y = top + 30;
        for (String line : lines) {
            fontRenderer.drawString(line, left + 10, y, 0xE0E0E0);
            y += 10;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 0) {
            close();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
            close();
        }
    }

    private void close() {
        if (done) {
            return;
        }
        done = true;
        mc.displayGuiScreen(null);
        ClientAbyss.onOk();
    }

    @Override
    public void onGuiClosed() {
        // окно закрыли иначе (смерть, смена мира) — считаем, что ОК нажат
        if (!done) {
            done = true;
            ClientAbyss.onOk();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
