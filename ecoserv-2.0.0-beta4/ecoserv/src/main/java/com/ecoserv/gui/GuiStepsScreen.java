package com.ecoserv.gui;

import com.ecoserv.dev.ConditionCatalog;
import com.ecoserv.dev.ParamForm;
import com.ecoserv.dev.StepsEditor;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Что делает элемент окна: шаги по нажатию, что потом, условие показа. Назад — в дизайнер. */
public class GuiStepsScreen extends UiScreen {

    private final String gui;
    private final int index;
    private final StepsEditor.State steps = new StepsEditor.State();
    private final Map<String, String> cond = new HashMap<String, String>();

    public GuiStepsScreen(EntityPlayerMP player, String gui, int index) {
        super(player);
        this.gui = gui;
        this.index = index;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        if (StepsEditor.modal(ui, player, steps)) {
            return;
        }
        final CustomGui g = GuiManager.get(gui);
        if (g == null || index < 0 || index >= g.elements.size()) {
            ui.title("Окно не найдено");
            ui.buttons().add("Закрыть", Ui.GREY, this::close);
            return;
        }
        final CustomGui.El e = g.elements.get(index);
        ui.title("Действия элемента").subtitle("&7" + g.id + " · " + e.kind + (e.text.isEmpty() ? "" : " «" + e.text + "»"))
                .icon(new ItemStack(Blocks.STONE_BUTTON));
        ui.back(() -> GuiManager.design(player, g.id));
        ui.sideButton("← В дизайнер", new ItemStack(Blocks.CRAFTING_TABLE), Ui.GREEN, () -> GuiManager.design(player, g.id));
        boolean clickable = "button".equals(e.kind) || "item".equals(e.kind) || "image".equals(e.kind);
        if (clickable) {
            StepsEditor.build(ui, steps, "При нажатии", e.steps, GuiManager::saveAll);
            ui.header("Потом");
            Ui.Select then = ui.select("После нажатия", "", e.then, v -> {
                e.then = v;
                GuiManager.saveAll();
            }).option("", "Окно остаётся", "Обновится — увидишь новый баланс и переменные")
                    .option("close", "Закрыть окно", "");
            for (CustomGui x : GuiManager.all()) {
                if (!x.id.equals(g.id)) {
                    then.option("gui:" + x.id, "Открыть окно «" + x.id + "»", "");
                }
            }
        } else {
            ui.note(Ui.BLUE, "Этот элемент не нажимается — шаги есть у кнопок, предметов и картинок.");
        }
        ui.header("Показывать");
        ui.toggle("Только при условии", "Кто не подходит — не увидит этот элемент", !e.condition.isEmpty(), on -> {
            e.condition = on ? "role vip" : "";
            GuiManager.saveAll();
        });
        if (!e.condition.isEmpty()) {
            cond.put("c", e.condition);
            ParamForm.condition(ui, player, "c", cond);
            if (!cond.get("c").equals(e.condition)) {
                e.condition = cond.get("c");
                GuiManager.saveAll();
            }
            ui.note(Ui.BLUE, "Условие: " + ConditionCatalog.describe(e.condition));
        }
        ui.field("Подсказка при наведении", "", e.tooltip, v -> {
            e.tooltip = v;
            GuiManager.saveAll();
        }).clearable().max(200);
    }
}
