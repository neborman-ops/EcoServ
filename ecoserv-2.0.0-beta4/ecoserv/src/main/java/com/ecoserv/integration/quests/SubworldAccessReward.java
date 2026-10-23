package com.ecoserv.integration.quests;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeams;
import com.ecoserv.util.ChatUtil;
import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.reward.Reward;
import com.feed_the_beast.ftbquests.quest.reward.RewardType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

/** Награда «Доступ к подмиру» (п. 3): открывает подмир с доступом «наградой в квестах» игроку и его команде. */
public class SubworldAccessReward extends Reward {

    public String subworld = "";

    public SubworldAccessReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return QuestActivation.SUBWORLD_ACCESS;
    }

    @Override
    public void writeData(NBTTagCompound nbt) {
        super.writeData(nbt);
        nbt.setString("subworld", subworld);
    }

    @Override
    public void readData(NBTTagCompound nbt) {
        super.readData(nbt);
        subworld = nbt.getString("subworld");
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(subworld);
    }

    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        subworld = data.readString();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addEnum("subworld", () -> SubworldQuestClient.toUi(subworld), v -> subworld = SubworldQuestClient.fromUi(v),
                SubworldQuestClient.subworlds(subworld, false))
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.subworld.name"));
    }

    @Override
    public void claim(EntityPlayerMP player, boolean notify) {
        Subworld sw = SubworldRegistry.get(subworld);
        if (sw == null) {
            ChatUtil.send(player, "&cПодмир «" + subworld + "» из награды квеста не найден — сообщи админу.");
            return;
        }
        SubworldRegistry.unlock(player.getUniqueID().toString(), sw.id);
        String team = SubworldTeams.teamKey(player.getUniqueID());
        if (team != null) {
            for (UUID member : SubworldTeams.members(team)) {
                SubworldRegistry.unlock(member.toString(), sw.id);
            }
        }
        if (notify) {
            ChatUtil.send(player, "&aОткрыт доступ в подмир &f" + sw.name + "&a! Меню миров — кнопка-планета в инвентаре.");
        }
    }

    @Override
    public String getAltTitle() {
        return QuestActivation.tr("ftbquests.reward.ecoserv.subworld_access", "Доступ к подмиру") + ": "
                + TextFormatting.AQUA + SubworldQuestClient.title(subworld);
    }
}
