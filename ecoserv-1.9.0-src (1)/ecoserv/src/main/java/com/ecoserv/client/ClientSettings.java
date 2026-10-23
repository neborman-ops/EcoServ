package com.ecoserv.client;

import com.ecoserv.EcoServ;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Клиентские настройки игрока (1.9.0): только на его компьютере, config/ecoserv-client.json.
 * Открываются шестерёнкой у инвентаря, в меню миров или клавишей.
 */
@SideOnly(Side.CLIENT)
public final class ClientSettings {

    public static final String[] THEMES = {"dark", "midnight", "amethyst", "forest", "classic"};
    public static final String[] REQUESTS = {"all", "team", "none"};

    // --- вид -------------------------------------------------------------------------------------------
    /** Язык текстов мода: auto (как в игре) или код языка (ru_ru, en_us…). */
    public String modLanguage = "auto";
    /** Тема окон мода: dark, midnight, amethyst, forest, classic. */
    public String theme = "dark";
    /** Показывать «хлебные крошки» — в каком меню находишься. */
    public boolean breadcrumbs = true;
    /** Прятать слоты своего инвентаря в меню-сундуках мода. */
    public boolean hideInventorySlots = false;
    /** Анимированные иконки в меню. */
    public boolean animatedIcons = true;

    // --- эффекты ----------------------------------------------------------------------------------------
    /** Качество чёрных дыр: -1 авто, 0 низкое, 1 среднее, 2 высокое, 3 максимальное (точный расчёт луча). */
    public int holeQuality = -1;
    /** Сила искажений экрана, %. */
    public int distortion = 100;
    /** Искажать ли интерфейс (хотбар, чат, меню). */
    public boolean distortHud = true;
    /** Яркие вспышки (выключить — для чувствительных к вспышкам). */
    public boolean flashes = true;
    /** Показывать игроков и мобов в окнах порталов. */
    public boolean portalEntities = true;
    /** Глитч-эффект на весь экран при падении в бездну. */
    public boolean screenGlitch = true;

    // --- звук ------------------------------------------------------------------------------------------
    /** Громкость звуков мода, %. */
    public int soundVolume = 100;
    /** Щелчки кнопок в меню. */
    public boolean uiSounds = true;

    // --- запросы ---------------------------------------------------------------------------------------
    /** Кто может присылать обмен: all, team, none. */
    public String tradeRequests = "all";
    /** Кто может просить денег / платить с подтверждением. */
    public String moneyRequests = "all";
    /** Приглашения в команды и миры. */
    public String inviteRequests = "all";
    /** Запросы компьютеров (ComputerCraft). */
    public boolean computerRequests = true;
    /** Всплывающие уведомления мода. */
    public boolean toasts = true;

    private static ClientSettings instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Автоматически выбранное качество (по видеокарте и FPS). */
    private static int autoQuality = -2;
    private static long slowFrames;
    private static long fastFrames;

    public static ClientSettings get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().gameDir, "config/ecoserv-client.json");
    }

    public static void load() {
        ClientSettings s = null;
        File f = file();
        if (f.isFile()) {
            try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                s = GSON.fromJson(r, ClientSettings.class);
            } catch (Exception e) {
                EcoServ.LOG.warn("Клиентские настройки не прочитались, беру по умолчанию: {}", e.toString());
            }
        }
        instance = s == null ? new ClientSettings() : s;
        instance.normalize();
        com.ecoserv.client.ui.UiTheme.apply(instance.theme);
    }

    public void save() {
        normalize();
        File f = file();
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            EcoServ.LOG.warn("Клиентские настройки не сохранились: {}", e.toString());
        }
        com.ecoserv.client.ui.UiTheme.apply(theme);
        ClientHooks.sendPrefs();
    }

    private void normalize() {
        theme = pick(theme, THEMES);
        tradeRequests = pick(tradeRequests, REQUESTS);
        moneyRequests = pick(moneyRequests, REQUESTS);
        inviteRequests = pick(inviteRequests, REQUESTS);
        holeQuality = Math.max(-1, Math.min(3, holeQuality));
        distortion = Math.max(0, Math.min(200, distortion));
        soundVolume = Math.max(0, Math.min(200, soundVolume));
    }

    private static String pick(String v, String[] allowed) {
        if (v != null) {
            for (String a : allowed) {
                if (a.equals(v)) {
                    return a;
                }
            }
        }
        return allowed[0];
    }

    // --- качество чёрных дыр ----------------------------------------------------------------------------

    /** Итоговое качество 0..3: своё или автоматическое по видеокарте и тому, как идёт игра. */
    public static int quality() {
        int q = get().holeQuality;
        if (q >= 0) {
            return q;
        }
        if (autoQuality == -2) {
            autoQuality = guessByGpu();
        }
        return autoQuality;
    }

    private static int guessByGpu() {
        try {
            String r = String.valueOf(GL11.glGetString(GL11.GL_RENDERER)).toLowerCase(Locale.ROOT);
            String v = String.valueOf(GL11.glGetString(GL11.GL_VENDOR)).toLowerCase(Locale.ROOT);
            if (r.contains("llvmpipe") || r.contains("software") || r.contains("gdi generic")) {
                return 0;
            }
            if (r.contains("rtx") || r.contains("rx 6") || r.contains("rx 7") || r.contains("radeon rx")) {
                return 3;
            }
            if (v.contains("intel") || r.contains("intel") || r.contains("mali") || r.contains("adreno")) {
                return 1;
            }
            return 2;
        } catch (Throwable t) {
            return 1;
        }
    }

    /**
     * Подстройка «на лету», пока эффект на экране: FPS ниже 40 — снижаем качество, выше 110 — повышаем
     * (не выше того, что подходит видеокарте, + 1).
     */
    public static void frame(int fps) {
        if (get().holeQuality >= 0 || autoQuality < 0 || fps <= 0) {
            return;
        }
        if (fps < 40) {
            slowFrames++;
            fastFrames = 0;
            if (slowFrames > 120 && autoQuality > 0) {
                autoQuality--;
                slowFrames = 0;
            }
        } else if (fps > 110) {
            fastFrames++;
            slowFrames = 0;
            if (fastFrames > 900 && autoQuality < Math.min(3, guessByGpu() + 1)) {
                autoQuality++;
                fastFrames = 0;
            }
        } else {
            slowFrames = Math.max(0, slowFrames - 1);
        }
    }

    public static String qualityTitle(int q) {
        switch (q) {
            case 0:
                return "Низкое";
            case 1:
                return "Среднее";
            case 2:
                return "Высокое";
            case 3:
                return "Максимальное (точный луч)";
            default:
                return "Авто";
        }
    }

    /** Множитель вспышек для шейдеров (0.25 — вспышки приглушены). */
    public static float flash() {
        return get().flashes ? 1.0F : 0.25F;
    }

    public static float distortion() {
        return get().distortion / 100.0F;
    }
    /** Код языка мода на этом клиенте («Как в игре» — язык Minecraft). */
    public static String locale() {
        ClientSettings s = get();
        if (s.modLanguage == null || s.modLanguage.isEmpty() || "auto".equals(s.modLanguage)) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            return mc.gameSettings == null ? "ru_ru" : mc.gameSettings.language;
        }
        return s.modLanguage;
    }

    /** Перевести русский текст клиентского окна на язык мода (словари те же, что у сервера). */
    public static String t(String text) {
        return com.ecoserv.lang.ServerLang.tr(text, com.ecoserv.lang.ServerLang.dictFor(locale()));
    }
}
