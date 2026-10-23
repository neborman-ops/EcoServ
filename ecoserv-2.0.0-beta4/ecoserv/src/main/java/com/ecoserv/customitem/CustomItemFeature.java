package com.ecoserv.customitem;

import com.ecoserv.dev.DevTools;
import com.ecoserv.dev.StepCatalog;
import com.ecoserv.dev.StepCatalog.Def;
import com.ecoserv.dev.StepCatalog.Kind;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/** Подключение своих предметов. */
public final class CustomItemFeature {

    private CustomItemFeature() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new CustomItems.Events());
        StepCatalog.source("citem", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (CustomItem c : CustomItems.all()) {
                out.add(new String[]{c.id, ChatUtil.strip(ChatUtil.color(c.name)), c.id, c.base});
            }
            return out;
        });
        StepCatalog.add(new Def("citem", "Дать свой предмет", "Предмет из конструктора", "minecraft:nether_star", StepCatalog.CATEGORIES[1])
                .pick("id", "Предмет", "citem", "", "")
                .p("count", "Сколько", Kind.INT, "", "1"));
        ScriptSteps.register("citem", (p, a, c) -> {
            String[] x = a.trim().split("\\s+");
            CustomItem ci = CustomItems.get(x[0]);
            if (ci != null) {
                int n = 1;
                try {
                    n = x.length > 1 ? Integer.parseInt(x[1]) : 1;
                } catch (NumberFormatException ignored) {
                    // 1
                }
                while (n > 0) {
                    ItemStack st = CustomItems.stack(ci, n);
                    if (st.isEmpty()) {
                        break;
                    }
                    n -= st.getCount();
                    PlayerUtil.giveOrDrop(p, st);
                }
            }
            return 0;
        }, a -> a.trim().isEmpty() ? "citem <id> [сколько]" : null, "citem hero_sword 1 — дать свой предмет");
        DevTools.add((ui, p, from) -> {
            ui.header("Свой контент");
            ui.row(new ItemStack(Items.NETHER_STAR), "&fСвои предметы").line("&7Имя, описание, свечение, урон, скорость, действие по ПКМ")
                    .onClick(() -> new CustomItemScreen(p, null).open());
            ui.row(new ItemStack(net.minecraft.init.Blocks.ENDER_CHEST), "&fПресеты").line("&7Наборы контента: сохранить, перенести, подключить")
                    .onClick(() -> new com.ecoserv.preset.PresetScreen(p).open());
        });
    }
}
