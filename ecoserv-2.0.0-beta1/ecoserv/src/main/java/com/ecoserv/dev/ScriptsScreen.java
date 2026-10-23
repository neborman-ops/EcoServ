package com.ecoserv.dev;

import com.ecoserv.script.Script;
import com.ecoserv.script.ScriptManager;
import com.ecoserv.script.ScriptRunner;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/** Сценарии в окне (2.0): список, создание, редактор шагов, запуск на себе. */
public class ScriptsScreen extends UiScreen {

    @Nullable
    private String editing;
    private final StepsEditor.State steps = new StepsEditor.State();

    public ScriptsScreen(EntityPlayerMP player) {
        super(player);
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        Script s = editing == null ? null : ScriptManager.get(editing);
        if (s != null) {
            edit(ui, s);
            return;
        }
        editing = null;
        ui.title("Сценарии").icon(new ItemStack(Items.WRITABLE_BOOK));
        ui.back(() -> new DevScreen(player).open());
        ui.text("&7Сценарий — цепочка шагов. Запускается зонами, кнопками, NPC, меню, квестами, событиями и командой "
                + "&f/script run <имя> <ник>&7.");
        ui.field("Новый сценарий", "Имя — Enter", "", v -> {
            Script n = ScriptManager.create(v);
            if (n == null) {
                throw new UiError("Имя занято или неверное.");
            }
            editing = n.name;
        }).placeholder("приветствие").max(48);
        ui.header("Сценарии", ScriptManager.all().size() + "");
        ui.search("поиск");
        for (final Script sc : ScriptManager.all()) {
            ui.row(new ItemStack(Items.PAPER), "&f" + sc.name).line("&7" + (sc.description.isEmpty() ? sc.steps.size() + " шаг." : sc.description))
                    .onClick(() -> editing = sc.name);
        }
    }

    private void edit(Ui ui, final Script s) {
        ui.back(() -> editing = null);
        ui.title("Сценарий " + s.name).icon(new ItemStack(Items.WRITABLE_BOOK));
        ui.sideButton("Запустить на себе", new ItemStack(Items.ENDER_EYE), Ui.GREEN, () -> {
            ScriptRunner.start(player, s.name, "test");
            toast("Запущено.");
        });
        ui.sideButton("Удалить", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            ScriptManager.delete(s.name);
            editing = null;
        }).confirm("Удалить сценарий «" + s.name + "»?");
        ui.field("Описание", "", s.description, v -> {
            s.description = v.trim();
            ScriptManager.save();
        }).clearable().max(200);
        ui.toggle("Можно запускать повторно, пока идёт", "", s.allowParallel, on -> {
            s.allowParallel = on;
            ScriptManager.save();
        });
        StepsEditor.build(ui, steps, "Шаги", s.steps, ScriptManager::save);
    }
}
