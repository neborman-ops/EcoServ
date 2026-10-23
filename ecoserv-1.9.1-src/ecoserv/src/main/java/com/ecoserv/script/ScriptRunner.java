package com.ecoserv.script;

import com.ecoserv.EcoServ;
import com.ecoserv.activator.ActivatorManager;
import com.ecoserv.content.Actions;
import com.ecoserv.dates.AgeFilter;
import com.ecoserv.dates.DatesManager;
import com.ecoserv.role.RoleManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Выполнение сценариев: шаги идут по тикам сервера, пауза «wait» откладывает следующие. */
public final class ScriptRunner {

    private static final class Run {
        final UUID player;
        final Script script;
        final int depth;
        @Nullable
        final String context;
        int step;
        int wait;
        boolean stopped;

        Run(UUID player, Script script, int depth, @Nullable String context) {
            this.player = player;
            this.script = script;
            this.depth = depth;
            this.context = context;
        }
    }

    private static final int MAX_RUNS = 1000;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_STEPS_PER_TICK = 64;
    private static final List<Run> RUNS = new ArrayList<Run>();
    private static final Set<String> WARNED = new HashSet<String>();

    private ScriptRunner() {
    }

    /** Запустить сценарий для игрока. context — имя зоны/кнопки для подстановки %name%. */
    public static boolean start(EntityPlayerMP player, String name, @Nullable String context) {
        return start(player, name, context, 0);
    }

    private static boolean start(EntityPlayerMP player, String name, @Nullable String context, int depth) {
        Script script = ScriptManager.get(name);
        if (script == null || player == null) {
            if (script == null && WARNED.add(name)) {
                EcoServ.LOG.warn("Сценарий «{}» не найден (вызван из «{}»)", name, context);
            }
            return false;
        }
        if (depth > MAX_DEPTH || RUNS.size() >= MAX_RUNS) {
            return false;
        }
        if (!script.allowParallel && isRunning(player.getUniqueID(), script.name)) {
            return false;
        }
        RUNS.add(new Run(player.getUniqueID(), script, depth, context));
        return true;
    }

    public static boolean isRunning(UUID player, String name) {
        String key = ScriptManager.key(name);
        for (Run run : RUNS) {
            if (run.player.equals(player) && run.script.name.equals(key) && !run.stopped) {
                return true;
            }
        }
        return false;
    }

    /** Остановить сценарии игрока. Возвращает, сколько остановлено. */
    public static int stop(UUID player) {
        int n = 0;
        for (Run run : RUNS) {
            if (run.player.equals(player) && !run.stopped) {
                run.stopped = true;
                n++;
            }
        }
        return n;
    }

    public static int running() {
        return RUNS.size();
    }

    /** Каждый тик сервера. */
    public static void tick() {
        if (RUNS.isEmpty()) {
            return;
        }
        for (Run run : new ArrayList<Run>(RUNS)) {
            EntityPlayerMP player = PlayerUtil.online(run.player);
            if (player == null || run.stopped) {
                RUNS.remove(run);
                continue;
            }
            if (run.wait > 0) {
                run.wait--;
                continue;
            }
            int executed = 0;
            while (!run.stopped && run.wait == 0 && run.step < run.script.steps.size()
                    && executed < MAX_STEPS_PER_TICK) {
                String step = run.script.steps.get(run.step++);
                executed++;
                try {
                    execute(run, player, step);
                } catch (Throwable t) {
                    EcoServ.LOG.error("Сценарий «{}», шаг {} («{}») упал", run.script.name, run.step, step, t);
                }
            }
            if (run.stopped || (run.wait == 0 && run.step >= run.script.steps.size())) {
                RUNS.remove(run);
            }
        }
    }

    private static void execute(Run run, EntityPlayerMP player, String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        String[] split = line.split("\\s+", 2);
        String op = split[0].toLowerCase(Locale.ROOT);
        String rest = split.length > 1 ? split[1] : "";
        switch (op) {
            case "msg":
                Actions.message(player, rest, run.context);
                break;
            case "title":
                Actions.title(player, rest, run.context, 10, 70, 20);
                break;
            case "actionbar":
                ChatUtil.actionBar(player, Actions.placeholders(rest, player, run.context));
                break;
            case "sound": {
                String[] p = rest.split("\\s+");
                float volume = p.length > 1 ? parseFloat(p[1], 1.0F) : 1.0F;
                float pitch = p.length > 2 ? parseFloat(p[2], 1.0F) : 1.0F;
                Actions.sound(player, p[0], volume, pitch);
                break;
            }
            case "wait":
                run.wait = Math.max(0, parseTicks(rest));
                break;
            case "cmd":
                Actions.serverCommand(rest, player, run.context);
                break;
            case "pcmd": {
                MinecraftServer server = PlayerUtil.server();
                String c = Actions.placeholders(rest.trim(), player, run.context);
                if (c.startsWith("/")) {
                    c = c.substring(1);
                }
                if (server != null && !c.isEmpty()) {
                    server.getCommandManager().executeCommand(player, c);
                }
                break;
            }
            case "tp": {
                String[] p = rest.split("\\s+");
                if (p.length >= 3) {
                    double x = coord(p[0], player.posX);
                    double y = coord(p[1], player.posY);
                    double z = coord(p[2], player.posZ);
                    float yaw = p.length > 3 ? parseFloat(p[3], player.rotationYaw) : player.rotationYaw;
                    float pitch = p.length > 4 ? parseFloat(p[4], player.rotationPitch) : player.rotationPitch;
                    if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z)) {
                        com.ecoserv.region.RegionManager.suppress(player, 1000L);
                        WorldTeleport.teleport(player, new WorldTeleport.Target(player.dimension, x, y, z, yaw, pitch));
                    }
                }
                break;
            }
            case "tpworld": {
                String[] p = rest.split("\\s+");
                Integer dim = WorldNames.resolve(p[0]);
                if (dim == null || !WorldNames.exists(dim)) {
                    warn(run, "мир «" + p[0] + "» не найден");
                    break;
                }
                double tx = p.length >= 4 ? parseDouble(p[1]) : Double.NaN;
                double ty = p.length >= 4 ? parseDouble(p[2]) : Double.NaN;
                double tz = p.length >= 4 ? parseDouble(p[3]) : Double.NaN;
                WorldTeleport.Target target = !Double.isNaN(tx) && !Double.isNaN(ty) && !Double.isNaN(tz)
                        ? new WorldTeleport.Target(dim, tx, ty, tz, player.rotationYaw, player.rotationPitch)
                        : WorldTeleport.pickTarget(player, dim, true);
                com.ecoserv.region.RegionManager.suppress(player, 1500L);
                WorldTeleport.rememberPosition(player);
                WorldTeleport.teleport(player, target);
                break;
            }
            case "activate":
                ActivatorManager.notifyListeners(player, Actions.placeholders(rest.trim(), player, run.context));
                break;
            case "require": {
                String[] p = rest.trim().split("\\s+", 2);
                String kind = p[0].toLowerCase(Locale.ROOT);
                String value = p.length > 1 ? p[1].trim() : "";
                boolean ok;
                if ("role".equals(kind) || "роль".equals(kind)) {
                    ok = Perms.isAdmin(player) || RoleManager.has(player, value);
                } else if ("age".equals(kind) || "возраст".equals(kind)) {
                    ok = DatesManager.checkAge(player.getUniqueID(), AgeFilter.parse(value));
                } else {
                    warn(run, "require: role или age");
                    ok = true;
                }
                if (!ok) {
                    run.stopped = true;
                }
                break;
            }
            case "run":
                start(player, rest.trim(), run.context, run.depth + 1);
                break;
            case "stop":
                run.stopped = true;
                break;
            default:
                warn(run, "неизвестный шаг «" + op + "»");
        }
    }

    private static void warn(Run run, String text) {
        String key = run.script.name + "|" + text;
        if (WARNED.add(key)) {
            EcoServ.LOG.warn("Сценарий «{}»: {}", run.script.name, text);
        }
    }

    /** «40» — тики, «2s» или «1.5с» — секунды, «1m» — минуты. -1 — не распознано. */
    public static int parseTicks(String text) {
        String s = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace(',', '.');
        if (s.isEmpty()) {
            return -1;
        }
        double factor = 1.0D;
        if (s.endsWith("s") || s.endsWith("с")) {
            factor = 20.0D;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m") || s.endsWith("м")) {
            factor = 1200.0D;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("t")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            double v = Double.parseDouble(s.trim());
            return v < 0 ? -1 : (int) Math.round(v * factor);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double coord(String s, double base) {
        if (s.startsWith("~")) {
            String r = s.substring(1);
            return r.isEmpty() ? base : base + parseDouble(r);
        }
        return parseDouble(s);
    }
}
