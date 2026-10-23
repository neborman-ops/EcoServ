package com.ecoserv.dev;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import com.ecoserv.util.Perms;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Маркер-помощник для шаблонов (2.0): точки появления, команды, финиш, чекпоинт, места предметов и мобов,
 * точки камеры, стыки структур, невидимый свет, звук, надпись. Обычным игрокам не видны и не мешают ходить;
 * разработчик (режим /dev или очки) видит их с подписями.
 */
public class BlockMarker extends Block {

    public static final String NAME = "marker";
    public static final PropertyInteger TYPE = PropertyInteger.create("type", 0, 15);

    public static final int SPAWN = 0;
    public static final int TEAM_RED = 1;
    public static final int TEAM_BLUE = 2;
    public static final int CHECKPOINT = 3;
    public static final int FINISH = 4;
    public static final int KILL = 5;
    public static final int ITEM = 6;
    public static final int MOB = 7;
    public static final int NPC = 8;
    public static final int CAMERA = 9;
    public static final int PATH = 10;
    public static final int CONNECTOR = 11;
    public static final int LIGHT = 12;
    public static final int SOUND = 13;
    public static final int TEXT = 14;
    public static final int CUSTOM = 15;

    public static final String[] IDS = {"spawn", "team_red", "team_blue", "checkpoint", "finish", "kill", "item", "mob",
            "npc", "camera", "path", "connector", "light", "sound", "text", "custom"};
    public static final String[] TITLES = {"Точка появления", "Появление: красные", "Появление: синие", "Чекпоинт",
            "Финиш", "Зона смерти", "Место предмета", "Место моба", "Место NPC", "Точка камеры", "Точка пути",
            "Стык структуры", "Невидимый свет", "Звук", "Надпись в мире", "Свой маркер"};
    public static final String[] HINTS = {"Где появляются игроки (в своих играх)", "Старт команды красных",
            "Старт команды синих", "Точка сохранения в своих играх", "Дошёл сюда — финишировал",
            "Коснулся — выбыл/назад на точку", "Данные: предмет (minecraft:apple 3) — появляется в игре",
            "Данные: моб (minecraft:zombie)", "Данные: id NPC — где он стоит в шаблоне",
            "Имя — точка катсцены (имя_1, имя_2…), поворот — куда смотрит камера",
            "Точки пути для NPC и спутников (имя_1, имя_2…)", "Данные: группа — куда пристыковываются комнаты при генерации",
            "Свет 15 без факела", "Данные: звук и громкость (minecraft:block.water.ambient 1)",
            "Данные: текст (| — новая строка, & — цвета). Видна всем", "Для своих сценариев"};
    public static final int[] COLORS = {0x40FF40, 0xFF4040, 0x4080FF, 0xFFE040, 0xFFD700, 0xFF2020, 0x40FFFF, 0xA040FF,
            0x40FF90, 0xFFFFFF, 0x90C0FF, 0xFF9020, 0xFFFF90, 0xFF80C0, 0xE0E0E0, 0xC0C0C0};

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.25D, 0.25D, 0.25D, 0.75D, 0.75D, 0.75D);

    public BlockMarker() {
        super(Material.BARRIER);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(0.1F);
        setResistance(6000000.0F);
        setSoundType(SoundType.GLASS);
        setCreativeTab(ModTabs.MAIN);
        setDefaultState(blockState.getBaseState().withProperty(TYPE, 0));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileMarker();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TYPE);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(TYPE, meta & 15);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(TYPE);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state);
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        for (int i = 0; i < 16; i++) {
            items.add(new ItemStack(this, 1, i));
        }
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getBlock() == this && state.getValue(TYPE) == LIGHT ? 15 : 0;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileMarker) {
            ((TileMarker) te).yaw = placer.rotationYaw;
            ((TileMarker) te).changed();
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return Perms.isAdmin(player) || player.isCreative();
        }
        if (hand == EnumHand.MAIN_HAND && player instanceof EntityPlayerMP && Perms.isAdmin(player)) {
            new MarkerScreen((EntityPlayerMP) player, pos.toImmutable()).open();
            return true;
        }
        return false;
    }

    @Override
    public float getPlayerRelativeBlockHardness(IBlockState state, EntityPlayer player, World world, BlockPos pos) {
        return Perms.isAdmin(player) ? 1.0F : 0.0F;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }
}
