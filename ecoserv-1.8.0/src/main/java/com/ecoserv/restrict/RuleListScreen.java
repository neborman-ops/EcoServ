package com.ecoserv.restrict;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Все запреты предметов и модов по мирам: список, новый запрет (из руки, из списка предметов, на мод). */
public class RuleListScreen extends UiScreen {

    public RuleListScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP player) {
        if (Perms.isAdmin(player)) {
            new RuleListScreen(player).open();
        }
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    /** Ключ правила для предмета (как у /ecoadmin restrict). */
    static String ruleKey(ItemStack held) {
        if (held == null || held.isEmpty()) {
            return "";
        }
        return ItemKey.of(held, false, !held.isItemStackDamageable()).serialize();
    }

    @Override
    protected void build(Ui ui) {
        ui.title("Запреты предметов").subtitle("по мирам").icon(new ItemStack(Blocks.BARRIER));
        ui.back(() -> com.ecoserv.subworld.menu.WorldsScreen.open(player, com.ecoserv.subworld.menu.WorldsScreen.Tab.ADMIN));
        ui.search("поиск запрета");
        ui.note(Ui.BLUE, "Запрет — что нельзя делать с предметом (крафт, использование, установка, носить с собой) и в "
                + "каких мирах. Целый мод можно выключить в выбранных мирах. Предметы, которые нельзя носить, уходят в "
                + "буфер /stash и возвращаются сами там, где разрешены.");
        ui.header("Новый запрет");
        ui.buttons().add("Предмет из руки", Ui.BLUE, () -> {
            ItemStack held = player.getHeldItemMainhand();
            if (held.isEmpty()) {
                throw new UiError("Возьми предмет в руку.");
            }
            ItemRule existing = ItemRules.findFor(held, ruleKey(held));
            new RuleScreen(player, existing == null ? ruleKey(held) : existing.item, held).open();
        });
        ui.field("Предмет из списка", "Кнопка … — выбрать из всех предметов игры и модов", "", v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Такого предмета нет: " + v);
            }
            ItemKey k = ItemKey.parse(id);
            new RuleScreen(player, k.serialize(), k.toStack(1)).open();
        }).picker(Ui.PICK_ITEM).placeholder("modid:item@meta");
        Ui.Select mod = ui.select("Выключить мод", "Запретить всё из мода — потом выбрать миры", "", v -> {
            if (v.isEmpty()) {
                return;
            }
            String key = ItemRules.modKey(v);
            if (ItemRules.exact(key) == null) {
                ItemRule rule = ItemRules.getOrCreate(key);
                for (RestrictAction a : RestrictAction.values()) {
                    rule.setDenied(a, true);
                }
                ItemRules.changed();
            }
            new RuleScreen(player, key, ItemStack.EMPTY).open();
        });
        mod.option("", "— выбрать мод —");
        List<ModContainer> mods = new ArrayList<ModContainer>(Loader.instance().getActiveModList());
        mods.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (ModContainer m : mods) {
            String id = m.getModId().toLowerCase(Locale.ROOT);
            if (id.equals("minecraft") || id.equals("mcp") || id.equals("fml") || id.equals("forge") || id.equals("ecoserv")) {
                continue;
            }
            mod.option(m.getModId(), m.getName(), m.getModId());
        }

        List<ItemRule> rules = new ArrayList<ItemRule>(ItemRules.all());
        rules.sort((a, b) -> UiUtil.plain(a.title()).compareToIgnoreCase(UiUtil.plain(b.title())));
        ui.header("Запреты", Integer.toString(rules.size()));
        if (rules.isEmpty()) {
            ui.text("&8Запретов пока нет.");
        }
        for (final ItemRule r : rules) {
            ItemStack icon = r.isModRule() ? new ItemStack(Items.BOOK) : r.key().toStack(1);
            if (icon.isEmpty()) {
                icon = new ItemStack(Items.PAPER);
            }
            final ItemStack shown = icon;
            Ui.Row row = ui.row(icon, "&f" + r.title())
                    .line("&7нельзя: " + r.describeDeny())
                    .line("&8" + r.describeWorlds() + (r.note.isEmpty() ? "" : " · " + r.note));
            if (r.isModRule()) {
                row.chip("мод", Ui.PURPLE);
            }
            row.onClick(() -> new RuleScreen(player, r.item, shown).open());
            row.button("Изменить", Ui.BLUE, () -> new RuleScreen(player, r.item, shown).open());
            row.button("✕", Ui.RED, () -> {
                ItemRules.remove(r.item);
                toast("Запрет удалён: " + UiUtil.plain(r.title()));
            }).confirm("Удалить запрет для «" + r.title() + "»?");
        }
    }
}
