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
        Item electric = Item.getItemFromBlock(ModRegistry2.ELECTRIC);
        for (int i = 0; i < 16; i++) {
            ModelLoader.setCustomModelResourceLocation(electric, i,
                    new ModelResourceLocation(new net.minecraft.util.ResourceLocation(Ref.MODID, "electric_" + com.ecoserv.electric.ElectricTypes.IDS[i]), "inventory"));
        }
        // 2.0 beta3: слоты своего контента (модели даёт ContentResourcePack)
        for (com.ecoserv.content.ContentBlock b : com.ecoserv.content.CustomContent.BLOCKS) {
            block(b);
        }
        for (com.ecoserv.content.ContentItem i : com.ecoserv.content.CustomContent.ITEMS) {
            item(i);
        }
        for (final com.ecoserv.content.ContentFluidBlock fb : com.ecoserv.content.CustomContent.FLUID_BLOCKS) {
            final ModelResourceLocation loc = new ModelResourceLocation(new net.minecraft.util.ResourceLocation(Ref.MODID, "cfluid"),
                    "fluid=" + fb.slot);
            ModelLoader.setCustomStateMapper(fb, new net.minecraft.client.renderer.block.statemap.StateMapperBase() {
                @Override
                public ModelResourceLocation getModelResourceLocation(net.minecraft.block.state.IBlockState state) {
                    return loc;
                }
            });
        }
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
