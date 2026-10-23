package com.ecoserv.v2;

import com.ecoserv.Ref;
import com.ecoserv.block.ItemBlockNamed;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Блоки и предметы EcoServ 2.0. */
@Mod.EventBusSubscriber(modid = Ref.MODID)
public final class ModRegistry2 {

    /** Банкомат. */
    public static final com.ecoserv.bank.BlockBank ATM = new com.ecoserv.bank.BlockBank();
    /** Маркеры-помощники шаблонов (16 видов). */
    public static final com.ecoserv.dev.BlockMarker MARKER = new com.ecoserv.dev.BlockMarker();
    /** Блок-программа. */
    public static final com.ecoserv.dev.BlockLogic LOGIC = new com.ecoserv.dev.BlockLogic();
    /** Электроника (16 видов). */
    public static final com.ecoserv.electric.BlockElectric ELECTRIC = new com.ecoserv.electric.BlockElectric();
    /** Очки разработчика. */
    public static final com.ecoserv.dev.ItemDevGlasses DEV_GLASSES = new com.ecoserv.dev.ItemDevGlasses();

    private ModRegistry2() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(ATM);
        GameRegistry.registerTileEntity(com.ecoserv.bank.TileBank.class, Ref.MODID + ":atm");
        event.getRegistry().register(MARKER);
        event.getRegistry().register(LOGIC);
        GameRegistry.registerTileEntity(com.ecoserv.dev.TileMarker.class, Ref.MODID + ":marker");
        GameRegistry.registerTileEntity(com.ecoserv.dev.TileLogic.class, Ref.MODID + ":logic_block");
        event.getRegistry().register(ELECTRIC);
        GameRegistry.registerTileEntity(com.ecoserv.electric.TileElectric.class, Ref.MODID + ":electric");
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemBlockNamed(ATM, "Банкомат"));
        event.getRegistry().register(new com.ecoserv.dev.ItemBlockMarker(MARKER));
        event.getRegistry().register(new ItemBlockNamed(LOGIC, "Блок-программа"));
        event.getRegistry().register(DEV_GLASSES);
        event.getRegistry().register(new com.ecoserv.electric.ItemBlockElectric(ELECTRIC));
    }
}
