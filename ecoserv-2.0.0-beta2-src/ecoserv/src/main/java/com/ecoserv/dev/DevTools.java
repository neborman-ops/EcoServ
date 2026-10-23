package com.ecoserv.dev;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.v2.ModRegistry2;
import com.ecoserv.v2.V2Actions;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

/** Разделы окна /dev: каждая система 2.0 добавляет сюда свою кнопку. */
public final class DevTools {

    public interface Section {
        void build(Ui ui, EntityPlayerMP player, UiScreen from);
    }

    private static final List<Section> SECTIONS = new ArrayList<Section>();

    static {
        add((ui, p, from) -> {
            ui.header("Скрытое и логика");
            ui.row(new ItemStack(ModRegistry2.DEV_GLASSES), "&fОчки разработчика")
                    .line("&7Надень — видно маркеры, зоны, программы, провода (без режима /dev)")
                    .button("Взять", Ui.GREEN, () -> DevScreen.give(p, DevScreen.glasses()));
            ui.row(new ItemStack(ModRegistry2.MARKER, 1, 0), "&fМаркеры-помощники")
                    .line("&716 видов: появление, команды, финиш, чекпоинт, камера, стык, свет, звук, надпись…")
                    .button("Взять все", Ui.GREEN, () -> {
                        for (int i = 0; i < 16; i++) {
                            DevScreen.give(p, new ItemStack(ModRegistry2.MARKER, 1, i));
                        }
                    });
            ui.row(new ItemStack(ModRegistry2.LOGIC), "&fБлок-программа")
                    .line("&7Нажатие, редстоун, игрок рядом, таймер, событие → шаги сценария")
                    .button("Взять", Ui.GREEN, () -> DevScreen.give(p, DevScreen.logic()));
            ui.row(new ItemStack(Items.MAP), "&fЗоны разработчика").line("&7Вход / выход / внутри → шаги; &cглобальные&7, &9локальные")
                    .onClick(() -> new DevZoneScreen(p).open());
            ui.row(new ItemStack(Items.WRITABLE_BOOK), "&fСценарии").line("&7Цепочки шагов, переменные, условия if")
                    .onClick(() -> new ScriptsScreen(p).open());
            ui.row(new ItemStack(Items.CLOCK), "&fСобытия").line("&7PlayerJoin(), смерть, вход в мир/зону, квест, мини-игра → сценарии")
                    .onClick(() -> new EventsScreen(p).open());
        });
    }

    private DevTools() {
    }

    public static void add(Section s) {
        SECTIONS.add(s);
    }

    static void hubSections(Ui ui, EntityPlayerMP p, UiScreen from) {
        for (Section s : SECTIONS) {
            s.build(ui, p, from);
        }
    }

    /** Свободная камера (клиент). */
    public static void freecam(EntityPlayerMP p) {
        V2Actions.send(p, "freecam", new NBTTagCompound());
    }
}
