package com.ecoserv.subworld.menu;

import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.ui.Ui;
import com.ecoserv.world.WorldBorders;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldSettings;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.common.DimensionManager;

/** Вкладка «Основные миры»: настройки обычных миров — как у подмиров. */
final class MainWorldsPanel {

    private MainWorldsPanel() {
    }

    static ItemStack dimIcon(int dim) {
        if (dim == 0) {
            return new ItemStack(Blocks.GRASS);
        }
        if (dim == -1) {
            return new ItemStack(Blocks.NETHERRACK);
        }
        if (dim == 1) {
            return new ItemStack(Blocks.END_STONE);
        }
        return new ItemStack(Items.ENDER_PEARL);
    }

    static void build(Ui ui, final WorldsScreen s) {
        final EntityPlayerMP player = s.player();
        ui.search("поиск мира");
        ui.note(Ui.BLUE, "Здесь то же, что у подмиров, но для обычных миров: PvP, режим игры, сохранение инвентаря, "
                + "мобы, приветствие, свой барьер, ГЛИТЧ-бездна и доп. правила (без голода, полёт, защита спавна, эффекты).");
        if (!WorldBorders.separate()) {
            ui.note(Ui.YELLOW, "Сейчас барьер общий для всех миров (как в ванили). Свой барьер у каждого мира включается "
                    + "в настройках мода: worlds → separate_borders.");
        }
        for (final Integer dim : WorldNames.allDims()) {
            if (SubworldRegistry.isOurs(dim)) {
                continue;
            }
            WorldSettings.Settings st = WorldSettings.peek(dim);
            WorldServer w = DimensionManager.getWorld(dim);
            Ui.Row row = ui.row(dimIcon(dim), "&f" + WorldNames.nameOf(dim))
                    .line("&8измерение " + dim + (w != null ? " · загружен, игроков: " + w.playerEntities.size() : " · не загружен"));
            StringBuilder sb = new StringBuilder();
            if (st != null) {
                if (WorldSettings.OFF.equals(st.pvp)) {
                    sb.append("без PvP, ");
                }
                if (!st.gamemode.isEmpty()) {
                    sb.append("режим: ").append(WorldParts.gamemodeTitle(st.gamemode)).append(", ");
                }
                if (WorldSettings.ON.equals(st.keepInventory)) {
                    sb.append("вещи сохраняются, ");
                } else if (WorldSettings.OFF.equals(st.keepInventory)) {
                    sb.append("вещи выпадают, ");
                }
                if (!"all".equals(st.mobs)) {
                    sb.append("none".equals(st.mobs) ? "без мобов, " : "без враждебных мобов, ");
                }
                if (st.extras.any()) {
                    sb.append(st.extras.describe()).append(", ");
                }
            }
            if (w != null) {
                WorldBorder b = w.getWorldBorder();
                if (b.getDiameter() < 2.9E7D) {
                    sb.append("барьер ").append((int) (b.getDiameter() / 2)).append(", ");
                }
            }
            if (sb.length() > 2) {
                row.line("&7" + sb.substring(0, sb.length() - 2));
            }
            if (st != null && st.abyss.enabled) {
                row.chip("ГЛИТЧ-бездна", Ui.PURPLE);
            }
            if (st != null && st.customized()) {
                row.chip("настроен", Ui.BLUE);
            }
            if (player.dimension == dim) {
                row.chip("ты здесь", Ui.GREEN);
            }
            row.onClick(() -> new MainWorldScreen(player, dim).open());
            row.button("Настроить", Ui.BLUE, () -> new MainWorldScreen(player, dim).open());
        }
    }
}
