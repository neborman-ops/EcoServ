package com.ecoserv.gui;

import com.ecoserv.Ref;
import com.ecoserv.client.ClientCache;
import com.ecoserv.client.Lang;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketMarketAction;
import com.ecoserv.shop.ShopService;
import com.ecoserv.tile.TileMarket;
import com.ecoserv.util.Fmt;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Окно блока-скупщика. */
@SideOnly(Side.CLIENT)
public class GuiMarket extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Ref.MODID, "textures/gui/market.png");

    private static final int ID_SELL = 1;
    private static final int ID_AUTO = 2;
    private static final int ID_OWNER_SELF = 3;
    private static final int ID_OWNER_SET = 4;
    private static final int ID_PICK = 5;

    private static final int PICK_ROWS = 5;
    private static final int PICK_ROW_H = 14;

    private final TileMarket market;
    private GuiTextField ownerField;
    private GuiButton autoButton;

    private boolean pickOpen;
    private int pickScroll;
    private ShopService.Preview preview;
    private final List<String> online = new ArrayList<String>();

    public GuiMarket(ContainerMarket container, TileMarket market) {
        super(container);
        this.market = market;
        this.xSize = 176;
        this.ySize = 222;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        buttonList.add(new GuiButton(ID_SELL, guiLeft + 8, guiTop + 94, 80, 20, Lang.get("ecoserv.gui.sell_now")));
        autoButton = new GuiButton(ID_AUTO, guiLeft + 92, guiTop + 94, 76, 20, autoLabel());
        buttonList.add(autoButton);

        buttonList.add(new GuiButton(ID_OWNER_SELF, guiLeft + 8, guiTop + 116, 56, 18,
                Lang.get("ecoserv.gui.owner_me")));
        buttonList.add(new GuiButton(ID_PICK, guiLeft + 126, guiTop + 116, 18, 18, "▼"));
        buttonList.add(new GuiButton(ID_OWNER_SET, guiLeft + 146, guiTop + 116, 22, 18,
                Lang.get("ecoserv.gui.ok")));

        ownerField = new GuiTextField(0, fontRenderer, guiLeft + 68, guiTop + 117, 54, 16);
        ownerField.setMaxStringLength(24);
        ownerField.setText(market.getOwnerName());
    }

    private String autoLabel() {
        return Lang.get(market.isAutoSell() ? "ecoserv.gui.auto_on" : "ecoserv.gui.auto_off");
    }

    private void refreshOnline() {
        online.clear();
        NetHandlerPlayClient connection = mc.getConnection();
        if (connection == null) {
            return;
        }
        Collection<NetworkPlayerInfo> infos = connection.getPlayerInfoMap();
        for (NetworkPlayerInfo info : infos) {
            if (info != null && info.getGameProfile() != null && info.getGameProfile().getName() != null) {
                online.add(info.getGameProfile().getName());
            }
        }
        Collections.sort(online, String.CASE_INSENSITIVE_ORDER);
        pickScroll = 0;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (ownerField != null) {
            ownerField.updateCursorCounter();
        }
        if (autoButton != null) {
            autoButton.displayString = autoLabel();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_SELL:
                PacketHandler.sendToServer(new PacketMarketAction(market.getPos(), PacketMarketAction.SELL_NOW, ""));
                break;
            case ID_AUTO:
                PacketHandler.sendToServer(new PacketMarketAction(market.getPos(), PacketMarketAction.TOGGLE_AUTO, ""));
                break;
            case ID_OWNER_SELF:
                PacketHandler.sendToServer(new PacketMarketAction(market.getPos(),
                        PacketMarketAction.SET_OWNER_SELF, ""));
                break;
            case ID_OWNER_SET:
                PacketHandler.sendToServer(new PacketMarketAction(market.getPos(),
                        PacketMarketAction.SET_OWNER, ownerField.getText()));
                break;
            case ID_PICK:
                pickOpen = !pickOpen;
                if (pickOpen) {
                    refreshOnline();
                }
                break;
            default:
                super.actionPerformed(button);
                break;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        if (pickOpen) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                pickScroll += wheel > 0 ? -1 : 1;
                pickScroll = Math.max(0, Math.min(pickScroll, Math.max(0, online.size() - PICK_ROWS)));
                return;
            }
        }
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (pickOpen) {
            int x = guiLeft + 8;
            int y = guiTop + 30;
            int w = 160;
            int h = PICK_ROWS * PICK_ROW_H + 14;
            if (GuiUtil.inside(mouseX, mouseY, x, y, w, h)) {
                int index = (mouseY - (y + 12)) / PICK_ROW_H + pickScroll;
                if (index >= 0 && index < online.size()) {
                    ownerField.setText(online.get(index));
                    pickOpen = false;
                }
                return;
            }
            pickOpen = false;
            return;
        }
        if (ownerField != null) {
            ownerField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (pickOpen && keyCode == Keyboard.KEY_ESCAPE) {
            pickOpen = false;
            return;
        }
        if (ownerField != null && ownerField.isFocused() && keyCode != Keyboard.KEY_ESCAPE) {
            ownerField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        preview = computePreview();
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (ownerField != null) {
            ownerField.drawTextBox();
        }
        if (pickOpen) {
            drawPicker(mouseX, mouseY);
        } else {
            renderHoveredToolTip(mouseX, mouseY);
            drawPreviewTooltip(mouseX, mouseY);
        }
    }

    /** Оценка выручки: одинаковые предметы суммируются, в зачёт идут только полные приёмы. */
    private ShopService.Preview computePreview() {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        for (int i = 0; i < ContainerMarket.MARKET_SLOTS; i++) {
            stacks.add(inventorySlots.getSlot(i).getStack());
        }
        return ShopService.preview(stacks, ClientCache.serverTaxPercent, com.ecoserv.client.ClientShop.list());
    }

    /** Наведение на строку выручки — список того, что ждёт полного приёма. */
    private void drawPreviewTooltip(int mouseX, int mouseY) {
        if (preview == null || preview.waiting.isEmpty()
                || !GuiUtil.inside(mouseX, mouseY, guiLeft + 8, guiTop + 81, xSize - 16, 10)) {
            return;
        }
        List<String> lines = new ArrayList<String>();
        lines.add("§6" + Lang.get("ecoserv.gui.until_batch") + ":");
        for (Map.Entry<String, int[]> e : preview.waiting.entrySet()) {
            lines.add("§f" + e.getKey() + " §7" + e.getValue()[0] + "/" + e.getValue()[1] + " "
                    + Lang.get("ecoserv.unit.pcs"));
        }
        lines.add("§8" + Lang.get("ecoserv.gui.market_wait_hint"));
        drawHoveringText(lines, mouseX, mouseY);
    }

    /** Всплывающий список игроков онлайн для быстрого ввода ника. */
    private void drawPicker(int mouseX, int mouseY) {
        int x = guiLeft + 8;
        int y = guiTop + 30;
        int w = 160;
        int h = PICK_ROWS * PICK_ROW_H + 14;

        GuiUtil.fill(x - 1, y - 1, w + 2, h + 2, 0xFF373737);
        GuiUtil.fill(x, y, w, h, 0xFFC6C6C6);
        fontRenderer.drawString("§8" + Lang.get("ecoserv.gui.pick_title"), x + 4, y + 3, 0x404040);

        if (online.isEmpty()) {
            fontRenderer.drawString("§7-", x + 4, y + 16, 0x808080);
            return;
        }
        for (int i = 0; i < PICK_ROWS; i++) {
            int index = pickScroll + i;
            if (index >= online.size()) {
                break;
            }
            int rowY = y + 12 + i * PICK_ROW_H;
            if (GuiUtil.inside(mouseX, mouseY, x, rowY, w, PICK_ROW_H)) {
                GuiUtil.fill(x, rowY, w, PICK_ROW_H, 0x60FFD700);
            }
            fontRenderer.drawString(GuiUtil.trim(online.get(index), w - 10), x + 4, rowY + 3, 0x202020);
        }
        if (online.size() > PICK_ROWS) {
            fontRenderer.drawString("§8" + (pickScroll + 1) + "-"
                            + Math.min(online.size(), pickScroll + PICK_ROWS) + "/" + online.size(),
                    x + w - 40, y + 3, 0x404040);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiUtil.bind(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(Lang.get("tile.ecoserv.market.name"), 8, 6, 0x404040);

        String owner = market.getOwnerName().isEmpty()
                ? Lang.get("ecoserv.gui.no_owner") : market.getOwnerName();
        fontRenderer.drawString(Lang.get("ecoserv.gui.owner") + ": §0" + owner, 8, 58, 0x404040);
        fontRenderer.drawString(Lang.get("ecoserv.gui.earned") + ": §2"
                + Fmt.money(market.getEarnedTotal()), 8, 70, 0x404040);

        ShopService.Preview p = preview != null ? preview : computePreview();
        String line;
        if (p.batches == 0 && !p.waiting.isEmpty()) {
            // полного приёма ещё нет — показываем, сколько не хватает
            Map.Entry<String, int[]> first = p.waiting.entrySet().iterator().next();
            line = "§6" + Lang.get("ecoserv.gui.until_batch") + ": §f" + GuiUtil.trim(first.getKey(), 80)
                    + " §7" + first.getValue()[0] + "/" + first.getValue()[1]
                    + (p.waiting.size() > 1 ? " §8+" + (p.waiting.size() - 1) : "");
        } else {
            String tax = ClientCache.serverTaxPercent > 0.0D
                    ? " §7(-" + Fmt.price(ClientCache.serverTaxPercent) + "%)" : "";
            line = Lang.get("ecoserv.gui.contents_value") + ": §6~" + Fmt.money(p.net) + tax
                    + (p.waiting.isEmpty() ? "" : " §8(+" + p.waiting.size() + " "
                    + Lang.get("ecoserv.gui.waiting_short") + ")");
        }
        fontRenderer.drawString(GuiUtil.trim(line, xSize - 16), 8, 82, 0x404040);

        String balance = "§7" + Lang.get("ecoserv.gui.balance") + ": §2" + Fmt.money(ClientCache.balance);
        fontRenderer.drawString(balance, xSize - 8 - fontRenderer.getStringWidth(balance), 6, 0x404040);
    }
}
