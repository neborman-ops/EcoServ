package com.ecoserv.block;

import com.ecoserv.util.Fmt;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Предмет блока продажи. Показывает в подсказке, что внутри —
 * блок ломается вместе с товаром и кассой, как шалкер.
 */
public class ItemBlockVending extends ItemBlockNamed {

    public ItemBlockVending(Block block) {
        super(block, "Блок продажи");
        setMaxStackSize(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("BlockEntityTag")) {
            tooltip.add("§7Ставь и настраивай: цена, количество, товар");
            tooltip.add("§8Вход для автоматики — грань с красной точкой");
            return;
        }
        NBTTagCompound data = tag.getCompoundTag("BlockEntityTag");

        long money = data.getLong("Money");
        if (money > 0L) {
            tooltip.add("§6В кассе: §f" + Fmt.money(money));
        }
        String owner = data.getString("OwnerName");
        if (!owner.isEmpty()) {
            tooltip.add("§7Владелец: §f" + owner);
        }

        int items = 0;
        try {
            NBTTagCompound inv = data.getCompoundTag("Inventory");
            NBTTagList list = inv.getTagList("Items", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                items += list.getCompoundTagAt(i).getByte("Count");
            }
        } catch (Exception ignored) {
            // подсказка не критична
        }
        if (items > 0) {
            tooltip.add("§7Внутри предметов: §f" + items);
        }
    }
}
