package com.ecoserv.client;

import com.ecoserv.Ref;
import com.ecoserv.v2.ModRegistry2;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Модели предметов EcoServ 2.0. */
@Mod.EventBusSubscriber(modid = Ref.MODID, value = Side.CLIENT)
public final class ClientModels2 {

    private ClientModels2() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        block(ModRegistry2.ATM);
        block(ModRegistry2.LOGIC);
        item(ModRegistry2.DEV_GLASSES);
        Item marker = Item.getItemFromBlock(ModRegistry2.MARKER);
        for (int i = 0; i < 16; i++) {
            ModelLoader.setCustomModelResourceLocation(marker, i,
                    new ModelResourceLocation(new net.minecraft.util.ResourceLocation(Ref.MODID, "marker_" + com.ecoserv.dev.BlockMarker.IDS[i]), "inventory"));
        }
    }

    static void block(net.minecraft.block.Block b) {
        item(Item.getItemFromBlock(b));
    }

    static void item(Item item) {
        if (item != null && item.getRegistryName() != null) {
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
