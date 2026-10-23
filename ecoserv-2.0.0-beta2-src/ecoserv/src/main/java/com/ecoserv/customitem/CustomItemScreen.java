package com.ecoserv.customitem;

import com.ecoserv.dev.ConditionCatalog;
import com.ecoserv.dev.ParamForm;
import com.ecoserv.dev.StepsEditor;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Конструктор своих предметов. */
public class CustomItemScreen extends UiScreen {

    @Nullable
    private String item;
    private final StepsEditor.State steps = new StepsEditor.State();
    private final Map<String, String> cond = new HashMap<String, String>();

    public CustomItemScreen(EntityPlayerMP player, @Nullable String item) {
        super(player);
        this.item = item;
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
        final CustomItem c = item == null ? null : CustomItems.get(item);
        if (c == null) {
            item = null;
            ui.title("Свои предметы").icon(new ItemStack(Items.NETHER_STAR));
            ui.text("&7Обычный предмет Minecraft со своим именем, описанием, свечением, уроном и действием по ПКМ."
                    + " Выдать: кнопка, шаг сценария «Дать свой предмет», награда квеста (из руки).");
            for (final CustomItem x : CustomItems.all()) {
                ui.row(CustomItems.stack(x, 1), x.name + " &8" + x.id)
                        .line("&7" + (x.steps.isEmpty() ? "без действия" : "ПКМ: " + x.steps.size() + " шаг.")
                                + (x.cooldown > 0 ? " · пауза " + x.cooldown + " с" : ""))
                        .onClick(() -> item = x.id)
                        .button("Взять", Ui.GREEN, () -> PlayerUtil.giveOrDrop(player, CustomItems.stack(x, 1)));
            }
            ui.field("Новый предмет", "id — Enter", "", v -> item = CustomItems.create(v).id).placeholder("magic_wand").max(32);
            ui.buttons().add("Из предмета в руке", Ui.BLUE, () -> {
                ItemStack h = player.getHeldItemMainhand();
                if (h.isEmpty()) {
                    throw new UiError("В руке пусто.");
                }
                CustomItem n = CustomItems.create("item_" + (CustomItems.all().size() + 1));
                n.base = com.ecoserv.util.ItemKey.of(h, false, true).toString();
                n.name = h.hasDisplayName() ? h.getDisplayName() : "&b" + h.getDisplayName();
                CustomItems.save();
                item = n.id;
            });
            return;
        }
        ui.back(() -> item = null);
        ui.title(c.name).subtitle("&8" + c.id).icon(CustomItems.stack(c, 1));
        ui.sideButton("Взять", new ItemStack(Items.NETHER_STAR), Ui.GREEN, () -> PlayerUtil.giveOrDrop(player, CustomItems.stack(c, 1)));
        ui.header("Вид");
        ui.field("Основа", "Какой предмет Minecraft (иконка и поведение)", c.base, v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Не нашёл такой предмет.");
            }
            c.base = id;
            CustomItems.save();
        }).picker(Ui.PICK_ITEM);
        ui.field("Название", "& — цвета", c.name, v -> {
            c.name = v.trim();
            CustomItems.save();
        }).max(64);
        ui.field("Описание", "| — новая строка", String.join("|", c.lore), v -> {
            c.lore.clear();
            for (String l : v.split("\\|")) {
                if (!l.trim().isEmpty()) {
                    c.lore.add(l.trim());
                }
            }
            CustomItems.save();
        }).clearable().max(300);
        ui.toggle("Свечение", "Как у зачарованного", c.glow, on -> {
            c.glow = on;
            CustomItems.save();
        });
        ui.toggle("Неразрушимый", "", c.unbreakable, on -> {
            c.unbreakable = on;
            CustomItems.save();
        });
        ui.header("Свойства в руке");
        dsel(ui, "Урон", "Сердечки ×2: 7 — как алмазный меч", c.damage, new double[]{0, 4, 6, 7, 9, 12, 16, 20}, v -> c.damage = v);
        dsel(ui, "Скорость атаки", "4 — как рука, 1.6 — как меч", c.attackSpeed, new double[]{0, 0.8, 1.2, 1.6, 2.0, 3.0, 4.0}, v -> c.attackSpeed = v);
        dsel(ui, "Скорость бега, %", "", c.moveSpeed, new double[]{0, 10, 20, 30, 50, -20}, v -> c.moveSpeed = v);
        dsel(ui, "Доп. здоровье", "Единиц (2 = сердце)", c.health, new double[]{0, 2, 4, 6, 10, 20}, v -> c.health = v);
        ui.header("Действие по ПКМ");
        ui.select("Пауза", "", Integer.toString(c.cooldown), v -> {
            c.cooldown = Integer.parseInt(v);
            CustomItems.save();
        }).option("0", "Нет").option("1", "1 сек").option("5", "5 сек").option("10", "10 сек").option("30", "30 сек")
                .option("60", "1 мин").option("300", "5 мин");
        ui.toggle("Тратится", "Один предмет уходит при использовании", c.consume, on -> {
            c.consume = on;
            CustomItems.save();
        });
        ui.toggle("Только при условии", "", !c.condition.isEmpty(), on -> {
            c.condition = on ? "role vip" : "";
            CustomItems.save();
        });
        if (!c.condition.isEmpty()) {
            cond.put("c", c.condition);
            ParamForm.condition(ui, player, "c", cond);
            if (!cond.get("c").equals(c.condition)) {
                c.condition = cond.get("c");
                CustomItems.save();
            }
            ui.note(Ui.BLUE, "Условие: " + ConditionCatalog.describe(c.condition));
        }
        StepsEditor.build(ui, steps, "Что делает", c.steps, CustomItems::save);
        ui.buttons().add("Удалить", Ui.RED, () -> {
            CustomItems.delete(c);
            item = null;
        }).confirm("Удалить «" + c.id + "»? Уже выданные предметы останутся, но без действия.");
    }

    private interface DSet {
        void set(double v);
    }

    private void dsel(Ui ui, String label, String desc, double cur, double[] opts, final DSet set) {
        Ui.Select s = ui.select(label, desc, fmt(cur), v -> {
            set.set(Double.parseDouble(v));
            CustomItems.save();
        });
        boolean has = false;
        for (double o : opts) {
            s.option(fmt(o), o == 0 ? "как у основы" : fmt(o));
            has |= o == cur;
        }
        if (!has) {
            s.option(fmt(cur), fmt(cur));
        }
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? Long.toString((long) d) : Double.toString(d);
    }
}
