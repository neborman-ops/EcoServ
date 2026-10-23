package com.ecoserv.preset;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.Map;

/** Окно пресетов. */
public class PresetScreen extends UiScreen {

    private String name = "";
    private boolean structures = true;

    public PresetScreen(EntityPlayerMP player) {
        super(player);
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        ui.title("Пресеты").icon(new ItemStack(Blocks.ENDER_CHEST));
        ui.text("&7Пресет — набор контента: свои меню, NPC, голограммы, катсцены, свои предметы и структуры. Сохрани,"
                + " перенеси папку &fworld/ecoserv/presets/имя&7 на другой сервер и подключи. При подключении записи"
                + " с тем же id заменяются, остальные остаются.");
        ui.header("Готовые");
        ui.row(new ItemStack(Items.EMERALD), "&fСтартовый набор").line("&7Гид-NPC с диалогом и подарком, голограмма"
                + " приветствия с онлайном, меню «Информация» в ESC — прямо здесь")
                .button("Поставить", Ui.GREEN, () -> {
                    PresetManager.starter(player);
                    toast("Готово: гид и голограмма рядом с тобой.");
                });
        ui.header("Сохранённые", PresetManager.list().size() + "");
        for (final String p : PresetManager.list()) {
            Map<String, Integer> c = PresetManager.contents(p);
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Integer> e : c.entrySet()) {
                String t = PresetManager.FILES.containsKey(e.getKey()) ? PresetManager.FILES.get(e.getKey()) : "Структуры";
                b.append(b.length() > 0 ? ", " : "").append(t).append(": ").append(e.getValue());
            }
            ui.row(new ItemStack(Blocks.CHEST), "&f" + p).line("&7" + (b.length() == 0 ? "пусто" : b))
                    .button("Подключить", Ui.GREEN, () -> toast("Подключено записей: " + PresetManager.apply(p)))
                    .confirm("Подключить «" + p + "»? Записи с теми же id будут заменены.")
                    .button("✕", Ui.RED, () -> PresetManager.delete(p)).confirm("Удалить пресет «" + p + "»?");
        }
        ui.header("Сохранить текущее");
        ui.field("Имя", "", name, v -> name = v.trim()).placeholder("мой_сервер").max(40);
        ui.toggle("Со структурами", "Библиотека стройки", structures, on -> structures = on);
        ui.buttons().add("Сохранить", Ui.GREEN, () -> {
            PresetManager.save(name.isEmpty() ? "пресет_" + (PresetManager.list().size() + 1) : name, structures);
            toast("Сохранено.");
            name = "";
        });
    }
}
