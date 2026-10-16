package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketSubworldSync;
import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Тип измерения подмиров и регистрация измерений в Forge (на сервере — при старте и при создании,
 * на клиенте — по пакету синхронизации, как это делают моды динамических измерений).
 */
public final class SubworldDims {

    public static DimensionType TYPE;

    /** Измерения, зарегистрированные нами на этой стороне. */
    private static final Set<Integer> SERVER_REGISTERED = new HashSet<Integer>();
    public static final Set<Integer> CLIENT_REGISTERED = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    /** Клиент: что известно об измерениях подмиров (небо, тип...). */
    public static final Map<Integer, SubworldView> CLIENT_VIEWS = new ConcurrentHashMap<Integer, SubworldView>();
    /** Сервер: готовый список для отправки при подключении (читается из сетевого потока). */
    private static volatile List<SubworldView> cachedViews = new ArrayList<SubworldView>();

    private SubworldDims() {
    }

    /** preInit на обеих сторонах. */
    public static void registerType() {
        if (TYPE == null) {
            TYPE = DimensionType.register("ecoserv_subworld", "_ecoserv", EcoConfig.subworldsDimTypeId,
                    WorldProviderSubworld.class, false);
        }
    }

    // --- сервер -------------------------------------------------------------------

    public static void registerAll() {
        for (SubworldDim d : SubworldRegistry.dims()) {
            register(d.dim);
        }
        refreshCache();
    }

    public static void register(int dim) {
        if (!DimensionManager.isDimensionRegistered(dim)) {
            try {
                DimensionManager.registerDimension(dim, TYPE);
            } catch (IllegalArgumentException e) {
                // в одиночной игре клиент мог успеть зарегистрировать его сам (общий список измерений)
                EcoServ.LOG.debug("Измерение {} уже зарегистрировано: {}", dim, e.toString());
            }
            SERVER_REGISTERED.add(dim);
        } else if (DimensionManager.getProviderType(dim) != TYPE) {
            EcoServ.LOG.error("Измерение {} уже занято другим модом — подмир в нём работать не будет. "
                    + "Поменяй subworlds.dim_id_start.", dim);
        }
    }

    /** При остановке сервера (важно для одиночной игры: следующий мир не должен видеть чужие измерения). */
    public static void unregisterAll() {
        for (Integer dim : SERVER_REGISTERED) {
            if (DimensionManager.isDimensionRegistered(dim)) {
                try {
                    DimensionManager.unregisterDimension(dim);
                } catch (Exception e) {
                    EcoServ.LOG.debug("Не удалось снять регистрацию измерения {}: {}", dim, e.toString());
                }
            }
        }
        SERVER_REGISTERED.clear();
        cachedViews = new ArrayList<SubworldView>();
    }

    /** Измерение снято с регистрации (удалено). */
    public static void forget(int dim) {
        SERVER_REGISTERED.remove(dim);
    }

    /** Новое измерение во время игры: регистрация и рассылка всем клиентам. */
    public static void registerAndSync(SubworldDim d) {
        register(d.dim);
        refreshCache();
        Subworld sw = SubworldRegistry.get(d.subworld);
        if (sw != null) {
            List<SubworldView> one = new ArrayList<SubworldView>();
            one.add(SubworldView.of(sw, d));
            PacketHandler.sendToAll(new PacketSubworldSync(false, one));
        }
    }

    /** Настройки подмира изменились (небо и т.п.) — разослать. */
    public static void sync(Subworld sw) {
        refreshCache();
        List<SubworldView> views = new ArrayList<SubworldView>();
        for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
            views.add(SubworldView.of(sw, d));
        }
        if (!views.isEmpty()) {
            PacketHandler.sendToAll(new PacketSubworldSync(false, views));
        }
    }

    public static void refreshCache() {
        List<SubworldView> list = new ArrayList<SubworldView>();
        for (SubworldDim d : SubworldRegistry.dims()) {
            Subworld sw = SubworldRegistry.get(d.subworld);
            if (sw != null) {
                list.add(SubworldView.of(sw, d));
            }
        }
        cachedViews = list;
    }

    /** Полный список для игрока при подключении. */
    public static PacketSubworldSync fullSync() {
        return new PacketSubworldSync(true, new ArrayList<SubworldView>(cachedViews));
    }

    // --- клиент ---------------------------------------------------------------------

    /** Вызывается на главном потоке клиента. */
    public static void applyOnClient(boolean full, List<SubworldView> views) {
        if (full) {
            for (Integer dim : new ArrayList<Integer>(CLIENT_REGISTERED)) {
                boolean still = false;
                for (SubworldView v : views) {
                    if (v.dim == dim) {
                        still = true;
                    }
                }
                if (!still) {
                    unregisterClient(dim);
                }
            }
            CLIENT_VIEWS.clear();
        }
        for (SubworldView v : views) {
            CLIENT_VIEWS.put(v.dim, v);
            if (!DimensionManager.isDimensionRegistered(v.dim)) {
                DimensionManager.registerDimension(v.dim, TYPE);
                CLIENT_REGISTERED.add(v.dim);
            }
        }
    }

    /** Отключились от сервера — убрать то, что регистрировал клиент. */
    public static void clearClient() {
        for (Integer dim : new ArrayList<Integer>(CLIENT_REGISTERED)) {
            unregisterClient(dim);
        }
        CLIENT_VIEWS.clear();
    }

    private static void unregisterClient(int dim) {
        CLIENT_REGISTERED.remove(dim);
        if (DimensionManager.isDimensionRegistered(dim) && DimensionManager.getProviderType(dim) == TYPE) {
            try {
                DimensionManager.unregisterDimension(dim);
            } catch (Exception ignored) {
                // уже снято
            }
        }
    }

    @Nullable
    public static SubworldView clientView(int dim) {
        return CLIENT_VIEWS.get(dim);
    }
}
