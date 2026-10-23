package com.ecoserv.gui;

import com.ecoserv.Ref;
import com.ecoserv.client.ClientCache;
import com.ecoserv.client.Lang;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketVendingAction;
import com.ecoserv.tile.TileVending;
import com.ecoserv.util.Fmt;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Окно блока продажи (виден только владельцу). */
@SideOnly(Side.CLIENT)
public class GuiVending extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Ref.MODID, "textures/gui/vending.png");

    private static final int ID_SAVE = 1;
    private static final int ID_WITHDRAW = 2;

    private final TileVending vending;
    private GuiTextField priceField;
    private GuiTextField amountField;

    public GuiVending(ContainerVending container, TileVending vending) {
        super(container);
        this.vending = vending;
        this.xSize = 176;
        this.ySize = 240;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        buttonList.add(new GuiButton(ID_SAVE, guiLeft + 8, guiTop + 132, 80, 20, Lang.get("ecoserv.gui.save")));
        buttonList.add(new GuiButton(ID_WITHDRAW, guiLeft + 92, guiTop + 132, 76, 20,
                Lang.get("ecoserv.gui.vending.withdraw")));

        priceField = new GuiTextField(0, fontRenderer, guiLeft + 8, guiTop + 41, 58, 14);
        priceField.setMaxStringLength(12);
        priceField.setText(Long.toString(vending.getPrice()));

        amountField = new GuiTextField(1, fontRenderer, guiLeft + 72, guiTop + 41, 30, 14);
        amountField.setMaxStringLength(3);
        amountField.setText(Integer.toString(vending.getAmount()));
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (priceField != null) {
            priceField.updateCursorCounter();
        }
        if (amountField != null) {
            amountField.updateCursorCounter();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_SAVE:
                PacketHandler.sendToServer(new PacketVendingAction(PacketVendingAction.SAVE,
                        vending.getPos().toLong(),
                        Math.max(0L, GuiUtil.parseLong(priceField.getText(), vending.getPrice())),
                        Math.max(1, GuiUtil.parseInt(amountField.getText(), vending.getAmount()))));
                break;
            case ID_WITHDRAW:
                PacketHandler.sendToServer(new PacketVendingAction(PacketVendingAction.WITHDRAW,
                        vending.getPos().toLong(), 0L, 0));
                break;
            default:
                super.actionPerformed(button);
                break;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (priceField != null) {
            priceField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (amountField != null) {
            amountField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode != Keyboard.KEY_ESCAPE) {
            if (priceField != null && priceField.isFocused()) {
                priceField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
            if (amountField != null && amountField.isFocused()) {
                amountField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (priceField != null) {
            priceField.drawTextBox();
        }
        if (amountField != null) {
            amountField.drawTextBox();
        }
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiUtil.bind(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(Lang.get("tile.ecoserv.vending.name"), 8, 6, 0xE8EAF0);

        String balance = "§7" + Lang.get("ecoserv.gui.balance") + ": §a" + Fmt.money(ClientCache.balance);
        fontRenderer.drawString(balance, xSize - 8 - fontRenderer.getStringWidth(balance), 6, 0xE8EAF0);

        fontRenderer.drawString("§7" + Lang.get("ecoserv.gui.vending.sample"), 30, 23, 0xE8EAF0);
        fontRenderer.drawString("§7" + Lang.get("ecoserv.gui.vending.fields"), 106, 45, 0xE8EAF0);

        String stock = vending.isAdminShop()
                ? "§d" + Lang.get("ecoserv.gui.vending.admin")
                : "§7" + Lang.get("ecoserv.gui.vending.stock") + ": §f" + vending.countStock();
        fontRenderer.drawString(stock, 8, 120, 0xE8EAF0);

        String cash = "§7" + Lang.get("ecoserv.gui.vending.cash") + ": §a"
                + Fmt.money(vending.getStoredMoney()) + " §7| " + Lang.get("ecoserv.gui.vending.sold")
                + ": §f" + vending.getSoldTotal();
        fontRenderer.drawString(GuiUtil.trim(cash, 160), 8, 108, 0xE8EAF0);
    }
}
