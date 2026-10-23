package com.ecoserv.init;

import com.ecoserv.Ref;
import com.ecoserv.block.BlockMarket;
import com.ecoserv.block.BlockVending;
import com.ecoserv.block.BlockVendor;
import com.ecoserv.block.ItemBlockNamed;
import com.ecoserv.block.ItemBlockVending;
import com.ecoserv.item.ItemActivatorWand;
import com.ecoserv.item.ItemZoneWand;
import com.ecoserv.tile.TileMarket;
import com.ecoserv.tile.TileVending;
import com.ecoserv.tile.TileVendor;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = Ref.MODID)
public final class ModRegistry {

    public static final BlockMarket MARKET = new BlockMarket();
    public static final BlockVendor VENDOR = new BlockVendor();
    public static final BlockVending VENDING = new BlockVending();
    /** Барьер ролей: пропускает только игроков с нужной ролью. */
    public static final com.ecoserv.block.BlockRoleBarrier ROLE_BARRIER = new com.ecoserv.block.BlockRoleBarrier();
    /** Чекпоинт: точка возрождения в этом мире. */
    public static final com.ecoserv.block.BlockCheckpoint CHECKPOINT = new com.ecoserv.block.BlockCheckpoint();
    /** Сундук добычи: у каждого игрока своя копия содержимого. */
    public static final com.ecoserv.block.BlockLootChest LOOT_CHEST = new com.ecoserv.block.BlockLootChest();
    /** Датчик игрока: редстоун-сигнал, когда рядом игрок. */
    public static final com.ecoserv.block.BlockPlayerSensor PLAYER_SENSOR = new com.ecoserv.block.BlockPlayerSensor();
    /** ГЛИТЧ-сундук: у каждого игрока свои пропавшие предметы (только админам, не крафтится). */
    public static final com.ecoserv.block.BlockGlitchChest GLITCH_CHEST = new com.ecoserv.block.BlockGlitchChest();

    /** BLOCK DESTROY DANGER — зона с предупреждением. */
    public static final ItemZoneWand ZONE_WAND_DANGER = new ItemZoneWand(true);
    /** BLOCK DESTROY — зона, которая молча запрещает ломать. */
    public static final ItemZoneWand ZONE_WAND = new ItemZoneWand(false);
    /** Палка активации: названия для кнопок и рычагов (условие «Активация» в FTB Quests). */
    public static final ItemActivatorWand ACTIVATOR_WAND = new ItemActivatorWand();
    /** Палочка триггера: зоны, срабатывающие при входе игрока (сообщение, команды, сценарий, квест). */
    public static final com.ecoserv.item.ItemRegionWand TRIGGER_WAND =
            new com.ecoserv.item.ItemRegionWand(com.ecoserv.region.Region.TRIGGER);
    /** Палочка порталов: область-портал в мир или подмир. */
    public static final com.ecoserv.item.ItemRegionWand PORTAL_WAND =
            new com.ecoserv.item.ItemRegionWand(com.ecoserv.region.Region.PORTAL);
    /** Палочка заполнения (крафтится): заполняет область, тратит в 2 раза больше блоков. */
    public static final com.ecoserv.item.ItemFillWand FILL_WAND = new com.ecoserv.item.ItemFillWand();
    /** Палочка разрешения (1.8.0): ломать в зонах BLOCK DESTROY, в том числе в приключении. */
    public static final com.ecoserv.item.ItemPermitWand PERMIT_WAND = new com.ecoserv.item.ItemPermitWand();
    /** Палка GLITCH SELECT: что попадает в ГЛИТЧ-сундук (только админам). */
    public static final com.ecoserv.item.ItemGlitchSelect GLITCH_SELECT = new com.ecoserv.item.ItemGlitchSelect();

    private ModRegistry() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(MARKET);
        event.getRegistry().register(VENDOR);
        event.getRegistry().register(VENDING);
        GameRegistry.registerTileEntity(TileMarket.class, Ref.MODID + ":market");
        GameRegistry.registerTileEntity(TileVendor.class, Ref.MODID + ":vendor");
        GameRegistry.registerTileEntity(TileVending.class, Ref.MODID + ":vending");
        event.getRegistry().register(ROLE_BARRIER);
        event.getRegistry().register(CHECKPOINT);
        event.getRegistry().register(LOOT_CHEST);
        event.getRegistry().register(PLAYER_SENSOR);
        event.getRegistry().register(GLITCH_CHEST);
        GameRegistry.registerTileEntity(com.ecoserv.tile.TileGlitchChest.class, Ref.MODID + ":glitch_chest");
        GameRegistry.registerTileEntity(com.ecoserv.tile.TileRoleBarrier.class, Ref.MODID + ":role_barrier");
        GameRegistry.registerTileEntity(com.ecoserv.tile.TileCheckpoint.class, Ref.MODID + ":checkpoint");
        GameRegistry.registerTileEntity(com.ecoserv.tile.TileLootChest.class, Ref.MODID + ":loot_chest");
        GameRegistry.registerTileEntity(com.ecoserv.tile.TilePlayerSensor.class, Ref.MODID + ":player_sensor");
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemBlockNamed(MARKET, "Торговый терминал"));
        event.getRegistry().register(new ItemBlockNamed(VENDOR, "Склад продавца"));
        event.getRegistry().register(new ItemBlockVending(VENDING));
        event.getRegistry().register(ZONE_WAND_DANGER);
        event.getRegistry().register(ZONE_WAND);
        event.getRegistry().register(ACTIVATOR_WAND);
        event.getRegistry().register(TRIGGER_WAND);
        event.getRegistry().register(PORTAL_WAND);
        event.getRegistry().register(FILL_WAND);
        event.getRegistry().register(PERMIT_WAND);
        event.getRegistry().register(new ItemBlockNamed(ROLE_BARRIER, "Барьер ролей"));
        event.getRegistry().register(new ItemBlockNamed(CHECKPOINT, "Чекпоинт"));
        event.getRegistry().register(new ItemBlockNamed(LOOT_CHEST, "Сундук добычи"));
        event.getRegistry().register(new ItemBlockNamed(PLAYER_SENSOR, "Датчик игрока"));
        event.getRegistry().register(new ItemBlockNamed(GLITCH_CHEST, "ГЛИТЧ-сундук"));
        event.getRegistry().register(GLITCH_SELECT);
    }
}
