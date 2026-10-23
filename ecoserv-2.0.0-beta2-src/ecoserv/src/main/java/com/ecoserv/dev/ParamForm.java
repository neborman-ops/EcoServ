package com.ecoserv.dev;

import com.ecoserv.bank.BankManager;
import com.ecoserv.bank.Currency;
import com.ecoserv.script.Script;
import com.ecoserv.script.ScriptManager;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Поля визуального редактора (2.0): для каждого вида — свой выбор (список, предмет, звук, координаты…). */
public final class ParamForm {

    private ParamForm() {
    }

    static String get(Map<String, String> v, String k, String def) {
        String s = v.get(k);
        return s == null || s.isEmpty() ? def : s;
    }

    /** Нарисовать поле параметра. */
    public static void field(Ui ui, final EntityPlayerMP player, final StepCatalog.Param p, final Map<String, String> v) {
        final String label = p.label + (p.optional ? " &8(необязательно)" : "");
        String cur = v.containsKey(p.key) ? v.get(p.key) : p.def;
        switch (p.kind) {
            case ITEM:
            case BLOCK:
                ui.field(label, p.hint.isEmpty() ? "Кнопка … — выбрать из всех предметов" : p.hint, cur, x -> {
                    String id = UiUtil.parseItemId(x);
                    if (id == null && !x.trim().isEmpty()) {
                        throw new UiError("Не нашёл такой предмет: " + x);
                    }
                    v.put(p.key, id == null ? "" : stripMeta(id));
                }).picker(Ui.PICK_ITEM).max(128);
                return;
            case SOUND:
                ui.field(label, p.hint.isEmpty() ? "Кнопка … — выбрать и послушать" : p.hint, cur, x -> v.put(p.key, x.trim()))
                        .picker(Ui.PICK_SOUND).max(128);
                return;
            case PLAYER:
                ui.field(label, p.hint, cur, x -> v.put(p.key, x.trim())).picker(Ui.PICK_PLAYER).max(32);
                return;
            case COORDS: {
                ui.field(label, p.hint.isEmpty() ? "x y z; ~ — от игрока (~ ~1 ~ — на блок выше)" : p.hint, cur,
                        x -> v.put(p.key, x.trim())).max(64).clearable();
                ui.buttons().add("«" + p.label + "» = где я стою", Ui.BLUE, () -> v.put(p.key,
                        (int) Math.floor(player.posX) + " " + (int) Math.floor(player.posY) + " " + (int) Math.floor(player.posZ)))
                        .tip("Точные координаты блока под тобой");
                return;
            }
            case WORLD: {
                Ui.Select s = ui.select(label, p.hint, cur, x -> v.put(p.key, x));
                for (String w : com.ecoserv.world.WorldNames.worldChoices()) {
                    s.option(w, w);
                }
                for (com.ecoserv.subworld.Subworld sw : com.ecoserv.subworld.SubworldRegistry.all()) {
                    s.option(sw.id, sw.name, "подмир");
                }
                return;
            }
            case LIST: {
                Ui.Select s = ui.select(label, p.hint, cur, x -> v.put(p.key, x));
                boolean has = false;
                for (String[] o : StepCatalog.options(p.options[0][0])) {
                    has |= o[0].equals(cur);
                    if (o.length > 3) {
                        s.option(o[0], o[1], o[2], UiUtil.icon(o[3]));
                    } else {
                        s.option(o[0], o.length > 1 ? o[1] : o[0], o.length > 2 ? o[2] : "");
                    }
                }
                if (!has && !cur.isEmpty()) {
                    s.option(cur, cur, "нет в списке");
                }
                return;
            }
            case SCRIPT: {
                Ui.Select s = ui.select(label, p.hint, cur, x -> v.put(p.key, x));
                for (Script sc : ScriptManager.all()) {
                    s.option(sc.name, sc.name, sc.description);
                }
                return;
            }
            case CURRENCY: {
                Ui.Select s = ui.select(label, p.hint, cur, x -> v.put(p.key, x));
                for (Currency c : BankManager.currencies()) {
                    s.option(c.id, c.name, c.description, UiUtil.icon(c.icon));
                }
                return;
            }
            case ROLE: {
                Ui.Select s = ui.select(label, p.hint, cur, x -> v.put(p.key, x));
                for (String r : com.ecoserv.role.RoleManager.ids()) {
                    s.option(r, r);
                }
                return;
            }
            case EFFECT: {
                Ui.Select s = ui.select(label, p.hint, cur, x -> v.put(p.key, x));
                List<String> ids = new ArrayList<String>();
                for (Object o : Potion.REGISTRY.getKeys()) {
                    ResourceLocation rl = (ResourceLocation) o;
                    ids.add("minecraft".equals(rl.getNamespace()) ? rl.getPath() : rl.toString());
                }
                Collections.sort(ids);
                for (String id : ids) {
                    s.option(id, id);
                }
                return;
            }
            case PARTICLE: {
                Ui.Select s = ui.select(label, p.hint, cur, x -> v.put(p.key, x));
                for (EnumParticleTypes t : EnumParticleTypes.values()) {
                    s.option(t.getParticleName(), t.getParticleName());
                }
                return;
            }
            case CHOICE: {
                Ui.Select s = ui.select(label, p.hint, cur, x -> v.put(p.key, x));
                for (String[] o : p.options) {
                    s.option(o[0], o[1]);
                }
                return;
            }
            case TIME: {
                Ui.Select s = ui.select(label, p.hint, cur, x -> v.put(p.key, x));
                for (String t : new String[]{"5", "10", "20", "1s", "2s", "3s", "5s", "10s", "30s", "1m", "5m"}) {
                    s.option(t, t.endsWith("s") ? t.replace("s", " сек") : t.endsWith("m") ? t.replace("m", " мин") : t + " тиков");
                }
                ui.field(label + " — своё", "40 — тики, 2s — секунды, 1m — минуты", cur, x -> v.put(p.key, x.trim())).max(12);
                return;
            }
            case CONDITION:
                condition(ui, player, p.key, v);
                return;
            case INT:
            case NUMBER:
            case WORD:
            case TEXT:
            case STEP:
            default:
                ui.field(label, p.hint, cur, x -> v.put(p.key, x.trim())).max(p.kind == StepCatalog.Kind.TEXT || p.kind == StepCatalog.Kind.STEP ? 400 : 64);
        }
    }

    private static String stripMeta(String id) {
        return id.endsWith("@0") ? id.substring(0, id.length() - 2) : id;
    }

    /** Конструктор условия: вид (список с иконками) + поля + «НЕ». Хранится строкой в v.get(key). */
    public static void condition(Ui ui, final EntityPlayerMP player, final String key, final Map<String, String> v) {
        if (get(v, key, "").isEmpty()) {
            v.put(key, "chance 50");
        }
        final Map<String, String> c = ConditionCatalog.parse(get(v, key, "role vip"));
        Ui.Select type = ui.select("Условие", "Что проверить", c.get("type"), x -> {
            c.clear();
            c.put("type", x);
            v.put(key, ConditionCatalog.build(c));
        });
        for (ConditionCatalog.Def d : ConditionCatalog.all()) {
            type.option(d.id, d.title, "", UiUtil.icon(d.icon));
        }
        ui.toggle("Наоборот (НЕ)", "Выполнится, если условие НЕ верно", "1".equals(c.get("not")), on -> {
            c.put("not", on ? "1" : "");
            v.put(key, ConditionCatalog.build(c));
        });
        ConditionCatalog.Def d = ConditionCatalog.get(c.get("type"));
        if (d != null) {
            for (final String[] f : d.fields) {
                String cur = c.containsKey(f[0]) ? c.get(f[0]) : "";
                switch (f[2]) {
                    case "op": {
                        Ui.Select s = ui.select(f[1], "", cur.isEmpty() ? ">=" : cur, x -> {
                            c.put(f[0], x);
                            v.put(key, ConditionCatalog.build(c));
                        });
                        for (String[] o : ConditionCatalog.OPS) {
                            s.option(o[0], o[1] + " (" + o[0] + ")");
                        }
                        break;
                    }
                    case "role": {
                        Ui.Select s = ui.select(f[1], "", cur, x -> {
                            c.put(f[0], x);
                            v.put(key, ConditionCatalog.build(c));
                        });
                        for (String r : com.ecoserv.role.RoleManager.ids()) {
                            s.option(r, r);
                        }
                        break;
                    }
                    case "world": {
                        Ui.Select s = ui.select(f[1], "", cur, x -> {
                            c.put(f[0], x);
                            v.put(key, ConditionCatalog.build(c));
                        });
                        for (String w : com.ecoserv.world.WorldNames.worldChoices()) {
                            s.option(w, w);
                        }
                        break;
                    }
                    case "currency": {
                        Ui.Select s = ui.select(f[1], "", cur, x -> {
                            c.put(f[0], x);
                            v.put(key, ConditionCatalog.build(c));
                        });
                        for (Currency cc : BankManager.currencies()) {
                            s.option(cc.id, cc.name, "", UiUtil.icon(cc.icon));
                        }
                        break;
                    }
                    case "quest": {
                        Ui.Select s = ui.select(f[1], "", cur, x -> {
                            c.put(f[0], x);
                            v.put(key, ConditionCatalog.build(c));
                        });
                        for (com.ecoserv.quest.QuestBook.Chapter ch : com.ecoserv.quest.QuestManager.chapters()) {
                            for (com.ecoserv.quest.QuestBook.Quest q : ch.quests) {
                                s.option(q.id, q.title, ch.title);
                            }
                        }
                        break;
                    }
                    case "item":
                        ui.field(f[1], "Кнопка … — выбрать", cur, x -> {
                            String id = UiUtil.parseItemId(x);
                            c.put(f[0], id == null ? x.trim() : stripMeta(id));
                            v.put(key, ConditionCatalog.build(c));
                        }).picker(Ui.PICK_ITEM);
                        break;
                    default:
                        ui.field(f[1], "", cur, x -> {
                            c.put(f[0], x.trim().replace(' ', '_'));
                            v.put(key, ConditionCatalog.build(c));
                        }).max(48);
                }
            }
        }
        ui.text("&7Сейчас: &f" + ConditionCatalog.describe(get(v, key, "")));
    }
}
