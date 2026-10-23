package com.ecoserv.scene;

import com.ecoserv.dev.DevTools;
import com.ecoserv.dev.StepCatalog;
import com.ecoserv.dev.StepCatalog.Def;
import com.ecoserv.lighting.LightingManager;
import com.ecoserv.lighting.LightingProfile;
import com.ecoserv.lighting.LightingScreen;
import com.ecoserv.panel.PanelManager;
import com.ecoserv.panel.PanelScreen;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.Ui;
import com.ecoserv.util.Perms;
import com.ecoserv.v2.Features;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Подключение 2.0 beta3: редактор сцены, освещение, 3D-панели, свой контент. */
public final class Beta3Feature {

    private Beta3Feature() {
    }

    public static void init() {
        SceneEditor.init();
        com.ecoserv.content.ContentNet.init();
        Features.JOIN.add(LightingManager::send);
        Features.JOIN.add(PanelManager::send);
        Features.DIM.add(LightingManager::send);
        Features.DIM.add(PanelManager::send);

        StepCatalog.source("lighting", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (String k : LightingManager.presets().keySet()) {
                out.add(new String[]{k.replace(' ', '_'), k, "", "minecraft:glowstone"});
            }
            return out;
        });
        StepCatalog.add(new Def("lighting", "Освещение мира", "Применить пресет освещения к этому миру (у всех в нём)",
                "minecraft:glowstone", "Камера и эффекты").pick("preset", "Пресет", "lighting", "", ""));
        ScriptSteps.register("lighting", (p, a, c) -> {
            String name = a.trim().replace('_', ' ');
            for (Map.Entry<String, LightingProfile> e : LightingManager.presets().entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    LightingProfile x = e.getValue().copy();
                    x.enabled = true;
                    LightingManager.setProfile(p.dimension, x);
                }
            }
            if ("off".equalsIgnoreCase(name) || "выкл".equalsIgnoreCase(name)) {
                LightingManager.profile(p.dimension).enabled = false;
                LightingManager.changed(p.dimension);
            }
            return 0;
        }, a -> a.trim().isEmpty() ? "lighting <пресет|off>" : null, "lighting Закат — пресет освещения мира");

        DevTools.add((ui, p, from) -> {
            ui.header("Редактор сцены и мир");
            ui.row(new ItemStack(Items.ENDER_EYE), "&fРедактор сцены (F8)")
                    .line("&7Как в Unity: свободная камера, иерархия, инспектор, перемещение/поворот/размер, добавление")
                    .onClick(() -> SceneEditor.open(p))
                    .button("Открыть", Ui.GREEN, () -> SceneEditor.open(p));
            ui.row(new ItemStack(Blocks.GLOWSTONE), "&fОсвещение мира").line("&7Как Lighting в Roblox: свет, атмосфера, свечение, лучи,"
                    + " цветокоррекция, небо, облака, источники света").onClick(() -> new LightingScreen(p).open());
            ui.row(new ItemStack(Items.ITEM_FRAME), "&f3D-панели").line("&7Окна прямо в мире: текст, кнопки, полосы")
                    .onClick(() -> new PanelScreen(p, null).open());
            ui.row(new ItemStack(Blocks.CRAFTING_TABLE), "&fСвой контент").line("&7Блоки, предметы, жидкости, текстуры, 3D-модели —"
                    + " буфер, применение после перезапуска").onClick(() -> new com.ecoserv.content.ContentScreen(p).open());
        });
    }

    /** /editor, /lighting, /panels, /content. */
    public static class Command extends CommandBase {
        private final String name;

        public Command(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<String> getAliases() {
            switch (name) {
                case "editor":
                    return Arrays.asList("редактор", "scene");
                case "lighting":
                    return Arrays.asList("освещение", "light");
                case "panels":
                    return Arrays.asList("панели");
                default:
                    return Arrays.asList("контент");
            }
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/" + name;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return Perms.isAdmin(sender);
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            EntityPlayerMP p = getCommandSenderAsPlayer(sender);
            switch (name) {
                case "editor":
                    SceneEditor.open(p);
                    break;
                case "lighting":
                    new LightingScreen(p).open();
                    break;
                case "panels":
                    new PanelScreen(p, null).open();
                    break;
                default:
                    new com.ecoserv.content.ContentScreen(p).open();
            }
        }
    }
}
