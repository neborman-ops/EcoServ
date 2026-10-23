package com.ecoserv.integration;

import com.ecoserv.EcoServ;
import com.ecoserv.economy.IEconomy;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Мост к чужой экономике (FTB Money и т.п.) через рефлексию.
 * <p>
 * ВАЖНО: «прибавить» и «установить» — разные операции. Если у чужого API есть
 * только get + set, прибавление делается как set(get() + сумма), а НЕ вызовом set(сумма).
 * После каждой операции баланс перечитывается и сверяется с ожидаемым:
 * если метод, названный «add», на деле перезаписывает баланс, мост это
 * замечает, сразу исправляет значение и дальше работает в безопасном режиме.
 */
public class ReflectiveEconomy implements IEconomy {

    public enum ArgType {
        UUID_ARG,
        PLAYER,
        GAME_PROFILE,
        FORGE_PLAYER
    }

    private final String label;
    private final ArgType argType;
    @Nullable
    private final Method accessor;
    private final Method getMethod;
    /** Настоящее прибавление (дельта). Может отсутствовать. */
    @Nullable
    private final Method addMethod;
    /** Установка абсолютного значения. Может отсутствовать. */
    @Nullable
    private final Method setMethod;
    /** Самопроверка выяснила, что addMethod на самом деле перезаписывает баланс. */
    private volatile boolean addActsAsSet;
    private volatile boolean warnedMismatch;

    public ReflectiveEconomy(String label, ArgType argType, @Nullable Method accessor,
                             Method getMethod, @Nullable Method addMethod, @Nullable Method setMethod) {
        this.label = label;
        this.argType = argType;
        this.accessor = accessor;
        this.getMethod = getMethod;
        this.addMethod = addMethod;
        this.setMethod = setMethod;
        EcoServ.LOG.info("Мост экономики {}: get={}, add={}, set={}", label, getMethod.getName(),
                addMethod == null ? "нет" : addMethod.getName(),
                setMethod == null ? "нет" : setMethod.getName());
    }

    /**
     * Статические методы по именам. addName и setName могут быть пустыми,
     * но хотя бы один из них должен найтись. Сеттер НИКОГДА не используется как «прибавить».
     */
    @Nullable
    public static ReflectiveEconomy tryCreate(String className, String getName, String addName, String setName) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        Class<?> cls;
        try {
            cls = Class.forName(className, false, ReflectiveEconomy.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }

        ArgType[] order = {ArgType.UUID_ARG, ArgType.PLAYER, ArgType.GAME_PROFILE, ArgType.FORGE_PLAYER};
        for (int i = 0; i < order.length; i++) {
            Class<?> argClass = argClass(order[i]);
            if (argClass == null) {
                continue;
            }
            Method get = findStatic(cls, getName, argClass, 1);
            Method add = findStatic(cls, addName, argClass, 2);
            Method set = findStatic(cls, setName, argClass, 2);
            if (get != null && (add != null || set != null)) {
                return new ReflectiveEconomy(className, order[i], null, get, add, set);
            }
        }
        return null;
    }

    @Nullable
    public static Class<?> argClass(ArgType type) {
        try {
            switch (type) {
                case UUID_ARG:
                    return UUID.class;
                case PLAYER:
                    return EntityPlayer.class;
                case GAME_PROFILE:
                    return Class.forName("com.mojang.authlib.GameProfile");
                case FORGE_PLAYER:
                    return Class.forName("com.feed_the_beast.ftblib.lib.data.ForgePlayer");
                default:
                    return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isPlayerArg(Class<?> type) {
        if (type == null) {
            return false;
        }
        String name = type.getName();
        return type == UUID.class
                || name.endsWith("EntityPlayer") || name.endsWith("EntityPlayerMP")
                || name.endsWith("GameProfile") || name.endsWith("ForgePlayer");
    }

    public static ArgType argTypeOf(Class<?> type) {
        String name = type.getName();
        if (type == UUID.class) {
            return ArgType.UUID_ARG;
        }
        if (name.endsWith("GameProfile")) {
            return ArgType.GAME_PROFILE;
        }
        if (name.endsWith("ForgePlayer")) {
            return ArgType.FORGE_PLAYER;
        }
        return ArgType.PLAYER;
    }

    public static boolean isNumeric(Class<?> type) {
        return type == long.class || type == int.class || type == double.class || type == float.class
                || type == Long.class || type == Integer.class || type == Double.class;
    }

    @Nullable
    private static Method findStatic(Class<?> cls, String name, Class<?> firstArg, int argCount) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Method[] methods = cls.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (!m.getName().equals(name) || !Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length != argCount || !argCompatible(params[0], firstArg)) {
                continue;
            }
            if (argCount == 2 && !isNumeric(params[1])) {
                continue;
            }
            if (argCount == 1 && !isNumeric(m.getReturnType())) {
                continue;
            }
            m.setAccessible(true);
            return m;
        }
        return null;
    }

    /**
     * Метод может принимать EntityPlayerMP вместо EntityPlayer — мост всё равно
     * передаёт туда онлайн-игрока (EntityPlayerMP), так что это совместимо.
     */
    private static boolean argCompatible(Class<?> param, Class<?> arg) {
        if (param.isAssignableFrom(arg)) {
            return true;
        }
        return arg == EntityPlayer.class && EntityPlayer.class.isAssignableFrom(param)
                && param.isAssignableFrom(EntityPlayerMP.class);
    }

    @Nullable
    private Object arg(UUID player) {
        switch (argType) {
            case UUID_ARG:
                return player;
            case PLAYER:
                return PlayerUtil.online(player);
            case GAME_PROFILE: {
                EntityPlayerMP p = PlayerUtil.online(player);
                return p == null ? null : p.getGameProfile();
            }
            case FORGE_PLAYER:
                return FTBPlayers.get(player);
            default:
                return null;
        }
    }

    @Nullable
    private Object target(UUID player) {
        Object a = arg(player);
        if (a == null || accessor == null) {
            return a;
        }
        try {
            return accessor.invoke(null, a);
        } catch (Throwable t) {
            EcoServ.LOG.error("Мост экономики: не удалось получить объект данных", t);
            return null;
        }
    }

    @Override
    public String getName() {
        return label + (addActsAsSet ? " (безопасный режим)" : "");
    }

    /** Как именно начисляются деньги — для /ecoadmin info. */
    public String describeMode() {
        if (addMethod != null && !addActsAsSet) {
            return addMethod.getName() + "(+сумма), результат проверяется";
        }
        Method writer = setMethod != null ? setMethod : addMethod;
        return (writer == null ? "?" : writer.getName()) + "(баланс + сумма), результат проверяется";
    }

    @Override
    public boolean supportsOffline() {
        return argType == ArgType.UUID_ARG || argType == ArgType.FORGE_PLAYER;
    }

    /** Для показа: при любой ошибке 0. Для изменения баланса используется {@link #read}. */
    @Override
    public long get(UUID player) {
        if (target(player) == null) {
            return 0L;
        }
        try {
            return read(player);
        } catch (Throwable e) {
            EcoServ.LOG.error("Мост экономики: ошибка чтения баланса", e);
            return 0L;
        }
    }

    /**
     * Точное чтение баланса. Если прочитать не удалось — исключение, и баланс НЕ трогаем:
     * иначе «0 + сумма» записалось бы поверх настоящего баланса (тот самый баг «set вместо add»).
     */
    private long read(UUID player) throws Exception {
        Object t = target(player);
        if (t == null) {
            throw new IllegalStateException("игрок недоступен для " + label);
        }
        Object result = accessor == null ? getMethod.invoke(null, t) : getMethod.invoke(t);
        if (!(result instanceof Number)) {
            throw new IllegalStateException(getMethod.getName() + " вернул " + result);
        }
        return ((Number) result).longValue();
    }

    // --- низкоуровневые вызовы ----------------------------------------------

    private void invoke(Method method, UUID player, long value) throws Exception {
        Object t = target(player);
        if (t == null) {
            throw new IllegalStateException("игрок недоступен для чужого API");
        }
        if (accessor == null) {
            method.invoke(null, t, cast(method, 1, value));
        } else {
            method.invoke(t, cast(method, 0, value));
        }
    }

    /** Записать абсолютное значение баланса самым надёжным способом. */
    private void writeAbsolute(UUID player, long value) throws Exception {
        long safe = Math.max(0L, value);
        if (setMethod != null) {
            invoke(setMethod, player, safe);
        } else if (addMethod != null && addActsAsSet) {
            invoke(addMethod, player, safe);
        } else if (addMethod != null) {
            invoke(addMethod, player, safe - read(player));
        }
    }

    /**
     * Прибавить (или отнять) с проверкой результата.
     *
     * @throws IllegalStateException если баланс не удалось прочитать — тогда ничего не изменено
     */
    private boolean applyDelta(UUID player, long delta) {
        if (delta == 0L) {
            return true;
        }
        long before;
        try {
            before = read(player);
        } catch (Throwable t) {
            throw new IllegalStateException("Мост экономики " + label + ": баланс не прочитан, операция отменена", t);
        }
        long expected = before + delta;
        try {
            if (addMethod != null && !addActsAsSet && (delta > 0L || setMethod == null)) {
                invoke(addMethod, player, delta);
                long after = get(player);
                if (after == expected) {
                    return true;
                }
                if (before != 0L && after == Math.max(0L, delta)) {
                    // «add» на самом деле перезаписал баланс — чиним и больше так не вызываем
                    addActsAsSet = true;
                    EcoServ.LOG.error("Мост экономики {}: метод {} ПЕРЕЗАПИСЫВАЕТ баланс вместо прибавления. "
                            + "Баланс исправлен ({} -> {}), дальше используется set(get + сумма).",
                            label, addMethod.getName(), after, expected);
                    writeAbsolute(player, expected);
                    return get(player) == expected;
                }
                warnMismatch(before, delta, after);
                return true;
            }
            writeAbsolute(player, expected);
            long after = get(player);
            if (after != Math.max(0L, expected)) {
                warnMismatch(before, delta, after);
            }
            return true;
        } catch (Throwable t) {
            EcoServ.LOG.error("Мост экономики: ошибка изменения баланса", t);
            return false;
        }
    }

    private void warnMismatch(long before, long delta, long after) {
        if (warnedMismatch) {
            return;
        }
        warnedMismatch = true;
        EcoServ.LOG.warn("Мост экономики {}: было {}, изменение {}, ожидали {}, стало {}. "
                + "Проверь integration.custom_* в конфиге или поставь economy_provider=internal.",
                label, before, delta, before + delta, after);
    }

    private static Object cast(Method m, int paramIndex, long value) {
        Class<?> p = m.getParameterTypes()[paramIndex];
        if (p == int.class || p == Integer.class) {
            return Integer.valueOf((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value)));
        }
        if (p == double.class || p == Double.class) {
            return Double.valueOf(value);
        }
        if (p == float.class) {
            return Float.valueOf(value);
        }
        return Long.valueOf(value);
    }

    // --- IEconomy -----------------------------------------------------------

    @Override
    public void set(UUID player, long amount) {
        try {
            writeAbsolute(player, amount);
        } catch (Throwable t) {
            EcoServ.LOG.error("Мост экономики: ошибка установки баланса", t);
        }
    }

    @Override
    public boolean take(UUID player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (get(player) < amount) {
            return false;
        }
        try {
            return applyDelta(player, -amount);
        } catch (IllegalStateException e) {
            EcoServ.LOG.error(e.getMessage(), e.getCause());
            return false;
        }
    }

    /**
     * Если игрок недоступен или баланс не прочитался, бросает исключение: EconomyManager
     * ловит его и откладывает выплату до следующего входа игрока — деньги не теряются
     * и не перезаписывают баланс.
     */
    @Override
    public void give(UUID player, long amount) {
        if (amount <= 0L) {
            return;
        }
        if (target(player) == null) {
            throw new IllegalStateException("игрок недоступен для " + label);
        }
        applyDelta(player, amount);
    }

    @Override
    public List<Map.Entry<UUID, Long>> top(int count) {
        return Collections.emptyList();
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {
    }
}
