package com.ecoserv.subworld.menu;

import com.ecoserv.init.ModRegistry;
import com.ecoserv.region.Region;
import com.ecoserv.region.RegionScreen;
import com.ecoserv.ui.Ui;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

/** Вкладка «Порталы и зоны»: список с поиском, куда ведёт каждый портал, что делает каждая зона. */
final class PortalsPanel {

    private PortalsPanel() {
    }

    static void build(Ui ui, final WorldsScreen s) {
        final EntityPlayerMP player = s.player();
        ui.search("поиск по названию, миру, цели");
        ui.note(Ui.BLUE, "Портал: возьми палочку порталов, ПКМ по двум углам, затем введи название в чат. "
                + "Зона-триггер — так же палочкой триггера. Поставленные в шаблоне подмира порталы и зоны работают "
                + "во всех локальных мирах команд.");
        Ui.Buttons wands = ui.buttons();
        wands.add("Палочка порталов", Ui.PURPLE, () -> give(player, ModRegistry.PORTAL_WAND));
        wands.add("Палочка триггеров", Ui.BLUE, () -> give(player, ModRegistry.TRIGGER_WAND));

        List<Region> portals = WorldsScreen.regions(Region.PORTAL);
        ui.header("Порталы", Integer.toString(portals.size()));
        if (portals.isEmpty()) {
            ui.text("&8Порталов пока нет.");
        }
        for (final Region r : portals) {
            boolean configured = "portal".equals(r.targetMode) ? !r.targetPortal.isEmpty() : !r.target.isEmpty();
            Ui.Row row = ui.row(new ItemStack(Items.ENDER_EYE), (r.enabled ? "&e" : "&7") + r.name)
                    .line("&7→ " + (configured ? "&f" + r.targetTitle() : "&cникуда не ведёт"))
                    .line("&8мир " + WorldNames.displayName(r.dim) + " · " + r.describeArea());
            if (!r.enabled) {
                row.chip("выключен", Ui.GREY);
            }
            if (!configured) {
                row.chip("не настроен", Ui.YELLOW);
            }
            row.onClick(() -> new RegionScreen(player, r.name).open());
            row.button("Настроить", Ui.BLUE, () -> new RegionScreen(player, r.name).open());
        }

        List<Region> triggers = WorldsScreen.regions(Region.TRIGGER);
        ui.header("Зоны-триггеры", Integer.toString(triggers.size()));
        if (triggers.isEmpty()) {
            ui.text("&8Зон пока нет.");
        }
        for (final Region r : triggers) {
            StringBuilder does = new StringBuilder();
            if (!r.message.isEmpty()) {
                does.append("сообщение, ");
            }
            if (!r.title.isEmpty()) {
                does.append("заголовок, ");
            }
            if (!r.sound.isEmpty()) {
                does.append("звук, ");
            }
            if (!r.commands.isEmpty()) {
                does.append("команд: ").append(r.commands.size()).append(", ");
            }
            if (!r.script.isEmpty()) {
                does.append("сценарий, ");
            }
            if (r.activation) {
                does.append("«Активация», ");
            }
            String d = does.length() > 2 ? does.substring(0, does.length() - 2) : "ничего не делает";
            Ui.Row row = ui.row(new ItemStack(Items.MAP), (r.enabled ? "&e" : "&7") + r.name)
                    .line("&7" + d + " · " + r.modeTitle())
                    .line("&8мир " + WorldNames.displayName(r.dim) + " · " + r.describeArea());
            if (!r.enabled) {
                row.chip("выключена", Ui.GREY);
            }
            row.onClick(() -> new RegionScreen(player, r.name).open());
            row.button("Настроить", Ui.BLUE, () -> new RegionScreen(player, r.name).open());
        }
    }

    private static void give(EntityPlayerMP player, Item item) {
        PlayerUtil.giveOrDrop(player, new ItemStack(item));
    }
}
