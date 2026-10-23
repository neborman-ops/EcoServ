package com.ecoserv.item;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import com.ecoserv.region.Region;
import com.ecoserv.region.RegionManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.Selection;
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
 * Палочка триггера (п. 14) и палочка порталов (п. 4): ПКМ — точка 1, ПКМ — точка 2,
 * затем название в чат — и область готова. Настройка — в меню (клик по ссылке в чате,
 * /trigger menu, /portal menu или вкладка «Настроить порталы»).
 */
public class ItemRegionWand extends Item {

    private final String kind;

    public ItemRegionWand(String kind) {
        this.kind = kind;
        String name = kind + "_wand";
        setRegistryName(Ref.MODID, name);
        setTranslationKey(Ref.MODID + "." + name);
        setMaxStackSize(1);
        setCreativeTab(ModTabs.MAIN);
        setFull3D();
        if (Region.PORTAL.equals(kind)) {
            // 1.8.0: модель палочки меняется в режиме «Чёрная дыра» (как конфигуратор Mekanism)
            addPropertyOverride(new net.minecraft.util.ResourceLocation(Ref.MODID, "blackhole"),
                    new net.minecraft.item.IItemPropertyGetter() {
                        @Override
                        @SideOnly(Side.CLIENT)
                        public float apply(ItemStack stack, @Nullable World world, @Nullable net.minecraft.entity.EntityLivingBase entity) {
                            return isBlackHole(stack) ? 1.0F : 0.0F;
                        }
                    });
            // 1.9.0: третий режим — «Монолит»
            addPropertyOverride(new net.minecraft.util.ResourceLocation(Ref.MODID, "monolith"),
                    new net.minecraft.item.IItemPropertyGetter() {
                        @Override
                        @SideOnly(Side.CLIENT)
                        public float apply(ItemStack stack, @Nullable World world, @Nullable net.minecraft.entity.EntityLivingBase entity) {
                            return isMonolith(stack) ? 1.0F : 0.0F;
                        }
                    });
        }
    }

    /** Режим палочки порталов: обычный, «Чёрная дыра» или «Монолит». */
    public static String style(ItemStack stack) {
        String s = stack.hasTagCompound() ? stack.getTagCompound().getString("style") : "";
        return Region.STYLE_BLACKHOLE.equals(s) || Region.STYLE_MONOLITH.equals(s) ? s : Region.STYLE_NORMAL;
    }

    public static boolean isBlackHole(ItemStack stack) {
        return Region.STYLE_BLACKHOLE.equals(style(stack));
    }

    public static boolean isMonolith(ItemStack stack) {
        return Region.STYLE_MONOLITH.equals(style(stack));
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String base = super.getItemStackDisplayName(stack);
        if (!Region.PORTAL.equals(kind)) {
            return base;
        }
        return base + (isBlackHole(stack) ? " §5[Чёрная дыра]" : isMonolith(stack) ? " §b[Монолит]" : " §7[Обычный]");
    }

    public String kind() {
        return kind;
    }

    /** Ключ выделения у каждого вида палочки свой. */
    public String tool() {
        return "region_" + kind;
    }

    private String what() {
        return Region.PORTAL.equals(kind) ? "портал" : "зону-триггер";
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote || !(player instanceof EntityPlayerMP)) {
            return EnumActionResult.SUCCESS;
        }
        final EntityPlayerMP p = (EntityPlayerMP) player;
        if (!Perms.isAdmin(p)) {
            ChatUtil.actionBar(p, "&cЭта палочка только для админов.");
            return EnumActionResult.SUCCESS;
        }
        Selection.Step step = Selection.click(p.getUniqueID(), tool(), p.dimension, pos);
        if (step == Selection.Step.FIRST) {
            ChatUtil.send(p, "&eТочка 1: &f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + " &7— теперь ПКМ по второму углу.");
            return EnumActionResult.SUCCESS;
        }
        final Selection.Sel sel = Selection.get(p.getUniqueID(), tool());
        if (sel == null) {
            return EnumActionResult.SUCCESS;
        }
        ChatUtil.send(p, "&eТочка 2: &f" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " &7— "
                + sel.describe());
        final String kindFinal = kind;
        final String wandStyle = Region.PORTAL.equals(kind) ? style(player.getHeldItem(hand)) : Region.STYLE_NORMAL;
        if (Region.STYLE_MONOLITH.equals(wandStyle)) {
            BlockPos a = sel.min();
            BlockPos b = sel.max();
            int axis = Region.planeAxis(b.getX() - a.getX() + 1, b.getY() - a.getY() + 1, b.getZ() - a.getZ() + 1);
            if (axis < 0) {
                Selection.clear(p.getUniqueID(), tool());
                ChatUtil.send(p, "&cМонолит — плоский: выдели область толщиной в 1 блок по одной оси "
                        + "(например 10×10×1 — стена, 10×1×10 — пол). Выделение сброшено.");
                return EnumActionResult.SUCCESS;
            }
            ChatUtil.actionBar(p, axis == 1 ? "&bМонолит будет лежать как пол" : "&bМонолит-стена");
        }
        boolean portal = Region.PORTAL.equals(kind);
        new com.ecoserv.ui.PromptScreen(p, portal ? "Новый портал" : "Новая зона-триггер",
                "&7Область: &f" + sel.describe() + "&7. Придумай название — по нему портал или зону находят в меню "
                        + "и в командах (буквы, цифры, _ и -, без пробелов).",
                "Название", "", portal ? "портал_в_город" : "вход_в_шахту",
                new ItemStack(portal ? net.minecraft.init.Items.ENDER_EYE : net.minecraft.init.Items.MAP), 48,
                (who, text) -> {
                    Selection.Sel current = Selection.complete(who.getUniqueID(), tool(), who.dimension);
                    if (current == null) {
                        throw new com.ecoserv.ui.UiError("Выделение пропало (другой мир?). Выдели заново.");
                    }
                    Region r = RegionManager.create(text, kindFinal, current.dim, current.min(), current.max(), who.getName());
                    if (r == null) {
                        throw new com.ecoserv.ui.UiError("Название занято или некорректно: " + text);
                    }
                    Selection.clear(who.getUniqueID(), tool());
                    if (!Region.STYLE_NORMAL.equals(wandStyle)) {
                        r.style = wandStyle;
                        RegionManager.changed();
                    }
                    ChatUtil.send(who, ChatUtil.link("&aГотово: " + (r.isPortal() ? "портал" : "зона-триггер") + " &f"
                            + r.name + " &7" + r.describeArea() + " &e[настроить]",
                            "/" + (r.isPortal() ? "portal" : "trigger") + " menu " + r.name, "&eОткрыть настройки"));
                    new com.ecoserv.region.RegionScreen(who, r.name).withToast(
                            (r.isPortal() ? "Портал" : "Зона") + " «" + r.name + "» создан" + (r.isPortal() ? "" : "а")
                                    + (r.isPortal() ? ". Выбери, куда он ведёт." : ". Выбери, что она делает.")).open();
                }, null).open();
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && player.isSneaking()) {
            if (Region.PORTAL.equals(kind)) {
                // Shift+ПКМ в воздух — сменить режим (и сбросить выделение)
                net.minecraft.nbt.NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound()
                        : new net.minecraft.nbt.NBTTagCompound();
                // обычный -> чёрная дыра -> монолит -> обычный
                String now = style(stack);
                String next = Region.STYLE_NORMAL.equals(now) ? Region.STYLE_BLACKHOLE
                        : Region.STYLE_BLACKHOLE.equals(now) ? Region.STYLE_MONOLITH : Region.STYLE_NORMAL;
                tag.setString("style", next);
                stack.setTagCompound(tag);
                Selection.clear(player.getUniqueID(), tool());
                ChatUtil.actionBar(player, Region.STYLE_BLACKHOLE.equals(next)
                        ? "&5Режим: Чёрная дыра &7(вид на место назначения, линза, искажения)"
                        : Region.STYLE_MONOLITH.equals(next)
                        ? "&bРежим: Монолит &7(плоская картинка, выдели толщину в 1 блок)"
                        : "&fРежим: Обычный портал");
                if (player instanceof EntityPlayerMP) {
                    com.ecoserv.init.ModSounds.play((EntityPlayerMP) player, com.ecoserv.init.ModSounds.UI_CLICK, 0.7F,
                            Region.STYLE_NORMAL.equals(next) ? 0.8F : Region.STYLE_BLACKHOLE.equals(next) ? 1.0F : 1.2F);
                }
            } else if (Selection.get(player.getUniqueID(), tool()) != null) {
                Selection.clear(player.getUniqueID(), tool());
                ChatUtil.actionBar(player, "&7Выделение сброшено");
            }
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
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
        tooltip.add("§7ПКМ по блоку — точка 1, ПКМ ещё раз — точка 2,");
        tooltip.add("§7потом название в окне — " + what() + " готов" + (Region.PORTAL.equals(kind) ? "." : "а."));
        tooltip.add(Region.PORTAL.equals(kind) ? "§7ЛКМ по блоку — что здесь." : "§7Shift+ПКМ в воздух — сбросить выбор, ЛКМ по блоку — что здесь.");
        if (Region.PORTAL.equals(kind)) {
            tooltip.add(isBlackHole(stack) ? "§5Режим: Чёрная дыра — сфера с видом на место назначения"
                    : isMonolith(stack) ? "§bРежим: Монолит — плоская картинка (толщина 1 блок)" : "§fРежим: Обычный портал");
            tooltip.add("§7Shift+ПКМ в воздух — сменить режим (обычный → чёрная дыра → монолит).");
            tooltip.add("§dКуда ведёт — во вкладке «Настроить порталы» или /portal menu <имя>.");
        } else {
            tooltip.add("§eПри входе игрока: сообщение, заголовок, звук, команды,");
            tooltip.add("§eсценарий и «Активация» для квестов (/trigger menu <имя>).");
        }
        tooltip.add("§8Только для админов.");
    }
}
