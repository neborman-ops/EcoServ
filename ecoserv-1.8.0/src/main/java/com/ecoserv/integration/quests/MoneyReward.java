package com.ecoserv.integration.quests;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.extra.Bank;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.extra.ExtraSync;
import com.ecoserv.extra.MoneyNote;
import com.ecoserv.role.RoleManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.util.misc.NameMap;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.reward.Reward;
import com.feed_the_beast.ftbquests.quest.reward.RewardType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Награда «Деньги EcoServ»: на баланс или сразу во вклад, по желанию — с множителем роли. */
public class MoneyReward extends Reward {
   public static final NameMap<String> TARGETS = NameMap.createWithTranslation(
      "balance", (sender, s) -> "ecoserv.quest.money_reward.target." + s, new String[]{"balance", "bank"}
   );
   public static final NameMap<String> MULT = NameMap.createWithTranslation(
      "no", (sender, s) -> "ecoserv.quest.money_reward.mult." + s, new String[]{"no", "yes"}
   );
   public int amount = 100;
   public String target = "balance";
   public String multiplier = "no";

   public MoneyReward(Quest quest) {
      super(quest);
   }

   public RewardType getType() {
      return QuestActivation.MONEY_REWARD;
   }

   public void writeData(NBTTagCompound nbt) {
      super.writeData(nbt);
      nbt.setInteger("amount", this.amount);
      nbt.setString("target", this.target);
      nbt.setString("multiplier", this.multiplier);
   }

   public void readData(NBTTagCompound nbt) {
      super.readData(nbt);
      this.amount = Math.max(1, nbt.getInteger("amount"));
      this.target = "bank".equals(nbt.getString("target")) ? "bank" : "balance";
      this.multiplier = "yes".equals(nbt.getString("multiplier")) ? "yes" : "no";
   }

   public void writeNetData(DataOut data) {
      super.writeNetData(data);
      data.writeVarInt(this.amount);
      data.writeString(this.target);
      data.writeString(this.multiplier);
   }

   public void readNetData(DataIn data) {
      super.readNetData(data);
      this.amount = data.readVarInt();
      this.target = data.readString();
      this.multiplier = data.readString();
   }

   @SideOnly(Side.CLIENT)
   public void getConfig(ConfigGroup config) {
      super.getConfig(config);
      config.addInt("amount", () -> this.amount, v -> this.amount = Math.max(1, v), 100, 1, Integer.MAX_VALUE)
         .setDisplayName(new TextComponentTranslation("ecoserv.quest.money_reward.amount", new Object[0]));
      config.addEnum("target", () -> this.target, v -> this.target = "bank".equals(v) ? "bank" : "balance", TARGETS)
         .setDisplayName(new TextComponentTranslation("ecoserv.quest.money_reward.target", new Object[0]));
      config.addEnum("multiplier", () -> this.multiplier, v -> this.multiplier = "yes".equals(v) ? "yes" : "no", MULT)
         .setDisplayName(new TextComponentTranslation("ecoserv.quest.money_reward.mult", new Object[0]));
   }

   public void claim(EntityPlayerMP player, boolean notify) {
      long sum = (long)this.amount;
      if ("yes".equals(this.multiplier)) {
         double m = RoleManager.rewardMultiplier(player.getUniqueID());
         if (m > 0.0 && !Double.isNaN(m) && !Double.isInfinite(m)) {
            sum = Math.max(1L, Math.round((double)sum * m));
         }
      }

      String note = "Награда квеста";
      if ("bank".equals(this.target) && ExtraConfig.bankEnabled) {
         Bank.credit(player.getUniqueID(), sum, note);
         ExtraSync.send(player);
         if (notify) {
            ChatUtil.send(player, "&aНаграда квеста: &e+" + Fmt.money(sum) + " &aво вклад");
         }
      } else {
         MoneyNote.set(note);

         try {
            EconomyManager.give(player.getUniqueID(), sum);
         } finally {
            MoneyNote.clear();
         }

         if (notify) {
            ChatUtil.send(player, "&aНаграда квеста: &e+" + Fmt.money(sum));
         }
      }
   }

   public String getAltTitle() {
      return TextFormatting.GOLD + "+" + Fmt.money((long)this.amount) + ("bank".equals(this.target) ? TextFormatting.GRAY + " (" + QuestActivation.tr("ecoserv.quest.money_reward.to_bank", "во вклад") + ")" : "");
   }

   public Icon getAltIcon() {
      return "bank".equals(this.target) ? AnimIcon.of("anim_bank", 8, 110) : AnimIcon.of("anim_money", 8, 120);
   }
}
