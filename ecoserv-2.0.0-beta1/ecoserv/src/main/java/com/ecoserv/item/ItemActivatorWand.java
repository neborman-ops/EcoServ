package com.ecoserv.item;

import com.ecoserv.Ref;
import com.ecoserv.activator.Activator;
import com.ecoserv.activator.ActivatorManager;
import com.ecoserv.init.ModTabs;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.BlockLever;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Палка активации: ПКМ по кнопке или рычагу — задать ему название.
 * Когда игрок нажмёт такую кнопку, выполнится условие «Активация» с этим названием в FTB Quests.
 */
public class ItemActivatorWand extends Item {

    public static final String NAME = "activator_wand";

    public ItemActivatorWand() {
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setMaxStackSize(1);
        setCreativeTab(ModTabs.MAIN);
        setFull3D();
    }

    /** Раньше, чем кнопка нажмётся: админ настраивает, а не активирует. */
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            configure((EntityPlayerMP) player, world, pos.toImmutable());
        }
        return EnumActionResult.SUCCESS;
    }

    private static void configure(EntityPlayerMP player, World world, final BlockPos pos) {
        if (!Perms.isAdmin(player)) {
            ChatUtil.actionBar(player, "&cПалка активации только для операторов");
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!ActivatorManager.isActivatorBlock(state)) {
            ChatUtil.actionBar(player, "&cЭто не кнопка и не рычаг");
            return;
        }
        final int dim = world.provider.getDimension();
        Activator existing = ActivatorManager.get(dim, pos);
        final String what = state.getBlock() instanceof BlockLever ? "рычага" : "кнопки";

        if (player.isSneaking()) {
            if (existing == null) {
                ChatUtil.actionBar(player, "&7У этого " + what + " нет названия");
            } else {
                ActivatorManager.unbind(dim, pos);
                ChatUtil.send(player, "&eНазвание &f«" + existing.name + "» &eснято с " + what + ".");
            }
            return;
        }

        new com.ecoserv.ui.PromptScreen(player, "Название " + what,
                "&7По названию " + what + " находят условие «Активация» в FTB Quests и команды /activator. "
                        + "Без пробелов (можно _ или -), например &fдверь_шахты&7.",
                "Название", existing == null ? "" : existing.name, "дверь_шахты",
                new ItemStack(net.minecraft.init.Blocks.LEVER), 48, (who, text) -> {
                    String name = text == null ? "" : text.trim();
                    if (name.isEmpty() || name.length() > 48) {
                        throw new com.ecoserv.ui.UiError("Название должно быть от 1 до 48 символов.");
                    }
                    if (name.matches(".*\\s.*")) {
                        throw new com.ecoserv.ui.UiError("Название без пробелов (можно _ или -), например: дверь_шахты");
                    }
                    MinecraftServer server = PlayerUtil.server();
                    WorldServer target = server == null ? null : server.getWorld(dim);
                    if (target == null || !ActivatorManager.isActivatorBlock(target.getBlockState(pos))) {
                        throw new com.ecoserv.ui.UiError("На этом месте уже нет кнопки или рычага.");
                    }
                    ActivatorManager.bind(dim, pos, name, who.getName());
                    ChatUtil.send(who, "&aГотово: &f«" + name + "» &7(" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ")");
                    ChatUtil.sendRaw(who, ActivatorManager.hasQuestListener()
                            ? " &7В FTB Quests добавь в квест условие &fАктивация&7 и впиши название &f" + name
                            : " &eУсловие «Активация» для FTB Quests в этой сборке не подключено (см. README), "
                            + "но можно повесить команду: &f/activator command " + name + " <команда>");
                }, null).open();
    }

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Override
    public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
        return false;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("§7ПКМ по кнопке или рычагу — дать название.");
        tooltip.add("§7Shift+ПКМ — снять название, ЛКМ — посмотреть.");
        tooltip.add("§7Нажатие кнопки выполняет в FTB Quests");
        tooltip.add("§7условие §fАктивация§7 с этим названием.");
        tooltip.add("§8Список: /activator list");
    }
}
