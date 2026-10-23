package com.ecoserv.client;

import com.ecoserv.shop.ShopList;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Прайс, который видит клиент. На сервере его присылает сервер (для текущего мира), а в одиночной игре
 * сервер в том же процессе — берём прайс мира напрямую (у подмиров со своими квестами он свой).
 */
@SideOnly(Side.CLIENT)
public final class ClientShop {

    private ClientShop() {
    }

    public static ShopList list() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isIntegratedServerRunning() && mc.player != null) {
            return ShopList.forDim(mc.player.dimension);
        }
        return ShopList.get();
    }
}
