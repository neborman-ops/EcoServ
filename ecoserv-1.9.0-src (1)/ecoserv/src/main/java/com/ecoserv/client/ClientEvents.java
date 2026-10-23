package com.ecoserv.client;

import com.ecoserv.client.ui.AnimSprite;
import com.ecoserv.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

/**
 * Клиентские мелочи 1.9.0: шестерёнка клиентских настроек у инвентаря, отправка настроек серверу при входе,
 * скрытие своего инвентаря в меню-сундуках мода (если включено).
 */
@SideOnly(Side.CLIENT)
public class ClientEvents {

    public static final int GEAR_BUTTON_ID = 47113;
    /** Меню-сундуки мода открываются с этим id окна (ChestMenu). */
    private static final int MOD_CHEST_WINDOW = 101;

    /** Кнопка-шестерёнка 16×16 (крутится при наведении). */
    static final class GearButton extends GuiButton {
        GearButton(int x, int y) {
            super(GEAR_BUTTON_ID, x, y, 16, 16, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) {
                return;
            }
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            AnimSprite.GEAR.draw(x, y, hovered);
            if (hovered) {
                mc.fontRenderer.drawStringWithShadow(ClientSettings.t("Клиентские настройки"), x + 18, y + 4, 0xFFFFA0);
            }
        }
    }

    /** Открыть настройки на следующем тике (после команды чат закроется сам). */
    public static boolean openNextTick;

    private static String sentLanguage = "";
    private static int langCheck;

    @SubscribeEvent
    public void onTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END && mc.player != null
                && ++langCheck >= 100) {
            // язык игры поменяли в настройках Minecraft — сообщить серверу
            langCheck = 0;
            String lang = String.valueOf(mc.gameSettings.language);
            if (!lang.equals(sentLanguage)) {
                sentLanguage = lang;
                ClientHooks.sendPrefs();
            }
        }
        if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END && openNextTick
                && Minecraft.getMinecraft().currentScreen == null) {
            openNextTick = false;
            Minecraft.getMinecraft().displayGuiScreen(new GuiClientSettings(null));
        }
    }

    /** Клиентская команда /ecosettings (и /настройки). */
    public static final class Command extends net.minecraft.command.CommandBase {
        @Override
        public String getName() {
            return "ecosettings";
        }

        @Override
        public java.util.List<String> getAliases() {
            return java.util.Collections.singletonList("настройки");
        }

        @Override
        public String getUsage(net.minecraft.command.ICommandSender sender) {
            return "/ecosettings — клиентские настройки EcoServ";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean checkPermission(net.minecraft.server.MinecraftServer server, net.minecraft.command.ICommandSender sender) {
            return true;
        }

        @Override
        public void execute(net.minecraft.server.MinecraftServer server, net.minecraft.command.ICommandSender sender, String[] args) {
            openNextTick = true;
        }
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote && event.getEntity() == Minecraft.getMinecraft().player) {
            ClientHooks.sendPrefs();
        }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiInventory || gui instanceof GuiContainerCreative)) {
            return;
        }
        GuiContainer c = (GuiContainer) gui;
        // справа над инвентарём, рядом с кнопками мода
        int x = c.getGuiLeft() + c.getXSize() - 18;
        int y = c.getGuiTop() - 18;
        event.getButtonList().add(new GearButton(x, y));
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton() != null && event.getButton().id == GEAR_BUTTON_ID) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiClientSettings(event.getGui()));
        }
    }

    // --- скрыть свой инвентарь в меню-сундуках мода ------------------------------------------------------

    private static boolean modChest(GuiScreen gui) {
        return gui instanceof GuiChest && ((GuiChest) gui).inventorySlots != null
                && ((GuiChest) gui).inventorySlots.windowId == MOD_CHEST_WINDOW
                && ClientSettings.get().hideInventorySlots;
    }

    /** Область своего инвентаря в окне сундука: {x, y, w, h}. */
    private static int[] playerArea(GuiChest gui) {
        int rows = (gui.getYSize() - 114) / 18;
        int x = gui.getGuiLeft();
        int y = gui.getGuiTop() + 17 + rows * 18 + 1;
        return new int[]{x, y, gui.getXSize(), gui.getGuiTop() + gui.getYSize() - y};
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!modChest(event.getGui())) {
            return;
        }
        int[] a = playerArea((GuiChest) event.getGui());
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        UiTheme.panel(a[0] + 3, a[1], a[2] - 6, a[3] - 3, UiTheme.WINDOW, UiTheme.BORDER);
        UiTheme.center("§7" + ClientSettings.t("Инвентарь скрыт"), a[0] + a[2] / 2, a[1] + a[3] / 2 - 9, UiTheme.DIM);
        UiTheme.center("§8(" + ClientSettings.t("клиентские настройки") + ")", a[0] + a[2] / 2, a[1] + a[3] / 2 + 2, UiTheme.MUTED);
        GlStateManager.enableDepth();
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!modChest(event.getGui()) || !Mouse.getEventButtonState()) {
            return;
        }
        GuiScreen gui = event.getGui();
        int mx = Mouse.getEventX() * gui.width / Minecraft.getMinecraft().displayWidth;
        int my = gui.height - Mouse.getEventY() * gui.height / Minecraft.getMinecraft().displayHeight - 1;
        int[] a = playerArea((GuiChest) gui);
        if (mx >= a[0] && mx < a[0] + a[2] && my >= a[1] && my < a[1] + a[3]) {
            event.setCanceled(true);
        }
    }
}
