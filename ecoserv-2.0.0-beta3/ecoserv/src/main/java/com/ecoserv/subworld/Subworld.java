package com.ecoserv.subworld;

import java.util.ArrayList;
import java.util.List;

/**
 * Подмир (п. 3): отдельный мир со своими настройками. Хранится в world/ecoserv/subworlds.json.
 * Если включены локальные миры — у каждой команды (или игрока) своя копия мира.
 */
public class Subworld {

    /** Типы мира. */
    public static final String NORMAL = "normal";
    public static final String NETHER = "nether";
    public static final String END = "end";
    public static final String FLAT = "flat";
    public static final String VOID = "void";
    public static final String CUSTOM = "custom";

    /** Способы получить доступ. */
    public static final String ACCESS_ALWAYS = "always";
    public static final String ACCESS_PRICE = "price";
    public static final String ACCESS_ADVANCEMENT = "advancement";
    public static final String ACCESS_QUEST = "quest";
    public static final String ACCESS_ROLE = "role";
    /** 1.9.0: личный мир игрока — владелец, приглашённые и админы. */
    public static final String ACCESS_PRIVATE = "private";

    /** id: буквы, цифры, _ (им пользуются /world, запреты, инвентари). */
    public String id = "";
    /** Название для людей (& — цвета). */
    public String name = "";
    public String description = "";
    /** Предмет-иконка в меню. */
    public String icon = "minecraft:grass";

    // --- мир ------------------------------------------------------------------
    public String type = NORMAL;
    /** Для normal: тип генерации (default, largeBiomes, amplified, customized или тип мода). */
    public String worldType = "default";
    /** Настройки генератора (для плоского — строка пресета). */
    public String generatorOptions = "";
    public long seed;
    public boolean structures = true;
    /** Для custom: имя загруженного мира (папка в ecoserv_uploads). */
    public String template = "";

    // --- доступ ---------------------------------------------------------------
    public String access = ACCESS_ALWAYS;
    /** Для price: цена разового доступа. */
    public long price;
    /** Для advancement: id достижения Minecraft. */
    public String advancement = "";
    /** Для role: одна из ролей. */
    public List<String> roles = new ArrayList<String>();
    /** Дополнительно: проверка возраста (пусто — нет). */
    public String age = "";

    // --- локальные миры -------------------------------------------------------
    public boolean localWorlds;
    /** Сколько сохранений (локальных миров) может быть у команды. */
    public int maxSaves = 1;
    /** Цена каждого сохранения сверх первого. */
    public long savePrice;
    /** Барьер локального мира: начальный радиус (0 — без барьера), шаг расширения и предел. */
    public int borderStart;
    public int borderStep = 16;
    public int borderMax;

    // --- своё содержимое --------------------------------------------------------
    /** Свои квесты (главы FTB Quests) и свой прайс магазина/продаж. */
    public boolean ownQuests;
    /** Главы FTB Quests подмира (id в hex, как в файлах квестов). */
    public List<String> questChapters = new ArrayList<String>();

    // --- GLITCH -----------------------------------------------------------------
    /** Мир с пометкой GLITCH: локальный мир у каждого ИГРОКА (не команды), там стоят ГЛИТЧ-сундуки. */
    public boolean glitch;
    public Abyss abyss = new Abyss();

    // --- мини-игра (1.8.0) ---------------------------------------------------------
    /**
     * Арена «Квестовой гонки»: на каждую игру создаётся своя копия опубликованного шаблона, у игроков там
     * ВСЕГДА свой (пустой) инвентарь, после игры копия удаляется. Обычным входом туда не попасть — только через /race.
     */
    public boolean minigame;
    /** Не показывать кнопку «Войти» в меню миров (попасть можно порталом, командой админа, гонкой). */
    public boolean noMenuTeleport;
    /** 1.9.0: мир игрока — UUID владельца (пусто — мир сервера) и приглашённые. */
    public String owner = "";
    public String ownerName = "";
    public List<String> members = new ArrayList<String>();

    // --- небо ---------------------------------------------------------------------
    /** default (по типу мира), overworld, nether, end, dim:&lt;номер&gt;, color. */
    public String sky = "default";
    public int skyColor = 0x101020;
    /** Цвет тумана (-1 — как у неба / по типу мира). */
    public int fogColor = -1;

    // --- правила (своя фишка 1) ----------------------------------------------------
    public Rules rules = new Rules();

    // --- гости (своя фишка 2) -------------------------------------------------------
    /** Владельцы локальных миров могут приглашать гостей. */
    public boolean guests = true;

    public boolean respawnInside = true;
    public boolean enabled = true;
    public String creator = "";
    public long created;
    /** Версия опубликованного шаблона (глобальное редактирование). */
    public int templateVersion;

    /** «Глитч-бездна» (п. 21). */
    public static class Abyss {
        public boolean enabled;
        /** Ниже какой высоты срабатывает. */
        public int y = -16;
        /** Тексты окна ENGINE ERROR на разных языках (ru_ru, en_us, ...). */
        public List<LangText> texts = new ArrayList<LangText>();
        /** Эффекты исчезают через N секунд после нажатия ОК («исчезновение после галочки»). */
        public boolean waitForOk = true;
        public int fadeSeconds = 3;
        /** Если не ждать ОК — эффекты длятся столько секунд. */
        public int effectSeconds = 8;
        public boolean shader = true;
        public boolean overlay = true;
        /** Видно другим: воронка из частиц и «битые пиксели» там, где игрок упал и где появился. */
        public boolean particles = true;
        /** Искажение пространства вокруг точки падения (как у чёрной дыры) — у тех, кто рядом. */
        public boolean distort = true;

        public void normalize() {
            if (texts == null) {
                texts = new ArrayList<LangText>();
            }
            if (texts.isEmpty()) {
                texts.add(new LangText("ru_ru", "ENGINE ERROR",
                        "Ошибка расчёта координат: позиция игрока вышла за допустимые пределы мира (Y < 0). "
                                + "Выполнен аварийный возврат на точку возрождения.", "ОК"));
                texts.add(new LangText("en_us", "ENGINE ERROR",
                        "Coordinate calculation error: player position is outside the valid world range (Y < 0). "
                                + "Emergency return to the spawn point.", "OK"));
            }
            fadeSeconds = Math.max(0, Math.min(600, fadeSeconds));
            effectSeconds = Math.max(1, Math.min(600, effectSeconds));
        }
    }

    public static class LangText {
        public String lang = "ru_ru";
        public String title = "ENGINE ERROR";
        public String text = "";
        public String button = "OK";

        public LangText() {
        }

        public LangText(String lang, String title, String text, String button) {
            this.lang = lang;
            this.title = title;
            this.text = text;
            this.button = button;
        }
    }

    /** Правила подмира. */
    public static class Rules {
        /** cycle (обычная смена дня и ночи), day, noon, sunset, night, midnight или число тиков 0–23999. */
        public String time = "cycle";
        /** cycle, clear, rain, thunder. */
        public String weather = "cycle";
        public boolean pvp = true;
        public boolean keepInventory;
        public boolean mobs = true;
        public boolean fireSpread = true;
        public boolean mobGriefing = true;
        /** Режим игры при входе: пусто — не менять, survival, adventure, creative. */
        public String gamemode = "";
        /** Заголовок при входе «заголовок|подзаголовок» (пусто — название подмира). */
        public String welcome = "";
        /** Предметы при первом входе (ItemCodec). */
        public List<String> starterKit = new ArrayList<String>();
        /** Доп. правила: без голода, полёт, защита спавна, эффекты... */
        public com.ecoserv.world.WorldExtras extras = new com.ecoserv.world.WorldExtras();

        public void normalize() {
            if (time == null || time.isEmpty()) {
                time = "cycle";
            }
            if (weather == null || weather.isEmpty()) {
                weather = "cycle";
            }
            if (gamemode == null) {
                gamemode = "";
            }
            if (welcome == null) {
                welcome = "";
            }
            if (starterKit == null) {
                starterKit = new ArrayList<String>();
            }
            if (extras == null) {
                extras = new com.ecoserv.world.WorldExtras();
            }
            extras.normalize();
        }
    }

    public void normalize() {
        if (owner == null) {
            owner = "";
        }
        if (ownerName == null) {
            ownerName = "";
        }
        if (members == null) {
            members = new ArrayList<String>();
        }
        if (name == null || name.isEmpty()) {
            name = id;
        }
        if (description == null) {
            description = "";
        }
        if (icon == null || icon.isEmpty()) {
            icon = "minecraft:grass";
        }
        if (type == null || type.isEmpty()) {
            type = NORMAL;
        }
        if (worldType == null || worldType.isEmpty()) {
            worldType = "default";
        }
        if (generatorOptions == null) {
            generatorOptions = "";
        }
        if (template == null) {
            template = "";
        }
        if (access == null || access.isEmpty()) {
            access = ACCESS_ALWAYS;
        }
        if (advancement == null) {
            advancement = "";
        }
        if (roles == null) {
            roles = new ArrayList<String>();
        }
        if (age == null) {
            age = "";
        }
        if (questChapters == null) {
            questChapters = new ArrayList<String>();
        }
        if (abyss == null) {
            abyss = new Abyss();
        }
        abyss.normalize();
        if (rules == null) {
            rules = new Rules();
        }
        rules.normalize();
        if (sky == null || sky.isEmpty()) {
            sky = "default";
        }
        if (minigame) {
            // арена: шаблон + копии на каждую игру; GLITCH и мини-игра не совмещаются
            glitch = false;
            localWorlds = true;
        }
        if (glitch) {
            // GLITCH-мир всегда локальный для игрока и с одним сохранением
            localWorlds = true;
            maxSaves = 1;
        }
        maxSaves = Math.max(1, Math.min(64, maxSaves));
        price = Math.max(0L, price);
        savePrice = Math.max(0L, savePrice);
        borderStart = Math.max(0, borderStart);
        borderStep = Math.max(1, borderStep);
        borderMax = Math.max(0, borderMax);
    }

    public String typeTitle() {
        switch (type) {
            case NETHER:
                return "ад";
            case END:
                return "край";
            case FLAT:
                return "плоский";
            case VOID:
                return "пустота";
            case CUSTOM:
                return "свой мир «" + template + "»";
            default:
                return "обычный" + ("default".equals(worldType) ? "" : " (" + worldType + ")");
        }
    }

    public String accessTitle() {
        switch (access) {
            case ACCESS_PRICE:
                return "за " + price + " монет";
            case ACCESS_ADVANCEMENT:
                return "за достижение " + advancement;
            case ACCESS_QUEST:
                return "наградой в квестах";
            case ACCESS_ROLE:
                return "по роли: " + String.join(", ", roles);
            case ACCESS_PRIVATE:
                return "личный мир " + ownerName;
            default:
                return "всегда";
        }
    }

    /** Мир игрока (1.9.0), а не сервера. */
    public boolean playerOwned() {
        return owner != null && !owner.isEmpty();
    }

    /** Владелец или приглашённый. */
    public boolean memberOf(java.util.UUID player) {
        String id = player.toString();
        return id.equals(owner) || members.contains(id);
    }

    /** Мир у каждой команды свой (или у каждого игрока для GLITCH). */
    public boolean perOwner() {
        return localWorlds || glitch || minigame;
    }

    public boolean perPlayer() {
        return glitch;
    }
}
