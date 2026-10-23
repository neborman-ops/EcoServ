package com.ecoserv.integration.quests;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.util.misc.NameMap;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.task.BooleanTaskData;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskData;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * «Скрытое достижение» (п. 15) — как скрытые достижения Steam: пока не выполнено, квест
 * показывается как «???» с подсказкой; после выполнения — настоящее название.
 * Чтобы работало, у квеста не должно быть своего названия и описания (их берём отсюда),
 * а это условие — первое в квесте.
 * Выполняется само: по «Активации» (кнопка, зона-триггер, сценарий, команда),
 * входу в мир, получению достижения Minecraft или предмету в инвентаре.
 */
public class HiddenTask extends Task {

    public static final NameMap<String> TRIGGERS = NameMap.createWithTranslation("activation",
            (sender, s) -> "ecoserv.quest.hidden.trigger." + s, "activation", "dimension", "advancement", "item");

    /** Как прятать награды квеста, пока секрет не открыт (1.8.0). */
    public static final NameMap<String> REWARD_MODES = NameMap.createWithTranslation("show",
            (sender, s) -> "ecoserv.quest.hidden.rewards." + s, "show", "until_revealed", "until_complete");

    public String trigger = "activation";
    public String rewardMode = "show";
    public String value = "";
    public String revealed = "";
    public String hint = "";

    public HiddenTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return QuestActivation.HIDDEN;
    }

    @Override
    public void writeData(NBTTagCompound nbt) {
        super.writeData(nbt);
        nbt.setString("trigger", trigger);
        nbt.setString("value", value);
        nbt.setString("revealed", revealed);
        nbt.setString("hint", hint);
        if (!"show".equals(rewardMode)) {
            nbt.setString("reward_mode", rewardMode);
        }
    }

    @Override
    public void readData(NBTTagCompound nbt) {
        super.readData(nbt);
        trigger = nbt.hasKey("trigger") ? nbt.getString("trigger") : "activation";
        value = nbt.getString("value");
        revealed = nbt.getString("revealed");
        hint = nbt.getString("hint");
        rewardMode = normMode(nbt.getString("reward_mode"));
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(trigger);
        data.writeString(value);
        data.writeString(revealed);
        data.writeString(hint);
        data.writeString(rewardMode);
    }

    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        trigger = data.readString();
        value = data.readString();
        revealed = data.readString();
        hint = data.readString();
        rewardMode = normMode(data.readString());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addEnum("trigger", () -> trigger, v -> trigger = v, TRIGGERS)
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.hidden.trigger"));
        config.addString("value", () -> value, v -> value = v == null ? "" : v.trim(), "")
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.hidden.value"));
        config.addString("revealed", () -> revealed, v -> revealed = v == null ? "" : v, "")
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.hidden.revealed"));
        config.addString("hint", () -> hint, v -> hint = v == null ? "" : v, "")
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.hidden.hint"));
        config.addEnum("reward_mode", () -> rewardMode, v -> rewardMode = normMode(v), REWARD_MODES)
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.hidden.rewards"));
    }

    static String normMode(String v) {
        return "until_revealed".equals(v) || "until_complete".equals(v) ? v : "show";
    }

    static int rank(String mode) {
        return "until_complete".equals(mode) ? 2 : "until_revealed".equals(mode) ? 1 : 0;
    }

    /**
     * Режим скрытия наград для всего квеста: если в квесте несколько скрытых заданий,
     * действует самый строгий режим среди них — награды не «просвечивают» из-за того,
     * что у одного из заданий режим не выставлен.
     */
    public static String questMode(Quest quest) {
        String best = "show";
        for (Task t : quest.tasks) {
            if (t instanceof HiddenTask && rank(((HiddenTask) t).rewardMode) > rank(best)) {
                best = ((HiddenTask) t).rewardMode;
            }
        }
        return best;
    }

    private static boolean syncing;

    /**
     * После правки в редакторе остальные скрытые задания этого квеста получают тот же режим
     * скрытия наград (и сохраняются на сервере), чтобы у всех был один режим.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void editedFromGUI() {
        super.editedFromGUI();
        if (!syncing) {
            syncing = true;
            try {
                HiddenClient.syncSiblings(this);
            } catch (Throwable ignored) {
            } finally {
                syncing = false;
            }
        }
    }

    /** Открыт ли секрет для того, кто смотрит (на клиенте — свой игрок; на сервере — всегда). */
    public boolean isRevealed() {
        if (quest.getQuestFile().isClient()) {
            return QuestActivation.clientComplete(this);
        }
        return true;
    }

    @Override
    public String getAltTitle() {
        if (isRevealed()) {
            return revealed.isEmpty() ? QuestActivation.tr("ecoserv.quest.hidden.done", "Секрет открыт") : revealed;
        }
        return "???";
    }

    @Override
    public Icon getAltIcon() {
        // анимированные иконки (1.8.0): моргающий глаз / закрытый глаз с замком
        return isRevealed() ? AnimIcon.of("anim_hidden_open", 8, 140) : AnimIcon.of("anim_hidden", 8, 160);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addMouseOverText(List<String> list, @Nullable TaskData data) {
        if (isRevealed()) {
            list.add(TextFormatting.GREEN + QuestActivation.tr("ecoserv.quest.hidden.done", "Секрет открыт"));
        } else {
            list.add(TextFormatting.GRAY + (hint.isEmpty()
                    ? QuestActivation.tr("ecoserv.quest.hidden.secret", "Скрытое достижение") : hint));
            if (!"show".equals(questMode(quest))) {
                list.add(TextFormatting.DARK_PURPLE + QuestActivation.tr("ecoserv.quest.hidden.rewards_hidden",
                        "Награды скрыты, пока секрет не открыт"));
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onButtonClicked(boolean canClick) {
    }

    @Override
    public TaskData createData(QuestData data) {
        return new Data(this, data);
    }

    public static class Data extends BooleanTaskData<HiddenTask> {
        Data(HiddenTask task, QuestData data) {
            super(task, data);
        }

        @Override
        public boolean canSubmit(EntityPlayerMP player) {
            return false;
        }
    }
}
