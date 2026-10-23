package com.ecoserv.integration.quests;

import com.ecoserv.EcoServ;
import com.ecoserv.Ref;
import com.ecoserv.activator.ActivationListener;
import com.ecoserv.activator.ActivatorManager;
import com.ecoserv.dates.AgeFilter;
import com.ecoserv.dates.DatesManager;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import com.feed_the_beast.ftbquests.quest.reward.RewardType;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskData;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;

/**
 * Интеграция с FTB Quests: условия «Активация», «Скрытое достижение», «Возраст» и награда «Роль».
 * Этот пакет компилируется, только если в папке libs/ лежат jar-файлы FTB Quests и FTBLib,
 * а вызывается через рефлексию — без FTB Quests мод работает как обычно.
 */
public final class QuestActivation {

    public static TaskType TYPE;
    public static TaskType HIDDEN;
    public static TaskType AGE;
    public static RewardType ROLE_REWARD;
    public static RewardType SUBWORLD_ACCESS;
    public static RewardType SUBWORLD_EXPAND;
    // 1.8.0
    public static TaskType MONEY_TASK;
    public static TaskType ROLE_TASK;
    public static RewardType MONEY_REWARD;

    private static Method clientComplete;
    private static int tick;

    private QuestActivation() {
    }

    /** Вызывается из EcoServ.init (до postInit FTB Quests, где создаются реестры условий и наград). */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(QuestActivation.class);
        ActivatorManager.addListener(new ActivationListener() {
            @Override
            public void onActivated(EntityPlayerMP player, String name) {
                complete(player, name);
                completeHidden(player, "activation", name);
            }
        });
        // подмиры: список глав для «своих квестов»
        com.ecoserv.subworld.SubworldQuests.setBridge(() -> {
            java.util.List<String[]> out = new java.util.ArrayList<String[]>();
            ServerQuestFile file = ServerQuestFile.INSTANCE;
            if (file != null) {
                for (com.feed_the_beast.ftbquests.quest.Chapter ch : file.chapters) {
                    out.add(new String[]{ch.getCodeString(), net.minecraft.util.text.TextFormatting
                            .getTextWithoutFormattingCodes(ch.getTitle())});
                }
            }
            return out;
        });
        // «Квестовая гонка»: задания из квестов
        com.ecoserv.minigame.RaceTasks.setQuestSource(new RaceQuestSource());
        if (FMLCommonHandler.instance().getSide().isClient()) {
            try {
                MinecraftForge.EVENT_BUS.register(Class.forName("com.ecoserv.integration.quests.QuestChapterFilter")
                        .newInstance());
            } catch (Throwable t) {
                EcoServ.LOG.warn("Свои квесты подмиров: клиентский фильтр глав не подключён: {}", t.toString());
            }
            try {
                Class<?> c = Class.forName("com.ecoserv.integration.quests.HiddenClient");
                clientComplete = c.getMethod("complete", HiddenTask.class);
                MinecraftForge.EVENT_BUS.register(c.newInstance());
            } catch (Throwable t) {
                EcoServ.LOG.warn("Скрытые квесты: клиентская часть не подключена: {}", t.toString());
            }
        }
    }

    @SubscribeEvent
    public static void registerTasks(RegistryEvent.Register<TaskType> event) {
        TYPE = new TaskType(ActivationTask::new);
        TYPE.setRegistryName(new ResourceLocation(Ref.MODID, "activation"));
        TYPE.setIcon(Icon.getIcon(Ref.MODID + ":textures/items/activator_wand.png"));
        event.getRegistry().register(TYPE);

        HIDDEN = new TaskType(HiddenTask::new);
        HIDDEN.setRegistryName(new ResourceLocation(Ref.MODID, "hidden"));
        HIDDEN.setIcon(AnimIcon.of("anim_hidden", 8, 160));
        event.getRegistry().register(HIDDEN);

        AGE = new TaskType(AgeTask::new);
        AGE.setRegistryName(new ResourceLocation(Ref.MODID, "age"));
        AGE.setIcon(Icon.getIcon("minecraft:textures/items/cake.png"));
        event.getRegistry().register(AGE);

        // 1.8.0: «Деньги» (иметь на балансе / заплатить) и «Роль»
        MONEY_TASK = new TaskType(MoneyTask::new);
        MONEY_TASK.setRegistryName(new ResourceLocation(Ref.MODID, "money"));
        MONEY_TASK.setIcon(AnimIcon.of("anim_money", 8, 120));
        event.getRegistry().register(MONEY_TASK);

        ROLE_TASK = new TaskType(RoleTask::new);
        ROLE_TASK.setRegistryName(new ResourceLocation(Ref.MODID, "has_role"));
        ROLE_TASK.setIcon(AnimIcon.of("anim_role", 8, 130));
        event.getRegistry().register(ROLE_TASK);
    }

    @SubscribeEvent
    public static void registerRewards(RegistryEvent.Register<RewardType> event) {
        ROLE_REWARD = new RewardType(RoleReward::new);
        ROLE_REWARD.setRegistryName(new ResourceLocation(Ref.MODID, "role"));
        ROLE_REWARD.setIcon(AnimIcon.of("anim_role", 8, 130));
        event.getRegistry().register(ROLE_REWARD);

        SUBWORLD_ACCESS = new RewardType(SubworldAccessReward::new);
        SUBWORLD_ACCESS.setRegistryName(new ResourceLocation(Ref.MODID, "subworld_access"));
        SUBWORLD_ACCESS.setIcon(AnimIcon.of("anim_portal", 8, 110));
        event.getRegistry().register(SUBWORLD_ACCESS);

        SUBWORLD_EXPAND = new RewardType(SubworldExpandReward::new);
        SUBWORLD_EXPAND.setRegistryName(new ResourceLocation(Ref.MODID, "subworld_expand"));
        SUBWORLD_EXPAND.setIcon(AnimIcon.of("anim_expand", 8, 120));
        event.getRegistry().register(SUBWORLD_EXPAND);

        // 1.8.0: деньги EcoServ на баланс или во вклад
        MONEY_REWARD = new RewardType(MoneyReward::new);
        MONEY_REWARD.setRegistryName(new ResourceLocation(Ref.MODID, "money"));
        MONEY_REWARD.setIcon(AnimIcon.of("anim_money", 8, 120));
        event.getRegistry().register(MONEY_REWARD);
    }

    // --- выполнение -------------------------------------------------------------

    /** Отметить выполненными все условия «Активация» с этим названием в квестах команды игрока. */
    static void complete(EntityPlayerMP player, String name) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || name == null) {
            return;
        }
        QuestData data = file.getData(player);
        if (data == null) {
            return;
        }
        String wanted = name.trim();
        for (ActivationTask task : file.collect(ActivationTask.class)) {
            if (task.name != null && task.name.trim().equalsIgnoreCase(wanted)) {
                finish(task, data);
            }
        }
    }

    /** Скрытые достижения с этим видом условия и значением. */
    static void completeHidden(EntityPlayerMP player, String trigger, String value) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || value == null) {
            return;
        }
        QuestData data = file.getData(player);
        if (data == null) {
            return;
        }
        for (HiddenTask task : file.collect(HiddenTask.class)) {
            if (trigger.equals(task.trigger) && task.value.trim().equalsIgnoreCase(value.trim())) {
                finish(task, data);
            }
        }
    }

    private static void finish(Task task, QuestData data) {
        TaskData taskData = data.getTaskData(task);
        if (taskData != null && !taskData.isComplete() && task.quest.canStartTasks(data)) {
            taskData.setProgress(1L);
        }
    }

    /** Вход в мир: скрытые достижения «dimension». */
    private static void checkDimension(EntityPlayerMP player) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) {
            return;
        }
        QuestData data = file.getData(player);
        if (data == null) {
            return;
        }
        for (HiddenTask task : file.collect(HiddenTask.class)) {
            if ("dimension".equals(task.trigger) && WorldNames.matches(player.dimension, task.value)) {
                finish(task, data);
            }
        }
    }

    /** Раз в 5 секунд: предметы в инвентаре (скрытые) и возраст. */
    private static void periodic(EntityPlayerMP player) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) {
            return;
        }
        QuestData data = file.getData(player);
        if (data == null) {
            return;
        }
        for (HiddenTask task : file.collect(HiddenTask.class)) {
            if (!"item".equals(task.trigger) || task.value.isEmpty()) {
                continue;
            }
            TaskData td = data.getTaskData(task);
            if (td == null || td.isComplete()) {
                continue;
            }
            ItemKey key = ItemKey.parse(task.value);
            for (ItemStack stack : player.inventory.mainInventory) {
                if (!stack.isEmpty() && key.matches(stack)) {
                    finish(task, data);
                    break;
                }
            }
        }
        // 1.8.0: «Деньги» в режиме «иметь на балансе» и «Роль» выполняются сами
        for (MoneyTask task : file.collect(MoneyTask.class)) {
            if (task.pay()) {
                continue;
            }
            TaskData td = data.getTaskData(task);
            if (td != null && !td.isComplete()
                    && com.ecoserv.economy.EconomyManager.has(player.getUniqueID(), task.amount)) {
                finish(task, data);
            }
        }
        for (RoleTask task : file.collect(RoleTask.class)) {
            TaskData td = data.getTaskData(task);
            if (td == null || td.isComplete()) {
                continue;
            }
            for (String role : task.list()) {
                if (!role.isEmpty() && com.ecoserv.role.RoleManager.has(player.getUniqueID(), role)) {
                    finish(task, data);
                    break;
                }
            }
        }
        for (AgeTask task : file.collect(AgeTask.class)) {
            TaskData td = data.getTaskData(task);
            if (td == null || td.isComplete()) {
                continue;
            }
            AgeFilter filter = AgeFilter.parse(task.filter);
            if (filter != null && DatesManager.age(player.getUniqueID()) >= 0
                    && DatesManager.checkAge(player.getUniqueID(), filter)) {
                finish(task, data);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            checkDimension((EntityPlayerMP) event.player);
            periodic((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            checkDimension((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP && event.getAdvancement() != null) {
            completeHidden((EntityPlayerMP) event.getEntityPlayer(), "advancement",
                    event.getAdvancement().getId().toString());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 100 != 0) {
            return;
        }
        for (EntityPlayerMP player : PlayerUtil.allOnline()) {
            try {
                periodic(player);
            } catch (Throwable t) {
                EcoServ.LOG.debug("Проверка квестов для {}: {}", player.getName(), t.toString());
            }
        }
    }

    // --- вспомогательное --------------------------------------------------------

    /** Клиент: выполнен ли скрытый квест у своего игрока. */
    static boolean clientComplete(HiddenTask task) {
        if (clientComplete == null) {
            return false;
        }
        try {
            return (Boolean) clientComplete.invoke(null, task);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Перевод, который работает и на сервере; fallback — если ключа нет. */
    @SuppressWarnings("deprecation")
    static String tr(String key, String fallback) {
        String s = net.minecraft.util.text.translation.I18n.translateToLocal(key);
        return s == null || s.isEmpty() || s.equals(key) ? fallback : s;
    }
}
