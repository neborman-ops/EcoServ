package com.ecoserv.subworld.menu;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.restrict.ItemRule;
import com.ecoserv.restrict.ItemRules;
import com.ecoserv.restrict.RestrictAction;
import com.ecoserv.restrict.RuleEditorMenu;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.util.Perms;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Отключение модов в подмире (п. 7): предметы мода не переносятся в подмир, не ставятся и не крафтятся.
 * Работает через обычные правила запретов «@мод» с миром = подмир.
 */
public class ModsMenu extends SubMenu {

    private static final List<String> SKIP = Arrays.asList("minecraft", "mcp", "fml", "forge", "ecoserv");

    private final String id;
    private final String filter;
    private int page;

    public ModsMenu(EntityPlayerMP player, String id, int page) {
        this(player, id, page, "");
    }

    private ModsMenu(EntityPlayerMP player, String id, int page, String filter) {
        super(player, "Моды в " + id, 6);
        this.id = id;
        this.page = page;
        this.filter = filter == null ? "" : filter;
    }

    @Override
    protected ChestMenu reopen(EntityPlayerMP who) {
        return new ModsMenu(who, id, page, filter);
    }

    private List<ModContainer> mods() {
        List<ModContainer> out = new ArrayList<ModContainer>();
        String f = filter.toLowerCase(Locale.ROOT);
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            String modId = mod.getModId().toLowerCase(Locale.ROOT);
            if (SKIP.contains(modId)) {
                continue;
            }
            if (!f.isEmpty() && !modId.contains(f) && !mod.getName().toLowerCase(Locale.ROOT).contains(f)) {
                continue;
            }
            out.add(mod);
        }
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    /** Действует ли правило мода в этом подмире. */
    static boolean disabledIn(ItemRule rule, String subworld) {
        if (rule == null || !rule.hasAnyDeny()) {
            return false;
        }
        if (rule.worlds.isEmpty()) {
            return !containsName(rule.except, subworld);
        }
        return containsName(rule.worlds, subworld);
    }

    private static boolean containsName(List<String> names, String name) {
        for (String n : names) {
            if (WorldNames.normalize(n).equals(WorldNames.normalize(name))) {
                return true;
            }
        }
        return false;
    }

    private static void removeName(List<String> names, String name) {
        names.removeIf(n -> WorldNames.normalize(n).equals(WorldNames.normalize(name)));
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(0, filler());
        fillRow(5, filler());
        Subworld sw = SubworldRegistry.get(id);
        setButton(4, Icons.of(new ItemStack(Blocks.BARRIER), "&6Отключённые моды: " + (sw == null ? id : sw.name),
                "&7Отключённый мод: его предметы не переносятся", "&7в подмир (ждут в буфере /stash),",
                "&7не ставятся и не крафтятся здесь.",
                "&8ЛКМ по моду — вкл/выкл, Shift+ЛКМ — точная настройка"));
        List<ModContainer> mods = mods();
        page = Math.min(page, pages(mods.size(), PER_PAGE) - 1);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < mods.size(); i++) {
            ModContainer mod = mods.get(page * PER_PAGE + i);
            ItemRule rule = ItemRules.exact(ItemRules.modKey(mod.getModId()));
            boolean off = disabledIn(rule, id);
            ItemStack icon = Icons.of(off ? new ItemStack(Blocks.BARRIER) : new ItemStack(Items.BOOK),
                    (off ? "&c✖ " : "&a") + mod.getName(),
                    "&7id: &f" + mod.getModId(),
                    off ? "&cотключён в этом подмире" : "&aработает",
                    rule != null ? "&7Правило: " + rule.describeWorlds() : "",
                    "&8ЛКМ — " + (off ? "включить" : "отключить") + ", Shift+ЛКМ — настроить");
            setButton(9 + i, icon);
        }
        setButton(46, Icons.of(Items.COMPASS, "&eПоиск" + (filter.isEmpty() ? "" : ": &f" + filter),
                "&8ЛКМ — ввести, Shift+ЛКМ — сбросить"));
        if (page > 0) {
            setButton(48, Icons.arrowLeft("&eНазад"));
        }
        if (page < pages(mods.size(), PER_PAGE) - 1) {
            setButton(50, Icons.arrowRight("&eДальше"));
        }
        setButton(45, Icons.back());
        setButton(53, Icons.close());
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (!Perms.isAdmin(player)) {
            player.closeScreen();
            return;
        }
        boolean shift = type == ClickType.QUICK_MOVE;
        switch (slot) {
            case 45:
                new SubworldEditMenu(player, id, SubworldEditMenu.Page.MAIN).open();
                return;
            case 53:
                player.closeScreen();
                return;
            case 46:
                if (shift) {
                    new ModsMenu(player, id, 0, "").open();
                    return;
                }
                final String sid = id;
                com.ecoserv.menu.ChatPrompt.ask(player, "&eЧасть названия или id мода:", (who, text) ->
                        new ModsMenu(who, sid, 0, text.trim()).open());
                return;
            case 48:
                if (page > 0) {
                    page--;
                }
                return;
            case 50:
                page++;
                return;
            default:
        }
        List<ModContainer> mods = mods();
        int index = page * PER_PAGE + slot - 9;
        if (slot < 9 || slot >= 45 || index < 0 || index >= mods.size()) {
            return;
        }
        String key = ItemRules.modKey(mods.get(index).getModId());
        ItemRule rule = ItemRules.exact(key);
        if (shift) {
            if (rule == null) {
                rule = create(key);
            }
            new RuleEditorMenu(player, key, ItemStack.EMPTY).open();
            return;
        }
        if (disabledIn(rule, id)) {
            if (rule.worlds.isEmpty()) {
                rule.except.add(id);
            } else {
                removeName(rule.worlds, id);
                if (rule.worlds.isEmpty()) {
                    ItemRules.remove(key);
                    return;
                }
            }
        } else if (rule == null) {
            create(key);
            return;
        } else if (rule.worlds.isEmpty()) {
            removeName(rule.except, id);
            if (!rule.hasAnyDeny()) {
                allDenied(rule);
            }
        } else {
            rule.worlds.add(id);
            if (!rule.hasAnyDeny()) {
                allDenied(rule);
            }
        }
        ItemRules.changed();
    }

    private ItemRule create(String key) {
        ItemRule rule = ItemRules.getOrCreate(key);
        allDenied(rule);
        rule.worlds.clear();
        rule.worlds.add(id);
        rule.note = "отключён в подмире " + id;
        ItemRules.changed();
        return rule;
    }

    private static void allDenied(ItemRule rule) {
        for (RestrictAction a : RestrictAction.values()) {
            rule.setDenied(a, true);
        }
    }
}
