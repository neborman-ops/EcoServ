package com.ecoserv.integration;

import com.ecoserv.EcoServ;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Поиск API чужой экономики прямо в jar-файле мода.
 * <p>
 * У FTB Money нет документированного API, и оно отличается между сборками,
 * поэтому мод сам просматривает классы мода и ищет методы вида
 * getMoney / addMoney / setMoney (статические или через объект данных).
 * Команда /ecoadmin ftbscan печатает найденное — по этому списку можно
 * дописать точные имена в конфиг, если автоподбор ошибся.
 */
public final class ApiScanner {

    /** Слова, по которым узнаём «денежные» методы. */
    private static final String[] MONEY_WORDS = {"money", "balance", "cash", "coin", "funds"};

    /** Моды, которые имеет смысл сканировать. */
    private static final String[] MOD_HINTS = {"money", "econom", "cash", "coin", "bank", "shop"};

    public static class Found {
        public String className = "";
        public String accessor = "";
        public String getter = "";
        public String adder = "";
        public String setter = "";
        public String argType = "";
        public boolean chained;

        public String describe() {
            if (chained) {
                return className + "#" + accessor + "(" + argType + ") -> ." + getter + "()"
                        + (adder.isEmpty() ? "" : " / ." + adder + "(long)")
                        + (setter.isEmpty() ? "" : " / ." + setter + "(long)");
            }
            return className + "#" + getter + "(" + argType + ")"
                    + (adder.isEmpty() ? "" : " / " + adder + "(" + argType + ", long)")
                    + (setter.isEmpty() ? "" : " / " + setter + "(" + argType + ", long)");
        }
    }

    private ApiScanner() {
    }

    // --- публичный вход -----------------------------------------------------

    /** Готовый мост по результатам сканирования (или null). */
    @Nullable
    public static ReflectiveEconomy autoBridge() {
        List<Found> found = scan(6);
        for (int i = 0; i < found.size(); i++) {
            ReflectiveEconomy bridge = build(found.get(i));
            if (bridge != null) {
                EcoServ.LOG.info("Экономический мост найден сканером: {}", found.get(i).describe());
                return bridge;
            }
        }
        return null;
    }

    /** Человекочитаемый отчёт для /ecoadmin ftbscan. */
    public static List<String> report() {
        List<String> lines = new ArrayList<String>();
        List<ModContainer> mods = targetMods();
        if (mods.isEmpty()) {
            lines.add("§cМоды экономики не найдены (искал ftbmoney и всё, где в id есть money/econom/cash).");
            return lines;
        }
        lines.add("§6=== Поиск API экономики ===");
        for (int i = 0; i < mods.size(); i++) {
            lines.add("§7Мод: §f" + mods.get(i).getModId() + " §8(" + mods.get(i).getVersion() + ")");
        }
        List<Found> found = scan(20);
        if (found.isEmpty()) {
            lines.add("§cПодходящих методов не нашлось.");
            lines.add("§7Скинь этот вывод разработчику мода вместе со списком модов.");
            return lines;
        }
        for (int i = 0; i < found.size(); i++) {
            lines.add((i == 0 ? "§a> " : "§f  ") + found.get(i).describe());
        }
        lines.add("§7Чтобы закрепить вариант вручную: config/ecoserv/ecoserv.cfg -> integration:");
        Found best = found.get(0);
        lines.add("§f  economy_provider=custom");
        lines.add("§f  custom_class=" + best.className);
        lines.add("§f  custom_get_method=" + best.getter);
        lines.add("§f  custom_add_method=" + best.adder + (best.adder.isEmpty()
                ? "   (пусто: прибавление пойдёт через set(get + сумма))" : ""));
        if (!best.setter.isEmpty()) {
            lines.add("§f  custom_set_method=" + best.setter);
        }
        return lines;
    }

    // --- сканирование -------------------------------------------------------

    private static List<ModContainer> targetMods() {
        List<ModContainer> result = new ArrayList<ModContainer>();
        List<ModContainer> all = Loader.instance().getActiveModList();
        for (int i = 0; i < all.size(); i++) {
            ModContainer mod = all.get(i);
            String id = mod.getModId() == null ? "" : mod.getModId().toLowerCase(Locale.ROOT);
            if (id.equals("ftbmoney")) {
                result.add(0, mod);
                continue;
            }
            for (int h = 0; h < MOD_HINTS.length; h++) {
                if (id.contains(MOD_HINTS[h])) {
                    result.add(mod);
                    break;
                }
            }
        }
        return result;
    }

    private static List<Found> scan(int limit) {
        List<Found> result = new ArrayList<Found>();
        List<ModContainer> mods = targetMods();
        Set<String> seen = new HashSet<String>();

        for (int i = 0; i < mods.size() && result.size() < limit; i++) {
            List<String> classes = classNames(mods.get(i));
            for (int c = 0; c < classes.size() && result.size() < limit; c++) {
                String name = classes.get(c);
                if (!seen.add(name)) {
                    continue;
                }
                Class<?> cls = load(name);
                if (cls == null) {
                    continue;
                }
                Found direct = inspectStatic(cls);
                if (direct != null) {
                    result.add(direct);
                    continue;
                }
                Found chained = inspectChained(cls);
                if (chained != null) {
                    result.add(chained);
                }
            }
        }
        return result;
    }

    private static List<String> classNames(ModContainer mod) {
        List<String> names = new ArrayList<String>();
        File source;
        try {
            source = mod.getSource();
        } catch (Throwable t) {
            return names;
        }
        if (source == null || !source.exists()) {
            return names;
        }
        if (source.isDirectory()) {
            collect(source, "", names);
            return names;
        }
        ZipFile zip = null;
        try {
            zip = new ZipFile(source);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (entry.endsWith(".class") && !entry.contains("$")) {
                    names.add(entry.substring(0, entry.length() - 6).replace('/', '.'));
                }
            }
        } catch (Throwable t) {
            EcoServ.LOG.debug("Не удалось прочитать {}: {}", source, t.toString());
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Throwable ignored) {
                    // ничего
                }
            }
        }
        return names;
    }

    private static void collect(File dir, String prefix, List<String> names) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                collect(f, prefix + f.getName() + ".", names);
            } else if (f.getName().endsWith(".class") && !f.getName().contains("$")) {
                names.add(prefix + f.getName().substring(0, f.getName().length() - 6));
            }
        }
    }

    @Nullable
    private static Class<?> load(String name) {
        try {
            // false = без статической инициализации, чтобы ничего не сломать
            return Class.forName(name, false, ApiScanner.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isMoneyName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < MONEY_WORDS.length; i++) {
            if (lower.contains(MONEY_WORDS[i])) {
                return true;
            }
        }
        return false;
    }

    /** Вариант «статические методы»: getMoney(player) / addMoney(player, long). */
    @Nullable
    private static Found inspectStatic(Class<?> cls) {
        Method[] methods;
        try {
            methods = cls.getMethods();
        } catch (Throwable t) {
            return null;
        }
        Method getter = null;
        Method adder = null;
        Method setter = null;

        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (!Modifier.isStatic(m.getModifiers()) || !isMoneyName(m.getName())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1 && ReflectiveEconomy.isPlayerArg(params[0])
                    && ReflectiveEconomy.isNumeric(m.getReturnType())) {
                if (getter == null || m.getName().toLowerCase(Locale.ROOT).startsWith("get")) {
                    getter = m;
                }
            } else if (params.length == 2 && ReflectiveEconomy.isPlayerArg(params[0])
                    && ReflectiveEconomy.isNumeric(params[1])) {
                String lower = m.getName().toLowerCase(Locale.ROOT);
                if (lower.startsWith("add") || lower.startsWith("give") || lower.startsWith("increase")) {
                    adder = m;
                } else if (lower.startsWith("set")) {
                    setter = m;
                }
            }
        }
        if (getter == null || (adder == null && setter == null)) {
            return null;
        }
        Class<?> argType = getter.getParameterTypes()[0];
        if (adder != null && !sameFamily(argType, adder.getParameterTypes()[0])) {
            adder = null;
        }
        if (setter != null && !sameFamily(argType, setter.getParameterTypes()[0])) {
            setter = null;
        }
        if (adder == null && setter == null) {
            return null;
        }

        // сеттер НИКОГДА не выдаётся за «прибавить»: иначе каждая продажа перезаписывала бы баланс
        Found found = new Found();
        found.className = cls.getName();
        found.getter = getter.getName();
        found.adder = adder == null ? "" : adder.getName();
        found.setter = setter == null ? "" : setter.getName();
        found.argType = getter.getParameterTypes()[0].getSimpleName();
        return found;
    }

    /** EntityPlayer и EntityPlayerMP считаем одним видом аргумента (мост передаёт EntityPlayerMP). */
    private static boolean sameFamily(Class<?> a, Class<?> b) {
        return a.equals(b) || a.isAssignableFrom(b) || b.isAssignableFrom(a);
    }

    /** Вариант «через объект»: static Data get(player) -> data.getMoney()/addMoney(long). */
    @Nullable
    private static Found inspectChained(Class<?> cls) {
        Method[] methods;
        try {
            methods = cls.getMethods();
        } catch (Throwable t) {
            return null;
        }
        for (int i = 0; i < methods.length; i++) {
            Method accessor = methods[i];
            if (!Modifier.isStatic(accessor.getModifiers())) {
                continue;
            }
            Class<?>[] params = accessor.getParameterTypes();
            if (params.length != 1 || !ReflectiveEconomy.isPlayerArg(params[0])) {
                continue;
            }
            Class<?> holder = accessor.getReturnType();
            if (holder == null || holder.isPrimitive() || holder == Void.TYPE) {
                continue;
            }
            Method getter = null;
            Method adder = null;
            Method setter = null;
            Method[] holderMethods;
            try {
                holderMethods = holder.getMethods();
            } catch (Throwable t) {
                continue;
            }
            for (int h = 0; h < holderMethods.length; h++) {
                Method m = holderMethods[h];
                if (Modifier.isStatic(m.getModifiers()) || !isMoneyName(m.getName())) {
                    continue;
                }
                Class<?>[] mp = m.getParameterTypes();
                if (mp.length == 0 && ReflectiveEconomy.isNumeric(m.getReturnType())) {
                    getter = m;
                } else if (mp.length == 1 && ReflectiveEconomy.isNumeric(mp[0])) {
                    String lower = m.getName().toLowerCase(Locale.ROOT);
                    if (lower.startsWith("add") || lower.startsWith("give")) {
                        adder = m;
                    } else if (lower.startsWith("set")) {
                        setter = m;
                    }
                }
            }
            if (getter == null || (adder == null && setter == null)) {
                continue;
            }
            Found found = new Found();
            found.chained = true;
            found.className = cls.getName();
            found.accessor = accessor.getName();
            found.getter = getter.getName();
            found.adder = adder == null ? "" : adder.getName();
            found.setter = setter == null ? "" : setter.getName();
            found.argType = params[0].getSimpleName();
            return found;
        }
        return null;
    }

    // --- сборка моста -------------------------------------------------------

    @Nullable
    private static ReflectiveEconomy build(Found found) {
        try {
            Class<?> cls = load(found.className);
            if (cls == null) {
                return null;
            }
            if (!found.chained) {
                return ReflectiveEconomy.tryCreate(found.className, found.getter, found.adder, found.setter);
            }
            Method accessor = null;
            Method[] methods = cls.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals(found.accessor) && methods[i].getParameterTypes().length == 1
                        && Modifier.isStatic(methods[i].getModifiers())) {
                    accessor = methods[i];
                    break;
                }
            }
            if (accessor == null) {
                return null;
            }
            Class<?> holder = accessor.getReturnType();
            Method getter = null;
            Method adder = null;
            Method setter = null;
            Method[] holderMethods = holder.getMethods();
            for (int i = 0; i < holderMethods.length; i++) {
                Method m = holderMethods[i];
                if (m.getName().equals(found.getter) && m.getParameterTypes().length == 0) {
                    getter = m;
                } else if (!found.adder.isEmpty() && m.getName().equals(found.adder)
                        && m.getParameterTypes().length == 1) {
                    adder = m;
                } else if (!found.setter.isEmpty() && m.getName().equals(found.setter)
                        && m.getParameterTypes().length == 1) {
                    setter = m;
                }
            }
            if (getter == null || (adder == null && setter == null)) {
                return null;
            }
            accessor.setAccessible(true);
            getter.setAccessible(true);
            if (adder != null) {
                adder.setAccessible(true);
            }
            if (setter != null) {
                setter.setAccessible(true);
            }
            return new ReflectiveEconomy(found.className,
                    ReflectiveEconomy.argTypeOf(accessor.getParameterTypes()[0]),
                    accessor, getter, adder, setter);
        } catch (Throwable t) {
            EcoServ.LOG.debug("Не удалось собрать мост {}: {}", found.className, t.toString());
            return null;
        }
    }
}
