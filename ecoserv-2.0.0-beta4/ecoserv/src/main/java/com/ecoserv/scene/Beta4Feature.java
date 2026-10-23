package com.ecoserv.scene;

import com.ecoserv.camera.CameraManager;
import com.ecoserv.camera.CameraScreen;
import com.ecoserv.camera.SceneCamera;
import com.ecoserv.dev.DevTools;
import com.ecoserv.dev.StepCatalog;
import com.ecoserv.dev.StepCatalog.Def;
import com.ecoserv.display.DisplayManager;
import com.ecoserv.display.DisplayScreen;
import com.ecoserv.gui.CustomGui;
import com.ecoserv.gui.GuiManager;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.UiError;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.v2.Features;
import com.ecoserv.v2.V2Actions;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Подключение 2.0 beta4: блок-дисплеи, камеры сцены, свои окна (дизайнер GUI). */
public final class Beta4Feature {

    private Beta4Feature() {
    }

    public static void init() {
        GuiManager.init();
        Features.JOIN.add(DisplayManager::send);
        Features.DIM.add(DisplayManager::send);
        Features.LEAVE.add(CameraManager::leave);
        Features.LEAVE.add(GuiManager::leave);
        V2Actions.on("camview_exit", (p, d) -> {
            SceneCamera c = CameraManager.viewing(p);
            if (c != null && c.allowExit) {
                CameraManager.stop(p);
            }
        });

        // --- шаги ---
        StepCatalog.source("camera", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (SceneCamera c : CameraManager.all()) {
                out.add(new String[]{c.id, c.title(), c.id, "minecraft:ender_eye"});
            }
            return out;
        });
        StepCatalog.add(new Def("camera", "Показать камеру", "Переключить игрока на камеру сцены (секунды — сколько, пусто — пока не выключат)",
                "minecraft:ender_eye", "Камера и эффекты").pick("camera", "Камера", "camera", "", "")
                .opt("sec", "Секунд", StepCatalog.Kind.NUMBER, "пусто — пока не выключат", ""));
        StepCatalog.add(new Def("camera_off", "Вернуть камеру", "Выключить камеру сцены у игрока", "minecraft:ender_pearl", "Камера и эффекты"));
        ScriptSteps.register("camera", (p, a, c) -> {
            String[] x = a.trim().split("\\s+");
            if (x.length == 0 || x[0].isEmpty() || "off".equalsIgnoreCase(x[0])) {
                CameraManager.stop(p);
                return 0;
            }
            SceneCamera cam = CameraManager.get(x[0]);
            if (cam != null && cam.dim == p.dimension) {
                double sec = 0;
                if (x.length > 1) {
                    try {
                        sec = Double.parseDouble(x[1].replace(',', '.'));
                    } catch (NumberFormatException ignored) {
                        // пока не выключат
                    }
                }
                CameraManager.view(p, cam, sec);
            }
            return 0;
        }, a -> a.trim().isEmpty() ? "camera <id> [секунд] | camera off" : null, "camera cam_1 5 — показать камеру 5 секунд");
        ScriptSteps.register("camera_off", (p, a, c) -> {
            CameraManager.stop(p);
            return 0;
        }, a -> null, "camera_off — вернуть обычный вид");

        StepCatalog.source("gui", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (CustomGui g : GuiManager.all()) {
                out.add(new String[]{g.id, ChatUtil.strip(ChatUtil.color(g.title.isEmpty() ? g.id : g.title)), g.id, "minecraft:painting"});
            }
            return out;
        });
        StepCatalog.add(new Def("gui", "Открыть окно", "Своё окно из дизайнера", "minecraft:painting", StepCatalog.CATEGORIES[0])
                .pick("gui", "Окно", "gui", "", ""));
        ScriptSteps.register("gui", (p, a, c) -> {
            String id = a.trim();
            if ("close".equalsIgnoreCase(id)) {
                GuiManager.close(p);
            } else {
                GuiManager.open(p, id);
            }
            return 0;
        }, a -> a.trim().isEmpty() ? "gui <id> | gui close" : null, "gui shop — открыть своё окно");

        DevTools.add((ui, p, from) -> {
            ui.header("Окна, камеры и дисплеи");
            ui.row(new ItemStack(Items.PAINTING), "&fОкна (дизайнер GUI)")
                    .line("&7Рисуешь окно мышью: кнопки, текст, предметы, картинки, полосы, поля ввода")
                    .onClick(() -> GuiManager.openList(p));
            ui.row(new ItemStack(Items.ENDER_EYE), "&fКамеры").line("&7Точки обзора; переключить игрока на камеру")
                    .onClick(() -> new CameraScreen(p, null).open());
            ui.row(new ItemStack(Items.ARMOR_STAND), "&fБлок-дисплеи").line("&7Блок или предмет со свободным поворотом и размером")
                    .onClick(() -> new DisplayScreen(p, null).open());
        });
    }

    public static void load() {
        DisplayManager.load();
        CameraManager.load();
        GuiManager.load();
    }

    public static void tick() {
        CameraManager.tick();
        GuiManager.tick();
    }

    /** /camera, /gui, /displays. */
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
                case "camera":
                    return Arrays.asList("камера", "cam");
                case "gui":
                    return Arrays.asList("окна", "guis");
                default:
                    return Arrays.asList("дисплеи", "display");
            }
        }

        @Override
        public String getUsage(ICommandSender sender) {
            switch (name) {
                case "camera":
                    return "/camera [view <id> [ник|@a] [секунд] | off [ник|@a]]";
                case "gui":
                    return "/gui [open <id> [ник|@a] | design <id> | close [ник|@a]]";
                default:
                    return "/displays";
            }
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return Perms.isAdmin(sender);
        }

        private static List<EntityPlayerMP> who(ICommandSender sender, String arg) throws CommandException {
            List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
            if (arg == null || arg.isEmpty()) {
                out.add(getCommandSenderAsPlayer(sender));
            } else if ("@a".equals(arg)) {
                out.addAll(PlayerUtil.allOnline());
            } else {
                EntityPlayerMP p = PlayerUtil.online(arg);
                if (p == null) {
                    throw new CommandException("Игрок «" + arg + "» не в сети.");
                }
                out.add(p);
            }
            return out;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            try {
                if ("camera".equals(name)) {
                    if (args.length == 0) {
                        new CameraScreen(getCommandSenderAsPlayer(sender), null).open();
                    } else if ("view".equalsIgnoreCase(args[0]) && args.length > 1) {
                        SceneCamera c = CameraManager.get(args[1]);
                        if (c == null) {
                            throw new CommandException("Нет камеры «" + args[1] + "».");
                        }
                        double sec = args.length > 3 ? Double.parseDouble(args[3]) : 0;
                        int n = 0;
                        for (EntityPlayerMP p : who(sender, args.length > 2 ? args[2] : null)) {
                            if (p.dimension == c.dim) {
                                CameraManager.view(p, c, sec);
                                n++;
                            }
                        }
                        sender.sendMessage(new TextComponentString("§aНа камеру «" + c.title() + "»: " + n));
                    } else if ("off".equalsIgnoreCase(args[0])) {
                        for (EntityPlayerMP p : who(sender, args.length > 1 ? args[1] : null)) {
                            CameraManager.stop(p);
                        }
                    } else {
                        new CameraScreen(getCommandSenderAsPlayer(sender), args[0]).open();
                    }
                } else if ("gui".equals(name)) {
                    if (args.length == 0) {
                        GuiManager.openList(getCommandSenderAsPlayer(sender));
                    } else if ("open".equalsIgnoreCase(args[0]) && args.length > 1) {
                        for (EntityPlayerMP p : who(sender, args.length > 2 ? args[2] : null)) {
                            GuiManager.open(p, args[1]);
                        }
                    } else if ("close".equalsIgnoreCase(args[0])) {
                        for (EntityPlayerMP p : who(sender, args.length > 1 ? args[1] : null)) {
                            GuiManager.close(p);
                        }
                    } else if ("design".equalsIgnoreCase(args[0]) && args.length > 1) {
                        GuiManager.design(getCommandSenderAsPlayer(sender), args[1]);
                    } else {
                        GuiManager.design(getCommandSenderAsPlayer(sender), args[0]);
                    }
                } else {
                    new DisplayScreen(getCommandSenderAsPlayer(sender), null).open();
                }
            } catch (UiError e) {
                throw new CommandException(e.getMessage());
            } catch (NumberFormatException e) {
                throw new CommandException("Секунды — число.");
            }
        }
    }
}
