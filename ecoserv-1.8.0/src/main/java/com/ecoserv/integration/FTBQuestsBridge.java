package com.ecoserv.integration;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Проверка выполненных квестов FTB Quests через рефлексию.
 * Используется товарами прайс-листа с полем requiredQuest:
 * пока квест не сдан, товар не продаётся/не покупается.
 * <p>
 * Обратная интеграция (квест выдаёт деньги) делается наградой типа "команда":
 * /eco give @p 500
 */
public final class FTBQuestsBridge {

    private static boolean available;
    private static Object questFile;
    private static Method getDataMethod;
    private static Method getObjectMethod;
    private static boolean warned;

    private FTBQuestsBridge() {
    }

    public static boolean isAvailable() {
        return available;
    }

    public static void detect() {
        available = false;
        questFile = null;
        if (!EcoConfig.ftbQuestsBridge || !Loader.isModLoaded("ftbquests")) {
            return;
        }
        try {
            Class<?> serverFile = Class.forName("com.feed_the_beast.ftbquests.quest.ServerQuestFile");
            Field instance = serverFile.getField("INSTANCE");
            questFile = instance.get(null);
            if (questFile == null) {
                return;
            }
            getDataMethod = findMethod(questFile.getClass(), "getData", 1);
            getObjectMethod = findMethod(questFile.getClass(), "get", 1);
            if (getObjectMethod == null) {
                getObjectMethod = findMethod(questFile.getClass(), "getObject", 1);
            }
            available = getDataMethod != null;
            EcoServ.LOG.info("FTB Quests: мост {}", available ? "подключён" : "не подключён (API не распознан)");
        } catch (Throwable t) {
            EcoServ.LOG.info("FTB Quests: мост не подключён ({})", t.toString());
        }
    }

    /** Файл квестов перезагружается вместе с миром — обновляем ссылку. */
    public static void refresh() {
        if (available) {
            detect();
        }
    }

    private static Method findMethod(Class<?> cls, String name, int args) {
        Method[] methods = cls.getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(name) && methods[i].getParameterTypes().length == args) {
                methods[i].setAccessible(true);
                return methods[i];
            }
        }
        return null;
    }

    /**
     * @param questId id квеста (число или строка, как в FTB Quests)
     * @return true, если квест сдан либо проверку выполнить нельзя и включён fail-open
     */
    public static boolean isCompleted(EntityPlayer player, String questId) {
        if (questId == null || questId.trim().isEmpty()) {
            return true;
        }
        if (!available || player == null) {
            return EcoConfig.questCheckFailOpen;
        }
        try {
            Object data = getDataMethod.invoke(questFile, player);
            if (data == null) {
                return EcoConfig.questCheckFailOpen;
            }
            Object quest = resolveQuest(questId.trim());
            if (quest == null) {
                return EcoConfig.questCheckFailOpen;
            }
            Method isComplete = null;
            Method[] methods = data.getClass().getMethods();
            for (int i = 0; i < methods.length; i++) {
                Method m = methods[i];
                if ((m.getName().equals("isComplete") || m.getName().equals("isCompleted"))
                        && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0].isInstance(quest)) {
                    isComplete = m;
                    break;
                }
            }
            if (isComplete == null) {
                return EcoConfig.questCheckFailOpen;
            }
            Object result = isComplete.invoke(data, quest);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                EcoServ.LOG.warn("FTB Quests: проверка квеста не удалась, дальше молча используется fail_open={} ({})",
                        EcoConfig.questCheckFailOpen, t.toString());
            }
            return EcoConfig.questCheckFailOpen;
        }
    }

    private static Object resolveQuest(String questId) throws Exception {
        if (getObjectMethod == null) {
            return null;
        }
        Class<?> param = getObjectMethod.getParameterTypes()[0];
        if (param == int.class || param == Integer.class) {
            int id;
            try {
                id = questId.startsWith("0x") ? Integer.parseInt(questId.substring(2), 16) : Integer.parseInt(questId);
            } catch (NumberFormatException e) {
                // FTB Quests 1.12 хранит короткие id в hex
                try {
                    id = Integer.parseInt(questId, 16);
                } catch (NumberFormatException e2) {
                    return null;
                }
            }
            return getObjectMethod.invoke(questFile, Integer.valueOf(id));
        }
        return getObjectMethod.invoke(questFile, questId);
    }
}
