package com.ecoserv.region;

import com.ecoserv.dates.AgeFilter;
import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.script.ScriptManager;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.Selection;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Настройка зоны-триггера или портала: что делает, когда срабатывает, кому можно, куда ведёт портал
 * (в мир, подмир или к другому порталу — в том числе внутри подмира и в локальных мирах команд).
 */
public class RegionScreen extends UiScreen {

    private enum Tab {
        TARGET, EFFECTS, ACTIONS, CONDITIONS, AREA
    }

    private String name;
    private Tab tab;

    public RegionScreen(EntityPlayerMP player, String name) {
        super(player);
        this.name = RegionManager.key(name);
        Region r = RegionManager.get(name);
        this.tab = r != null && r.isPortal() ? Tab.TARGET : Tab.ACTIONS;
    }

    /** Список областей вида kind — вкладка «Порталы и зоны» в меню миров. */
    public static void openList(EntityPlayerMP player, String kind) {
        if (Perms.isAdmin(player)) {
            new com.ecoserv.subworld.menu.WorldsScreen(player, com.ecoserv.subworld.menu.WorldsScreen.Tab.PORTALS).open();
        }
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        ui.back(() -> new com.ecoserv.subworld.menu.WorldsScreen(player, com.ecoserv.subworld.menu.WorldsScreen.Tab.PORTALS).open());
        final Region r = RegionManager.get(name);
        if (r == null) {
            ui.title("Область удалена");
            ui.note(Ui.RED, "Области «" + name + "» больше нет.");
            return;
        }
        ui.title((r.isPortal() ? "Портал " : "Зона ") + r.name).subtitle("мир " + WorldNames.displayName(r.dim))
                .icon(r.isPortal() ? UiIcons.portal() : UiIcons.map());
        if (r.isPortal()) {
            ui.tab("Куда ведёт", UiIcons.compass(), tab == Tab.TARGET, () -> tab = Tab.TARGET);
        } else if (tab == Tab.TARGET) {
            tab = Tab.ACTIONS;
        }
        if (r.isSpecial()) {
            ui.tab("Эффекты", UiIcons.glitch(), tab == Tab.EFFECTS, () -> tab = Tab.EFFECTS);
        } else if (tab == Tab.EFFECTS) {
            tab = Tab.TARGET;
        }
        ui.tab("Что делает", UiIcons.admin(), tab == Tab.ACTIONS, () -> tab = Tab.ACTIONS);
        ui.tab("Условия", UiIcons.role(), tab == Tab.CONDITIONS, () -> tab = Tab.CONDITIONS);
        ui.tab("Границы", UiIcons.expand(), tab == Tab.AREA, () -> tab = Tab.AREA);
        ui.sideLabel("&8Действия");
        ui.sideButton("Телепорт к ней", new ItemStack(Items.ENDER_PEARL), Ui.GREEN, () -> {
            close();
            WorldTeleport.teleport(player, WorldTeleport.near(player, r.dim, r.center()));
        });
        ui.sideButton("Проверить на себе", new ItemStack(Items.BLAZE_POWDER), Ui.PURPLE, () -> {
            close();
            RegionManager.fire(player, r, true);
        });
        ui.sideButton("Удалить", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            RegionManager.remove(r.name);
            new com.ecoserv.subworld.menu.WorldsScreen(player, com.ecoserv.subworld.menu.WorldsScreen.Tab.PORTALS)
                    .withToast("Удалено: " + r.name).open();
        }).confirm("Удалить " + (r.isPortal() ? "портал «" : "зону «") + r.name + "» навсегда?");
        ui.status("&8создал " + r.creator + " · " + r.describeArea());
        switch (tab) {
            case TARGET:
                buildTarget(ui, r);
                break;
            case CONDITIONS:
                buildConditions(ui, r);
                break;
            case EFFECTS:
                ui.note(Ui.BLUE, "Значения «как у всех» задаются в списке порталов → «Настройка для всех». "
                        + "Здесь — только для «" + r.name + "»; &b•&f — своё значение, ↺ — вернуть как у всех.");
                PortalFxScreen.build(ui, this, r.style, r);
                break;
            case AREA:
                buildArea(ui, r);
                break;
            default:
                buildActions(ui, r);
        }
    }

    private static void changed() {
        RegionManager.changed();
    }

    // --- куда ведёт портал --------------------------------------------------------------------------

    private void buildTarget(Ui ui, final Region r) {
        ui.toggle("Портал включён", "", r.enabled, on -> {
            r.enabled = on;
            changed();
        });
        ui.select("Вид портала", "«Чёрная дыра»: сфера, сквозь неё видно место назначения, линза и искажения. "
                + "«Монолит»: плоская картинка (область толщиной в 1 блок) — как окно, почти без искажений", r.style, v -> {
            if (Region.STYLE_MONOLITH.equals(v) && r.planeAxis() < 0) {
                throw new UiError("Монолит — плоский: область должна быть толщиной в 1 блок по одной оси "
                        + "(например 10×10×1). Сейчас " + r.describeArea());
            }
            r.style = Region.STYLE_BLACKHOLE.equals(v) || Region.STYLE_MONOLITH.equals(v) ? v : Region.STYLE_NORMAL;
            changed();
        }).option(Region.STYLE_NORMAL, "Обычный", "срабатывает при входе в область", UiIcons.portal())
                .option(Region.STYLE_BLACKHOLE, "Чёрная дыра", "вид на место назначения, линза, эффекты", UiIcons.glitch())
                .option(Region.STYLE_MONOLITH, "Монолит", r.planeAxis() < 0 ? "нужна плоская область (толщина 1 блок)"
                        : r.planeAxis() == 1 ? "плоский, лежит как пол" : "плоская стена-картинка", UiIcons.expand());
        String current = "portal".equals(r.targetMode) ? "portal" : r.target;
        Ui.Select where = ui.select("Куда ведёт", "Мир, подмир или другой портал", current, v -> {
            if ("portal".equals(v)) {
                r.targetMode = "portal";
            } else {
                r.target = v;
                if ("portal".equals(r.targetMode)) {
                    r.targetMode = "spawn";
                }
            }
            changed();
        });
        where.option("portal", "К другому порталу", "выход у выбранного портала (и в подмире)", new ItemStack(Items.ENDER_EYE));
        for (String w : WorldNames.worldChoices()) {
            Subworld sw = SubworldRegistry.get(w);
            Integer dim = WorldNames.resolve(w);
            if (sw != null) {
                where.option(w, "Подмир " + UiUtil.plain(sw.name), sw.perOwner() ? "в свой локальный мир команды" : "общий мир",
                        UiUtil.icon(sw.icon));
            } else {
                where.option(w, "Мир " + w, dim != null ? "измерение " + dim : "", new ItemStack(Items.ENDER_PEARL));
            }
        }
        if ("portal".equals(r.targetMode)) {
            Ui.Select dest = ui.select("Портал назначения", "Игрок появится у выхода этого портала. Если он в подмире "
                    + "с локальными мирами — в своём мире команды", r.targetPortal, v -> {
                r.targetPortal = v;
                changed();
            });
            List<Region> portals = RegionManager.all(Region.PORTAL);
            portals.sort((a, b) -> a.name.compareTo(b.name));
            for (Region p : portals) {
                if (p != r) {
                    dest.option(p.name, p.name, "мир " + WorldNames.displayName(p.dim) + " · → " + UiUtil.plain(p.targetTitle()),
                            new ItemStack(Items.ENDER_EYE));
                }
            }
            final Region target = RegionManager.get(r.targetPortal);
            if (target != null) {
                boolean back = "portal".equals(target.targetMode) && RegionManager.key(target.targetPortal).equals(r.name);
                ui.row(new ItemStack(Items.ENDER_EYE), "&fСвязь с «" + target.name + "»")
                        .line(back ? "&aсвязаны в обе стороны" : "&7обратно «" + target.name + "» ведёт: " + UiUtil.plain(target.targetTitle()))
                        .button("Связать в обе стороны", Ui.BLUE, () -> {
                            target.targetMode = "portal";
                            target.targetPortal = r.name;
                            changed();
                            toast("«" + target.name + "» теперь ведёт обратно сюда.");
                        }).disabled(back);
            } else if (!r.targetPortal.isEmpty()) {
                ui.note(Ui.RED, "Портала «" + r.targetPortal + "» больше нет — выбери другой.");
            }
        } else if (!r.target.isEmpty()) {
            ui.select("Куда именно", "", r.targetMode, v -> {
                r.targetMode = v;
                changed();
            }).option("spawn", "На точку появления мира", "", new ItemStack(Items.COMPASS))
                    .option("last", "Где игрок был в прошлый раз", "в этом мире", new ItemStack(Items.MAP))
                    .option("point", "В заданную точку", "кнопка «Точка = где я стою»", new ItemStack(Items.ENDER_PEARL));
            if ("point".equals(r.targetMode)) {
                ui.row(new ItemStack(Items.ENDER_PEARL), "&fТочка: " + (int) r.tx + " " + (int) r.ty + " " + (int) r.tz)
                        .line("&7мир " + r.target);
            }
            ui.buttons().add("Точка = где я стою", Ui.BLUE, () -> {
                r.targetMode = "point";
                r.target = WorldNames.displayName(player.dimension);
                r.tx = player.posX;
                r.ty = player.posY;
                r.tz = player.posZ;
                r.tyaw = player.rotationYaw;
                r.tpitch = player.rotationPitch;
                changed();
                toast("Точка: " + r.target + " " + (int) r.tx + " " + (int) r.ty + " " + (int) r.tz);
            }).tip("Мир точки станет целью портала. Точка в шаблоне подмира работает во всех локальных мирах");
        } else {
            ui.note(Ui.YELLOW, "Портал пока никуда не ведёт — выбери цель.");
        }
        ui.header("Выход у этого портала");
        ui.text("&7Куда попадает тот, кто пришёл СЮДА через другой портал. По умолчанию — перед порталом, снаружи.");
        double[] exit = RegionManager.exitPoint(r);
        ui.row(new ItemStack(Items.OAK_DOOR), "&f" + (r.exitSet ? "Своя точка выхода" : "Перед порталом (автоматически)"))
                .line("&8" + (int) Math.floor(exit[0]) + " " + (int) Math.floor(exit[1]) + " " + (int) Math.floor(exit[2]));
        Ui.Buttons eb = ui.buttons();
        eb.add("Выход = где я стою", Ui.BLUE, () -> {
            if (RegionManager.regionDim(player.dimension) != r.dim && player.dimension != r.dim) {
                throw new UiError("Встань в мире этого портала (" + WorldNames.displayName(r.dim) + ").");
            }
            r.exitSet = true;
            r.ex = player.posX;
            r.ey = player.posY;
            r.ez = player.posZ;
            r.eyaw = player.rotationYaw;
            r.epitch = player.rotationPitch;
            changed();
            toast("Точка выхода запомнена.");
        });
        eb.add("Автоматически", Ui.GREY, () -> {
            r.exitSet = false;
            changed();
        }).disabled(!r.exitSet);
        List<Region> incoming = RegionManager.linkedTo(r.name);
        if (!incoming.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Region in : incoming) {
                sb.append(in.name).append(", ");
            }
            ui.note(Ui.BLUE, "Сюда ведут порталы: " + sb.substring(0, sb.length() - 2));
        }
    }

    // --- что делает -----------------------------------------------------------------------------------

    private void buildActions(Ui ui, final Region r) {
        if (!r.isPortal()) {
            ui.toggle("Зона включена", "", r.enabled, on -> {
                r.enabled = on;
                changed();
            });
        }
        ui.field("Сообщение в чат", "& — цвета, %player% — ник", r.message, v -> {
            r.message = v;
            changed();
        }).placeholder("нет").clearable().max(256);
        ui.field("Заголовок на экране", "«заголовок|подзаголовок»", r.title, v -> {
            r.title = v;
            changed();
        }).placeholder("нет").clearable().max(160);
        ui.field("Звук", "Кнопка … — выбрать из всех звуков игры и модов (с прослушиванием)", r.sound, v -> {
            r.sound = v.trim();
            changed();
        }).picker(Ui.PICK_SOUND).placeholder("нет").clearable().max(128);
        ui.header("Команды от сервера", r.commands.size() + " шт.");
        ui.text("&7Выполняются от имени сервера; %player% — ник игрока.");
        for (int i = 0; i < r.commands.size(); i++) {
            final int index = i;
            ui.row(new ItemStack(Blocks.COMMAND_BLOCK), "&f/" + r.commands.get(i))
                    .button("✕", Ui.RED, () -> {
                        if (index < r.commands.size()) {
                            r.commands.remove(index);
                            changed();
                        }
                    }).tip("Убрать команду");
        }
        ui.field("Добавить команду", "Без «/», например: give %player% minecraft:apple 3", "", v -> {
            String c = v.trim();
            while (c.startsWith("/")) {
                c = c.substring(1);
            }
            if (!c.isEmpty()) {
                r.commands.add(c);
                changed();
            }
        }).placeholder("команда").max(256);
        Ui.Select script = ui.select("Сценарий", "Запустить сценарий /script", r.script, v -> {
            r.script = v.isEmpty() ? "" : ScriptManager.key(v);
            changed();
        });
        script.option("", "Нет");
        for (String s : ScriptManager.names()) {
            script.option(ScriptManager.key(s), s);
        }
        ui.toggle("«Активация» для квестов", "Засчитывает условие FTB Quests «Активация» с названием «" + r.name + "»",
                r.activation, on -> {
                    r.activation = on;
                    changed();
                });
    }

    // --- условия -------------------------------------------------------------------------------------------

    private void buildConditions(Ui ui, final Region r) {
        ui.select("Когда срабатывает", "", r.mode, v -> {
            r.mode = v;
            changed();
        }).option("enter", "При каждом входе", "", new ItemStack(Items.CLOCK))
                .option("once", "Один раз на игрока", "", new ItemStack(Items.NETHER_STAR))
                .option("cooldown", "Не чаще раза в N секунд", "", new ItemStack(Items.COMPASS));
        if ("cooldown".equals(r.mode)) {
            ui.number("Пауза (секунд)", "", r.cooldownSeconds, 0, 86400 * 30, v -> {
                r.cooldownSeconds = (int) v;
                changed();
            });
        }
        if (!"enter".equals(r.mode)) {
            ui.buttons().add("Сбросить «уже срабатывало» у всех", Ui.YELLOW, () -> {
                int n = RegionManager.resetState(r.name, null);
                toast("Сброшено у игроков: " + n);
            }).confirm("Сбросить отметки у всех игроков — зона снова сработает для каждого?");
        }
        ui.header("Кому можно", r.roles.isEmpty() ? "&8всем" : r.roles.size() + " ролей");
        List<Role> roles = RoleManager.all();
        if (roles.isEmpty()) {
            ui.text("&8Ролей нет — срабатывает для всех.");
        } else {
            ui.text("&7Отметь роли — сработает только у игроков с одной из них. Ничего не отмечено — у всех.");
        }
        for (final Role role : roles) {
            ui.toggle("Роль " + role.title(), "&8" + role.id, r.roles.contains(role.id), on -> {
                r.roles.remove(role.id);
                if (on) {
                    r.roles.add(role.id);
                }
                changed();
            });
        }
        AgeFilter age = AgeFilter.parse(r.age);
        ui.field("Проверка возраста", "18 — ровно, >=16, <=12, 14-17 (/birthday)" + (age != null ? ". Сейчас: &f" + age.describe() : ""),
                r.age, v -> {
                    String t = v.trim();
                    if (!t.isEmpty() && AgeFilter.parse(t) == null) {
                        throw new UiError("Не понял фильтр возраста: " + t);
                    }
                    r.age = t;
                    changed();
                }).placeholder("без проверки").clearable().max(16);
        ui.field("Сообщение, если нельзя", "Пусто — молча", r.denyMessage, v -> {
            r.denyMessage = v;
            changed();
        }).placeholder("молча").clearable().max(256);
    }

    // --- границы -------------------------------------------------------------------------------------------

    private void buildArea(Ui ui, final Region r) {
        ui.row(new ItemStack(Items.MAP), "&f" + r.describeArea()).line("&7мир " + WorldNames.displayName(r.dim)
                + (SubworldRegistry.isOurs(r.dim) ? " · работает и во всех локальных мирах этого подмира" : ""));
        ui.buttons().add("Взять новое выделение палочкой", Ui.BLUE, () -> {
            String tool = "region_" + r.kind;
            Selection.Sel sel = Selection.complete(player.getUniqueID(), tool, player.dimension);
            if (sel == null) {
                throw new UiError("Сначала выдели новую область палочкой " + (r.isPortal() ? "порталов" : "триггеров")
                        + " (обе точки).");
            }
            r.setArea(sel.dim, sel.min(), sel.max());
            Selection.clear(player.getUniqueID(), tool);
            changed();
            toast("Новые границы: " + r.describeArea());
        });
        ui.field("Переименовать", "Латиница, кириллица, цифры, _ и -", r.name, v -> {
            String old = r.name;
            if (RegionManager.key(v).equals(old)) {
                return;
            }
            if (!RegionManager.rename(old, v)) {
                throw new UiError("Название занято или некорректно.");
            }
            name = RegionManager.key(v);
            toast("Переименовано.");
        }).max(48);
    }
}
