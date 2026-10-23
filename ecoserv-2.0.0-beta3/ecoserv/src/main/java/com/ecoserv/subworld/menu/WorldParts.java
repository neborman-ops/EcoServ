package com.ecoserv.subworld.menu;

import com.ecoserv.restrict.ItemRule;
import com.ecoserv.restrict.ItemRules;
import com.ecoserv.restrict.RestrictAction;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.world.WorldNames;
import net.minecraft.init.Blocks;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldRules;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.world.WorldExtras;
import com.ecoserv.world.WorldRulesEvents;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Общие части окон настроек мира: доп. правила, ГЛИТЧ-бездна, режим игры. */
final class WorldParts {

    private WorldParts() {
    }

    static void gamemodeSelect(Ui ui, String label, String desc, String value, Ui.Text action) {
        ui.select(label, desc, value == null ? "" : value, action)
                .option("", "Не менять", "у каждого свой режим", new ItemStack(Items.MAP))
                .option("survival", "Выживание", "", new ItemStack(Items.IRON_SWORD))
                .option("adventure", "Приключение", "нельзя ломать блоки без нужного инструмента", new ItemStack(Items.LEATHER_BOOTS))
                .option("creative", "Творческий", "", new ItemStack(Items.NETHER_STAR))
                .option("spectator", "Наблюдатель", "летает сквозь блоки", new ItemStack(Items.ENDER_EYE));
    }

    static String gamemodeTitle(String mode) {
        return SubworldRules.gamemodeTitle(mode);
    }

    // --- доп. правила -------------------------------------------------------------------------------

    static void extras(Ui ui, final WorldExtras x, final Runnable changed) {
        ui.toggle("Без голода", "Сытость не убывает", x.noHunger, on -> {
            x.noHunger = on;
            changed.run();
        });
        ui.toggle("Полёт для всех", "Можно летать, как в творческом. При выходе из мира полёт пропадает", x.allowFly, on -> {
            x.allowFly = on;
            changed.run();
        });
        ui.toggle("Без урона от падения", "", x.noFallDamage, on -> {
            x.noFallDamage = on;
            changed.run();
        });
        ui.toggle("Без урона вообще", "Мобы, огонь, падение, утопление… Бездна и /kill работают", x.noDamage, on -> {
            x.noDamage = on;
            changed.run();
        });
        ui.toggle("Строят только админы", "Игроки не могут ломать и ставить блоки во всём мире", x.buildLocked, on -> {
            x.buildLocked = on;
            changed.run();
        });
        ui.number("Защита спавна", "Радиус вокруг точки появления, где строят только админы (0 — нет)", x.protectRadius,
                0, 10000, v -> {
                    x.protectRadius = (int) v;
                    changed.run();
                });
        ui.toggle("Нельзя выбрасывать предметы", "Клавиша Q не работает (админам можно)", x.noItemDrop, on -> {
            x.noItemDrop = on;
            changed.run();
        });
        ui.header("Постоянные эффекты", x.effects.isEmpty() ? "&8нет" : Integer.toString(x.effects.size()));
        for (int i = 0; i < x.effects.size(); i++) {
            final int index = i;
            String e = x.effects.get(i);
            PotionEffect effect = WorldRulesEvents.effect(e);
            String[] parts = split(e);
            int level = parts[1].isEmpty() ? 0 : parse(parts[1]);
            Ui.Row row = ui.row(potionIcon(effect), effect == null ? "&c" + e + " &7(нет такого эффекта)"
                    : "{t:" + effect.getEffectName() + "} &7" + roman(level + 1))
                    .line("&8" + parts[0]);
            row.button("−", Ui.GREY, () -> {
                setLevel(x, index, Math.max(0, level - 1));
                changed.run();
            }).disabled(level <= 0).tip("Уровень ниже");
            row.button("+", Ui.GREY, () -> {
                setLevel(x, index, Math.min(9, level + 1));
                changed.run();
            }).disabled(level >= 9).tip("Уровень выше");
            row.button("✕", Ui.RED, () -> {
                if (index < x.effects.size()) {
                    x.effects.remove(index);
                    changed.run();
                }
            }).tip("Убрать эффект");
        }
        Ui.Select add = ui.select("Добавить эффект", "Действует на всех игроков в мире, пока они там", "", v -> {
            if (v.isEmpty()) {
                return;
            }
            for (String e : x.effects) {
                if (split(e)[0].equals(v)) {
                    throw new UiError("Этот эффект уже есть — поменяй ему уровень кнопками + и −.");
                }
            }
            x.effects.add(v);
            changed.run();
        });
        add.option("", "— выбрать —");
        List<ResourceLocation> keys = new ArrayList<ResourceLocation>(Potion.REGISTRY.getKeys());
        keys.sort((a, b) -> a.toString().compareTo(b.toString()));
        for (ResourceLocation rl : keys) {
            Potion p = Potion.REGISTRY.getObject(rl);
            if (p == null || p.isInstant()) {
                continue;
            }
            add.option(rl.toString(), "{t:" + p.getName() + "}", rl.toString(), potionIcon(new PotionEffect(p, 20)));
        }
    }

    private static String[] split(String e) {
        String t = e == null ? "" : e.trim().toLowerCase(Locale.ROOT);
        int last = t.lastIndexOf(':');
        if (last > 0) {
            String tail = t.substring(last + 1);
            if (tail.matches("\\d+")) {
                return new String[]{t.substring(0, last), tail};
            }
        }
        return new String[]{t, ""};
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void setLevel(WorldExtras x, int index, int level) {
        if (index < 0 || index >= x.effects.size()) {
            return;
        }
        String id = split(x.effects.get(index))[0];
        x.effects.set(index, level <= 0 ? id : id + ":" + level);
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
    }

    private static ItemStack potionIcon(PotionEffect effect) {
        ItemStack stack = new ItemStack(Items.POTIONITEM);
        PotionUtils.addPotionToItemStack(stack, PotionTypes.WATER);
        if (effect != null) {
            List<PotionEffect> list = new ArrayList<PotionEffect>();
            list.add(effect);
            PotionUtils.appendEffects(stack, list);
        }
        return stack;
    }

    // --- ГЛИТЧ-бездна --------------------------------------------------------------------------------

    static void abyss(Ui ui, final Subworld.Abyss a, final Runnable changed, final Runnable test) {
        ui.note(Ui.PURPLE, "Игрок, упавший ниже заданной высоты, возвращается на точку появления мира. У него — "
                + "глитч на экране и окно ENGINE ERROR, а у всех рядом — воронка из частиц, «битые» пиксели и "
                + "искажение пространства, как у чёрной дыры.");
        ui.toggle("ГЛИТЧ-бездна включена", "", a.enabled, on -> {
            a.enabled = on;
            changed.run();
        });
        ui.number("Высота срабатывания", "Ниже этой Y — сработает (например -16 или 0)", a.y, -512, 255, v -> {
            a.y = (int) v;
            changed.run();
        });
        ui.header("Что видят другие");
        ui.toggle("Частицы и «битые» пиксели", "Воронка там, где игрок упал, и вспышка там, где появился", a.particles, on -> {
            a.particles = on;
            changed.run();
        });
        ui.toggle("Искажение пространства", "Линза «чёрной дыры» вокруг точки падения (нужны шейдеры OpenGL; "
                + "если видеокарта не умеет — останутся частицы)", a.distort, on -> {
            a.distort = on;
            changed.run();
        });
        ui.header("Что видит упавший");
        ui.toggle("Глитч на весь экран", "Срывы строк, сдвиг цветов, «битые» блоки (вместо старого фильтра эндермена)", a.shader, on -> {
            a.shader = on;
            changed.run();
        });
        ui.toggle("Глитч-текстуры по краям экрана", "Картинку можно заменить ресурспаком: "
                + "assets/ecoserv/textures/gui/glitch_overlay.png", a.overlay, on -> {
            a.overlay = on;
            changed.run();
        });
        ui.toggle("Исчезновение после галочки", "Да — эффекты держатся, пока игрок не нажмёт ОК, и гаснут через N "
                + "секунд после этого. Нет — гаснут сами", a.waitForOk, on -> {
            a.waitForOk = on;
            changed.run();
        });
        if (a.waitForOk) {
            ui.number("Гаснут через (сек) после ОК", "0–600", a.fadeSeconds, 0, 600, v -> {
                a.fadeSeconds = (int) v;
                changed.run();
            });
        } else {
            ui.number("Длятся (сек)", "1–600", a.effectSeconds, 1, 600, v -> {
                a.effectSeconds = (int) v;
                changed.run();
            });
        }
        ui.buttons().add("Проверить на себе", Ui.PURPLE, test::run);
        ui.header("Окно ENGINE ERROR", "&8на языке игрока");
        for (int i = 0; i < a.texts.size(); i++) {
            final int index = i;
            final Subworld.LangText t = a.texts.get(i);
            ui.row(new ItemStack(Items.PAPER), "&e" + t.lang).line("&7" + t.title).line("&8кнопка: " + t.button)
                    .button("Удалить", Ui.RED, () -> {
                        if (a.texts.size() <= 1) {
                            throw new UiError("Должен остаться хотя бы один язык.");
                        }
                        a.texts.remove(index);
                        changed.run();
                    }).confirm("Удалить тексты для языка " + t.lang + "?");
            ui.field(t.lang + ": заголовок", "", t.title, v -> {
                t.title = v.trim();
                changed.run();
            }).max(64);
            ui.field(t.lang + ": текст", "\\n — новая строка", t.text.replace("\n", "\\n"), v -> {
                t.text = v.trim().replace("\\n", "\n");
                changed.run();
            }).max(1000);
            ui.field(t.lang + ": кнопка", "", t.button, v -> {
                t.button = v.trim().isEmpty() ? "OK" : v.trim();
                changed.run();
            }).max(32);
        }
        ui.field("Добавить язык", "Код языка: ru_ru, en_us, uk_ua, de_de…", "", v -> {
            String lang = v.trim().toLowerCase(Locale.ROOT);
            if (lang.isEmpty()) {
                return;
            }
            if (!lang.matches("[a-z]{2,3}_[a-z]{2,3}")) {
                throw new UiError("Код языка вида ru_ru.");
            }
            for (Subworld.LangText x : a.texts) {
                if (x.lang.equals(lang)) {
                    throw new UiError("Этот язык уже есть.");
                }
            }
            Subworld.LangText base = a.texts.isEmpty() ? new Subworld.LangText() : a.texts.get(0);
            a.texts.add(new Subworld.LangText(lang, base.title, base.text, base.button));
            changed.run();
        }).placeholder("en_us").max(8);
    }

    // --- моды по мирам (общее для подмиров и обычных миров) ---------------------------------------------

    private static final List<String> SKIP_MODS = java.util.Arrays.asList("minecraft", "mcp", "fml", "forge", "ecoserv", "forgelin");

    static boolean disabledIn(ItemRule rule, String subworld) {
        if (rule == null || !rule.hasAnyDeny()) {
            return false;
        }
        if (rule.worlds.isEmpty()) {
            return !containsName(rule.except, subworld);
        }
        return containsName(rule.worlds, subworld);
    }

    private static boolean containsName(List<String> names, String name) {
        for (String n : names) {
            if (WorldNames.normalize(n).equals(WorldNames.normalize(name))) {
                return true;
            }
        }
        return false;
    }

    private static void removeName(List<String> names, String name) {
        names.removeIf(n -> WorldNames.normalize(n).equals(WorldNames.normalize(name)));
    }

    static void mods(Ui ui, final net.minecraft.entity.player.EntityPlayerMP player, final String world, String title) {
        ui.note(Ui.BLUE, "Отключённый мод: его предметы не переносятся в " + title + " (ждут в буфере /stash), не ставятся "
                + "и не крафтятся здесь. «Тонко» — какие именно действия запретить.");
        ui.search("поиск мода");
        List<ModContainer> mods = new ArrayList<ModContainer>();
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            if (!SKIP_MODS.contains(mod.getModId().toLowerCase(Locale.ROOT))) {
                mods.add(mod);
            }
        }
        mods.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (final ModContainer mod : mods) {
            final String key = ItemRules.modKey(mod.getModId());
            ItemRule rule = ItemRules.exact(key);
            boolean off = disabledIn(rule, world);
            Ui.Row row = ui.row(off ? new ItemStack(Blocks.BARRIER) : new ItemStack(Items.BOOK), (off ? "&c" : "&f") + mod.getName())
                    .line("&8" + mod.getModId() + (rule != null ? " · правило: " + UiUtil.plain(rule.describeWorlds()) : ""))
                    .chip(off ? "отключён" : "работает", off ? Ui.RED : Ui.GREEN);
            row.button(off ? "Включить" : "Отключить", off ? Ui.GREEN : Ui.RED, () -> toggleMod(world, key));
            row.button("Тонко", Ui.GREY, () -> {
                if (ItemRules.exact(key) == null) {
                    create(world, key);
                }
                new com.ecoserv.restrict.RuleScreen(player, key, ItemStack.EMPTY).open();
            }).tip("Настроить правило мода: действия и миры");
        }
    }

    private static void toggleMod(String world, String key) {
        ItemRule rule = ItemRules.exact(key);
        if (disabledIn(rule, world)) {
            if (rule.worlds.isEmpty()) {
                rule.except.add(world);
            } else {
                removeName(rule.worlds, world);
                if (rule.worlds.isEmpty()) {
                    ItemRules.remove(key);
                    return;
                }
            }
        } else if (rule == null) {
            create(world, key);
            return;
        } else if (rule.worlds.isEmpty()) {
            removeName(rule.except, world);
            if (!rule.hasAnyDeny()) {
                allDenied(rule);
            }
        } else {
            rule.worlds.add(world);
            if (!rule.hasAnyDeny()) {
                allDenied(rule);
            }
        }
        ItemRules.changed();
    }

    private static ItemRule create(String world, String key) {
        ItemRule rule = ItemRules.getOrCreate(key);
        allDenied(rule);
        rule.worlds.clear();
        rule.worlds.add(world);
        rule.note = "отключён в мире " + world;
        ItemRules.changed();
        return rule;
    }

    private static void allDenied(ItemRule rule) {
        for (RestrictAction a : RestrictAction.values()) {
            rule.setDenied(a, true);
        }
    }

}
