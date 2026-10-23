package com.ecoserv.integration;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.IEconomy;
import net.minecraftforge.fml.common.Loader;

/** Определение и подключение сторонних модов. */
public final class Integrations {

    /**
     * Известные варианты API FTB Money: класс, геттер, прибавление, установка.
     * Первый — настоящий класс FTB Money для 1.12.2 (ветка 1.12 репозитория FTBTeam/FTB-Money):
     * com.feed_the_beast.mods.money.FTBMoney#getMoney(EntityPlayer) / #setMoney(EntityPlayer, long).
     * Метода addMoney там нет, поэтому мост умеет работать через пару get+set.
     */
    private static final String[][] FTB_MONEY_CANDIDATES = {
            {"com.feed_the_beast.mods.money.FTBMoney", "getMoney", "addMoney", "setMoney"},
            {"com.feed_the_beast.mods.ftbmoney.FTBMoney", "getMoney", "addMoney", "setMoney"},
            {"com.feed_the_beast.ftbmoney.FTBMoney", "getMoney", "addMoney", "setMoney"},
            {"com.feed_the_beast.mods.money.MoneyUtils", "getMoney", "addMoney", "setMoney"},
            {"com.feed_the_beast.mods.ftbmoney.MoneyUtils", "getMoney", "addMoney", "setMoney"},
            {"com.feed_the_beast.ftbquests.util.MoneyUtils", "getMoney", "addMoney", "setMoney"},
    };

    public static boolean ftbLib;
    public static boolean ftbMoney;
    public static boolean ftbQuests;
    public static boolean ftbUtilities;

    private Integrations() {
    }

    public static void detect() {
        ftbLib = Loader.isModLoaded("ftblib");
        ftbMoney = Loader.isModLoaded("ftbmoney");
        ftbQuests = Loader.isModLoaded("ftbquests");
        ftbUtilities = Loader.isModLoaded("ftbutilities");

        EcoServ.LOG.info("Интеграции: ftblib={} ftbmoney={} ftbquests={} ftbutilities={}",
                ftbLib, ftbMoney, ftbQuests, ftbUtilities);

        selectEconomy();
        FTBQuestsBridge.detect();
    }

    public static void selectEconomy() {
        String mode = EcoConfig.economyProvider == null ? "auto" : EcoConfig.economyProvider.trim().toLowerCase();

        if ("internal".equals(mode)) {
            EconomyManager.setProvider(EconomyManager.internal());
            return;
        }

        if ("custom".equals(mode)) {
            IEconomy custom = ReflectiveEconomy.tryCreate(EcoConfig.customEconomyClass,
                    EcoConfig.customGetMethod, EcoConfig.customAddMethod, EcoConfig.customSetMethod);
            if (custom != null) {
                EconomyManager.setProvider(custom);
                return;
            }
            EcoServ.LOG.warn("economy_provider=custom, но мост не создан. Использую внутреннюю экономику.");
            EconomyManager.setProvider(EconomyManager.internal());
            return;
        }

        boolean wantFtb = "ftbmoney".equals(mode) || ("auto".equals(mode) && EcoConfig.ftbMoneyBridge);
        if (wantFtb) {
            // 1) известные варианты API
            for (int i = 0; i < FTB_MONEY_CANDIDATES.length; i++) {
                String[] c = FTB_MONEY_CANDIDATES[i];
                IEconomy bridge = ReflectiveEconomy.tryCreate(c[0], c[1], c[2], c[3]);
                if (bridge != null) {
                    EconomyManager.setProvider(bridge);
                    return;
                }
            }
            // 2) поиск по классам мода (API у FTB Money меняется между сборками)
            FTBPlayers.reset();
            IEconomy scanned = ApiScanner.autoBridge();
            if (scanned != null) {
                EconomyManager.setProvider(scanned);
                return;
            }
            if (ftbMoney) {
                EcoServ.LOG.warn("FTB Money найден, но его API не распознано даже сканером. "
                        + "Выполни на сервере /ecoadmin ftbscan и пропиши integration.custom_* вручную. "
                        + "Пока работает внутренняя экономика.");
            }
        }
        EconomyManager.setProvider(EconomyManager.internal());
    }
}
