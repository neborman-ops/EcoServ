package com.ecoserv.client;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.gui.GuiTabButton;
import com.ecoserv.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

/**
 * Две кнопки рядом с инвентарём: магазин/аукцион и отдельно обмен.
 * Работают и в обычном, и в креативном инвентаре.
 * Shift + перетаскивание двигает кнопку, позиция сохраняется в конфиг.
 */
@SideOnly(Side.CLIENT)
public class InventoryTabHandler {

    public static final int SHOP_BUTTON_ID = 47110;
    public static final int TRADE_BUTTON_ID = 47111;
    public static final int WORLDS_BUTTON_ID = 47112;

    private GuiTabButton shopButton;
    private GuiTabButton tradeButton;
    private com.ecoserv.gui.GuiPlanetButton worldsButton;
    private GuiButton dragged;
    /** Окно, в которое добавлены наши кнопки. */
    private GuiScreen owner;
    private boolean wasDown;
    private int dragOffsetX;
    private int dragOffsetY;

    private static boolean supported(GuiScreen gui) {
        return gui instanceof GuiInventory || gui instanceof GuiContainerCreative;
    }

    private static boolean screenMode() {
        return !"inventory".equalsIgnoreCase(EcoConfig.tabMode);
    }

    public static void openShop() {
        openShopTab("AUCTION");
    }

    public static void openTrade() {
        openShopTab("PLAYERS");
    }

    /** Окно магазина теперь серверное (как меню миров): просим сервер открыть нужную вкладку. */
    public static void openShopTab(String tab) {
        PacketHandler.sendToServer(new com.ecoserv.network.packet.PacketSubworldAction(
                com.ecoserv.network.packet.PacketSubworldAction.OPEN_SHOP, tab));
    }

    /** Меню миров открывает сервер (серверное меню-сундук). */
    public static void openWorlds() {
        PacketHandler.sendToServer(new com.ecoserv.network.packet.PacketSubworldAction(
                com.ecoserv.network.packet.PacketSubworldAction.OPEN_MENU, ""));
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() != Minecraft.getMinecraft().currentScreen) {
            // В творческом режиме обычный инвентарь сразу подменяется творческим (внутри своего initGui),
            // а событие для него приходит уже ПОСЛЕ творческого. Раньше кнопки пересоздавались для невидимого
            // окна — тащили невидимую кнопку, а видимая стояла на месте до переоткрытия инвентаря.
            return;
        }
        shopButton = null;
        tradeButton = null;
        worldsButton = null;
        dragged = null;
        owner = event.getGui();
        if (!supported(event.getGui())) {
            return;
        }
        GuiContainer gui = (GuiContainer) event.getGui();
        if (EcoConfig.showWorldsButton) {
            int wx;
            int wy;
            if (screenMode()) {
                wx = clamp(EcoConfig.worldsTabX, gui.width - com.ecoserv.gui.GuiPlanetButton.SIZE);
                wy = clamp(EcoConfig.worldsTabY, gui.height - com.ecoserv.gui.GuiPlanetButton.SIZE);
            } else {
                wx = gui.getGuiLeft() + 54 + EcoConfig.inventoryTabOffsetX;
                wy = gui.getGuiTop() - 34 + EcoConfig.inventoryTabOffsetY;
            }
            worldsButton = new com.ecoserv.gui.GuiPlanetButton(WORLDS_BUTTON_ID, wx, wy);
            event.getButtonList().add(worldsButton);
        }
        if (!EcoConfig.showInventoryTab) {
            return;
        }

        int x;
        int y;
        if (screenMode()) {
            x = clamp(EcoConfig.tabX, gui.width - GuiTabButton.SIZE);
            y = clamp(EcoConfig.tabY, gui.height - GuiTabButton.SIZE);
        } else {
            x = gui.getGuiLeft() + EcoConfig.inventoryTabOffsetX;
            y = gui.getGuiTop() - 28 + EcoConfig.inventoryTabOffsetY;
        }
        shopButton = new GuiTabButton(SHOP_BUTTON_ID, x, y, 0);
        event.getButtonList().add(shopButton);

        if (EcoConfig.showTradeButton) {
            int tx;
            int ty;
            if (screenMode()) {
                tx = clamp(EcoConfig.tradeTabX, gui.width - GuiTabButton.SIZE);
                ty = clamp(EcoConfig.tradeTabY, gui.height - GuiTabButton.SIZE);
            } else {
                tx = x + GuiTabButton.SIZE + 1;
                ty = y;
            }
            tradeButton = new GuiTabButton(TRADE_BUTTON_ID, tx, ty, 1);
            event.getButtonList().add(tradeButton);
        }
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, Math.max(0, max)));
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiButton pressed = event.getButton();
        if (pressed == null || !supported(event.getGui())) {
            return;
        }
        if (pressed.id != SHOP_BUTTON_ID && pressed.id != TRADE_BUTTON_ID && pressed.id != WORLDS_BUTTON_ID) {
            return;
        }
        event.setCanceled(true);
        if (GuiScreen.isShiftKeyDown() && screenMode()) {
            return;
        }
        if (pressed.id == SHOP_BUTTON_ID) {
            openShop();
        } else if (pressed.id == WORLDS_BUTTON_ID) {
            openWorlds();
        } else {
            openTrade();
        }
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        boolean down = Mouse.isButtonDown(0);
        if ((shopButton == null && worldsButton == null) || event.getGui() != owner || !supported(event.getGui())
                || !screenMode()) {
            wasDown = down;
            return;
        }
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        GuiButton hovered = hovered(mouseX, mouseY);
        if (down && !wasDown && hovered != null && GuiScreen.isShiftKeyDown()) {
            dragged = hovered;
            dragOffsetX = mouseX - dragged.x;
            dragOffsetY = mouseY - dragged.y;
        }

        if (dragged != null) {
            if (down) {
                dragged.x = clamp(mouseX - dragOffsetX, event.getGui().width - dragged.width);
                dragged.y = clamp(mouseY - dragOffsetY, event.getGui().height - dragged.height);
            } else {
                if (dragged == shopButton) {
                    EcoConfig.saveTabPosition(dragged.x, dragged.y);
                } else if (dragged == worldsButton) {
                    EcoConfig.saveWorldsTabPosition(dragged.x, dragged.y);
                } else {
                    EcoConfig.saveTradeTabPosition(dragged.x, dragged.y);
                }
                dragged = null;
            }
        }
        wasDown = down;

        if (hovered != null && dragged == null) {
            Minecraft mc = Minecraft.getMinecraft();
            String key = hovered == shopButton ? "ecoserv.gui.tab.auction"
                    : hovered == worldsButton ? "ecoserv.gui.tab.worlds" : "ecoserv.gui.tab.players";
            String hint = Lang.get(key) + " §7(" + Lang.get("ecoserv.gui.drag_hint") + ")";
            mc.fontRenderer.drawStringWithShadow(hint, hovered.x + hovered.width + 2, hovered.y + hovered.height / 2 - 4, 0xFFFFA0);
        }
    }

    private GuiButton hovered(int mouseX, int mouseY) {
        if (inside(shopButton, mouseX, mouseY)) {
            return shopButton;
        }
        if (inside(tradeButton, mouseX, mouseY)) {
            return tradeButton;
        }
        if (inside(worldsButton, mouseX, mouseY)) {
            return worldsButton;
        }
        return null;
    }

    private static boolean inside(GuiButton button, int mouseX, int mouseY) {
        return button != null && mouseX >= button.x && mouseX < button.x + button.width
                && mouseY >= button.y && mouseY < button.y + button.height;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KeyBindings.openShop != null && KeyBindings.openShop.isPressed()) {
            openShop();
        }
        if (KeyBindings.openTrade != null && KeyBindings.openTrade.isPressed()) {
            openTrade();
        }
        if (KeyBindings.openSettings != null && KeyBindings.openSettings.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiClientSettings(Minecraft.getMinecraft().currentScreen));
        }
        if (KeyBindings.openWorlds != null && KeyBindings.openWorlds.isPressed()) {
            openWorlds();
        }
    }
}
