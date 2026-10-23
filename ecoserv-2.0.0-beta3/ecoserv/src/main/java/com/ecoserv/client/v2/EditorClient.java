package com.ecoserv.client.v2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/** Редактор сцены: клавиша F8, приём сцены от сервера, рисовалка текстур и снятие моделей. */
@SideOnly(Side.CLIENT)
public class EditorClient {

    public static KeyBinding openEditor;

    public static void register() {
        openEditor = new KeyBinding("key.ecoserv.editor", Keyboard.KEY_F8, "key.categories.ecoserv");
        ClientRegistry.registerKeyBinding(openEditor);
        ClientV2.on("ed_scene", d -> {
            EditorState.receive(d);
            Minecraft mc = Minecraft.getMinecraft();
            if (d.getBoolean("open") && !(mc.currentScreen instanceof GuiSceneEditor)) {
                mc.displayGuiScreen(new GuiSceneEditor());
            }
        });
        ClientV2.on("px_edit", d -> Minecraft.getMinecraft().displayGuiScreen(new GuiPixelEditor(d.getString("k"), d.getInteger("size"),
                d.hasKey("png") ? d.getByteArray("png") : null)));
        ClientV2.on("model_capture", ModelCapture::capture);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (openEditor != null && openEditor.isPressed() && Minecraft.getMinecraft().currentScreen == null) {
            ClientV2.send("ed_open", new NBTTagCompound());
        }
    }
}
