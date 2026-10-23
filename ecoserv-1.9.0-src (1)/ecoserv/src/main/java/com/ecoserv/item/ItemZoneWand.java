package com.ecoserv.item;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.zone.Zone;
import com.ecoserv.zone.ZoneEvents;
import com.ecoserv.zone.ZoneManager;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Палочки BLOCK DESTROY DANGER и BLOCK DESTROY: ПКМ по первому углу, ПКМ по второму — зона готова.
 * В зоне ломать и ставить блоки может только оператор в креативе.
 * DANGER при попытке пишет, что ломать нельзя; обычная — молча не даёт.
 */
public class ItemZoneWand extends Item {

    private final boolean danger;

    public ItemZoneWand(boolean danger) {
        this.danger = danger;
        String name = danger ? "zone_wand_danger" : "zone_wand";
        setRegistryName(Ref.MODID, name);
        setTranslationKey(Ref.MODID + "." + name);
        setMaxStackSize(1);
        setCreativeTab(ModTabs.MAIN);
        setFull3D();
    }

    public String zoneType() {
        return danger ? Zone.DANGER : Zone.SILENT;
    }

    public String title() {
        return danger ? "BLOCK DESTROY DANGER" : "BLOCK DESTROY";
    }

    /** Срабатывает раньше, чем блок (сундук, дверь, кнопка) обработает клик. */
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            ZoneEvents.onWandClick((EntityPlayerMP) player, this, world, pos);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && player.isSneaking() && ZoneManager.selection(player.getUniqueID()) != null) {
            ZoneManager.clearSelection(player.getUniqueID());
            ChatUtil.actionBar(player, "&7Выделение сброшено");
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /** Палочка никогда не ломает блоки (даже в креативе). */
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
        return danger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("§7ПКМ по блоку — первый угол, ПКМ ещё раз — второй угол.");
        tooltip.add("§7Зона создаётся сразу. Shift+ПКМ в воздух — сбросить выбор.");
        tooltip.add("§7ЛКМ по блоку — какая здесь зона.");
        tooltip.add(danger
                ? "§cВ зоне при попытке ломать пишет: «ломать нельзя»."
                : "§9В зоне молча запрещает ломать и ставить.");
        tooltip.add("§8Ломать и ставить в зоне может только оператор в креативе.");
        tooltip.add("§8Команды: /zone list | remove | vert | type | tp");
    }
}
