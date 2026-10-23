package com.ecoserv.integration.quests;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldBorder;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldQuests;
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

/**
 * Награда «Расширение мира» (п. 3): расширяет барьер локального мира команды (как в skyblock).
 * Для подмиров со своими квестами; подмир можно не указывать — возьмётся тот, чьей главе принадлежит квест.
 */
public class SubworldExpandReward extends Reward {

    public String subworld = "";
    /** На сколько блоков; 0 — шаг из настроек подмира. */
    public int amount;

    public SubworldExpandReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return QuestActivation.SUBWORLD_EXPAND;
    }

    @Override
    public void writeData(NBTTagCompound nbt) {
        super.writeData(nbt);
        nbt.setString("subworld", subworld);
        nbt.setInteger("amount", amount);
    }

    @Override
    public void readData(NBTTagCompound nbt) {
        super.readData(nbt);
        subworld = nbt.getString("subworld");
        amount = nbt.getInteger("amount");
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(subworld);
        data.writeVarInt(amount);
    }

    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        subworld = data.readString();
        amount = data.readVarInt();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addEnum("subworld", () -> SubworldQuestClient.toUi(subworld), v -> subworld = SubworldQuestClient.fromUi(v),
                SubworldQuestClient.subworlds(subworld, true))
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.subworld.name"));
        config.addInt("amount", () -> amount, v -> amount = v, 0, 0, 100000)
                .setDisplayName(new TextComponentTranslation("ecoserv.quest.subworld.amount"));
    }

    private Subworld target() {
        Subworld sw = subworld.isEmpty() ? null : SubworldRegistry.get(subworld);
        if (sw == null) {
            sw = SubworldQuests.subworldOfChapter(quest.chapter.getCodeString());
        }
        return sw;
    }

    @Override
    public void claim(EntityPlayerMP player, boolean notify) {
        Subworld sw = target();
        if (sw == null || !sw.perOwner()) {
            ChatUtil.send(player, "&cНаграда «Расширение мира»: подмир с локальными мирами не найден — сообщи админу.");
            return;
        }
        String owner = SubworldTeams.ownerKey(player, sw);
        SubworldDim d = SubworldRegistry.dim(player.dimension);
        if (d == null || !d.isInstance() || !d.subworld.equals(sw.id) || !d.owner.equals(owner)) {
            d = SubworldRegistry.activeInstance(sw.id, owner);
        }
        if (d == null) {
            ChatUtil.send(player, "&eУ тебя ещё нет локального мира в «" + sw.name + "&e» — расширять нечего.");
            return;
        }
        // награда выдаётся каждому участнику — а мир у команды один: расширяем один раз
        String key = isTeamReward() ? null : getCodeString();
        if (key != null && d.appliedRewards.contains(key)) {
            if (notify) {
                ChatUtil.send(player, "&7Это расширение мира команда уже получила.");
            }
            return;
        }
        int before = d.border;
        int now = SubworldBorder.expand(d, amount);
        if (key != null && now > before) {
            // засчитываем, только если барьер и правда вырос (упёрся в максимум — пусть сработает у другого)
            d.appliedRewards.add(key);
            SubworldRegistry.markDirty();
        }
        SubworldRegistry.saveIfDirty();
        if (notify) {
            if (now < 0) {
                ChatUtil.send(player, "&eВ этом мире нет барьера.");
            } else if (now == before) {
                ChatUtil.send(player, "&eБарьер уже максимальный: &f" + now);
            } else {
                ChatUtil.send(player, "&aБарьер мира «" + d.saveName + "» расширен: &f" + before + " → " + now);
            }
        }
    }

    @Override
    public String getAltTitle() {
        return QuestActivation.tr("ftbquests.reward.ecoserv.subworld_expand", "Расширение мира")
                + (amount > 0 ? TextFormatting.GRAY + " +" + amount : "")
                + (subworld.isEmpty() ? "" : TextFormatting.AQUA + " (" + SubworldQuestClient.title(subworld) + ")");
    }
}
