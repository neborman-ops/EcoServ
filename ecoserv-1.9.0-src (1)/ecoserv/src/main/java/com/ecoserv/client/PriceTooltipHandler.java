package com.ecoserv.client;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.shop.ShopEntry;
import com.ecoserv.shop.ShopList;
import com.ecoserv.util.Fmt;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Строка в подсказке предмета: «Скупка: 64 шт → 58 000». Прайс присылает сервер. */
@SideOnly(Side.CLIENT)
public class PriceTooltipHandler {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (!EcoConfig.priceTooltip) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ShopEntry entry;
        try {
            entry = ClientShop.list().find(stack);
        } catch (RuntimeException e) {
            return;
        }
        if (entry == null || !entry.canSell()) {
            return;
        }
        int batch = entry.batchSize();
        String line = "§6" + Lang.get("ecoserv.tooltip.buyer") + ": §f" + batch + " "
                + Lang.get("ecoserv.unit.pcs") + " §7→ §e" + Fmt.money(entry.getEffectiveSellPrice());
        if (batch > 1) {
            line += " §8(≈" + Fmt.price(round2(entry.getUnitPrice())) + "/" + Lang.get("ecoserv.unit.pcs") + ")";
        }
        event.getToolTip().add(line);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0D) / 100.0D;
    }
}
