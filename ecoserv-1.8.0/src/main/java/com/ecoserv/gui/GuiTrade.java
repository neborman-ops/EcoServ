package com.ecoserv.gui;

import com.ecoserv.Ref;
import com.ecoserv.client.ClientCache;
import com.ecoserv.client.Lang;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketTradeAction;
import com.ecoserv.trade.ContainerTrade;
import com.ecoserv.util.Fmt;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Окно обмена между игроками. */
@SideOnly(Side.CLIENT)
public class GuiTrade extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Ref.MODID, "textures/gui/trade.png");

    private static final int ID_SET_MONEY = 1;
    private static final int ID_CONFIRM = 2;
    private static final int ID_CANCEL = 3;

    private GuiTextField moneyField;
    private GuiButton confirmButton;

    public GuiTrade(ContainerTrade container) {
        super(container);
        this.xSize = 176;
        this.ySize = 222;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        buttonList.add(new GuiButton(ID_SET_MONEY, guiLeft + 68, guiTop + 87, 22, 16, Lang.get("ecoserv.gui.ok")));
        confirmButton = new GuiButton(ID_CONFIRM, guiLeft + 8, guiTop + 108, 80, 20,
                Lang.get("ecoserv.gui.trade.confirm"));
        buttonList.add(confirmButton);
        buttonList.add(new GuiButton(ID_CANCEL, guiLeft + 92, guiTop + 108, 76, 20,
                Lang.get("ecoserv.gui.trade.cancel")));

        // при изменении размера окна введённая сумма и фокус больше не сбрасываются
        String typed = moneyField == null ? null : moneyField.getText();
        boolean focused = moneyField != null && moneyField.isFocused();
        moneyField = new GuiTextField(0, fontRenderer, guiLeft + 8, guiTop + 88, 56, 14);
        moneyField.setMaxStringLength(16);
        moneyField.setText(typed != null ? typed : Long.toString(ClientCache.tradeState.ownMoney));
        moneyField.setFocused(focused);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        ClientCache.tradeState.reset();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (moneyField != null) {
            moneyField.updateCursorCounter();
        }
        if (confirmButton != null) {
            confirmButton.displayString = Lang.get(ClientCache.tradeState.ownConfirm
                    ? "ecoserv.gui.trade.unconfirm" : "ecoserv.gui.trade.confirm");
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_SET_MONEY:
                PacketHandler.sendToServer(new PacketTradeAction(PacketTradeAction.SET_MONEY,
                        Math.max(0L, GuiUtil.parseLong(moneyField.getText(), 0L))));
                break;
            case ID_CONFIRM:
                PacketHandler.sendToServer(new PacketTradeAction(
                        ClientCache.tradeState.ownConfirm ? PacketTradeAction.UNCONFIRM : PacketTradeAction.CONFIRM, 0L));
                break;
            case ID_CANCEL:
                PacketHandler.sendToServer(new PacketTradeAction(PacketTradeAction.CANCEL, 0L));
                mc.displayGuiScreen(null);
                break;
            default:
                super.actionPerformed(button);
                break;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (moneyField != null) {
            moneyField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (moneyField != null && moneyField.isFocused() && keyCode != Keyboard.KEY_ESCAPE) {
            if (keyCode == Keyboard.KEY_RETURN) {
                PacketHandler.sendToServer(new PacketTradeAction(PacketTradeAction.SET_MONEY,
                        Math.max(0L, GuiUtil.parseLong(moneyField.getText(), 0L))));
                return;
            }
            moneyField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (moneyField != null) {
            moneyField.drawTextBox();
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
        ClientCache.TradeState state = ClientCache.tradeState;

        // заголовок над окном: раньше он рисовался поверх строк «Ты»/партнёр
        fontRenderer.drawStringWithShadow(Lang.get("ecoserv.gui.trade.title"), 2.0F, -10.0F, 0xFFFFFF);

        String you = Lang.get("ecoserv.gui.trade.you");
        String them = state.otherName == null || state.otherName.isEmpty()
                ? Lang.get("ecoserv.gui.trade.partner") : state.otherName;
        fontRenderer.drawString("§2" + you + (state.ownConfirm ? " §a✔" : ""), 8, 8, 0x404040);
        fontRenderer.drawString("§1" + GuiUtil.trim(them, 60) + (state.otherConfirm ? " §a✔" : ""),
                98, 8, 0x404040);

        fontRenderer.drawString("§7" + Lang.get("ecoserv.gui.trade.money"), 8, 78, 0x404040);
        fontRenderer.drawString("§6" + Fmt.money(state.otherMoney), 98, 90, 0x404040);

        String status;
        if (state.ownConfirm && state.otherConfirm) {
            status = "§a" + Lang.get("ecoserv.gui.trade.both_ready");
        } else if (state.otherConfirm) {
            status = "§e" + Lang.get("ecoserv.gui.trade.waiting_you");
        } else if (state.ownConfirm) {
            status = "§e" + Lang.get("ecoserv.gui.trade.waiting_other");
        } else {
            status = "§7" + Lang.get("ecoserv.gui.trade.hint");
        }
        fontRenderer.drawString(GuiUtil.trim(status, 160), 8, 131, 0x404040);
    }
}
