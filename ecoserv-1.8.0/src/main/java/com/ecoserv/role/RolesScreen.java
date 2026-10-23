package com.ecoserv.role;

import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Роли и теги — понятным окном: что даёт роль (тег в чате, команды без OP, права с пояснениями,
 * миры для /world, телепорт по карте, множитель наград), кому выдана и на сколько.
 */
public class RolesScreen extends UiScreen {

    /** Известные права мода — с пояснением, что они дают. */
    private static final String[][] PERMS = {
            {"*", "Все права EcoServ (почти как админ мода)"},
            {Perms.ADMIN, "Полный доступ к EcoServ: админ-меню, настройки, команды /ecoadmin"},
            {Perms.SHOP_EDIT, "Править прайс магазина"},
            {Perms.SHOP_USE, "Пользоваться магазином и балансом (обычно есть у всех)"},
            {Perms.TRADE, "Обмен между игроками (обычно есть у всех)"},
            {Perms.MARKET_PLACE, "Ставить блок-скупщик"},
            {Perms.BACKUP, "Управлять бэкапами (/backup)"},
            {Perms.WORLD, "Команда /world (обычно есть у всех)"},
    };

    private static final String[][] COLORS = {
            {"&f", "Белый"}, {"&e", "Жёлтый"}, {"&6", "Золотой"}, {"&c", "Красный"}, {"&4", "Тёмно-красный"},
            {"&d", "Розовый"}, {"&5", "Фиолетовый"}, {"&9", "Синий"}, {"&1", "Тёмно-синий"}, {"&b", "Голубой"},
            {"&3", "Бирюзовый"}, {"&a", "Зелёный"}, {"&2", "Тёмно-зелёный"}, {"&7", "Серый"}, {"&8", "Тёмно-серый"},
            {"&0", "Чёрный"},
    };

    /** Выбранная роль; «» — обзор, «+» — новая роль, «@» — роли игрока. */
    private String view = "";
    /** Срок для выдачи из окна: 0 — навсегда, иначе дней. */
    private int giveDays;
    /** Игрок для вкладки «Роли игрока». */
    @Nullable
    private UUID lookup;

    public RolesScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP player) {
        if (!Perms.isAdmin(player)) {
            ChatUtil.send(player, "&cРоли настраивает только админ. Свои роли: &f/role");
            return;
        }
        new RolesScreen(player).open();
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        ui.title("Роли и теги").icon(UiIcons.role());
        ui.back(() -> com.ecoserv.subworld.menu.WorldsScreen.open(player, com.ecoserv.subworld.menu.WorldsScreen.Tab.ADMIN));
        ui.tab("Как это работает", UiIcons.book(), view.isEmpty(), () -> view = "");
        ui.tab("Роли игрока", UiIcons.profile(), "@".equals(view), () -> view = "@");
        List<Role> roles = RoleManager.all();
        for (final Role r : roles) {
            ui.tab(r.title(), UiIcons.role(), r.id.equals(view), () -> view = r.id);
        }
        ui.sideButton("Новая роль", new ItemStack(Items.NETHER_STAR), Ui.GREEN, () -> view = "+");
        Role role = RoleManager.get(view);
        if ("+".equals(view)) {
            ui.subtitle("новая роль");
            buildCreate(ui);
        } else if ("@".equals(view)) {
            ui.subtitle("кому что выдано");
            buildPlayer(ui);
        } else if (role != null) {
            ui.subtitle(role.id);
            buildRole(ui, role);
        } else {
            view = "";
            ui.subtitle("обзор");
            buildHelp(ui, roles);
        }
    }

    // --- обзор ------------------------------------------------------------------------------------------

    private void buildHelp(Ui ui, List<Role> roles) {
        ui.note(Ui.BLUE, "Роль (тег) — набор привилегий, который выдают игроку навсегда или на срок: VIP, модератор, "
                + "строитель… Роль может давать: &fтег в чате&f, &fкоманды без OP&f, &fправа мода&f, &fмиры для /world&f, "
                + "&fтелепорт по карте Xaero&f и &fмножитель наград&f. Ещё роли проверяют барьеры ролей, датчики, "
                + "порталы и зоны, доступ в подмиры.");
        ui.text("&7Тег в чате берётся у роли с самым большим приоритетом. Если у игрока несколько ролей — привилегии "
                + "складываются, а множитель наград берётся самый большой.");
        ui.header("Роли", Integer.toString(roles.size()));
        if (roles.isEmpty()) {
            ui.text("&8Ролей пока нет — нажми «Новая роль» слева.");
        }
        for (final Role r : roles) {
            List<String> gives = gives(r);
            ui.row(new ItemStack(Items.NAME_TAG), r.title() + (r.prefix.isEmpty() ? "" : " &7тег: " + r.prefix))
                    .line("&7" + (gives.isEmpty() ? "пока ничего не даёт" : String.join(", ", gives)))
                    .line("&8приоритет " + r.priority + " · у игроков: " + RoleManager.holders(r.id).size()
                            + (r.description.isEmpty() ? "" : " · " + r.description))
                    .onClick(() -> view = r.id)
                    .button("Открыть", Ui.BLUE, () -> view = r.id);
        }
        ui.header("Команды", "&8для консоли и RCON");
        ui.text("&f/role give <ник> <роль> [30d|12h|90m]&7 — выдать, &f/role take <ник> <роль>&7 — снять, "
                + "&f/role player <ник>&7 — роли игрока, &f/role&7 — свои роли (любой игрок).");
    }

    private static List<String> gives(Role r) {
        List<String> out = new ArrayList<String>();
        if (!r.prefix.isEmpty()) {
            out.add("тег в чате");
        }
        if (!r.commands.isEmpty()) {
            out.add("команд: " + r.commands.size());
        }
        if (!r.permissions.isEmpty()) {
            out.add("прав: " + r.permissions.size());
        }
        if (!r.worlds.isEmpty()) {
            out.add("миров: " + (r.worlds.contains("*") ? "все" : Integer.toString(r.worlds.size())));
        }
        if (r.xaeroTp) {
            out.add("телепорт по карте");
        }
        if (r.rewardMultiplier != 1.0D) {
            out.add("награды x" + Fmt.price(r.rewardMultiplier));
        }
        return out;
    }

    // --- новая роль ------------------------------------------------------------------------------------------

    private String newId = "";
    private String newName = "";

    private void buildCreate(Ui ui) {
        ui.text("&7Придумай короткое имя для команд (латиница, цифры, _) и название, которое увидят игроки.");
        ui.field("id роли", "Например vip, moder, builder. Его пишут в командах /role give", newId, v -> {
            newId = v.trim().toLowerCase(Locale.ROOT);
            if (!newId.isEmpty() && !RoleManager.ID.matcher(newId).matches()) {
                String bad = newId;
                newId = "";
                throw new UiError("id «" + bad + "»: только латиница, цифры и _, до 32 символов.");
            }
        }).placeholder("vip").max(32);
        ui.field("Название", "Как роль видят игроки: «VIP», «Модератор»", newName, v -> newName = v.trim())
                .placeholder("VIP").max(32);
        ui.footer("Создать роль", Ui.GREEN, () -> {
            if (newId.isEmpty()) {
                throw new UiError("Введи id роли.");
            }
            Role r = RoleManager.create(newId, newName.isEmpty() ? newId : newName);
            if (r == null) {
                throw new UiError("id «" + newId + "» уже занят.");
            }
            view = r.id;
            newId = "";
            newName = "";
            toast("Роль создана: " + UiUtil.plain(r.title()) + ". Настрой, что она даёт.");
        }).disabled(newId.isEmpty());
    }

    // --- одна роль -------------------------------------------------------------------------------------------

    private void buildRole(Ui ui, final Role r) {
        final Runnable save = () -> {
            r.normalize();
            RoleManager.changed();
        };
        ui.sideLabel("&8Роль " + r.id);
        ui.sideButton("Удалить роль", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            RoleManager.delete(r.id);
            view = "";
            toast("Роль удалена и снята со всех игроков.");
        }).confirm("Удалить роль " + r.title() + "&f? Она снимется со всех игроков.");

        String preview = (r.prefix.isEmpty() ? "" : r.prefix + " ") + "&f" + player.getName() + "&7: привет!";
        ui.row(new ItemStack(Items.WRITABLE_BOOK), preview).line("&8так выглядит сообщение в чате")
                .chip("приоритет " + r.priority, Ui.GREY).highlight(true);

        ui.header("Как выглядит");
        ui.field("Название", "Для списков и сообщений", r.name, v -> {
            r.name = v.trim().isEmpty() ? r.id : v.trim();
            save.run();
        }).max(32);
        Ui.Select color = ui.select("Цвет названия", "", r.color, v -> {
            r.color = v;
            save.run();
        });
        for (String[] c : COLORS) {
            color.option(c[0], c[0] + c[1]);
        }
        ui.field("Тег в чате", "Перед ником, & — цвета. Пусто — без тега", r.prefix, v -> {
            r.prefix = v.trim();
            save.run();
        }).placeholder("&6[" + UiUtil.plain(r.name) + "]").clearable().max(48);
        ui.number("Приоритет", "Чем больше — тем выше в списке; тег в чате берётся у роли с наибольшим", r.priority,
                -1000, 1000, v -> {
                    r.priority = (int) v;
                    save.run();
                });
        ui.field("Описание", "Для себя и для списка ролей", r.description, v -> {
            r.description = v.trim();
            save.run();
        }).placeholder("нет").clearable().max(160);

        ui.header("Что даёт");
        ui.toggle("Телепорт по карте Xaero", "Клик по карте Xaero переносит игрока (карта шлёт /tp)", r.xaeroTp, on -> {
            r.xaeroTp = on;
            save.run();
        });
        ui.field("Множитель наград", "Деньги за игру и ежедневки ×N (1 — как у всех, 1.5 — на половину больше)",
                Fmt.price(r.rewardMultiplier), v -> {
                    try {
                        r.rewardMultiplier = Math.max(0.0D, Math.min(100.0D, Double.parseDouble(v.trim().replace(',', '.'))));
                    } catch (NumberFormatException e) {
                        throw new UiError("Нужно число, например 1.5");
                    }
                    save.run();
                }).max(8);

        ui.header("Команды без OP", r.commands.isEmpty() ? "&8нет" : Integer.toString(r.commands.size()));
        ui.text("&7Игрок с ролью сможет выполнять эти команды, даже если он не оператор.");
        for (final String c : new ArrayList<String>(r.commands)) {
            ui.row(new ItemStack(Blocks.COMMAND_BLOCK), "&f/" + c).button("✕", Ui.RED, () -> {
                r.commands.remove(c);
                save.run();
            }).tip("Убрать команду");
        }
        Ui.Select cmd = ui.select("Добавить команду", "Все команды сервера (и модов)", "", v -> {
            if (!v.isEmpty() && !r.commands.contains(v)) {
                r.commands.add(v);
                save.run();
            }
        });
        cmd.option("", "— выбрать —");
        List<String> names = new ArrayList<String>(player.getServer().getCommandManager().getCommands().keySet());
        names.sort(String::compareTo);
        for (String n : names) {
            if (!r.commands.contains(n)) {
                cmd.option(n, "/" + n);
            }
        }

        ui.header("Права", r.permissions.isEmpty() ? "&8нет" : Integer.toString(r.permissions.size()));
        for (final String[] p : PERMS) {
            ui.toggle(p[0], p[1], r.permissions.contains(p[0]), on -> {
                r.permissions.remove(p[0]);
                if (on) {
                    r.permissions.add(p[0]);
                }
                save.run();
            });
        }
        for (final String p : new ArrayList<String>(r.permissions)) {
            boolean known = false;
            for (String[] k : PERMS) {
                known |= k[0].equals(p);
            }
            if (!known) {
                ui.row(new ItemStack(Items.PAPER), "&f" + p).line("&8право другого мода (Forge PermissionAPI)")
                        .button("✕", Ui.RED, () -> {
                            r.permissions.remove(p);
                            save.run();
                        });
            }
        }
        ui.field("Другое право", "Узел PermissionAPI другого мода, например ftbutilities.home.max; «узел.*» — все внутри",
                "", v -> {
                    String p = v.trim();
                    if (!p.isEmpty() && !r.permissions.contains(p)) {
                        r.permissions.add(p);
                        save.run();
                    }
                }).placeholder("mod.permission").max(128);

        ui.header("Миры для /world", r.worlds.isEmpty() ? "&8только общие" : r.worlds.contains("*") ? "все" : Integer.toString(r.worlds.size()));
        ui.text("&7Кроме миров, открытых всем в настройках (worlds → player_worlds).");
        ui.toggle("Любые миры", "Все миры сервера", r.worlds.contains("*"), on -> {
            r.worlds.remove("*");
            if (on) {
                r.worlds.add("*");
            }
            save.run();
        });
        if (!r.worlds.contains("*")) {
            for (Integer dim : WorldNames.allDims()) {
                if (SubworldRegistry.isOurs(dim)) {
                    continue;
                }
                final String name = WorldNames.nameOf(dim);
                boolean on = false;
                for (String w : r.worlds) {
                    Integer d = WorldNames.resolve(w);
                    on |= d != null && d.equals(dim);
                }
                ui.toggle("Мир " + name, "&8измерение " + dim, on, enable -> {
                    r.worlds.removeIf(w -> {
                        Integer d = WorldNames.resolve(w);
                        return d != null && d.equals(WorldNames.resolve(name));
                    });
                    if (enable) {
                        r.worlds.add(name);
                    }
                    save.run();
                });
            }
        }

        ui.header("У кого есть", RoleManager.holders(r.id).size() + " игроков");
        long now = System.currentTimeMillis();
        for (final UUID id : RoleManager.holders(r.id)) {
            RoleManager.Assignment a = null;
            for (RoleManager.Assignment x : RoleManager.assignments(id)) {
                if (x.role.equals(r.id)) {
                    a = x;
                }
            }
            boolean online = PlayerUtil.online(id) != null;
            Ui.Row row = ui.row(new ItemStack(Items.SKULL, 1, 3), (online ? "&f" : "&7") + PlayerUtil.nameOf(id))
                    .line(a == null || a.until <= 0 ? "&7навсегда" : "&7до " + Fmt.timestamp(a.until) + " (осталось "
                            + Fmt.duration(a.until - now) + ")")
                    .line(a == null || a.by.isEmpty() ? "" : "&8выдал " + a.by + " " + Fmt.timestamp(a.at));
            row.button("Снять", Ui.RED, () -> {
                RoleManager.take(id, r.id);
                toast("Роль снята: " + PlayerUtil.nameOf(id));
            }).confirm("Снять роль " + r.title() + " &fс игрока " + PlayerUtil.nameOf(id) + "?");
        }
        ui.select("Срок для новой выдачи", "", Integer.toString(giveDays), v -> {
            try {
                giveDays = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                giveDays = 0;
            }
        }).option("0", "Навсегда").option("1", "1 день").option("7", "7 дней").option("30", "30 дней")
                .option("90", "90 дней").option("365", "1 год");
        ui.field("Выдать игроку", "Ник — роль выдаётся сразу (срок — выше)", "", v -> {
            String n = v.trim();
            if (n.isEmpty()) {
                return;
            }
            UUID id = PlayerUtil.uuidOf(n);
            if (id == null) {
                throw new UiError("Игрок " + n + " не найден (должен хотя бы раз зайти на сервер).");
            }
            long until = giveDays <= 0 ? 0L : System.currentTimeMillis() + giveDays * 86_400_000L;
            RoleManager.give(id, r.id, until, player.getName());
            EntityPlayerMP online = PlayerUtil.online(id);
            if (online != null && online != player) {
                ChatUtil.send(online, "&aТебе выдана роль " + r.title() + (until > 0 ? " &7до &f" + Fmt.timestamp(until) : ""));
            }
            toast("Роль выдана: " + PlayerUtil.nameOf(id) + (until > 0 ? " до " + Fmt.timestamp(until) : " навсегда"));
        }).picker(Ui.PICK_PLAYER).placeholder("ник").max(16);
    }

    // --- роли игрока ----------------------------------------------------------------------------------------------

    private void buildPlayer(Ui ui) {
        ui.field("Игрок", "Ник — показать его роли", lookup == null ? "" : PlayerUtil.nameOf(lookup), v -> {
            String n = v.trim();
            if (n.isEmpty()) {
                lookup = null;
                return;
            }
            UUID id = PlayerUtil.uuidOf(n);
            if (id == null) {
                throw new UiError("Игрок " + n + " не найден.");
            }
            lookup = id;
        }).picker(Ui.PICK_PLAYER).placeholder("ник").max(16);
        if (lookup == null) {
            return;
        }
        final UUID id = lookup;
        List<RoleManager.Assignment> list = RoleManager.assignments(id);
        ui.header("Роли " + PlayerUtil.nameOf(id), Integer.toString(list.size()));
        if (list.isEmpty()) {
            ui.text("&8Ролей нет.");
        }
        long now = System.currentTimeMillis();
        for (final RoleManager.Assignment a : list) {
            final Role r = RoleManager.get(a.role);
            if (r == null) {
                continue;
            }
            ui.row(new ItemStack(Items.NAME_TAG), r.title())
                    .line(a.until <= 0 ? "&7навсегда" : "&7до " + Fmt.timestamp(a.until) + " (осталось " + Fmt.duration(a.until - now) + ")")
                    .button("Открыть роль", Ui.BLUE, () -> view = r.id)
                    .button("Снять", Ui.RED, () -> {
                        RoleManager.take(id, r.id);
                        toast("Роль снята.");
                    }).confirm("Снять роль " + r.title() + "&f?");
        }
        Ui.Select give = ui.select("Выдать роль навсегда", "", "", v -> {
            if (v.isEmpty()) {
                return;
            }
            RoleManager.give(id, v, 0L, player.getName());
            toast("Роль выдана.");
        });
        give.option("", "— выбрать —");
        for (Role r : RoleManager.all()) {
            give.option(r.id, r.title(), r.id);
        }
    }
}
