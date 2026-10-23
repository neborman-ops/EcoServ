package com.ecoserv.script;

import com.ecoserv.content.Actions;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Шаги сценариев 2.0 — расширяемый список. Системы мода добавляют свои шаги через register().
 * <pre>
 * money 100 | money -50                 — дать/забрать деньги
 * currency gems 5                       — валюта (минус — забрать)
 * give minecraft:diamond 3              — предмет
 * effect speed 10 1                     — эффект: id, секунд, уровень
 * particle flame 20 [x y z]             — частицы (~ — от игрока)
 * setblock ~ ~-1 ~ minecraft:gold_block — блок
 * fill x1 y1 z1 x2 y2 z2 блок
 * quest имя                             — событие для квестов («Событие сценария»)
 * set очки 0 | add очки 5               — переменные игрока
 * if var очки >= 10 then msg &aМолодец  — условие (var, role, money, item, chance, world, quest, level)
 * broadcast &6Текст всем
 * heal | feed
 * </pre>
 */
public final class ScriptSteps {

    public static final int UNKNOWN = -100;
    public static final int STOP = -1;

    public interface Step {
        /** Выполнить. Вернуть паузу в тиках (0 — сразу дальше) или STOP. */
        int run(EntityPlayerMP player, String args, @Nullable String context);
    }

    public interface Check {
        /** null — шаг понятен, иначе что не так. */
        @Nullable
        String validate(String args);
    }

    private static final class Entry {
        final Step step;
        final Check check;
        final String help;

        Entry(Step step, Check check, String help) {
            this.step = step;
            this.check = check;
            this.help = help;
        }
    }

    private static final Map<String, Entry> STEPS = new LinkedHashMap<String, Entry>();
    private static final Random RANDOM = new Random();

    static {
        register("money", (p, a, c) -> {
            long v = num(a, 0);
            if (v > 0) {
                com.ecoserv.economy.EconomyManager.give(p.getUniqueID(), v);
            } else if (v < 0) {
                com.ecoserv.economy.EconomyManager.take(p.getUniqueID(), -v);
            }
            return 0;
        }, a -> isNum(a) ? null : "money <сумма>", "money 100 — дать (минус — забрать)");
        register("currency", (p, a, c) -> {
            String[] x = a.split("\\s+");
            long v = x.length > 1 ? num(x[1], 0) : 0;
            if (v > 0) {
                com.ecoserv.bank.BankManager.give(p.getUniqueID(), x[0], v);
            } else if (v < 0) {
                com.ecoserv.bank.BankManager.take(p.getUniqueID(), x[0], -v);
            }
            return 0;
        }, a -> a.split("\\s+").length >= 2 ? null : "currency <валюта> <сумма>", "currency gems 5");
        register("give", (p, a, c) -> {
            String[] x = a.split("\\s+");
            ItemKey k = ItemKey.parse(x[0]);
            int n = x.length > 1 ? (int) num(x[1], 1) : 1;
            if (k.isValid()) {
                while (n > 0) {
                    ItemStack s = k.toStack(1);
                    int m = Math.min(n, s.getMaxStackSize());
                    s.setCount(m);
                    PlayerUtil.giveOrDrop(p, s);
                    n -= m;
                }
            }
            return 0;
        }, a -> ItemKey.parse(a.split("\\s+")[0]).isValid() ? null : "нет предмета «" + a.split("\\s+")[0] + "»",
                "give minecraft:diamond 3");
        register("effect", (p, a, c) -> {
            String[] x = a.split("\\s+");
            Potion pot = potion(x[0]);
            if (pot != null) {
                int sec = x.length > 1 ? (int) num(x[1], 10) : 10;
                int lvl = x.length > 2 ? (int) num(x[2], 1) : 1;
                p.addPotionEffect(new PotionEffect(pot, sec * 20, Math.max(0, lvl - 1)));
            }
            return 0;
        }, a -> potion(a.split("\\s+")[0]) != null ? null : "нет эффекта «" + a.split("\\s+")[0] + "»", "effect speed 10 1");
        register("particle", (p, a, c) -> {
            String[] x = a.split("\\s+");
            EnumParticleTypes t = EnumParticleTypes.getByName(x[0]);
            if (t == null) {
                return 0;
            }
            int n = x.length > 1 ? (int) num(x[1], 10) : 10;
            double px = x.length > 4 ? coord(x[2], p.posX) : p.posX;
            double py = x.length > 4 ? coord(x[3], p.posY + 1) : p.posY + 1;
            double pz = x.length > 4 ? coord(x[4], p.posZ) : p.posZ;
            p.getServerWorld().spawnParticle(t, px, py, pz, n, 0.5D, 0.5D, 0.5D, 0.02D);
            return 0;
        }, a -> EnumParticleTypes.getByName(a.split("\\s+")[0]) != null ? null : "нет частиц «" + a.split("\\s+")[0] + "»",
                "particle flame 20");
        register("setblock", (p, a, c) -> {
            String[] x = a.split("\\s+");
            if (x.length >= 4) {
                IBlockState st = state(x[3]);
                if (st != null) {
                    p.getServerWorld().setBlockState(new BlockPos(coord(x[0], p.posX), coord(x[1], p.posY), coord(x[2], p.posZ)), st);
                }
            }
            return 0;
        }, a -> {
            String[] x = a.split("\\s+");
            return x.length >= 4 && state(x[3]) != null ? null : "setblock x y z блок";
        }, "setblock ~ ~-1 ~ minecraft:gold_block");
        register("fill", (p, a, c) -> {
            String[] x = a.split("\\s+");
            if (x.length >= 7) {
                IBlockState st = state(x[6]);
                if (st == null) {
                    return 0;
                }
                BlockPos f = new BlockPos(coord(x[0], p.posX), coord(x[1], p.posY), coord(x[2], p.posZ));
                BlockPos t = new BlockPos(coord(x[3], p.posX), coord(x[4], p.posY), coord(x[5], p.posZ));
                WorldServer w = p.getServerWorld();
                int count = 0;
                for (BlockPos b : BlockPos.getAllInBox(f, t)) {
                    if (++count > 32768) {
                        break;
                    }
                    w.setBlockState(b, st, 2);
                }
            }
            return 0;
        }, a -> {
            String[] x = a.split("\\s+");
            return x.length >= 7 && state(x[6]) != null ? null : "fill x1 y1 z1 x2 y2 z2 блок";
        }, "fill ~ ~ ~ ~3 ~ ~3 minecraft:stone");
        register("quest", (p, a, c) -> {
            com.ecoserv.quest.QuestManager.event(p, "event", a.trim(), 1);
            return 0;
        }, a -> a.trim().isEmpty() ? "quest <имя события>" : null, "quest нашёл_ключ");
        register("set", (p, a, c) -> {
            String[] x = a.split("\\s+", 2);
            ScriptVars.set(p.getUniqueID(), x[0], x.length > 1 ? Actions.placeholders(x[1], p, c) : "");
            return 0;
        }, a -> a.trim().isEmpty() ? "set <переменная> <значение>" : null, "set уровень 1");
        register("add", (p, a, c) -> {
            String[] x = a.split("\\s+");
            ScriptVars.add(p.getUniqueID(), x[0], x.length > 1 ? num(x[1], 1) : 1);
            return 0;
        }, a -> a.trim().isEmpty() ? "add <переменная> [число]" : null, "add очки 5");
        register("if", (p, a, c) -> {
            int then = a.indexOf(" then ");
            if (then < 0) {
                return 0;
            }
            String cond = a.substring(0, then).trim();
            String step = a.substring(then + 6).trim();
            String orElse = null;
            int el = step.indexOf(" else ");
            if (el >= 0) {
                orElse = step.substring(el + 6).trim();
                step = step.substring(0, el).trim();
            }
            boolean ok = condition(p, cond, c);
            String run = ok ? step : orElse;
            return run == null || run.isEmpty() ? 0 : ScriptRunner.runInline(p, run, c);
        }, a -> a.contains(" then ") ? validateCondition(a.substring(0, a.indexOf(" then ")).trim()) : "if <условие> then <шаг> [else <шаг>]",
                "if var очки >= 10 then msg &aМолодец else msg &cЕщё нет");
        register("broadcast", (p, a, c) -> {
            ChatUtil.broadcast(Actions.placeholders(a, p, c));
            return 0;
        }, a -> null, "broadcast &6Текст всем");
        register("heal", (p, a, c) -> {
            p.setHealth(p.getMaxHealth());
            return 0;
        }, a -> null, "heal");
        register("feed", (p, a, c) -> {
            p.getFoodStats().addStats(20, 20.0F);
            return 0;
        }, a -> null, "feed");
        register("event", (p, a, c) -> {
            com.ecoserv.dev.DevEvents.fire(a.trim(), p, c);
            return 0;
        }, a -> a.trim().isEmpty() ? "event <имя>" : null, "event дверь_открыта — своё событие для привязок");
    }

    private ScriptSteps() {
    }

    public static void register(String name, Step step, Check check, String help) {
        STEPS.put(name.toLowerCase(Locale.ROOT), new Entry(step, check, help));
    }

    public static boolean known(String name) {
        return STEPS.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public static String allNames() {
        List<String> base = new ArrayList<String>();
        for (String s : new String[]{"msg", "title", "actionbar", "sound", "wait", "cmd", "pcmd", "tp", "tpworld",
                "activate", "require", "run", "stop"}) {
            base.add(s);
        }
        base.addAll(STEPS.keySet());
        return String.join(", ", base);
    }

    /** Подсказки по шагам 2.0 (для редактора сценариев). */
    public static List<String> help() {
        List<String> out = new ArrayList<String>();
        for (Entry e : STEPS.values()) {
            out.add(e.help);
        }
        return out;
    }

    static int run(String op, String args, EntityPlayerMP player, @Nullable String context) {
        Entry e = STEPS.get(op);
        if (e == null) {
            return UNKNOWN;
        }
        return e.step.run(player, args == null ? "" : args.trim(), context);
    }

    @Nullable
    static String validate(String op, String args) {
        Entry e = STEPS.get(op);
        if (e == null) {
            return "неизвестный шаг «" + op + "»";
        }
        try {
            return e.check.validate(args == null ? "" : args.trim());
        } catch (Throwable t) {
            return "не понял шаг";
        }
    }

    // --- условия ---------------------------------------------------------------------------------------

    /**
     * var имя оп значение | role id | money >= N | item предмет N | chance процент | world имя | quest id
     * | level N | not &lt;условие&gt;
     */
    public static boolean condition(EntityPlayerMP p, String cond, @Nullable String context) {
        String c = cond.trim();
        if (c.startsWith("not ")) {
            return !condition(p, c.substring(4), context);
        }
        String[] x = c.split("\\s+");
        switch (x[0].toLowerCase(Locale.ROOT)) {
            case "var": {
                if (x.length < 4) {
                    return false;
                }
                String have = ScriptVars.get(p.getUniqueID(), x[1]);
                return compare(have, x[2], Actions.placeholders(x[3], p, context));
            }
            case "role":
                return x.length > 1 && com.ecoserv.role.RoleManager.has(p, x[1]);
            case "money":
                return x.length > 2 && compare(Long.toString(com.ecoserv.economy.EconomyManager.get(p.getUniqueID())), x[1], x[2]);
            case "currency":
                return x.length > 3 && compare(Long.toString(com.ecoserv.bank.BankManager.balance(p.getUniqueID(), x[1])), x[2], x[3]);
            case "item": {
                if (x.length < 2) {
                    return false;
                }
                long need = x.length > 2 ? num(x[2], 1) : 1;
                ItemKey k = ItemKey.parse(x[1]);
                long n = 0;
                for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
                    ItemStack s = p.inventory.getStackInSlot(i);
                    if (!s.isEmpty() && k.matches(s)) {
                        n += s.getCount();
                    }
                }
                return n >= need;
            }
            case "chance":
                return x.length > 1 && RANDOM.nextDouble() * 100.0D < num(x[1], 50);
            case "world":
                return x.length > 1 && com.ecoserv.world.WorldNames.matches(p.dimension, x[1]);
            case "quest": {
                com.ecoserv.quest.QuestBook.Quest q = x.length > 1 ? com.ecoserv.quest.QuestManager.quest(x[1]) : null;
                return q != null && com.ecoserv.quest.QuestManager.isDone(p, q);
            }
            case "level":
                return x.length > 2 && compare(Integer.toString(p.experienceLevel), x[1], x[2]);
            case "sneaking":
                return p.isSneaking();
            default:
                return false;
        }
    }

    @Nullable
    static String validateCondition(String cond) {
        String c = cond.trim();
        if (c.startsWith("not ")) {
            c = c.substring(4).trim();
        }
        String k = c.split("\\s+")[0].toLowerCase(Locale.ROOT);
        switch (k) {
            case "var":
                return c.split("\\s+").length >= 4 ? null : "var <имя> <оп> <значение> (оп: = != > < >= <=)";
            case "role":
            case "world":
            case "quest":
            case "chance":
                return c.split("\\s+").length >= 2 ? null : k + " <значение>";
            case "money":
            case "level":
                return c.split("\\s+").length >= 3 ? null : k + " <оп> <число>";
            case "currency":
                return c.split("\\s+").length >= 4 ? null : "currency <валюта> <оп> <число>";
            case "item":
                return c.split("\\s+").length >= 2 ? null : "item <предмет> [количество]";
            case "sneaking":
                return null;
            default:
                return "неизвестное условие «" + k + "» (var, role, money, currency, item, chance, world, quest, level, sneaking, not …)";
        }
    }

    static boolean compare(String a, String op, String b) {
        Double x = dbl(a);
        Double y = dbl(b);
        if (x != null && y != null) {
            switch (op) {
                case ">":
                    return x > y;
                case "<":
                    return x < y;
                case ">=":
                    return x >= y;
                case "<=":
                    return x <= y;
                case "!=":
                    return !x.equals(y);
                default:
                    return x.equals(y);
            }
        }
        boolean eq = (a == null ? "" : a).equalsIgnoreCase(b == null ? "" : b);
        return "!=".equals(op) != eq;
    }

    // --- разбор -------------------------------------------------------------------------------------------

    @Nullable
    private static Double dbl(String s) {
        try {
            return s == null ? null : Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static long num(String s, long def) {
        Double d = dbl(s);
        return d == null ? def : Math.round(d);
    }

    private static boolean isNum(String s) {
        return dbl(s) != null;
    }

    static double coord(String s, double base) {
        if (s.startsWith("~")) {
            Double d = dbl(s.substring(1));
            return base + (d == null ? 0.0D : d);
        }
        Double d = dbl(s);
        return d == null ? base : d;
    }

    @Nullable
    static Potion potion(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String k = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        return Potion.REGISTRY.getObject(new ResourceLocation(k));
    }

    @Nullable
    static IBlockState state(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        ItemKey k = ItemKey.parse(id);
        Block b = Block.getBlockFromName(k.id);
        if (b == null) {
            return null;
        }
        return k.meta >= 0 ? b.getStateFromMeta(k.meta) : b.getDefaultState();
    }
}
