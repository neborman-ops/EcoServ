package com.ecoserv.config;

import java.util.List;
import java.util.ArrayList;

import com.ecoserv.EcoServ;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

/**
 * Весь конфиг мода. Файл: config/ecoserv/ecoserv.cfg
 * Перезагружается командой /ecoadmin reload без рестарта сервера.
 */
public final class EcoConfig {

    public static final String CAT_GENERAL = "general";
    public static final String CAT_ECONOMY = "economy";
    public static final String CAT_MARKET = "market";
    public static final String CAT_BACKUP = "backup";
    public static final String CAT_TRADE = "trade";
    public static final String CAT_RESTART = "restart";
    public static final String CAT_ANNOUNCER = "announcer";
    public static final String CAT_REWARDS = "rewards";
    public static final String CAT_BLACKLIST = "blacklist";
    public static final String CAT_AUCTION = "auction";
    public static final String CAT_VENDING = "vending";
    public static final String CAT_INTEGRATION = "integration";
    public static final String CAT_WORLDS = "worlds";
    public static final String CAT_RESTRICT = "restrictions";
    public static final String CAT_ZONES = "zones";
    public static final String CAT_ACTIVATORS = "activators";
    public static final String CAT_ROLES = "roles";
    public static final String CAT_RCON = "rcon";
    public static final String CAT_SUBWORLDS = "subworlds";
    public static final String CAT_TOOLS = "tools";
    public static final String CAT_DATES = "dates";
    public static final String CAT_INVENTORIES = "inventories";

    private static Configuration cfg;
    private static File configDir;

    // --- general -----------------------------------------------------------
    public static String currencyName = "монет";
    public static String currencySymbol = "$";
    public static String chatPrefix = "&6[EcoServ]&r ";
    public static boolean showInventoryTab = true;
    public static String tabMode = "screen";
    public static int tabX = 4;
    public static int tabY = 4;
    public static boolean showTradeButton = true;
    public static boolean priceTooltip = true;
    public static int tradeTabX = 4;
    public static int tradeTabY = 30;
    public static boolean showWorldsButton = true;
    public static int worldsTabX = 4;
    public static int worldsTabY = 56;
    public static boolean requireClientMod = true;
    public static boolean adminsOpenPrivate = false;
    public static String welcomeMessage = "&aДобро пожаловать! Магазин и обмен - кнопки слева сверху в инвентаре.";
    public static int inventoryTabOffsetX = 0;
    public static int inventoryTabOffsetY = 0;

    // --- economy -----------------------------------------------------------
    public static long startingBalance = 0L;
    public static long maxBalance = 1_000_000_000_000L;
    public static boolean allowPayCommand = true;
    public static double payTaxPercent = 0.0D;
    public static long payMinAmount = 1L;
    public static int topListSize = 10;
    public static double priceMultiplier = 1.0D;
    public static double deathLossPercent = 0.0D;
    public static long deathLossMax = 0L;

    // --- market (блок-скупщик) --------------------------------------------
    public static int marketSellIntervalTicks = 20;
    public static boolean marketInstantSell = true;
    public static double marketTaxPercent = 5.0D;
    public static boolean marketRequireOwner = true;
    public static boolean marketOwnerOnlyAccess = true;
    public static boolean marketAllowHopperInput = true;
    public static boolean marketVoidUnsellable = false;
    public static boolean marketNotifyOwner = true;
    public static boolean dynamicPricingEnabled = false;
    public static double dynamicPriceMinFactor = 0.25D;
    public static double dynamicPriceDecayPerItem = 0.001D;
    public static double dynamicPriceRecoveryPerHour = 0.20D;
    public static boolean allowBuyFromGui = false;

    // --- vending (блок продажи) -------------------------------------------
    public static boolean vendingEnabled = true;
    public static double vendingTaxPercent = 0.0D;
    public static boolean vendingProtectFromOthers = true;
    public static boolean vendingAllowAdminShops = true;
    public static boolean vendingLogSales = true;
    public static long vendingDefaultPrice = 100L;
    public static int vendingDefaultAmount = 1;
    public static long vendingMaxPrice = 1_000_000_000L;
    public static boolean vendingAnnounceBig = false;
    public static long vendingAnnounceThreshold = 100_000L;

    // --- auction (аукцион игроков) ----------------------------------------
    public static boolean auctionEnabled = true;
    public static double auctionTaxPercent = 2.0D;
    public static int auctionMaxListingsPerPlayer = 20;
    public static long auctionMinPrice = 1L;
    public static long auctionMaxPrice = 1_000_000_000L;
    public static int auctionListingDays = 0;
    public static boolean auctionAnnounceNew = false;

    // --- backup ------------------------------------------------------------
    public static boolean backupEnabled = true;
    public static int backupIntervalMinutes = 60;
    public static int backupKeepCount = 24;
    public static int backupMaxAgeDays = 7;
    public static int backupMaxTotalSizeMb = 20000;
    public static String backupDir = "backups";
    public static boolean backupSkipIfNoPlayers = true;
    public static boolean backupAnnounce = true;
    public static boolean backupAnnounceOnlyAdmins = false;
    public static int backupCompressionLevel = 4;
    public static boolean backupIncludeConfig = true;
    public static boolean backupIncludeModList = true;
    public static boolean backupOnServerStop = false;
    public static int backupWarnSeconds = 10;
    public static String[] backupExclude = {"session.lock", "ecoserv/trades.log"};

    // --- trade -------------------------------------------------------------
    public static boolean tradeEnabled = true;
    public static int tradeRequestTimeoutSeconds = 60;
    public static double tradeMaxDistance = 0.0D;
    public static boolean tradeSameDimension = true;
    public static boolean tradeAllowMoney = true;
    public static boolean tradeLog = true;
    public static int tradeConfirmDelayMillis = 750;

    // --- restart -----------------------------------------------------------
    public static boolean restartEnabled = false;
    public static String[] restartTimes = {"04:00"};
    public static int[] restartWarnMinutes = {15, 10, 5, 3, 1};
    public static boolean restartBackupBefore = true;
    public static String restartMessage = "&cПерезапуск сервера через %s. Сохранитесь!";

    // --- announcer ---------------------------------------------------------
    public static boolean announcerEnabled = false;
    public static int announcerIntervalMinutes = 15;
    public static boolean announcerRandom = false;
    public static String[] announcerMessages = {
            "&eНе забудь поставить блок-скупщик, чтобы продавать ресурсы даже офлайн!",
            "&eОбмен с другими игроками: &f/trade <ник>",
            "&eБаланс и магазин: вкладка слева сверху в инвентаре."
    };

    // --- rewards -----------------------------------------------------------
    public static boolean playtimeRewardEnabled = true;
    public static int playtimeRewardMinutes = 15;
    public static long playtimeRewardAmount = 50L;
    public static int afkMinutes = 10;
    public static boolean dailyEnabled = true;
    public static long dailyAmount = 250L;
    public static long dailyStreakBonus = 50L;
    public static int dailyStreakMax = 7;

    // --- blacklist ---------------------------------------------------------
    public static boolean blacklistEnabled = false;
    public static boolean blacklistBlockPlace = true;
    public static boolean blacklistBlockUse = true;
    public static boolean blacklistBlockCraft = true;
    public static boolean blacklistAdminsBypass = true;
    public static String blacklistMessage = "&cЭтот предмет запрещён на сервере.";

    // --- worlds (/world) ---------------------------------------------------
    public static boolean worldsEnabled = true;
    public static String[] worldAliases = {"overworld=0", "nether=-1", "end=1"};
    public static String[] worldPlayerWorlds = {"overworld", "nether", "end"};
    public static int worldCooldownSeconds = 5;
    public static boolean worldReturnToLast = true;
    public static int worldSearchRadius = 16;
    public static boolean worldsSeparateBorders = true;

    // --- restrictions (частичные запреты по мирам) -------------------------
    public static boolean restrictionsEnabled = true;
    public static boolean restrictionsAdminsBypass = true;
    public static boolean restrictionsCreativeBypass = false;
    public static String restrictMessageCraft = "&cЭтот предмет нельзя крафтить в этом мире.";
    public static String restrictMessageUse = "&cЭтот предмет нельзя использовать в этом мире.";
    public static String restrictMessagePlace = "&cЭтот блок нельзя ставить в этом мире.";
    public static String restrictMessageCarry = "&cЭтот предмет нельзя носить в этом мире.";

    // --- zones (палочки BLOCK DESTROY) -------------------------------------
    public static boolean zonesEnabled = true;
    public static boolean zonesProtectExplosions = true;
    public static boolean zonesParticles = true;
    public static int zonesMargin = 6;
    public static String zonesDangerMessage = "&c⚠ Здесь ломать и ставить блоки нельзя!";

    // --- activators (палка активации) --------------------------------------
    public static boolean activatorsEnabled = true;
    public static boolean activatorsNotify = true;
    public static String activatorsMessage = "&a✔ Активировано: &f%name%";

    // --- roles (роли / теги) ----------------------------------------------
    public static boolean rolesChatPrefix = true;
    public static int rolesXaeroCooldownSeconds = 3;

    // --- rcon ---------------------------------------------------------------
    public static boolean wrapCommands = true;
    public static boolean rconMainThread = true;
    public static int rconTimeoutSeconds = 30;

    // --- inventories (раздельные инвентари по мирам) -----------------------
    public static boolean invEnabled = false;
    public static String[] invGroups = new String[0];
    public static boolean invSeparateXp = true;
    public static boolean invSeparateEnderChest = true;
    public static boolean invSeparateHealth = false;
    public static boolean invSeparateEffects = false;
    public static String[] invGamemodes = new String[0];
    public static boolean invAdminsKeepGamemode = true;
    public static boolean invNotify = true;

    // --- dates (значимые даты и дни рождения) ------------------------------
    public static boolean datesEnabled = true;
    public static boolean birthdaysEnabled = false;
    public static long birthdayMoney = 1000L;
    public static boolean birthdayBroadcast = true;
    public static String birthdayMessage = "&d🎂 С днём рождения, %player%! &7Подарок: %reward%";
    public static boolean birthdayPlayersCanChange = false;
    public static String datesTimezone = "";

    // --- tools (палочка заполнения и т.п.) ---------------------------------
    public static int fillMaxVolume = 4096;
    public static int fillBlocksPerTick = 64;

    // --- subworlds (подмиры) -------------------------------------------------
    public static boolean subworldsEnabled = true;
    public static int subworldsDimStart = 1000;
    public static int subworldsDimTypeId = 5873;
    public static int subworldsMaxInstances = 1000;
    public static String subworldsUploadsFolder = "ecoserv_uploads";
    public static int subworldsMaxUploadMb = 2048;
    public static boolean subworldsClientUpload = true;
    public static boolean subworldsWelcomeTitle = true;
    public static int subworldsPublishSyncMb = 256;
    public static int subworldsUnloadEmptySeconds = 60;

    // --- integration -------------------------------------------------------
    public static String economyProvider = "auto";
    public static boolean ftbMoneyBridge = true;
    public static boolean ftbQuestsBridge = true;
    public static boolean questCheckFailOpen = true;
    public static String customEconomyClass = "";
    public static String customGetMethod = "getMoney";
    public static String customAddMethod = "addMoney";
    public static String customSetMethod = "setMoney";

    private EcoConfig() {
    }

    public static void init(File dir) {
        configDir = dir;
        cfg = new Configuration(new File(dir, "ecoserv.cfg"));
        cfg.load();
        load();
    }

    public static File getConfigDir() {
        return configDir;
    }

    public static Configuration raw() {
        return cfg;
    }

    /** Перечитать значения из файла (/ecoadmin reload). */
    public static void reload() {
        if (cfg == null) {
            return;
        }
        cfg.load();
        load();
    }

    /** Версия формата конфига: по ней старые конфиги получают исправленные значения. */
    private static final int CONFIG_VERSION = 4;

    /**
     * Обновление старых конфигов. До 1.4.0 терминал по умолчанию открывался любым игроком
     * (owner_only_access=false уже записано в файле), поэтому один раз ставим true.
     */
    private static void migrate() {
        Property version = cfg.get(CAT_GENERAL, "config_version", 0,
                "Служебное: версия формата конфига, не менять вручную");
        int current = version.getInt(0);
        if (current < 4) {
            cfg.get(CAT_MARKET, "owner_only_access", true).set(true);
            EcoServ.LOG.info("Конфиг EcoServ обновлён: market.owner_only_access=true (терминал теперь приватный)");
        }
        if (current != CONFIG_VERSION) {
            version.set(CONFIG_VERSION);
        }
    }

    public static void load() {
        try {
            migrate();

            // general
            currencyName = cfg.getString("currency_name", CAT_GENERAL, currencyName, "Название валюты в родительном падеже: '100 монет'");
            currencySymbol = cfg.getString("currency_symbol", CAT_GENERAL, currencySymbol, "Символ валюты");
            chatPrefix = cfg.getString("chat_prefix", CAT_GENERAL, chatPrefix, "Префикс сообщений мода, & = цветовые коды");
            showInventoryTab = cfg.getBoolean("show_inventory_tab", CAT_GENERAL, showInventoryTab, "Кнопка магазина при открытом инвентаре");
            tabMode = cfg.getString("tab_mode", CAT_GENERAL, tabMode,
                    "screen - кнопка в углу экрана рядом с кнопками FTB; inventory - прикреплена к окну инвентаря");
            adminsOpenPrivate = cfg.getBoolean("admins_open_private", CAT_GENERAL, adminsOpenPrivate,
                    "Могут ли админы/операторы открывать чужие приватные блоки (по умолчанию нет, "
                            + "для управления есть /ecoadmin blockinfo и setowner)");
            requireClientMod = cfg.getBoolean("require_client_mod", CAT_GENERAL, requireClientMod,
                    "Не пускать на сервер игроков без этого мода (и с другой его версией)");
            showTradeButton = cfg.getBoolean("show_trade_button", CAT_GENERAL, showTradeButton,
                    "Отдельная кнопка обмена рядом с кнопкой магазина");
            priceTooltip = cfg.getBoolean("price_tooltip", CAT_GENERAL, priceTooltip,
                    "Показывать в подсказке предмета, сколько за него даёт скупщик (клиентская настройка)");
            tradeTabX = cfg.getInt("trade_tab_x", CAT_GENERAL, tradeTabX, 0, 4000, "Позиция кнопки обмена по X");
            tradeTabY = cfg.getInt("trade_tab_y", CAT_GENERAL, tradeTabY, 0, 4000, "Позиция кнопки обмена по Y");
            showWorldsButton = cfg.getBoolean("show_worlds_button", CAT_GENERAL, showWorldsButton,
                    "Кнопка-планета в инвентаре: меню миров и подмиров");
            worldsTabX = cfg.getInt("worlds_tab_x", CAT_GENERAL, worldsTabX, 0, 4000, "Позиция кнопки-планеты по X");
            worldsTabY = cfg.getInt("worlds_tab_y", CAT_GENERAL, worldsTabY, 0, 4000, "Позиция кнопки-планеты по Y");
            welcomeMessage = cfg.getString("welcome_message", CAT_GENERAL, welcomeMessage,
                    "Сообщение новичку при первом входе (пусто - выключить)");
            tabX = cfg.getInt("tab_x", CAT_GENERAL, tabX, 0, 4000, "Позиция кнопки по X в режиме screen (можно двигать Shift+перетаскиванием)");
            tabY = cfg.getInt("tab_y", CAT_GENERAL, tabY, 0, 4000, "Позиция кнопки по Y в режиме screen");
            inventoryTabOffsetX = cfg.getInt("inventory_tab_offset_x", CAT_GENERAL, inventoryTabOffsetX, -200, 200, "Сдвиг кнопки по X в режиме inventory");
            inventoryTabOffsetY = cfg.getInt("inventory_tab_offset_y", CAT_GENERAL, inventoryTabOffsetY, -200, 200, "Сдвиг кнопки по Y в режиме inventory");

            // economy
            startingBalance = getLong("starting_balance", CAT_ECONOMY, startingBalance, "Стартовый баланс нового игрока");
            maxBalance = getLong("max_balance", CAT_ECONOMY, maxBalance, "Максимальный баланс");
            allowPayCommand = cfg.getBoolean("allow_pay", CAT_ECONOMY, allowPayCommand, "Разрешить /eco pay");
            payTaxPercent = cfg.get(CAT_ECONOMY, "pay_tax_percent", payTaxPercent, "Комиссия при переводе, %").getDouble();
            payMinAmount = getLong("pay_min_amount", CAT_ECONOMY, payMinAmount, "Минимальная сумма перевода");
            topListSize = cfg.getInt("top_list_size", CAT_ECONOMY, topListSize, 3, 50, "Размер /eco top");
            priceMultiplier = cfg.get(CAT_ECONOMY, "price_multiplier", priceMultiplier,
                    "Глобальный множитель цен скупщика: 0.5 - всё вдвое дешевле, 2.0 - вдвое дороже").getDouble();
            deathLossPercent = cfg.get(CAT_ECONOMY, "death_loss_percent", deathLossPercent,
                    "Сколько процентов денег игрок теряет при смерти (0 = ничего)").getDouble();
            deathLossMax = getLong("death_loss_max", CAT_ECONOMY, deathLossMax,
                    "Потолок потери при смерти (0 = без потолка)");

            // market
            marketSellIntervalTicks = cfg.getInt("sell_interval_ticks", CAT_MARKET, marketSellIntervalTicks, 5, 12000,
                    "Как часто блок-скупщик продаёт содержимое (20 тиков = 1 сек, минимум 5)");
            marketInstantSell = cfg.getBoolean("instant_sell", CAT_MARKET, marketInstantSell,
                    "Продавать сразу, как только предмет попал в терминал, не дожидаясь таймера");
            marketTaxPercent = cfg.get(CAT_MARKET, "tax_percent", marketTaxPercent, "Налог сервера с продажи, % (сток денег из экономики)").getDouble();
            marketRequireOwner = cfg.getBoolean("require_owner", CAT_MARKET, marketRequireOwner, "Без владельца блок не продаёт");
            marketOwnerOnlyAccess = cfg.getBoolean("owner_only_access", CAT_MARKET, marketOwnerOnlyAccess, "Открывать терминал может только владелец (false - любой игрок)");
            marketAllowHopperInput = cfg.getBoolean("allow_hopper_input", CAT_MARKET, marketAllowHopperInput, "Разрешить загрузку воронками/трубами");
            marketVoidUnsellable = cfg.getBoolean("void_unsellable", CAT_MARKET, marketVoidUnsellable, "Удалять предметы, которых нет в прайсе (иначе они просто лежат)");
            marketNotifyOwner = cfg.getBoolean("notify_owner", CAT_MARKET, marketNotifyOwner, "Писать владельцу в чат о продажах");
            dynamicPricingEnabled = cfg.getBoolean("dynamic_pricing", CAT_MARKET, dynamicPricingEnabled,
                    "Динамические цены: чем больше продано, тем дешевле принимают");
            dynamicPriceMinFactor = cfg.get(CAT_MARKET, "dynamic_min_factor", dynamicPriceMinFactor, "Нижняя граница цены (доля от базовой)").getDouble();
            dynamicPriceDecayPerItem = cfg.get(CAT_MARKET, "dynamic_decay_per_item", dynamicPriceDecayPerItem, "Насколько падает цена за каждый проданный предмет").getDouble();
            dynamicPriceRecoveryPerHour = cfg.get(CAT_MARKET, "dynamic_recovery_per_hour", dynamicPriceRecoveryPerHour, "Восстановление цены в час (доля от базовой)").getDouble();
            allowBuyFromGui = cfg.getBoolean("allow_buy_from_gui", CAT_MARKET, allowBuyFromGui, "Разрешить покупку предметов из GUI магазина");

            // vending
            vendingEnabled = cfg.getBoolean("enabled", CAT_VENDING, vendingEnabled, "Блок продажи (торговый автомат)");
            vendingTaxPercent = cfg.get(CAT_VENDING, "tax_percent", vendingTaxPercent,
                    "Комиссия сервера с каждой покупки в блоке продажи, %").getDouble();
            vendingProtectFromOthers = cfg.getBoolean("protect_from_others", CAT_VENDING, vendingProtectFromOthers,
                    "Чужой игрок не может сломать блок продажи (иначе унесёт его вместе с товаром и деньгами)");
            vendingAllowAdminShops = cfg.getBoolean("allow_admin_shops", CAT_VENDING, vendingAllowAdminShops,
                    "Разрешить админские блоки с бесконечным товаром (/ecoadmin adminshop)");
            vendingLogSales = cfg.getBoolean("log_sales", CAT_VENDING, vendingLogSales,
                    "Писать каждую покупку в world/ecoserv/sales.log");
            vendingDefaultPrice = getLong("default_price", CAT_VENDING, vendingDefaultPrice,
                    "Цена по умолчанию у нового блока");
            vendingDefaultAmount = cfg.getInt("default_amount", CAT_VENDING, vendingDefaultAmount, 1, 512,
                    "Сколько штук выдаётся за одну покупку по умолчанию");
            vendingMaxPrice = getLong("max_price", CAT_VENDING, vendingMaxPrice, "Максимальная цена покупки");
            vendingAnnounceBig = cfg.getBoolean("announce_big", CAT_VENDING, vendingAnnounceBig,
                    "Объявлять в чат крупные покупки");
            vendingAnnounceThreshold = getLong("announce_threshold", CAT_VENDING, vendingAnnounceThreshold,
                    "С какой суммы покупка считается крупной");

            // auction
            auctionEnabled = cfg.getBoolean("enabled", CAT_AUCTION, auctionEnabled, "Аукцион игроков");
            auctionTaxPercent = cfg.get(CAT_AUCTION, "tax_percent", auctionTaxPercent,
                    "Комиссия сервера с каждой продажи на аукционе, %").getDouble();
            auctionMaxListingsPerPlayer = cfg.getInt("max_listings_per_player", CAT_AUCTION,
                    auctionMaxListingsPerPlayer, 1, 1000, "Сколько лотов может держать один игрок");
            auctionMinPrice = getLong("min_price", CAT_AUCTION, auctionMinPrice, "Минимальная цена за штуку");
            auctionMaxPrice = getLong("max_price", CAT_AUCTION, auctionMaxPrice, "Максимальная цена за штуку");
            auctionListingDays = cfg.getInt("listing_days", CAT_AUCTION, auctionListingDays, 0, 365,
                    "Через сколько дней лот снимается автоматически (0 = никогда)");
            auctionAnnounceNew = cfg.getBoolean("announce_new", CAT_AUCTION, auctionAnnounceNew,
                    "Писать в чат о новых лотах");

            // backup
            backupEnabled = cfg.getBoolean("enabled", CAT_BACKUP, backupEnabled, "Автоматические бэкапы");
            backupIntervalMinutes = cfg.getInt("interval_minutes", CAT_BACKUP, backupIntervalMinutes, 5, 10080, "Интервал между бэкапами, минут");
            backupKeepCount = cfg.getInt("keep_count", CAT_BACKUP, backupKeepCount, 1, 10000, "Сколько последних бэкапов хранить (0 = без лимита по количеству)");
            backupMaxAgeDays = cfg.getInt("max_age_days", CAT_BACKUP, backupMaxAgeDays, 0, 3650, "Удалять бэкапы старше N дней (0 = не удалять по возрасту)");
            backupMaxTotalSizeMb = cfg.getInt("max_total_size_mb", CAT_BACKUP, backupMaxTotalSizeMb, 0, 10_000_000, "Лимит общего размера папки бэкапов, МБ (0 = без лимита)");
            backupDir = cfg.getString("directory", CAT_BACKUP, backupDir, "Папка для бэкапов (относительно папки сервера или абсолютный путь)");
            backupSkipIfNoPlayers = cfg.getBoolean("skip_if_no_players", CAT_BACKUP, backupSkipIfNoPlayers, "Пропускать бэкап, если с прошлого раза никто не заходил");
            backupAnnounce = cfg.getBoolean("announce", CAT_BACKUP, backupAnnounce, "Сообщать в чат о начале/конце бэкапа");
            backupAnnounceOnlyAdmins = cfg.getBoolean("announce_only_admins", CAT_BACKUP, backupAnnounceOnlyAdmins, "Сообщать только админам");
            backupCompressionLevel = cfg.getInt("compression_level", CAT_BACKUP, backupCompressionLevel, 0, 9, "Уровень сжатия zip (0 - без сжатия и быстро, 9 - максимум)");
            backupIncludeConfig = cfg.getBoolean("include_config", CAT_BACKUP, backupIncludeConfig, "Класть в архив папку config");
            backupIncludeModList = cfg.getBoolean("include_mod_list", CAT_BACKUP, backupIncludeModList, "Класть в архив список модов (modlist.txt)");
            backupOnServerStop = cfg.getBoolean("backup_on_stop", CAT_BACKUP, backupOnServerStop, "Делать бэкап при остановке сервера");
            backupWarnSeconds = cfg.getInt("warn_seconds", CAT_BACKUP, backupWarnSeconds, 0, 300, "За сколько секунд предупреждать игроков");
            backupExclude = cfg.getStringList("exclude", CAT_BACKUP, backupExclude, "Пути внутри мира, которые не попадут в архив");

            // trade
            tradeEnabled = cfg.getBoolean("enabled", CAT_TRADE, tradeEnabled, "Торговля между игроками");
            tradeRequestTimeoutSeconds = cfg.getInt("request_timeout_seconds", CAT_TRADE, tradeRequestTimeoutSeconds, 5, 600, "Время жизни запроса на обмен");
            tradeMaxDistance = cfg.get(CAT_TRADE, "max_distance", tradeMaxDistance, "Максимальное расстояние между игроками (0 = без ограничения)").getDouble();
            tradeSameDimension = cfg.getBoolean("same_dimension", CAT_TRADE, tradeSameDimension, "Требовать одно измерение");
            tradeAllowMoney = cfg.getBoolean("allow_money", CAT_TRADE, tradeAllowMoney, "Разрешить добавлять деньги в сделку");
            tradeLog = cfg.getBoolean("log", CAT_TRADE, tradeLog, "Писать все сделки в world/ecoserv/trades.log");
            tradeConfirmDelayMillis = cfg.getInt("confirm_delay_ms", CAT_TRADE, tradeConfirmDelayMillis, 0, 10000,
                    "Задержка перед возможностью подтвердить после изменения предложения (антискам)");

            // restart
            restartEnabled = cfg.getBoolean("enabled", CAT_RESTART, restartEnabled, "Автоперезапуск по расписанию (нужен внешний скрипт-обёртка!)");
            restartTimes = cfg.getStringList("times", CAT_RESTART, restartTimes, "Время перезапуска по локальному времени сервера, формат HH:mm");
            restartWarnMinutes = cfg.get(CAT_RESTART, "warn_minutes", restartWarnMinutes, "За сколько минут предупреждать").getIntList();
            restartBackupBefore = cfg.getBoolean("backup_before", CAT_RESTART, restartBackupBefore, "Делать бэкап перед перезапуском");
            restartMessage = cfg.getString("message", CAT_RESTART, restartMessage, "Сообщение предупреждения, %s = оставшееся время");

            // announcer
            announcerEnabled = cfg.getBoolean("enabled", CAT_ANNOUNCER, announcerEnabled, "Периодические объявления в чат");
            announcerIntervalMinutes = cfg.getInt("interval_minutes", CAT_ANNOUNCER, announcerIntervalMinutes, 1, 1440, "Интервал объявлений");
            announcerRandom = cfg.getBoolean("random", CAT_ANNOUNCER, announcerRandom, "Случайный порядок вместо по кругу");
            announcerMessages = cfg.getStringList("messages", CAT_ANNOUNCER, announcerMessages, "Сообщения, & = цветовые коды");

            // rewards
            playtimeRewardEnabled = cfg.getBoolean("playtime_enabled", CAT_REWARDS, playtimeRewardEnabled, "Деньги за время на сервере");
            playtimeRewardMinutes = cfg.getInt("playtime_minutes", CAT_REWARDS, playtimeRewardMinutes, 1, 1440, "Каждые N минут игры");
            playtimeRewardAmount = getLong("playtime_amount", CAT_REWARDS, playtimeRewardAmount, "Сколько выдавать");
            afkMinutes = cfg.getInt("afk_minutes", CAT_REWARDS, afkMinutes, 0, 120, "Через сколько минут без действий игрок считается АФК (0 = не проверять)");
            dailyEnabled = cfg.getBoolean("daily_enabled", CAT_REWARDS, dailyEnabled, "Ежедневная награда /eco daily");
            dailyAmount = getLong("daily_amount", CAT_REWARDS, dailyAmount, "Базовая ежедневная награда");
            dailyStreakBonus = getLong("daily_streak_bonus", CAT_REWARDS, dailyStreakBonus, "Бонус за каждый день серии");
            dailyStreakMax = cfg.getInt("daily_streak_max", CAT_REWARDS, dailyStreakMax, 1, 365, "Максимальная длина серии");

            // blacklist
            blacklistEnabled = cfg.getBoolean("enabled", CAT_BLACKLIST, blacklistEnabled, "Чёрный список предметов (список редактируется в GUI или /ecoadmin blacklist)");
            blacklistBlockPlace = cfg.getBoolean("block_place", CAT_BLACKLIST, blacklistBlockPlace, "Запрещать ставить блоки");
            blacklistBlockUse = cfg.getBoolean("block_use", CAT_BLACKLIST, blacklistBlockUse, "Запрещать использовать предмет");
            blacklistBlockCraft = cfg.getBoolean("block_craft", CAT_BLACKLIST, blacklistBlockCraft, "Запрещать крафт");
            blacklistAdminsBypass = cfg.getBoolean("admins_bypass", CAT_BLACKLIST, blacklistAdminsBypass, "Админы игнорируют чёрный список");
            blacklistMessage = cfg.getString("message", CAT_BLACKLIST, blacklistMessage, "Сообщение при блокировке");

            // worlds
            worldsEnabled = cfg.getBoolean("enabled", CAT_WORLDS, worldsEnabled, "Команда /world <мир> — телепорт между мирами");
            worldAliases = cfg.getStringList("aliases", CAT_WORLDS, worldAliases,
                    "Имена миров для /world в виде имя=номер_измерения. Узнать номера: /world list (админ видит все)");
            worldPlayerWorlds = cfg.getStringList("player_worlds", CAT_WORLDS, worldPlayerWorlds,
                    "Куда могут телепортироваться обычные игроки (имена из aliases или названия измерений, * = все). "
                            + "Админы могут в любой мир");
            worldCooldownSeconds = cfg.getInt("cooldown_seconds", CAT_WORLDS, worldCooldownSeconds, 0, 3600,
                    "Пауза между телепортами для игрока (админам не действует)");
            worldReturnToLast = cfg.getBoolean("return_to_last_position", CAT_WORLDS, worldReturnToLast,
                    "Возвращать туда, где игрок был в этом мире в прошлый раз (иначе — на спавн мира)");
            worldSearchRadius = cfg.getInt("safe_search_radius", CAT_WORLDS, worldSearchRadius, 0, 64,
                    "Радиус поиска безопасного места вокруг точки прибытия");
            worldsSeparateBorders = cfg.getBoolean("separate_borders", CAT_WORLDS, worldsSeparateBorders,
                    "У каждого мира свой барьер: /worldborder меняет барьер того мира, где стоит игрок "
                            + "(«/worldborder in <мир> ...» — другого). Выключено — как в ванили, один барьер на всех");

            // restrictions
            restrictionsEnabled = cfg.getBoolean("enabled", CAT_RESTRICT, restrictionsEnabled,
                    "Частичные запреты предметов по мирам (config/ecoserv/restrictions.json, редактор: /ecoadmin restrict)");
            restrictionsAdminsBypass = cfg.getBoolean("admins_bypass", CAT_RESTRICT, restrictionsAdminsBypass,
                    "Админы не подчиняются запретам");
            restrictionsCreativeBypass = cfg.getBoolean("creative_bypass", CAT_RESTRICT, restrictionsCreativeBypass,
                    "Игроки в креативе не подчиняются запретам");
            restrictMessageCraft = cfg.getString("message_craft", CAT_RESTRICT, restrictMessageCraft, "Сообщение: крафт запрещён");
            restrictMessageUse = cfg.getString("message_use", CAT_RESTRICT, restrictMessageUse, "Сообщение: использование запрещено");
            restrictMessagePlace = cfg.getString("message_place", CAT_RESTRICT, restrictMessagePlace, "Сообщение: установка запрещена");
            restrictMessageCarry = cfg.getString("message_carry", CAT_RESTRICT, restrictMessageCarry,
                    "Сообщение: предмет нельзя носить (при попытке подобрать)");

            // zones
            zonesEnabled = cfg.getBoolean("enabled", CAT_ZONES, zonesEnabled,
                    "Зоны палочек BLOCK DESTROY: ломать и ставить можно только оператору в креативе");
            zonesProtectExplosions = cfg.getBoolean("protect_from_explosions", CAT_ZONES, zonesProtectExplosions,
                    "Взрывы не разрушают блоки внутри зон");
            zonesParticles = cfg.getBoolean("show_particles", CAT_ZONES, zonesParticles,
                    "Показывать границы зон частицами админу с палочкой в руке");
            zonesMargin = cfg.getInt("margin", CAT_ZONES, zonesMargin, 0, 32,
                    "Запас вокруг зоны (в блоках), где нельзя лить лаву/воду, поджигать и ставить поршни — "
                            + "иначе они затекут или въедут в зону (0 — только внутри)");
            zonesDangerMessage = cfg.getString("danger_message", CAT_ZONES, zonesDangerMessage,
                    "Что пишется игроку в зоне BLOCK DESTROY DANGER (зона BLOCK DESTROY молчит)");

            // activators
            activatorsEnabled = cfg.getBoolean("enabled", CAT_ACTIVATORS, activatorsEnabled,
                    "Кнопки и рычаги с названиями (палка активации) для условия «Активация» в FTB Quests");
            activatorsNotify = cfg.getBoolean("notify", CAT_ACTIVATORS, activatorsNotify,
                    "Писать игроку над хотбаром, что он активировал кнопку");
            activatorsMessage = cfg.getString("message", CAT_ACTIVATORS, activatorsMessage,
                    "Текст уведомления, %name% — название кнопки");

            // roles
            rolesChatPrefix = cfg.getBoolean("chat_prefix", CAT_ROLES, rolesChatPrefix,
                    "Показывать тег роли перед сообщениями в чате (роли: config/ecoserv/roles.json, команда /role)");
            rolesXaeroCooldownSeconds = cfg.getInt("xaero_cooldown_seconds", CAT_ROLES, rolesXaeroCooldownSeconds, 0, 3600,
                    "Пауза между телепортами по карте Xaero для игроков с ролью (админам не действует)");

            // rcon
            wrapCommands = cfg.getBoolean("wrap_commands", CAT_RCON, wrapCommands,
                    "Обёртка всех команд сервера: доступ к командам по ролям и выполнение команд RCON в главном потоке. "
                            + "Выключать только при конфликте с другим модом (нужен перезапуск)");
            rconMainThread = cfg.getBoolean("main_thread", CAT_RCON, rconMainThread,
                    "Команды из RCON выполнять в главном потоке сервера (в ванильном 1.12 они идут в чужом потоке "
                            + "и могут ломать моды). Ответ команды всё равно возвращается в RCON");
            rconTimeoutSeconds = cfg.getInt("timeout_seconds", CAT_RCON, rconTimeoutSeconds, 1, 600,
                    "Сколько ждать выполнения команды RCON в главном потоке");

            // inventories
            invEnabled = cfg.getBoolean("enabled", CAT_INVENTORIES, invEnabled,
                    "Раздельные инвентари: у каждой группы миров свой инвентарь (и по настройкам ниже — опыт, эндер-сундук...). "
                            + "Миры, не вошедшие ни в одну группу, делят общий инвентарь группы default");
            invGroups = cfg.getStringList("groups", CAT_INVENTORIES, invGroups,
                    "Группы: одна строка — «имя: мир1, мир2». Миры — псевдонимы из worlds.aliases, номера измерений "
                            + "или имена подмиров. Пример: «creative: creative_world» или «sky: skyblock, sky2»");
            invSeparateXp = cfg.getBoolean("separate_xp", CAT_INVENTORIES, invSeparateXp, "Свой опыт у каждой группы");
            invSeparateEnderChest = cfg.getBoolean("separate_ender_chest", CAT_INVENTORIES, invSeparateEnderChest,
                    "Свой эндер-сундук у каждой группы");
            invSeparateHealth = cfg.getBoolean("separate_health", CAT_INVENTORIES, invSeparateHealth,
                    "Своё здоровье и голод у каждой группы");
            invSeparateEffects = cfg.getBoolean("separate_effects", CAT_INVENTORIES, invSeparateEffects,
                    "Свои эффекты зелий у каждой группы");
            invGamemodes = cfg.getStringList("gamemodes", CAT_INVENTORIES, invGamemodes,
                    "Режим игры при входе в группу: строки «группа=creative» (survival, creative, adventure, spectator). "
                            + "Без строки — возвращается режим, в котором игрок был в этой группе");
            invAdminsKeepGamemode = cfg.getBoolean("admins_keep_gamemode", CAT_INVENTORIES, invAdminsKeepGamemode,
                    "Админам режим игры при смене группы не менять");
            invNotify = cfg.getBoolean("notify", CAT_INVENTORIES, invNotify,
                    "Писать игроку, что загружен инвентарь другой группы");

            // dates
            datesEnabled = cfg.getBoolean("enabled", CAT_DATES, datesEnabled,
                    "Значимые даты: в этот день каждый игрок один раз за год получает награду. "
                            + "Даты настраиваются в меню /ecoadmin dates (файл config/ecoserv/dates.json)");
            birthdaysEnabled = cfg.getBoolean("birthdays_enabled", CAT_DATES, birthdaysEnabled,
                    "Включить ввод дня рождения (/birthday дд.мм.гггг): награда в день рождения и проверка возраста "
                            + "(условие доступа к подмирам и условие «Возраст» в квестах)");
            birthdayMoney = getLong("birthday_money", CAT_DATES, birthdayMoney, "Монет в подарок на день рождения");
            birthdayBroadcast = cfg.getBoolean("birthday_broadcast", CAT_DATES, birthdayBroadcast,
                    "Объявлять в чат «сегодня день рождения у …» (возраст не показывается)");
            birthdayMessage = cfg.getString("birthday_message", CAT_DATES, birthdayMessage,
                    "Поздравление игроку. %player% — ник, %reward% — что подарено");
            birthdayPlayersCanChange = cfg.getBoolean("players_can_change", CAT_DATES, birthdayPlayersCanChange,
                    "Может ли игрок сам изменить уже введённый день рождения (иначе — только админ)");
            datesTimezone = cfg.getString("timezone", CAT_DATES, datesTimezone,
                    "Часовой пояс для дат, например Europe/Moscow (пусто — пояс сервера)");

            // tools
            fillMaxVolume = cfg.getInt("fill_max_volume", CAT_TOOLS, fillMaxVolume, 1, 1000000,
                    "Палочка заполнения: самая большая область (в блоках) за один раз");
            fillBlocksPerTick = cfg.getInt("fill_blocks_per_tick", CAT_TOOLS, fillBlocksPerTick, 1, 4096,
                    "Палочка заполнения: сколько блоков ставить за тик (меньше — меньше нагрузка)");

            // subworlds
            subworldsEnabled = cfg.getBoolean("enabled", CAT_SUBWORLDS, subworldsEnabled,
                    "Подмиры: отдельные миры со своими настройками, локальные миры команд, порталы (меню — кнопка с планетой в инвентаре)");
            subworldsDimStart = cfg.getInt("dim_id_start", CAT_SUBWORLDS, subworldsDimStart, 2, 1000000,
                    "С какого номера выдавать измерения подмирам (не должен пересекаться с измерениями других модов)");
            subworldsDimTypeId = cfg.getInt("dimension_type_id", CAT_SUBWORLDS, subworldsDimTypeId, 3, 30000,
                    "Служебный номер типа измерения подмиров (менять только при конфликте с другим модом)");
            subworldsMaxInstances = cfg.getInt("max_instances", CAT_SUBWORLDS, subworldsMaxInstances, 1, 100000,
                    "Сколько всего локальных миров может быть на сервере");
            subworldsUploadsFolder = cfg.getString("uploads_folder", CAT_SUBWORLDS, subworldsUploadsFolder,
                    "Папка (рядом с папкой мира сервера), куда кладутся свои миры для типа «загрузить свою»: "
                            + "папка мира целиком или .zip");
            subworldsMaxUploadMb = cfg.getInt("max_upload_mb", CAT_SUBWORLDS, subworldsMaxUploadMb, 1, 8192,
                    "Самый большой загружаемый мир, МБ");
            subworldsClientUpload = cfg.getBoolean("client_upload", CAT_SUBWORLDS, subworldsClientUpload,
                    "Разрешить админу загружать мир прямо из своей игры (из одиночных сохранений)");
            subworldsWelcomeTitle = cfg.getBoolean("welcome_title", CAT_SUBWORLDS, subworldsWelcomeTitle,
                    "Показывать название подмира на экране при входе");
            subworldsPublishSyncMb = cfg.getInt("publish_sync_mb", CAT_SUBWORLDS, subworldsPublishSyncMb, 1, 100000,
                    "Публикация шаблона: до скольких МБ изменений копировать сразу; больше — только когда шаблон "
                            + "выгружен (в нём никого нет), копирование идёт в фоне");
            subworldsUnloadEmptySeconds = cfg.getInt("unload_empty_seconds", CAT_SUBWORLDS, subworldsUnloadEmptySeconds,
                    10, 86400, "Через сколько секунд выгружать пустые миры подмиров (экономит память)");

            // integration
            economyProvider = cfg.getString("economy_provider", CAT_INTEGRATION, economyProvider,
                    "auto | internal | ftbmoney | custom — откуда брать баланс игрока");
            ftbMoneyBridge = cfg.getBoolean("ftb_money", CAT_INTEGRATION, ftbMoneyBridge, "Пробовать подключиться к FTB Money");
            ftbQuestsBridge = cfg.getBoolean("ftb_quests", CAT_INTEGRATION, ftbQuestsBridge, "Пробовать подключиться к FTB Quests (проверка выполненных квестов для товаров)");
            questCheckFailOpen = cfg.getBoolean("quest_check_fail_open", CAT_INTEGRATION, questCheckFailOpen,
                    "Если проверку квеста выполнить не удалось: true - пропускать игрока, false - блокировать");
            customEconomyClass = cfg.getString("custom_class", CAT_INTEGRATION, customEconomyClass,
                    "Полное имя класса чужой экономики для моста через рефлексию (economy_provider=custom)");
            customGetMethod = cfg.getString("custom_get_method", CAT_INTEGRATION, customGetMethod, "Статический метод long get(EntityPlayer)");
            customAddMethod = cfg.getString("custom_add_method", CAT_INTEGRATION, customAddMethod, "Статический метод void add(EntityPlayer, long)");
            customSetMethod = cfg.getString("custom_set_method", CAT_INTEGRATION, customSetMethod, "Статический метод void set(EntityPlayer, long)");

            if (restartWarnMinutes == null || restartWarnMinutes.length == 0) {
                restartWarnMinutes = new int[]{5, 1};
            }
        } catch (Exception e) {
            EcoServ.LOG.error("Ошибка чтения конфига", e);
        } finally {
            if (cfg != null && cfg.hasChanged()) {
                cfg.save();
            }
        }
    }

    private static long getLong(String key, String category, long def, String comment) {
        // Forge Configuration не умеет long напрямую — храним строкой.
        String raw = cfg.getString(key, category, Long.toString(def), comment + " (целое число)");
        try {
            return Long.parseLong(raw.trim().replace("_", ""));
        } catch (NumberFormatException e) {
            EcoServ.LOG.warn("Некорректное число в конфиге {}.{} = {}, использую {}", category, key, raw, def);
            return def;
        }
    }

    /**
     * Меняет значение в конфиге, сохраняя тип уже существующего параметра.
     * Используется кнопками админ-панели (налог, интервал продажи и т.п.).
     */
    public static boolean set(String category, String key, String value) {
        if (cfg == null) {
            return false;
        }
        ConfigCategory cat = cfg.hasCategory(category) ? cfg.getCategory(category) : null;
        Property property = cat == null ? null : cat.get(key);
        if (property == null) {
            cfg.get(category, key, value).set(value);
            cfg.save();
            reload();
            return true;
        }
        return setChecked(category, key, value) == null;
    }

    // --- редактор конфига в игре (/ecoadmin config) ---------------------------

    /** Служебные ключи и координаты кнопок (их двигают мышкой) — в редакторе не показываем. */
    private static final java.util.Set<String> HIDDEN_KEYS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "general.config_version", "general.tab_x", "general.tab_y", "general.trade_tab_x", "general.trade_tab_y",
            "general.inventory_tab_offset_x", "general.inventory_tab_offset_y", "general.worlds_tab_x",
            "general.worlds_tab_y"));

    /** Настройки внешнего вида, которые у каждого игрока в его собственном конфиге. */
    private static final java.util.Set<String> CLIENT_KEYS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "general.show_inventory_tab", "general.tab_mode", "general.show_trade_button", "general.price_tooltip",
            "general.show_worlds_button"));

    public static boolean isHidden(String category, String key) {
        return HIDDEN_KEYS.contains(category + "." + key);
    }

    public static boolean isClientOnly(String category, String key) {
        return CLIENT_KEYS.contains(category + "." + key);
    }

    @javax.annotation.Nullable
    public static Property property(String category, String key) {
        if (cfg == null || category == null || key == null || !cfg.hasCategory(category)) {
            return null;
        }
        return cfg.getCategory(category).get(key);
    }

    /** Число, записанное строкой (деньги: long не помещается в int). */
    public static boolean isLongString(Property p) {
        return p != null && !p.isList() && p.getType() == Property.Type.STRING
                && p.getDefault() != null && p.getDefault().trim().matches("-?\\d+");
    }

    @javax.annotation.Nullable
    public static Boolean parseBool(String text) {
        String s = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.equals("true") || s.equals("on") || s.equals("yes") || s.equals("1") || s.equals("да")
                || s.equals("вкл") || s.equals("+")) {
            return Boolean.TRUE;
        }
        if (s.equals("false") || s.equals("off") || s.equals("no") || s.equals("0") || s.equals("нет")
                || s.equals("выкл") || s.equals("-")) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * Изменение с проверкой типа, границ и допустимых значений.
     * Возвращает null, если записано, иначе причину отказа (для игрока).
     */
    @javax.annotation.Nullable
    public static String setChecked(String category, String key, String value) {
        Property p = property(category, key);
        if (p == null) {
            return "нет параметра " + category + "." + key;
        }
        String v = value == null ? "" : value.trim();
        if (p.isList()) {
            List<String> items = new ArrayList<String>();
            for (String part : v.split(";")) {
                if (!part.trim().isEmpty()) {
                    items.add(part.trim());
                }
            }
            return setList(category, key, items);
        }
        switch (p.getType()) {
            case INTEGER: {
                int n;
                try {
                    n = Integer.parseInt(v.replace("_", "").replace(" ", ""));
                } catch (NumberFormatException e) {
                    return "нужно целое число";
                }
                String bad = outOfRange(p, n);
                if (bad != null) {
                    return bad;
                }
                p.set(n);
                break;
            }
            case DOUBLE: {
                double d;
                try {
                    d = Double.parseDouble(v.replace(',', '.').replace(" ", ""));
                } catch (NumberFormatException e) {
                    return "нужно число (например 2.5)";
                }
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    return "нужно число";
                }
                String bad = outOfRange(p, d);
                if (bad != null) {
                    return bad;
                }
                p.set(d);
                break;
            }
            case BOOLEAN: {
                Boolean b = parseBool(v);
                if (b == null) {
                    return "нужно да/нет (true/false)";
                }
                p.set(b.booleanValue());
                break;
            }
            default: {
                String[] valid = p.getValidValues();
                if (valid != null && valid.length > 0) {
                    boolean ok = false;
                    for (String option : valid) {
                        if (option.equalsIgnoreCase(v)) {
                            v = option;
                            ok = true;
                        }
                    }
                    if (!ok) {
                        return "допустимо: " + String.join(", ", valid);
                    }
                }
                if (isLongString(p)) {
                    try {
                        Long.parseLong(v.replace("_", "").replace(" ", ""));
                        v = v.replace("_", "").replace(" ", "");
                    } catch (NumberFormatException e) {
                        return "нужно целое число";
                    }
                }
                p.set(v);
                break;
            }
        }
        cfg.save();
        reload();
        return null;
    }

    @javax.annotation.Nullable
    private static String outOfRange(Property p, double n) {
        try {
            String min = p.getMinValue();
            if (min != null && !min.isEmpty() && n < Double.parseDouble(min)) {
                return "не меньше " + min;
            }
        } catch (NumberFormatException ignored) {
            // границы не заданы
        }
        try {
            String max = p.getMaxValue();
            if (max != null && !max.isEmpty() && n > Double.parseDouble(max)) {
                return "не больше " + max;
            }
        } catch (NumberFormatException ignored) {
            // границы не заданы
        }
        return null;
    }

    @javax.annotation.Nullable
    public static String setList(String category, String key, List<String> values) {
        Property p = property(category, key);
        if (p == null || !p.isList()) {
            return "это не список";
        }
        p.set(values.toArray(new String[0]));
        cfg.save();
        reload();
        return null;
    }

    public static boolean reset(String category, String key) {
        Property p = property(category, key);
        if (p == null) {
            return false;
        }
        p.setToDefault();
        cfg.save();
        reload();
        return true;
    }

    /** Текущее значение для показа человеку. */
    public static String display(Property p) {
        if (p == null) {
            return "";
        }
        if (p.isList()) {
            return String.join("; ", p.getStringList());
        }
        if (p.getType() == Property.Type.BOOLEAN) {
            return p.getBoolean() ? "да" : "нет";
        }
        return p.getString();
    }

    public static String displayDefault(Property p) {
        if (p == null) {
            return "";
        }
        if (p.isList()) {
            return String.join("; ", p.getDefaults());
        }
        if (p.getType() == Property.Type.BOOLEAN) {
            return Boolean.parseBoolean(p.getDefault()) ? "да" : "нет";
        }
        return p.getDefault();
    }

    public static void setBoolean(String category, String key, boolean value) {
        if (cfg == null) {
            return;
        }
        cfg.get(category, key, value).set(value);
        cfg.save();
        reload();
    }

    /** Сохранение позиции кнопки-планеты. */
    public static void saveWorldsTabPosition(int x, int y) {
        worldsTabX = x;
        worldsTabY = y;
        if (cfg == null) {
            return;
        }
        cfg.get(CAT_GENERAL, "worlds_tab_x", x).set(x);
        cfg.get(CAT_GENERAL, "worlds_tab_y", y).set(y);
        cfg.save();
    }

    /** Сохранение позиции кнопки обмена. */
    public static void saveTradeTabPosition(int x, int y) {
        tradeTabX = x;
        tradeTabY = y;
        if (cfg == null) {
            return;
        }
        cfg.get(CAT_GENERAL, "trade_tab_x", x).set(x);
        cfg.get(CAT_GENERAL, "trade_tab_y", y).set(y);
        cfg.save();
    }

    /** Сохранение позиции кнопки после перетаскивания (без полной перезагрузки конфига). */
    public static void saveTabPosition(int x, int y) {
        tabX = x;
        tabY = y;
        if (cfg == null) {
            return;
        }
        cfg.get(CAT_GENERAL, "tab_x", x).set(x);
        cfg.get(CAT_GENERAL, "tab_y", y).set(y);
        cfg.save();
    }
}
