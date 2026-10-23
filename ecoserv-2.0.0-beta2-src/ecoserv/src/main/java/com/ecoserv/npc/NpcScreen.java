package com.ecoserv.npc;

import com.ecoserv.dev.ConditionCatalog;
import com.ecoserv.dev.ParamForm;
import com.ecoserv.dev.StepsEditor;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Редактор NPC и голограмм (2.0): всё выбирается списками и карточками. */
public class NpcScreen extends UiScreen {

    private enum Tab {
        LOOK("Вид", "minecraft:armor_stand"),
        GEAR("Снаряжение", "minecraft:iron_chestplate"),
        TALK("Диалог", "minecraft:writable_book"),
        CLICK("При нажатии", "minecraft:stone_button"),
        PLACE("Место", "minecraft:compass");

        final String title;
        final String icon;

        Tab(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static final String[][] SLOTS = {
            {"head", "Голова", "minecraft:iron_helmet"}, {"chest", "Тело", "minecraft:iron_chestplate"},
            {"legs", "Ноги", "minecraft:iron_leggings"}, {"feet", "Ступни", "minecraft:iron_boots"},
            {"mainhand", "Правая рука", "minecraft:iron_sword"}, {"offhand", "Левая рука", "minecraft:shield"}};

    private static final String[][] DIRS = {{"180", "на север"}, {"225", "на северо-восток"}, {"270", "на восток"},
            {"315", "на юго-восток"}, {"0", "на юг"}, {"45", "на юго-запад"}, {"90", "на запад"}, {"135", "на северо-запад"}};

    @Nullable
    private String npc;
    @Nullable
    private String holo;
    private boolean holoList;
    private Tab tab = Tab.LOOK;
    private String newId = "";
    private String newType = "minecraft:villager";
    /** Диалог: выбранная реплика и вариант. */
    @Nullable
    private String node;
    private int option = -1;
    private final StepsEditor.State clickSteps = new StepsEditor.State();
    private final StepsEditor.State optSteps = new StepsEditor.State();
    private final Map<String, String> cond = new HashMap<String, String>();

    public NpcScreen(EntityPlayerMP player, @Nullable String npc) {
        super(player);
        this.npc = npc;
    }

    public static NpcScreen holograms(EntityPlayerMP player) {
        NpcScreen s = new NpcScreen(player, null);
        s.holoList = true;
        return s;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        if (StepsEditor.modal(ui, player, clickSteps) || StepsEditor.modal(ui, player, optSteps)) {
            return;
        }
        if (holo != null) {
            Hologram h = NpcManager.hologram(holo);
            if (h != null) {
                buildHolo(ui, h);
                return;
            }
            holo = null;
        }
        if (npc != null) {
            Npc n = NpcManager.get(npc);
            if (n != null) {
                buildNpc(ui, n);
                return;
            }
            npc = null;
        }
        ui.title("NPC и голограммы").icon(new ItemStack(Items.ARMOR_STAND));
        ui.tab("NPC", new ItemStack(Items.EMERALD), !holoList, () -> holoList = false);
        ui.tab("Голограммы", new ItemStack(Items.NAME_TAG), holoList, () -> holoList = true);
        if (holoList) {
            buildHoloList(ui);
        } else {
            buildList(ui);
        }
    }

    // --- список -------------------------------------------------------------------------------------

    private void buildList(Ui ui) {
        ui.header("NPC", NpcManager.all().size() + "");
        ui.text("&7NPC стоит на месте, смотрит на игроков и разговаривает: диалог с вариантами ответа, действия,"
                + " квест «Поговорить с NPC». &fShift+ПКМ&7 по NPC (админ) — сразу в настройки.");
        if (NpcManager.all().size() > 8) {
            ui.search("поиск NPC");
        }
        for (final Npc n : NpcManager.all()) {
            ui.row(UiUtil.icon(MobTypes.icon(n.type)), n.name + " &8" + n.id)
                    .line("&7" + MobTypes.title(n.type) + " · " + com.ecoserv.world.WorldNames.nameOf(n.dim)
                            + " " + (int) n.x + " " + (int) n.y + " " + (int) n.z)
                    .line("&8реплик: " + n.dialog.size() + (n.clickSteps.isEmpty() ? "" : " · действий: " + n.clickSteps.size()))
                    .onClick(() -> open(n.id))
                    .button("Настроить", Ui.BLUE, () -> open(n.id));
        }
        ui.header("Новый NPC — здесь, где ты стоишь");
        Ui.Select s = ui.select("Кто это", "", newType, v -> newType = v);
        for (String[] t : MobTypes.ALL) {
            s.option(t[0], t[1], "", UiUtil.icon(t[2]));
        }
        ui.field("id", "Латиница, цифры, _ (для квестов и сценариев)", newId, v -> newId = v.trim()).placeholder("торговец_иван").max(32);
        ui.buttons().add("Создать", Ui.GREEN, () -> {
            Npc n = NpcManager.create(player, newId.isEmpty() ? "npc_" + (NpcManager.all().size() + 1) : newId, newType);
            newId = "";
            open(n.id);
            toast("NPC создан — настрой вид и диалог.");
        });
    }

    private void open(String id) {
        npc = id;
        tab = Tab.LOOK;
        node = null;
        option = -1;
    }

    // --- NPC ----------------------------------------------------------------------------------------

    private void buildNpc(Ui ui, final Npc n) {
        ui.back(() -> npc = null);
        ui.title(n.name).subtitle("&8" + n.id + " · " + MobTypes.title(n.type)).icon(UiUtil.icon(MobTypes.icon(n.type)));
        for (Tab x : Tab.values()) {
            final Tab tx = x;
            ui.tab(x.title, UiUtil.icon(x.icon), tab == x, () -> tab = tx);
        }
        ui.sideLabel("&8Проверка");
        ui.sideButton("Поговорить", new ItemStack(Items.EMERALD), Ui.GREEN, () -> new NpcDialogScreen(player, n, n.dialog.get(0).id).open());
        switch (tab) {
            case GEAR:
                gear(ui, n);
                break;
            case TALK:
                talk(ui, n);
                break;
            case CLICK:
                click(ui, n);
                break;
            case PLACE:
                place(ui, n);
                break;
            default:
                look(ui, n);
        }
    }

    private void changed(Npc n, boolean respawn) {
        if (respawn) {
            NpcManager.respawn(n);
        } else {
            NpcManager.save();
            com.ecoserv.npc.NpcManager.respawn(n);
        }
    }

    private void look(Ui ui, final Npc n) {
        ui.field("Имя над головой", "& — цвета; пусто — без имени", n.name, v -> {
            n.name = v.trim();
            changed(n, true);
        }).max(64);
        Ui.Select s = ui.select("Кто это", "Вид сущности", n.type, v -> {
            n.type = v;
            changed(n, true);
        });
        for (String[] t : MobTypes.ALL) {
            s.option(t[0], t[1], "", UiUtil.icon(t[2]));
        }
        if ("minecraft:villager".equals(n.type)) {
            Ui.Select pr = ui.select("Профессия", "Одежда жителя", Integer.toString(n.profession), v -> {
                n.profession = Integer.parseInt(v);
                changed(n, true);
            });
            for (String[] p : MobTypes.PROFESSIONS) {
                pr.option(p[0], p[1], "", UiUtil.icon(p[2]));
            }
        }
        ui.toggle("Маленький", "Детёныш / маленькая стойка", n.baby, on -> {
            n.baby = on;
            changed(n, true);
        });
        ui.toggle("Смотрит на игроков", "Поворачивает голову к ближайшему игроку", n.lookAtPlayers, on -> {
            n.lookAtPlayers = on;
            NpcManager.save();
        });
        ui.toggle("Имя видно всегда", "Иначе — только при наведении", n.nameVisible, on -> {
            n.nameVisible = on;
            changed(n, true);
        });
        ui.toggle("Свечение", "Контур, видный сквозь стены", n.glowing, on -> {
            n.glowing = on;
            changed(n, true);
        });
        if (n.type.contains("zombie") || n.type.contains("skeleton") || n.type.contains("husk")) {
            ui.note(Ui.YELLOW, "Нежить горит днём (урона нет, но видно огонь) — надень на неё шлем во вкладке «Снаряжение».");
        }
    }

    private void gear(Ui ui, final Npc n) {
        ui.text("&7Выбери предмет для каждого слота или нажми «Как у меня» — NPC наденет то же, что на тебе.");
        ui.buttons().add("Как у меня", Ui.BLUE, () -> {
            for (EntityEquipmentSlot s : EntityEquipmentSlot.values()) {
                ItemStack st = player.getItemStackFromSlot(s);
                if (st.isEmpty()) {
                    n.equipment.remove(s.getName());
                } else {
                    n.equipment.put(s.getName(), ItemKey.of(st, false, true).toString());
                }
            }
            changed(n, true);
            toast("Снаряжение скопировано.");
        });
        for (final String[] slot : SLOTS) {
            String cur = n.equipment.containsKey(slot[0]) ? n.equipment.get(slot[0]) : "";
            ui.field(slot[1], cur.isEmpty() ? "пусто" : "", cur, v -> {
                if (v.trim().isEmpty()) {
                    n.equipment.remove(slot[0]);
                } else {
                    String id = UiUtil.parseItemId(v);
                    if (id == null) {
                        throw new UiError("Не нашёл такой предмет.");
                    }
                    n.equipment.put(slot[0], id);
                }
                changed(n, true);
            }).picker(Ui.PICK_ITEM).clearable();
        }
    }

    // --- диалог -------------------------------------------------------------------------------------

    private void talk(Ui ui, final Npc n) {
        Npc.Node cur = node == null ? null : n.node(node);
        if (cur != null && node != null && cur.id.equals(node)) {
            nodeEditor(ui, n, cur);
            return;
        }
        ui.header("Реплики", n.dialog.size() + "");
        ui.text("&7Диалог — это реплики NPC и ответы игрока кнопками. Ответ может вести к другой реплике,"
                + " выполнять действия (дать предмет, начать квест…) и показываться только при условии."
                + " Первая реплика — с неё начинается разговор.");
        for (int i = 0; i < n.dialog.size(); i++) {
            final Npc.Node d = n.dialog.get(i);
            final int idx = i;
            Ui.Row r = ui.row(new ItemStack(i == 0 ? Items.WRITABLE_BOOK : Items.PAPER), "&f" + d.id + (i == 0 ? " &a(начало)" : ""))
                    .line("&7" + UiUtil.cut(ChatUtil.strip(ChatUtil.color(d.text)).replace("\\n", " "), 60));
            StringBuilder b = new StringBuilder();
            for (Npc.Option o : d.options) {
                b.append(b.length() > 0 ? " &8| " : "").append("&e").append(UiUtil.cut(o.label, 18))
                        .append(o.next.isEmpty() ? "" : " &8→ " + o.next);
            }
            if (b.length() > 0) {
                r.line(b.toString());
            }
            r.onClick(() -> {
                node = d.id;
                option = -1;
            });
            r.button("✎", Ui.BLUE, () -> {
                node = d.id;
                option = -1;
            });
            if (i > 0) {
                r.button("✕", Ui.RED, () -> {
                    n.dialog.remove(idx);
                    NpcManager.save();
                }).confirm("Удалить реплику «" + d.id + "»?");
            }
        }
        ui.field("Новая реплика", "id реплики — Enter", "", v -> {
            String id = v.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
            if (id.isEmpty()) {
                return;
            }
            for (Npc.Node d : n.dialog) {
                if (d.id.equals(id)) {
                    throw new UiError("Такая реплика уже есть.");
                }
            }
            Npc.Node d = new Npc.Node();
            d.id = id;
            d.text = "…";
            n.dialog.add(d);
            NpcManager.save();
            node = id;
        }).placeholder("про_квест").max(24);
    }

    private void nodeEditor(Ui ui, final Npc n, final Npc.Node d) {
        ui.back(() -> {
            if (option >= 0) {
                option = -1;
            } else {
                node = null;
            }
        });
        if (option >= 0 && option < d.options.size()) {
            optionEditor(ui, n, d, d.options.get(option));
            return;
        }
        ui.header("Реплика «" + d.id + "»");
        ui.field("Что говорит NPC", "\\n — новая строка; %player% — ник; %var:имя%", d.text, v -> {
            d.text = v;
            NpcManager.save();
        }).max(400);
        ui.header("Ответы игрока", d.options.size() + "");
        if (d.options.isEmpty()) {
            ui.text("&8Ответов нет — в диалоге будет кнопка «Уйти».");
        }
        for (int i = 0; i < d.options.size(); i++) {
            final Npc.Option o = d.options.get(i);
            final int idx = i;
            Ui.Row r = ui.row(new ItemStack(o.next.isEmpty() ? Blocks.OAK_DOOR : Blocks.LEVER), "&e› &f" + o.label)
                    .line("&7" + (o.next.isEmpty() ? "закрывает диалог" : "→ реплика " + o.next)
                            + (o.steps.isEmpty() ? "" : " · действий: " + o.steps.size()));
            if (!o.condition.isEmpty()) {
                r.line("&9если " + ConditionCatalog.describe(o.condition));
            }
            r.onClick(() -> option = idx);
            r.button("✎", Ui.BLUE, () -> option = idx);
            r.button("↑", Ui.GREY, () -> {
                if (idx > 0) {
                    d.options.add(idx - 1, d.options.remove(idx));
                    NpcManager.save();
                }
            });
            r.button("✕", Ui.RED, () -> {
                d.options.remove(idx);
                NpcManager.save();
            });
        }
        ui.buttons().add("Добавить ответ", Ui.GREEN, () -> {
            Npc.Option o = new Npc.Option();
            o.label = "Ответ " + (d.options.size() + 1);
            d.options.add(o);
            NpcManager.save();
            option = d.options.size() - 1;
        });
    }

    private void optionEditor(Ui ui, final Npc n, final Npc.Node d, final Npc.Option o) {
        ui.header("Ответ игрока");
        ui.field("Текст кнопки", "", o.label, v -> {
            o.label = v.trim().isEmpty() ? "…" : v.trim();
            NpcManager.save();
        }).max(80);
        Ui.Select next = ui.select("Что дальше", "", o.next, v -> {
            if ("+new".equals(v)) {
                Npc.Node nn = new Npc.Node();
                nn.id = "реплика_" + (n.dialog.size() + 1);
                nn.text = "…";
                n.dialog.add(nn);
                o.next = nn.id;
            } else {
                o.next = v;
            }
            NpcManager.save();
        });
        next.option("", "Закрыть диалог", "", new ItemStack(Blocks.OAK_DOOR));
        for (Npc.Node x : n.dialog) {
            next.option(x.id, "Реплика «" + x.id + "»", UiUtil.cut(ChatUtil.strip(ChatUtil.color(x.text)), 40), new ItemStack(Items.PAPER));
        }
        next.option("+new", "Новая реплика…", "Создать и перейти к ней", new ItemStack(Items.WRITABLE_BOOK));
        ui.toggle("Только при условии", "Иначе ответ скрыт", !o.condition.isEmpty(), on -> {
            o.condition = on ? "chance 100" : "";
            NpcManager.save();
        });
        if (!o.condition.isEmpty()) {
            cond.put("c", o.condition);
            ParamForm.condition(ui, player, "c", cond);
            if (!cond.get("c").equals(o.condition)) {
                o.condition = cond.get("c");
                NpcManager.save();
            }
            ui.note(Ui.BLUE, "Сейчас: " + ConditionCatalog.describe(o.condition));
            ui.buttons().add("Сохранить условие", Ui.BLUE, () -> {
                o.condition = cond.get("c");
                NpcManager.save();
                toast("Условие сохранено.");
            });
        }
        StepsEditor.build(ui, optSteps, "Действия при выборе", o.steps, NpcManager::save);
    }

    private void click(Ui ui, final Npc n) {
        ui.select("При нажатии", "", n.onClick, v -> {
            n.onClick = v;
            NpcManager.save();
        }).option("dialog", "Открыть диалог", "Окно с репликой и ответами", new ItemStack(Items.WRITABLE_BOOK))
                .option("steps", "Только действия", "Выполнить шаги ниже, без окна", new ItemStack(Blocks.STONE_BUTTON))
                .option("both", "Действия и диалог", "Сначала шаги, потом окно", new ItemStack(Blocks.COMMAND_BLOCK));
        ui.number("Пауза между нажатиями, сек", "", n.cooldown, 0, 3600, v -> {
            n.cooldown = (int) v;
            NpcManager.save();
        });
        ui.note(Ui.BLUE, "Нажатие на NPC засчитывает квест «Поговорить с NPC» (id &f" + n.id + "&9) и событие &fnpc_click&9.");
        if (!"dialog".equals(n.onClick)) {
            StepsEditor.build(ui, clickSteps, "Действия при нажатии", n.clickSteps, NpcManager::save);
        }
    }

    private void place(Ui ui, final Npc n) {
        ui.row(new ItemStack(Items.COMPASS), "&f" + com.ecoserv.world.WorldNames.nameOf(n.dim))
                .line("&7" + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", n.x, n.y, n.z));
        ui.buttons().add("Поставить сюда", Ui.GREEN, () -> {
            NpcManager.moveTo(n, player);
            NpcManager.respawn(n);
            toast("NPC перенесён.");
        }).add("Телепорт к NPC", Ui.BLUE, () -> {
            com.ecoserv.util.TeleportRules.class.getName();
            player.connection.setPlayerLocation(n.x, n.y, n.z + 1.5D, 180.0F, 0.0F);
        });
        ui.header("Ходьба");
        ui.field("Путь", "Имя маршрута: поставь маркеры «Точка пути» с именами имя_1, имя_2… Пусто — стоит на месте",
                n.path, v -> {
                    n.path = v.trim();
                    NpcManager.save();
                }).clearable().placeholder("обход").max(32);
        if (!n.path.isEmpty()) {
            ui.select("Скорость", "", Double.toString(n.speed), v -> {
                n.speed = Double.parseDouble(v);
                NpcManager.save();
            }).option("0.8", "Медленно").option("1.5", "Шагом").option("3.0", "Быстро").option("5.0", "Бегом");
            ui.toggle("По кругу", "Иначе — туда и обратно", n.loop, on -> {
                n.loop = on;
                NpcManager.save();
            });
            ui.note(Ui.BLUE, "Маршрут обновляется раз в 5 секунд. Если рядом игрок — NPC останавливается.");
        }
        Ui.Select dir = ui.select("Смотрит", "Куда повёрнут, когда рядом никого", Integer.toString(((int) n.yaw % 360 + 360) % 360), v -> {
            n.yaw = Float.parseFloat(v);
            NpcManager.respawn(n);
        });
        for (String[] d : DIRS) {
            dir.option(d[0], d[1]);
        }
        ui.buttons().add("Удалить NPC", Ui.RED, () -> {
            NpcManager.delete(n);
            npc = null;
            toast("NPC удалён.");
        }).confirm("Удалить NPC «" + n.id + "»?");
    }

    // --- голограммы ---------------------------------------------------------------------------------

    private void buildHoloList(Ui ui) {
        ui.header("Голограммы", NpcManager.holograms().size() + "");
        ui.text("&7Текст в воздухе: правила у спавна, табло, подписи. Подстановки: &f%online% %max% %time% %date%&7.");
        for (final Hologram h : NpcManager.holograms()) {
            ui.row(new ItemStack(Items.NAME_TAG), "&f" + h.id)
                    .line(h.lines.isEmpty() ? "" : h.lines.get(0))
                    .line("&8" + com.ecoserv.world.WorldNames.nameOf(h.dim) + " " + (int) h.x + " " + (int) h.y + " " + (int) h.z)
                    .onClick(() -> holo = h.id)
                    .button("Настроить", Ui.BLUE, () -> holo = h.id);
        }
        ui.field("Новая голограмма здесь", "id — Enter", "", v -> holo = NpcManager.createHologram(player, v).id)
                .placeholder("правила").max(32);
    }

    private void buildHolo(Ui ui, final Hologram h) {
        ui.back(() -> holo = null);
        ui.title("Голограмма " + h.id).icon(new ItemStack(Items.NAME_TAG));
        ui.header("Строки", h.lines.size() + "");
        for (int i = 0; i < h.lines.size(); i++) {
            final int idx = i;
            ui.field((i + 1) + ".", "", h.lines.get(i), v -> {
                h.lines.set(idx, v);
                NpcManager.respawnHologram(h);
            }).max(120);
        }
        ui.buttons().add("+ строка", Ui.GREEN, () -> {
            h.lines.add("&7новая строка");
            NpcManager.respawnHologram(h);
        }).add("− последняя", Ui.GREY, () -> {
            if (h.lines.size() > 1) {
                h.lines.remove(h.lines.size() - 1);
                NpcManager.respawnHologram(h);
            }
        });
        ui.select("Расстояние между строками", "", Double.toString(h.spacing), v -> {
            h.spacing = Double.parseDouble(v);
            NpcManager.respawnHologram(h);
        }).option("0.22", "Плотно").option("0.27", "Обычно").option("0.35", "Свободно").option("0.5", "Широко");
        ui.buttons().add("Перенести сюда", Ui.BLUE, () -> {
            h.dim = player.dimension;
            h.x = player.posX;
            h.y = player.posY + 1.5D;
            h.z = player.posZ;
            NpcManager.respawnHologram(h);
        }).add("Выше", Ui.GREY, () -> {
            h.y += 0.25D;
            NpcManager.respawnHologram(h);
        }).add("Ниже", Ui.GREY, () -> {
            h.y -= 0.25D;
            NpcManager.respawnHologram(h);
        });
        ui.buttons().add("Удалить", Ui.RED, () -> {
            NpcManager.deleteHologram(h);
            holo = null;
        }).confirm("Удалить голограмму «" + h.id + "»?");
    }
}
