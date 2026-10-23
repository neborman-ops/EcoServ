package com.ecoserv.auth;

import com.ecoserv.EcoServ;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Аккаунты и вход по паролю (2.0) для серверов без проверки лицензии: пока игрок не вошёл, он стоит на месте,
 * ничего не может трогать, писать в чат, выбрасывать и получать урон. Пароль — только PBKDF2-хэш с солью.
 */
public final class AuthManager {

    private static final Type MAP = new TypeToken<LinkedHashMap<String, AuthAccount>>() {
    }.getType();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static Map<String, AuthAccount> accounts = new LinkedHashMap<String, AuthAccount>();
    private static AuthSettings settings = new AuthSettings();

    /** Ещё не вошли: uuid -> состояние. */
    static final Map<UUID, Pending> PENDING = new HashMap<UUID, Pending>();

    static final class Pending {
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        int dim;
        long since = System.currentTimeMillis();
        int attempts;
        long lastHint;
    }

    private AuthManager() {
    }

    public static void load() {
        Map<String, AuthAccount> m = JsonUtil.readType(DataStore.file("accounts.json"), MAP, null);
        accounts = m == null ? new LinkedHashMap<String, AuthAccount>() : m;
        AuthSettings s = JsonUtil.read(DataStore.file("auth_settings.json"), AuthSettings.class, null);
        settings = s == null ? new AuthSettings() : s;
        JsonUtil.write(DataStore.file("auth_settings.json"), settings);
        PENDING.clear();
    }

    static void save() {
        JsonUtil.write(DataStore.file("accounts.json"), accounts);
    }

    public static AuthSettings settings() {
        return settings;
    }

    public static void saveSettings() {
        JsonUtil.write(DataStore.file("auth_settings.json"), settings);
    }

    /** Включён ли вход по паролю на этом сервере. */
    public static boolean enabled() {
        String mode = settings.mode == null ? "auto" : settings.mode;
        if ("off".equals(mode)) {
            return false;
        }
        if ("on".equals(mode)) {
            return true;
        }
        net.minecraft.server.MinecraftServer server = PlayerUtil.server();
        // одиночная игра — без паролей
        return server != null && server.isDedicatedServer() && !server.isServerInOnlineMode();
    }

    public static boolean isPending(@Nullable net.minecraft.entity.Entity e) {
        return e instanceof EntityPlayerMP && !PENDING.isEmpty() && PENDING.containsKey(e.getUniqueID());
    }

    public static boolean registered(UUID id) {
        return accounts.containsKey(id.toString());
    }

    public static int accountCount() {
        return accounts.size();
    }

    // --- хэш --------------------------------------------------------------------------------------------

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 недоступен", e);
        }
    }

    private static boolean check(AuthAccount a, String password) {
        byte[] salt = Base64.getDecoder().decode(a.salt);
        byte[] want = Base64.getDecoder().decode(a.hash);
        return MessageDigest.isEqual(want, pbkdf2(password, salt, Math.max(1000, a.iterations)));
    }

    private static void setPassword(AuthAccount a, String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        a.salt = Base64.getEncoder().encodeToString(salt);
        a.iterations = 120000;
        a.hash = Base64.getEncoder().encodeToString(pbkdf2(password, salt, a.iterations));
    }

    // --- вход / выход ---------------------------------------------------------------------------------------

    static String ip(EntityPlayerMP p) {
        try {
            String s = p.getPlayerIP();
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Игрок зашёл. */
    public static void onLogin(EntityPlayerMP p) {
        if (!enabled()) {
            return;
        }
        AuthAccount a = accounts.get(p.getUniqueID().toString());
        String ip = ip(p);
        if (a != null && settings.sessionMinutes > 0 && ip.equals(a.lastIp)
                && System.currentTimeMillis() - a.lastLogin < settings.sessionMinutes * 60000L) {
            a.lastLogin = System.currentTimeMillis();
            save();
            ChatUtil.actionBar(p, "&aС возвращением! &7(вход по сессии)");
            return;
        }
        Pending pd = new Pending();
        pd.x = p.posX;
        pd.y = p.posY;
        pd.z = p.posZ;
        pd.yaw = p.rotationYaw;
        pd.pitch = p.rotationPitch;
        pd.dim = p.dimension;
        PENDING.put(p.getUniqueID(), pd);
        hint(p);
        AuthScreen.open(p);
    }

    public static void onLogout(EntityPlayerMP p) {
        PENDING.remove(p.getUniqueID());
    }

    static void hint(EntityPlayerMP p) {
        boolean reg = registered(p.getUniqueID());
        com.ecoserv.content.Actions.title(p, reg ? "&eВход|&7/login <пароль>" : "&eРегистрация|&7/register <пароль> <пароль>",
                null, 0, 60, 10);
        ChatUtil.send(p, reg ? "&eВведи пароль: &f/login <пароль>" : "&eПридумай пароль: &f/register <пароль> <пароль ещё раз>");
    }

    /** Раз в тик: держать на месте, напоминать, выгнать по таймауту. */
    public static void tick(EntityPlayerMP p) {
        Pending pd = PENDING.get(p.getUniqueID());
        if (pd == null) {
            return;
        }
        if (p.dimension == pd.dim) {
            double dx = p.posX - pd.x;
            double dy = p.posY - pd.y;
            double dz = p.posZ - pd.z;
            if (dx * dx + dy * dy + dz * dz > 0.04D) {
                p.connection.setPlayerLocation(pd.x, pd.y, pd.z, p.rotationYaw, p.rotationPitch);
            }
        }
        p.fallDistance = 0.0F;
        long now = System.currentTimeMillis();
        if (now - pd.lastHint > 10000L) {
            pd.lastHint = now;
            p.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 260, 0, false, false));
            if (now - pd.since > 3000L) {
                hint(p);
            }
        }
        if (settings.timeoutSeconds > 0 && now - pd.since > settings.timeoutSeconds * 1000L) {
            PENDING.remove(p.getUniqueID());
            p.connection.disconnect(new TextComponentString("Время на вход вышло. Зайди снова и введи пароль."));
        }
    }

    private static void finish(EntityPlayerMP p, AuthAccount a) {
        a.lastIp = ip(p);
        a.lastLogin = System.currentTimeMillis();
        a.name = p.getName();
        save();
        PENDING.remove(p.getUniqueID());
        p.removePotionEffect(MobEffects.BLINDNESS);
        com.ecoserv.content.Actions.title(p, "&aДобро пожаловать|&7" + p.getName(), null, 0, 30, 10);
        com.ecoserv.init.ModSounds.play(p, com.ecoserv.init.ModSounds.UI_TOAST, 0.8F, 1.0F);
        if (com.ecoserv.ui.UiManager.isOpen(p, AuthScreen.class)) {
            com.ecoserv.ui.UiManager.close(p);
        }
        // 2.0: событие «вход» для сценариев разработчика
        com.ecoserv.dev.DevManager.onJoin(p);
    }

    /** null — вошёл, иначе что не так. */
    @Nullable
    public static String login(EntityPlayerMP p, String password) {
        Pending pd = PENDING.get(p.getUniqueID());
        if (pd == null) {
            return "&7Ты уже вошёл.";
        }
        AuthAccount a = accounts.get(p.getUniqueID().toString());
        if (a == null) {
            return "&cСначала зарегистрируйся: /register <пароль> <пароль>";
        }
        if (password == null || !check(a, password)) {
            pd.attempts++;
            EcoServ.LOG.info("Неверный пароль: {} ({}), попытка {}", p.getName(), ip(p), pd.attempts);
            if (pd.attempts >= settings.maxAttempts) {
                PENDING.remove(p.getUniqueID());
                p.connection.disconnect(new TextComponentString("Слишком много неверных паролей."));
                return "&cНеверный пароль.";
            }
            return "&cНеверный пароль. Осталось попыток: " + (settings.maxAttempts - pd.attempts);
        }
        finish(p, a);
        return null;
    }

    @Nullable
    public static String register(EntityPlayerMP p, String password, String again) {
        if (!PENDING.containsKey(p.getUniqueID())) {
            return "&7Ты уже вошёл.";
        }
        if (registered(p.getUniqueID())) {
            return "&cАккаунт уже есть — /login <пароль>";
        }
        if (password == null || password.length() < settings.minLength) {
            return "&cПароль слишком короткий: нужно хотя бы " + settings.minLength + " символов.";
        }
        if (!password.equals(again)) {
            return "&cПароли не совпадают.";
        }
        if (password.equalsIgnoreCase(p.getName())) {
            return "&cПароль не должен совпадать с ником.";
        }
        String ip = ip(p);
        if (settings.maxPerIp > 0) {
            int n = 0;
            for (AuthAccount a : accounts.values()) {
                if (ip.equals(a.regIp)) {
                    n++;
                }
            }
            if (n >= settings.maxPerIp) {
                return "&cС этого адреса уже зарегистрировано " + n + " аккаунтов.";
            }
        }
        AuthAccount a = new AuthAccount();
        a.name = p.getName();
        a.created = System.currentTimeMillis();
        a.regIp = ip;
        setPassword(a, password);
        accounts.put(p.getUniqueID().toString(), a);
        finish(p, a);
        return null;
    }

    @Nullable
    public static String change(EntityPlayerMP p, String old, String now) {
        AuthAccount a = accounts.get(p.getUniqueID().toString());
        if (a == null) {
            return "&cУ тебя нет аккаунта с паролем.";
        }
        if (!check(a, old)) {
            return "&cСтарый пароль неверный.";
        }
        if (now == null || now.length() < settings.minLength) {
            return "&cНовый пароль слишком короткий (минимум " + settings.minLength + ").";
        }
        setPassword(a, now);
        save();
        return null;
    }

    /** Админ: сбросить аккаунт (игрок зарегистрируется заново). */
    public static boolean reset(UUID id) {
        boolean removed = accounts.remove(id.toString()) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static List<String> names() {
        List<String> out = new ArrayList<String>();
        for (AuthAccount a : accounts.values()) {
            out.add(a.name);
        }
        return out;
    }

    @Nullable
    public static AuthAccount get(UUID id) {
        return accounts.get(id.toString());
    }
}
