package com.ecoserv.activator;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockLever;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Именованные кнопки и рычаги. Файл: world/ecoserv/activators.json */
public final class ActivatorManager {

    private static final Type LIST_TYPE = new TypeToken<ArrayList<Activator>>() {
    }.getType();

    private static final List<Activator> LIST = new ArrayList<Activator>();
    private static final Map<String, Activator> BY_POS = new HashMap<String, Activator>();
    private static final List<ActivationListener> LISTENERS = new CopyOnWriteArrayList<ActivationListener>();
    private static final Map<String, Long> LAST_TRIGGER = new HashMap<String, Long>();
    private static boolean dirty;

    private ActivatorManager() {
    }

    public static void addListener(ActivationListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static boolean hasQuestListener() {
        return !LISTENERS.isEmpty();
    }

    public static List<Activator> all() {
        return Collections.unmodifiableList(LIST);
    }

    @Nullable
    public static Activator get(int dim, BlockPos pos) {
        return BY_POS.get(Activator.key(dim, pos.getX(), pos.getY(), pos.getZ()));
    }

    public static List<Activator> byName(String name) {
        List<Activator> out = new ArrayList<Activator>();
        for (int i = 0; i < LIST.size(); i++) {
            if (LIST.get(i).name.equalsIgnoreCase(name.trim())) {
                out.add(LIST.get(i));
            }
        }
        return out;
    }

    public static Set<String> names() {
        Set<String> set = new LinkedHashSet<String>();
        for (int i = 0; i < LIST.size(); i++) {
            set.add(LIST.get(i).name);
        }
        return set;
    }

    public static Activator bind(int dim, BlockPos pos, String name, String creator) {
        Activator existing = get(dim, pos);
        if (existing != null) {
            existing.name = name;
            save();
            return existing;
        }
        Activator activator = new Activator(name, dim, pos, creator);
        LIST.add(activator);
        BY_POS.put(activator.key(), activator);
        save();
        return activator;
    }

    @Nullable
    public static Activator unbind(int dim, BlockPos pos) {
        Activator existing = get(dim, pos);
        if (existing != null) {
            LIST.remove(existing);
            BY_POS.remove(existing.key());
            save();
        }
        return existing;
    }

    public static int removeByName(String name) {
        List<Activator> found = byName(name);
        for (int i = 0; i < found.size(); i++) {
            LIST.remove(found.get(i));
            BY_POS.remove(found.get(i).key());
        }
        if (!found.isEmpty()) {
            save();
        }
        return found.size();
    }

    public static void changed() {
        save();
    }

    // --- какие блоки подходят -----------------------------------------------

    /**
     * Только кнопки и рычаги (в том числе кнопки модов, унаследованные от ванильных).
     * Двери, калитки, нажимные плиты и растяжки тоже имеют свойство powered, но это не «нажатие».
     */
    public static boolean isActivatorBlock(IBlockState state) {
        Block block = state.getBlock();
        return block instanceof BlockButton || block instanceof BlockLever;
    }

    /** Значение свойства powered или null, если его у блока нет. */
    @Nullable
    public static Boolean poweredValue(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if ("powered".equals(property.getName()) && Boolean.class.equals(property.getValueClass())) {
                Object value = state.getValue(property);
                return value instanceof Boolean ? (Boolean) value : null;
            }
        }
        return null;
    }

    // --- срабатывание -------------------------------------------------------

    /**
     * Сообщить обработчикам (условию «Активация» в квестах, скрытым квестам) об активации
     * с этим названием — без кнопки: зоны-триггеры, сценарии, датчики.
     */
    public static void notifyListeners(EntityPlayerMP player, String name) {
        if (player == null || name == null || name.trim().isEmpty()) {
            return;
        }
        for (ActivationListener listener : LISTENERS) {
            try {
                listener.onActivated(player, name);
            } catch (Throwable t) {
                EcoServ.LOG.error("Ошибка обработчика активации «{}»", name, t);
            }
        }
    }

    public static void trigger(EntityPlayerMP player, Activator activator) {
        String throttleKey = player.getUniqueID() + "|" + activator.key();
        long now = System.currentTimeMillis();
        Long last = LAST_TRIGGER.get(throttleKey);
        if (last != null && now - last < 500L) {
            return;
        }
        LAST_TRIGGER.put(throttleKey, now);

        activator.uses++;
        dirty = true;

        if (EcoConfig.activatorsNotify && EcoConfig.activatorsMessage != null && !EcoConfig.activatorsMessage.isEmpty()) {
            ChatUtil.actionBar(player, EcoConfig.activatorsMessage.replace("%name%", activator.name));
        }
        notifyListeners(player, activator.name);
        if (activator.command != null && !activator.command.trim().isEmpty()) {
            MinecraftServer server = PlayerUtil.server();
            if (server != null) {
                String command = activator.command.trim()
                        .replace("%player%", player.getName())
                        .replace("%name%", activator.name);
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                try {
                    server.getCommandManager().executeCommand(server, command);
                } catch (Throwable t) {
                    EcoServ.LOG.error("Команда активатора «{}» упала: {}", activator.name, command, t);
                }
            }
        }
    }

    // --- хранение -----------------------------------------------------------

    public static void load() {
        LIST.clear();
        BY_POS.clear();
        LAST_TRIGGER.clear();
        ArrayList<Activator> loaded = JsonUtil.readType(DataStore.file("activators.json"), LIST_TYPE,
                new ArrayList<Activator>());
        for (int i = 0; i < loaded.size(); i++) {
            Activator a = loaded.get(i);
            if (a == null || a.name == null || a.name.trim().isEmpty()) {
                continue;
            }
            if (a.command == null) {
                a.command = "";
            }
            LIST.add(a);
            BY_POS.put(a.key(), a);
        }
        dirty = false;
        if (!LIST.isEmpty()) {
            EcoServ.LOG.info("Кнопок и рычагов с названиями: {}", LIST.size());
        }
    }

    public static void save() {
        dirty = !JsonUtil.write(DataStore.file("activators.json"), LIST);
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }
}
