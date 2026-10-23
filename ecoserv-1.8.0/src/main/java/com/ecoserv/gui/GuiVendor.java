package com.ecoserv.gui;

import com.ecoserv.Ref;
import com.ecoserv.auction.AuctionManager;
import com.ecoserv.auction.Listing;
import com.ecoserv.client.ClientCache;
import com.ecoserv.client.Lang;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketAuctionAction;
import com.ecoserv.tile.TileVendor;
import com.ecoserv.util.Fmt;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

/** Окно склада продавца: сложил товар, поставил цену, выставил на аукцион. */
@SideOnly(Side.CLIENT)
public class GuiVendor extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Ref.MODID, "textures/gui/vendor.png");

    private static final int ID_LIST = 1;
    private static final int ID_CANCEL_ALL = 2;
    private static final int ID_AUCTION = 3;

    private final TileVendor vendor;
    private GuiTextField priceField;

    public GuiVendor(ContainerVendor container, TileVendor vendor) {
        super(container);
        this.vendor = vendor;
        this.xSize = 176;
        this.ySize = 244;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        buttonList.add(new GuiButton(ID_LIST, guiLeft + 68, guiTop + 97, 100, 20,
                Lang.get("ecoserv.gui.vendor.list")));
        buttonList.add(new GuiButton(ID_CANCEL_ALL, guiLeft + 8, guiTop + 121, 80, 20,
                Lang.get("ecoserv.gui.vendor.unlist")));
        buttonList.add(new GuiButton(ID_AUCTION, guiLeft + 92, guiTop + 121, 76, 20,
                Lang.get("ecoserv.gui.tab.auction")));

        priceField = new GuiTextField(0, fontRenderer, guiLeft + 8, guiTop + 99, 56, 16);
        priceField.setMaxStringLength(12);
        priceField.setText(Fmt.price(vendor.getPrice()));
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
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_LIST:
                PacketHandler.sendToServer(new PacketAuctionAction(PacketAuctionAction.LIST_FROM_BLOCK,
                        0L, 0, vendor.getPos().toLong(),
                        Math.max(0.0D, GuiUtil.parseDouble(priceField.getText(), vendor.getPrice()))));
                break;
            case ID_CANCEL_ALL:
                PacketHandler.sendToServer(new PacketAuctionAction(PacketAuctionAction.CANCEL_FROM_BLOCK,
                        0L, 0, vendor.getPos().toLong(), 0.0D));
                break;
            case ID_AUCTION:
                com.ecoserv.client.InventoryTabHandler.openShopTab("AUCTION");
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
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (priceField != null && priceField.isFocused() && keyCode != Keyboard.KEY_ESCAPE) {
            priceField.textboxKeyTyped(typedChar, keyCode);
            return;
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
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiUtil.bind(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(Lang.get("tile.ecoserv.vendor.name"), 8, 6, 0x404040);

        String balance = "§7" + Lang.get("ecoserv.gui.balance") + ": §2" + Fmt.money(ClientCache.balance);
        fontRenderer.drawString(balance, xSize - 8 - fontRenderer.getStringWidth(balance), 6, 0x404040);

        int lots = 0;
        int items = 0;
        long posLong = vendor.getPos().toLong();
        List<Listing> listings = AuctionManager.all();
        for (int i = 0; i < listings.size(); i++) {
            if (listings.get(i).pos == posLong) {
                lots++;
                items += listings.get(i).count;
            }
        }

        fontRenderer.drawString(Lang.get("ecoserv.gui.owner") + ": §0"
                + (vendor.getOwnerName().isEmpty() ? Lang.get("ecoserv.gui.no_owner") : vendor.getOwnerName()),
                8, 74, 0x404040);
        fontRenderer.drawString(Lang.get("ecoserv.gui.vendor.listed") + ": §6" + lots
                + " §0(" + items + ") §0| " + Lang.get("ecoserv.gui.earned") + ": §2"
                + Fmt.money(vendor.getEarnedTotal()), 8, 85, 0x404040);

        fontRenderer.drawString("§7" + Lang.get("ecoserv.gui.vendor.price"), 8, 143, 0x404040);
        fontRenderer.drawString("§8" + GuiUtil.trim(Lang.get("ecoserv.gui.vendor.back_hint"), 160), 8, 152, 0x404040);
    }
}
