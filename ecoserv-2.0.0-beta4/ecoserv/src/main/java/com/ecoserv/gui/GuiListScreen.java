package com.ecoserv.gui;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.Map;

/** Список своих окон: создать (пустое или из шаблона), открыть дизайнер, проверить, удалить. */
public class GuiListScreen extends UiScreen {

    private String template = "Пустое";

    public GuiListScreen(EntityPlayerMP player) {
        super(player);
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        ui.title("Окна (GUI)").icon(new ItemStack(Items.PAINTING));
        ui.text("&7Свои окна рисуются в визуальном дизайнере: перетаскиваешь кнопки, текст, предметы и картинки прямо на"
                + " окно, тянешь за уголки, цвета — палитрой. Открыть окно игроку: шаг &fgui <id>&7, команда &f/gui open <id>"
                + " <ник|@a>&7, кнопка 3D-панели, NPC или меню ESC.");
        ui.header("Новое окно");
        final Map<String, CustomGui> templates = GuiTemplates.all();
        Ui.Select sel = ui.select("Шаблон", "С чего начать", template, v -> template = v);
        for (String k : templates.keySet()) {
            sel.option(k, k);
        }
        ui.field("Создать", "id окна — Enter", "", v -> {
            CustomGui g = GuiManager.create(v, templates.get(template));
            GuiManager.design(player, g.id);
        }).placeholder("shop").max(32);
        ui.header("Окна", GuiManager.all().size() + "");
        for (final CustomGui g : GuiManager.all()) {
            Ui.Row row = ui.row(new ItemStack(Items.PAINTING), "&f" + g.id + (g.title.isEmpty() ? "" : " &7— " + g.title))
                    .line("&7" + g.width + "×" + g.height + " · элементов: " + g.elements.size())
                    .onClick(() -> GuiManager.design(player, g.id));
            row.button("✎ Дизайнер", Ui.GREEN, () -> GuiManager.design(player, g.id));
            row.button("▶", Ui.BLUE, () -> GuiManager.open(player, g.id)).tip("Открыть себе — проверить");
        }
        if (GuiManager.all().isEmpty()) {
            ui.note(Ui.BLUE, "Окон пока нет — выбери шаблон и введи id.");
        }
        ui.header("Удалить");
        for (final CustomGui g : GuiManager.all()) {
            ui.buttons().add("✕ " + g.id, Ui.RED, () -> {
                if (GuiManager.get(g.id) == null) {
                    throw new UiError("Уже удалено.");
                }
                GuiManager.delete(g);
            }).confirm("Удалить окно «" + g.id + "»?");
        }
    }
}
