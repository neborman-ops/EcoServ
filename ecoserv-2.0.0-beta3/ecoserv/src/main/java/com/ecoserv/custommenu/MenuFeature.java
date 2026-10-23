package com.ecoserv.custommenu;

import com.ecoserv.dev.DevTools;
import com.ecoserv.dev.StepCatalog;
import com.ecoserv.dev.StepCatalog.Def;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.v2.Features;
import com.ecoserv.v2.V2Actions;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Подключение своих меню: шаги, ESC, предмет-ключ, раздел /dev. */
public final class MenuFeature {

    private MenuFeature() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MenuFeature.Events());
        Features.JOIN.add(MenuManager::sendEsc);
        V2Actions.on("esc_click", (p, data) -> MenuManager.open(p, data.getString("id")));

        StepCatalog.source("menu", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (CustomMenu m : MenuManager.all()) {
                out.add(new String[]{m.id, UiUtil.plain(m.title), m.id, m.icon});
            }
            return out;
        });
        StepCatalog.add(new Def("menu", "Открыть меню", "Своё меню из конструктора", "minecraft:item_frame", StepCatalog.CATEGORIES[0])
                .pick("menu", "Меню", "menu", "", ""));
        StepCatalog.add(new Def("close", "Закрыть окно", "Закрыть открытое окно мода", "minecraft:oak_door", StepCatalog.CATEGORIES[0]));
        ScriptSteps.register("menu", (p, a, c) -> {
            MenuManager.open(p, a.trim());
            return 0;
        }, a -> a.trim().isEmpty() ? "menu <id>" : null, "menu donate — открыть своё меню");
        ScriptSteps.register("close", (p, a, c) -> {
            com.ecoserv.ui.UiManager.close(p);
            return 0;
        }, a -> null, "close — закрыть окно");

        DevTools.add((ui, p, from) -> {
            ui.header("Интерфейс");
            ui.row(new ItemStack(Items.ITEM_FRAME), "&fСвои меню").line("&7Конструктор окон: кнопки, текст, предметы, кнопки в ESC")
                    .onClick(() -> new MenuEditor(p, null).open());
        });
    }

    public static class Events {
        @SubscribeEvent
        public void onRightClick(PlayerInteractEvent.RightClickItem event) {
            ItemStack st = event.getItemStack();
            if (st.isEmpty() || !st.hasTagCompound() || !st.getTagCompound().hasKey(MenuManager.ITEM_TAG)) {
                return;
            }
            event.setCanceled(true);
            if (!event.getWorld().isRemote && event.getEntityPlayer() instanceof EntityPlayerMP) {
                MenuManager.open((EntityPlayerMP) event.getEntityPlayer(), st.getTagCompound().getString(MenuManager.ITEM_TAG));
            }
        }
    }
}
