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
import com.ecoserv.dates.AgeFilter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Условие «Возраст» (проверка возраста, п. 24): выполняется, если возраст игрока по дню рождения
 * подходит под фильтр (18, >=16, <=12, 14-17). Нужен включённый ввод дня рождения.
 */
public class AgeTask extends Task {

    public String filter = ">=16";

    public AgeTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return QuestActivation.AGE;
    }

    @Override
    public void writeData(NBTTagCompound nbt) {
        super.writeData(nbt);
        nbt.setString("filter", filter);
    }

    @Override
    public void readData(NBTTagCompound nbt) {
        super.readData(nbt);
        filter = nbt.hasKey("filter") ? nbt.getString("filter") : ">=16";
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(filter);
    }

    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        filter = data.readString();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addString("filter", () -> filter, v -> filter = v == null ? "" : v.trim(), ">=16")
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.age.filter"));
    }

    @Override
    public String getAltTitle() {
        AgeFilter f = AgeFilter.parse(filter);
        return QuestActivation.tr("ftbquests.task.ecoserv.age", "Возраст") + ": " + TextFormatting.YELLOW
                + (f == null ? filter : f.describe());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addMouseOverText(List<String> list, @Nullable TaskData data) {
        list.add(TextFormatting.GRAY + QuestActivation.tr("ecoserv.quest.age.hint", "Укажи день рождения: /birthday"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onButtonClicked(boolean canClick) {
    }

    @Override
    public TaskData createData(QuestData data) {
        return new Data(this, data);
    }

    public static class Data extends BooleanTaskData<AgeTask> {
        Data(AgeTask task, QuestData data) {
            super(task, data);
        }

        @Override
        public boolean canSubmit(EntityPlayerMP player) {
            return false;
        }
    }
}
