package com.ecoserv.cc;

import com.ecoserv.EcoServ;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ключи блоков для ComputerCraft: владелец терминала/склада/автомата задаёт секрет (/ecocc key, смотря на блок),
 * программа на его компьютере вызывает auth(секрет) — и только тогда ей доступны действия владельца
 * (выдать товар, поменять цену, выплатить игроку). Чужой компьютер, подключённый к тому же блоку модемом,
 * секрета не знает. Хранятся хэши: world/ecoserv/cc_keys.json.
 */
public final class CcKeys {

    private static final Type TYPE = new TypeToken<HashMap<String, String>>() {
    }.getType();
    private static Map<String, String> keys;
    /** «блок|компьютер», прошедшие auth в этом запуске сервера. */
    private static final Set<String> AUTHORIZED = new HashSet<String>();

    private CcKeys() {
    }

    public static String blockKey(int dim, BlockPos pos) {
        return dim + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private static synchronized Map<String, String> keys() {
        if (keys == null) {
            keys = JsonUtil.readType(DataStore.file("cc_keys.json"), TYPE, new HashMap<String, String>());
            if (keys == null) {
                keys = new HashMap<String, String>();
            }
        }
        return keys;
    }

    public static synchronized void unload() {
        keys = null;
        AUTHORIZED.clear();
    }

    static String hash(String secret) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("ecoserv:" + secret).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(secret.hashCode());
        }
    }

    public static synchronized void set(String block, String secret) {
        if (secret == null || secret.isEmpty()) {
            keys().remove(block);
        } else {
            keys().put(block, hash(secret));
        }
        AUTHORIZED.removeIf(a -> a.startsWith(block + "|"));
        if (!JsonUtil.write(DataStore.file("cc_keys.json"), keys())) {
            EcoServ.LOG.warn("Не удалось записать cc_keys.json");
        }
    }

    public static synchronized boolean hasKey(String block) {
        return keys().containsKey(block);
    }

    public static synchronized boolean auth(String block, int computer, String secret) {
        String h = keys().get(block);
        if (h == null || secret == null || !h.equals(hash(secret))) {
            return false;
        }
        AUTHORIZED.add(block + "|" + computer);
        return true;
    }

    public static synchronized boolean authorized(String block, int computer) {
        return AUTHORIZED.contains(block + "|" + computer);
    }
}
