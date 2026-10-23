package com.ecoserv.integration.quests;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.extra.MoneyNote;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
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
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Задание «Деньги»: либо иметь на балансе нужную сумму (выполняется само), либо
 * заплатить её кнопкой в окне квеста (деньги списываются).
 */
public class MoneyTask extends Task {
   public static final NameMap<String> MODES = NameMap.createWithTranslation(
      "have", (sender, s) -> "ecoserv.quest.money.mode." + s, new String[]{"have", "pay"}
   );
   public int amount = 100;
   public String mode = "have";

   public MoneyTask(Quest quest) {
      super(quest);
   }

   public TaskType getType() {
      return QuestActivation.MONEY_TASK;
   }

   boolean pay() {
      return "pay".equals(this.mode);
   }

   public void writeData(NBTTagCompound nbt) {
      super.writeData(nbt);
      nbt.setInteger("amount", this.amount);
      nbt.setString("mode", this.mode);
   }

   public void readData(NBTTagCompound nbt) {
      super.readData(nbt);
      this.amount = Math.max(1, nbt.getInteger("amount"));
      this.mode = "pay".equals(nbt.getString("mode")) ? "pay" : "have";
   }

   public void writeNetData(DataOut data) {
      super.writeNetData(data);
      data.writeVarInt(this.amount);
      data.writeString(this.mode);
   }

   public void readNetData(DataIn data) {
      super.readNetData(data);
      this.amount = data.readVarInt();
      this.mode = data.readString();
   }

   @SideOnly(Side.CLIENT)
   public void getConfig(ConfigGroup config) {
      super.getConfig(config);
      config.addInt("amount", () -> this.amount, v -> this.amount = Math.max(1, v), 100, 1, Integer.MAX_VALUE)
         .setDisplayName(new TextComponentTranslation("ecoserv.quest.money.amount", new Object[0]));
      config.addEnum("mode", () -> this.mode, v -> this.mode = "pay".equals(v) ? "pay" : "have", MODES)
         .setDisplayName(new TextComponentTranslation("ecoserv.quest.money.mode", new Object[0]));
   }

   public String getAltTitle() {
      return (this.pay() ? QuestActivation.tr("ecoserv.quest.money.title_pay", "Заплатить") : QuestActivation.tr("ftbquests.task.ecoserv.money", "Деньги"))
         + ": "
         + TextFormatting.GOLD
         + Fmt.money((long)this.amount);
   }

   public Icon getAltIcon() {
      return AnimIcon.of("anim_money", 8, 120);
   }

   @SideOnly(Side.CLIENT)
   public void addMouseOverText(List<String> list, @Nullable TaskData data) {
      list.add(
         TextFormatting.GRAY
            + (this.pay()
               ? QuestActivation.tr("ecoserv.quest.money.hint_pay", "Нажми, чтобы заплатить — сумма спишется с баланса")
               : QuestActivation.tr("ecoserv.quest.money.hint_have", "Накопи эту сумму на балансе (вклад не считается)"))
      );
   }

   @SideOnly(Side.CLIENT)
   public void onButtonClicked(boolean canClick) {
      if (this.pay()) {
         super.onButtonClicked(canClick);
      }
   }

   public TaskData createData(QuestData data) {
      return new MoneyTask.Data(this, data);
   }

   public static class Data extends BooleanTaskData<MoneyTask> {
      private final MoneyTask money;

      Data(MoneyTask task, QuestData data) {
         super(task, data);
         this.money = task;
      }

      public boolean canSubmit(EntityPlayerMP player) {
         return this.money.pay() && EconomyManager.has(player.getUniqueID(), (long)this.money.amount);
      }

      public void submitTask(EntityPlayerMP player, ItemStack item) {
         if (this.money.pay() && !this.isComplete()) {
            long sum = (long)this.money.amount;
            if (!EconomyManager.has(player.getUniqueID(), sum)) {
               ChatUtil.send(player, "&cНе хватает денег: нужно &f" + Fmt.money(sum) + "&c, на балансе &f" + Fmt.money(EconomyManager.get(player.getUniqueID())));
               return;
            }

            MoneyNote.set("Оплата задания квеста");

            boolean ok;
            try {
               ok = EconomyManager.take(player.getUniqueID(), sum);
            } finally {
               MoneyNote.clear();
            }

            if (ok) {
               this.setProgress(1L);
               ChatUtil.send(player, "&aОплачено: &f" + Fmt.money(sum));
            }
         }
      }
   }
}
