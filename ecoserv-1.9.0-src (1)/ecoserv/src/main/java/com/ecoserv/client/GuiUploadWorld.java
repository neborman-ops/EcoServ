package com.ecoserv.client;

import com.ecoserv.util.Fmt;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Выбор своего одиночного мира и загрузка его на сервер (админ, тип подмира «загрузить свою»). */
@SideOnly(Side.CLIENT)
public class GuiUploadWorld extends GuiScreen {

    private static final int ROWS = 8;
    private static final int ROW_H = 22;
    private static final int MAX_DATA_VERSION = 1343;

    private static final class Entry {
        File dir;
        String levelName = "";
        long size;
        String problem;
    }

    private final long maxBytes;
    private final List<Entry> worlds = new ArrayList<Entry>();
    private int scroll;
    private int selected = -1;
    private GuiButton uploadButton;
    private GuiButton cancelButton;
    private int listTop;
    private long speedMark;
    private long speedBytes;
    private String speed = "";

    public GuiUploadWorld(long maxBytes) {
        this.maxBytes = maxBytes;
        scan();
    }

    private void scan() {
        worlds.clear();
        File saves = new File(net.minecraft.client.Minecraft.getMinecraft().gameDir, "saves");
        File[] dirs = saves.listFiles();
        if (dirs == null) {
            return;
        }
        for (File dir : dirs) {
            File level = new File(dir, "level.dat");
            if (!dir.isDirectory() || !level.isFile()) {
                continue;
            }
            Entry e = new Entry();
            e.dir = dir;
            e.size = level.length();
            File[] mca = new File(dir, "region").listFiles();
            int regions = 0;
            if (mca != null) {
                for (File f : mca) {
                    if (f.getName().endsWith(".mca")) {
                        e.size += f.length();
                        regions++;
                    }
                }
            }
            try (InputStream in = new FileInputStream(level)) {
                NBTTagCompound data = CompressedStreamTools.readCompressed(in).getCompoundTag("Data");
                e.levelName = data.getString("LevelName");
                if (data.getInteger("DataVersion") > MAX_DATA_VERSION) {
                    e.problem = Lang.get("ecoserv.upload.too_new");
                }
            } catch (Exception ex) {
                e.problem = Lang.get("ecoserv.upload.broken");
            }
            if (e.problem == null && regions == 0) {
                e.problem = Lang.get("ecoserv.upload.empty");
            }
            if (e.problem == null && e.size > maxBytes) {
                e.problem = Lang.get("ecoserv.upload.too_big") + " " + Fmt.bytes(maxBytes);
            }
            worlds.add(e);
        }
        worlds.sort((a, b) -> Long.compare(b.dir.lastModified(), a.dir.lastModified()));
    }

    @Override
    public void initGui() {
        buttonList.clear();
        listTop = 40;
        int bottom = listTop + ROWS * ROW_H + 10;
        uploadButton = new GuiButton(0, width / 2 - 154, bottom, 100, 20, Lang.get("ecoserv.upload.start"));
        cancelButton = new GuiButton(1, width / 2 - 50, bottom, 100, 20, Lang.get("ecoserv.upload.cancel"));
        buttonList.add(uploadButton);
        buttonList.add(cancelButton);
        buttonList.add(new GuiButton(2, width / 2 + 54, bottom, 100, 20, Lang.get("ecoserv.gui.close")));
        updateButtons();
    }

    private void updateButtons() {
        boolean busy = UploadSender.busy();
        uploadButton.enabled = !busy && selected >= 0 && selected < worlds.size() && worlds.get(selected).problem == null;
        cancelButton.enabled = busy;
    }

    @Override
    public void updateScreen() {
        updateButtons();
        UploadSender s = UploadSender.current();
        if (s != null && !s.finished) {
            long now = System.currentTimeMillis();
            if (speedMark == 0L) {
                speedMark = now;
                speedBytes = s.sent;
            } else if (now - speedMark >= 1000L) {
                speed = Fmt.bytes((s.sent - speedBytes) * 1000L / Math.max(1L, now - speedMark)) + "/s";
                speedMark = now;
                speedBytes = s.sent;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                if (selected >= 0 && selected < worlds.size() && !UploadSender.busy()) {
                    Entry e = worlds.get(selected);
                    speedMark = 0L;
                    speed = "";
                    UploadSender.start(e.dir, e.dir.getName());
                }
                break;
            case 1:
                UploadSender.cancelCurrent();
                break;
            case 2:
                mc.displayGuiScreen(null);
                break;
            default:
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, Math.min(Math.max(0, worlds.size() - ROWS), scroll + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        int left = width / 2 - 154;
        if (mouseX >= left && mouseX < left + 308 && mouseY >= listTop && mouseY < listTop + ROWS * ROW_H) {
            int index = scroll + (mouseY - listTop) / ROW_H;
            if (index < worlds.size() && !UploadSender.busy()) {
                selected = index;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, Lang.get("ecoserv.upload.title"), width / 2, 12, 0xFFFFFF);
        drawCenteredString(fontRenderer, Lang.get("ecoserv.upload.hint") + " " + Fmt.bytes(maxBytes), width / 2, 24, 0xA0A0A0);
        int left = width / 2 - 154;
        drawRect(left, listTop, left + 308, listTop + ROWS * ROW_H, 0x80000000);
        for (int i = 0; i < ROWS && scroll + i < worlds.size(); i++) {
            Entry e = worlds.get(scroll + i);
            int y = listTop + i * ROW_H;
            if (scroll + i == selected) {
                drawRect(left, y, left + 308, y + ROW_H, 0x60FFFFFF);
            }
            String title = e.dir.getName() + (e.levelName.isEmpty() || e.levelName.equals(e.dir.getName()) ? "" : " §7(" + e.levelName + ")");
            fontRenderer.drawString(title, left + 4, y + 3, e.problem == null ? 0xFFFFFF : 0x909090);
            fontRenderer.drawString(e.problem == null ? "§a" + Fmt.bytes(e.size) : "§c" + e.problem, left + 4, y + 12, 0xFFFFFF);
        }
        if (worlds.isEmpty()) {
            drawCenteredString(fontRenderer, Lang.get("ecoserv.upload.no_worlds"), width / 2, listTop + 40, 0xA0A0A0);
        }
        UploadSender s = UploadSender.current();
        if (s != null) {
            int y = listTop + ROWS * ROW_H + 36;
            int barW = 308;
            float part = s.total <= 0 ? 0.0F : Math.min(1.0F, s.sent / (float) s.total);
            drawRect(left, y, left + barW, y + 8, 0xFF303030);
            drawRect(left, y, left + (int) (barW * part), y + 8, s.failed ? 0xFFC03030 : 0xFF30C050);
            String line = s.name + ": " + Fmt.bytes(s.sent) + " / " + Fmt.bytes(s.total) + " (" + (int) (part * 100) + "%)"
                    + (s.finished ? "" : "  " + speed);
            drawCenteredString(fontRenderer, line, width / 2, y + 12, 0xFFFFFF);
            drawCenteredString(fontRenderer, s.status, width / 2, y + 24, s.failed ? 0xFF6060 : 0xA0FFA0);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
