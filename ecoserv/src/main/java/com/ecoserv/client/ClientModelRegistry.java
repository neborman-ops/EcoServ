package com.ecoserv.client;

import com.ecoserv.Ref;
import com.ecoserv.init.ModRegistry;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Ref.MODID, value = Side.CLIENT)
public final class ClientModelRegistry {

    private ClientModelRegistry() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        registerBlock(ModRegistry.MARKET);
        registerBlock(ModRegistry.VENDOR);
        registerBlock(ModRegistry.VENDING);
        registerItem(ModRegistry.ZONE_WAND_DANGER);
        registerItem(ModRegistry.ZONE_WAND);
        registerItem(ModRegistry.ACTIVATOR_WAND);
        registerItem(ModRegistry.TRIGGER_WAND);
        registerItem(ModRegistry.PORTAL_WAND);
        registerItem(ModRegistry.FILL_WAND);
        registerBlock(ModRegistry.ROLE_BARRIER);
        registerBlock(ModRegistry.CHECKPOINT);
        registerBlock(ModRegistry.LOOT_CHEST);
        registerBlock(ModRegistry.PLAYER_SENSOR);
        registerBlock(ModRegistry.GLITCH_CHEST);
        registerItem(ModRegistry.GLITCH_SELECT);
    }

    private static void registerBlock(net.minecraft.block.Block block) {
        registerItem(Item.getItemFromBlock(block));
    }

    private static void registerItem(Item item) {
        if (item != null && item.getRegistryName() != null) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
