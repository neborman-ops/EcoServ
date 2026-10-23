package com.ecoserv.integration.quests;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
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
 * Условие FTB Quests «Активация»: выполняется, когда игрок нажимает кнопку или рычаг
 * с нужным названием (название задаётся палкой активации).
 */
public class ActivationTask extends Task {

    public String name = "";

    public ActivationTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return QuestActivation.TYPE;
    }

    @Override
    public void writeData(NBTTagCompound nbt) {
        super.writeData(nbt);
        nbt.setString("name", name);
    }

    @Override
    public void readData(NBTTagCompound nbt) {
        super.readData(nbt);
        name = nbt.getString("name");
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(name);
    }

    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        name = data.readString();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addString("name", () -> name, v -> name = v == null ? "" : v.trim(), "")
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.activation.name"));
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getAltTitle() {
        // серверный переводчик: этот метод может вызываться и на сервере
        String key = "ftbquests.task.ecoserv.activation";
        String title = net.minecraft.util.text.translation.I18n.translateToLocal(key);
        if (title == null || title.isEmpty() || title.equals(key)) {
            title = "Активация";
        }
        return title + ": " + TextFormatting.YELLOW + name;
    }

    /** Выполняется только нажатием кнопки в мире — кнопка в книге квестов ничего не делает. */
    @Override
    @SideOnly(Side.CLIENT)
    public void onButtonClicked(boolean canClick) {
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addMouseOverText(List<String> list, @Nullable TaskData data) {
        list.add(TextFormatting.GRAY + net.minecraft.client.resources.I18n.format("ecoserv.quest.activation.hint",
                TextFormatting.YELLOW + name + TextFormatting.GRAY));
    }

    @Override
    public TaskData createData(QuestData data) {
        return new Data(this, data);
    }

    /** Сдать вручную нельзя: прогресс ставит только нажатие кнопки (QuestActivation). */
    public static class Data extends BooleanTaskData<ActivationTask> {
        Data(ActivationTask task, QuestData data) {
            super(task, data);
        }

        @Override
        public boolean canSubmit(EntityPlayerMP player) {
            return false;
        }
    }
}
