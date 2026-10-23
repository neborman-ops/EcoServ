package com.ecoserv.integration.quests;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.reward.Reward;
import com.feed_the_beast.ftbquests.quest.reward.RewardType;
import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.util.ChatUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Награда «Роль»: выдать игроку роль (навсегда или на N дней). */
public class RoleReward extends Reward {

    public String role = "";
    public int days;

    public RoleReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return QuestActivation.ROLE_REWARD;
    }

    @Override
    public void writeData(NBTTagCompound nbt) {
        super.writeData(nbt);
        nbt.setString("role", role);
        nbt.setInteger("days", days);
    }

    @Override
    public void readData(NBTTagCompound nbt) {
        super.readData(nbt);
        role = nbt.getString("role");
        days = nbt.getInteger("days");
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(role);
        data.writeVarInt(days);
    }

    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        role = data.readString();
        days = data.readVarInt();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addString("role", () -> role, v -> role = v == null ? "" : v.trim().toLowerCase(), "")
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.role.role"));
        config.addInt("days", () -> days, v -> days = v, 0, 0, 36500)
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.role.days"));
    }

    @Override
    public void claim(EntityPlayerMP player, boolean notify) {
        Role r = RoleManager.get(role);
        if (r == null) {
            ChatUtil.send(player, "&cРоль «" + role + "» из награды квеста не найдена — сообщи админу.");
            return;
        }
        long until = days > 0 ? System.currentTimeMillis() + days * 86_400_000L : 0L;
        RoleManager.give(player.getUniqueID(), r.id, until, "quest");
        if (notify) {
            ChatUtil.send(player, "&aПолучена роль " + r.title() + (days > 0 ? " &7на " + days + " дн." : ""));
        }
    }

    @Override
    public String getAltTitle() {
        return QuestActivation.tr("ftbquests.reward.ecoserv.role", "Роль") + ": " + TextFormatting.GOLD + role
                + (days > 0 ? TextFormatting.GRAY + " (" + days + ")" : "");
    }
}
