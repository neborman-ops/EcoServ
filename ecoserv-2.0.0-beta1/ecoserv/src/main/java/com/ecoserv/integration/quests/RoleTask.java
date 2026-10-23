package com.ecoserv.integration.quests;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.task.BooleanTaskData;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskData;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Задание «Роль»: выполняется само, когда у игрока есть нужная роль (или любая из списка через запятую). */
public class RoleTask extends Task {
   public String roles = "";

   public RoleTask(Quest quest) {
      super(quest);
   }

   public TaskType getType() {
      return QuestActivation.ROLE_TASK;
   }

   public void writeData(NBTTagCompound nbt) {
      super.writeData(nbt);
      nbt.setString("roles", this.roles);
   }

   public void readData(NBTTagCompound nbt) {
      super.readData(nbt);
      this.roles = nbt.getString("roles");
   }

   public void writeNetData(DataOut data) {
      super.writeNetData(data);
      data.writeString(this.roles);
   }

   public void readNetData(DataIn data) {
      super.readNetData(data);
      this.roles = data.readString();
   }

   @SideOnly(Side.CLIENT)
   public void getConfig(ConfigGroup config) {
      super.getConfig(config);
      config.addString("roles", () -> this.roles, v -> this.roles = v == null ? "" : v.trim().toLowerCase(), "")
         .setDisplayName(new TextComponentTranslation("ecoserv.quest.role_task.roles", new Object[0]));
   }

   String[] list() {
      String[] parts = this.roles.split("[,; ]+");
      return parts;
   }

   public String getAltTitle() {
      return QuestActivation.tr("ftbquests.task.ecoserv.has_role", "Роль") + ": " + TextFormatting.GOLD + (this.roles.isEmpty() ? "?" : this.roles);
   }

   public Icon getAltIcon() {
      return AnimIcon.of("anim_role", 8, 130);
   }

   @SideOnly(Side.CLIENT)
   public void addMouseOverText(List<String> list, @Nullable TaskData data) {
      list.add(TextFormatting.GRAY + QuestActivation.tr("ecoserv.quest.role_task.hint", "Выполнится само, когда у тебя будет эта роль"));
   }

   @SideOnly(Side.CLIENT)
   public void onButtonClicked(boolean canClick) {
   }

   public TaskData createData(QuestData data) {
      return new RoleTask.Data(this, data);
   }

   public static class Data extends BooleanTaskData<RoleTask> {
      Data(RoleTask task, QuestData data) {
         super(task, data);
      }

      public boolean canSubmit(EntityPlayerMP player) {
         return false;
      }
   }
}
