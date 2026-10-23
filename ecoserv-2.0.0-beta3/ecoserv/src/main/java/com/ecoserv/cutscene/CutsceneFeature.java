package com.ecoserv.cutscene;

import com.ecoserv.dev.DevTools;
import com.ecoserv.dev.StepCatalog;
import com.ecoserv.dev.StepCatalog.Def;
import com.ecoserv.dev.StepCatalog.Kind;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.Ui;
import com.ecoserv.v2.Features;
import com.ecoserv.v2.V2Actions;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumParticleTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Подключение катсцен, свободной камеры и эффектов. */
public final class CutsceneFeature {

    private CutsceneFeature() {
    }

    public static void init() {
        V2Actions.on("cutscene_end", (p, d) -> CutsceneManager.stop(p, true));
        Features.LEAVE.add(p -> CutsceneManager.stop(p, false));

        StepCatalog.source("cutscene", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (Cutscene c : CutsceneManager.all()) {
                out.add(new String[]{c.id, c.id, c.points.size() + " точек", "minecraft:ender_eye"});
            }
            return out;
        });
        StepCatalog.source("vfx", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (String[] s : Vfx.SHAPES) {
                out.add(s);
            }
            return out;
        });
        String c = "Камера и эффекты";
        StepCatalog.add(new Def("cutscene", "Катсцена", "Полёт камеры по точкам", "minecraft:ender_eye", c)
                .pick("id", "Катсцена", "cutscene", "", ""));
        StepCatalog.add(new Def("freecam", "Свободная камера", "Включить/выключить полёт камеры (WASD, пробел, Shift)", "minecraft:feather", c));
        StepCatalog.add(new Def("vfx", "Фигура из частиц", "Кольцо, сфера, спираль, столб, взрыв, волна, луч", "minecraft:blaze_powder", c)
                .pick("shape", "Фигура", "vfx", "", "ring")
                .p("type", "Частицы", Kind.PARTICLE, "", "flame")
                .p("r", "Размер", Kind.NUMBER, "радиус в блоках", "2")
                .opt("at", "Где", Kind.COORDS, "пусто — у игрока", ""));
        StepCatalog.add(new Def("shake", "Тряска экрана", "Как от взрыва", "minecraft:tnt", c)
                .p("s", "Сила", Kind.NUMBER, "1 — слегка, 5 — сильно", "3")
                .p("time", "Сколько", Kind.TIME, "", "1s"));
        StepCatalog.add(new Def("flash", "Вспышка", "Экран заливается цветом и гаснет", "minecraft:glowstone_dust", c)
                .choice("color", "Цвет", "", "FFFFFF", new String[][]{{"FFFFFF", "Белый"}, {"FF2020", "Красный"},
                        {"20FF40", "Зелёный"}, {"3080FF", "Синий"}, {"000000", "Чёрный (затемнение)"}, {"FFD000", "Золотой"}})
                .p("time", "Сколько", Kind.TIME, "", "1s"));
        StepCatalog.add(new Def("lightning", "Молния", "Без урона и пожара", "minecraft:end_rod", c)
                .opt("at", "Где", Kind.COORDS, "пусто — у игрока", ""));

        ScriptSteps.register("cutscene", (p, a, ctx) -> {
            Cutscene cs = CutsceneManager.get(a.trim());
            if (cs != null) {
                CutsceneManager.play(p, cs, ctx);
                return cs.length();
            }
            return 0;
        }, a -> a.trim().isEmpty() ? "cutscene <id>" : null, "cutscene intro — проиграть катсцену (сценарий ждёт её конца)");
        ScriptSteps.register("freecam", (p, a, ctx) -> {
            DevTools.freecam(p);
            return 0;
        }, a -> null, "freecam — свободная камера");
        ScriptSteps.register("vfx", (p, a, ctx) -> {
            String[] x = a.trim().split("\\s+");
            EnumParticleTypes t = x.length > 1 ? EnumParticleTypes.getByName(x[1]) : EnumParticleTypes.FLAME;
            if (t == null) {
                t = EnumParticleTypes.FLAME;
            }
            double r = x.length > 2 ? num(x[2], 2) : 2;
            double cx = x.length > 5 ? coord(x[3], p.posX) : p.posX;
            double cy = x.length > 5 ? coord(x[4], p.posY + 0.5D) : p.posY + 0.5D;
            double cz = x.length > 5 ? coord(x[5], p.posZ) : p.posZ;
            Vfx.shape(p, x[0], t, cx, cy, cz, r);
            return 0;
        }, a -> a.trim().isEmpty() ? "vfx <фигура> [частицы] [радиус] [x y z]" : null, "vfx ring flame 2");
        ScriptSteps.register("shake", (p, a, ctx) -> {
            String[] x = a.trim().split("\\s+");
            Vfx.shake(p, (float) num(x[0], 3), x.length > 1 ? com.ecoserv.script.ScriptRunner.parseTicks(x[1]) : 20);
            return 0;
        }, a -> null, "shake 3 1s — тряска экрана");
        ScriptSteps.register("flash", (p, a, ctx) -> {
            String[] x = a.trim().split("\\s+");
            int color;
            try {
                color = Integer.parseInt(x[0].replace("#", ""), 16);
            } catch (NumberFormatException e) {
                color = 0xFFFFFF;
            }
            Vfx.flash(p, color, x.length > 1 ? com.ecoserv.script.ScriptRunner.parseTicks(x[1]) : 20);
            return 0;
        }, a -> null, "flash FFFFFF 1s — вспышка");
        ScriptSteps.register("lightning", (p, a, ctx) -> {
            String[] x = a.trim().split("\\s+");
            boolean at = x.length >= 3;
            Vfx.lightning(p, at ? coord(x[0], p.posX) : p.posX, at ? coord(x[1], p.posY) : p.posY, at ? coord(x[2], p.posZ) : p.posZ);
            return 0;
        }, a -> null, "lightning [x y z] — молния без урона");

        DevTools.add((ui, p, from) -> {
            ui.header("Камера и эффекты");
            ui.row(new ItemStack(Items.ENDER_EYE), "&fКатсцены").line("&7Полёт камеры по точкам, текст, действия")
                    .onClick(() -> new CutsceneScreen(p, null).open());
            ui.row(new ItemStack(Items.FEATHER), "&fСвободная камера").line("&7WASD, пробел, Shift; ещё раз — выключить")
                    .button("Вкл/выкл", Ui.GREEN, () -> DevTools.freecam(p));
            ui.row(new ItemStack(Items.BLAZE_POWDER), "&fЭффекты").line("&7Шаги «Фигура из частиц», «Тряска», «Вспышка», «Молния»")
                    .button("Пример", Ui.BLUE, () -> {
                        Vfx.shape(p, "spiral", EnumParticleTypes.END_ROD, p.posX, p.posY, p.posZ, 1.5D);
                        Vfx.shake(p, 2, 15);
                        Vfx.flash(p, 0xFFD000, 15);
                    });
        });
    }

    private static double num(String s, double def) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double coord(String s, double base) {
        if (s.startsWith("~")) {
            return base + (s.length() > 1 ? num(s.substring(1), 0) : 0);
        }
        return num(s, base);
    }

    /** /cutscene &lt;id&gt; [ник] (админ). */
    public static class Command extends CommandBase {
        @Override
        public String getName() {
            return "cutscene";
        }

        @Override
        public List<String> getAliases() {
            return Arrays.asList("катсцена");
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/cutscene <id> [ник] | /cutscene — редактор";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return com.ecoserv.util.Perms.isAdmin(sender);
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) {
                new CutsceneScreen(getCommandSenderAsPlayer(sender), null).open();
                return;
            }
            EntityPlayerMP target = args.length > 1 ? getPlayer(server, sender, args[1]) : getCommandSenderAsPlayer(sender);
            Cutscene cs = CutsceneManager.get(args[0]);
            if (cs == null) {
                throw new CommandException("Нет катсцены " + args[0]);
            }
            CutsceneManager.play(target, cs, null);
        }
    }
}
