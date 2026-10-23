package com.ecoserv.ui;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Анимированные иконки для окон (1.8.0). Иконка уходит клиенту как обычный предмет, но с меткой
 * {@code ecoAnim} — клиент рисует вместо предмета анимированную ленту
 * {@code assets/ecoserv/textures/icons/<имя>.png} (кадры 16×16 сверху вниз). Если клиент старый
 * или текстура не нашлась, видно обычный предмет-заглушку.
 */
public final class UiIcons {

    public static final String TAG = "ecoAnim";

    private UiIcons() {
    }

    public static ItemStack anim(String name, Item fallback) {
        ItemStack stack = new ItemStack(fallback);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(TAG, name);
        stack.setTagCompound(tag);
        return stack;
    }

    public static ItemStack auction() {
        return anim("anim_auction", net.minecraft.init.Items.DIAMOND_AXE);
    }

    public static ItemStack profile() {
        return anim("anim_profile", net.minecraft.init.Items.NAME_TAG);
    }

    public static ItemStack bank() {
        return anim("anim_bank", net.minecraft.init.Items.GOLD_INGOT);
    }

    public static ItemStack history() {
        return anim("anim_history", net.minecraft.init.Items.WRITABLE_BOOK);
    }

    public static ItemStack players() {
        return anim("anim_players", net.minecraft.init.Items.SKULL);
    }

    public static ItemStack prices() {
        return anim("anim_prices", net.minecraft.init.Items.EMERALD);
    }

    public static ItemStack server() {
        return anim("anim_server", net.minecraft.init.Items.COMPARATOR);
    }

    public static ItemStack money() {
        return anim("anim_money", net.minecraft.init.Items.GOLD_NUGGET);
    }

    public static ItemStack role() {
        return anim("anim_role", net.minecraft.init.Items.NAME_TAG);
    }

    public static ItemStack hourglass() {
        return anim("anim_hourglass", net.minecraft.init.Items.CLOCK);
    }

    public static ItemStack trade() {
        return anim("anim_trade", net.minecraft.init.Items.EMERALD);
    }

    public static ItemStack planet() {
        return anim("anim_planet", net.minecraft.init.Items.ENDER_EYE);
    }

    public static ItemStack team() {
        return anim("anim_team", net.minecraft.init.Items.BANNER);
    }

    public static ItemStack create() {
        return anim("anim_create", net.minecraft.init.Items.NETHER_STAR);
    }

    public static ItemStack compass() {
        return anim("anim_compass", net.minecraft.init.Items.COMPASS);
    }

    public static ItemStack admin() {
        return anim("anim_admin", net.minecraft.init.Items.COMPARATOR);
    }

    public static ItemStack book() {
        return anim("anim_book", net.minecraft.init.Items.BOOK);
    }

    public static ItemStack questBook() {
        return anim("anim_quest_book", net.minecraft.init.Items.ENCHANTED_BOOK);
    }

    public static ItemStack key() {
        return anim("anim_key", net.minecraft.init.Items.GOLD_INGOT);
    }

    public static ItemStack map() {
        return anim("anim_map", net.minecraft.init.Items.FILLED_MAP);
    }

    public static ItemStack feather() {
        return anim("anim_feather", net.minecraft.init.Items.FEATHER);
    }

    public static ItemStack sky() {
        return anim("anim_sky", net.minecraft.init.Items.FEATHER);
    }

    public static ItemStack glitch() {
        return anim("anim_glitch", net.minecraft.init.Items.ENDER_EYE);
    }

    public static ItemStack gear() {
        return anim("anim_gear", net.minecraft.init.Items.COMPARATOR);
    }

    public static ItemStack cake() {
        return anim("anim_cake", net.minecraft.init.Items.PAPER);
    }

    public static ItemStack portal() {
        return anim("anim_portal", net.minecraft.init.Items.ENDER_PEARL);
    }

    public static ItemStack expand() {
        return anim("anim_expand", net.minecraft.init.Items.MAP);
    }

    public static ItemStack race() {
        return anim("anim_race", net.minecraft.init.Items.FEATHER);
    }

    public static ItemStack orders() {
        return anim("anim_orders", net.minecraft.init.Items.PAPER);
    }

    public static ItemStack market() {
        return anim("anim_market", net.minecraft.init.Items.EMERALD);
    }

    public static ItemStack vending() {
        return anim("anim_vending", net.minecraft.init.Items.CHEST_MINECART);
    }

    public static ItemStack backpack() {
        return anim("anim_backpack", net.minecraft.init.Items.LEATHER_BOOTS);
    }

    public static ItemStack ban() {
        return anim("anim_ban", net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.BARRIER));
    }

    public static ItemStack bomb() {
        return anim("anim_bomb", net.minecraft.init.Items.FIRE_CHARGE);
    }

    public static ItemStack pickaxe() {
        return anim("anim_pickaxe", net.minecraft.init.Items.IRON_PICKAXE);
    }

    public static ItemStack wand() {
        return anim("anim_wand", net.minecraft.init.Items.STICK);
    }

    public static ItemStack cube() {
        return anim("anim_cube", net.minecraft.init.Items.COMPARATOR);
    }

    public static ItemStack anvil() {
        return anim("anim_anvil", net.minecraft.init.Items.DIAMOND_AXE);
    }

    public static ItemStack disk() {
        return anim("anim_disk", net.minecraft.init.Items.MAP);
    }

    public static ItemStack power() {
        return anim("anim_power", net.minecraft.init.Items.REPEATER);
    }

    public static ItemStack megaphone() {
        return anim("anim_megaphone", net.minecraft.init.Items.PAPER);
    }

    public static ItemStack terminal() {
        return anim("anim_terminal", net.minecraft.init.Items.REDSTONE);
    }

    public static ItemStack reload() {
        return anim("anim_reload", net.minecraft.init.Items.COMPASS);
    }

    public static ItemStack save() {
        return anim("anim_save", net.minecraft.init.Items.WRITABLE_BOOK);
    }
}
