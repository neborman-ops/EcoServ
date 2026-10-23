package com.ecoserv.restrict;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Запрет для одного предмета или целого мода: что нельзя (крафт, использование, установка, носить) и где
 * (во всех мирах, только в выбранных, везде кроме выбранных).
 */
public class RuleScreen extends UiScreen {

    private final String key;
    private final ItemStack display;
    private boolean deleted;

    public RuleScreen(EntityPlayerMP player, String key, ItemStack display) {
        super(player);
        ItemRule rule = ItemRules.getOrCreate(key);
        this.key = rule.item;
        this.display = rule.isModRule() ? new ItemStack(Items.BOOK)
                : display.isEmpty() ? rule.key().toStack(1) : display.copy();
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    private static boolean sameWorld(String a, String b) {
        if (WorldNames.normalize(a).equals(WorldNames.normalize(b))) {
            return true;
        }
        Integer da = WorldNames.resolve(a);
        Integer db = WorldNames.resolve(b);
        return da != null && da.equals(db);
    }

    private static boolean contains(List<String> list, String name) {
        for (String s : list) {
            if (sameWorld(s, name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void build(Ui ui) {
        ui.back(() -> new RuleListScreen(player).open());
        final ItemRule rule = ItemRules.exact(key);
        if (rule == null) {
            ui.title("Запрет удалён");
            ui.note(Ui.GREY, "Этого правила больше нет.");
            return;
        }
        ItemStack icon = display.isEmpty() ? new ItemStack(Items.PAPER) : display.copy();
        icon.setCount(1);
        ui.title("Запрет: " + UiUtil.plain(rule.title())).subtitle(rule.item).icon(icon);
        ui.sideButton("Удалить запрет", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            ItemRules.remove(rule.item);
            deleted = true;
            new RuleListScreen(player).withToast("Запрет для «" + UiUtil.plain(rule.title()) + "» удалён.").open();
        }).confirm("Удалить запрет — предмет снова будет разрешён везде?");
        ui.row(icon, "&e" + rule.title())
                .line("&7Запрещено: &f" + rule.describeDeny())
                .line("&7Где: &f" + rule.describeWorlds())
                .highlight(true);
        if (rule.isModRule()) {
            ui.note(Ui.BLUE, "Правило на весь мод: «использование» запрещает и открывать его блоки.");
        }
        ui.header("Что нельзя");
        for (final RestrictAction a : RestrictAction.values()) {
            ui.toggle(a.title, hint(a), rule.denies(a), on -> {
                rule.setDenied(a, on);
                ItemRules.changed();
            });
        }
        ui.header("Где действует");
        final int mode = rule.worldMode();
        ui.select("Режим", "", Integer.toString(mode), v -> {
            int m = "1".equals(v) ? 1 : "2".equals(v) ? 2 : 0;
            List<String> selected = new ArrayList<String>(rule.selectedWorlds());
            if (m != 0 && selected.isEmpty()) {
                selected.add(WorldNames.displayName(player.dimension));
            }
            apply(rule, m, m == 0 ? new ArrayList<String>() : selected);
        }).option("0", "Во всех мирах", "", new ItemStack(Blocks.GRASS))
                .option("1", "Только в выбранных", "отметь миры ниже", new ItemStack(Items.COMPASS))
                .option("2", "Везде, кроме выбранных", "отметь миры ниже", new ItemStack(Blocks.BARRIER));
        if (mode != 0) {
            final List<String> selected = new ArrayList<String>(rule.selectedWorlds());
            List<String> choices = new ArrayList<String>(WorldNames.worldChoices());
            for (String s : selected) {
                if (!contains(choices, s)) {
                    choices.add(WorldNames.normalize(s));
                }
            }
            for (final String w : choices) {
                Integer dim = WorldNames.resolve(w);
                boolean on = contains(selected, w);
                ui.toggle("Мир " + w, dim != null ? "&8измерение " + dim : WorldNames.isGroup(w) ? "&8подмир (все его миры)"
                        : "&8сейчас не загружен", on, enable -> {
                    List<String> list = new ArrayList<String>(rule.selectedWorlds());
                    list.removeIf(x -> sameWorld(x, w));
                    if (enable) {
                        list.add(WorldNames.normalize(w));
                    }
                    apply(rule, mode, list);
                });
            }
            if (mode == 1 && selected.isEmpty()) {
                ui.note(Ui.YELLOW, "Ни один мир не выбран — правило пока действует везде.");
            }
        }
        ui.header("Как это работает");
        ui.text("&fКрафт&7 — результат не появляется в верстаке. &fИспользование&7 — ПКМ, удар, копание этим предметом. "
                + "&fУстановка&7 — нельзя поставить блок. &fНосить&7 — в этом мире предмет уходит в буфер (/stash) и "
                + "возвращается сам там, где разрешён.");
    }

    private static void apply(ItemRule rule, int mode, List<String> selected) {
        rule.worlds.clear();
        rule.except.clear();
        if (mode == 1) {
            rule.worlds.addAll(selected);
        } else if (mode == 2) {
            rule.except.addAll(selected);
        }
        ItemRules.changed();
    }

    private static String hint(RestrictAction action) {
        switch (action) {
            case CRAFT:
                return "Нельзя скрафтить этот предмет";
            case USE:
                return "Нельзя применять: ПКМ, удар, копание";
            case PLACE:
                return "Нельзя поставить как блок";
            default:
                return "Нельзя держать в инвентаре — уйдёт в буфер";
        }
    }

    @Override
    protected void onClosed() {
        if (deleted) {
            return;
        }
        ItemRule rule = ItemRules.exact(key);
        if (rule != null && !rule.hasAnyDeny()) {
            // пустое правило ничего не делает — не засоряем файл
            ItemRules.remove(rule.item);
            ChatUtil.send(player, "&7Ничего не запрещено — правило для &f" + rule.title() + " &7не сохранено.");
        }
    }
}
