package com.ecoserv.restrict;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Редактор частичного запрета для одного предмета (/ecoadmin restrict с предметом в руке).
 * Открывается как обычный сундук, клики обрабатывает сервер.
 */
public class RuleEditorMenu extends ChestMenu {

    private static final int SLOT_ITEM = 4;
    private static final int[] ACTION_SLOTS = {10, 12, 14, 16};
    private static final int SLOT_MODE = 27;
    private static final int WORLD_FIRST = 28;
    private static final int WORLD_LAST = 44;
    private static final int SLOT_DELETE = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_HELP = 53;

    private final ItemRule rule;
    private final ItemStack display;
    /** Варианты миров для кнопок: обычные измерения и подмиры (по имени). */
    private final List<String> worldButtons = new ArrayList<String>();
    /** 0 — везде, 1 — только в выбранных, 2 — везде кроме выбранных. */
    private int mode;
    private final List<String> selected = new ArrayList<String>();
    private boolean deleted;

    public RuleEditorMenu(EntityPlayerMP player, String key, ItemStack display) {
        super(player, "Запрет: " + shortName(key, display), 6);
        this.rule = ItemRules.getOrCreate(key);
        if (rule.isModRule()) {
            this.display = Icons.of(Items.BOOK, "&e" + rule.title());
        } else {
            this.display = display.isEmpty() ? rule.key().toStack(1) : display.copy();
        }
        this.mode = rule.worldMode();
        this.selected.addAll(rule.selectedWorlds());
        List<String> choices = WorldNames.worldChoices();
        for (int i = 0; i < choices.size() && i <= WORLD_LAST - WORLD_FIRST; i++) {
            worldButtons.add(choices.get(i));
        }
        // выбранные миры, которых нет среди кнопок (например, мир выгружен), тоже показываем
        for (String name : selected) {
            if (worldButtons.size() > WORLD_LAST - WORLD_FIRST) {
                break;
            }
            if (!containsWorld(worldButtons, name)) {
                worldButtons.add(WorldNames.normalize(name));
            }
        }
    }

    private static String shortName(String key, ItemStack stack) {
        String name = key != null && key.startsWith("@") ? "мод " + key.substring(1)
                : stack.isEmpty() ? "?" : stack.getDisplayName();
        return name.length() > 22 ? name.substring(0, 21) + "…" : name;
    }

    /** Одно и то же ли это место: имена равны или указывают на одно измерение. */
    private static boolean sameWorld(String a, String b) {
        if (WorldNames.normalize(a).equals(WorldNames.normalize(b))) {
            return true;
        }
        Integer da = WorldNames.resolve(a);
        Integer db = WorldNames.resolve(b);
        return da != null && da.equals(db);
    }

    private static boolean containsWorld(List<String> list, String name) {
        for (String s : list) {
            if (sameWorld(s, name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, Icons.filler());
        }

        ItemStack icon = display.isEmpty() ? new ItemStack(Items.PAPER) : display.copy();
        icon.setCount(1);
        setButton(SLOT_ITEM, Icons.build(icon, "&e" + rule.title(),
                "&7Ключ: &f" + rule.item,
                "&7Запрещено: &f" + rule.describeDeny(),
                "&7Где: &f" + rule.describeWorlds()));

        RestrictAction[] actions = RestrictAction.values();
        for (int i = 0; i < actions.length && i < ACTION_SLOTS.length; i++) {
            RestrictAction action = actions[i];
            boolean denied = rule.denies(action);
            String state = denied ? "&cзапрещено" : "&aразрешено";
            setButton(ACTION_SLOTS[i], Icons.of(actionIcon(action), "&f" + action.title + ": " + state,
                    actionHint(action), "", "&eКлик — переключить"));
            setButton(ACTION_SLOTS[i] + 9, denied
                    ? Icons.red("&c" + action.title + ": запрещено", "&eКлик — разрешить")
                    : Icons.green("&a" + action.title + ": разрешено", "&eКлик — запретить"));
        }

        String modeName = mode == 1 ? "&eтолько в выбранных мирах" : mode == 2 ? "&eвезде, кроме выбранных" : "&aво всех мирах";
        setButton(SLOT_MODE, Icons.of(Items.COMPASS, "&fГде действует: " + modeName,
                "&7Клик — сменить режим:",
                "&f во всех мирах → только в выбранных → везде кроме выбранных",
                mode == 1 && selected.isEmpty() ? "&cНи один мир не выбран — правило пока действует везде" : ""));

        for (int i = 0; i < worldButtons.size(); i++) {
            String name = worldButtons.get(i);
            boolean on = containsWorld(selected, name);
            Integer dim = WorldNames.resolve(name);
            ItemStack stack = Icons.of(worldIcon(name),
                    (on ? "&a✔ " : "&7") + name,
                    dim != null ? "&7измерение " + dim : WorldNames.isGroup(name) ? "&7подмир (все его миры)" : "&8сейчас не загружен",
                    mode == 0 ? "&8Сначала выбери режим «только в» или «кроме»" : on ? "&eКлик — убрать" : "&eКлик — выбрать");
            if (on) {
                glint(stack);
            }
            setButton(WORLD_FIRST + i, stack);
        }

        setButton(SLOT_DELETE, Icons.of(Item.getItemFromBlock(Blocks.TNT), "&cУдалить правило",
                "&7Предмет снова будет разрешён везде"));
        setButton(SLOT_CLOSE, Icons.of(Item.getItemFromBlock(Blocks.BARRIER), "&fГотово",
                "&7Все изменения уже сохранены"));
        setButton(SLOT_HELP, Icons.of(Items.BOOK, "&6Как это работает",
                "&fКрафт&7 — результат не появляется в верстаке",
                "&fИспользование&7 — ПКМ, удар, копание этим предметом",
                "&fУстановка&7 — нельзя поставить блок",
                "&fНосить с собой&7 — в этом мире предмет уходит в буфер",
                "&7  и возвращается сам там, где разрешён (/stash)",
                rule.isModRule() ? "&6Правило на весь мод: использование" : "",
                rule.isModRule() ? "&6запрещает и открывать его блоки" : ""));
    }

    private void toggleWorld(String name) {
        boolean removed = false;
        for (int i = selected.size() - 1; i >= 0; i--) {
            if (sameWorld(selected.get(i), name)) {
                selected.remove(i);
                removed = true;
            }
        }
        if (!removed) {
            selected.add(WorldNames.normalize(name));
        }
    }

    /** Записать режим и выбранные миры в правило. */
    private void applyWorlds() {
        rule.worlds.clear();
        rule.except.clear();
        if (mode == 1) {
            rule.worlds.addAll(selected);
        } else if (mode == 2) {
            rule.except.addAll(selected);
        }
        ItemRules.changed();
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        for (int i = 0; i < ACTION_SLOTS.length; i++) {
            if (slot == ACTION_SLOTS[i] || slot == ACTION_SLOTS[i] + 9) {
                RestrictAction action = RestrictAction.values()[i];
                rule.setDenied(action, !rule.denies(action));
                ItemRules.changed();
                return;
            }
        }
        if (slot == SLOT_MODE) {
            mode = (mode + 1) % 3;
            if (mode == 0) {
                selected.clear();
            } else if (selected.isEmpty()) {
                // удобная отправная точка — мир, где стоит админ
                selected.add(WorldNames.displayName(player.dimension));
            }
            applyWorlds();
            return;
        }
        if (slot >= WORLD_FIRST && slot <= WORLD_LAST) {
            int index = slot - WORLD_FIRST;
            if (index < worldButtons.size() && mode != 0) {
                toggleWorld(worldButtons.get(index));
                applyWorlds();
            }
            return;
        }
        if (slot == SLOT_DELETE) {
            ItemRules.remove(rule.item);
            deleted = true;
            player.closeScreen();
            ChatUtil.send(player, "&aПравило для &f" + rule.title() + " &aудалено.");
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeScreen();
        }
    }

    @Override
    protected void onClosed() {
        if (deleted) {
            return;
        }
        if (!rule.hasAnyDeny()) {
            // пустое правило ничего не делает — не засоряем файл
            ItemRules.remove(rule.item);
            ChatUtil.send(player, "&7Ничего не запрещено — правило для &f" + rule.title() + " &7не сохранено.");
            return;
        }
        ChatUtil.send(player, "&aСохранено: &f" + rule.title() + " &7— запрещено: &f" + rule.describeDeny()
                + "&7, " + rule.describeWorlds());
    }

    private static Item actionIcon(RestrictAction action) {
        switch (action) {
            case CRAFT:
                return Item.getItemFromBlock(Blocks.CRAFTING_TABLE);
            case USE:
                return Items.FLINT_AND_STEEL;
            case PLACE:
                return Item.getItemFromBlock(Blocks.GRASS);
            default:
                return Item.getItemFromBlock(Blocks.CHEST);
        }
    }

    private static String actionHint(RestrictAction action) {
        switch (action) {
            case CRAFT:
                return "&7Нельзя скрафтить этот предмет";
            case USE:
                return "&7Нельзя применять: ПКМ, удар, копание";
            case PLACE:
                return "&7Нельзя поставить как блок";
            default:
                return "&7Нельзя держать в инвентаре — уйдёт в буфер";
        }
    }

    private static Item worldIcon(String name) {
        Integer resolved = WorldNames.resolve(name);
        if (resolved == null) {
            return WorldNames.isGroup(name) ? Items.ENDER_EYE : Items.PAPER;
        }
        int dim = resolved;
        if (dim == 0) {
            return Item.getItemFromBlock(Blocks.GRASS);
        }
        if (dim == -1) {
            return Item.getItemFromBlock(Blocks.NETHERRACK);
        }
        if (dim == 1) {
            return Item.getItemFromBlock(Blocks.END_STONE);
        }
        return Items.ENDER_PEARL;
    }

    private static void glint(ItemStack stack) {
        stack.addEnchantment(Enchantments.INFINITY, 1);
        if (stack.getTagCompound() != null) {
            stack.getTagCompound().setInteger("HideFlags", 1);
        }
    }
}
