package com.ecoserv.subworld.menu;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldAbyss;
import com.ecoserv.subworld.SubworldDims;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;

import java.util.Locale;

/**
 * ГЛИТЧ-бездна подмира (п. 21): высота срабатывания, эффекты, «исчезновение после галочки»,
 * тексты окна ENGINE ERROR на разных языках.
 */
public class AbyssMenu extends SubMenu {

    private final String id;

    public AbyssMenu(EntityPlayerMP player, String id) {
        super(player, "ГЛИТЧ-бездна: " + id, 6);
        this.id = id;
    }

    @Override
    protected ChestMenu reopen(EntityPlayerMP who) {
        return new AbyssMenu(who, id);
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(0, filler());
        fillRow(5, filler());
        Subworld sw = SubworldRegistry.get(id);
        if (sw == null) {
            setButton(45, Icons.back());
            return;
        }
        Subworld.Abyss a = sw.abyss;
        setButton(4, Icons.of(new ItemStack(Blocks.END_PORTAL_FRAME), "&5ГЛИТЧ-бездна",
                "&7Игрок, упавший ниже заданной высоты,", "&7возвращается на спавн мира; экран в глитч-",
                "&7текстурах, фильтр эндермена и окно ENGINE ERROR."));
        setButton(10, toggle(a.enabled, "Включена"));
        setButton(11, Icons.of(Items.FEATHER, "&fВысота срабатывания: &e" + a.y, "&7Ниже этой Y — сработает", "&8ЛКМ — ввести"));
        setButton(12, toggle(a.waitForOk, "Исчезновение после галочки",
                "&7да — эффекты держатся до нажатия ОК", "&7и гаснут через N секунд после него;",
                "&7нет — гаснут сами, не дожидаясь окна"));
        setButton(13, Icons.of(Items.CLOCK, "&fПосле ОК исчезают через: &e" + a.fadeSeconds + " с", "&8ЛКМ — ввести"));
        setButton(14, Icons.of(Items.CLOCK, "&fБез ожидания длятся: &e" + a.effectSeconds + " с",
                "&7Для режима «не ждать ОК»", "&8ЛКМ — ввести"));
        setButton(15, toggle(a.shader, "Фильтр эндермена"));
        setButton(16, toggle(a.overlay, "Глитч-текстуры по краям экрана"));
        setButton(20, Icons.of(Items.BLAZE_POWDER, "&fПроверить на себе", "&8ЛКМ — показать эффекты"));
        setButton(24, Icons.of(Items.WRITABLE_BOOK, "&aДобавить язык", "&7Код языка: ru_ru, en_us, uk_ua, de_de...",
                "&8ЛКМ — ввести"));
        int slot = 27;
        for (Subworld.LangText t : a.texts) {
            if (slot > 44) {
                break;
            }
            setButton(slot++, Icons.of(Items.PAPER, "&e" + t.lang + "&7: &f" + t.title,
                    "&7Текст: &f" + cutText(t.text),
                    "&7Кнопка: &f" + t.button,
                    "&8ЛКМ — заголовок, ПКМ — текст,", "&8Shift+ЛКМ — кнопка, Shift+ПКМ — удалить"));
        }
        setButton(45, Icons.back());
        setButton(53, Icons.close());
    }

    private static String cutText(String s) {
        return s.length() > 40 ? s.substring(0, 39) + "…" : s;
    }

    private void save(Subworld sw) {
        sw.normalize();
        SubworldRegistry.save();
        SubworldDims.sync(sw);
    }

    private interface Edit {
        void apply(Subworld sw, String text);
    }

    private void edit(String question, final Edit edit) {
        final String sid = id;
        prompt(question, new Input() {
            @Override
            public void accept(EntityPlayerMP who, String text) {
                Subworld s = SubworldRegistry.get(sid);
                if (s != null && Perms.isAdmin(who)) {
                    edit.apply(s, text);
                    save(s);
                }
            }
        });
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (!Perms.isAdmin(player)) {
            player.closeScreen();
            return;
        }
        final Subworld sw = SubworldRegistry.get(id);
        if (slot == 45) {
            new SubworldEditMenu(player, id, SubworldEditMenu.Page.LOOK).open();
            return;
        }
        if (slot == 53 || sw == null) {
            player.closeScreen();
            return;
        }
        boolean shift = type == ClickType.QUICK_MOVE;
        boolean right = dragType == 1;
        final Subworld.Abyss a = sw.abyss;
        switch (slot) {
            case 10:
                a.enabled = !a.enabled;
                save(sw);
                return;
            case 11:
                edit("&eВысота срабатывания (например -16 или 0):", (s, t) -> {
                    try {
                        s.abyss.y = Math.max(-512, Math.min(255, Integer.parseInt(t.trim())));
                    } catch (NumberFormatException e) {
                        ChatUtil.send(player, "&cНужно число.");
                    }
                });
                return;
            case 12:
                a.waitForOk = !a.waitForOk;
                save(sw);
                return;
            case 13:
                edit("&eЧерез сколько секунд после ОК исчезают эффекты (0–600):", (s, t) -> {
                    try {
                        s.abyss.fadeSeconds = Integer.parseInt(t.trim());
                    } catch (NumberFormatException e) {
                        ChatUtil.send(player, "&cНужно число.");
                    }
                });
                return;
            case 14:
                edit("&eСколько секунд длятся эффекты без ожидания ОК (1–600):", (s, t) -> {
                    try {
                        s.abyss.effectSeconds = Integer.parseInt(t.trim());
                    } catch (NumberFormatException e) {
                        ChatUtil.send(player, "&cНужно число.");
                    }
                });
                return;
            case 15:
                a.shader = !a.shader;
                save(sw);
                return;
            case 16:
                a.overlay = !a.overlay;
                save(sw);
                return;
            case 20:
                player.closeScreen();
                SubworldAbyss.show(player, sw);
                return;
            case 24:
                edit("&eКод языка (например en_us):", (s, t) -> {
                    String lang = t.trim().toLowerCase(Locale.ROOT);
                    if (!lang.matches("[a-z]{2,3}_[a-z]{2,3}")) {
                        ChatUtil.send(player, "&cКод вида ru_ru.");
                        return;
                    }
                    for (Subworld.LangText x : s.abyss.texts) {
                        if (x.lang.equals(lang)) {
                            ChatUtil.send(player, "&eЭтот язык уже есть.");
                            return;
                        }
                    }
                    Subworld.LangText base = s.abyss.texts.isEmpty() ? new Subworld.LangText() : s.abyss.texts.get(0);
                    s.abyss.texts.add(new Subworld.LangText(lang, base.title, base.text, base.button));
                });
                return;
            default:
        }
        int index = slot - 27;
        if (slot < 27 || slot > 44 || index >= a.texts.size()) {
            return;
        }
        final int i = index;
        if (shift && right) {
            if (a.texts.size() > 1) {
                a.texts.remove(i);
                save(sw);
            } else {
                ChatUtil.send(player, "&eДолжен остаться хотя бы один язык.");
            }
            return;
        }
        if (shift) {
            edit("&eТекст кнопки:", (s, t) -> {
                if (i < s.abyss.texts.size()) {
                    s.abyss.texts.get(i).button = t.trim();
                }
            });
            return;
        }
        if (right) {
            edit("&eТекст ошибки (\\n — новая строка):", (s, t) -> {
                if (i < s.abyss.texts.size()) {
                    s.abyss.texts.get(i).text = t.trim().replace("\\n", "\n");
                }
            });
            return;
        }
        edit("&eЗаголовок окна:", (s, t) -> {
            if (i < s.abyss.texts.size()) {
                s.abyss.texts.get(i).title = t.trim();
            }
        });
    }
}
